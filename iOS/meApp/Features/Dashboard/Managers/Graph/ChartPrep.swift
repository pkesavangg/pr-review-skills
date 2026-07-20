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

    /// Half-width (in visible-windows) of the WINDOWED x-axis tick set. The scroll domain stays FULL (so
    /// scrolling is continuous with no walls/jumps), but only ticks within ±this-many windows of the scroll
    /// position are generated. The dominant scroll-hang cost was the `AxisMarks` count, not the canvas: a
    /// small canvas with the full ~1000-tick span still hung, while the same canvas with ~50 ticks was
    /// smooth. So we cap the ticks, not the domain. Refreshed in place at scroll-end
    /// (`DashboardStore.settleWeightChart`) so gridlines follow the scroll without rebuilding the scroll
    /// view. Clamped to the data span, so short-history accounts just get their whole span.
    static let tickWindowRadius: Double = 10

    /// Build the weight `ChartModel` for one period at one scroll position.
    static func buildWeight( // swiftlint:disable:this function_parameter_count function_body_length
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        goalWeight: Double?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double,
        chartHeight: CGFloat = 265,
        lastYAxis: YAxisScale? = nil,
        selectedMetric: String? = nil,
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

        // V-A5: x-geometry (window length, full scrollable domain, ticks) is FULL-DOMAIN and
        //    scroll-INDEPENDENT — sourced from `GraphRenderingConfiguration`, not `data.min…max`. It is
        //    identical at every scroll position, so Swift Charts scrolls natively within it and a y-settle
        //    never rebuilds the scroll region. `visibleDomainLength(for:)` (no position) → month uses the
        //    constant window, not the per-month duration, so scrolling between months doesn't re-lay-out.
        // Weight engine uses a 7-day WEEK viewport (== the Sun→Sun value-alignment stride) for the visible
        // window + y-axis window. The shared `week` (7.15) stays for legacy baby/BPM — MOB-518 review: the
        // flat 7 there had silently narrowed their week view. Non-week periods use the shared config value.
        // (fullXDomain / tick-window keep the shared default; that's a sub-day extent delta, snapping-neutral.)
        let visibleLength = period == .week
            ? DashboardConstants.TimeInterval.weightWeekWindow
            : config.visibleDomainLength(for: period)
        // MOB-518 — FULL scroll domain (scroll-independent, V-A5a): continuous scrolling through all history
        // with no walls and no scroll-view rebuild on settle. The hang is avoided by WINDOWING the ticks
        // (below), not the domain — the AxisMarks count was the real cost, not the canvas.
        let xDomain = config.fullXDomain(for: period, from: operations)
            ?? xDomainRange(plotted: plotted, scrollPosition: scrollPosition, visibleLength: visibleLength)

        // 3. Adaptive y-axis over the visible window (Y-B) — the ONLY scroll-position-dependent output.
        //    Computed inline (rather than via `weightYAxis`) so the domain AND the window ops can also
        //    normalize the co-plotted metric series consistently. (`weightYAxis` stays for the in-place
        //    weight-only settle in `settleWeightChart`.)
        let yAxisOps = yAxisWindowOperations(
            operations: operations,
            period: period,
            scrollPosition: scrollPosition,
            visibleDomainLength: visibleLength,
            preparer: preparer
        )
        let scale = YAxisCalculator.calculateYAxis(
            operations: yAxisOps,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertWeight,
            chartHeight: chartHeight,
            lastScale: lastYAxis
        )
        let yAxis = YAxisModel(domain: scale.domain, ticks: scale.ticks, average: scale.average)

        // 4. Series dicts. Weight is always present; a selected body-comp metric (6e) is co-plotted as a
        //    2nd line NORMALIZED into the weight y-domain (so it overlays the same axis). That normalization
        //    is scroll-dependent (keyed off the y-domain + window ops), so a scroll-end settle must
        //    re-normalize it — `DashboardStore.settleWeightChart` does a full rebuild when a metric is
        //    active (x-geometry is scroll-independent since V-A5a, so the scroll region still stays stable).
        var orderedNames: [String] = plotted.isEmpty ? [] : [seriesName]
        var full: [String: [PlottedGraphSeries]] = [seriesName: plotted]

        if let metric = selectedMetric, metric != seriesName, !plotted.isEmpty {
            let plottedMetric = preparer.buildNormalizedMetricSeriesWithDomain(
                for: metric,
                from: operations,
                visibleOperations: [],
                operationsForYAxis: yAxisOps,
                toWeightDomain: scale.domain,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertWeight: convertWeight
            )
            .map { PlottedGraphSeries(original: $0, xDate: plotXDate($0.date, period: period, calendar: plotCalendar)) }
            .sorted { $0.xDate < $1.xDate }
            if !plottedMetric.isEmpty {
                orderedNames.append(metric)
                full[metric] = plottedMetric
            }
        }

        // 5. One full-domain decimation per series (no-op for the usual few-hundred-point series).
        let decimated = full.mapValues { ChartDecimator.decimate($0) }

        // MOB-1516: weight series (+ any co-plotted metric) are all `.data`; the period line width matches the
        // legacy renderer (3 pt scrollable, 2 pt total). No horizontal reference lines for weight.
        let dataLineWidth: CGFloat = period == .total ? 2 : 3
        let styles = Dictionary(uniqueKeysWithValues: orderedNames.map {
            ($0, ChartSeriesStyle(role: .data, lineWidth: dataLineWidth, showsPoints: true))
        })

        return ChartModel(
            period: period,
            productType: .scale,
            orderedSeriesNames: orderedNames,
            seriesPoints: decimated,
            fullResolution: full,
            xDomain: xDomain,
            visibleDomainLength: visibleLength,
            // MOB-518 — WINDOWED ticks (±tickWindowRadius windows around the scroll position, clamped to the
            // data span): only ~dozens of AxisMarks instead of ~1000 across a multi-year span → the fix for
            // the scroll hang. Refreshed in place at scroll-end so gridlines follow the scroll.
            xAxisTicks: config.boundedXAxisValues(
                for: period, from: operations, around: scrollPosition, windows: tickWindowRadius
            ),
            goalWeight: goalWeight,
            seriesStyle: styles,
            referenceLines: [],
            yAxis: yAxis,
            dataFingerprint: fingerprint(orderedSeriesNames: orderedNames, points: full)
        )
    }

    // MARK: - MOB-1516: BPM (systolic / diastolic / pulse)

    /// Build the BPM `ChartModel` — three `.data` series (systolic/diastolic/pulse) + two fixed AHA
    /// reference lines (120/80). Reuses `GraphDataPreparer.buildBpmChartSeries` (daily wk/mo, monthly yr/total)
    /// and the adaptive `bpmScale` over the visible window (re-settles on scroll like weight, in place).
    /// No goal / weightless / metric co-plot. Same x-geometry (full-domain, windowed ticks) as weight.
    static func buildBpm( // swiftlint:disable:this function_body_length
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        calendar: Calendar = .current,
        config: GraphRenderingConfiguration = GraphRenderingConfiguration()
    ) -> ChartModel {
        let preparer = GraphDataPreparer()
        let plotCalendar = localGregorian(from: calendar)

        // 3 series, already aggregated by the reused builder. Draw order PINNED: systolic → diastolic → pulse
        // (the legacy render order was unspecified — bucket 2 in `seriesRenderPriority`).
        let grouped = Dictionary(
            grouping: preparer.buildBpmChartSeries(from: operations, period: period), by: \.series
        )
        var orderedNames: [String] = []
        var full: [String: [PlottedGraphSeries]] = [:]
        for name in ["systolic", "diastolic", "pulse"] {
            let points = (grouped[name] ?? [])
                .map { PlottedGraphSeries(original: $0, xDate: plotXDate($0.date, period: period, calendar: plotCalendar)) }
                .sorted { $0.xDate < $1.xDate }
            if !points.isEmpty {
                orderedNames.append(name)
                full[name] = points
            }
        }

        // x-geometry — full-domain, scroll-INDEPENDENT (identical to weight; the scroll hang is avoided by
        // windowing the ticks, not the domain).
        let visibleLength = config.visibleDomainLength(for: period)
        let xDomain = config.fullXDomain(for: period, from: operations)
            ?? xDomainRange(
                plotted: full[orderedNames.first ?? ""] ?? [],
                scrollPosition: scrollPosition,
                visibleLength: visibleLength
            )

        // Adaptive clinical y-axis over the visible window (mmHg/bpm).
        let yAxis = bpmYAxis(
            operations: operations,
            period: period,
            scrollPosition: scrollPosition,
            visibleDomainLength: visibleLength,
            preparer: preparer
        )

        let decimated = full.mapValues { ChartDecimator.decimate($0) }
        let lineWidth: CGFloat = period == .total ? 2 : 3
        let styles = Dictionary(uniqueKeysWithValues: orderedNames.map {
            ($0, ChartSeriesStyle(role: .data, lineWidth: lineWidth, showsPoints: true))
        })
        // MOB-1591: the fixed AHA 120/80 reference rules only make sense alongside real readings. With no BPM
        // entries the chart is an empty skeleton (hidden y-axis, like empty weight), so drawing two lone
        // horizontal lines at 120/80 read as phantom data — suppress them until there's at least one series.
        let referenceLines: [ChartReferenceLine] = orderedNames.isEmpty
            ? []
            : [
                ChartReferenceLine(value: Double(BpmConstants.normalSystolic), dashed: true, color: .bpmReference),
                ChartReferenceLine(value: Double(BpmConstants.normalDiastolic), dashed: true, color: .bpmReference)
            ]

        return ChartModel(
            period: period,
            productType: .bpm,
            orderedSeriesNames: orderedNames,
            seriesPoints: decimated,
            fullResolution: full,
            xDomain: xDomain,
            visibleDomainLength: visibleLength,
            xAxisTicks: config.boundedXAxisValues(
                for: period, from: operations, around: scrollPosition, windows: tickWindowRadius
            ),
            goalWeight: nil,
            seriesStyle: styles,
            referenceLines: referenceLines,
            yAxis: yAxis,
            dataFingerprint: fingerprint(orderedSeriesNames: orderedNames, points: full)
        )
    }

    /// The adaptive BPM y-axis for one visible window — `bpmScale` over the windowed ops (visible ∪ bracket),
    /// so it re-settles as you scroll to windows with different value ranges. Used by `buildBpm` and by the
    /// in-place scroll-end settle (`DashboardStore.settleChart` for `.bpm`).
    static func bpmYAxis(
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        visibleDomainLength: TimeInterval,
        preparer: GraphDataPreparer = GraphDataPreparer()
    ) -> YAxisModel {
        let windowOps = yAxisWindowOperations(
            operations: operations,
            period: period,
            scrollPosition: scrollPosition,
            visibleDomainLength: visibleDomainLength,
            preparer: preparer
        )
        let scale = DashboardChartScaleProvider.bpmScale(from: windowOps)
        return YAxisModel(domain: scale.domain, ticks: scale.ticks, average: scale.average)
    }

    // MARK: - MOB-1516: Baby growth (real series + WHO/CDC percentile reference curves)

    /// Build the baby `ChartModel` — one `.data` series (weight OR height) + up to seven `.reference`
    /// percentile curves (WHO/CDC), drawn line-only BEHIND the data. The curves are sampled across the FULL
    /// domain (scroll-independent → they slide with the poster, replacing the legacy per-scroll windowing).
    /// The reference-driven y-axis (widened to contain the curve band) is window-adaptive, recomputed on a
    /// scroll-end via a full rebuild (baby has no metric co-plot, and the curves are cheap analytic values).
    /// `isSexWithheld` → no curves (parity). Reuses `BabyDashboardChartSupport` verbatim.
    ///
    /// ⚠️ Curve density: `percentileSeries` caps to ~150 points per curve across the dateRange. Over a full
    /// multi-month domain a WEEK window then sees few curve points, but the curves are smooth (`.monotone`)
    /// so a near-linear week segment reads correctly. VERIFY week-zoom smoothness on device; if too coarse,
    /// raise the sampling cadence in `BabyPercentileGrowthReference.percentileChartPoints`.
    static func buildBaby( // swiftlint:disable:this function_parameter_count function_body_length
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        scrollPosition: Date,
        babyProfile: BabyProfile,
        metric: BabyMetric,
        convertWeight: @escaping (Double) -> Double,
        convertDecigramsToDisplay: @escaping (Int) -> Double,
        calendar: Calendar = .current,
        config: GraphRenderingConfiguration = GraphRenderingConfiguration()
    ) -> ChartModel {
        let preparer = GraphDataPreparer()
        let plotCalendar = localGregorian(from: calendar)
        // MOB-1591: baby uses the SAME exact 7-day WEEK viewport as weight (`weightWeekWindow`), not the shared
        // `week` (7.15). The flat 7 makes page == period == the value-alignment unit, so the value-aligned
        // scroll rests exactly on a Sunday boundary; the 0.15-day surplus in the shared constant let a partial
        // 8th day / second Sunday bleed in, so week windows drifted onto Saturday starts (e.g. Jun 27–Jul 3
        // instead of Jun 28–Jul 4) and the section-switch landing looked off. Non-week periods keep the shared
        // value.
        let visibleLength = period == .week
            ? DashboardConstants.TimeInterval.weightWeekWindow
            : config.visibleDomainLength(for: period)
        let xDomain = config.fullXDomain(for: period, from: operations)
            ?? xDomainRange(plotted: [], scrollPosition: scrollPosition, visibleLength: visibleLength)
        // Curves span the full (scroll-independent) domain; the y-axis adapts to the visible window (parity
        // with the legacy `babyChartVisibleDateRange`) — total isn't scrollable, so it uses the whole domain.
        let yWindow = period == .total
            ? xDomain
            : scrollPosition...scrollPosition.addingTimeInterval(visibleLength)

        let dataName: String
        let rawData: [GraphSeries]
        let rawCurves: [GraphSeries]
        let scale: YAxisScale
        switch metric {
        case .weight:
            dataName = DashboardStrings.weight
            rawData = preparer.buildWeightSeries(
                from: operations, isWeightlessMode: false, anchorWeight: nil, convertWeight: convertWeight
            )
            rawCurves = BabyDashboardChartSupport.percentileSeries(
                for: babyProfile, dateRange: xDomain, convertDecigramsToDisplay: convertDecigramsToDisplay
            )
            scale = BabyDashboardChartSupport.yAxisScale(
                for: operations,
                babyProfile: babyProfile,
                dateRange: yWindow,
                convertStoredWeightToDisplay: { convertWeight(Double($0)) },
                convertDecigramsToDisplay: convertDecigramsToDisplay
            )
        case .height:
            dataName = BabyDashboardChartSupport.heightSeriesName
            rawData = BabyDashboardChartSupport.heightSeries(from: operations)
            rawCurves = BabyDashboardChartSupport.heightPercentileSeries(for: babyProfile, dateRange: xDomain)
            scale = BabyDashboardChartSupport.heightYAxisScale(
                for: operations, babyProfile: babyProfile, dateRange: yWindow
            )
        }

        func plot(_ series: [GraphSeries]) -> [PlottedGraphSeries] {
            series
                .map { PlottedGraphSeries(original: $0, xDate: plotXDate($0.date, period: period, calendar: plotCalendar)) }
                .sorted { $0.xDate < $1.xDate }
        }

        // Reference curves FIRST (drawn behind), then the data series (on top).
        let curvesByName = Dictionary(grouping: rawCurves, by: \.series)
        var orderedNames: [String] = []
        var full: [String: [PlottedGraphSeries]] = [:]
        var styles: [String: ChartSeriesStyle] = [:]
        for line in BabyPercentileLine.allCases {
            let name = "baby_percentile_\(line.rawValue)"
            let points = plot(curvesByName[name] ?? [])
            guard !points.isEmpty else { continue }
            orderedNames.append(name)
            full[name] = points
            styles[name] = ChartSeriesStyle(role: .reference, lineWidth: 1, showsPoints: false)
        }
        let dataPoints = plot(rawData)
        if !dataPoints.isEmpty {
            orderedNames.append(dataName)
            full[dataName] = dataPoints
            styles[dataName] = ChartSeriesStyle(role: .data, lineWidth: period == .total ? 2 : 3, showsPoints: true)
        }

        let decimated = full.mapValues { ChartDecimator.decimate($0) }
        return ChartModel(
            period: period,
            productType: .baby,
            orderedSeriesNames: orderedNames,
            seriesPoints: decimated,
            fullResolution: full,
            xDomain: xDomain,
            visibleDomainLength: visibleLength,
            xAxisTicks: config.boundedXAxisValues(
                for: period, from: operations, around: scrollPosition, windows: tickWindowRadius
            ),
            goalWeight: nil,
            seriesStyle: styles,
            referenceLines: [],
            yAxis: YAxisModel(domain: scale.domain, ticks: scale.ticks, average: scale.average),
            dataFingerprint: fingerprint(orderedSeriesNames: orderedNames, points: full)
        )
    }

    // MARK: - MOB-1591: empty skeleton (no readings)

    /// Build an EMPTY chart skeleton for `productType` — no data series, no reference curves: just the
    /// period-correct x-geometry (`xDomain` / `xAxisTicks` / `visibleDomainLength`) and a placeholder
    /// (hidden) y-axis. Used for the baby dashboard when the baby has no real readings yet, so the empty
    /// baby graph renders through the SAME engine as an empty weight/BPM chart — per-period x labels +
    /// gridlines, reserved y-axis column, closed box, leading inset — instead of a separate hand-rolled
    /// view that had to re-implement (and got wrong) the per-period axes.
    ///
    /// `TrendChartView.hidesYAxis` hides the `.placeholder` numbers + horizontal gridlines (no `.data`
    /// series, no goal), but the reserved 40 pt column keeps the plot width / trailing edge identical to a
    /// populated chart. The x-geometry is byte-identical to an empty weight chart (`buildWeight` with no
    /// operations), so every empty state looks and measures the same across products.
    static func buildEmpty(
        productType: EntryType,
        period: TimePeriod,
        scrollPosition: Date,
        config: GraphRenderingConfiguration = GraphRenderingConfiguration()
    ) -> ChartModel {
        // MOB-1591: match the populated engines' exact 7-day WEEK viewport (`weightWeekWindow`) so an empty
        // week reads identically to a populated one and rests on a clean Sunday boundary (see `buildBaby`).
        let visibleLength = period == .week
            ? DashboardConstants.TimeInterval.weightWeekWindow
            : config.visibleDomainLength(for: period)
        let xDomain = config.fullXDomain(for: period, from: [])
            ?? xDomainRange(plotted: [], scrollPosition: scrollPosition, visibleLength: visibleLength)
        return ChartModel(
            period: period,
            productType: productType,
            orderedSeriesNames: [],
            seriesPoints: [:],
            fullResolution: [:],
            xDomain: xDomain,
            visibleDomainLength: visibleLength,
            xAxisTicks: config.boundedXAxisValues(
                for: period, from: [], around: scrollPosition, windows: tickWindowRadius
            ),
            goalWeight: nil,
            seriesStyle: [:],
            referenceLines: [],
            yAxis: .placeholder,
            dataFingerprint: fingerprint(orderedSeriesNames: [], points: [:])
        )
    }

    /// The adaptive y-axis (Y-B) for one visible window: visible ∪ bracketing ops (deduped) → `YAxisScale`,
    /// exactly as `DashboardChartManager.updateYAxisCache`. Total isn't scrollable → the whole dataset
    /// defines the axis. This is the ONLY scroll-position-dependent output of the weight model, so a
    /// scroll-end settle recomputes just this and calls `ChartModel.withYAxisAndTicks` — leaving the (scroll-stable)
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
        let yAxisOperations = yAxisWindowOperations(
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
    private static func yAxisWindowOperations(
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

    // MARK: - Plot-X normalization (v2 weight engine: points sit ON the day/month gridline)

    /// The x-position a summary plots at. **Start-of-day (local-tz midnight)** for week/month, and the
    /// **1st-of-month at midnight** for year — so each point lands ON its day/month gridline, not centered in
    /// the column. The v2 gridlines (`WeightChartView.gridTicks`), week labels, and the value-aligned scroll
    /// all rest on midnight/day boundaries already, so plotting at midnight makes point + line + scroll-rest
    /// coincide (e.g. a Wednesday reading sits on the "Wed" line, not between Wed and Thu). The incoming
    /// `date` is `BathScaleWeightSummary.date`, which aggregation already normalized to the **local** day
    /// (from the entry's UTC timestamp), so `startOfDay` here is DST-correct and timezone-correct.
    ///
    /// NOTE: this deliberately DIVERGES from the legacy section VMs' `plotXDate` (which offset to local NOON
    /// so points centered between the legacy noon gridlines). The legacy engine (baby/BPM) keeps its noon
    /// convention — do not unify them; the two engines draw their gridlines at different times (v2 = midnight,
    /// legacy = noon), so each plots on its own grid.
    static func plotXDate(_ date: Date, period: TimePeriod, calendar: Calendar) -> Date {
        switch period {
        case .week, .month:
            return calendar.startOfDay(for: date)
        case .year:
            var components = calendar.dateComponents([.year, .month], from: date)
            components.day = 1
            components.hour = 0
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
