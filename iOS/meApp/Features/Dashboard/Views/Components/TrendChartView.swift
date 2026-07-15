//
//  TrendChartView.swift
//  meApp
//
//  MOB-518 — v2 weight-graph engine (greenfield strangler rebuild).
//
//  The new STABLE `Chart`: one view identity for the life of the period, rendering straight from an
//  immutable `ChartModel`. No `.id` (so no teardown/rebuild on a y-settle — the S1 fix by construction),
//  no per-frame windowing (Swift Charts owns the native scroll over the full decimated series — S2/S10),
//  no caches. The y-axis animates in place on settle (the model is only rebuilt at scroll-end, never
//  per frame — see `WeightChartHost`).
//
//  V2 scope: line + points + adaptive y-axis + native scroll. Crosshair/selection, header average, and
//  the goal chip/line come in V4. Marks reuse the exact legacy styling (`DashboardChartStyleProvider`
//  colors, `.monotone`, period line width / point size, `ChartDomainSanitizer`).
//
//  MULTI-SERIES: `orderedSeriesNames`/`seriesPoints` already loop over N series; BPM/baby add reference
//  lines + percentile styling here once weight is signed off.
//

import Accessibility
import Charts
import SwiftUI

/// Carries the selected point's (already-clamped) x-position out of the chart so the date callout can be
/// floated ABOVE the plot as an overlay (issue #2) — without reserving in-plot space that would compress
/// the graph.
private struct SelectionCalloutXKey: PreferenceKey {
    static let defaultValue: CGFloat? = nil
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        if let next = nextValue() { value = next }
    }
}

/// V4 (6c): carries the goal value's y-position out of the plot so the goal chip can be floated over the
/// y-axis label column as an overlay — parity with the legacy `BaseGraphView`, which `.position`s the chip
/// at `chartFrame.width - 20` (i.e. ON the trailing y-axis marks), not at the plot's inner trailing edge.
private struct GoalChipYKey: PreferenceKey {
    static let defaultValue: CGFloat? = nil
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        if let next = nextValue() { value = next }
    }
}

struct TrendChartView: View {

    let model: ChartModel
    @Binding var scrollX: Date
    /// V4 (6a): raw x-value from `.chartXSelection`; the host snaps it to the nearest day/month gridline
    /// (interpolating the value on a gap day) and drives `crosshairDate`.
    @Binding var selectedX: Date?
    /// V4 (6a): plotted x-date of the currently-selected (snapped) point — draws the crosshair + enlarges the
    /// matching point. `nil` when nothing is selected / crosshair hidden (e.g. during a scroll).
    let crosshairDate: Date?
    /// Issue #2: the selected point's date, formatted + lowercased ("jul 7, 2026" / "jul 2026"), shown as a
    /// callout ABOVE the crosshair line at the top of the plot. `nil` when nothing is selected.
    let selectionDateLabel: String?
    /// V4 (6c): formatted goal-weight label for the goal chip (nil → no chip). The value is `model.goalWeight`.
    let goalLabel: String?
    /// V4 (6f): month-view active-month interval (nil outside month view / while scrolling) — points whose
    /// entry date falls outside it are drawn in the muted "outside month" colour.
    let activeMonthInterval: DateInterval?
    /// V4 (6f): suppresses greying mid-scroll (parity with the legacy `guard !isScrolling`).
    let isScrolling: Bool
    let yLabel: (Double) -> String
    let xLabel: (Date) -> String
    let theme: AppColors.Palette
    /// MOB-1516 (BPM): the selected/window reading's AHA class, so systolic+diastolic recolour on selection.
    /// `nil` for weight/baby → the colour provider uses its default. A cheap injected colour swap (no model
    /// rebuild), like `activeMonthInterval`/`isScrolling`.
    var bpmClassification: AhaPressureClass?

    private var isScrollable: Bool { model.period != .total }
    /// Fixed width the y-axis number is centered in, so it sits off the trailing screen edge with a gap
    /// (parity with the legacy `BaseGraphView.yAxisLabelWidth`).
    private let yAxisLabelWidth: CGFloat = 40
    private func pointArea(selected: Bool) -> CGFloat {
        let diameter: CGFloat = selected ? (isScrollable ? 12 : 8) : (isScrollable ? 8 : 4)
        let radius = diameter / 2
        return .pi * radius * radius
    }

