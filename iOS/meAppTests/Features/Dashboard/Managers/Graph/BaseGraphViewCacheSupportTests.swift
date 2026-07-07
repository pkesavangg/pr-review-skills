import CoreGraphics
import Foundation
@testable import meApp
import Testing

/// Deterministic unit tests for `BaseGraphViewCacheSupport` — the pure, dependency-free
/// math/grouping helpers behind the Dashboard graph cache. Every input struct here is built
/// from verified initializers (`GraphSeries`, `PlottedGraphSeries`, `BaseGraphViewCacheSnapshot`)
/// and every assertion is time/randomness independent.
@Suite(.serialized)
@MainActor
struct BaseGraphViewCacheSupportTests {

    // MARK: - Helpers

    private func date(_ interval: TimeInterval) -> Date {
        Date(timeIntervalSince1970: interval)
    }

    // MARK: - roundedFrame

    @Test("roundedFrame expands a fractional rect to its integral bounds")
    func roundedFrameExpandsToIntegral() {
        let result = BaseGraphViewCacheSupport.roundedFrame(
            CGRect(x: 1.4, y: 2.6, width: 10.3, height: 20.7)
        )
        // .integral floors the origin and ceils the far edge:
        // minX 1.4 -> 1, maxX 11.7 -> 12 => width 11
        // minY 2.6 -> 2, maxY 23.3 -> 24 => height 22
        #expect(result == CGRect(x: 1, y: 2, width: 11, height: 22))
    }

    @Test("roundedFrame leaves an already-integral rect unchanged")
    func roundedFrameIdentityOnIntegralRect() {
        let rect = CGRect(x: 0, y: 0, width: 100, height: 50)
        #expect(BaseGraphViewCacheSupport.roundedFrame(rect) == rect)
    }

    // MARK: - roundedHeight

    @Test("roundedHeight rounds to the nearest whole point")
    func roundedHeightRounds() {
        #expect(BaseGraphViewCacheSupport.roundedHeight(20.4) == 20)
        #expect(BaseGraphViewCacheSupport.roundedHeight(20.6) == 21)
        #expect(BaseGraphViewCacheSupport.roundedHeight(20.5) == 21)
        #expect(BaseGraphViewCacheSupport.roundedHeight(19) == 19)
    }

    // MARK: - boundaryYAxisTicks

    @Test("boundaryYAxisTicks returns empty for empty input")
    func boundaryTicksEmpty() {
        #expect(BaseGraphViewCacheSupport.boundaryYAxisTicks(from: []).isEmpty)
    }

    @Test("boundaryYAxisTicks returns the single value when only one tick")
    func boundaryTicksSingle() {
        #expect(BaseGraphViewCacheSupport.boundaryYAxisTicks(from: [5.0]) == [5.0])
    }

    @Test("boundaryYAxisTicks returns first and last for a spread")
    func boundaryTicksFirstLast() {
        #expect(BaseGraphViewCacheSupport.boundaryYAxisTicks(from: [10, 20, 30]) == [10, 30])
    }

    @Test("boundaryYAxisTicks collapses to first when first and last are within epsilon")
    func boundaryTicksCollapsed() {
        #expect(BaseGraphViewCacheSupport.boundaryYAxisTicks(from: [5.0, 5.0]) == [5.0])
    }

    // MARK: - adjustedBoundaryTick

    @Test("adjustedBoundaryTick returns the tick unchanged when there is no X axis")
    func adjustedTickNoXAxis() {
        let result = BaseGraphViewCacheSupport.adjustedBoundaryTick(
            42,
            hasXAxis: false,
            yAxisDomain: 0...100,
            chartHeight: 118,
            isBabySelection: false
        )
        #expect(result == 42)
    }

    @Test("adjustedBoundaryTick pushes the upper boundary down by two points")
    func adjustedTickUpperBoundary() {
        // chartHeight 118 -> availableHeight 100, range 100 -> onePointValue 1 -> offset 2.
        let result = BaseGraphViewCacheSupport.adjustedBoundaryTick(
            100,
            hasXAxis: true,
            yAxisDomain: 0...100,
            chartHeight: 118,
            isBabySelection: false
        )
        #expect(result == 98)
    }

