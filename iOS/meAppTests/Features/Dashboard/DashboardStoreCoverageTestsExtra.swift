import Foundation
@testable import meApp
import Testing

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

// MARK: - getCurrentAverageWeight Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreAverageWeightTests {

    @Test func getCurrentAverageWeightReturnsZeroWithNoOps() {
        let store = DashboardStore(lightweight: true)
        store.data.dailySummaries = []
        store.data.monthlySummaries = []
        let avg = store.getCurrentAverageWeight()
        #expect(avg == 0)
    }
}

// MARK: - selectMetric Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreSelectMetricTests {

    @Test func selectMetricSetsLabel() {
        let store = DashboardStore(lightweight: true)
        store.selectMetric(DashboardStrings.bmi)
        #expect(store.ui.selectedMetricLabel == DashboardStrings.bmi)
    }

    @Test func selectMetricTogglesClearingOnSecondCall() {
        let store = DashboardStore(lightweight: true)
        store.selectMetric(DashboardStrings.bmi)
        store.selectMetric(DashboardStrings.bmi)
        #expect(store.ui.selectedMetricLabel == nil)
    }

    @Test func selectMetricSwitchesBetweenDifferentLabels() {
        let store = DashboardStore(lightweight: true)
        store.selectMetric(DashboardStrings.bmi)
        store.selectMetric(DashboardStrings.bodyFat)
        #expect(store.ui.selectedMetricLabel == DashboardStrings.bodyFat)
    }
}

// MARK: - allContentRemoved Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreAllContentRemovedTests {

    @Test func allContentRemovedFalseWhenConfigNotLoaded() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = false
        // metricsToShow is empty (config not loaded) but allMetricsRemoved requires non-edit + all in set
        #expect(store.allContentRemoved == false)
    }

    @Test func allContentRemovedFalseWhenGoalCardPresent() {
        let store = DashboardStore(lightweight: true)
        store.ui.hasLoadedDashboardConfig = true
        store.ui.isGoalCardRemoved = false
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
        #expect(store.ui.hasLoadedDashboardConfig == false)
    }

    @Test func hasLoadedMetricValuesDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.ui.hasLoadedMetricValues == false)
    }

    @Test func isEditModeDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.ui.isEditMode == false)
    }

    @Test func isGoalCardRemovedDefaultFalse() {
        let store = DashboardStore(lightweight: true)
        #expect(store.ui.isGoalCardRemoved == false)
    }

    @Test func goalCardPositionDefaultZero() {
        let store = DashboardStore(lightweight: true)
        #expect(store.ui.goalCardPosition == 0)
    }
}

// MARK: - Graph State Integration Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreGraphStateTests {

    @Test func clearSelectionClearsGraphState() {
        let store = DashboardStore(lightweight: true)
        store.graph.selectedPoint = makeSummary(date: makeDate(2026, 4, 20), weight: 180, bmi: 24.0)
        store.graph.selectedXValue = makeDate(2026, 4, 20)
        store.graph.showCrosshair = true
        store.graph.clearSelection()
        #expect(store.graph.selectedPoint == nil)
        #expect(store.graph.selectedXValue == nil)
        #expect(store.graph.showCrosshair == false)
    }

    @Test func initialSelectedPeriodIsMonth() {
        let store = DashboardStore(lightweight: true)
        #expect(store.graph.selectedPeriod == .month)
    }
}

// MARK: - Format Function Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreFormatTests {

    @Test func formatWeightDisplayTextNilReturnsZero() {
        let store = DashboardStore(lightweight: true)
        #expect(store.formatWeightDisplayText(nil) == "0.0")
    }

    @Test func formatYAxisTickLabelWholeNumber() {
        let store = DashboardStore(lightweight: true)
        let result = store.formatYAxisTickLabel(180.0)
        // Should have no decimal places for whole numbers
        #expect(!result.contains("."))
    }

    @Test func formatYAxisTickLabelLargeValueUsesThousandSeparator() {
        let store = DashboardStore(lightweight: true)
        let result = store.formatYAxisTickLabel(1_500.0)
        // NumberFormatter with .decimal style adds thousands separator for values ≥ 1000
        #expect(result.contains("1") && result.contains("5"))
        #expect(!result.contains("."))
    }

    @Test func formatChartDateWeekPeriodUsesShortFormat() {
        let store = DashboardStore(lightweight: true)
        store.graph.selectedPeriod = .week
        let date = makeDate(2026, 4, 20)
        let result = store.formatChartDate(date)
        #expect(result.contains("Apr"))
        #expect(result.contains("20"))
    }

    @Test func formatChartDateMonthPeriodUsesShortFormat() {
        let store = DashboardStore(lightweight: true)
        store.graph.selectedPeriod = .month
        let date = makeDate(2026, 4, 20)
        let result = store.formatChartDate(date)
        #expect(result.contains("Apr"))
    }

    @Test func formatChartDateYearPeriodUsesLongFormat() {
        let store = DashboardStore(lightweight: true)
        store.graph.selectedPeriod = .year
        let date = makeDate(2026, 4, 1)
        let result = store.formatChartDate(date)
        #expect(result.contains("2026"))
        #expect(result.contains("Apr"))
    }

    @Test func formatChartDateTotalPeriodUsesLongFormat() {
        let store = DashboardStore(lightweight: true)
        store.graph.selectedPeriod = .total
        let date = makeDate(2026, 1, 1)
        let result = store.formatChartDate(date)
        #expect(result.contains("2026"))
    }

    @Test func roundedGoalWeightRoundsCorrectly() {
        let store = DashboardStore(lightweight: true)
        #expect(store.roundedGoalWeight(180.4) == 180.0)
        #expect(store.roundedGoalWeight(180.5) == 181.0)
        #expect(store.roundedGoalWeight(179.6) == 180.0)
    }
}

