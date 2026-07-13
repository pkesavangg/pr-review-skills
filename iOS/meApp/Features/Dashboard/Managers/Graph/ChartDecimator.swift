//
//  ChartDecimator.swift
//  meApp
//
//  MOB-518 — v2 weight-graph engine.
//
//  ONE shape-preserving downsample done at prep time, over the FULL x-domain — replacing the old
//  engine's two stale windowing passes (store buffer + the view's 30-left/30-right `pointsToRender`).
//  We hand Swift Charts the whole decimated series once and let it scroll natively; no per-frame or
//  per-settle re-windowing (fixes S2/S10, the "coarse/sparse line while scrolling" symptom).
//
//  Weight plots aggregated daily/monthly summaries, so most series are a few hundred points and this
//  returns the input UNCHANGED. Decimation only engages on long `total` ranges. Min/max bucketing keeps
//  the line SHAPE (both local extremes per bucket) and preserves the exact endpoints; the undecimated
//  set is kept separately in `ChartModel.fullResolution` for crosshair snapping.
//

import Foundation

enum ChartDecimator {

    /// Downsample to ~`target` points using min/max bucketing when the series exceeds `threshold`.
    /// Below the threshold the input is returned verbatim. Input must be sorted ascending by `xDate`.
    static func decimate(
        _ points: [PlottedGraphSeries],
        threshold: Int = 800,
        target: Int = 600
    ) -> [PlottedGraphSeries] {
        guard points.count > threshold, target >= 4 else { return points }

        let lastIndex = points.count - 1
        let innerCount = points.count - 2                    // excludes fixed first/last
        // Each bucket contributes up to two points (its min and its max), plus the two endpoints.
        let bucketCount = max(1, (target - 2) / 2)
        let bucketSize = Double(innerCount) / Double(bucketCount)

        var result: [PlottedGraphSeries] = [points[0]]
        var lastAppended = 0

        for bucket in 0..<bucketCount {
            let start = 1 + Int((Double(bucket) * bucketSize).rounded(.down))
            let end = min(lastIndex, 1 + Int((Double(bucket + 1) * bucketSize).rounded(.down)))
            guard start < end else { continue }

            var minIdx = start
            var maxIdx = start
            for i in start..<end {
                let value = points[i].original.value
                if value < points[minIdx].original.value { minIdx = i }
                if value > points[maxIdx].original.value { maxIdx = i }
            }
            // Emit the two extremes in x (index) order so the line stays monotone in time; dedup.
            for idx in [Swift.min(minIdx, maxIdx), Swift.max(minIdx, maxIdx)] where idx != lastAppended {
                result.append(points[idx])
                lastAppended = idx
            }
        }

        if lastAppended != lastIndex {
            result.append(points[lastIndex])
        }
        return result
    }
}