    @Test("adjustedBoundaryTick pushes lower boundary up for baby selection")
    func adjustedTickLowerBoundaryBaby() {
        let result = BaseGraphViewCacheSupport.adjustedBoundaryTick(
            0,
            hasXAxis: true,
            yAxisDomain: 0...100,
            chartHeight: 118,
            isBabySelection: true
        )
        #expect(result == 2)
    }

    @Test("adjustedBoundaryTick keeps a non-negative lower boundary unchanged for non-baby")
    func adjustedTickLowerBoundaryNonNegative() {
        let result = BaseGraphViewCacheSupport.adjustedBoundaryTick(
            0,
            hasXAxis: true,
            yAxisDomain: 0...100,
            chartHeight: 118,
            isBabySelection: false
        )
        #expect(result == 0)
    }

    @Test("adjustedBoundaryTick nudges a negative lower boundary up by one point")
    func adjustedTickNegativeLowerBoundary() {
        // domain -50...50 -> range 100, availableHeight 100 -> onePointValue 1.
        let result = BaseGraphViewCacheSupport.adjustedBoundaryTick(
            -50,
            hasXAxis: true,
            yAxisDomain: -50...50,
            chartHeight: 118,
            isBabySelection: false
        )
        #expect(result == -49)
    }

    @Test("adjustedBoundaryTick leaves an interior tick unchanged")
    func adjustedTickInterior() {
        let result = BaseGraphViewCacheSupport.adjustedBoundaryTick(
            50,
            hasXAxis: true,
            yAxisDomain: 0...100,
            chartHeight: 118,
            isBabySelection: false
        )
        #expect(result == 50)
    }

    // MARK: - precomputedYAxisLabels

    @Test("precomputedYAxisLabels formats every tick and the goal value")
    func yAxisLabelsFormatsTicksAndGoal() {
        let labels = BaseGraphViewCacheSupport.precomputedYAxisLabels(
            ticks: [10, 20],
            goalWeight: 25,
            existingLabels: [:],
            formatter: { String(Int($0)) }
        )
        #expect(labels[10] == "10")
        #expect(labels[20] == "20")
        #expect(labels[25] == "25")
        #expect(labels.count == 3)
    }

    @Test("precomputedYAxisLabels preserves existing labels and skips a nil goal")
    func yAxisLabelsPreservesExisting() {
        let labels = BaseGraphViewCacheSupport.precomputedYAxisLabels(
            ticks: [10, 20],
            goalWeight: nil,
            existingLabels: [10: "TEN"],
            formatter: { String(Int($0)) }
        )
        #expect(labels[10] == "TEN")
        #expect(labels[20] == "20")
        #expect(labels.count == 2)
    }

    // MARK: - precomputedXAxisLabels

    @Test("precomputedXAxisLabels adds formatted labels and preserves existing ones")
    func xAxisLabelsAddsAndPreserves() {
        let d0 = date(1_000_000)
        let d1 = date(1_100_000)
        let labels = BaseGraphViewCacheSupport.precomputedXAxisLabels(
            dates: [d0, d1],
            existingLabels: [d0: "KEEP"],
            formatter: { _ in "NEW" }
        )
        #expect(labels[d0] == "KEEP")
        #expect(labels[d1] == "NEW")
        #expect(labels.count == 2)
    }

    @Test("precomputedXAxisLabels omits dates whose formatter returns nil")
    func xAxisLabelsOmitsNil() {
        let d0 = date(1_000_000)
        let d1 = date(1_100_000)
        let labels = BaseGraphViewCacheSupport.precomputedXAxisLabels(
            dates: [d0, d1],
            existingLabels: [:],
            formatter: { $0 == d1 ? nil : "L" }
        )
        #expect(labels[d0] == "L")
        #expect(labels[d1] == nil)
        #expect(labels.count == 1)
    }

    // MARK: - pointsToRender

