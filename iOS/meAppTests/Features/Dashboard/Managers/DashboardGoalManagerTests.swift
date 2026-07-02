import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardGoalManagerTests {
    private struct DashboardGoalManagerTestsSUT {
        let sut: DashboardGoalManager
        let accountService: AccountService
        let entryRepo: MockEntryRepository
    }

    @Test("loadGoalData: sets target, current delta, and progress from the active account and latest entry")
    func loadGoalDataSuccess() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        let entryRepo = sutBundle.entryRepo
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)
        entryRepo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-09T08:00:00Z", weight: 1900)
        ]

        try await sut.loadGoalData()

        #expect(sut.state.hasGoalSet == true)
        #expect(sut.state.goalStartWeight == 180.0)
        #expect(sut.state.goalWeight == 200.0)
        #expect(sut.state.goalDelta == 10.0)
        #expect(sut.formatGoalProgress() == "50%")
    }

    @Test("loadGoalData: no goal weight keeps goal hidden")
    func loadGoalDataWithoutGoalKeepsHiddenState() async throws {
        let sutBundle = makeSUT(goalWeight: nil)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: nil)

        try await sut.loadGoalData()

        #expect(sut.state.hasGoalSet == false)
        #expect(sut.getGoalWeightForDisplay(isWeightlessMode: false, anchorWeight: nil) == nil)
    }

    @Test("loadGoalData: without an active account throws noActiveAccount")
    func loadGoalDataWithoutActiveAccountThrows() async {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = nil

        do {
            try await sut.loadGoalData()
            Issue.record("Expected loadGoalData to throw")
        } catch let error as DashboardError {
            guard case .noActiveAccount = error else {
                Issue.record("Expected noActiveAccount, got \(error)")
                return
            }
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("loadGoalData: latest entry failure does not prevent goal state refresh")
    func loadGoalDataToleratesLatestEntryFailure() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        let entryRepo = sutBundle.entryRepo
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)
        entryRepo.fetchLatestEntryError = DashboardTestError.repoFailure

        try await sut.loadGoalData()

        #expect(sut.state.goalStartWeight == 180.0)
        #expect(sut.state.goalWeight == 200.0)
        #expect(sut.state.goalDelta == 200.0)
        #expect(sut.formatGoalProgress() == "100%")
    }

    @Test("loadGoalData: missing goal settings returns without mutating goal state")
    func loadGoalDataWithoutGoalSettingsReturns() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            goalType: nil,
            goalWeight: nil,
            initialWeight: nil
        )

        try await sut.loadGoalData()

        #expect(sut.state.hasGoalSet == false)
        #expect(sut.state.goalWeight == 0)
        #expect(sut.state.goalProgress == 0)
    }

    @Test("updateGoalProgress: recomputes target versus current progress")
    func updateGoalProgressRecomputesState() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)

        try await sut.updateGoalProgress(currentWeight: 1950)

        #expect(sut.state.goalStartWeight == 180.0)
        #expect(sut.state.goalWeight == 200.0)
        #expect(sut.state.goalDelta == 15.0)
        #expect(sut.formatGoalProgress() == "75%")
    }

    @Test("getGoalWeightForDisplay: uses live account fallback before state refresh and supports weightless mode")
    func getGoalWeightForDisplayUsesFallbackAndWeightlessMode() {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)

        #expect(sut.getGoalWeightForDisplay(isWeightlessMode: false, anchorWeight: nil) == 200.0)
        #expect(sut.getGoalWeightForDisplay(isWeightlessMode: true, anchorWeight: 175.0) == 25.0)
    }

    @Test("refreshGoalDataForUnitChange: reloads the goal state using the current unit")
    func refreshGoalDataForUnitChangeUsesCurrentUnit() async throws {
        let sutBundle = makeSUT(goalWeight: 2000, weightUnit: .kg)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        let entryRepo = sutBundle.entryRepo
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            weightUnit: .kg,
            goalWeight: 2000
        )
        entryRepo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-09T08:00:00Z", weight: 1900)
        ]

        try await sut.refreshGoalDataForUnitChange()

        #expect(sut.state.goalUnit == .kg)
        #expect(sut.state.goalStartWeight > 0)
        #expect(sut.state.goalWeight > 0)
        #expect(sut.formatGoalProgress() == "50%")
    }

    @Test("updateGoalProgress: missing goal settings returns without mutating state")
    func updateGoalProgressWithoutGoalSettingsReturns() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            goalType: nil,
            goalWeight: nil,
            initialWeight: nil
        )

        try await sut.updateGoalProgress(currentWeight: 1950)

        #expect(sut.state.goalWeight == 0)
        #expect(sut.state.goalProgress == 0)
    }

    @Test("resetGoalState and goal state mutators: update goal type and unit predictably")
    func goalStateMutators() {
        let sut = makeSUT(goalWeight: 2000).sut

        sut.updateGoalType(.lose)
        sut.updateGoalUnit(.kg)

        #expect(sut.state.goalType == .lose)
        #expect(sut.state.goalUnit == .kg)

        sut.resetGoalState()

        #expect(sut.state.goalType == .gain)
        #expect(sut.state.goalUnit == .lb)
        #expect(sut.state.hasGoalSet == false)
    }

    @Test("calculateWeightlessGoal: offsets start, target, delta, and progress by the anchor")
    func calculateWeightlessGoalUsesAnchor() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        let entryRepo = sutBundle.entryRepo
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)
        entryRepo.entries = [EntryTestFixtures.makeEntry(timestamp: "2026-03-09T08:00:00Z", weight: 1900)]

        try await sut.calculateWeightlessGoal(anchorWeight: 175.0)

        #expect(sut.state.goalStartWeight == 5.0)
        #expect(sut.state.goalWeight == 25.0)
        #expect(sut.state.goalDelta == 15.0)
        #expect(sut.formatGoalProgress() == "75%")
    }

    @Test("calculateWeightlessGoal: missing goal settings returns without mutating state")
    func calculateWeightlessGoalWithoutGoalSettingsReturns() async throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            goalType: nil,
            goalWeight: nil,
            initialWeight: nil
        )

        try await sut.calculateWeightlessGoal(anchorWeight: 175.0)

        #expect(sut.state.goalWeight == 0)
        #expect(sut.state.goalProgress == 0)
    }

    @Test("validateGoalSettings: succeeds for a valid gain goal")
    func validateGoalSettingsSuccess() throws {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)

        try sut.validateGoalSettings()
    }

    @Test("validateGoalSettings: rejects missing goal settings and invalid gain targets")
    func validateGoalSettingsFailures() {
        let sutBundle = makeSUT(goalWeight: 1700)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService

        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 1700)
        do {
            try sut.validateGoalSettings()
            Issue.record("Expected invalid gain goal to throw")
        } catch { }

        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            goalType: nil,
            goalWeight: nil,
            initialWeight: nil
        )

        do {
            try sut.validateGoalSettings()
            Issue.record("Expected missing goal settings to throw")
        } catch { }
    }

    @Test("validateGoalSettings: without an active account throws noActiveAccount")
    func validateGoalSettingsWithoutActiveAccountThrows() {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = nil

        do {
            try sut.validateGoalSettings()
            Issue.record("Expected noActiveAccount to throw")
        } catch let error as DashboardError {
            guard case .noActiveAccount = error else {
                Issue.record("Expected noActiveAccount, got \(error)")
                return
            }
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("calculateGoalAnalytics: returns neutral analytics with formatted progress percentage")
    func calculateGoalAnalyticsReturnsProgress() {
        let sut = makeSUT(goalWeight: 2000).sut
        sut.state.goalProgress = 0.75

        let analytics = sut.calculateGoalAnalytics()

        #expect(analytics.currentTrend == .neutral)
        #expect(analytics.progressPercentage == 75.0)
        #expect(analytics.daysToGoal == nil)
        #expect(analytics.weeklyTarget == nil)
    }

    @Test("weight formatting helpers: convert and format weights for normal and weightless display")
    func weightFormattingHelpers() {
        let sutBundle = makeSUT(goalWeight: 2000)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 2000)

        #expect(sut.convertWeightToDisplay(1800) == 180.0)
        #expect(sut.formatWeightForDisplay(180.0, isWeightlessMode: false) == "180")
        #expect(sut.formatWeightForDisplay(5.25, isWeightlessMode: true) == "+5.3")
    }

    @Test("goal display helpers: expose unit text and period label")
    func goalDisplayHelpers() {
        let sutBundle = makeSUT(goalWeight: 2000, weightUnit: .kg)
        let sut = sutBundle.sut
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            weightUnit: .kg,
            goalWeight: 2000
        )

        #expect(sut.getUnitText() == "kg")
        #expect(sut.getWeightDisplayLabel(for: .month) == "month average")
    }

    @Test("hasEntriesButNoneInCurrentPeriod: returns true only when historical entries exist but the current period is empty")
    func hasEntriesButNoneInCurrentPeriod() {
        let sut = makeSUT(goalWeight: 2000).sut

        #expect(sut.hasEntriesButNoneInCurrentPeriod(
            continuousOperations: [DashboardTestFixtures.makeSummary()],
            visibleOperations: []
        ) == true)
        #expect(sut.hasEntriesButNoneInCurrentPeriod(
            continuousOperations: [],
            visibleOperations: []
        ) == false)
    }

    @Test("updateVisibleDataAfterScroll: triggers UI update and logs the visible average")
    func updateVisibleDataAfterScrollLogsAverage() {
        let sut = makeSUT(goalWeight: 2000).sut
        var triggerCalls = 0
        var loggedAverage: Double?

        sut.updateVisibleDataAfterScroll(
            visibleOperations: [
                DashboardTestFixtures.makeSummary(weight: 1800),
                DashboardTestFixtures.makeSummary(weight: 1820)
            ],
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            triggerUpdate: { triggerCalls += 1 },
            logAverage: { loggedAverage = $0 }
        )

        #expect(triggerCalls == 1)
        #expect(loggedAverage == 181.0)
    }

    private func makeSUT(
        goalWeight: Double?,
        weightUnit: WeightUnit = .lb
    ) -> DashboardGoalManagerTestsSUT {
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

        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            weightUnit: weightUnit,
            goalWeight: goalWeight
        )

        let sut = DashboardGoalManager()
        return DashboardGoalManagerTestsSUT(sut: sut, accountService: accountService, entryRepo: entryRepo)
    }
}
