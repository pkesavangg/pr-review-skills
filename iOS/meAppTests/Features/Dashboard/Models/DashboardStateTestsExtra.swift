import Foundation
@testable import meApp
import SwiftUI
import Testing

// MARK: - DashboardState Tests (continued)

@MainActor
extension DashboardStateTests {

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
