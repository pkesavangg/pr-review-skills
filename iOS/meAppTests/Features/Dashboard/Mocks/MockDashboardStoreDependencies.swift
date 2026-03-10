import Foundation
@testable import meApp

@MainActor
final class MockDashboardFormatter: DashboardFormatterProtocol {
    func formatYAxisTickLabel(_ weight: Double) -> String { "\(weight)" }
    func roundedGoalWeight(_ weight: Double) -> Double { weight }
    func formatChartDate(_ date: Date, period: TimePeriod) -> String { "mock-date" }
    func formatMetricInfoSingleDate(_ date: Date, period: TimePeriod) -> String { "mock-single" }
    func formatMetricInfoDateLabel(
        entryDate: Date?,
        isFromHistory: Bool,
        period: TimePeriod,
        selectedPointDate: Date?,
        crosshairDate: Date?,
        weightLabel: String
    ) -> String { "mock-label" }
    func parseEntryDate(from entryDTO: BathScaleOperationDTO) -> Date? { nil }
    func isDashboardEntry(_ entryDTO: BathScaleOperationDTO) -> Bool { true }
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String { metric.value }
    func composeMetricInfoLabel(prefix: String, dateText: String) -> String { "\(prefix) \(dateText)" }
    func selectionPrefix(for period: TimePeriod) -> String { "Avg" }
}

@MainActor
final class MockDashboardCacheManager: DashboardCacheManagerProtocol {
    private(set) var invalidateContinuousOpsCalls = 0
    private(set) var invalidateChartSeriesCalls = 0
    private(set) var clearAllCachesCalls = 0
    private var boolCache: [String: Bool] = [:]

    func getContinuousOperations(for period: TimePeriod, getOperations: () -> [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        getOperations()
    }

    func invalidateContinuousOperationsCache() {
        invalidateContinuousOpsCalls += 1
    }

    func getVisibleOperations(isScrolling: Bool, getVisibleOperations: () -> [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        getVisibleOperations()
    }

    func getChartSeriesData(isScrolling: Bool, isProcessingScrollEnd: Bool, period: TimePeriod, selectedMetric: String?, operationsCount: Int, yAxisDomain: ClosedRange<Double>?, getChartSeries: () -> [GraphSeries]) -> [GraphSeries] {
        getChartSeries()
    }

    func invalidateChartSeriesCache() {
        invalidateChartSeriesCalls += 1
    }

    func getLabelDateRangeOperations(period: TimePeriod, scrollPosition: Date?, getOperations: () -> DateRangeOperationsResult) -> DateRangeOperationsResult {
        DateRangeOperationsResult(
            operations: [],
            cachedPeriod: period,
            cachedScrollPos: scrollPosition ?? Date(),
            cachedOps: []
        )
    }

    func getBool(forKey key: String) -> Bool {
        boolCache[key] ?? false
    }

    func setBool(_ value: Bool, forKey key: String) {
        boolCache[key] = value
    }

    func clearAllCaches() {
        clearAllCachesCalls += 1
    }
}
