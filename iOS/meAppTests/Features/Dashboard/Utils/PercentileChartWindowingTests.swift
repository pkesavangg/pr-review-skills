//
//  PercentileChartWindowingTests.swift
//  meAppTests
//
//  MOB-518 — proves the binary-searched percentile windowing (`PercentileChartWindowing`)
//  produces a point set IDENTICAL to the previous O(n) `.filter` + `first`/`last(where:)`
//  implementation it replaced. The `legacy*` functions below are a verbatim copy of the old
//  behaviour; every test asserts the new output equals the legacy output.
//

import Foundation
@testable import meApp
import Testing

@Suite("PercentileChartWindowing — parity with the pre-MOB-518 linear implementation")
struct PercentileChartWindowingTests {

    // MARK: - Fixtures

    private static let epoch = Date(timeIntervalSinceReferenceDate: 0)

    /// A sorted-by-xDate percentile series, one point per day (mirrors the ~150-point curves).
    private func makeSeries(count: Int, valueStep: Double = 1.0) -> [PlottedGraphSeries] {
        (0..<count).map { i in
            let date = Self.epoch.addingTimeInterval(Double(i) * 86_400)
            return PlottedGraphSeries(
                original: GraphSeries(date: date, value: Double(i) * valueStep, series: "baby_percentile_50"),
                xDate: date
            )
        }
    }

    private func day(_ offset: Double) -> Date { Self.epoch.addingTimeInterval(offset * 86_400) }

    // MARK: - Legacy reference implementation (verbatim pre-MOB-518 behaviour)

    private func legacyBoundaryExtended(
        from points: [PlottedGraphSeries],
        visibleGridRange: ClosedRange<Date>
    ) -> [PlottedGraphSeries] {
        let pointsInGridRange = points.filter {
            $0.xDate >= visibleGridRange.lowerBound && $0.xDate <= visibleGridRange.upperBound
        }
        guard !pointsInGridRange.isEmpty else { return points }
        var result = pointsInGridRange
        if result.first?.xDate != visibleGridRange.lowerBound,
           let leading = legacyInterpolatedBoundary(from: points, at: visibleGridRange.lowerBound) {
            result.insert(leading, at: 0)
        }
        if result.last?.xDate != visibleGridRange.upperBound,
           let trailing = legacyInterpolatedBoundary(from: points, at: visibleGridRange.upperBound) {
            result.append(trailing)
        }
        return result
    }

    private func legacyInterpolatedBoundary(
        from points: [PlottedGraphSeries],
        at boundary: Date
    ) -> PlottedGraphSeries? {
        if let exactMatch = points.first(where: { $0.xDate == boundary }) { return exactMatch }
        let previousPoint = points.last { $0.xDate < boundary }
        let nextPoint = points.first { $0.xDate > boundary }

        let segment: (start: PlottedGraphSeries, end: PlottedGraphSeries)?
        switch (previousPoint, nextPoint) {
        case let (.some(previous), .some(next)):
            segment = (previous, next)
        case let (.some(lastPoint), .none):
            guard let priorPoint = points.dropLast().last else { return nil }
            segment = (priorPoint, lastPoint)
        case let (.none, .some(firstPoint)):
            guard let followingPoint = points.dropFirst().first else { return nil }
            segment = (firstPoint, followingPoint)
        case (.none, .none):
            return nil
        }
        guard let segment else { return nil }
        let interpolatedValue = BabyDashboardChartSupport.interpolatedValue(
            at: boundary,
            from: segment.start.xDate,
            startValue: segment.start.original.value,
            to: segment.end.xDate,
            endValue: segment.end.original.value
        )
        let interpolatedSeriesPoint = GraphSeries(
            date: boundary,
            value: interpolatedValue,
            series: segment.start.original.series
        )
        return PlottedGraphSeries(original: interpolatedSeriesPoint, xDate: boundary)
    }

    // MARK: - Point-set parity

    @Test("boundaryExtendedPoints matches the legacy filter for many grid ranges")
    func pointSetParity() {
        let series = makeSeries(count: 150)

        // Windows: fully inside, spanning exact points, off-by-half-day edges,
        // clamped to first/last, single-day, and degenerate/empty windows.
        let ranges: [ClosedRange<Date>] = [
            day(10)...day(120),          // exact point boundaries
            day(9.5)...day(120.5),       // between-point boundaries (forces interpolation)
            day(0)...day(149),           // whole series
            day(-5)...day(5),            // clamped left, interpolated right edge only
            day(140)...day(200),         // clamped right, interpolated left edge only
            day(75)...day(75),           // single exact point
            day(75.5)...day(76.5)        // narrow window between two points
        ]

        for range in ranges {
            let expected = legacyBoundaryExtended(from: series, visibleGridRange: range)
            let actual = PercentileChartWindowing.boundaryExtendedPoints(from: series, visibleGridRange: range)
            #expect(actual == expected, "mismatch for range \(range)")
            // Extra guards for clearer failures.
            #expect(actual.count == expected.count)
            #expect(zip(actual, expected).allSatisfy { $0.xDate == $1.xDate && $0.original.value == $1.original.value })
        }
    }

    @Test("Empty in-range window returns all points (legacy fallback preserved)")
    func emptyWindowReturnsAll() {
        let series = makeSeries(count: 20)
        // A window entirely to the right of all data → nothing in range.
        let range = day(1000)...day(2000)
        let actual = PercentileChartWindowing.boundaryExtendedPoints(from: series, visibleGridRange: range)
        #expect(actual == series)
    }

    @Test("Empty input series returns empty")
    func emptyInput() {
        let actual = PercentileChartWindowing.boundaryExtendedPoints(from: [], visibleGridRange: day(0)...day(10))
        #expect(actual.isEmpty)
    }

    // MARK: - Boundary interpolation parity

    @Test("interpolatedBoundaryPoint matches legacy neighbours for boundaries across the domain")
    func interpolationParity() {
        let series = makeSeries(count: 120, valueStep: 2.5)

        // Boundaries: exact hits, mid-segment, before-first, after-last.
        let boundaries: [Date] = [
            day(0), day(0.5), day(37), day(37.25), day(59.9),
            day(-2), day(119), day(150)
        ]

        for boundary in boundaries {
            let expected = legacyInterpolatedBoundary(from: series, at: boundary)
            let actual = PercentileChartWindowing.interpolatedBoundaryPoint(from: series, at: boundary)
            #expect(actual == expected, "mismatch at boundary \(boundary)")
        }
    }

    @Test("interpolatedBoundaryPoint on an exact point returns that point")
    func interpolationExactHit() {
        let series = makeSeries(count: 10)
        let boundary = day(4)
        let point = PercentileChartWindowing.interpolatedBoundaryPoint(from: series, at: boundary)
        #expect(point?.xDate == boundary)
        #expect(point?.original.value == 4.0)
    }

    @Test("interpolatedBoundaryPoint returns a true linear midpoint between two points")
    func interpolationMidpointValue() {
        // Two points: (day 0, value 0) and (day 10, value 100). Midpoint day 5 → value 50.
        let series: [PlottedGraphSeries] = [
            PlottedGraphSeries(original: GraphSeries(date: day(0), value: 0, series: "baby_percentile_50"), xDate: day(0)),
            PlottedGraphSeries(original: GraphSeries(date: day(10), value: 100, series: "baby_percentile_50"), xDate: day(10))
        ]
        let mid = PercentileChartWindowing.interpolatedBoundaryPoint(from: series, at: day(5))
        #expect(mid?.original.value == 50.0)
        #expect(mid?.xDate == day(5))
    }
}
