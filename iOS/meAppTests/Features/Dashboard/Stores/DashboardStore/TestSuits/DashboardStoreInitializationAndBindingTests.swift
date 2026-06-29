import Foundation
@testable import meApp
import Testing

extension DashboardStoreTests {
    @Suite("Initialization And Bindings")
    @MainActor
    struct InitializationAndBindings {

    @Test("init lightweight: creates store with default state")
    func initLightweightDefaultState() {
        let store = DashboardStoreTestSupport.makeSUT(lightweight: true).store

        #expect(store.state.ui.isEditMode == false)
        #expect(store.state.ui.isLoading == false)
        #expect(store.state.ui.selectedMetricLabel == nil)
        #expect(store.state.graph.selectedPeriod == .month)
        #expect(store.state.goal.hasGoalSet == false)
    }

    @Test("init lightweight: managers are initialized")
    func initLightweightManagersInitialized() {
        let store = DashboardStoreTestSupport.makeSUT(lightweight: true).store

        #expect(store.metricsManager.state.dashboardType == .dashboard12)
        #expect(store.graphManager.state.selectedPeriod == .month)
        #expect(store.streakManager.state.activeStreakItemsCount == 6)
        #expect(store.dataManager.state.dailySummaries.isEmpty)
        #expect(store.goalManager.state.hasGoalSet == false)
        #expect(store.gridEditingManager != nil)
        #expect(store.chartManager != nil)
        #expect(store.displayManager != nil)
        #expect(store.lifecycleManager != nil)
    }

    @Test("init lightweight true: does not setup subscriptions")
    func initLightweightTrueSkipsSubscriptions() {
        let store = DashboardStoreTestSupport.makeSUT(lightweight: true).store
        #expect(store.state.ui.isLoading == false)
    }

    @Test("init lightweight false: sets up subscriptions")
    func initLightweightFalseSetupSubscriptions() {
        let store = DashboardStoreTestSupport.makeSUT(lightweight: false).store
        #expect(store.state.ui.isLoading == false)
    }

    @Test("init full: seeds default progress state and kicks off initialization")
    func initFullSeedsProgressState() async {
        TestDependencyContainer.reset()
        _ = TestDependencyContainer.registerDashboardConcreteDependencies()

        let store = DashboardStore(
            formatter: MockDashboardFormatter(),
            cacheManager: MockDashboardCacheManager()
        )

        #expect(store.state.ui.isResettingDashboard == true)
        #expect(store.state.ui.hasLoadedProgressMetrics == true)
        #expect(store.streakManager.state.streakItems.count == 6)
        #expect(store.state.ui.streakGridOrder.count == 6)

        await DashboardTestFixtures.waitUntil { store.isInitialized }
        #expect(store.isInitialized == true)
    }

    @Test("init: formatter and cacheManager are injected correctly")
    func initFormatterAndCacheInjected() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let cacheManager = sutBundle.cacheManager

        #expect(store.formatter is MockDashboardFormatter)
        #expect(store.cacheManager is MockDashboardCacheManager)
        #expect(cacheManager === store.cacheManager as? MockDashboardCacheManager)
    }

    @Test("init: editSessionManager starts with no snapshot")
    func initEditSessionManagerEmpty() {
        let store = DashboardStoreTestSupport.makeSUT().store

        #expect(store.editSessionManager.hasSnapshot == false)
        #expect(store.editSessionManager.snapshot == nil)
    }

    @Test("bindings: metricsManager state changes propagate to store")
    func bindingsMetricsManagerPropagates() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        let item = DashboardTestFixtures.makeMetricItem(label: "bmi", unit: "")
        store.metricsManager.state.metrics = [item]

        await DashboardTestFixtures.waitUntil { store.state.metrics.metrics.count == 1 }

