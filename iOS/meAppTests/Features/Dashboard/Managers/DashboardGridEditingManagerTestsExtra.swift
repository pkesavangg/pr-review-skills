import Foundation
@testable import meApp
import Testing

@MainActor
extension DashboardGridEditingManagerTests {

    @Test("selectMetric, drag state, and bindings keep dashboard UI state synchronized")
    func selectionDragStateAndBindingsStaySynchronized() {
        let store = makeSUT().store
        configureStore(store, metrics: makeDefaultMetrics(), streaks: makeDefaultStreaks(), isEditMode: true)

        store.gridEditingManager.selectMetric(DashboardStrings.bmi)
        #expect(store.state.ui.selectedMetricLabel == DashboardStrings.bmi)
        store.gridEditingManager.selectMetric(DashboardStrings.bmi)
        #expect(store.state.ui.selectedMetricLabel == nil)

        let newMetric = DashboardTestFixtures.makeMetricItem(label: DashboardStrings.muscle)
        let newStreak = DashboardTestFixtures.makeMetricItem(label: "lb/month")
        store.gridEditingManager.metricsBinding.wrappedValue = [newMetric]
        store.gridEditingManager.streakItemsBinding.wrappedValue = [newStreak]
        store.gridEditingManager.draggingMetricBinding.wrappedValue = newMetric
        store.gridEditingManager.draggingStreakBinding.wrappedValue = newStreak
        store.gridEditingManager.dropHoverIdBinding.wrappedValue = "hover"

        #expect(metricLabels(in: store) == [DashboardStrings.muscle])
        #expect(streakLabels(in: store) == ["lb/month"])
        #expect(store.state.ui.draggingMetric?.label == DashboardStrings.muscle)
        #expect(store.state.ui.draggingStreak?.label == "lb/month")
        #expect(store.state.ui.dropHoverId == "hover")

        store.gridEditingManager.startDraggingGoalCard()
        store.gridEditingManager.updateDropTarget("goal")
        #expect(store.state.ui.isGoalCardBeingDragged == true)
        #expect(store.state.ui.dropHoverId == "goal")
    }

    @Test("reset drag and layout helpers clear drag state and create a new grid layout id")
    func resetDragAndLayoutHelpersClearTransientState() {
        let store = makeSUT().store
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
        let store = makeSUT().store
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
        let store = makeSUT().store
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
        let store = makeSUT().store
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
        let store = makeSUT().store
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

        // Same fire-and-forget race as saveChangesPersistsEditedGridState: give the removal Task
        // a generous ceiling (poll exits as soon as it lands) so a starved CI main actor can't
        // let it land AFTER cancelEdit and corrupt the restored state.
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 30_000_000_000) {
            store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }
        #expect(store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak))

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

        // toggleStreakRemoval applies its removal via a fire-and-forget Task. The removal MUST
        // land before updateGoalCardPosition(1): with removedStreaks still empty, that call
        // rounds the position down to the column grid ((1 / 2) * 2 == 0), the goal card saves at
        // index 0, and the progress-metrics assertion below fails with a reordered array — which
        // is exactly how this test failed on the congested CI main actor with the default 1s wait.
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 30_000_000_000) {
            store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak)
        }
        #expect(store.state.ui.removedStreaks.contains(DashboardStrings.longestStreak))

        store.gridEditingManager.updateGoalCardPosition(1)
        store.lifecycleManager.saveChanges()

        // The save chain runs through AccountService + mock repos asynchronously — pure mock work,
        // but it needs the main actor, which the full parallel suite starves on the CI runner.
        // 2s and then 5s both flaked on CI (passed locally). The poll exits as soon as the
        // condition holds, so a generous ceiling costs nothing on a green run.
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 30_000_000_000) {
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
        let store = makeSUT().store
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