    /// Finite, positive-width y-domain (W2 guard) — used for the scale AND the mark clamp so points and
    /// scale always agree (the S6 fix: plot the same value in `LineMark` and `PointMark`).
    private var yDomain: ClosedRange<Double> { ChartDomainSanitizer.finiteWidth(model.yAxis.domain) }

    /// V4 (6c): goal value clamped into the y-domain so the chip stays visible (parity with the legacy
    /// clamp-to-edge). `nil` when no goal is set.
    private var clampedGoalValue: Double? {
        model.goalWeight.map { min(max($0, yDomain.lowerBound), yDomain.upperBound) }
    }

    /// Window width. Total isn't scrollable → show the whole span.
    private var visibleLength: TimeInterval {
        guard isScrollable else {
            return max(model.xDomain.upperBound.timeIntervalSince(model.xDomain.lowerBound), 1)
        }
        return max(model.visibleDomainLength, 1)
    }

    // MARK: - X-axis ticks (V3 — parity with the legacy `gridTicks` / `adjustedLabelTicks`)

    /// Gridline ticks — drop the trailing phantom tick the generators append (matches `gridTicks`), then
    /// snap each to its day boundary (midnight). The tick generators place ticks at NOON (so labels read
    /// centered under each day), but the scroll decelerates onto a midnight/day boundary (value-aligned
    /// only snaps to `hour: 0`; noon is a device-proven no-op). Drawing the *gridlines* at midnight makes
    /// the leading period rule coincide with the left edge → no gap between the edge and the Sunday/1st
    /// line. On week (7-day window) that 12 h shift is the ~7% gap being closed; on month/year it's <2%,
    /// invisible. Labels stay at noon (see `labelTicks`) so they remain centered, like Apple Health.
    private var gridTicks: [Date] {
        guard !model.xAxisTicks.isEmpty else { return [] }
        let calendar = Calendar.current
        return model.xAxisTicks.dropLast().map { calendar.startOfDay(for: $0) }
    }

    /// Label ticks — week/month/year drop the phantom so labels stop at the last real unit; total keeps it
    /// (matches the legacy `adjustedLabelTicks`).
    private var labelTicks: [Date] {
        guard !model.xAxisTicks.isEmpty else { return [] }
        let base = model.period == .total ? model.xAxisTicks : Array(model.xAxisTicks.dropLast())
        // Week only: snap labels to the same day boundary (midnight) as the gridlines so each day label
        // sits ON its rule — matching month/year, where "1"/"Jan" sit on the boundary rule. Without this the
        // label stays at the noon tick, half a column right of the midnight gridline → the "wrong side" look.
        // Month/year keep the noon tick (their 12 h vs the midnight gridline is <2% of the window → the label
        // already reads on the rule), so they're untouched. Month labels are a continuous Sunday grid
        // (… 17, 24, 31, 7 …) — the solid month divider is drawn separately (see `monthBoundaryTicks`), so no
        // Sunday label is hidden by an adjacent boundary tick.
        guard model.period == .week else { return base }
        let calendar = Calendar.current
        return base.map { calendar.startOfDay(for: $0) }
    }

    /// MOB-518 — the 1st of each month within the tick window, for the SOLID month-divider rule. Drawn as a
    /// gridline-only mark (no tick, no label) in a dedicated `AxisMarks` block so it can't hide the Sunday
    /// label that sits next to it (the reason the boundary rule is NOT baked into `monthlyWeeklyTicks`).
    private var monthBoundaryTicks: [Date] {
        guard model.period == .month,
              let lo = model.xAxisTicks.first,
              let hi = model.xAxisTicks.last else { return [] }
        let calendar = Calendar.current
        guard var monthStart = calendar.dateInterval(of: .month, for: lo)?.start else { return [] }
        var result: [Date] = []
        while monthStart <= hi {
            result.append(monthStart)
            guard let next = calendar.date(byAdding: .month, value: 1, to: monthStart) else { break }
            monthStart = next
        }
        return result
    }

