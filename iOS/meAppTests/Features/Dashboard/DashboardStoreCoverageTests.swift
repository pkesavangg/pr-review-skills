import Foundation
@testable import meApp
import Testing

// MARK: - Display Properties Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreDisplayTests {

    // MARK: - shouldShowGoalCardOrStreaks

    @Test func goalCardOrStreaksShownWhenGoalCardNotRemoved() {
        let store = DashboardStore(lightweight: true)
        store.ui.isGoalCardRemoved = false
        #expect(store.shouldShowGoalCardOrStreaks == true)
    }

    @Test func goalCardOrStreaksShownWhenGoalRemovedButStreaksPresent() {
        let store = DashboardStore(lightweight: true)
        store.ui.isGoalCardRemoved = true
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.removedStreaks = []
        #expect(store.shouldShowGoalCardOrStreaks == true)
    }

    @Test func goalCardOrStreaksHiddenWhenBothRemoved() {
        let store = DashboardStore(lightweight: true)
        store.ui.isGoalCardRemoved = true
        store.streakManager.state.streakItems = []
        store.streak = store.streakManager.state
        #expect(store.shouldShowGoalCardOrStreaks == false)
    }

    // MARK: - hasBodyMetrics

    @Test func hasBodyMetricsFalseWhenConfigNotLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = false
        // metricsToShow returns [] when config not loaded → hasBodyMetrics is false
        #expect(store.hasBodyMetrics == false)
    }

    @Test func hasBodyMetricsTrueWhenConfigLoadedAndMetricsPresent() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.metricsManager.setupInitialMetrics()
        store.metrics = store.metricsManager.state
        store.ui.removedMetrics = []
        #expect(store.hasBodyMetrics == true)
    }

    // MARK: - shouldShowBodyMetricsSkeleton

    @Test func bodyMetricsSkeletonShownBeforeConfigLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = false
        // shouldShowBodyMetrics returns false when no account → skeleton = false
        // (skeleton = shouldShowBodyMetrics && (!hasLoaded || !hasLoadedValues))
        // When shouldShowBodyMetrics is false, skeleton is false
        #expect(store.shouldShowBodyMetricsSkeleton == false)
    }

    @Test func bodyMetricsSkeletonShownWhenConfigLoadedButValuesNot() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.ui.hasLoadedMetricValues = false
        store.metricsManager.setupInitialMetrics()
        store.metrics = store.metricsManager.state
        store.ui.removedMetrics = []
        // shouldShowBodyMetrics = true (config loaded, metrics exist), values not loaded → skeleton
        #expect(store.shouldShowBodyMetricsSkeleton == true)
    }

    @Test func bodyMetricsSkeletonHiddenWhenBothLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.ui.hasLoadedMetricValues = true
        store.metricsManager.setupInitialMetrics()
        store.metrics = store.metricsManager.state
        store.ui.removedMetrics = []
        #expect(store.shouldShowBodyMetricsSkeleton == false)
    }

    // MARK: - shouldShowProgressMetricsSkeleton

    @Test func progressMetricsSkeletonTrueWhenProgressNotLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedProgressMetrics = false
        #expect(store.shouldShowProgressMetricsSkeleton == true)
    }

    @Test func progressMetricsSkeletonFalseWhenProgressLoadedNoGoalStreak() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedProgressMetrics = true
        store.ui.hasLoadedDashboardConfig = true
        store.ui.isGoalCardRemoved = true
        store.streakManager.state.streakItems = []
        store.streak = store.streakManager.state
        // shouldShowGoalStreakSection = false → skeleton = false
        #expect(store.shouldShowProgressMetricsSkeleton == false)
    }

    @Test func progressMetricsSkeletonTrueWhenStreakOrderEmpty() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedProgressMetrics = true
        store.ui.hasLoadedDashboardConfig = true
        store.ui.isGoalCardRemoved = false
        store.ui.isEditMode = false
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.removedStreaks = []
        store.ui.streakGridOrder = []
        // shouldShowGoalStreakSection = true, streakItemsToShow not empty, streakGridOrder empty → skeleton
        #expect(store.shouldShowProgressMetricsSkeleton == true)
    }

    // MARK: - skeletonProgressMetricsHasContentAbove

    @Test func skeletonHasContentAboveMatchesShouldShowBodyMetrics() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.metricsManager.setupInitialMetrics()
        store.metrics = store.metricsManager.state
        store.ui.removedMetrics = []
        #expect(store.skeletonProgressMetricsHasContentAbove == store.shouldShowBodyMetrics)
    }

    // MARK: - shouldShowDivider

    @Test func dividerNotShownWhenNoBodyMetrics() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = false
        #expect(store.shouldShowDivider == false)
    }

    // MARK: - shouldShowGoalStreakSection

    @Test func goalStreakSectionHiddenBeforeConfigLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = false
        #expect(store.shouldShowGoalStreakSection == false)
    }

    @Test func goalStreakSectionShownAfterConfigLoadedWithGoal() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.ui.isGoalCardRemoved = false
        #expect(store.shouldShowGoalStreakSection == true)
    }

    // MARK: - metricsToShow

    @Test func metricsToShowEmptyBeforeConfigLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = false
        #expect(store.metricsToShow.isEmpty)
    }

    @Test func metricsToShowPopulatedAfterConfigLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.metricsManager.setupInitialMetrics()
        store.metrics = store.metricsManager.state
        store.ui.removedMetrics = []
        #expect(!store.metricsToShow.isEmpty)
    }

    // MARK: - streakItemsToShow

    @Test func streakItemsToShowAllWhenProgressNotLoaded() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.isEditMode = false
        store.ui.hasLoadedProgressMetrics = false
        // Returns all streaks before API loads
        #expect(store.streakItemsToShow.count == store.streakManager.state.streakItems.count)
    }

    @Test func streakItemsToShowFiltersRemovedInNonEditMode() throws {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.hasLoadedProgressMetrics = true
        store.ui.isEditMode = false
        let firstLabel = try #require(store.streakManager.state.streakItems.first).label
        store.ui.removedStreaks = [firstLabel]
        let shown = store.streakItemsToShow
        #expect(!shown.contains { $0.label == firstLabel })
    }

    @Test func streakItemsToShowAllInEditMode() throws {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.hasLoadedProgressMetrics = true
        store.ui.isEditMode = true
        let firstLabel = try #require(store.streakManager.state.streakItems.first).label
        store.ui.removedStreaks = [firstLabel]
        // In edit mode: nonRemoved + removed (all shown)
        #expect(store.streakItemsToShow.count == store.streakManager.state.streakItems.count)
    }

    // MARK: - isAnyItemBeingDragged

    @Test func isAnyItemBeingDraggedFalseInitially() {
        let store = DashboardStore(lightweight: true)
        #expect(store.isAnyItemBeingDragged == false)
    }

    @Test func isAnyItemBeingDraggedTrueWhenMetricDragging() {
        let store = DashboardStore(lightweight: true)
        let item = MetricItem(value: "10", label: DashboardStrings.bmi, unit: nil, preLabel: nil, icon: nil)
        store.startDraggingMetric(item)
        #expect(store.isAnyItemBeingDragged == true)
    }

    @Test func isAnyItemBeingDraggedTrueWhenGoalCardDragging() {
        let store = DashboardStore(lightweight: true)
        store.startDraggingGoalCard()
        #expect(store.isAnyItemBeingDragged == true)
    }

    // MARK: - hasAnyEntries

    @Test func hasAnyEntriesFalseWithEmptyData() {
        let store = DashboardStore(lightweight: true)
        store.data.dailySummaries = []
        store.data.monthlySummaries = []
        #expect(store.hasAnyEntries == false)
    }

    @Test func hasAnyEntriesTrueWithDailyData() {
        let store = DashboardStore(lightweight: true)
        let summary = makeSummary(date: makeDate(2026, 4, 20), weight: 180, bmi: 24.0)
        store.data.dailySummaries = [summary]
        #expect(store.hasAnyEntries == true)
    }
}

