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
        weightUnit: WeightUnit = .lb,
        goalWeight: Double? = nil,
        weightlessOn: Bool = false,
        weightlessWeight: Double? = nil
    ) -> Account {
        let account = AccountTestFixtures.makeAccountModel(
            id: id,
            email: "dashboard@example.com",
            isActive: true
        )
        account.dashboardSettings = DashboardSettings(
            accountId: id,
            dashboardMetrics: dashboardMetrics,
            progressMetrics: nil,
            dashboardType: dashboardType,
            isSynced: false
        )
        account.weightSettings = WeightCompSettings(
            accountId: id,
            height: "70",
            activityLevel: .normal,
            weightUnit: weightUnit
        )
        account.goalSettings = GoalSettings(
            accountId: id,
            goalType: .gain,
            initialWeight: 1800,
            goalWeight: goalWeight,
            goalPercent: nil,
            isSynced: false
        )
        account.weightlessSettings = WeightlessSettings(
            accountId: id,
            isWeightlessOn: weightlessOn,
            weightlessTimestamp: nil,
            weightlessWeight: weightlessWeight,
            isSynced: false
        )
        return account
    }
}
