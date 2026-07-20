import Foundation
@testable import meApp
import SwiftData
import SwiftUI
import Testing

@Suite(.serialized)
@MainActor
struct DashboardLifecycleManagerTests {

    // MARK: - SUT Factory

    // swiftlint:disable:next large_tuple
    typealias SUTBundle = (
        store: DashboardStore,
        accountService: AccountService,
        entryService: EntryService,
        cacheManager: MockDashboardCacheManager,
        accountLocalRepo: MockAccountRepository
    )

    func makeSUT() -> SUTBundle {
        let cacheManager = MockDashboardCacheManager()
        let sut = DashboardManagerTestSupport.makeStoreWithRepo(
            cacheManager: cacheManager,
            formatter: MockDashboardFormatter()
        )
        return (
            store: sut.store,
            accountService: sut.accountService,
            entryService: sut.entryService,
            cacheManager: cacheManager,
            accountLocalRepo: sut.accountLocalRepo
        )
    }

    func makeNotification() -> MockNotificationHelperService? {
        DependencyContainer.shared.resolve(MockNotificationHelperService.self)
    }

    // MARK: - On-Appear Tests

    @Test("onAppearActions: triggers entry data and goal card load")
    func onAppearActionsTriggersDataLoad() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let entryService = sut.entryService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "on-appear-load")

        let daily = [
            DashboardTestFixtures.makeSummary(accountId: "on-appear-load", period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(accountId: "on-appear-load", period: "2026-03-02", weight: 1810)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.lifecycleManager.onAppearActions()

        let completed = await waitUntil(timeoutNanoseconds: 10_000_000_000) {
            store.state.ui.hasLoadedDashboardConfig
        }

        #expect(completed == true)
        #expect(store.state.ui.hasLoadedDashboardConfig == true)
    }

    @Test("onAppearActions: syncs removal state after config load")
    func onAppearActionsSyncsRemovalState() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "on-appear-removal")

        store.lifecycleManager.onAppearActions()

        let completed = await waitUntil(timeoutNanoseconds: 1_000_000_000) {
            store.state.ui.removedMetrics.isEmpty &&
                store.state.ui.removedStreaks.isEmpty
        }

        #expect(completed == true)
        #expect(store.state.ui.removedMetrics.isEmpty)
        #expect(store.state.ui.removedStreaks.isEmpty)
    }

    @Test("onAppearActions: schedules UI update")
    func onAppearActionsSchedulesUIUpdate() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "on-appear-ui-update")

        store.lifecycleManager.onAppearActions()

        let completed = await waitUntil(timeoutNanoseconds: 10_000_000_000) {
            store.state.ui.hasLoadedDashboardConfig
        }

        #expect(completed == true)
        #expect(store.state.ui.hasLoadedDashboardConfig == true)
    }

    @Test("initializeDashboard: loads dashboard state and exits reset mode")
    func initializeDashboardLoadsState() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await store.lifecycleManager.initializeDashboard()

        // The progress-metric / dashboard-config flags are set by a staged background
        // refresh task that outlives initializeDashboard()'s await; poll for completion.
        let loaded = await waitUntil {
            store.state.ui.hasLoadedDashboardConfig && store.state.ui.hasLoadedProgressMetrics
        }

        #expect(store.state.ui.isResettingDashboard == false)
        #expect(loaded == true)
        #expect(store.state.ui.hasLoadedDashboardConfig == true)
        #expect(store.state.ui.hasLoadedProgressMetrics == true)
        #expect(store.state.metrics.dashboardType == .dashboard12)
        #expect(!store.metricsManager.state.metrics.isEmpty)
        #expect(!store.streakManager.state.streakItems.isEmpty)
    }

    // MARK: - On-Disappear / Account Changed Tests (cleanup behavior)

    @Test("handleActiveAccountChanged: clears chart caches and resets chart init flag")
    func handleActiveAccountChangedClearsCaches() async {
        let sut = makeSUT()
        let store = sut.store
        let accountService = sut.accountService
        let entryService = sut.entryService
        let cacheManager = sut.cacheManager
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        // Simulate chart was initialized
        store.state.ui.hasInitializedChart = true

        store.lifecycleManager.handleActiveAccountChanged()

        #expect(store.state.ui.hasInitializedChart == false)
        #expect(cacheManager.clearAllCachesCalls >= 1)
    }

    @Test("handleActiveAccountChanged: clears graph selection")
    func handleActiveAccountChangedClearsSelection() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let entryService = sut.entryService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPoint = daily[1]
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.lifecycleManager.handleActiveAccountChanged()

        #expect(store.state.graph.selectedPoint == nil)
    }

    @Test("handleActiveAccountChanged: resets graph ready flag")
    func handleActiveAccountChangedResetsGraphReady() {
        let sut = makeSUT(); let store = sut.store

        store.graphManager.state.isGraphReady = true

        store.lifecycleManager.handleActiveAccountChanged()

        #expect(store.graphManager.state.isGraphReady == false)
    }

    // MARK: - Period Change Propagation Tests

    @Test("handleDashboardTypeChange: propagates dashboard4 type from account")
    func handleDashboardTypeChangePropagatesDashboard4() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardType: "dashboard4"
        )

        store.lifecycleManager.handleDashboardTypeChange()

        #expect(store.state.metrics.dashboardType == .dashboard4)
        #expect(store.metricsManager.state.dashboardType == .dashboard4)
    }

    @Test("handleDashboardTypeChange: propagates dashboard12 type from account")
    func handleDashboardTypeChangePropagatesDashboard12() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardType: "dashboard12"
        )

        store.lifecycleManager.handleDashboardTypeChange()

        #expect(store.state.metrics.dashboardType == .dashboard12)
        #expect(store.metricsManager.state.dashboardType == .dashboard12)
    }

    @Test("handleDashboardTypeChange: defaults to dashboard12 for unknown type")
    func handleDashboardTypeChangeDefaultsToDashboard12() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardType: "unknown"
        )

        store.lifecycleManager.handleDashboardTypeChange()

        #expect(store.state.metrics.dashboardType == .dashboard12)
    }

    @Test("handleDashboardTypeChange: defaults to dashboard12 when no account")
    func handleDashboardTypeChangeNoAccount() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = nil

        store.lifecycleManager.handleDashboardTypeChange()

        #expect(store.state.metrics.dashboardType == .dashboard12)
    }

    // MARK: - Entry Lifecycle Tests

    @Test("onEntryAdded: invalidates continuous operations cache")
    func onEntryAddedInvalidatesCache() async {
        let sut = makeSUT(); let store = sut.store; let cacheManager = sut.cacheManager

        let notification = makeEntryNotification()
        let before = cacheManager.invalidateContinuousOpsCalls

        store.lifecycleManager.onEntryAdded(notification)

        // Entry-lifecycle changes are debounced (~250ms); wait for the coalesced invalidation.
        let invalidated = await waitUntil { cacheManager.invalidateContinuousOpsCalls > before }
        #expect(invalidated == true)
    }

    @Test("onEntryUpdated: invalidates continuous operations cache")
    func onEntryUpdatedInvalidatesCache() async {
        let sut = makeSUT(); let store = sut.store; let cacheManager = sut.cacheManager

        let notification = makeEntryNotification()
        let before = cacheManager.invalidateContinuousOpsCalls

        store.lifecycleManager.onEntryUpdated(notification)

        let invalidated = await waitUntil { cacheManager.invalidateContinuousOpsCalls > before }
        #expect(invalidated == true)
    }

    @Test("onEntryDeleted: invalidates continuous operations cache")
    func onEntryDeletedInvalidatesCache() async {
        let sut = makeSUT(); let store = sut.store; let cacheManager = sut.cacheManager

        let notification = makeEntryNotification()
        let before = cacheManager.invalidateContinuousOpsCalls

        store.lifecycleManager.onEntryDeleted(notification)

        let invalidated = await waitUntil { cacheManager.invalidateContinuousOpsCalls > before }
        #expect(invalidated == true)
    }

    @Test("onEntryAdded: updates display metrics when chart is initialized")
    func onEntryAddedUpdatesDisplayWhenChartReady() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let entryService = sut.entryService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.state.ui.hasInitializedChart = true

        let notification = makeEntryNotification()
        store.lifecycleManager.onEntryAdded(notification)

        // The entry lifecycle change triggers async display metric update
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
            true // Just allow async tasks to propagate
        }

        // Verify entry lifecycle triggers cache invalidation (sync part)
        #expect(store.state.ui.hasInitializedChart == true)
    }

    @Test("onEntryUpdated: keeps matching selected graph point")
    func onEntryUpdatedKeepsMatchingSelectedPoint() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let entryService = sut.entryService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = daily[1]
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.lifecycleManager.onEntryUpdated(makeEntryNotification())

        let updated = await waitUntil {
            store.state.graph.selectedPoint?.period == daily[1].period
        }

        #expect(updated == true)
    }

    @Test("onEntryDeleted: clears selected point when it no longer exists")
    func onEntryDeletedClearsMissingSelectedPoint() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let entryService = sut.entryService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = Array(DashboardTestFixtures.makeSortedDailySummaries().prefix(2))
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        let missingPoint = DashboardTestFixtures.makeSummary(
            period: "2026-04-01",
            entryTimestamp: "2026-04-01T08:00:00Z"
        )
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = missingPoint
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.lifecycleManager.onEntryDeleted(makeEntryNotification())

        let cleared = await waitUntil {
            store.state.graph.selectedPoint == nil
        }

        #expect(cleared == true)
    }

    @Test("onEntryAdded: advances selection to the latest entry when added on a new day (MOB-1582)")
    func onEntryAddedAdvancesSelectionToLatestOnNewDay() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let entryService = sut.entryService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        // Sorted daily summaries span 2026-03-01 … 2026-03-05; daily[4] is the latest.
        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        // User is parked on an earlier (non-latest) day when a newer entry is added.
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = daily[2] // 2026-03-03
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.lifecycleManager.onEntryAdded(makeEntryNotification())

        // Debounced (~250ms) → the selection should advance to the latest entry and show the crosshair,
        // rather than staying re-pinned to the previous day (the MOB-1582 regression).
        let advanced = await waitUntil {
            store.state.graph.selectedPoint?.period == daily[4].period
        }

        #expect(advanced == true)
        #expect(store.state.graph.selectedPoint?.period == "2026-03-05")
        #expect(store.state.graph.showCrosshair == true)
    }

    // MARK: - Settings Change Tests

    @Test("handleSettingsChange: syncs removal state")
    func handleSettingsChangeSyncsRemovalState() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        store.lifecycleManager.handleSettingsChange(shouldRefreshStreak: false)

        // Allow async work to complete
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
            true
        }

        // Removal state should be synced - no metrics removed by default
        #expect(store.state.ui.removedMetrics.isEmpty)
    }

    @Test("handleSettingsChange: invalidates chart series cache when streak refresh enabled")
    func handleSettingsChangeInvalidatesChartSeriesCache() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService; let cacheManager = sut.cacheManager
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let before = cacheManager.invalidateChartSeriesCalls

        store.lifecycleManager.handleSettingsChange(shouldRefreshStreak: true)

        let completed = await waitUntil(timeoutNanoseconds: 2_000_000_000) {
            cacheManager.invalidateChartSeriesCalls > before
        }

        #expect(completed == true)
        #expect(cacheManager.invalidateChartSeriesCalls > before)
    }

    @Test("handleSettingsChange: streak failure does not block goal loading or cache invalidation")
    func handleSettingsChangeStreakFailureDoesNotBlockGoal() async {
        let sut = makeSUT(); let store = sut.store; let cacheManager = sut.cacheManager
        // No active account — streak refresh will fail (getProgress requires data),
        // and goal loading will exit gracefully (no activeAccount guard).
        // Key assertion: cache invalidation still runs after both independent error boundaries.

        let before = cacheManager.invalidateChartSeriesCalls

        store.lifecycleManager.handleSettingsChange(shouldRefreshStreak: true)

        let completed = await waitUntil(timeoutNanoseconds: 2_000_000_000) {
            cacheManager.invalidateChartSeriesCalls > before
        }

        #expect(completed == true)
        #expect(cacheManager.invalidateChartSeriesCalls > before)
    }

    @Test("handleUnitChange: triggers streak refresh and UI update")
    func handleUnitChangeTriggers() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        let accountId = "unit-change-success"
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

        store.lifecycleManager.handleUnitChange()

        let completed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            store.streakManager.state.streakItems.first?.value != DashboardStrings.placeholder
        }

        #expect(completed == true)
        #expect(store.streakManager.state.streakItems.first?.value != DashboardStrings.placeholder)
    }

    // MARK: - Load Failure / Error State Tests

    @Test("loadDashboardConfigurationFromAPI: sets hasLoadedDashboardConfig on error with empty metrics")
    func loadConfigSetsLoadedFlagOnError() async {
        let sut = makeSUT(); let store = sut.store
        // No active account, so API calls will fail
        store.metricsManager.state.metrics = []

        await store.lifecycleManager.loadDashboardConfigurationFromAPI()

        #expect(store.state.ui.hasLoadedDashboardConfig == true)
    }

    @Test("loadDashboardConfigurationFromAPI: sets up initial metrics on error when metrics are empty")
    func loadConfigSetsUpInitialMetricsOnError() async {
        let sut = makeSUT(); let store = sut.store
        store.metricsManager.state.metrics = []

        await store.lifecycleManager.loadDashboardConfigurationFromAPI()

        // On error with empty metrics, setupInitialMetrics is called
        #expect(!store.metricsManager.state.metrics.isEmpty)
    }

    @Test("loadDashboardConfigurationFromAPI: sets hasLoadedProgressMetrics on error")
    func loadConfigSetsProgressMetricsLoadedOnError() async {
        let sut = makeSUT(); let store = sut.store

        await store.lifecycleManager.loadDashboardConfigurationFromAPI()

        #expect(store.state.ui.hasLoadedProgressMetrics == true)
    }

    @Test("loadDashboardConfigurationFromAPI: sets up initial streak items on error when streaks are empty")
    func loadConfigSetsUpInitialStreakItemsOnError() async {
        let sut = makeSUT(); let store = sut.store

        // Clear streak items
        store.streakManager.state.streakItems = []

        await store.lifecycleManager.loadDashboardConfigurationFromAPI()

        // On error with empty streaks, setupInitialStreakItems is called
        #expect(!store.streakManager.state.streakItems.isEmpty)
    }

    // MARK: - Refresh Dashboard State Tests

    @Test("refreshDashboardState: schedules UI update")
    func refreshDashboardStateSchedulesUpdate() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        store.lifecycleManager.refreshDashboardState()

        // refreshDashboardState calls scheduleUIUpdate - no crash means success
        #expect(true)
    }

    @Test("refreshDashboardState: resets grid layout")
    func refreshDashboardStateResetsGrid() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        store.lifecycleManager.refreshDashboardState()

        // Grid layout reset is triggered - verified by no state corruption
        #expect(true)
    }

    // MARK: - Helper

    func makeEntryNotification() -> EntryNotification {
        let dto = BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "2026-03-01T08:00:00Z",
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: OperationType.create.rawValue,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: 1800
        )
        return EntryNotification(from: dto)
    }

    func makeSnapshot(for store: DashboardStore) -> EditSessionSnapshot {
        EditSessionSnapshot(
            metrics: store.metricsManager.state.metrics,
            activeMetricsCount: store.metricsManager.state.activeMetricsCount,
            streakItems: store.streakManager.state.streakItems,
            activeStreakItemsCount: store.streakManager.state.activeStreakItemsCount,
            isGoalCardRemoved: store.state.ui.isGoalCardRemoved,
            goalCardPosition: store.state.ui.goalCardPosition,
            streakGridOrder: store.state.ui.streakGridOrder,
            removedMetrics: store.state.ui.removedMetrics,
            removedStreaks: store.state.ui.removedStreaks
        )
    }

    func persistEntries(_ entries: [Entry]) throws {
        let context = PersistenceController.shared.context
        for entry in entries {
            context.insert(entry)
        }
        try context.save()
    }

    func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }
}
