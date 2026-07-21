//
//  ChartDecimatorTests.swift
//  meAppTests
//
//  MOB-1515 (AC #4) — unit coverage for `ChartDecimator.decimate`, the v2 engine's single
//  shape-preserving downsample (min/max bucketing over the full x-domain). Asserts the contract the
//  renderer + crosshair rely on: below-threshold pass-through, exact endpoint preservation, per-bucket
//  extreme retention (so a spike/dip survives), and a time-monotone result. Fixtures build
//  `PlottedGraphSeries` directly with strictly ascending, distinct xDates.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
struct ChartDecimatorTests {

    /// One point per day (strictly ascending, distinct xDates) for the given values.
    private func series(_ values: [Double]) -> [PlottedGraphSeries] {
        values.enumerated().map { index, value in
            let date = Date(timeIntervalSinceReferenceDate: Double(index) * 86_400)
            return PlottedGraphSeries(
                original: GraphSeries(date: date, value: value, series: DashboardStrings.weight),
                xDate: date
            )
        }
    }

    @Test("returns the input unchanged when the count does not exceed the threshold")
    func belowThresholdIsPassThrough() {
        let points = series(Array(stride(from: 100.0, through: 149.0, by: 1.0))) // 50 points
        let result = ChartDecimator.decimate(points) // default threshold 800
        #expect(result == points)
    }

    @Test("reduces the count once the series exceeds the threshold")
    func aboveThresholdDownsamples() {
        let points = series((0..<40).map { Double($0) })
        let result = ChartDecimator.decimate(points, threshold: 4, target: 6)
        #expect(result.count < points.count)
        #expect(result.count >= 2) // never drops below the two endpoints
    }

    @Test("always preserves the exact first and last points")
    func preservesEndpoints() {
        let points = series((0..<40).map { Double($0) })
        let result = ChartDecimator.decimate(points, threshold: 4, target: 8)
        #expect(result.first == points.first)
        #expect(result.last == points.last)
    }

    @Test("retains both the global spike and the global dip (shape preservation)")
    func retainsExtremes() {
        var values = Array(repeating: 100.0, count: 40)
        values[10] = 9_999   // global max, interior
        values[30] = -9_999  // global min, interior
        let result = ChartDecimator.decimate(series(values), threshold: 4, target: 6)
        #expect(result.contains { $0.original.value == 9_999 })
        #expect(result.contains { $0.original.value == -9_999 })
    }

    @Test("keeps the result monotonically ascending in xDate")
    func resultIsTimeMonotone() {
        let values = (0..<40).map { Double(($0 * 7) % 13) } // non-monotone values, distinct dates
        let result = ChartDecimator.decimate(series(values), threshold: 4, target: 10)
        let ascending = zip(result, result.dropFirst()).allSatisfy { $0.xDate <= $1.xDate }
        #expect(ascending)
    }

    @Test("guards a degenerate target (< 4) by returning the input unchanged")
    func degenerateTargetIsPassThrough() {
        let points = series((0..<40).map { Double($0) })
        let result = ChartDecimator.decimate(points, threshold: 4, target: 3)
        #expect(result == points)
    }
}