    /// V4 (6f): a point's entry date falls outside the active month (month view only, not while scrolling)
    /// — mirrors the legacy `isOutsideActiveMonth`.
    private func isOutsideActiveMonth(_ date: Date) -> Bool {
        guard model.period == .month, !isScrolling, let interval = activeMonthInterval else { return false }
        return date < interval.start || date >= interval.end
    }

    /// A "major" boundary that gets a solid vertical rule — start of week / Jan 1st
    /// (mirrors `BaseSectionViewModel.shouldShowSolidLine`, incl. its use of `Calendar.current`).
    /// MONTH is deliberately NOT here: its solid divider is drawn by a dedicated gridline-only block over
    /// `monthBoundaryTicks`, so the boundary can't sit on a `gridTicks` tick and hide the adjacent Sunday
    /// label. So for month this returns false → every Sunday gridline is light/dashed.
    private func isPeriodBoundary(_ date: Date) -> Bool {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.weekday, .day, .month], from: date)
        switch model.period {
        case .week:  return components.weekday == calendar.firstWeekday
        case .month: return false
        case .year:  return components.month == 1 && components.day == 1
        case .total: return false
        }
    }

    /// Native value-aligned scrolling, Apple-Health style. Per Swift Charts (Majid / Apple docs):
    /// `matching` is the FINE grid of valid resting positions; `majorAlignment` is the COARSE boundary a
    /// *fling* decelerates onto. We set `matching` to the DAY grid (month grid for year) so a slow drag can
    /// rest the window on ANY unit — e.g. Wed→Wed, or mid-month — and `majorAlignment` to the period boundary
    /// (week start / 1st / Jan 1) so a fling still lands on a clean full week/month/year window in one motion.
    /// (Issue #1: the earlier config put the period boundary in BOTH `matching` and `majorAlignment`, which
    /// force-snapped EVERY release to the boundary and made mid-window placement impossible.) `majorAlignment`
    /// is a subset of `matching` (a Sunday-midnight is a day-midnight; Jan-1 is a month-1st), as the API
    /// requires. The host (`commitWeightScroll`) records wherever native rested VERBATIM — no re-snap, no
    /// reflect — so visual == committed with no post-release hop whether the user flung (→ boundary) or
    /// nudged (→ any day), and re-adopting the stored position on return never drifts by a unit.
    private func scrollBehavior(for period: TimePeriod) -> ValueAlignedChartScrollTargetBehavior {
        let firstWeekday = Calendar.current.firstWeekday
        switch period {
        case .week:
            // Fine grid = any day (rest on Wed→Wed); a fling lands on the week start.
            return ValueAlignedChartScrollTargetBehavior(
                matching: DateComponents(hour: 0),
                majorAlignment: .matching(DateComponents(hour: 0, weekday: firstWeekday))
            )
        case .month:
            // Fine grid = any day (rest mid-month); a fling lands on the 1st.
            return ValueAlignedChartScrollTargetBehavior(
                matching: DateComponents(hour: 0),
                majorAlignment: .matching(DateComponents(day: 1, hour: 0))
            )
        case .year:
            // Fine grid = any month-1st (rest on any month); a fling lands on Jan 1.
            return ValueAlignedChartScrollTargetBehavior(
                matching: DateComponents(day: 1, hour: 0),
                majorAlignment: .matching(DateComponents(month: 1, day: 1, hour: 0))
            )
        case .total:
            return ValueAlignedChartScrollTargetBehavior(
                matching: DateComponents(hour: 0),
                majorAlignment: .page
            )
        }
    }

    /// Issue #2 — the selected point's x-position (in the chart's coordinate space), clamped so a label near
    /// the leading/trailing edge shifts inward instead of clipping. `nil` when nothing is selected.
    private func calloutX(_ proxy: ChartProxy, _ geo: GeometryProxy) -> CGFloat? {
        guard let crosshairDate,
              let anchor = proxy.plotFrame,
              let xInPlot = proxy.position(forX: crosshairDate) else { return nil }
        let plot = geo[anchor]
        // Keep the label center ≥ `inset` from each plot edge so a ≤ 2·inset-wide label never clips.
        let inset: CGFloat = 55
        let lower = plot.minX + inset
        let upper = max(lower, plot.maxX - inset)
        return min(max(plot.minX + xInPlot, lower), upper)
    }

    /// V4 (6c) — the (clamped) goal value's y-position in the chart's coordinate space, so the goal chip can
    /// be floated over the trailing y-axis label column (see `GoalChipYKey`). `nil` when no goal is set.
    private func goalChipY(_ proxy: ChartProxy, _ geo: GeometryProxy) -> CGFloat? {
        guard let goalValue = clampedGoalValue,
              let anchor = proxy.plotFrame,
              let yInPlot = proxy.position(forY: goalValue) else { return nil }
        return geo[anchor].minY + yInPlot
    }

    /// MOB-1516: map a reference-line colour role to a theme token (keeps `ChartModel` free of theme types).
    private func referenceLineColor(_ role: ChartReferenceLineColor) -> Color {
        switch role {
        case .bpmReference: return theme.textSubheading.opacity(0.4)
        }
    }

    var body: some View {
        Chart {
            // MOB-1516 (BPM): fixed horizontal reference rules (systolic 120 / diastolic 80), drawn FIRST so
            // the data series render on top. Empty for weight/baby → nothing drawn.
            ForEach(Array(model.referenceLines.enumerated()), id: \.offset) { _, line in
                RuleMark(y: .value("Reference", line.value))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: line.dashed ? [4, 4] : []))
                    .foregroundStyle(referenceLineColor(line.color))
            }

            ForEach(model.orderedSeriesNames, id: \.self) { name in
                // MOB-1516: per-series style (role/lineWidth/showsPoints) is baked into the model. Weight/BPM
                // series are `.data` (line + dots); baby percentile curves are `.reference` (line only).
                let style = model.style(for: name)
                let regularColors = DashboardChartStyleProvider.seriesColors(
                    for: name,
                    productType: model.productType,
                    theme: theme,
                    bpmClassification: bpmClassification,
                    isOutsideMonthInterval: false
                )
                let outsideColors = DashboardChartStyleProvider.seriesColors(
                    for: name,
                    productType: model.productType,
                    theme: theme,
                    bpmClassification: bpmClassification,
                    isOutsideMonthInterval: true
                )
                ForEach(model.seriesPoints[name] ?? []) { plotted in
                    let value = min(max(plotted.original.value, yDomain.lowerBound), yDomain.upperBound)
                    let isSelected = crosshairDate.map { plotted.xDate == $0 } ?? false
                    // V4 (6f): dim points/segments outside the active month (month view, when not scrolling).
                    let colors = isOutsideActiveMonth(plotted.original.date) ? outsideColors : regularColors

                    LineMark(
                        x: .value("Date", plotted.xDate),
                        y: .value(name, value),
                        series: .value("Series", name)
                    )
                    .foregroundStyle(colors.line)
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: style.lineWidth))

                    if style.showsPoints {
                        PointMark(
                            x: .value("Date", plotted.xDate),
                            y: .value(name, value)
                        )
                        .symbolSize(pointArea(selected: isSelected))
                        .foregroundStyle(colors.point)
                    }
                }
            }

            // V4 (6a) — vertical crosshair at the selected (snapped) point. High-contrast neutral rule,
            // behind the marks (zIndex −100), width 1 — parity with the legacy `CrosshairContent`.
            if let crosshairDate {
                RuleMark(x: .value("Selected", crosshairDate))
                    .zIndex(-100)
                    .foregroundStyle(theme.actionPrimary)
                    .lineStyle(StrokeStyle(lineWidth: 1))
            }

            // V4 (6c) — the goal chip is NOT drawn inside the plot (an `.annotation(position: .trailing)` pins
            // it to the plot's INNER trailing edge, left of the y-axis numbers — the "shows differently" bug).
            // It's floated as an overlay over the trailing y-axis label column instead (see `goalChipY` +
            // the `.overlayPreferenceValue` below), matching the legacy chip's on-axis placement.
        }
        .chartXSelection(value: $selectedX)
        .chartYScale(domain: yDomain)
        .chartXScale(domain: ChartDomainSanitizer.orderedDates(model.xDomain))
        .chartYAxis {
            AxisMarks(values: model.yAxis.ticks) { value in
                AxisGridLine()
                if let doubleValue = value.as(Double.self) {
                    AxisValueLabel {
                        // Parity with the legacy `yAxisMarks`: center the number in a fixed-width box so it
                        // sits off the right screen edge with a gap, instead of the bare label hugging the
                        // trailing edge.
                        Text(yLabel(doubleValue))
                            .frame(width: yAxisLabelWidth, alignment: .center)
                    }
                }
            }
        }
        .chartXAxis {
            // V3 — vertical gridlines: solid rule at period boundaries (week start / month 1st / Jan 1),
            // light default rule between. Parity with the legacy `.chartXAxis` first `AxisMarks` block.
            AxisMarks(values: gridTicks) { value in
                if let date = value.as(Date.self), isPeriodBoundary(date) {
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 1))
                        .foregroundStyle(theme.statusIconSecondaryDisabled)
                    AxisTick(stroke: StrokeStyle(lineWidth: 1))
                        .foregroundStyle(theme.statusIconSecondaryDisabled)
                } else {
                    AxisGridLine()
                    AxisTick()
                }
            }
            // Month only — the solid month-divider rule, drawn as a GRIDLINE-ONLY mark (no tick, no label) at
            // each month's 1st, so it can't sit on a `gridTicks` tick and hide the Sunday label beside it.
            AxisMarks(values: monthBoundaryTicks) { _ in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 1))
                    .foregroundStyle(theme.statusIconSecondaryDisabled)
            }
            // Labels only (no gridline here → no double rule). Month gets a background chip, like the legacy.
            AxisMarks(values: labelTicks) { value in
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        if model.period == .month {
                            Text(xLabel(date))
                                .font(.caption)
                                .foregroundStyle(theme.textSubheading)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 2)
                                .background(theme.textInverse)
                        } else {
                            Text(xLabel(date))
                                .font(.caption)
                                .foregroundStyle(theme.textSubheading)
                        }
                    }
                }
            }
        }
        // Trailing "closing" rule: a fixed 1 pt vertical line at the plot's right edge (where the y-axis
        // begins), so the last week/month/year window reads as a closed frame instead of an open right side.
        // `.chartPlotStyle` styles the FIXED viewport (content scrolls within it), so the line stays put at
        // the window's right edge as you scroll — matching the leading period-boundary rule on the left.
        .chartPlotStyle { plot in
            plot.overlay(alignment: .trailing) {
                Rectangle()
                    .fill(theme.statusIconSecondaryDisabled)
                    .frame(width: 1)
            }
        }
        // Issue #2 — publish the selected point's (clamped) x so the overlay below can float the date label
        // ABOVE the chart. `.chartBackground` only emits a preference here (Color.clear) — it doesn't render
        // the label and doesn't reserve plot space, so the graph is NOT compressed.
        .chartBackground { proxy in
            GeometryReader { geo in
                Color.clear
                    .preference(key: SelectionCalloutXKey.self, value: calloutX(proxy, geo))
                    .preference(key: GoalChipYKey.self, value: goalChipY(proxy, geo))
            }
        }
        .chartLegend(.hidden)
        // VoiceOver Audio Graph — parity with the legacy `BaseGraphView.accessibilityChartDescriptor`
        // (MOB-518 review: the v2 weight chart had dropped it, so the primary chart exposed nothing to
        // VoiceOver). Built from the immutable `model` (see the `AXChartDescriptorRepresentable` extension).
        .accessibilityChartDescriptor(self)
        .chartScrollableAxes(isScrollable ? .horizontal : [])
        .chartXVisibleDomain(length: ChartDomainSanitizer.positiveLength(visibleLength))
        .chartScrollPosition(x: $scrollX)
        // MOB-518 — native value-aligned scrolling (Apple Health): a fling decelerates onto the period
        // boundary (Sunday / 1st / Jan 1) in one motion; a slow drag rests on any day/month, so the user can
        // place the window mid-period (Wed→Wed). See `scrollBehavior(for:)`.
        .chartScrollTargetBehavior(scrollBehavior(for: model.period))
        // The model is only rebuilt at scroll-END, so the y-domain changes once per settle → this is the
        // single, smooth, adaptive settle (Y-B). No animation fires during a drag (nothing changes then).
        .animation(.easeInOut(duration: 0.25), value: yDomain)
        .frame(height: 265)
        // Issue #2 — the date callout floats in the gap ABOVE the plot, at the selected x (from the preference
        // above). It OVERFLOWS the chart's top edge into the header gap (no ancestor clips it), so it does NOT
        // compress the plot or crowd the x-axis labels / section buttons — it reads as "above the graph", like
        // Health, with the x clamped so it stays fully visible at the leading/trailing edges.
        .overlayPreferenceValue(SelectionCalloutXKey.self) { calloutX in
            if let calloutX, let selectionDateLabel {
                Text(selectionDateLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundStyle(theme.textSubheading)
                    .fixedSize()
                    .position(x: calloutX, y: -12)
            }
        }
        // V4 (6c) — the goal chip floats over the trailing y-axis label column at the goal's y-level, so it
        // reads as sitting ON the y-axis marks (parity with the legacy `chartFrame.width - 20` placement),
        // not adrift inside the plot. `width - yAxisLabelWidth/2` centres it on the y-axis number column
        // (the number is centred in a `yAxisLabelWidth`-wide box at the trailing edge), and the y-domain
        // clamp on `clampedGoalValue` keeps it on-screen when the goal sits far outside the visible range.
        .overlayPreferenceValue(GoalChipYKey.self) { goalY in
            if let goalY, let goalLabel {
                GeometryReader { geo in
                    GoalWeightChipView(label: goalLabel, theme: theme)
                        .position(x: geo.size.width - yAxisLabelWidth / 2, y: goalY)
                }
            }
        }
    }
}

