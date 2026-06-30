// swiftlint:disable file_length
import Foundation
@testable import meApp
import SwiftUI
import Testing

// MARK: - DashboardState Tests

@Suite(.serialized)
@MainActor
// swiftlint:disable:next type_body_length
struct DashboardStateTests {

    // MARK: - Default Initialization

    @Test("init: creates state with default UI, metrics, and streak sub-states")
    func initDefaultsUIMetricsStreak() {
        let state = DashboardState()

        // UIState defaults
        #expect(state.ui.isLoading == false)
        #expect(state.ui.hasInitializedChart == false)
        #expect(state.ui.hasLoadedDashboardConfig == false)
        #expect(state.ui.hasLoadedProgressMetrics == false)
        #expect(state.ui.hasLoadedMetricValues == false)
        #expect(state.ui.loaderOverride == nil)
        #expect(state.ui.alertData == nil)
        #expect(state.ui.isEditMode == false)
        #expect(state.ui.selectedMetricLabel == nil)
        #expect(state.ui.isGoalCardRemoved == false)
        #expect(state.ui.isResettingDashboard == false)
        #expect(state.ui.removedMetrics.isEmpty)
        #expect(state.ui.removedStreaks.isEmpty)
        #expect(state.ui.goalCardPosition == 0)
        #expect(state.ui.streakGridOrder.isEmpty)
        #expect(state.ui.draggingMetric == nil)
        #expect(state.ui.draggingStreak == nil)
        #expect(state.ui.isGoalCardBeingDragged == false)
        #expect(state.ui.dropHoverId == nil)

        // MetricsState defaults
        #expect(state.metrics.dashboardType == .dashboard12)
        #expect(state.metrics.metrics.isEmpty)
        #expect(state.metrics.activeMetricsCount == 12)
        #expect(state.metrics.removedMetrics.isEmpty)

        // StreakState defaults
        #expect(state.streak.streakItems.isEmpty)
        #expect(state.streak.activeStreakItemsCount == 6)
        #expect(state.streak.removedStreaks.isEmpty)
    }

    @Test("init: creates state with default graph, goal, and data sub-states")
    func initDefaultsGraphGoalData() {
        let state = DashboardState()

        // GraphState defaults
        #expect(state.graph.selectedEntry == nil)
        #expect(state.graph.selectedPeriod == .month)
        #expect(state.graph.selectedWeight == nil)
        #expect(state.graph.selectedPoint == nil)
        #expect(state.graph.selectedXValue == nil)
        #expect(state.graph.chartHeight == 0)
        #expect(state.graph.annotationHeight == 0)
        #expect(state.graph.isGraphReady == false)
        #expect(state.graph.isScrolling == false)
        #expect(state.graph.showCrosshair == false)
        #expect(state.graph.scrollEndTimer == nil)
        #expect(state.graph.dataChangeTrigger == 0)
        #expect(state.graph.hasDetectedScrollInCurrentGesture == false)
        #expect(state.graph.cachedYAxisDomain == nil)
        #expect(state.graph.cachedYAxisTicks == nil)
        #expect(state.graph.cachedXAxisValues == nil)

        // GoalState defaults
        #expect(state.goal.goalType == .gain)
        #expect(state.goal.goalStartWeight == 0.0)
        #expect(state.goal.goalWeight == 0.0)
        #expect(state.goal.goalUnit == .lb)
        #expect(state.goal.goalDelta == 0.0)
        #expect(state.goal.goalProgress == 0.0)
        #expect(state.goal.hasGoalSet == false)

        // DataState defaults
        #expect(state.data.dailySummaries.isEmpty)
        #expect(state.data.monthlySummaries.isEmpty)
        #expect(state.data.latestWeightStored == 0)
        #expect(state.data.dailyCache.isEmpty)
        #expect(state.data.monthlyCache.isEmpty)
    }

    // MARK: - UIState Tests

    @Test("UIState: isAnyItemBeingDragged returns false when nothing is dragged")
    func isAnyItemBeingDraggedFalseWhenNothingDragged() {
        let ui = UIState()
        #expect(ui.isAnyItemBeingDragged == false)
    }

