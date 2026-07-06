import Foundation
@testable import meApp
import Testing

// Store-level coverage for DashboardStore members that survived the manager
// decomposition. Suites that exercised behaviour since moved onto the managers
// (metric selection, formatting, metric-info gating, weight labels, average
// weight) were removed here because that behaviour is now covered by the
// dedicated manager suites (DashboardFormatterTests, DashboardMetricsManagerTests,
// DashboardGoalManagerTests, …). State access uses the current `store.state.*`
// shape rather than the pre-refactor `store.ui/.graph/.data` accessors.

// MARK: - Visible Domain Length Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreVisibleDomainTests {

    @Test func visibleDomainLengthDelegatesToGraphManager() {
        let store = DashboardStore(lightweight: true)
        let managerLength = store.graphManager.visibleDomainLength(for: .month)
        let storeLength = store.visibleDomainLength(for: .month)
        #expect(storeLength == managerLength)
    }

    @Test func visibleDomainLengthForAllPeriods() {
        let store = DashboardStore(lightweight: true)
        for period in [TimePeriod.week, .month, .year, .total] {
            let length = store.visibleDomainLength(for: period)
            #expect(length > 0)
        }
    }
}

// MARK: - allContentRemoved Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreAllContentRemovedTests {

    @Test func allContentRemovedFalseWhenConfigNotLoaded() {
        let store = DashboardStore(lightweight: true)
        store.state.ui.hasLoadedDashboardConfig = false
        // metricsToShow is empty (config not loaded) but allMetricsRemoved requires non-edit + all in set
        #expect(store.allContentRemoved == false)
    }

    @Test func allContentRemovedFalseWhenGoalCardPresent() {
        let store = DashboardStore(lightweight: true)
        store.state.ui.hasLoadedDashboardConfig = true
        store.state.ui.isGoalCardRemoved = false
        #expect(store.allContentRemoved == false)
    }
}

// MARK: - invalidateContinuousOperationsCache Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreCacheTests {

    @Test func invalidateContinuousOperationsCacheClearsCache() {
        let store = DashboardStore(lightweight: true)
        // Prime the cache by reading continuousOperations
        _ = store.continuousOperations
        // Invalidate
        store.invalidateContinuousOperationsCache()
        // After invalidation, reading again should work (just returning empty since no data)
        let ops = store.continuousOperations
        #expect(ops.isEmpty)
    }
}

// MARK: - UIState Flags Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreUIStateFlagTests {

    @Test func hasLoadedDashboardConfigDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.state.ui.hasLoadedDashboardConfig == false)
    }

    @Test func hasLoadedMetricValuesDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.state.ui.hasLoadedMetricValues == false)
    }

    @Test func isEditModeDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.state.ui.isEditMode == false)
    }

    @Test func isGoalCardRemovedDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.state.ui.isGoalCardRemoved == false)
    }

    @Test func goalCardPositionDefaultZero() {
        let store = DashboardStore(lightweight: true)
        #expect(store.state.ui.goalCardPosition == 0)
    }
}

// MARK: - Graph State Integration Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreGraphStateTests {

    @Test func clearSelectionClearsGraphState() {
        let store = DashboardStore(lightweight: true)
        store.state.graph.selectedPoint = makeSummary(date: makeDate(2026, 4, 20), weight: 180, bmi: 24.0)
        store.state.graph.selectedXValue = makeDate(2026, 4, 20)
        store.state.graph.showCrosshair = true
        store.state.graph.clearSelection()
        #expect(store.state.graph.selectedPoint == nil)
        #expect(store.state.graph.selectedXValue == nil)
        #expect(store.state.graph.showCrosshair == false)
    }

    @Test func initialSelectedPeriodIsMonth() {
        let store = DashboardStore(lightweight: true)
        #expect(store.state.graph.selectedPeriod == .month)
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
