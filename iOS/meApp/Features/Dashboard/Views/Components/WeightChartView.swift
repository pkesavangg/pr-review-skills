//
//  WeightChartView.swift
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

import Charts
import SwiftUI

struct WeightChartView: View {

    let model: ChartModel
    @Binding var scrollX: Date
    /// V4 (6a): raw x-value from `.chartXSelection`; the host snaps it to a real entry and drives `crosshairDate`.
    @Binding var selectedX: Date?
    /// V4 (6a): plotted x-date of the currently-selected (snapped) point — draws the crosshair + enlarges the
    /// matching point. `nil` when nothing is selected / crosshair hidden (e.g. during a scroll).
    let crosshairDate: Date?
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

    private var isScrollable: Bool { model.period != .total }
    private var lineWidth: CGFloat { isScrollable ? 3 : 2 }
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

    /// Gridline ticks — drop the trailing phantom tick the generators append (matches `gridTicks`).
    private var gridTicks: [Date] {
        model.xAxisTicks.isEmpty ? [] : Array(model.xAxisTicks.dropLast())
    }

    /// Label ticks — week/month/year drop the phantom so labels stop at the last real unit; total keeps it
    /// (matches the legacy `adjustedLabelTicks`).
    private var labelTicks: [Date] {
        guard !model.xAxisTicks.isEmpty else { return [] }
        return model.period == .total ? model.xAxisTicks : Array(model.xAxisTicks.dropLast())
    }

    /// V4 (6f): a point's entry date falls outside the active month (month view only, not while scrolling)
    /// — mirrors the legacy `isOutsideActiveMonth`.
    private func isOutsideActiveMonth(_ date: Date) -> Bool {
        guard model.period == .month, !isScrolling, let interval = activeMonthInterval else { return false }
        return date < interval.start || date >= interval.end
    }

    /// A "major" boundary that gets a solid vertical rule — start of week / 1st of month / Jan 1st
    /// (mirrors `BaseSectionViewModel.shouldShowSolidLine`, incl. its use of `Calendar.current`).
    private func isPeriodBoundary(_ date: Date) -> Bool {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.weekday, .day, .month], from: date)
        switch model.period {
        case .week:  return components.weekday == calendar.firstWeekday
        case .month: return components.day == 1
        case .year:  return components.month == 1 && components.day == 1
        case .total: return false
        }
    }

    var body: some View {
        Chart {
            ForEach(model.orderedSeriesNames, id: \.self) { name in
                let regularColors = DashboardChartStyleProvider.seriesColors(
                    for: name, productType: model.productType, theme: theme, isOutsideMonthInterval: false
                )
                let outsideColors = DashboardChartStyleProvider.seriesColors(
                    for: name, productType: model.productType, theme: theme, isOutsideMonthInterval: true
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
                    .lineStyle(StrokeStyle(lineWidth: lineWidth))

                    PointMark(
                        x: .value("Date", plotted.xDate),
                        y: .value(name, value)
                    )
                    .symbolSize(pointArea(selected: isSelected))
                    .foregroundStyle(colors.point)
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

            // V4 (6c) — goal chip: a trailing-edge pill at the goal's y-level (no visible rule, matching the
            // legacy floating chip). Clamped into the y-domain so it stays on-screen when the goal is far off.
            if let goalLabel, let goalValue = clampedGoalValue {
                RuleMark(y: .value("Goal", goalValue))
                    .foregroundStyle(.clear)
                    .annotation(position: .trailing, alignment: .center, spacing: 0) {
                        GoalWeightChipView(label: goalLabel, theme: theme)
                    }
            }
        }
        .chartXSelection(value: $selectedX)
        .chartYScale(domain: yDomain)
        .chartXScale(domain: ChartDomainSanitizer.orderedDates(model.xDomain))
        .chartYAxis {
            AxisMarks(values: model.yAxis.ticks) { value in
                AxisGridLine()
                if let doubleValue = value.as(Double.self) {
                    AxisValueLabel { Text(yLabel(doubleValue)) }
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
        .chartLegend(.hidden)
        .chartScrollableAxes(isScrollable ? .horizontal : [])
        .chartXVisibleDomain(length: ChartDomainSanitizer.positiveLength(visibleLength))
        .chartScrollPosition(x: $scrollX)
        // The model is only rebuilt at scroll-END, so the y-domain changes once per settle → this is the
        // single, smooth, adaptive settle (Y-B). No animation fires during a drag (nothing changes then).
        .animation(.easeInOut(duration: 0.25), value: yDomain)
        .frame(height: 265)
    }
}