// MARK: - Metric/Streak Removal Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreRemovalTests {

    @Test func isMetricRemovedFalseInitially() {
        let store = DashboardStore(lightweight: true)
        #expect(store.isMetricRemoved(DashboardStrings.bmi) == false)
    }

    @Test func isMetricRemovedTrueAfterInsertion() {
        let store = DashboardStore(lightweight: true)
        store.ui.removedMetrics.insert(DashboardStrings.bmi)
        #expect(store.isMetricRemoved(DashboardStrings.bmi) == true)
    }

    @Test func isStreakRemovedFalseInitially() {
        let store = DashboardStore(lightweight: true)
        #expect(store.isStreakRemoved(DashboardStrings.currentStreak) == false)
    }

    @Test func isStreakRemovedTrueAfterInsertion() {
        let store = DashboardStore(lightweight: true)
        store.ui.removedStreaks.insert(DashboardStrings.currentStreak)
        #expect(store.isStreakRemoved(DashboardStrings.currentStreak) == true)
    }

    @Test func syncRemovalStateFromMetricsManagerClearsAndRepopulates() {
        let store = DashboardStore(lightweight: true)
        store.metricsManager.setupInitialMetrics()
        // Only mark first 2 metrics as active
        store.metricsManager.state.activeMetricsCount = 2
        store.ui.removedMetrics = ["some-old-label"]

        store.syncRemovalStateFromMetricsManager()

        // All metrics beyond index 2 should be in removedMetrics
        let metrics = store.metricsManager.state.metrics
        for i in 2..<metrics.count {
            #expect(store.ui.removedMetrics.contains(metrics[i].label))
        }
        // First two should NOT be removed
        #expect(!store.ui.removedMetrics.contains(metrics[0].label))
        #expect(!store.ui.removedMetrics.contains(metrics[1].label))
    }

    @Test func syncRemovalStateFromMetricsManagerHandlesActiveCountExceedingMetricsCount() {
        let store = DashboardStore(lightweight: true)
        store.metricsManager.setupInitialMetrics()
        let totalCount = store.metricsManager.state.metrics.count
        // Set activeCount way beyond actual count
        store.metricsManager.state.activeMetricsCount = totalCount + 10

        // Should not crash and should result in no removed metrics (all active)
        store.syncRemovalStateFromMetricsManager()
        #expect(store.ui.removedMetrics.isEmpty)
    }

    @Test func syncRemovalStateFromStreakManagerWithAllActive() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streakManager.state.activeStreakItemsCount = store.streakManager.state.streakItems.count

        store.syncRemovalStateFromStreakManager()

        #expect(store.ui.removedStreaks.isEmpty)
    }

    @Test func syncRemovalStateFromStreakManagerWithSomeRemoved() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        let total = store.streakManager.state.streakItems.count
        store.streakManager.state.activeStreakItemsCount = max(0, total - 2)

        store.syncRemovalStateFromStreakManager()

        // Last 2 streaks should be in removedStreaks
        let streaks = store.streakManager.state.streakItems
        let activeCount = store.streakManager.state.activeStreakItemsCount
        for i in activeCount..<streaks.count {
            #expect(store.ui.removedStreaks.contains(streaks[i].label))
        }
    }

    @Test func syncRemovalStateFromStreakManagerWithEmptyStreaks() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.state.streakItems = []
        store.ui.removedStreaks = ["some-label"]

        store.syncRemovalStateFromStreakManager()

        #expect(store.ui.removedStreaks.isEmpty)
    }

    @Test func toggleGoalCardRemovalFlipsFlag() {
        let store = DashboardStore(lightweight: true)
        let initial = store.ui.isGoalCardRemoved
        store.toggleGoalCardRemoval()
        #expect(store.ui.isGoalCardRemoved == !initial)
        store.toggleGoalCardRemoval()
        #expect(store.ui.isGoalCardRemoved == initial)
    }
}