    @Test("UIState: isAnyItemBeingDragged returns true when dragging metric")
    func isAnyItemBeingDraggedTrueForMetric() {
        var ui = UIState()
        ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "bmi")
        #expect(ui.isAnyItemBeingDragged == true)
    }

    @Test("UIState: isAnyItemBeingDragged returns true when dragging streak")
    func isAnyItemBeingDraggedTrueForStreak() {
        var ui = UIState()
        ui.draggingStreak = DashboardTestFixtures.makeMetricItem(label: "streak")
        #expect(ui.isAnyItemBeingDragged == true)
    }

    @Test("UIState: isAnyItemBeingDragged returns true when dragging goal card")
    func isAnyItemBeingDraggedTrueForGoalCard() {
        var ui = UIState()
        ui.isGoalCardBeingDragged = true
        #expect(ui.isAnyItemBeingDragged == true)
    }

    @Test("UIState: isAnyItemBeingDragged returns true when multiple items dragged simultaneously")
    func isAnyItemBeingDraggedMultipleItems() {
        var ui = UIState()
        ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "bmi")
        ui.draggingStreak = DashboardTestFixtures.makeMetricItem(label: "streak")
        ui.isGoalCardBeingDragged = true
        #expect(ui.isAnyItemBeingDragged == true)
    }

    @Test("UIState: resetDragState clears all drag-related properties")
    func resetDragStateClearsAll() {
        var ui = UIState()
        ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "bmi")
        ui.draggingStreak = DashboardTestFixtures.makeMetricItem(label: "streak")
        ui.isGoalCardBeingDragged = true
        ui.dropHoverId = "hover-123"

        ui.resetDragState()

        #expect(ui.draggingMetric == nil)
        #expect(ui.draggingStreak == nil)
        #expect(ui.isGoalCardBeingDragged == false)
        #expect(ui.dropHoverId == nil)
    }

    @Test("UIState: resetDragState on already-clean state is idempotent")
    func resetDragStateIdempotent() {
        var ui = UIState()
        ui.resetDragState()

        #expect(ui.draggingMetric == nil)
        #expect(ui.draggingStreak == nil)
        #expect(ui.isGoalCardBeingDragged == false)
        #expect(ui.dropHoverId == nil)
    }

    @Test("UIState: removedMetrics set operations work correctly")
    func removedMetricsSetOperations() {
        var ui = UIState()
        ui.removedMetrics.insert("bmi")
        ui.removedMetrics.insert("bodyFat")

        #expect(ui.removedMetrics.count == 2)
        #expect(ui.removedMetrics.contains("bmi"))
        #expect(ui.removedMetrics.contains("bodyFat"))

        ui.removedMetrics.remove("bmi")
        #expect(ui.removedMetrics.count == 1)
        #expect(!ui.removedMetrics.contains("bmi"))
    }

    @Test("UIState: removedStreaks set operations work correctly")
    func removedStreaksSetOperations() {
        var ui = UIState()
        ui.removedStreaks.insert("current")
        ui.removedStreaks.insert("longest")
        ui.removedStreaks.insert("weeklyChange")

        #expect(ui.removedStreaks.count == 3)

        ui.removedStreaks.removeAll()
        #expect(ui.removedStreaks.isEmpty)
    }

    @Test("UIState: streakGridOrder preserves insertion order")
    func streakGridOrderPreservesOrder() {
        var ui = UIState()
        let ids = ["id-1", "id-2", "id-3"]
        ui.streakGridOrder = ids

        #expect(ui.streakGridOrder == ids)
        #expect(ui.streakGridOrder[0] == "id-1")
        #expect(ui.streakGridOrder[2] == "id-3")
    }

    @Test("UIState: gridLayoutId is unique on each instantiation")
    func gridLayoutIdUnique() {
        let ui1 = UIState()
        let ui2 = UIState()
        #expect(ui1.gridLayoutId != ui2.gridLayoutId)
    }

    // MARK: - MetricsState Tests

    @Test("MetricsState: metricsToShow returns prefix of activeMetricsCount")
    func metricsToShowReturnsPrefix() {
        var metricsState = MetricsState()
        let items = (0..<12).map { DashboardTestFixtures.makeMetricItem(label: "metric-\($0)") }
        metricsState.metrics = items
        metricsState.activeMetricsCount = 4

        let shown = metricsState.metricsToShow
        #expect(shown.count == 4)
        #expect(shown[0].label == "metric-0")
        #expect(shown[3].label == "metric-3")
    }

    @Test("MetricsState: metricsToShow returns all when activeMetricsCount exceeds array size")
    func metricsToShowAllWhenCountExceedsSize() {
        var metricsState = MetricsState()
        let items = [
            DashboardTestFixtures.makeMetricItem(label: "a"),
            DashboardTestFixtures.makeMetricItem(label: "b")
        ]
        metricsState.metrics = items
        metricsState.activeMetricsCount = 100

        #expect(metricsState.metricsToShow.count == 2)
    }

    @Test("MetricsState: metricsToShow returns empty when metrics array is empty")
    func metricsToShowEmptyWhenNoMetrics() {
        var metricsState = MetricsState()
        metricsState.activeMetricsCount = 12
        #expect(metricsState.metricsToShow.isEmpty)
    }

    @Test("MetricsState: metricsToShow returns empty when activeMetricsCount is zero")
    func metricsToShowEmptyWhenCountZero() {
        var metricsState = MetricsState()
        metricsState.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        metricsState.activeMetricsCount = 0

        #expect(metricsState.metricsToShow.isEmpty)
    }

    @Test("MetricsState: negative activeMetricsCount is clamped to zero")
    func metricsToShowNegativeCountClampedToZero() {
        var metricsState = MetricsState()
        metricsState.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]
        metricsState.activeMetricsCount = -1

        #expect(metricsState.activeMetricsCount == 0)
        #expect(metricsState.metricsToShow.isEmpty)
    }

    @Test("MetricsState: gridColumns for dashboard12 returns 3 columns")
    func gridColumnsDashboard12() {
        var metricsState = MetricsState()
        metricsState.dashboardType = .dashboard12
        #expect(metricsState.gridColumns.count == DashboardConstants.UIConstants.twelveMetricGridColumns)
    }

    @Test("MetricsState: gridColumns for dashboard4 returns 2 columns")
    func gridColumnsDashboard4() {
        var metricsState = MetricsState()
        metricsState.dashboardType = .dashboard4
        #expect(metricsState.gridColumns.count == DashboardConstants.UIConstants.fourMetricGridColumns)
    }

    @Test("MetricsState: default dashboard type is dashboard12")
    func defaultDashboardType() {
        let metricsState = MetricsState()
        #expect(metricsState.dashboardType == .dashboard12)
    }

    @Test("MetricsState: removedMetrics tracks removed labels independently from UIState")
    func metricsStateRemovedMetrics() {
        var metricsState = MetricsState()
        metricsState.removedMetrics = ["bmi", "water"]
        #expect(metricsState.removedMetrics.count == 2)
    }

    // MARK: - StreakState Tests

    @Test("StreakState: streakItemsToShow returns prefix of activeStreakItemsCount")
    func streakItemsToShowPrefix() {
        var streakState = StreakState()
        let items = (0..<6).map { DashboardTestFixtures.makeMetricItem(label: "streak-\($0)") }
        streakState.streakItems = items
        streakState.activeStreakItemsCount = 3

        #expect(streakState.streakItemsToShow.count == 3)
        #expect(streakState.streakItemsToShow[0].label == "streak-0")
    }

    @Test("StreakState: streakItemsToShow returns all when count exceeds items")
    func streakItemsToShowAllWhenCountExceeds() {
        var streakState = StreakState()
        let items = [DashboardTestFixtures.makeMetricItem(label: "a")]
        streakState.streakItems = items
        streakState.activeStreakItemsCount = 10

        #expect(streakState.streakItemsToShow.count == 1)
    }

    @Test("StreakState: streakItemsToShow returns empty when items empty")
    func streakItemsToShowEmptyWhenNoItems() {
        let streakState = StreakState()
        #expect(streakState.streakItemsToShow.isEmpty)
    }

    @Test("StreakState: streakItemsToShow returns empty when activeCount is zero")
    func streakItemsToShowEmptyWhenCountZero() {
        var streakState = StreakState()
        streakState.streakItems = [DashboardTestFixtures.makeMetricItem(label: "x")]
        streakState.activeStreakItemsCount = 0

        #expect(streakState.streakItemsToShow.isEmpty)
    }

    @Test("StreakState: negative activeStreakItemsCount is clamped to zero")
    func streakItemsToShowNegativeCountClampedToZero() {
        var streakState = StreakState()
        streakState.streakItems = [DashboardTestFixtures.makeMetricItem(label: "x")]
        streakState.activeStreakItemsCount = -1

        #expect(streakState.activeStreakItemsCount == 0)
        #expect(streakState.streakItemsToShow.isEmpty)
    }

    @Test("StreakState: default activeStreakItemsCount is 6")
    func streakDefaultActiveCount() {
        let streakState = StreakState()
        #expect(streakState.activeStreakItemsCount == 6)
    }

    @Test("StreakState: removedStreaks set operations")
    func streakRemovedStreaksSetOps() {
        var streakState = StreakState()
        streakState.removedStreaks = ["current", "longest"]
        #expect(streakState.removedStreaks.count == 2)
        #expect(streakState.removedStreaks.contains("current"))
    }

    // MARK: - GraphState Tests

    @Test("GraphState: default selectedPeriod is month")
    func graphDefaultPeriod() {
        let graphState = GraphState()
        #expect(graphState.selectedPeriod == .month)
    }

    @Test("GraphState: clearSelection resets all selection properties")
    func graphClearSelection() {
        var graphState = GraphState()
        graphState.selectedEntry = nil // Would need a DTO but test structure
        graphState.selectedPoint = DashboardTestFixtures.makeSummary()
        graphState.selectedXValue = Date()
        graphState.selectedWeight = 180.0
        graphState.showCrosshair = true

        graphState.clearSelection()

        #expect(graphState.selectedEntry == nil)
        #expect(graphState.selectedPoint == nil)
        #expect(graphState.selectedXValue == nil)
        #expect(graphState.selectedWeight == nil)
        #expect(graphState.showCrosshair == false)
    }

    @Test("GraphState: clearSelection on already-clear state is idempotent")
    func graphClearSelectionIdempotent() {
        var graphState = GraphState()
        graphState.clearSelection()

        #expect(graphState.selectedPoint == nil)
        #expect(graphState.selectedWeight == nil)
        #expect(graphState.showCrosshair == false)
    }

    @Test("GraphState: updateScrollState sets scrolling true and clears selection")
    func graphUpdateScrollStateTrue() {
        var graphState = GraphState()
        graphState.selectedWeight = 180.0
        graphState.selectedPoint = DashboardTestFixtures.makeSummary()
        graphState.showCrosshair = true

        graphState.updateScrollState(isScrolling: true)

        #expect(graphState.isScrolling == true)
        #expect(graphState.selectedWeight == nil)
        #expect(graphState.selectedPoint == nil)
        #expect(graphState.showCrosshair == false)
    }

    @Test("GraphState: updateScrollState sets scrolling false without clearing selection")
    func graphUpdateScrollStateFalse() {
        var graphState = GraphState()
        graphState.selectedWeight = 180.0
        graphState.showCrosshair = true

        graphState.updateScrollState(isScrolling: false)

        #expect(graphState.isScrolling == false)
        #expect(graphState.selectedWeight == 180.0)
        #expect(graphState.showCrosshair == true)
    }

    @Test("GraphState: updateScrollState toggles scroll state back and forth")
    func graphUpdateScrollStateToggle() {
        var graphState = GraphState()

        graphState.updateScrollState(isScrolling: true)
        #expect(graphState.isScrolling == true)

        graphState.updateScrollState(isScrolling: false)
        #expect(graphState.isScrolling == false)
    }

    @Test("GraphState: cachedYAxisDomain stores closed range correctly")
    func graphCachedYAxisDomain() {
        var graphState = GraphState()
        graphState.cachedYAxisDomain = 100.0...300.0

        #expect(graphState.cachedYAxisDomain?.lowerBound == 100.0)
        #expect(graphState.cachedYAxisDomain?.upperBound == 300.0)
    }

    @Test("GraphState: cachedYAxisTicks stores array correctly")
    func graphCachedYAxisTicks() {
        var graphState = GraphState()
        graphState.cachedYAxisTicks = [100.0, 150.0, 200.0, 250.0, 300.0]

        #expect(graphState.cachedYAxisTicks?.count == 5)
    }

    @Test("GraphState: cachedXAxisValues stores dates correctly")
    func graphCachedXAxisValues() {
        var graphState = GraphState()
        let dates = [Date(), Date().addingTimeInterval(86400)]
        graphState.cachedXAxisValues = dates

        #expect(graphState.cachedXAxisValues?.count == 2)
    }

    @Test("GraphState: dataChangeTrigger increments correctly")
    func graphDataChangeTrigger() {
        var graphState = GraphState()
        #expect(graphState.dataChangeTrigger == 0)

        graphState.dataChangeTrigger += 1
        #expect(graphState.dataChangeTrigger == 1)

        graphState.dataChangeTrigger += 1
        #expect(graphState.dataChangeTrigger == 2)
    }

    @Test("GraphState: chartHeight and annotationHeight can be set independently")
    func graphHeightProperties() {
        var graphState = GraphState()
        graphState.chartHeight = 250.0
        graphState.annotationHeight = 30.0

        #expect(graphState.chartHeight == 250.0)
        #expect(graphState.annotationHeight == 30.0)
    }

    @Test("GraphState: xScrollPosition defaults to current date")
    func graphXScrollPositionDefault() {
        let graphState = GraphState()
        let now = Date()
        let diff = abs(graphState.xScrollPosition.timeIntervalSince(now))
        #expect(diff < 1.0) // Within 1 second
    }

    @Test("GraphState: all period cases can be assigned")
    func graphAllPeriodCases() {
        var graphState = GraphState()

        for period in TimePeriod.allCases {
            graphState.selectedPeriod = period
            #expect(graphState.selectedPeriod == period)
        }
    }

    // MARK: - GoalState Tests

    @Test("GoalState: default values")
    func goalDefaults() {
        let goalState = GoalState()

        #expect(goalState.goalType == .gain)
        #expect(goalState.goalStartWeight == 0.0)
        #expect(goalState.goalWeight == 0.0)
        #expect(goalState.goalUnit == .lb)
        #expect(goalState.goalDelta == 0.0)
        #expect(goalState.goalProgress == 0.0)
        #expect(goalState.hasGoalSet == false)
    }

    @Test("GoalState: set goal with loss type")
    func goalLossType() {
        var goalState = GoalState()
        goalState.goalType = .lose
        goalState.goalStartWeight = 200.0
        goalState.goalWeight = 180.0
        goalState.goalDelta = -20.0
        goalState.goalProgress = 0.5
        goalState.hasGoalSet = true

        #expect(goalState.goalType == .lose)
        #expect(goalState.goalStartWeight == 200.0)
        #expect(goalState.goalWeight == 180.0)
        #expect(goalState.goalDelta == -20.0)
        #expect(goalState.goalProgress == 0.5)
        #expect(goalState.hasGoalSet == true)
    }

    @Test("GoalState: set goal with gain type")
    func goalGainType() {
        var goalState = GoalState()
        goalState.goalType = .gain
        goalState.goalStartWeight = 150.0
        goalState.goalWeight = 170.0
        goalState.goalDelta = 20.0
        goalState.goalProgress = 0.75
        goalState.hasGoalSet = true

        #expect(goalState.goalType == .gain)
        #expect(goalState.goalDelta == 20.0)
        #expect(goalState.goalProgress == 0.75)
    }

    @Test("GoalState: maintain goal type with zero delta")
    func goalMaintainType() {
        var goalState = GoalState()
        goalState.goalType = .maintain
        goalState.goalStartWeight = 180.0
        goalState.goalWeight = 180.0
        goalState.goalDelta = 0.0
        goalState.goalProgress = 1.0
        goalState.hasGoalSet = true

        #expect(goalState.goalType == .maintain)
        #expect(goalState.goalDelta == 0.0)
        #expect(goalState.goalProgress == 1.0)
    }

    @Test("GoalState: goalUnit can be set to kg")
    func goalUnitKg() {
        var goalState = GoalState()
        goalState.goalUnit = .kg

        #expect(goalState.goalUnit == .kg)
    }

    @Test("GoalState: goalProgress at boundary values")
    func goalProgressBoundaries() {
        var goalState = GoalState()

        goalState.goalProgress = 0.0
        #expect(goalState.goalProgress == 0.0)

        goalState.goalProgress = 1.0
        #expect(goalState.goalProgress == 1.0)

        // Over 100% (exceeded goal)
        goalState.goalProgress = 1.5
        #expect(goalState.goalProgress == 1.5)

        // Negative (regression)
        goalState.goalProgress = -0.1
        #expect(goalState.goalProgress == -0.1)
    }

    @Test("GoalState: none goal type")
    func goalNoneType() {
        var goalState = GoalState()
        goalState.goalType = .none
        goalState.hasGoalSet = false

        #expect(goalState.goalType == .none)
        #expect(goalState.hasGoalSet == false)
    }

    // MARK: - DataState Tests

    @Test("DataState: hasAnyEntries returns false when both summaries empty")
    func dataHasAnyEntriesFalseWhenEmpty() {
        let dataState = DataState()
        #expect(dataState.hasAnyEntries == false)
    }

    @Test("DataState: hasAnyEntries returns true when dailySummaries not empty")
    func dataHasAnyEntriesTrueWithDaily() {
        var dataState = DataState()
        dataState.dailySummaries = [DashboardTestFixtures.makeSummary()]

        #expect(dataState.hasAnyEntries == true)
    }

    @Test("DataState: hasAnyEntries returns true when monthlySummaries not empty")
    func dataHasAnyEntriesTrueWithMonthly() {
        var dataState = DataState()
        dataState.monthlySummaries = [DashboardTestFixtures.makeSummary(period: "2026-01")]

        #expect(dataState.hasAnyEntries == true)
    }

    @Test("DataState: hasAnyEntries returns true when both summaries not empty")
    func dataHasAnyEntriesTrueWithBoth() {
        var dataState = DataState()
        dataState.dailySummaries = [DashboardTestFixtures.makeSummary()]
        dataState.monthlySummaries = [DashboardTestFixtures.makeSummary(period: "2026-01")]

        #expect(dataState.hasAnyEntries == true)
    }

    @Test("DataState: hasAnyEntries returns true with nil entries in array")
    func dataHasAnyEntriesTrueWithNilEntries() {
        var dataState = DataState()
        dataState.dailySummaries = [nil, nil]

        #expect(dataState.hasAnyEntries == true) // Array is not empty even with nils
    }

    @Test("DataState: dailyCache stores and retrieves by period key")
    func dataDailyCacheStoreRetrieve() {
        var dataState = DataState()
        let summary = DashboardTestFixtures.makeSummary(period: "2026-03-01")
        dataState.dailyCache["2026-03-01"] = summary

        #expect(dataState.dailyCache["2026-03-01"] != nil)
        #expect(dataState.dailyCache["2026-03-02"] == nil)
    }

    @Test("DataState: monthlyCache stores and retrieves by period key")
    func dataMonthlyCacheStoreRetrieve() {
        var dataState = DataState()
        let summary = DashboardTestFixtures.makeSummary(period: "2026-01")
        dataState.monthlyCache["2026-01"] = summary

        #expect(dataState.monthlyCache["2026-01"] != nil)
        #expect(dataState.monthlyCache.count == 1)
    }

    @Test("DataState: latestWeightStored tracks stored weight in tenths")
    func dataLatestWeightStored() {
        var dataState = DataState()
        dataState.latestWeightStored = 1805 // 180.5 lbs

        #expect(dataState.latestWeightStored == 1805)
    }

    // MARK: - Cross-State Mutation Tests

    @Test("DashboardState: mutating one sub-state does not affect others")
    func mutatingOneSubStateIsolated() {
        var state = DashboardState()
        state.ui.isLoading = true
        state.metrics.activeMetricsCount = 4
        state.goal.hasGoalSet = true

        #expect(state.ui.isLoading == true)
        #expect(state.metrics.activeMetricsCount == 4)
        #expect(state.goal.hasGoalSet == true)
        // Others unchanged
        #expect(state.streak.activeStreakItemsCount == 6)
        #expect(state.graph.selectedPeriod == .month)
        #expect(state.data.dailySummaries.isEmpty)
    }

    @Test("DashboardState: replacing entire sub-state preserves other sub-states")
    func replacingSubStatePreservesOthers() {
        var state = DashboardState()
        state.ui.isEditMode = true

        var newMetrics = MetricsState()
        newMetrics.dashboardType = .dashboard4
        newMetrics.activeMetricsCount = 4
        state.metrics = newMetrics

        #expect(state.ui.isEditMode == true) // Preserved
        #expect(state.metrics.dashboardType == .dashboard4) // Updated
        #expect(state.metrics.activeMetricsCount == 4) // Updated
    }

    @Test("DashboardState: full state replacement")
    func fullStateReplacement() {
        var state = DashboardState()
        state.ui.isLoading = true
        state.goal.hasGoalSet = true

        state = DashboardState() // Reset to defaults

        #expect(state.ui.isLoading == false)
        #expect(state.goal.hasGoalSet == false)
    }

    // MARK: - Edge Cases

    @Test("UIState: setting selectedMetricLabel to empty string")
    func uiSelectedMetricLabelEmptyString() {
        var ui = UIState()
        ui.selectedMetricLabel = ""
        #expect(ui.selectedMetricLabel?.isEmpty == true)
    }

    @Test("UIState: goalCardPosition negative value")
    func uiGoalCardPositionNegative() {
        var ui = UIState()
        ui.goalCardPosition = -1
        #expect(ui.goalCardPosition == -1)
    }

    @Test("UIState: goalCardPosition large value")
    func uiGoalCardPositionLarge() {
        var ui = UIState()
        ui.goalCardPosition = 999
        #expect(ui.goalCardPosition == 999)
    }

    @Test("GoalState: very large weight values")
    func goalLargeWeightValues() {
        var goalState = GoalState()
        goalState.goalStartWeight = 99999.99
        goalState.goalWeight = 99999.99
        goalState.goalDelta = 0.0

        #expect(goalState.goalStartWeight == 99999.99)
        #expect(goalState.goalWeight == 99999.99)
    }

    @Test("GoalState: zero weight values")
    func goalZeroWeightValues() {
        var goalState = GoalState()
        goalState.goalStartWeight = 0.0
        goalState.goalWeight = 0.0

        #expect(goalState.goalStartWeight == 0.0)
        #expect(goalState.goalWeight == 0.0)
    }

    @Test("GraphState: setting all cached values then clearing")
    func graphSetAndClearCaches() {
        var graphState = GraphState()
        graphState.cachedYAxisDomain = 100.0...300.0
        graphState.cachedYAxisTicks = [100.0, 200.0, 300.0]
        graphState.cachedXAxisValues = [Date()]

        graphState.cachedYAxisDomain = nil
        graphState.cachedYAxisTicks = nil
        graphState.cachedXAxisValues = nil

        #expect(graphState.cachedYAxisDomain == nil)
        #expect(graphState.cachedYAxisTicks == nil)
        #expect(graphState.cachedXAxisValues == nil)
    }

    @Test("DataState: empty caches after removing entries")
    func dataEmptyCachesAfterRemoval() {
        var dataState = DataState()
        dataState.dailyCache["2026-03-01"] = DashboardTestFixtures.makeSummary()
        dataState.monthlyCache["2026-01"] = DashboardTestFixtures.makeSummary(period: "2026-01")

        dataState.dailyCache.removeAll()
        dataState.monthlyCache.removeAll()

        #expect(dataState.dailyCache.isEmpty)
        #expect(dataState.monthlyCache.isEmpty)
    }

    @Test("DataState: overwriting cache entry with same key")
    func dataOverwriteCacheEntry() {
        var dataState = DataState()
        let s1 = DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800)
        let s2 = DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1850)

        dataState.dailyCache["2026-03-01"] = s1
        #expect(dataState.dailyCache["2026-03-01"]?.weight == 1800)

        dataState.dailyCache["2026-03-01"] = s2
        #expect(dataState.dailyCache["2026-03-01"]?.weight == 1850)
        #expect(dataState.dailyCache.count == 1)
    }
}
