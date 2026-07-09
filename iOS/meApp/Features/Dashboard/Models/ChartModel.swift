//
//  ChartModel.swift
//  meApp
//
//  MOB-518 — v2 weight-graph engine (greenfield strangler rebuild).
//
//  The single immutable, Sendable, plot-ready value the new chart renders from. It replaces the
//  six-cache tangle of the old engine (VM `cachedSeriesData`/`cachedPlotXDates`/…, view
//  `cachedPlottedPoints`/`cachedAllPlottedPoints`, manager `cachedChartSeries`). Built ONCE per
//  data / period / unit / metric / scroll-settle change by `ChartPrep` — never during a scroll frame.
//
//  Why this shape kills the old `.id`-rebuild bug (S1) by construction: `dataFingerprint` deliberately
//  EXCLUDES the y-axis, so a y-settle updates `yAxis` in place without changing series identity. The
//  view keeps one stable `Chart` identity for the life of the period.
//
//  MULTI-SERIES: weight uses one entry ("weight") in `orderedSeriesNames`/`seriesPoints`. BPM will add
//  three (systolic/diastolic/pulse); baby adds 5–10 percentile curves. The type is already multi-series;
//  only `ChartPrep` and the renderer need to learn the extra series when weight is signed off.
//

import Foundation

/// Adaptive y-axis snapshot (Y-B: rescales to the visible window, same behaviour as today).
/// `domain` is routed through `ChartDomainSanitizer.finiteWidth` before it reaches Swift Charts.
struct YAxisModel: Equatable, Sendable {
    let domain: ClosedRange<Double>
    let ticks: [Double]
    let average: Double

    static let placeholder = YAxisModel(domain: 0...100, ticks: [0, 25, 50, 75, 100], average: 50)
}

/// Everything the new weight `Chart` needs, as one immutable value. `GraphSeries`/`PlottedGraphSeries`
/// are already value types, so this is `Sendable` for free and cheap to diff via `dataFingerprint`.
struct ChartModel: Equatable, Sendable {
    let period: TimePeriod
    let productType: EntryType

    /// Draw order (back-to-front). Weight = `["weight"]`.
    let orderedSeriesNames: [String]

    /// Display points per series — pre-sorted ascending by `xDate`, `plotXDate` baked in, decimated for
    /// the plot. This is what the line/points render from.
    let seriesPoints: [String: [PlottedGraphSeries]]

    /// Undecimated points per series — kept for crosshair snapping / selection so taps always land on a
    /// real entry even where the drawn line is decimated.
    let fullResolution: [String: [PlottedGraphSeries]]

    /// Full scrollable x-domain (Charts scrolls within this).
    let xDomain: ClosedRange<Date>

    /// Width of one visible window (`.chartXVisibleDomain(length:)`).
    let visibleDomainLength: TimeInterval

    /// Pre-generated x-axis tick dates for the period.
    let xAxisTicks: [Date]

    /// Adaptive y-axis (domain + ticks + window average). Updated in place on settle.
    let yAxis: YAxisModel

    /// Cheap change token: series count + endpoints. EXCLUDES `yAxis` — this is what lets the `Chart`
    /// keep a stable identity across a y-settle (the fix for S1).
    let dataFingerprint: Int

    /// `true` when there is nothing to plot (empty account / no readings in range).
    var isEmpty: Bool { seriesPoints.values.allSatisfy { $0.isEmpty } }

    /// Convenience accessor for the primary (weight) series' display points.
    var weightPoints: [PlottedGraphSeries] { seriesPoints[DashboardStrings.weight] ?? [] }
}
