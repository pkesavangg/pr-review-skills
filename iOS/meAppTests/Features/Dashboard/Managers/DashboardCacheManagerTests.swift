import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardCacheManagerTests {

    func makeSUT() -> DashboardCacheManager {
        DashboardCacheManager()
    }

    func makeSummary(
        period: String = "2026-03-01",
        weight: Double = 1800
    ) -> BathScaleWeightSummary {
        DashboardTestFixtures.makeSummary(period: period, weight: weight)
    }

    func makeSeries(
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

    func makeDateRangeResult(
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

    @Test("invalidateContinuousOperationsCache: clears continuous operations cache")
    func invalidateContinuousOperationsCacheClearsContinuous() {
        let sut = makeSUT()
        var continuousCalls = 0

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.invalidateContinuousOperationsCache()

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(continuousCalls == 2)
    }

    @Test("invalidateContinuousOperationsCache: clears visible operations cache")
    func invalidateContinuousOperationsCacheClearsVisible() {
        let sut = makeSUT()
        var visibleCalls = 0

        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.invalidateContinuousOperationsCache()

        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(visibleCalls == 2)
    }

    @Test("invalidateContinuousOperationsCache: clears chart series cache")
    func invalidateContinuousOperationsCacheClearsChart() {
        let sut = makeSUT()
        var chartCalls = 0

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

        sut.invalidateContinuousOperationsCache()

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

        #expect(chartCalls == 2)
    }

    @Test("invalidateContinuousOperationsCache: clears label date range cache")
    func invalidateContinuousOperationsCacheClearsLabelRange() {
        let sut = makeSUT()
        var labelRangeCalls = 0
        let scroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")

        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary()])
        }

        sut.invalidateContinuousOperationsCache()

        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary(period: "2026-03-02")])
        }

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

        UserDefaults.standard.removeObject(forKey: key) // swiftlint:disable:this no_direct_userdefaults
    }

    // MARK: - setProductContext Tests

    @Test("setProductContext: changing product type invalidates all caches")
    func setProductContextChangingProductTypeInvalidates() {
        let sut = makeSUT()
        var callCount = 0

        sut.setProductContext(productType: .scale, babyProfileId: nil)
        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.setProductContext(productType: .bpm, babyProfileId: nil)
        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(callCount == 2)
    }

    @Test("setProductContext: same product type does not invalidate caches")
    func setProductContextSameProductTypeDoesNotInvalidate() {
        let sut = makeSUT()
        var callCount = 0

        sut.setProductContext(productType: .scale, babyProfileId: nil)
        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.setProductContext(productType: .scale, babyProfileId: nil)
        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(callCount == 1)
    }

    @Test("setProductContext: changing baby profile invalidates caches")
    func setProductContextChangingBabyProfileInvalidates() {
        let sut = makeSUT()
        var callCount = 0

        sut.setProductContext(productType: .scale, babyProfileId: "baby1")
        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.setProductContext(productType: .scale, babyProfileId: "baby2")
        _ = sut.getContinuousOperations(for: .week) {
            callCount += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(callCount == 2)
    }

    @Test("clearAllCaches: resets continuous operations cache")
    func clearAllCachesResetsContinuous() {
        let sut = makeSUT()
        var continuousCalls = 0

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.clearAllCaches()

        _ = sut.getContinuousOperations(for: .week) {
            continuousCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(continuousCalls == 2)
    }

    @Test("clearAllCaches: resets visible operations cache")
    func clearAllCachesResetsVisible() {
        let sut = makeSUT()
        var visibleCalls = 0

        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-01")]
        }

        sut.clearAllCaches()

        _ = sut.getVisibleOperations(isScrolling: true) {
            visibleCalls += 1
            return [makeSummary(period: "2026-03-02")]
        }

        #expect(visibleCalls == 2)
    }

    @Test("clearAllCaches: resets chart series cache")
    func clearAllCachesResetsChart() {
        let sut = makeSUT()
        var chartCalls = 0

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

        sut.clearAllCaches()

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

        #expect(chartCalls == 2)
    }

    @Test("clearAllCaches: resets label date range cache")
    func clearAllCachesResetsLabelRange() {
        let sut = makeSUT()
        var labelRangeCalls = 0
        let scroll = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")

        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary()])
        }

        sut.clearAllCaches()

        _ = sut.getLabelDateRangeOperations(period: .week, scrollPosition: scroll) {
            labelRangeCalls += 1
            return makeDateRangeResult(period: .week, scrollPosition: scroll, ops: [makeSummary(period: "2026-03-02")])
        }

        #expect(labelRangeCalls == 2)
    }
}