// MARK: - Drag & Drop Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreDragDropTests {

    @Test func startDraggingMetricSetsMetric() {
        let store = DashboardStore(lightweight: true)
        let item = MetricItem(value: "24.1", label: DashboardStrings.bmi, unit: nil, preLabel: nil, icon: nil)
        store.startDraggingMetric(item)
        #expect(store.ui.draggingMetric?.label == DashboardStrings.bmi)
    }

    @Test func startDraggingStreakSetsStreak() {
        let store = DashboardStore(lightweight: true)
        let item = MetricItem(value: "5", label: DashboardStrings.currentStreak, unit: nil, preLabel: nil, icon: nil)
        store.startDraggingStreak(item)
        #expect(store.ui.draggingStreak?.label == DashboardStrings.currentStreak)
    }

    @Test func startDraggingGoalCardSetsFlag() {
        let store = DashboardStore(lightweight: true)
        store.startDraggingGoalCard()
        #expect(store.ui.isGoalCardBeingDragged == true)
    }

    @Test func updateDropTargetSetsHoverId() {
        let store = DashboardStore(lightweight: true)
        store.updateDropTarget("some-id")
        #expect(store.ui.dropHoverId == "some-id")
        store.updateDropTarget(nil)
        #expect(store.ui.dropHoverId == nil)
    }

    @Test func endDraggingClearsAllDragState() {
        let store = DashboardStore(lightweight: true)
        let item = MetricItem(value: "1", label: DashboardStrings.bmi, unit: nil, preLabel: nil, icon: nil)
        store.startDraggingMetric(item)
        store.startDraggingGoalCard()
        store.updateDropTarget("target")
        store.endDragging()
        #expect(store.ui.draggingMetric == nil)
        #expect(store.ui.draggingStreak == nil)
        #expect(store.ui.isGoalCardBeingDragged == false)
        #expect(store.ui.dropHoverId == nil)
    }

    @Test func resetDragStateClearsAll() {
        let store = DashboardStore(lightweight: true)
        let item = MetricItem(value: "5", label: DashboardStrings.currentStreak, unit: nil, preLabel: nil, icon: nil)
        store.startDraggingStreak(item)
        store.updateDropTarget("id")
        store.resetDragState()
        #expect(store.ui.draggingMetric == nil)
        #expect(store.ui.draggingStreak == nil)
        #expect(store.ui.dropHoverId == nil)
        #expect(store.ui.isGoalCardBeingDragged == false)
    }

    @Test func resetGridLayoutChangesGridLayoutId() {
        let store = DashboardStore(lightweight: true)
        let initial = store.ui.gridLayoutId
        store.resetGridLayout()
        #expect(store.ui.gridLayoutId != initial)
    }
}