// MARK: - AXChartDescriptorRepresentable (VoiceOver Audio Graph)

/// Exposes the weight chart to VoiceOver as a navigable Audio Graph, mirroring the legacy
/// `BaseGraphView` descriptor (title "Weight trend chart", categorical Date x-axis, numeric Weight y-axis,
/// per-series data points). Built purely from the immutable `ChartModel` this view already holds — no store
/// access — so it stays in lock-step with what's drawn. MOB-518 review restored this after the v2 rebuild
/// had left the primary chart with no accessibility semantics.
extension TrendChartView: AXChartDescriptorRepresentable {
    func makeChartDescriptor() -> AXChartDescriptor {
        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .medium
        dateFormatter.timeStyle = .none

        // Ordered unique date strings across all plotted series (full resolution) → categorical x-axis.
        let allPoints = model.orderedSeriesNames
            .flatMap { model.fullResolution[$0] ?? [] }
            .sorted { $0.original.date < $1.original.date }
        var seenDates = Set<String>()
        var orderedDateStrings: [String] = []
        for point in allPoints {
            let str = dateFormatter.string(from: point.original.date)
            if seenDates.insert(str).inserted { orderedDateStrings.append(str) }
        }

        let xAxis = AXCategoricalDataAxisDescriptor(
            title: DashboardStrings.accChartXAxisName,
            categoryOrder: orderedDateStrings.isEmpty ? ["–"] : orderedDateStrings
        )

        // `yDomain` is already sanitized (finite, positive-width); the guard is belt-and-suspenders.
        let safeRange = yDomain.lowerBound < yDomain.upperBound
            ? yDomain
            : yDomain.lowerBound...(yDomain.lowerBound + 1)
        let yAxis = AXNumericDataAxisDescriptor(
            title: DashboardStrings.accChartWeightYAxisName,
            range: safeRange,
            gridlinePositions: model.yAxis.ticks
        ) { yLabel($0) }

        var seriesDescriptors = model.orderedSeriesNames.compactMap { name -> AXDataSeriesDescriptor? in
            guard let points = model.fullResolution[name], !points.isEmpty else { return nil }
            let dataPoints = points
                .sorted { $0.original.date < $1.original.date }
                .map { AXDataPoint(x: dateFormatter.string(from: $0.original.date), y: $0.original.value) }
            return AXDataSeriesDescriptor(name: name, isContinuous: true, dataPoints: dataPoints)
        }
        if seriesDescriptors.isEmpty {
            seriesDescriptors = [AXDataSeriesDescriptor(name: "", isContinuous: true, dataPoints: [])]
        }

        return AXChartDescriptor(
            title: DashboardStrings.accWeightChartLabel,
            summary: nil,
            xAxis: xAxis,
            yAxis: yAxis,
            additionalAxes: [],
            series: seriesDescriptors
        )
    }
}
