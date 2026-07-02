import Foundation
@testable import meApp
import Testing

extension DashboardStoreTests {
    @Suite("Edit Session")
    @MainActor
    struct EditSession {

    @Test("beginEdit: takes snapshot of current state")
    func beginEditTakesSnapshot() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.metricsManager.state.activeMetricsCount = 4
        store.state.ui.isGoalCardRemoved = false

        store.beginEdit()

        #expect(store.editSessionManager.hasSnapshot == true)
        let snapshot = store.editSessionManager.snapshot
        #expect(snapshot?.metrics.count == 1)
        #expect(snapshot?.metrics[0].label == "bmi")
        #expect(snapshot?.activeMetricsCount == 4)
        #expect(snapshot?.isGoalCardRemoved == false)
    }

    @Test("beginEdit: does not overwrite existing snapshot")
    func beginEditDoesNotOverwrite() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.beginEdit()

        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "bmi"),
            DashboardTestFixtures.makeMetricItem(label: "water")
        ]
        store.beginEdit()

        #expect(store.editSessionManager.snapshot?.metrics.count == 1)
    }

    @Test("cancelEdit: restores state from snapshot")
    func cancelEditRestoresSnapshot() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.metricsManager.state.activeMetricsCount = 4
        store.state.ui.isGoalCardRemoved = false
        store.state.ui.isEditMode = true

        store.beginEdit()

        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "water"),
            DashboardTestFixtures.makeMetricItem(label: "bmi")
        ]
        store.metricsManager.state.activeMetricsCount = 2
        store.state.ui.isGoalCardRemoved = true

        store.cancelEdit()

        #expect(store.metricsManager.state.metrics.count == 1)
        #expect(store.metricsManager.state.metrics[0].label == "bmi")
        #expect(store.metricsManager.state.activeMetricsCount == 4)
        #expect(store.state.ui.isGoalCardRemoved == false)
    }

    @Test("cancelEdit: clears edit mode and selection state")
    func cancelEditClearsEditState() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.isEditMode = true
        store.state.ui.selectedMetricLabel = "bmi"
        store.state.ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "drag")
        store.state.ui.draggingStreak = DashboardTestFixtures.makeMetricItem(label: "streak")
        store.state.ui.dropHoverId = "hover"

        store.beginEdit()
        store.cancelEdit()

        #expect(store.state.ui.isEditMode == false)
        #expect(store.state.ui.selectedMetricLabel == nil)
        #expect(store.state.ui.draggingMetric == nil)
        #expect(store.state.ui.draggingStreak == nil)
        #expect(store.state.ui.dropHoverId == nil)
    }

    @Test("cancelEdit: clears snapshot after restoration")
    func cancelEditClearsSnapshot() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.beginEdit()
        store.cancelEdit()

        #expect(store.editSessionManager.hasSnapshot == false)
    }

    @Test("cancelEdit: without snapshot does not crash")
    func cancelEditWithoutSnapshotSafe() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.cancelEdit()

        #expect(store.state.ui.isEditMode == false)
    }

    @Test("hasUnsavedChanges: returns false when no snapshot")
    func hasUnsavedChangesFalseNoSnapshot() {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.hasUnsavedChanges() == false)
    }

    @Test("hasUnsavedChanges: returns false when state unchanged")
    func hasUnsavedChangesFalseUnchanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.beginEdit()

        #expect(store.hasUnsavedChanges() == false)
    }

    @Test("hasUnsavedChanges: returns true when metrics changed")
    func hasUnsavedChangesTrueMetricsChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.beginEdit()

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "water")]

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when activeMetricsCount changed")
    func hasUnsavedChangesTrueActiveCountChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.activeMetricsCount = 12
        store.beginEdit()

        store.metricsManager.state.activeMetricsCount = 4

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when goal card removal changed")
    func hasUnsavedChangesTrueGoalCardChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.isGoalCardRemoved = false
        store.beginEdit()

        store.state.ui.isGoalCardRemoved = true

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when streaks changed")
    func hasUnsavedChangesTrueStreaksChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "current")]
        store.beginEdit()

        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "longest")]

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when removedMetrics changed")
    func hasUnsavedChangesTrueRemovedMetricsChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.removedMetrics = []
        store.beginEdit()

        store.state.ui.removedMetrics = ["bmi"]

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when removedStreaks changed")
    func hasUnsavedChangesTrueRemovedStreaksChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.removedStreaks = []
        store.beginEdit()

        store.state.ui.removedStreaks = ["current"]

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when goalCardPosition changed")
    func hasUnsavedChangesTrueGoalCardPositionChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.goalCardPosition = 0
        store.beginEdit()

        store.state.ui.goalCardPosition = 2

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("hasUnsavedChanges: returns true when streakGridOrder changed")
    func hasUnsavedChangesTrueStreakGridOrderChanged() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.streakGridOrder = ["a", "b"]
        store.beginEdit()

        store.state.ui.streakGridOrder = ["b", "a"]

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("updateSnapshot: updates existing snapshot")
    func updateSnapshotUpdatesExisting() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.beginEdit()
        #expect(store.editSessionManager.snapshot?.metrics.count == 1)

        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "bmi"),
            DashboardTestFixtures.makeMetricItem(label: "water")
        ]
        store.updateSnapshot()

        #expect(store.editSessionManager.snapshot?.metrics.count == 2)
    }

    @Test("currentEditSnapshot: captures all fields correctly")
    func currentEditSnapshotCapturesAllFields() {
        let store = DashboardStoreTestSupport.makeSUT().store

        let metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        let streaks = [DashboardTestFixtures.makeMetricItem(label: "streak")]
        store.metricsManager.state.metrics = metrics
        store.metricsManager.state.activeMetricsCount = 8
        store.streakManager.state.streakItems = streaks
        store.streakManager.state.activeStreakItemsCount = 4
        store.state.ui.isGoalCardRemoved = true
        store.state.ui.goalCardPosition = 2
        store.state.ui.streakGridOrder = ["a", "b"]
        store.state.ui.removedMetrics = ["water"]
        store.state.ui.removedStreaks = ["longest"]

        let snapshot = store.currentEditSnapshot()

        #expect(snapshot.metrics.count == 1)
        #expect(snapshot.metrics[0].label == "bmi")
        #expect(snapshot.activeMetricsCount == 8)
        #expect(snapshot.streakItems.count == 1)
        #expect(snapshot.streakItems[0].label == "streak")
        #expect(snapshot.activeStreakItemsCount == 4)
        #expect(snapshot.isGoalCardRemoved == true)
        #expect(snapshot.goalCardPosition == 2)
        #expect(snapshot.streakGridOrder == ["a", "b"])
        #expect(snapshot.removedMetrics == Set(["water"]))
        #expect(snapshot.removedStreaks == Set(["longest"]))
    }

    @Test("restoreFromSnapshot: restores all fields correctly")
    func restoreFromSnapshotRestoresAll() {
        let store = DashboardStoreTestSupport.makeSUT().store

        let snapshot = EditSessionSnapshot(
            metrics: [DashboardTestFixtures.makeMetricItem(label: "restored")],
            activeMetricsCount: 6,
            streakItems: [DashboardTestFixtures.makeMetricItem(label: "restoredStreak")],
            activeStreakItemsCount: 3,
            isGoalCardRemoved: true,
            goalCardPosition: 1,
            streakGridOrder: ["x", "y"],
            removedMetrics: ["bmi"],
            removedStreaks: ["current"]
        )

        store.restoreFromSnapshot(snapshot)

        #expect(store.metricsManager.state.metrics.count == 1)
        #expect(store.metricsManager.state.metrics[0].label == "restored")
        #expect(store.metricsManager.state.activeMetricsCount == 6)
        #expect(store.streakManager.state.streakItems.count == 1)
        #expect(store.streakManager.state.streakItems[0].label == "restoredStreak")
        #expect(store.streakManager.state.activeStreakItemsCount == 3)
        #expect(store.state.ui.isGoalCardRemoved == true)
        #expect(store.state.ui.goalCardPosition == 1)
        #expect(store.state.ui.streakGridOrder == ["x", "y"])
        #expect(store.state.ui.removedMetrics == Set(["bmi"]))
        #expect(store.state.ui.removedStreaks == Set(["current"]))
    }

    @Test("resetEditSession: restores snapshot, resets order, clears state, and starts new edit")
    func resetEditSessionBehavior() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.state.ui.selectedMetricLabel = "bmi"
        store.state.ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "x")
        store.state.ui.draggingStreak = DashboardTestFixtures.makeMetricItem(label: "y")
        store.state.ui.dropHoverId = "hover"

        store.beginEdit()

        store.resetEditSession()

        #expect(store.editSessionManager.hasSnapshot == true)
        #expect(store.state.ui.selectedMetricLabel == nil)
        #expect(store.state.ui.draggingMetric == nil)
        #expect(store.state.ui.draggingStreak == nil)
        #expect(store.state.ui.dropHoverId == nil)
    }

    @Test("full edit flow: begin → modify → cancel restores original state")
    func fullEditFlowBeginModifyCancel() {
        let store = DashboardStoreTestSupport.makeSUT().store

        let initialMetrics = [
            DashboardTestFixtures.makeMetricItem(label: "bmi"),
            DashboardTestFixtures.makeMetricItem(label: "bodyFat"),
            DashboardTestFixtures.makeMetricItem(label: "water")
        ]
        let initialStreaks = [
            DashboardTestFixtures.makeMetricItem(label: "current"),
            DashboardTestFixtures.makeMetricItem(label: "longest")
        ]
        store.metricsManager.state.metrics = initialMetrics
        store.metricsManager.state.activeMetricsCount = 3
        store.streakManager.state.streakItems = initialStreaks
        store.streakManager.state.activeStreakItemsCount = 2
        store.state.ui.isGoalCardRemoved = false
        store.state.ui.goalCardPosition = 0
        store.state.ui.removedMetrics = []
        store.state.ui.removedStreaks = []

        store.beginEdit()
        #expect(store.editSessionManager.hasSnapshot == true)
        #expect(store.hasUnsavedChanges() == false)

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "water")]
        store.metricsManager.state.activeMetricsCount = 1
        store.state.ui.isGoalCardRemoved = true
        store.state.ui.removedMetrics = ["bmi", "bodyFat"]
        #expect(store.hasUnsavedChanges() == true)

        store.cancelEdit()

        #expect(store.metricsManager.state.metrics.count == 3)
        #expect(store.metricsManager.state.activeMetricsCount == 3)
        #expect(store.state.ui.isGoalCardRemoved == false)
        #expect(store.state.ui.removedMetrics.isEmpty)
        #expect(store.state.ui.isEditMode == false)
        #expect(store.editSessionManager.hasSnapshot == false)
    }

    @Test("full edit flow: begin → modify → save (update snapshot)")
    func fullEditFlowBeginModifySave() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]

        store.beginEdit()
        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "bmi"),
            DashboardTestFixtures.makeMetricItem(label: "water")
        ]
        #expect(store.hasUnsavedChanges() == true)

        store.updateSnapshot()

        #expect(store.hasUnsavedChanges() == false)
    }

    @Test("edit mode: multiple edit sessions work independently")
    func multipleEditSessions() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        store.beginEdit()
        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "water")]
        store.cancelEdit()
        #expect(store.metricsManager.state.metrics[0].label == "bmi")

        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "bmi"),
            DashboardTestFixtures.makeMetricItem(label: "bodyFat")
        ]
        store.beginEdit()
        #expect(store.editSessionManager.snapshot?.metrics.count == 2)

        store.cancelEdit()
        #expect(store.metricsManager.state.metrics.count == 2)
    }

    @Test("edit session with empty metrics array")
    func editSessionEmptyMetrics() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = []
        store.beginEdit()

        #expect(store.editSessionManager.snapshot?.metrics.isEmpty == true)
        #expect(store.hasUnsavedChanges() == false)

        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        #expect(store.hasUnsavedChanges() == true)

        store.cancelEdit()
        #expect(store.metricsManager.state.metrics.isEmpty)
    }

    @Test("edit session with empty streaks array")
    func editSessionEmptyStreaks() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.streakManager.state.streakItems = []
        store.beginEdit()

        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "streak")]
        #expect(store.hasUnsavedChanges() == true)

        store.cancelEdit()
        #expect(store.streakManager.state.streakItems.isEmpty)
    }

    @Test("edit session: all removal sets empty then populated")
    func editSessionRemovalSetsEmptyThenPopulated() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.removedMetrics = []
        store.state.ui.removedStreaks = []
        store.beginEdit()

        store.state.ui.removedMetrics = ["bmi", "water", "bodyFat"]
        store.state.ui.removedStreaks = ["current", "longest"]

        #expect(store.hasUnsavedChanges() == true)

        store.cancelEdit()

        #expect(store.state.ui.removedMetrics.isEmpty)
        #expect(store.state.ui.removedStreaks.isEmpty)
    }

    @Test("edit session: goalCardPosition from 0 to large value")
    func editSessionGoalCardPositionLargeValue() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.goalCardPosition = 0
        store.beginEdit()

        store.state.ui.goalCardPosition = 999

        #expect(store.hasUnsavedChanges() == true)

        store.cancelEdit()

        #expect(store.state.ui.goalCardPosition == 0)
    }

    @Test("edit session: streakGridOrder reorder detection")
    func editSessionStreakGridOrderReorder() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.state.ui.streakGridOrder = ["a", "b", "c"]
        store.beginEdit()

        store.state.ui.streakGridOrder = ["c", "a", "b"]

        #expect(store.hasUnsavedChanges() == true)
    }

    @Test("edit session: same data different order is detected")
    func editSessionSameDataDifferentOrderDetected() {
        let store = DashboardStoreTestSupport.makeSUT().store

        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "bmi"),
            DashboardTestFixtures.makeMetricItem(label: "water")
        ]
        store.beginEdit()

        store.metricsManager.state.metrics = [
            DashboardTestFixtures.makeMetricItem(label: "water"),
            DashboardTestFixtures.makeMetricItem(label: "bmi")
        ]

        #expect(store.hasUnsavedChanges() == true)
    }
    }
}
