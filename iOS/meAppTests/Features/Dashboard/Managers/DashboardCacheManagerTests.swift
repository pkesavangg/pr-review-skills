import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardCacheManagerTests {

    private func makeSUT() -> DashboardCacheManager {
        DashboardCacheManager()
    }

    private func makeSummary(
        period: String = "2026-03-01",
        weight: Double = 1800
    ) -> BathScaleWeightSummary {
        DashboardTestFixtures.makeSummary(period: period, weight: weight)
    }

    private func makeSeries(
        dateString: String = "2026-03-01",
        value: Double = 180.0,
        series: String = "weight"
    ) -> [GraphSeries] {
        [GraphSeries(
            date: DateTimeTools.getDateFromDateString(dateString, format: "yyyy-MM-dd"),
            value: value,
            series: series
        )]
    }

    private func makeDateRangeResult(
        period: TimePeriod = .week,
        scrollPosition: Date = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd"),
        ops: [BathScaleWeightSummary]
    ) -> DateRangeOperationsResult {
        DateRangeOperationsResult(
            operations: ops,
            cachedPeriod: period,
            cachedScrollPos: scrollPosition,
            cachedOps: ops
        )
    }

    @Test("getContinuousOperations: first load caches result")
    func getContinuousOperationsFirstLoadCaches() {
        let sut = makeSUT()
        var callCount = 0

        let result = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary()]
        }

        #expect(result.count == 1)
        #expect(callCount == 1)
    }

    @Test("getContinuousOperations: cache hit reuses same-period non-empty data")
    func getContinuousOperationsCacheHit() {
        let sut = makeSUT()
        var callCount = 0

        let first = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-01", weight: 1800)]
        }
        let second = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-02", weight: 1810)]
        }

        #expect(first.map(\.period) == ["2026-03-01"])
        #expect(second.map(\.period) == ["2026-03-01"])
        #expect(callCount == 1)
    }

    @Test("getContinuousOperations: empty cache result recalculates on next request")
    func getContinuousOperationsEmptyResultRecalculates() {
        let sut = makeSUT()
        var callCount = 0

        let first = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return []
        }
        let second = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary()]
        }

        #expect(first.isEmpty)
        #expect(second.count == 1)
        #expect(callCount == 2)
    }

    @Test("getContinuousOperations: period change invalidates cached result")
    func getContinuousOperationsPeriodChangeRecalculates() {
        let sut = makeSUT()
        var callCount = 0

        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }
        let second = sut.getContinuousOperations(for: .month) {
            callCount += 1
            return [makeSummary(period: "2026-03-15")]
        }

        #expect(second.map(\.period) == ["2026-03-15"])
        #expect(callCount == 2)
    }

    @Test("invalidateContinuousOperationsCache: clears dependent caches")
    func invalidateContinuousOperationsCacheClearsDependentCaches() {
        let sut = makeSUT()
        var continuousCalls = 0
        var visibleCalls = 0
        var chartCalls = 0
        var labelRangeCalls = 0
        let scroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }
        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }
        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            chartCalls += 1
            return makeSeries()
        }
        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary()])
        }

        sut.invalidateContinuousOperationsCache()

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }
        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }
        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            chartCalls += 1
            return makeSeries(dateString: "2026-03-02", value: 181.0)
        }
        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary(period: "2026-03-02")])
        }

        #expect(continuousCalls == 2)
        #expect(visibleCalls == 2)
        #expect(chartCalls == 2)
        #expect(labelRangeCalls == 2)
    }

    @Test("getVisibleOperations: non-scrolling requests always refresh")
    func getVisibleOperationsNotScrollingRefreshes() {
        let sut = makeSUT()
        var callCount = 0

        _ = sut.getVisibleOperations(isScrolling: false) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }
        let second = sut.getVisibleOperations(isScrolling: false) {
            callCount += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(second.map(\.period) == ["2026-03-02"])
        #expect(callCount == 2)
    }

    @Test("getVisibleOperations: scrolling uses fresh cache within debounce window")
    func getVisibleOperationsScrollingUsesCache() {
        let sut = makeSUT()
        var callCount = 0

        let first = sut.getVisibleOperations(isScrolling: true) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }
        let second = sut.getVisibleOperations(isScrolling: true) {
            callCount += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(first.map(\.period) == ["2026-03-01"])
        #expect(second.map(\.period) == ["2026-03-01"])
        #expect(callCount == 1)
    }

    @Test("getVisibleOperations: scrolling recomputes after cache window expires")
    func getVisibleOperationsScrollingStaleCacheRecomputes() async {
        let sut = makeSUT()
        var callCount = 0

        _ = sut.getVisibleOperations(isScrolling: true) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }
        try? await Task.sleep(nanoseconds: 300_000_000)
        let second = sut.getVisibleOperations(isScrolling: true) {
            callCount += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(second.map(\.period) == ["2026-03-02"])
        #expect(callCount == 2)
    }

    @Test("getChartSeriesData: processing scroll end returns cached data without recomputing")
    func getChartSeriesDataProcessingScrollEndUsesCache() {
        let sut = makeSUT()
        var callCount = 0

        let primed = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries()
        }

        let second = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: true,
            period: .month,
            selectedMetric: "bmi",
            operationsCount: 999,
            yAxisDomain: 0.0 ... 1.0
        ) {
            callCount += 1
            return makeSeries(dateString: "2026-03-02", value: 181.0, series: "bmi")
        }

        #expect(second == primed)
        #expect(callCount == 1)
    }

    @Test("getChartSeriesData: empty processing scroll end returns empty when cache is empty")
    func getChartSeriesDataProcessingScrollEndEmptyWithoutCache() {
        let sut = makeSUT()
        var callCount = 0

        let result = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: true,
            period: .week,
            selectedMetric: nil,
            operationsCount: 0,
            yAxisDomain: nil
        ) {
            callCount += 1
            return makeSeries()
        }

        #expect(result.isEmpty)
        #expect(callCount == 0)
    }

    @Test("getChartSeriesData: valid cached metadata returns cached series")
    func getChartSeriesDataCacheHitByMetadata() {
        let sut = makeSUT()
        var callCount = 0

        let first = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 3,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries()
        }
        let second = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 3,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(dateString: "2026-03-02", value: 181.0)
        }

        #expect(second == first)
        #expect(callCount == 1)
    }

    @Test("getChartSeriesData: scrolling uses cache only when metric is unchanged")
    func getChartSeriesDataScrollingMetricChangeInvalidatesCache() {
        let sut = makeSUT()
        var callCount = 0

        let first = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(series: "weight")
        }
        let cached = sut.getChartSeriesData(
            isScrolling: true,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(series: "weight-2")
        }
        let metricChanged = sut.getChartSeriesData(
            isScrolling: true,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "bmi",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(series: "bmi")
        }

        #expect(cached == first)
        #expect(metricChanged != first)
        #expect(callCount == 2)
    }

    @Test("getChartSeriesData: data-count and y-axis changes invalidate cache")
    func getChartSeriesDataMetadataChangesRecalculate() {
        let sut = makeSUT()
        var callCount = 0

        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries()
        }
        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 2,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(dateString: "2026-03-02", value: 181.0)
        }
        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 2,
            yAxisDomain: 160.0 ... 200.0
        ) {
            callCount += 1
            return makeSeries(dateString: "2026-03-03", value: 182.0)
        }

        #expect(callCount == 3)
    }

    @Test("getChartSeriesData: period change invalidates cached series")
    func getChartSeriesDataPeriodChangeRecalculates() {
        let sut = makeSUT()
        var callCount = 0

        let first = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(series: "week")
        }
        let second = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .month,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(dateString: "2026-03-15", value: 181.0, series: "month")
        }

        #expect(first != second)
        #expect(callCount == 2)
    }

    @Test("getChartSeriesData: scrolling metric deselection invalidates cache")
    func getChartSeriesDataScrollingMetricDeselectionInvalidatesCache() {
        let sut = makeSUT()
        var callCount = 0

        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(series: "weight")
        }
        let second = sut.getChartSeriesData(
            isScrolling: true,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: nil,
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(series: "all")
        }

        #expect(second.map(\.series) == ["all"])
        #expect(callCount == 2)
    }

    @Test("invalidateChartSeriesCache: clears chart series metadata")
    func invalidateChartSeriesCacheForcesRefresh() {
        let sut = makeSUT()
        var callCount = 0

        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries()
        }

        sut.invalidateChartSeriesCache()

        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            callCount += 1
            return makeSeries(dateString: "2026-03-02", value: 181.0)
        }

        #expect(callCount == 2)
    }

    @Test("getLabelDateRangeOperations: cache hit reuses same period and scroll position")
    func getLabelDateRangeOperationsCacheHit() {
        let sut = makeSUT()
        let scroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        var callCount = 0

        let first = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            callCount += 1
            return makeDateRangeResult(
                period: .week,
                scrollPosition: scroll,
                ops: [makeSummary(period: "2026-03-01")]
            )
        }
        let second = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            callCount += 1
            return makeDateRangeResult(
                period: .week,
                scrollPosition: scroll,
                ops: [makeSummary(period: "2026-03-02")]
            )
        }

        #expect(first.cachedOps.map(\.period) == ["2026-03-01"])
        #expect(second.cachedOps.map(\.period) == ["2026-03-01"])
        #expect(callCount == 1)
    }

    @Test("getLabelDateRangeOperations: empty cached operations do not count as a valid hit")
    func getLabelDateRangeOperationsEmptyCacheRecalculates() {
        let sut = makeSUT()
        let scroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        var callCount = 0

        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            callCount += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [])
        }
        let second = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            callCount += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary()])
        }

        #expect(second.cachedOps.count == 1)
        #expect(callCount == 2)
    }

    @Test("getLabelDateRangeOperations: period or scroll position changes invalidate cache")
    func getLabelDateRangeOperationsInputChangesRecalculate() {
        let sut = makeSUT()
        let firstScroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let secondScroll = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        var callCount = 0

        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: firstScroll) {
            callCount += 1
            return makeDateRangeResult(period: .week, scrollPosition: firstScroll, ops: [makeSummary(period: "2026-03-01")])
        }
        _ = sut.getLabelDateRangeOperations(period: .month, scrollPosition: firstScroll) {
            callCount += 1
            return makeDateRangeResult(period: .month, scrollPosition: firstScroll, ops: [makeSummary(period: "2026-03-15")])
        }
        _ = sut.getLabelDateRangeOperations(period: .month, scrollPosition: secondScroll) {
            callCount += 1
            return makeDateRangeResult(period: .month, scrollPosition: secondScroll, ops: [makeSummary(period: "2026-03-16")])
        }

        #expect(callCount == 3)
    }

    @Test("getBool and setBool: round-trip values through user defaults")
    func getAndSetBoolRoundTrip() {
        let sut = makeSUT()
        let key = "DashboardCacheManagerTests.bool.\(UUID().uuidString)"

        sut.setBool(true, forKey: key)

        #expect(sut.getBool(forKey: key) == true)

        UserDefaults.standard.removeObject(forKey: key)
    }

    @Test("clearAllCaches: resets all cache layers")
    func clearAllCachesResetsEverything() {
        let sut = makeSUT()
        var continuousCalls = 0
        var visibleCalls = 0
        var chartCalls = 0
        var labelRangeCalls = 0
        let scroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }
        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }
        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            chartCalls += 1
            return makeSeries()
        }
        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary()])
        }

        sut.clearAllCaches()

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }
        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }
        _ = sut.getChartSeriesData(
            isScrolling: false,
            isProcessingScrollEnd: false,
            period: .week,
            selectedMetric: "weight",
            operationsCount: 1,
            yAxisDomain: 170.0 ... 190.0
        ) {
            chartCalls += 1
            return makeSeries(dateString: "2026-03-02", value: 181.0)
        }
        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary(period: "2026-03-02")])
        }

        #expect(continuousCalls == 2)
        #expect(visibleCalls == 2)
        #expect(chartCalls == 2)
        #expect(labelRangeCalls == 2)
    }
}
