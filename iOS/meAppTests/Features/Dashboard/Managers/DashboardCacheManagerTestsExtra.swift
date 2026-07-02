import Foundation
@testable import meApp
import Testing

extension DashboardCacheManagerTests {

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
}
