import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardStreakManagerTests {
    private struct DashboardStreakManagerTestsSUT {
        let sut: DashboardStreakManager
        let accountService: AccountService
        let entryRepo: MockEntryRepository
    }

    @Test("init and setupInitialStreakItems: create six placeholder streak cards")
    func initAndSetupInitialStreakItems() {
        let sut = makeSUT().sut

        #expect(sut.state.streakItems.count == 6)
        #expect(sut.state.activeStreakItemsCount == 6)
        #expect(sut.state.streakItems.allSatisfy { $0.value == DashboardStrings.placeholder })

        sut.state.streakItems = [DashboardTestFixtures.makeMetricItem(value: "9", label: DashboardStrings.currentStreak)]
        sut.state.activeStreakItemsCount = 1
        sut.setupInitialStreakItems()

        #expect(sut.state.streakItems.count == 6)
        #expect(sut.state.activeStreakItemsCount == 6)
        #expect(sut.state.streakItems.first?.value == DashboardStrings.placeholder)
    }

    @Test("updateStreakItems: populates streak cards from Progress in pounds")
    func updateStreakItemsPopulatesLbsValues() async throws {
        let sutBundle = makeSUT(weightUnit: .lb)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightUnit: .lb)

        try await sut.updateStreakItems(with: makeProgress())

        #expect(sut.state.streakItems[0].value == "3")
        #expect(sut.state.streakItems[1].value == "7")
        #expect(sut.state.streakItems[2].label == "lb/week")
        #expect(sut.state.streakItems[2].value == "+1.0")
        #expect(sut.state.streakItems[5].label == "lb/total")
    }

    @Test("updateStreakItems: populates streak cards from Progress in kilograms")
    func updateStreakItemsPopulatesKgValues() async throws {
        let sutBundle = makeSUT(weightUnit: .kg)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightUnit: .kg)

        try await sut.updateStreakItems(with: makeProgress())

        #expect(sut.state.streakItems[2].label == "kg/week")
        #expect(sut.state.streakItems[3].label == "kg/month")
        #expect(sut.state.streakItems[2].value != DashboardStrings.placeholder)
        #expect(sut.state.streakItems[5].value != DashboardStrings.placeholder)
    }

    @Test("updateStreakItems: preserves the visible streak count after the first real refresh")
    func updateStreakItemsPreservesActiveCountAfterInitialRefresh() async throws {
        let sutBundle = makeSUT()
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        try await sut.updateStreakItems(with: makeProgress())
        sut.state.activeStreakItemsCount = 3

        try await sut.updateStreakItems(with: meApp.Progress(
            count: 4,
            currentStreak: 4,
            initYear: nil,
            initMonth: nil,
            initWeek: nil,
            initWt: 1800,
            latest: nil,
            longestStreak: 8,
            month: 30,
            percent: nil,
            total: 50,
            week: 20,
            year: 40
        ))

        #expect(sut.state.activeStreakItemsCount == 3)
        #expect(sut.state.streakItems[0].value == "4")
    }

    @Test("refreshStreakData: loads progress from entry service and replaces placeholders")
    func refreshStreakDataLoadsFromEntryService() async throws {
        let sutBundle = makeSUT()
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        let accountId = "streak-refresh"
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: accountId)
        try persistEntries([
            EntryTestFixtures.makeEntry(
                accountId: accountId,
                timestamp: "2026-03-01T08:00:00Z",
                weight: 1800
            ),
            EntryTestFixtures.makeEntry(
                accountId: accountId,
                timestamp: "2026-03-05T08:00:00Z",
                weight: 1840
            )
        ])

        try await sut.refreshStreakData()

        #expect(sut.state.streakItems.count == 6)
        #expect(sut.state.streakItems.first?.value != DashboardStrings.placeholder)
        #expect(sut.state.streakItems[2].label.contains("/week") == true)
    }

    @Test("refreshStreakData: wraps service failures as dataLoadingFailed")
    func refreshStreakDataErrorHandling() async {
        let sut = makeSUT().sut

        do {
            try await sut.refreshStreakData()
            Issue.record("Expected refreshStreakData to throw")
        } catch let error as DashboardError {
            guard case .dataLoadingFailed = error else {
                Issue.record("Expected dataLoadingFailed, got \(error)")
                return
            }
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("toggleStreakVisibility and getStreakItemsToShow: keep streak visibility and ordering consistent")
    func toggleStreakVisibilityMaintainsShownItems() async throws {
        let sut = makeSUT().sut
        sut.state.streakItems = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lb/week")
        ]
        sut.state.activeStreakItemsCount = 2

        try await sut.toggleStreakVisibility(at: 2)
        #expect(sut.state.activeStreakItemsCount == 3)
        #expect(sut.getStreakItemsToShow(isEditMode: false).count == 3)

        try await sut.toggleStreakVisibility(at: 0)
        #expect(sut.state.activeStreakItemsCount == 2)
        #expect(sut.getStreakItemsToShow(isEditMode: false).count == 2)
        #expect(sut.getStreakItemsToShow(isEditMode: true).count == 3)
    }

    @Test("validateStreakData: rejects invalid active streak counts")
    func validateStreakDataRejectsInvalidState() {
        let sut = makeSUT().sut
        sut.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak)]
        sut.state.activeStreakItemsCount = 2

        do {
            try sut.validateStreakData()
            Issue.record("Expected validateStreakData to throw")
        } catch let error as DashboardError {
            guard case .invalidMetricData = error else {
                Issue.record("Expected invalidMetricData, got \(error)")
                return
            }
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("resetStreakData and refreshStreakDataForUnitChange: restore placeholders, clear removals, and reload values")
    func resetAndRefreshStreakData() async throws {
        let sutBundle = makeSUT()
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        let accountId = "streak-reset"
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: accountId)
        try persistEntries([
            EntryTestFixtures.makeEntry(accountId: accountId, timestamp: "2026-03-01T08:00:00Z", weight: 1800),
            EntryTestFixtures.makeEntry(accountId: accountId, timestamp: "2026-03-05T08:00:00Z", weight: 1840)
        ])

        try await sut.updateStreakItems(with: makeProgress())
        sut.state.activeStreakItemsCount = 2
        sut.state.removedStreaks = ["old"]

        try await sut.resetStreakData()

        #expect(sut.state.activeStreakItemsCount == 6)
        #expect(sut.state.removedStreaks.isEmpty)
        #expect(sut.state.streakItems.first?.value != DashboardStrings.placeholder)

        try await sut.refreshStreakDataForUnitChange()
        #expect(sut.state.streakItems[2].label.contains("/week") == true)
    }

    @Test("streak analytics: derive trend and momentum from the visible streak items")
    func calculateStreakAnalytics() {
        let sut = makeSUT().sut
        sut.state.streakItems = [
            DashboardTestFixtures.makeMetricItem(value: "4", label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(value: "8", label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(value: "+3.0", label: "lb/week"),
            DashboardTestFixtures.makeMetricItem(value: "+8.0", label: "lb/month"),
            DashboardTestFixtures.makeMetricItem(value: "+12.0", label: "lb/year"),
            DashboardTestFixtures.makeMetricItem(value: "-4.0", label: "lb/total")
        ]

        let analytics = sut.calculateStreakAnalytics()

        #expect(analytics.currentStreak == 4)
        #expect(analytics.longestStreak == 8)
        #expect(analytics.weeklyChange == 3.0)
        #expect(analytics.totalChange == -4.0)
        #expect(analytics.trend == .building)
        #expect(analytics.momentum == .accelerating)
    }

    @Test("streak formatting helpers: format streak counts and weight changes")
    func streakFormattingHelpers() {
        let sut = makeSUT().sut

        #expect(sut.formatStreakDisplay(0) == "0 days")
        #expect(sut.formatStreakDisplay(1) == "1 day")
        #expect(sut.formatStreakDisplay(3) == "3 days")
        #expect(sut.formatWeightChangeDisplay(10) == "+1.0")
    }

    @Test("streak visibility helpers: report removed items, show grid state, and expose grid columns")
    func streakVisibilityHelpers() {
        let sut = makeSUT().sut
        sut.state.streakItems = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak)
        ]
        sut.state.activeStreakItemsCount = 1

        #expect(sut.isStreakRemoved(at: 1) == true)
        #expect(sut.isStreakRemoved(at: 0) == false)
        #expect(sut.shouldShowStreakGrid() == true)
        #expect(sut.getStreakGridColumns().count == 2)

        sut.resetActiveStreakItemsCountToShowAll()
        #expect(sut.state.activeStreakItemsCount == 2)
    }

    @Test("reorderStreakItems: reorders the underlying streak array")
    func reorderStreakItems() async throws {
        let sut = makeSUT().sut
        sut.state.streakItems = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lb/week")
        ]

        try await sut.reorderStreakItems(from: IndexSet(integer: 0), to: 3)

        #expect(sut.state.streakItems.map(\.label) == [
            DashboardStrings.longestStreak,
            "lb/week",
            DashboardStrings.currentStreak
        ])
    }

    private func makeSUT(weightUnit: WeightUnit = .lb) -> DashboardStreakManagerTestsSUT {
        TestDependencyContainer.reset()

        let accountService = AccountService(
            apiRepo: MockAccountAPIRepository(),
            localRepo: MockAccountRepository(),
            integrationApiRepo: MockIntegrationAPIRepository(),
            networkMonitor: MockNetworkMonitor(isConnected: true),
            performInitialLoad: false
        )
        let logger = LoggerService()
        let entryRepo = MockEntryRepository()
        let entryService = EntryService(
            accountService: accountService,
            localRepo: entryRepo,
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: MockEntryRepositoryAPI()
        )

        DependencyContainer.shared.register(accountService as AccountService)
        DependencyContainer.shared.register(logger as LoggerService)
        DependencyContainer.shared.register(entryService as EntryService)

        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightUnit: weightUnit)

        let sut = DashboardStreakManager()
        return DashboardStreakManagerTestsSUT(sut: sut, accountService: accountService, entryRepo: entryRepo)
    }

    private func makeProgress() -> meApp.Progress {
        meApp.Progress(
            count: 3,
            currentStreak: 3,
            initYear: nil,
            initMonth: nil,
            initWeek: nil,
            initWt: 1800,
            latest: nil,
            longestStreak: 7,
            month: 20,
            percent: nil,
            total: 40,
            week: 10,
            year: 30
        )
    }

    private func persistEntries(_ entries: [Entry]) throws {
        let context = PersistenceController.shared.context
        for entry in entries {
            context.insert(entry)
        }
        try context.save()
    }
}
