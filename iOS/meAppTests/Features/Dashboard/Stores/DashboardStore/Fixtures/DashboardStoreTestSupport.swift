import Foundation
@testable import meApp

@MainActor
enum DashboardStoreTestSupport {
    struct SUT {
        let store: DashboardStore
        let accountService: AccountService
        let cacheManager: MockDashboardCacheManager
    }

    static func makeSUT(
        lightweight: Bool = true,
        performInitialAccountLoad: Bool = false,
        initialAccount: AccountSnapshot? = nil
    ) -> SUT {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies(
            performInitialAccountLoad: performInitialAccountLoad
        )

        // The store's managers resolve the account through DIFFERENT @Injector keys:
        // DashboardStore/DashboardMetricsManager use the concrete `AccountService`, while
        // DashboardLifecycleManager uses the `AccountServiceProtocol`. registerDashboardConcreteDependencies
        // only registers the real account under the concrete key, so the lifecycle manager resolves
        // reset()'s throwaway MockAccountService (nil account) — its handleDashboardTypeChange then
        // computes .dashboard12 from a nil account and races the metrics manager (which reads the real
        // account). Register the real account under the protocol key too so every manager sees the same
        // instance the test mutates, making the dashboard-type subscription deterministic.
        DependencyContainer.shared.register(deps.account as AccountServiceProtocol)

        // Seed a non-nil active account BEFORE the store initializes so the async init pipeline
        // (initializeDashboard → loadDashboardConfigurationFromAPI → loadMetricsFromAPI) never takes
        // the `noActiveAccount` catch path that resets dashboardType to the default .dashboard12.
        // refreshAccount() can't clobber it: the mock local repo has no matching record, so it throws
        // accountNotFound which the pipeline swallows, leaving the seeded account intact.
        if let initialAccount {
            deps.account.activeAccount = initialAccount
        }

        let formatter = MockDashboardFormatter()
        let cacheManager = MockDashboardCacheManager()

        let store = DashboardStore(
            lightweight: lightweight,
            formatter: formatter,
            cacheManager: cacheManager
        )

        return SUT(store: store, accountService: deps.account, cacheManager: cacheManager)
    }

    static func makeActiveAccount(
        id: String = "acct-1",
        dashboardMetrics: String? = nil,
        dashboardType: String? = "dashboard12",
        progressMetrics: String? = nil,
        weightUnit: WeightUnit = .lb,
        goalType: GoalType? = .gain,
        goalWeight: Double? = nil,
        initialWeight: Double? = 1800,
        weightlessOn: Bool = false,
        weightlessWeight: Double? = nil
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: "dashboard@example.com",
            isActiveAccount: true,
            weightUnit: weightUnit,
            weightHeight: "70",
            activityLevel: .normal,
            goalType: goalType,
            goalWeight: goalWeight,
            initialWeight: initialWeight,
            isWeightlessOn: weightlessOn,
            weightlessWeight: weightlessWeight,
            dashboardType: dashboardType,
            dashboardMetrics: dashboardMetrics,
            progressMetrics: progressMetrics
        )
    }
}