    @Test("pointsToRender returns the input unchanged when at or below the 200-point threshold")
    func pointsToRenderSmallSetIdentity() {
        let points = (0..<3).map { index -> PlottedGraphSeries in
            let series = GraphSeries(date: date(Double(index)), value: Double(index), series: "weight")
            return PlottedGraphSeries(original: series, xDate: date(Double(index)))
        }
        let result = BaseGraphViewCacheSupport.pointsToRender(
            from: points,
            visibleStart: date(0),
            visibleEnd: date(2)
        )
        #expect(result == points)
    }

    @Test("pointsToRender keeps every point when all fall inside the visible window")
    func pointsToRenderAllVisible() {
        // 201 points (> 200 threshold) all inside the window => no buffer sampling occurs.
        let points = (0..<201).map { index -> PlottedGraphSeries in
            let series = GraphSeries(date: date(Double(index)), value: Double(index), series: "weight")
            return PlottedGraphSeries(original: series, xDate: date(Double(index)))
        }
        let result = BaseGraphViewCacheSupport.pointsToRender(
            from: points,
            visibleStart: date(0),
            visibleEnd: date(200)
        )
        #expect(result.count == 201)
        #expect(result == points)
    }

    // MARK: - makeCacheUpdate

    @Test("makeCacheUpdate groups, sorts, and orders series by render priority")
    func makeCacheUpdateGroupsAndOrders() throws {
        let d0 = date(1_000_000)
        let d1 = date(1_100_000)
        let d2 = date(1_200_000)

        let seriesData = [
            GraphSeries(date: d2, value: 5, series: "weight"),
            GraphSeries(date: d0, value: 4, series: "weight"),
            GraphSeries(date: d1, value: 1, series: "baby_percentile_p50"),
            GraphSeries(date: d1, value: 3, series: "other")
        ]
        let snapshot = BaseGraphViewCacheSnapshot(
            seriesData: seriesData,
            yAxisDomain: 0...10,
            yAxisTicks: [0, 5, 10]
        )

        let update = try #require(
            BaseGraphViewCacheSupport.makeCacheUpdate(
                snapshot: snapshot,
                previousHash: 0,
                isCacheEmpty: true,
                plotXDate: { $0 }
            )
        )

        // chartPoints mirror the raw input order.
        #expect(update.chartPoints == seriesData)

        // Render priority: percentile (0) < weight (1) < other (2).
        #expect(update.orderedSeriesNames == ["baby_percentile_p50", "weight", "other"])

        // Weight group is sorted ascending by date.
        let weightGroup = try #require(update.groupedPoints["weight"])
        #expect(weightGroup.map(\.value) == [4, 5])

        // plottedPoints mirror the sorted group and use plotXDate for xDate (identity here).
        let plottedWeight = try #require(update.plottedPoints["weight"])
        #expect(plottedWeight.map { $0.original.value } == [4, 5])
        #expect(plottedWeight.first?.xDate == d0)

        // allPlottedPoints are flattened in series-priority order, then by date within a series.
        #expect(update.allPlottedPoints.count == 4)
        #expect(update.allPlottedPoints.map { $0.original.value } == [1, 4, 5, 3])
    }

    @Test("makeCacheUpdate returns nil when the hash is unchanged and the cache is not empty")
    func makeCacheUpdateSkipsWhenUnchanged() throws {
        let snapshot = BaseGraphViewCacheSnapshot(
            seriesData: [GraphSeries(date: date(1_000_000), value: 1, series: "weight")],
            yAxisDomain: 0...10,
            yAxisTicks: [0, 10]
        )

        let first = try #require(
            BaseGraphViewCacheSupport.makeCacheUpdate(
                snapshot: snapshot,
                previousHash: 0,
                isCacheEmpty: true,
                plotXDate: { $0 }
            )
        )

        // Re-running with the just-produced hash and a non-empty cache yields no update.
        let second = BaseGraphViewCacheSupport.makeCacheUpdate(
            snapshot: snapshot,
            previousHash: first.dataHash,
            isCacheEmpty: false,
            plotXDate: { $0 }
        )
        #expect(second == nil)
    }
}
