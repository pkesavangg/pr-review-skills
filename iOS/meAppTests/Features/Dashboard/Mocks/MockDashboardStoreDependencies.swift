import Foundation
@testable import meApp

@MainActor
final class MockDashboardFormatter: DashboardFormatterProtocol {
    var yAxisTickLabelResult: String?
    var roundedGoalWeightResult: Double?
    var chartDateResult: String = "mock-date"
    var metricInfoSingleDateResult: String = "mock-single"
    var metricInfoDateLabelResult: String = "mock-label"
    var parsedEntryDate: Date?
    var dashboardEntryResult = true
    var formattedMetricValueResult: String?
    // swiftlint:disable:next large_tuple
    private(set) var lastMetricInfoDateLabelArgs: (
        entryDate: Date?,
        isFromHistory: Bool,
        period: TimePeriod,
        selectedPointDate: Date?,
        crosshairDate: Date?,
        isLatestDaySelected: Bool,
        weightLabel: String
    )?
    private(set) var lastChartDatePeriod: TimePeriod?

    func formatYAxisTickLabel(_ weight: Double) -> String { yAxisTickLabelResult ?? "\(weight)" }
    func roundedGoalWeight(_ weight: Double) -> Double { roundedGoalWeightResult ?? weight }
    func formatChartDate(_ date: Date, period: TimePeriod) -> String {
        lastChartDatePeriod = period
        return chartDateResult
    }
    func formatMetricInfoSingleDate(_ date: Date, period: TimePeriod) -> String { metricInfoSingleDateResult }
    func formatMetricInfoDateLabel(
        entryDate: Date?,
        isFromHistory: Bool,
        period: TimePeriod,
        selectedPointDate: Date?,
        crosshairDate: Date?,
        isLatestDaySelected: Bool,
        weightLabel: String
    ) -> String {
        lastMetricInfoDateLabelArgs = (
            entryDate: entryDate,
            isFromHistory: isFromHistory,
            period: period,
            selectedPointDate: selectedPointDate,
            crosshairDate: crosshairDate,
            isLatestDaySelected: isLatestDaySelected,
            weightLabel: weightLabel
        )
        return metricInfoDateLabelResult
    }
    func parseEntryDate(from entryDTO: BathScaleOperationDTO) -> Date? { parsedEntryDate }
    func isDashboardEntry(_ entryDTO: BathScaleOperationDTO) -> Bool { dashboardEntryResult }
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        formattedMetricValueResult ?? metric.value
    }
    func composeMetricInfoLabel(prefix: String, dateText: String) -> String { "\(prefix) \(dateText)" }
    func selectionPrefix(for period: TimePeriod, isLatestDaySelected: Bool) -> String {
        // Mirror the real DashboardFormatter so DisplayManager tests assert against the
        // production hybrid "latest entry" / "day average" / "month average" labels.
        switch period {
        case .week, .month: return isLatestDaySelected ? "latest entry" : "day average"
        case .year, .total: return "month average"
        }
    }
}

@MainActor
final class MockDashboardCacheManager: DashboardCacheManagerProtocol {
    private(set) var invalidateContinuousOpsCalls = 0
    private(set) var invalidateChartSeriesCalls = 0
    private(set) var clearAllCachesCalls = 0
    private(set) var setProductContextCalls = 0
    private(set) var lastProductContext: (productType: EntryType, babyProfileId: String?)?
    private(set) var getVisibleOperationsCalls = 0
    private(set) var getChartSeriesDataCalls = 0
    private(set) var getLabelDateRangeOperationsCalls = 0
    private(set) var lastVisibleIsScrolling: Bool?
    // swiftlint:disable:next large_tuple
    private(set) var lastChartSeriesRequest: (
        isScrolling: Bool,
        isProcessingScrollEnd: Bool,
        period: TimePeriod,
        selectedMetric: String?,
        operationsCount: Int,
        yAxisDomain: ClosedRange<Double>?
    )?
    private(set) var lastLabelDateRangeRequest: (period: TimePeriod, scrollPosition: Date?)?
    private var boolCache: [String: Bool] = [:]
    var visibleOperationsOverride: [BathScaleWeightSummary]?
    var chartSeriesOverride: [GraphSeries]?
    var labelDateRangeOverride: DateRangeOperationsResult?

    func setProductContext(productType: EntryType, babyProfileId: String?) {
        setProductContextCalls += 1
        lastProductContext = (productType: productType, babyProfileId: babyProfileId)
    }

