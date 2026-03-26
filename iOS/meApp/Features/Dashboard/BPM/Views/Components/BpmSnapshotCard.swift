//
//  BpmSnapshotCard.swift
//  meApp
//
//  Non-interactive BP snapshot card for the multi-device dashboard.
//  Shows headline sys/dia + pulse values and a static 3-line week graph.
//

import Charts
import SwiftUI

struct BpmSnapshotCard: View {
    let summaries: [BathScaleWeightSummary]
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme
    private let yAxisFormatter = DashboardFormatter()

    private var snapshotWindow: DashboardSnapshotChartWindow? {
        DashboardSnapshotChartWindow.make(summaries: summaries) { $0.systolic != nil }
    }

    private var chartSummaries: [BathScaleWeightSummary] {
        snapshotWindow?.chartSummaries ?? []
    }

    private var recentWeekSummaries: [BathScaleWeightSummary] {
        snapshotWindow?.visibleSummaries ?? []
    }

    private var latestClassification: AhaPressureClass? {
        guard let latest = recentWeekSummaries.last,
              let sys = latest.systolic,
              let dia = latest.diastolic else { return nil }
        return AhaPressureClass.classify(systolic: Int(sys), diastolic: Int(dia))
    }

    private struct LatestReading {
        let systolic: Int
        let diastolic: Int
        let pulse: Int
    }

    private var latestReading: LatestReading? {
        guard let latest = recentWeekSummaries.last,
              let sys = latest.systolic,
              let dia = latest.diastolic else { return nil }
        return LatestReading(
            systolic: Int(sys),
            diastolic: Int(dia),
            pulse: Int(latest.pulse ?? 0)
        )
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: .zero) {
                headlineSection
                    .padding(.horizontal, .spacingSM)
                    .padding(.top, .spacingSM)

                if !recentWeekSummaries.isEmpty {
                    snapshotChart
                        .frame(height: 240)
                        .padding(.top, .spacingXS)
                        .padding(.bottom, .spacingSM)
                } else {
                    emptyState
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusSM)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }

    // MARK: - Headline

    private var headlineSection: some View {
        Group {
            if let reading = latestReading, let classification = latestClassification {
                VStack(alignment: .leading, spacing: .zero) {
                    HStack {
                        Text(BpmDashboardStrings.mmhg)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        Spacer()
                        Text(BpmDashboardStrings.pulse)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                    }

                    HStack(alignment: .lastTextBaseline, spacing: .zero) {
                        HStack(alignment: .lastTextBaseline, spacing: 4) {
                            Text("\(reading.systolic)")
                                .fontOpenSans(.heading1)
                                .fontWeight(.heavy)
                                .foregroundColor(classification.color(theme: theme))

                            slashDivider

                            Text("\(reading.diastolic)")
                                .fontOpenSans(.heading1)
                                .fontWeight(.heavy)
                                .foregroundColor(classification.color(theme: theme))
                        }

                        Spacer()

                        Text("\(reading.pulse)")
                            .fontOpenSans(.heading1)
                            .fontWeight(.heavy)
                            .foregroundColor(theme.textSubheading)
                    }
                }
            } else {
                Text(BpmDashboardStrings.noReadingsYet)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textSubheading)
                    .frame(height: 80)
            }
        }
    }

    private var slashDivider: some View {
        SlashDividerView(color: theme.textSubheading.opacity(0.45))
    }

    // MARK: - Chart

    private var snapshotChart: some View {
        let points = buildChartSeries()
        let yScale = calculateYAxisScale()
        let xDomain = weekXDomain()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks

        return Chart(points, id: \.id) { point in
            LineMark(
                x: .value("Date", point.date),
                y: .value("Value", point.value),
                series: .value("Series", point.series)
            )
            .foregroundStyle(point.color)
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: 2))

            PointMark(
                x: .value("Date", point.date),
                y: .value("Value", point.value)
            )
            .foregroundStyle(point.color)
            .symbolSize(30)
        }
        .chartYScale(domain: yRange)
        .chartXScale(domain: xDomain)
        .chartXAxis {
            AxisMarks(values: .stride(by: .day)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(theme.textSubheading.opacity(0.3))
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(date.formatted(.dateTime.weekday(.abbreviated)).lowercased())
                            .font(.caption)
                            .foregroundColor(theme.textSubheading)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: yTickValues) { value in
                AxisValueLabel {
                    if let val = value.as(Double.self) {
                        Text(yAxisFormatter.formatYAxisTickLabel(val))
                            .font(.caption)
                            .foregroundColor(theme.textSubheading)
                    }
                }
            }
        }
        .chartLegend(.hidden)
        .chartPlotStyle { plot in
            plot.overlay {
                SnapshotChartPlotBorderView(
                    color: theme.textSubheading.opacity(0.3),
                    yDomain: yRange,
                    yTicks: yTickValues
                )
            }
        }
        .padding(.horizontal, .spacingXS)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: .spacingXS) {
            Spacer()
            Text(BpmDashboardStrings.noReadingsYet)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textSubheading)
            Spacer()
        }
        .frame(height: 200)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Helpers

    private struct ChartPoint: Identifiable {
        let id: String
        let date: Date
        let value: Double
        let series: String
        let color: Color
    }

    private func weekXDomain() -> ClosedRange<Date> {
        guard let bounds = snapshotWindow?.bounds else { return Date()...Date() }
        return bounds.start...bounds.end
    }

    private func buildChartSeries() -> [ChartPoint] {
        chartSummaries.flatMap { op -> [ChartPoint] in
            var pts: [ChartPoint] = []
            if let sys = op.systolic {
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: "systolic",
                    productType: .bpm,
                    theme: theme,
                    bpmClassification: latestClassification
                )
                pts.append(ChartPoint(id: "sys_\(op.period)", date: op.date, value: sys, series: "systolic", color: colors.line))
            }
            if let dia = op.diastolic {
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: "diastolic",
                    productType: .bpm,
                    theme: theme,
                    bpmClassification: latestClassification
                )
                pts.append(ChartPoint(id: "dia_\(op.period)", date: op.date, value: dia, series: "diastolic", color: colors.line))
            }
            if let pulse = op.pulse {
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: "pulse",
                    productType: .bpm,
                    theme: theme,
                    bpmClassification: latestClassification
                )
                pts.append(ChartPoint(id: "pulse_\(op.period)", date: op.date, value: pulse, series: "pulse", color: colors.line))
            }
            return pts
        }
    }

    private func calculateYAxisScale() -> YAxisScale {
        DashboardChartScaleProvider.bpmScale(from: chartSummaries)
    }

    private var accessibilityLabel: String {
        if let reading = latestReading {
            return BpmDashboardStrings.bpSnapshotAccessibility(systolic: reading.systolic, diastolic: reading.diastolic, pulse: reading.pulse)
        }
        return BpmDashboardStrings.bloodPressureSnapshotNoReadings
    }
}
