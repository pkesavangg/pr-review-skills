//
//  SortedArrayIndexTests.swift
//  meAppTests
//
//  MOB-518 — verifies the generic O(log n) `SortedArrayIndex` helper returns byte-identical
//  indices to the linear `firstIndex(where:)` / `lastIndex(where:)` scans it replaced on the
//  chart's percentile path. The predicate must be monotonic over the sorted input.
//

import Foundation
@testable import meApp
import Testing

@Suite("SortedArrayIndex — binary search vs linear scan parity")
struct SortedArrayIndexTests {

    // MARK: - Edge cases

    @Test("Empty array returns nil for both first and last")
    func emptyArray() {
        let empty: [Int] = []
        #expect(SortedArrayIndex.first(in: empty, where: { $0 >= 0 }) == nil)
        #expect(SortedArrayIndex.last(in: empty, where: { $0 <= 0 }) == nil)
    }

    @Test("All-false predicate returns nil")
    func allFalse() {
        let values = [0, 2, 4, 6, 8]
        // Nothing is >= 100 → first has no true element.
        #expect(SortedArrayIndex.first(in: values, where: { $0 >= 100 }) == nil)
        // Nothing is <= -1 → last has no true element.
        #expect(SortedArrayIndex.last(in: values, where: { $0 <= -1 }) == nil)
    }

    @Test("All-true predicate returns the boundary index")
    func allTrue() {
        let values = [0, 2, 4, 6, 8]
        #expect(SortedArrayIndex.first(in: values, where: { $0 >= -1 }) == 0)
        #expect(SortedArrayIndex.last(in: values, where: { $0 <= 100 }) == values.count - 1)
    }

    @Test("Single element")
    func singleElement() {
        #expect(SortedArrayIndex.first(in: [5], where: { $0 >= 5 }) == 0)
        #expect(SortedArrayIndex.first(in: [5], where: { $0 >= 6 }) == nil)
        #expect(SortedArrayIndex.last(in: [5], where: { $0 <= 5 }) == 0)
        #expect(SortedArrayIndex.last(in: [5], where: { $0 <= 4 }) == nil)
    }

    // MARK: - Exhaustive parity across sizes and thresholds

    @Test("first(>= t) / last(<= t) match linear scan for every threshold and size")
    func exhaustiveParity() {
        for count in [0, 1, 2, 3, 5, 33, 200, 501] {
            // Sorted, evenly-spaced with gaps so thresholds can land on and between elements.
            let values = (0..<count).map { $0 * 2 }
            // Cover below-min, exact hits, between-elements, and above-max.
            let thresholds = Swift.stride(from: -3, through: count * 2 + 3, by: 1)
            for threshold in thresholds {
                let expectedFirst = values.firstIndex(where: { $0 >= threshold })
                let actualFirst = SortedArrayIndex.first(in: values, where: { $0 >= threshold })
                #expect(actualFirst == expectedFirst,
                        "first(>= \(threshold)) count=\(count): got \(String(describing: actualFirst)), expected \(String(describing: expectedFirst))")

                let expectedLast = values.lastIndex(where: { $0 <= threshold })
                let actualLast = SortedArrayIndex.last(in: values, where: { $0 <= threshold })
                #expect(actualLast == expectedLast,
                        "last(<= \(threshold)) count=\(count): got \(String(describing: actualLast)), expected \(String(describing: expectedLast))")
            }
        }
    }

    @Test("Handles duplicate keys — first true / last true across a run of equal values")
    func duplicateKeys() {
        // Runs of equal values around a threshold boundary.
        let values = [0, 0, 2, 2, 2, 4, 4]
        for threshold in -1...5 {
            #expect(SortedArrayIndex.first(in: values, where: { $0 >= threshold })
                    == values.firstIndex(where: { $0 >= threshold }))
            #expect(SortedArrayIndex.last(in: values, where: { $0 <= threshold })
                    == values.lastIndex(where: { $0 <= threshold }))
        }
    }

    // MARK: - PlottedGraphSeries (the real percentile-path element type)

    @Test("Date-keyed slice over PlottedGraphSeries matches the filter it replaced")
    func plottedGraphSeriesDateSlice() {
        let base = Date(timeIntervalSinceReferenceDate: 0)
        let points: [PlottedGraphSeries] = (0..<150).map { i in
            let date = base.addingTimeInterval(Double(i) * 86_400) // one point per day, sorted
            return PlottedGraphSeries(original: GraphSeries(date: date, value: Double(i), series: "baby_percentile_50"),
                                      xDate: date)
        }

        // A window that starts and ends between real points.
        let lower = base.addingTimeInterval(9.5 * 86_400)
        let upper = base.addingTimeInterval(120.5 * 86_400)

        let lo = SortedArrayIndex.first(in: points, where: { $0.xDate >= lower })
        let hi = SortedArrayIndex.last(in: points, where: { $0.xDate <= upper })

        // Reference: the old O(n) filter's index bounds.
        let expected = points.enumerated().filter { $0.element.xDate >= lower && $0.element.xDate <= upper }.map(\.offset)
        #expect(lo == expected.first)
        #expect(hi == expected.last)
    }
}
