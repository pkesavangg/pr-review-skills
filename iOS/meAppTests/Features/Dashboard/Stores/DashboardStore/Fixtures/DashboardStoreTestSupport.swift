import Foundation
@testable import meApp

@MainActor
enum DashboardStoreTestSupport {
    typealias SUT = (
        store: DashboardStore,
        accountService: AccountService,
        cacheManager: MockDashboardCacheManager
    )

    static func makeSUT(
        lightweight: Bool = true,
        performInitialAccountLoad: Bool = false
    ) -> SUT {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies(
            performInitialAccountLoad: performInitialAccountLoad
        )

        let formatter = MockDashboardFormatter()
        let cacheManager = MockDashboardCacheManager()

        let store = DashboardStore(
            lightweight: lightweight,
            formatter: formatter,
            cacheManager: cacheManager
        )

        return (store, deps.account, cacheManager)
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