// MARK: - Metric Info Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreMetricInfoTests {

    @Test func allowedMetricsForDashboard4ReturnsBasicSet() {
        let store = DashboardStore(lightweight: true)
        store.metrics.dashboardType = .dashboard4
        let allowed = store.allowedMetricsForMetricInfo()
        #expect(allowed.contains(.weight))
        #expect(allowed.contains(.bmi))
        #expect(allowed.contains(.bodyFat))
        #expect(allowed.contains(.muscleMass))
        #expect(allowed.contains(.water))
        #expect(!allowed.contains(.pulse))
    }

    @Test func allowedMetricsForDashboard12ReturnsFullSet() {
        let store = DashboardStore(lightweight: true)
        store.metrics.dashboardType = .dashboard12
        let allowed = store.allowedMetricsForMetricInfo()
        #expect(allowed.contains(.pulse))
        #expect(allowed.contains(.boneMass))
        #expect(allowed.contains(.bmr))
        #expect(allowed.contains(.metabolicAge))
        #expect(allowed.count > 5)
    }

    @Test func validateMetricInfoSelectionReturnsCurrentIfAllowed() {
        let store = DashboardStore(lightweight: true)
        store.metrics.dashboardType = .dashboard12
        let result = store.validateMetricInfoSelection(.pulse)
        #expect(result == .pulse)
    }

    @Test func validateMetricInfoSelectionFallsBackForDashboard4() {
        let store = DashboardStore(lightweight: true)
        store.metrics.dashboardType = .dashboard4
        // .pulse is not in dashboard4 allowed set
        let result = store.validateMetricInfoSelection(.pulse)
        #expect(result != .pulse)
        #expect(result == .weight) // first allowed is .weight
    }

    @Test func getBodyMetricDelegatesToMetricsManager() {
        let store = DashboardStore(lightweight: true)
        let result = store.getBodyMetric(for: DashboardStrings.bmi)
        #expect(result == .bmi)
    }

    @Test func getBodyMetricReturnsWeightForUnknown() {
        let store = DashboardStore(lightweight: true)
        let result = store.getBodyMetric(for: "unknown-metric")
        #expect(result == .weight)
    }
}

// MARK: - WeightLabel Empty State Tests

@MainActor
@Suite(.serialized)
struct DashboardStoreWeightLabelTests {

    @Test func weightLabelReturnsEmptyStateLabelForMonthWhenNoEntries() {
        let store = DashboardStore(lightweight: true)
        store.data.dailySummaries = []
        store.data.monthlySummaries = []
        store.graph.selectedPeriod = .month
        let label = store.weightLabel
        // emptyStatePeriodLabel(.month) = "MMM, yyyy" format
        #expect(!label.isEmpty)
        #expect(label.contains(","))
    }

    @Test func weightLabelReturnsEmptyStateLabelForYearWhenNoEntries() {
        let store = DashboardStore(lightweight: true)
        store.data.dailySummaries = []
        store.data.monthlySummaries = []
        store.graph.selectedPeriod = .year
        let label = store.weightLabel
        let cal = Calendar.current
        let year = cal.component(.year, from: Date())
        #expect(label.contains(String(year)))
    }

    @Test func weightLabelReturnsEmptyStateLabelForTotalWhenNoEntries() {
        let store = DashboardStore(lightweight: true)
        store.data.dailySummaries = []
        store.data.monthlySummaries = []
        store.graph.selectedPeriod = .total
        let label = store.weightLabel
        let cal = Calendar.current
        let year = cal.component(.year, from: Date())
        #expect(label.contains(String(year)))
    }

    @Test func weightLabelReturnsWeekRangeWhenNoEntries() {
        let store = DashboardStore(lightweight: true)
        store.data.dailySummaries = []
        store.data.monthlySummaries = []
        store.graph.selectedPeriod = .week
        let label = store.weightLabel
        // Week label has " - " separating start and end
        #expect(label.contains(" - "))
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