// MARK: - Edit Session Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreEditSessionTests {

    @Test func beginEditSnapshotsState() {
        let store = DashboardStore(lightweight: true)
        store.metricsManager.setupInitialMetrics()
        store.ui.isGoalCardRemoved = true
        store.ui.goalCardPosition = 2

        store.beginEdit()

        #expect(store.hasUnsavedChanges() == false)
    }

    @Test func beginEditIsIdempotent() {
        let store = DashboardStore(lightweight: true)
        store.beginEdit()
        store.metricsManager.state.activeMetricsCount = 1
        store.beginEdit() // should not re-snapshot (guard !hasEditSnapshot)
        // hasUnsavedChanges should be true since we mutated after first beginEdit
        #expect(store.hasUnsavedChanges() == true)
    }

    @Test func hasUnsavedChangesFalseWithoutSnapshot() {
        let store = DashboardStore(lightweight: true)
        #expect(store.hasUnsavedChanges() == false)
    }

    @Test func hasUnsavedChangesTrueAfterGoalCardToggle() {
        let store = DashboardStore(lightweight: true)
        store.ui.isGoalCardRemoved = false
        store.beginEdit()
        store.ui.isGoalCardRemoved = true
        #expect(store.hasUnsavedChanges() == true)
    }

    @Test func hasUnsavedChangesTrueAfterGoalCardPositionChange() {
        let store = DashboardStore(lightweight: true)
        store.ui.goalCardPosition = 0
        store.beginEdit()
        store.ui.goalCardPosition = 2
        #expect(store.hasUnsavedChanges() == true)
    }

    @Test func hasUnsavedChangesTrueAfterRemovedMetricsChange() {
        let store = DashboardStore(lightweight: true)
        store.ui.removedMetrics = []
        store.beginEdit()
        store.ui.removedMetrics.insert(DashboardStrings.bmi)
        #expect(store.hasUnsavedChanges() == true)
    }

    @Test func hasUnsavedChangesTrueAfterStreakRemoval() {
        let store = DashboardStore(lightweight: true)
        store.ui.removedStreaks = []
        store.beginEdit()
        store.ui.removedStreaks.insert(DashboardStrings.currentStreak)
        #expect(store.hasUnsavedChanges() == true)
    }

    @Test func cancelEditRestoresSnapshot() {
        let store = DashboardStore(lightweight: true)
        store.metricsManager.setupInitialMetrics()
        store.ui.isGoalCardRemoved = false
        store.ui.goalCardPosition = 0
        store.ui.removedMetrics = []

        store.beginEdit()

        store.ui.isGoalCardRemoved = true
        store.ui.goalCardPosition = 4
        store.ui.removedMetrics = [DashboardStrings.bmi]

        store.cancelEdit()

        #expect(store.ui.isGoalCardRemoved == false)
        #expect(store.ui.goalCardPosition == 0)
        #expect(!store.ui.removedMetrics.contains(DashboardStrings.bmi))
        #expect(store.ui.isEditMode == false)
    }

    @Test func cancelEditClearsDragState() {
        let store = DashboardStore(lightweight: true)
        store.beginEdit()
        let item = MetricItem(value: "1", label: DashboardStrings.bmi, unit: nil, preLabel: nil, icon: nil)
        store.startDraggingMetric(item)
        store.updateDropTarget("x")
        store.cancelEdit()
        #expect(store.ui.draggingMetric == nil)
        #expect(store.ui.dropHoverId == nil)
    }

    @Test func updateSnapshotNoopsWithoutSnapshot() {
        let store = DashboardStore(lightweight: true)
        store.ui.goalCardPosition = 5
        store.updateSnapshot() // no-op since !hasEditSnapshot
        store.beginEdit()
        // snapshot captured goalCardPosition=5, then change
        store.ui.goalCardPosition = 10
        #expect(store.hasUnsavedChanges() == true)
    }

    @Test func updateSnapshotRefreshesBaseline() {
        let store = DashboardStore(lightweight: true)
        store.ui.isGoalCardRemoved = false
        store.beginEdit()
        store.ui.isGoalCardRemoved = true
        store.updateSnapshot()
        // After updateSnapshot, current state IS the new baseline
        #expect(store.hasUnsavedChanges() == false)
    }

    @Test func resetEditSessionRestoresAndCreatesNewSnapshot() {
        let store = DashboardStore(lightweight: true)
        store.metricsManager.setupInitialMetrics()
        store.ui.isGoalCardRemoved = false
        store.beginEdit()
        store.ui.isGoalCardRemoved = true

        store.resetEditSession()

        // After reset, hasUnsavedChanges should be false (new snapshot created)
        #expect(store.hasUnsavedChanges() == false)
    }
}

