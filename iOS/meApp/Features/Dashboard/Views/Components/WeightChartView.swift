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
            AxisMarks(values: model.xAxisTicks) { value in
                if let date = value.as(Date.self) {
                    AxisValueLabel { Text(xLabel(date)) }
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