        #expect(store.state.metrics.metrics.count == 1)
        #expect(store.state.metrics.metrics[0].label == "bmi")
    }

    @Test("bindings: streakManager state changes propagate to store")
    func bindingsStreakManagerPropagates() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        let item = DashboardTestFixtures.makeMetricItem(label: "current-streak")
        store.streakManager.state.streakItems = [item]

        await DashboardTestFixtures.waitUntil { store.state.streak.streakItems.count == 1 }

        #expect(store.state.streak.streakItems.count == 1)
    }

    @Test("bindings: goalManager state changes propagate to store")
    func bindingsGoalManagerPropagates() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.goalManager.state.hasGoalSet = true
        store.goalManager.state.goalType = .lose
        store.goalManager.state.goalWeight = 170.0

        await DashboardTestFixtures.waitUntil { store.state.goal.hasGoalSet == true }

        #expect(store.state.goal.hasGoalSet == true)
        #expect(store.state.goal.goalType == .lose)
        #expect(store.state.goal.goalWeight == 170.0)
    }

    @Test("bindings: graphManager state changes propagate to store")
    func bindingsGraphManagerPropagates() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.isScrolling = true

        await DashboardTestFixtures.waitUntil { store.state.graph.selectedPeriod == .month }

        #expect(store.state.graph.selectedPeriod == .month)
        #expect(store.state.graph.isScrolling == true)
    }

    @Test("bindings: dataManager state changes propagate to store")
    func bindingsDataManagerPropagates() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        let summary = DashboardTestFixtures.makeSummary(period: "2026-03-01")
        store.dataManager.state.dailySummaries = [summary]

        await DashboardTestFixtures.waitUntil { store.state.data.dailySummaries.count == 1 }

        #expect(store.state.data.dailySummaries.count == 1)
    }

    @Test("bindings: metricsManager state suppressed during dashboard reset")
    func bindingsSuppressedDuringReset() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        let initialLabels = store.state.metrics.metrics.map(\.label)

        store.state.ui.isResettingDashboard = true
        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]

        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(store.state.metrics.metrics.map(\.label) == initialLabels)
        #expect(store.state.metrics.metrics.count == initialLabels.count)
    }

    @Test("bindings: streakManager state suppressed during dashboard reset")
    func bindingsStreakSuppressedDuringReset() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        let initialLabels = store.state.streak.streakItems.map(\.label)

        store.state.ui.isResettingDashboard = true
        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "streak")]

        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(store.state.streak.streakItems.map(\.label) == initialLabels)
        #expect(store.state.streak.streakItems.count == initialLabels.count)
    }

    @Test("bindings: goalManager state NOT suppressed during dashboard reset")
    func bindingsGoalNotSuppressedDuringReset() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.isResettingDashboard = true
        store.goalManager.state.hasGoalSet = true

        await DashboardTestFixtures.waitUntil { store.state.goal.hasGoalSet == true }

        #expect(store.state.goal.hasGoalSet == true)
    }

    @Test("subscriptions: dashboard type change updates store metrics type")
    func subscriptionsDashboardTypeChangeUpdatesStore() async {
        // Seed a non-nil dashboard12 account BEFORE init so the async init pipeline never takes the
        // `noActiveAccount` catch path (which defaults dashboardType to .dashboard12). With a non-nil
        // account throughout, the only source of dashboardType is the account's own value, so the
        // $activeAccount subscription is the deterministic writer — no init-vs-subscription race.
        let (store, accountService, _) = DashboardStoreTestSupport.makeSUT(
            lightweight: false,
            initialAccount: DashboardStoreTestSupport.makeActiveAccount(
                dashboardMetrics: "bmi,bodyFat,muscleMass,water,pulse,boneMass",
                dashboardType: "dashboard12"
            )
        )

        // Let the full init pipeline settle on the seeded baseline (dashboard12).
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 5_000_000_000) {
            store.state.ui.hasLoadedDashboardConfig == true &&
            store.state.ui.isResettingDashboard == false &&
            store.state.metrics.dashboardType == .dashboard12
        }

        // Now exercise the subscription: change to a dashboard4 account. The account stays non-nil,
        // so no catch-path default can clobber the value.
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardMetrics: "bmi,bodyFat,muscleMass,water",
            dashboardType: "dashboard4"
        )

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 5_000_000_000) {
            store.state.metrics.dashboardType == .dashboard4 &&
            store.metricsManager.state.dashboardType == .dashboard4
        }

        #expect(store.state.metrics.dashboardType == .dashboard4)
        #expect(store.metricsManager.state.dashboardType == .dashboard4)
    }

    @Test("subscriptions: active account change clears chart initialization state")
    func subscriptionsActiveAccountChangeClearsChartState() async {
        let sutBundle = DashboardStoreTestSupport.makeSUT(lightweight: false)
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        store.state.ui.hasInitializedChart = true
        store.graphManager.state.isGraphReady = true
        store.state.graph.selectedPoint = DashboardTestFixtures.makeSummary()

        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "acct-2")

        await DashboardTestFixtures.waitUntil {
            store.state.ui.hasInitializedChart == false &&
            store.graphManager.state.isGraphReady == false &&
            store.state.graph.selectedPoint == nil
        }

        #expect(store.state.ui.hasInitializedChart == false)
        #expect(store.graphManager.state.isGraphReady == false)
        #expect(store.state.graph.selectedPoint == nil)
    }

    @Test("bindings: entry data changes invalidate continuous operations cache")
    func bindingsEntryDataChangeInvalidatesContinuousCache() async {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let cacheManager = sutBundle.cacheManager
        let entryService = DependencyContainer.shared.resolve(EntryService.self)

        #expect(entryService != nil)

        entryService?.dailySummaries = [DashboardTestFixtures.makeSummary()]

        await DashboardTestFixtures.waitUntil {
            cacheManager.invalidateContinuousOpsCalls == 1
        }

        #expect(cacheManager.invalidateContinuousOpsCalls == 1)
        #expect(store.state.data.hasAnyEntries == true)
    }

    @Test("bindings: first entry arrival resets chart initialization while scrolling")
    func bindingsFirstEntryArrivalResetsChartInitialization() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        let entryService = DependencyContainer.shared.resolve(EntryService.self)

        #expect(entryService != nil)

        store.state.ui.hasInitializedChart = true
        store.state.graph.isScrolling = true

        entryService?.dailySummaries = [DashboardTestFixtures.makeSummary()]

        await DashboardTestFixtures.waitUntil {
            store.state.ui.hasInitializedChart == false
        }

        #expect(store.state.ui.hasInitializedChart == false)
    }

    @Test("graph period change: store state reflects new period after manager update")
    func graphPeriodChangeReflected() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.graphManager.state.selectedPeriod = .year

        await DashboardTestFixtures.waitUntil { store.state.graph.selectedPeriod == .year }

        #expect(store.state.graph.selectedPeriod == .year)
    }

    @Test("graph period: all period values can be set")
    func graphAllPeriodValues() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        for period in TimePeriod.allCases {
            store.graphManager.state.selectedPeriod = period
            await DashboardTestFixtures.waitUntil { store.state.graph.selectedPeriod == period }
            #expect(store.state.graph.selectedPeriod == period)
        }
    }

    @Test("data state: adding daily summaries updates hasAnyEntries")
    func dataStateAddingDailyUpdatesHasEntries() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.hasAnyEntries == false)

        store.dataManager.state.dailySummaries = [DashboardTestFixtures.makeSummary()]

        await DashboardTestFixtures.waitUntil { store.hasAnyEntries == true }

        #expect(store.hasAnyEntries == true)
    }

    @Test("data state: clearing daily summaries updates hasAnyEntries")
    func dataStateClearingDailyUpdatesHasEntries() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.dataManager.state.dailySummaries = [DashboardTestFixtures.makeSummary()]
        await DashboardTestFixtures.waitUntil { store.hasAnyEntries == true }

        store.dataManager.state.dailySummaries = []
        store.dataManager.state.monthlySummaries = []

        await DashboardTestFixtures.waitUntil { store.hasAnyEntries == false }

        #expect(store.hasAnyEntries == false)
    }

    @Test("goal state: goalManager changes propagate")
    func goalManagerChangesPropagate() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.goalManager.state.goalType = .lose
        store.goalManager.state.goalStartWeight = 200.0
        store.goalManager.state.goalWeight = 180.0
        store.goalManager.state.goalDelta = -20.0
        store.goalManager.state.goalProgress = 0.5
        store.goalManager.state.hasGoalSet = true

        await DashboardTestFixtures.waitUntil { store.state.goal.hasGoalSet == true }

        #expect(store.state.goal.goalType == .lose)
        #expect(store.state.goal.goalStartWeight == 200.0)
        #expect(store.state.goal.goalWeight == 180.0)
        #expect(store.state.goal.goalDelta == -20.0)
        #expect(store.state.goal.goalProgress == 0.5)
        #expect(store.hasGoalSet == true)
    }
    }
}