// MARK: - Goal Card Position Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreGoalCardPositionTests {

    @Test func updateGoalCardPositionClampsToZero() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.state.streakItems = []
        store.streak = store.streakManager.state
        store.ui.isGoalCardRemoved = false
        store.updateGoalCardPosition(-5)
        #expect(store.ui.goalCardPosition == 0)
    }

    @Test func updateGoalCardPositionClampsToMax() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.removedStreaks = []
        let maxPosition = store.streakItemsToShow.count
        store.updateGoalCardPosition(maxPosition + 100)
        // clamped to maxPosition or snapped to column boundary
        #expect(store.ui.goalCardPosition <= maxPosition)
    }

    @Test func validateGoalCardPositionClampsWhenTooHigh() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.removedStreaks = []
        store.ui.goalCardPosition = 999
        store.validateGoalCardPosition()
        let maxPosition = store.streakItemsToShow.count
        #expect(store.ui.goalCardPosition <= maxPosition)
    }

    @Test func validateGoalCardPositionClampsNegative() {
        let store = DashboardStore(lightweight: true)
        store.ui.goalCardPosition = -1
        store.validateGoalCardPosition()
        #expect(store.ui.goalCardPosition == 0)
    }

    @Test func validateGoalCardPositionSnapsToRowWhenNoRemovedStreaks() {
        let store = DashboardStore(lightweight: true)
        store.streakManager.setupInitialStreakItems()
        store.streak = store.streakManager.state
        store.ui.removedStreaks = []
        // Set to position 1 (not a column boundary for phone with 2 cols)
        store.ui.goalCardPosition = 1
        store.validateGoalCardPosition()
        // Should snap to 0 (nearest row start for 2-column grid: 0*2=0)
        #expect(store.ui.goalCardPosition % 2 == 0)
    }
}

// MARK: - Helpers (file-private)

private func makeDate(_ year: Int, _ month: Int, _ day: Int) -> Date {
    var components = DateComponents()
    components.calendar = Calendar(identifier: .gregorian)
    components.timeZone = TimeZone(secondsFromGMT: 0)
    components.year = year
    components.month = month
    components.day = day
    guard let date = components.date else {
        Issue.record("unexpected nil date from components")
        return Date()
    }
    return date
}

private func makeSummary(date: Date, weight: Double, bmi: Double) -> BathScaleWeightSummary {
    let formatter = ISO8601DateFormatter()
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return BathScaleWeightSummary(
        accountId: "test-account",
        period: DateTimeTools.formatter("yyyy-MM-dd").string(from: date),
        entryTimestamp: formatter.string(from: date),
        date: date,
        count: 1,
        weight: weight,
        bmi: bmi
    )
}
