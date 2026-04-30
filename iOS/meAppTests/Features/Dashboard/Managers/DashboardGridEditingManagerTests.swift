import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardGridEditingManagerTests {

    private func makeSUT() -> DashboardStoreTestSupport.SUT {
        DashboardStoreTestSupport.makeSUT()
    }

    private func makeSaveSUT(activeAccount: AccountSnapshot) async throws -> (store: DashboardStore, apiRepo: MockAccountAPIRepository) {
        TestDependencyContainer.reset()

        let apiRepo = MockAccountAPIRepository()
        let localRepo = MockAccountRepository()
        let accountService = AccountService(
            apiRepo: apiRepo,
            localRepo: localRepo,
            integrationApiRepo: MockIntegrationAPIRepository(),
            networkMonitor: MockNetworkMonitor(isConnected: true),
            performInitialLoad: false
        )
        let loggerService = LoggerService()
        let scaleService = ScaleService(
            accountService: accountService,
            apiRepository: MockScaleRepositoryAPI(),
            localRepository: MockScaleRepository()
        )
        let entryService = EntryService(
            accountService: accountService,
            localRepo: MockEntryRepository(),
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: MockEntryRepositoryAPI()
        )

        DependencyContainer.shared.register(loggerService as LoggerService)
        DependencyContainer.shared.register(accountService as AccountService)
        DependencyContainer.shared.register(scaleService as ScaleService)
        DependencyContainer.shared.register(entryService as EntryService)

        let store = DashboardStore(
            lightweight: true,
            formatter: MockDashboardFormatter(),
            cacheManager: MockDashboardCacheManager()
        )

        // Pin the concrete service instances used by the real save path so later
        // container mutations from other suites cannot affect this test.
        store.accountService = accountService
        store.logger = loggerService
        store.lifecycleManager.accountService = accountService
        store.lifecycleManager.logger = loggerService

        accountService.activeAccount = activeAccount

        // Prime lazy @Injector resolution now, while this test's custom dependencies are still registered.
        store.metricsManager.state.metrics = makeDefaultMetrics()
        store.metricsManager.state.activeMetricsCount = makeDefaultMetrics().count
        try await store.metricsManager.saveMetricsToAPI()
        try await store.syncCoordinator.saveProgressMetricsToAPI(
            streakItems: makeDefaultStreaks(),
            streakOrder: [],
            goalCardPosition: 0,
            isGoalCardRemoved: false,
            removedStreaks: []
        ) { _ in }
        apiRepo.resetCapturedMetrics()

        return (store, apiRepo)
    }

    private func configureStore(
        _ store: DashboardStore,
        metrics: [MetricItem],
        activeMetricsCount: Int? = nil,
        streaks: [MetricItem],
        activeStreakItemsCount: Int? = nil,
        removedMetrics: Set<String> = [],
        removedStreaks: Set<String> = [],
        goalCardPosition: Int = 0,
        isGoalCardRemoved: Bool = false,
        isEditMode: Bool = false,
        dashboardType: DashboardType = .dashboard12
    ) {
        store.state.ui.hasLoadedDashboardConfig = true
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.isEditMode = isEditMode
        store.state.ui.goalCardPosition = goalCardPosition
        store.state.ui.isGoalCardRemoved = isGoalCardRemoved
        store.state.ui.removedMetrics = removedMetrics
        store.state.ui.removedStreaks = removedStreaks

        store.metricsManager.state.dashboardType = dashboardType
        store.metricsManager.state.metrics = metrics
        store.metricsManager.state.activeMetricsCount = activeMetricsCount ?? metrics.count

        store.streakManager.state.streakItems = streaks
        store.streakManager.state.activeStreakItemsCount = activeStreakItemsCount ?? streaks.count
        store.state.ui.streakGridOrder = streaks.map(\.id.uuidString)
    }

    private func metricLabels(in store: DashboardStore) -> [String] {
        store.metricsManager.state.metrics.map(\.label)
    }

    private func streakLabels(in store: DashboardStore) -> [String] {
        store.streakManager.state.streakItems.map(\.label)
    }

    private func makeDefaultMetrics() -> [MetricItem] {
        [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bodyFat),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.water)
        ]
    }

    private func makeDefaultStreaks() -> [MetricItem] {
        [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lbs/week")
        ]
    }

    @Test("toggleEditMode enters edit mode and snapshots the current dashboard state")
    func toggleEditModeEntersEditMode() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks())

        store.gridEditingManager.toggleEditMode()

        #expect(store.state.ui.isEditMode == true)
        #expect(store.editSessionManager.hasSnapshot == true)
        #expect(store.editSessionManager.snapshot?.metrics.map(\.label) == metricLabels(in: store))
        #expect(store.editSessionManager.snapshot?.streakGridOrder == store.state.ui.streakGridOrder)
    }

    @Test("regenerateStreakGridOrderAfterRefresh preserves label order across refreshed ids and appends new items")
    func regenerateStreakGridOrderAfterRefreshPreservesVisibleOrder() {
        let (store, _, _) = makeSUT()
        let oldStreaks = makeDefaultStreaks()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: oldStreaks)

        let oldOrder = [oldStreaks[1].id.uuidString, oldStreaks[0].id.uuidString]
        let refreshed = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lbs/month")
        ]
        store.streakManager.state.streakItems = refreshed

        store.gridEditingManager.regenerateStreakGridOrderAfterRefresh(
            oldStreakItems: oldStreaks,
            oldOrder: oldOrder
        )

        #expect(store.state.ui.streakGridOrder == [
            refreshed[1].id.uuidString,
            refreshed[0].id.uuidString,
            refreshed[2].id.uuidString
        ])
    }

    @Test("regenerateStreakGridOrderAfterRefresh falls back to default order when no prior order exists")
    func regenerateStreakGridOrderAfterRefreshFallsBackToDefaults() {
        let (store, _, _) = makeSUT()
        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lbs/week"),
            DashboardTestFixtures.makeMetricItem(label: "lbs/month")
        ]
        configureStore(store, metrics: makeDefaultMetrics(), streaks: streaks)
        store.state.ui.streakGridOrder = []

        store.gridEditingManager.regenerateStreakGridOrderAfterRefresh(
            oldStreakItems: streaks,
            oldOrder: []
        )

        #expect(store.state.ui.streakGridOrder == [
            streaks[0].id.uuidString,
            streaks[1].id.uuidString,
            streaks[2].id.uuidString,
            streaks[3].id.uuidString
        ])
    }

    @Test("loadProgressMetricsFromAccount defaults when there is no active account")
    func loadProgressMetricsFromAccountWithoutAccountUsesDefaults() async {
        let (store, _, _) = makeSUT()
        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lbs/week"),
            DashboardTestFixtures.makeMetricItem(label: "lbs/month"),
            DashboardTestFixtures.makeMetricItem(label: "lbs/year"),
            DashboardTestFixtures.makeMetricItem(label: "lbs/total")
        ]
        configureStore(store, metrics: makeDefaultMetrics(), streaks: streaks)
        store.accountService.activeAccount = nil
        store.state.ui.streakGridOrder = []
        store.state.ui.removedStreaks = []

        await store.gridEditingManager.loadProgressMetricsFromAccount()

        #expect(store.state.ui.isGoalCardRemoved == false)
        #expect(store.state.ui.goalCardPosition == 0)
        #expect(store.state.ui.streakGridOrder == streaks.map(\.id.uuidString))
        #expect(store.state.ui.removedStreaks.isEmpty)
    }

    @Test("loadProgressMetricsFromAccount marks all progress metrics removed when configured empty after initial load")
    func loadProgressMetricsFromAccountHandlesAllRemovedState() async {
        let (store, accountService, cacheManager) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            id: "progress-empty",
            progressMetrics: ""
        )
        accountService.activeAccount = activeAccount

        let streaks = makeDefaultStreaks()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: streaks)
        store.state.ui.streakGridOrder = [streaks[0].id.uuidString]
        cacheManager.setBool(true, forKey: "dashboard.allProgressMetricsRemoved")

        await store.gridEditingManager.loadProgressMetricsFromAccount()

        #expect(store.state.ui.isGoalCardRemoved == true)
        #expect(store.state.ui.goalCardPosition == 0)
        #expect(store.state.ui.removedStreaks == Set(streaks.map(\.label)))
        #expect(store.streakManager.state.activeStreakItemsCount == 0)
    }

    @Test("loadProgressMetricsFromAccount restores saved goal card position, streak order, and removals")
    func loadProgressMetricsFromAccountRestoresSavedOrder() async {
        let (store, accountService, _) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            id: "progress-saved",
            progressMetrics: "currentStreak,goal,weeklyChange"
        )
        accountService.activeAccount = activeAccount

        let streaks = makeDefaultStreaks()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: streaks)
        store.state.ui.streakGridOrder = [UUID().uuidString]
        store.state.ui.removedStreaks = ["placeholder"]

        await store.gridEditingManager.loadProgressMetricsFromAccount()

        #expect(store.state.ui.goalCardPosition == 1)
        #expect(store.state.ui.isGoalCardRemoved == false)
        #expect(store.state.ui.streakGridOrder == [streaks[0].id.uuidString, streaks[2].id.uuidString])
        #expect(store.state.ui.removedStreaks == Set([DashboardStrings.longestStreak]))
        #expect(store.streakManager.state.activeStreakItemsCount == 2)
    }

    @Test("resetProgressMetricsToDefaults restores the default goal and streak ordering")
    func resetProgressMetricsToDefaultsRestoresDefaultOrder() async {
        let (store, _, _) = makeSUT()
        let streaks = makeDefaultStreaks()
        configureStore(
            store,
            metrics: makeDefaultMetrics(),
            streaks: streaks,
            removedStreaks: [DashboardStrings.longestStreak],
            goalCardPosition: 2,
            isGoalCardRemoved: true,
            isEditMode: true
        )

        await store.gridEditingManager.resetProgressMetricsToDefaults()

        #expect(store.state.ui.goalCardPosition == 0)
        #expect(store.state.ui.isGoalCardRemoved == false)
        #expect(store.state.ui.streakGridOrder == streaks.map(\.id.uuidString))
        #expect(store.state.ui.removedStreaks.isEmpty)
    }

    @Test("sync removal helpers derive removed metric and streak labels from manager counts")
    func syncRemovalHelpersReflectManagerState() async {
        let (store, _, _) = makeSUT()
        let metrics = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bodyFat),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.water)
        ]
        let streaks = makeDefaultStreaks()
        configureStore(
            store,
            metrics: metrics,
            activeMetricsCount: 1,
            streaks: streaks,
            activeStreakItemsCount: 2
        )

        store.gridEditingManager.syncRemovalStateFromMetricsManager()
        store.gridEditingManager.syncRemovalStateFromStreakManager()

        #expect(store.state.ui.removedMetrics == Set([DashboardStrings.bodyFat, DashboardStrings.water]))
        #expect(store.state.ui.removedStreaks == Set(["lbs/week"]))

        store.metricsManager.state.activeMetricsCount = 2
        store.gridEditingManager.debouncedSyncRemovalState()
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
            store.state.ui.removedMetrics == Set([DashboardStrings.water])
        }

        store.streakManager.state.streakItems = []
        store.state.ui.removedStreaks = [DashboardStrings.longestStreak]
        store.gridEditingManager.syncRemovalStateFromStreakManager()
        #expect(store.state.ui.removedStreaks.isEmpty)
    }

    @Test("moveMetric reorders only the active metrics and leaves removed metrics at the end")
    func moveMetricReordersVisibleMetrics() {
        let (store, _, _) = makeSUT()
        let metrics = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.water),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bodyFat),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.muscle)
        ]
        configureStore(
            store,
            metrics: metrics,
            activeMetricsCount: 3,
            streaks: makeDefaultStreaks(),
            removedMetrics: [DashboardStrings.muscle],
            isEditMode: true
        )

        store.gridEditingManager.moveMetric(from: 0, to: 2)

        #expect(metricLabels(in: store) == [
            DashboardStrings.water,
            DashboardStrings.bodyFat,
            DashboardStrings.bmi,
            DashboardStrings.muscle
        ])
        #expect(store.metricsManager.state.activeMetricsCount == 3)
        #expect(store.gridEditingManager.isMetricRemovedInReorderedArray(at: 3) == true)
    }

    @Test("reordered-array toggle helpers update metric and streak removal state")
    func reorderedArrayToggleHelpersUpdateRemovalState() async {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.toggleMetricRemovalInReorderedArray(at: 1)
        await DashboardTestFixtures.waitUntil {
            store.state.ui.removedMetrics.contains(DashboardStrings.bodyFat)
        }
        #expect(store.gridEditingManager.isMetricRemovedInReorderedArray(at: 2) == true)

        store.gridEditingManager.toggleStreakRemovalInReorderedArray(at: 1)
        await DashboardTestFixtures.waitUntil {
            store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }
        #expect(store.gridEditingManager.isStreakRemovedInReorderedArray(at: 2) == true)
    }

    @Test("reorderStreakItems updates the underlying streak order")
    func reorderStreakItemsUpdatesState() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.reorderStreakItems(from: IndexSet(integer: 0), to: 3)

        #expect(streakLabels(in: store) == [
            DashboardStrings.longestStreak,
            "lbs/week",
            DashboardStrings.currentStreak
        ])
    }

    @Test("toggleMetricRemoval removes and restores a metric while keeping removal queries in sync")
    func toggleMetricRemovalRemovesAndRestoresMetric() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.toggleMetricRemoval(DashboardStrings.water)

        #expect(metricLabels(in: store) == [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.water
        ])
        #expect(store.metricsManager.state.activeMetricsCount == 2)
        #expect(store.state.ui.removedMetrics == Set([DashboardStrings.water]))
        #expect(store.gridEditingManager.isMetricRemoved(DashboardStrings.water) == true)
        #expect(store.gridEditingManager.isMetricRemovedInReorderedArray(at: 2) == true)

        store.gridEditingManager.toggleMetricRemoval(DashboardStrings.water)

        #expect(store.metricsManager.state.activeMetricsCount == 3)
        #expect(store.state.ui.removedMetrics.isEmpty)
        #expect(store.gridEditingManager.isMetricRemoved(DashboardStrings.water) == false)
    }

    @Test("toggleStreakRemoval removes and restores streak visibility for UIKit callers")
    func toggleStreakRemovalRemovesAndRestoresStreak() async {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.toggleStreakRemoval(DashboardStrings.longestStreak)
        await DashboardTestFixtures.waitUntil {
            store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }

        #expect(store.gridEditingManager.isStreakRemoved(DashboardStrings.longestStreak) == true)
        #expect(store.streakManager.state.activeStreakItemsCount == 2)

        store.gridEditingManager.toggleStreakRemoval(DashboardStrings.longestStreak)
        await DashboardTestFixtures.waitUntil {
            !store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }

        #expect(store.gridEditingManager.isStreakRemoved(DashboardStrings.longestStreak) == false)
        #expect(store.streakManager.state.activeStreakItemsCount == 3)
    }

    @Test("toggleGoalCardRemoval and goal position updates handle remove, restore, and clamping")
    func goalCardRemovalAndPositionUpdatesStayConsistent() {
        let (store, _, _) = makeSUT()
        configureStore(
            store,
            metrics: makeDefaultMetrics(),
            streaks: makeDefaultStreaks(),
            removedStreaks: [DashboardStrings.longestStreak],
            isEditMode: true
        )

        store.gridEditingManager.toggleGoalCardRemoval()
        #expect(store.state.ui.isGoalCardRemoved == true)

        store.gridEditingManager.toggleGoalCardRemoval()
        #expect(store.state.ui.isGoalCardRemoved == false)

        store.gridEditingManager.updateGoalCardPosition(99)
        #expect(store.state.ui.goalCardPosition == 3)

        store.state.ui.goalCardPosition = -7
        store.gridEditingManager.validateGoalCardPosition()
        #expect(store.state.ui.goalCardPosition == 0)
    }

    @Test("selectMetric, drag state, and bindings keep dashboard UI state synchronized")
    func selectionDragStateAndBindingsStaySynchronized() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.selectMetric(DashboardStrings.bmi)
        #expect(store.state.ui.selectedMetricLabel == DashboardStrings.bmi)
        store.gridEditingManager.selectMetric(DashboardStrings.bmi)
        #expect(store.state.ui.selectedMetricLabel == nil)

        let newMetric = DashboardTestFixtures.makeMetricItem(label: DashboardStrings.muscle)
        let newStreak = DashboardTestFixtures.makeMetricItem(label: "lbs/month")
        store.gridEditingManager.metricsBinding.wrappedValue = [newMetric]
        store.gridEditingManager.streakItemsBinding.wrappedValue = [newStreak]
        store.gridEditingManager.draggingMetricBinding.wrappedValue = newMetric
        store.gridEditingManager.draggingStreakBinding.wrappedValue = newStreak
        store.gridEditingManager.dropHoverIdBinding.wrappedValue = "hover"

        #expect(metricLabels(in: store) == [DashboardStrings.muscle])
        #expect(streakLabels(in: store) == ["lbs/month"])
        #expect(store.state.ui.draggingMetric?.label == DashboardStrings.muscle)
        #expect(store.state.ui.draggingStreak?.label == "lbs/month")
        #expect(store.state.ui.dropHoverId == "hover")

        store.gridEditingManager.startDraggingGoalCard()
        store.gridEditingManager.updateDropTarget("goal")
        #expect(store.state.ui.isGoalCardBeingDragged == true)
        #expect(store.state.ui.dropHoverId == "goal")
    }

    @Test("reset drag and layout helpers clear drag state and create a new grid layout id")
    func resetDragAndLayoutHelpersClearTransientState() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        let metric = DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi)
        let streak = DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak)
        let originalLayoutId = store.state.ui.gridLayoutId

        store.gridEditingManager.startDraggingMetric(metric)
        store.gridEditingManager.startDraggingStreak(streak)
        store.gridEditingManager.startDraggingGoalCard()
        store.gridEditingManager.updateDropTarget("hover")
        store.gridEditingManager.resetDragState()

        #expect(store.state.ui.draggingMetric == nil)
        #expect(store.state.ui.draggingStreak == nil)
        #expect(store.state.ui.isGoalCardBeingDragged == false)
        #expect(store.state.ui.dropHoverId == nil)

        store.gridEditingManager.startDraggingMetric(metric)
        store.gridEditingManager.startDraggingStreak(streak)
        store.gridEditingManager.startDraggingGoalCard()
        store.gridEditingManager.updateDropTarget("hover")
        store.gridEditingManager.resetGridLayout()

        #expect(store.state.ui.gridLayoutId != originalLayoutId)
        #expect(store.state.ui.draggingMetric == nil)
        #expect(store.state.ui.draggingStreak == nil)
        #expect(store.state.ui.isGoalCardBeingDragged == false)
        #expect(store.state.ui.dropHoverId == nil)

        store.gridEditingManager.restartWiggleAnimations()
        #expect(store.state.ui.draggingMetric == nil)
        #expect(store.state.ui.draggingStreak == nil)
    }

    @Test("drag end helpers clear transient drag state")
    func dragEndHelpersClearTransientState() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.startDraggingMetric(DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi))
        store.gridEditingManager.startDraggingStreak(DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak))
        store.gridEditingManager.startDraggingGoalCard()
        store.gridEditingManager.updateDropTarget("hover")
        store.gridEditingManager.handleMetricDragEnd()

        #expect(store.state.ui.draggingMetric == nil)
        #expect(store.state.ui.draggingStreak == nil)
        #expect(store.state.ui.isGoalCardBeingDragged == false)
        #expect(store.state.ui.dropHoverId == nil)

        store.gridEditingManager.startDraggingStreak(DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak))
        store.gridEditingManager.updateDropTarget("hover")
        store.gridEditingManager.handleStreakDragEnd()

        #expect(store.state.ui.draggingStreak == nil)
        #expect(store.state.ui.dropHoverId == nil)
    }

    @Test("toggleEditMode while already editing resets the edit session through the store")
    func toggleEditModeWhileEditingResetsEditSession() {
        let (store, _, _) = makeSUT()
        let metrics = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bodyFat),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.muscle),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.water)
        ]
        configureStore(
            store,
            metrics: metrics,
            streaks: makeDefaultStreaks(),
            isEditMode: false,
            dashboardType: .dashboard4
        )

        store.gridEditingManager.toggleEditMode()
        store.state.ui.selectedMetricLabel = DashboardStrings.water
        store.state.ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "dragging")
        store.metricsManager.state.metrics = [metrics[3], metrics[0], metrics[1], metrics[2]]

        store.gridEditingManager.toggleEditMode()

        #expect(store.state.ui.isEditMode == true)
        #expect(store.state.ui.selectedMetricLabel == nil)
        #expect(store.state.ui.draggingMetric == nil)
        #expect(metricLabels(in: store) == [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.muscle,
            DashboardStrings.water
        ])
        #expect(store.editSessionManager.hasSnapshot == true)
    }

    @Test("reorderMetrics and invalid moveMetric inputs leave state consistent")
    func reorderMetricsAndInvalidMoveInputsStaySafe() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.reorderMetrics(from: IndexSet(integer: 0), to: 3)
        #expect(metricLabels(in: store) == [
            DashboardStrings.bodyFat,
            DashboardStrings.water,
            DashboardStrings.bmi
        ])

        let beforeInvalidMove = metricLabels(in: store)
        store.gridEditingManager.moveMetric(from: -1, to: 1)
        store.gridEditingManager.moveMetric(from: 0, to: 99)
        store.gridEditingManager.moveMetric(from: 1, to: 1)

        #expect(metricLabels(in: store) == beforeInvalidMove)
    }

    @Test("cancelEdit restores reordered metrics, streaks, removals, goal card state, and grid order")
    func cancelEditRestoresSnapshotAfterGridEdits() async {
        let (store, _, _) = makeSUT()
        let metrics = makeDefaultMetrics()
        let streaks = makeDefaultStreaks()
        configureStore(
            store,
            metrics: metrics,
            streaks: streaks,
            goalCardPosition: 1
        )
        let originalMetricLabels = metricLabels(in: store)
        let originalStreakLabels = streakLabels(in: store)
        let originalStreakOrder = store.state.ui.streakGridOrder

        store.gridEditingManager.toggleEditMode()
        store.gridEditingManager.moveMetric(from: 0, to: 2)
        store.gridEditingManager.toggleMetricRemoval(DashboardStrings.water)
        store.gridEditingManager.reorderStreakItems(from: IndexSet(integer: 0), to: 3)
        store.state.ui.streakGridOrder = store.streakManager.state.streakItems.map(\.id.uuidString)
        store.gridEditingManager.toggleGoalCardRemoval()
        store.gridEditingManager.updateGoalCardPosition(2)
        store.gridEditingManager.toggleStreakRemoval(DashboardStrings.longestStreak)

        await DashboardTestFixtures.waitUntil {
            store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }

        store.cancelEdit()

        #expect(store.state.ui.isEditMode == false)
        #expect(store.editSessionManager.hasSnapshot == false)
        #expect(metricLabels(in: store) == originalMetricLabels)
        #expect(streakLabels(in: store) == originalStreakLabels)
        #expect(store.metricsManager.state.activeMetricsCount == metrics.count)
        #expect(store.streakManager.state.activeStreakItemsCount == streaks.count)
        #expect(store.state.ui.removedMetrics.isEmpty)
        #expect(store.state.ui.removedStreaks.isEmpty)
        #expect(store.state.ui.isGoalCardRemoved == false)
        #expect(store.state.ui.goalCardPosition == 1)
        #expect(store.state.ui.streakGridOrder == originalStreakOrder)
    }

    @Test("saveChanges persists metric and progress order changes and clears edit state")
    func saveChangesPersistsEditedGridState() async throws {
        UserDefaults.standard.removeObject(forKey: "dashboard.allProgressMetricsRemoved") // swiftlint:disable:this no_direct_userdefaults

        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "dashboard-grid-save")
        let (store, apiRepo) = try await makeSaveSUT(activeAccount: activeAccount)

        let metrics = makeDefaultMetrics()
        let streaks = makeDefaultStreaks()
        configureStore(store, metrics: metrics, streaks: streaks)

        store.gridEditingManager.toggleEditMode()
        store.gridEditingManager.moveMetric(from: 2, to: 0)
        store.gridEditingManager.toggleMetricRemoval(DashboardStrings.bodyFat)
        store.gridEditingManager.reorderStreakItems(from: IndexSet(integer: 2), to: 0)
        store.state.ui.streakGridOrder = store.streakManager.state.streakItems.map(\.id.uuidString)
        store.gridEditingManager.toggleStreakRemoval(DashboardStrings.longestStreak)

        await DashboardTestFixtures.waitUntil {
            store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }

        store.gridEditingManager.updateGoalCardPosition(1)
        store.lifecycleManager.saveChanges()

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 2_000_000_000) {
            store.state.ui.isEditMode == false &&
                store.editSessionManager.hasSnapshot == false &&
                apiRepo.lastPatchDashboardMetrics == ["water", "bmi"] &&
                apiRepo.lastPatchProgressMetrics == ["weeklyChange", "goal", "currentStreak"]
        }

        #expect(store.state.ui.isEditMode == false)
        #expect(store.editSessionManager.hasSnapshot == false)
        #expect(apiRepo.lastPatchDashboardMetrics == ["water", "bmi"])
        #expect(apiRepo.lastPatchProgressMetrics == ["weeklyChange", "goal", "currentStreak"])
    }

    @Test("empty and single-item metric or streak grids handle edit operations without changing state")
    func emptyAndSingleItemOperationsAreSafe() {
        let (store, _, _) = makeSUT()
        configureStore(store, metrics: [], activeMetricsCount: 0, streaks: [], activeStreakItemsCount: 0, isEditMode: true)

        store.gridEditingManager.moveMetric(from: 0, to: 1)
        store.gridEditingManager.toggleMetricRemoval("missing")
        store.gridEditingManager.toggleStreakRemoval("missing")
        store.gridEditingManager.validateGoalCardPosition()

        #expect(store.metricsManager.state.metrics.isEmpty)
        #expect(store.streakManager.state.streakItems.isEmpty)
        #expect(store.gridEditingManager.isMetricRemovedInReorderedArray(at: 0) == false)
        #expect(store.gridEditingManager.isStreakRemovedInReorderedArray(at: 0) == false)

        let singleMetric = [DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bmi)]
        let singleStreak = [DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak)]
        configureStore(store, metrics: singleMetric, streaks: singleStreak, isEditMode: true)

        store.gridEditingManager.moveMetric(from: 0, to: 0)
        store.gridEditingManager.reorderStreakItems(from: IndexSet(integer: 0), to: 1)

        #expect(metricLabels(in: store) == [DashboardStrings.bmi])
        #expect(streakLabels(in: store) == [DashboardStrings.currentStreak])
    }
}
