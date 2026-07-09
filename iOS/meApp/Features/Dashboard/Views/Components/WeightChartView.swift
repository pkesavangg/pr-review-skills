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
    let yLabel: (Double) -> String
    let xLabel: (Date) -> String
    let theme: AppColors.Palette

    private var isScrollable: Bool { model.period != .total }
    private var lineWidth: CGFloat { isScrollable ? 3 : 2 }
    private var pointArea: CGFloat {
        let diameter: CGFloat = isScrollable ? 8 : 4
        let radius = diameter / 2
        return .pi * radius * radius
    }

    /// Finite, positive-width y-domain (W2 guard) — used for the scale AND the mark clamp so points and
    /// scale always agree (the S6 fix: plot the same value in `LineMark` and `PointMark`).
    private var yDomain: ClosedRange<Double> { ChartDomainSanitizer.finiteWidth(model.yAxis.domain) }

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
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: name, productType: model.productType, theme: theme
                )
                ForEach(model.seriesPoints[name] ?? []) { plotted in
                    let value = min(max(plotted.original.value, yDomain.lowerBound), yDomain.upperBound)

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
                    .symbolSize(pointArea)
                    .foregroundStyle(colors.point)
                }
            }
        }
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
