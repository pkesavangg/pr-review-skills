//
//  ChartPrep.swift
//  meApp
//
//  MOB-518 — v2 weight-graph engine.
//
//  Pure builder: `[BathScaleWeightSummary]` (+ config) → `ChartModel`. No `@MainActor`, no side effects.
//
//  It REUSES the existing pure domain layer verbatim so the output is byte-identical to today — only the
//  plumbing is new:
//    • `GraphDataPreparer.buildWeightSeries`  — weight display values (unit + weightless handled inside)
//    • `YAxisCalculator.calculateYAxis`       — the adaptive y-axis (Y-B) over the visible window
//    • `GraphRenderingConfiguration`          — x-axis ticks + visible-domain length
//    • `GraphDataPreparer.strictlyVisible/bracketingOperations` — the y-axis window (binary-searched)
//
//  V1 is called on the MAIN actor, once per data/period/unit/metric/scroll-settle change (never per
//  frame — that repetition + the old `.id` teardown were the hitch, not a single pass over a few hundred
//  aggregated points). Moving the build off-main via a Sendable primitive snapshot is a later, isolated
//  optimization (`BathScaleWeightSummary` is a class, so that step extracts primitives first).
//
//  MULTI-SERIES: `buildWeight` builds the single "weight" series. BPM/baby get sibling builders here
//  (`buildBpm`, `buildBaby`) reusing `GraphDataPreparer.buildBpmChartSeries` / the percentile path once
//  weight is signed off — same output shape, no new hot path.
//

import CoreGraphics
import Foundation

enum ChartPrep {

    /// Build the weight `ChartModel` for one period at one scroll position.
    static func buildWeight( // swiftlint:disable:this function_parameter_count
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        goalWeight: Double?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: (Double) -> Double,
        chartHeight: CGFloat = 265,
        lastYAxis: YAxisScale? = nil,
        calendar: Calendar = .current,
        config: GraphRenderingConfiguration = GraphRenderingConfiguration()
    ) -> ChartModel {
        let seriesName = DashboardStrings.weight
        let preparer = GraphDataPreparer()
        let plotCalendar = localGregorian(from: calendar)

        // 1. Weight values (unit conversion + weightless anchoring handled inside the reused builder).
        let rawSeries = preparer.buildWeightSeries(
            from: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight
        )

        // 2. Bake the per-period plot-x (week/month → local noon, year → month-1st noon, total → raw),
        //    then sort ascending — matches the section VMs' `plotXDate` exactly (parity).
        let plotted = rawSeries
            .map { PlottedGraphSeries(original: $0, xDate: plotXDate($0.date, period: period, calendar: plotCalendar)) }
            .sorted { $0.xDate < $1.xDate }

        // 3. One full-domain decimation (no-op for the usual few-hundred-point weight series).
        let decimated = ChartDecimator.decimate(plotted)

        let visibleLength = config.visibleDomainLength(for: period, at: scrollPosition)

        // 4. Adaptive y-axis over the visible window (Y-B). Factored out so a scroll-end settle can
        //    recompute JUST this and update the model in place — see `weightYAxis` / `ChartModel.withYAxis`.
        let yAxis = weightYAxis(
            operations: operations,
            period: period,
            scrollPosition: scrollPosition,
            visibleDomainLength: visibleLength,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            chartHeight: chartHeight,
            lastYAxis: lastYAxis,
            preparer: preparer
        )

        return ChartModel(
            period: period,
            productType: .scale,
            orderedSeriesNames: plotted.isEmpty ? [] : [seriesName],
            seriesPoints: [seriesName: decimated],
            fullResolution: [seriesName: plotted],
            xDomain: xDomainRange(plotted: plotted, scrollPosition: scrollPosition, visibleLength: visibleLength),
            visibleDomainLength: visibleLength,
            xAxisTicks: config.xAxisValues(for: period, from: operations, scrollPosition: scrollPosition),
            yAxis: yAxis,
            dataFingerprint: fingerprint(orderedSeriesNames: [seriesName], points: [seriesName: plotted])
        )
    }