    func getContinuousOperations(for period: TimePeriod, getOperations: () -> [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        getOperations()
    }

    func invalidateContinuousOperationsCache() {
        invalidateContinuousOpsCalls += 1
    }

    func getVisibleOperations(isScrolling: Bool, getVisibleOperations: () -> [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        getVisibleOperationsCalls += 1
        lastVisibleIsScrolling = isScrolling
        return visibleOperationsOverride ?? getVisibleOperations()
    }

    func getChartSeriesData(isScrolling: Bool, isProcessingScrollEnd: Bool, period: TimePeriod, selectedMetric: String?, operationsCount: Int, yAxisDomain: ClosedRange<Double>?, getChartSeries: () -> [GraphSeries]) -> [GraphSeries] {
        getChartSeriesDataCalls += 1
        lastChartSeriesRequest = (
            isScrolling: isScrolling,
            isProcessingScrollEnd: isProcessingScrollEnd,
            period: period,
            selectedMetric: selectedMetric,
            operationsCount: operationsCount,
            yAxisDomain: yAxisDomain
        )
        return chartSeriesOverride ?? getChartSeries()
    }

    func invalidateChartSeriesCache() {
        invalidateChartSeriesCalls += 1
    }

    func getLabelDateRangeOperations(period: TimePeriod, scrollPosition: Date?, getOperations: () -> DateRangeOperationsResult) -> DateRangeOperationsResult {
        getLabelDateRangeOperationsCalls += 1
        lastLabelDateRangeRequest = (period: period, scrollPosition: scrollPosition)
        return labelDateRangeOverride ?? getOperations()
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

@MainActor
final class MockDashboardDisplayManager: DashboardDisplayManaging {
    private(set) var updateMetricsForCurrentViewCalls = 0
    private(set) var updateMetricsWithVisibleRegionAverageCalls = 0
    private(set) var resetMetricsToLatestEntryCalls = 0
    private(set) var handleBpmPointSelectionCalls = 0
    var displayWeight: Double?
    var weightLabel: String = ""
    var weightDisplayLabel: String = ""
    var displayUnitText: String = ""
    var activeMonthInterval: DateInterval?
    var operationsForLabelDateRange: [BathScaleWeightSummary] = []
    var currentBpmClassification: AhaPressureClass = .normal
    var bpmDisplayValues: BpmDisplayData?
    var bpmDisplayData: BpmDisplayData? {
        get { bpmDisplayValues }
        set { bpmDisplayValues = newValue }
    }

    func getCurrentAverageWeight() -> Double { 0 }
    func updateVisibleDataAfterScroll() {}
    func getOperationsForLabelDateRange() -> [BathScaleWeightSummary] { operationsForLabelDateRange }
    func formatWeightDisplayText(_ weight: Double?) -> String { weight.map { String($0) } ?? "0.0" }
    func formatYAxisTickLabel(_ weight: Double) -> String { String(weight) }
    func formatChartDate(_ date: Date) -> String { "mock-date" }
    func roundedGoalWeight(_ weight: Double) -> Double { weight }
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String { metric.value }
    func createEntryForMetricInfo(metricLabel: String?) -> Entry {
        Entry(entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(), accountId: "dashboard", operationType: OperationType.create.rawValue)
    }
    func createEntryForMetricInfoAsync(metricLabel: String?) async -> Entry { createEntryForMetricInfo(metricLabel: metricLabel) }
    func metricInfoDateLabel(for entryDTO: BathScaleOperationDTO) -> String { "mock-label" }
    func allowedMetricsForMetricInfo() -> [BodyMetric] { [.weight] }
    func validateMetricInfoSelection(_ current: BodyMetric) -> BodyMetric { current }
    func getBodyMetric(for metricLabel: String) -> BodyMetric { .weight }
    func updateMetricsForCurrentView() { updateMetricsForCurrentViewCalls += 1 }
    func updateMetricsWithVisibleRegionAverage() { updateMetricsWithVisibleRegionAverageCalls += 1 }
    func resetMetricsToLatestEntry() { resetMetricsToLatestEntryCalls += 1 }
    func handleBpmPointSelection(_ point: BathScaleWeightSummary) { handleBpmPointSelectionCalls += 1 }
    func getBpmDisplayValues() -> BpmDisplayData? { bpmDisplayValues }
}