    /// The adaptive y-axis (Y-B) for one visible window: visible ∪ bracketing ops (deduped) → `YAxisScale`,
    /// exactly as `DashboardChartManager.updateYAxisCache`. Total isn't scrollable → the whole dataset
    /// defines the axis. This is the ONLY scroll-position-dependent output of the weight model, so a
    /// scroll-end settle recomputes just this and calls `ChartModel.withYAxis` — leaving the (scroll-stable)
    /// series + x-geometry untouched so the chart never rebuilds its scroll view on settle.
    static func weightYAxis( // swiftlint:disable:this function_parameter_count
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        visibleDomainLength: TimeInterval,
        goalWeight: Double?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: (Double) -> Double,
        chartHeight: CGFloat = 265,
        lastYAxis: YAxisScale? = nil,
        preparer: GraphDataPreparer = GraphDataPreparer()
    ) -> YAxisModel {
        let yAxisOperations = weightYAxisOperations(
            operations: operations,
            period: period,
            scrollPosition: scrollPosition,
            visibleDomainLength: visibleDomainLength,
            preparer: preparer
        )

        let scale = YAxisCalculator.calculateYAxis(
            operations: yAxisOperations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertWeight,
            chartHeight: chartHeight,
            lastScale: lastYAxis
        )

        return YAxisModel(domain: scale.domain, ticks: scale.ticks, average: scale.average)
    }

    /// Mirrors `DashboardChartManager.updateYAxisCache`: visible-window ops (with edge buffer) combined
    /// with the bracketing ops, deduped by `entryTimestamp`; falls back to bracket, then all ops.
    private static func weightYAxisOperations(
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        visibleDomainLength: TimeInterval,
        preparer: GraphDataPreparer
    ) -> [BathScaleWeightSummary] {
        guard period != .total else { return operations }

        let visible = preparer.visibleOperations(
            from: operations, scrollPosition: scrollPosition, visibleDomainLength: visibleDomainLength
        )
        let bracket = preparer.bracketingOperations(
            from: operations, scrollPosition: scrollPosition, visibleDomainLength: visibleDomainLength
        )
        guard !visible.isEmpty else {
            return bracket.isEmpty ? operations : bracket
        }
        let visibleTimestamps = Set(visible.map(\.entryTimestamp))
        return visible + bracket.filter { !visibleTimestamps.contains($0.entryTimestamp) }
    }

    // MARK: - Plot-X normalization (matches the section VMs' `plotXDate`)

    static func plotXDate(_ date: Date, period: TimePeriod, calendar: Calendar) -> Date {
        switch period {
        case .week, .month:
            let dayStart = calendar.startOfDay(for: date)
            return calendar.date(byAdding: .hour, value: 12, to: dayStart) ?? date
        case .year:
            var components = calendar.dateComponents([.year, .month], from: date)
            components.day = 1
            components.hour = 12
            components.minute = 0
            components.second = 0
            return calendar.date(from: components) ?? date
        case .total:
            return date
        }
    }

    // MARK: - Private helpers

    /// Sunday-first Gregorian calendar in the injected calendar's timezone — matches `localCalendar`
    /// in the section VMs so plotted points align with the x-axis ticks.
    private static func localGregorian(from calendar: Calendar) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale
        return cal
    }

    /// Full scrollable x-domain. Widens a single-point (or empty) domain to a full window so Swift
    /// Charts never receives a degenerate range (belt for `ChartDomainSanitizer.orderedDates`).
    private static func xDomainRange(
        plotted: [PlottedGraphSeries],
        scrollPosition: Date,
        visibleLength: TimeInterval
    ) -> ClosedRange<Date> {
        let half = max(visibleLength, 1) / 2
        guard let first = plotted.first?.xDate, let last = plotted.last?.xDate else {
            return scrollPosition.addingTimeInterval(-half)...scrollPosition.addingTimeInterval(half)
        }
        guard first < last else {
            return first.addingTimeInterval(-half)...first.addingTimeInterval(half)
        }
        return first...last
    }

    /// Change token that EXCLUDES the y-axis: series count + first/last (xDate, value) per series.
    /// A y-settle changes `yAxis` but not this, so the `Chart` keeps a stable identity (kills S1).
    private static func fingerprint(
        orderedSeriesNames: [String],
        points: [String: [PlottedGraphSeries]]
    ) -> Int {
        var hasher = Hasher()
        for name in orderedSeriesNames {
            hasher.combine(name)
            let series = points[name] ?? []
            hasher.combine(series.count)
            if let first = series.first {
                hasher.combine(first.xDate)
                hasher.combine(first.original.value)
            }
            if let last = series.last {
                hasher.combine(last.xDate)
                hasher.combine(last.original.value)
            }
        }
        return hasher.finalize()
    }
}
