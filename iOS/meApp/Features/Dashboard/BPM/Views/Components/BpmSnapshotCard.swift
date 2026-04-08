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
    private enum Layout {
        static let valueSpacing: CGFloat = 12
        static let pulseColumnWidth: CGFloat = 72
        static let labelBottomTightening: CGFloat = -6
    }

    let summaries: [BathScaleWeightSummary]
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme
    private let yAxisFormatter = DashboardFormatter()

    // MARK: - Cached State (Performance Optimization)
    // Pre-computed in .task{} so unrelated parent state changes don't re-derive these.

    @State private var cachedSnapshotWindow: DashboardSnapshotChartWindow?
    @State private var cachedChartSummaries: [BathScaleWeightSummary] = []
    @State private var cachedRecentWeekSummaries: [BathScaleWeightSummary] = []
    @State private var cachedChartPoints: [ChartPoint] = []

    private var latestClassification: AhaPressureClass? {
        guard let latest = cachedRecentWeekSummaries.last,
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
        guard let latest = cachedRecentWeekSummaries.last,
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

                if !cachedRecentWeekSummaries.isEmpty {
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
        .task(id: summariesTaskID) {
            await recomputeCache()
        }
    }

    // MARK: - Cache Computation

    private var summariesTaskID: Int {
        var hasher = Hasher()
        hasher.combine(summaries.count)
        if let first = summaries.first { hasher.combine(first.entryTimestamp) }
        if let last = summaries.last { hasher.combine(last.entryTimestamp) }
        return hasher.finalize()
    }

    @MainActor
    private func recomputeCache() async {
        let window = DashboardSnapshotChartWindow.make(summaries: summaries) { $0.systolic != nil }
        let chart = window?.chartSummaries ?? []
        let recent = window?.visibleSummaries ?? []

        // Derive classification from the latest reading for color assignment
        let classification: AhaPressureClass? = {
            guard let latest = recent.last,
                  let sys = latest.systolic,
                  let dia = latest.diastolic else { return nil }
            return AhaPressureClass.classify(systolic: Int(sys), diastolic: Int(dia))
        }()

        let points = buildChartSeries(from: chart, classification: classification)

        cachedSnapshotWindow = window
        cachedChartSummaries = chart
        cachedRecentWeekSummaries = recent
        cachedChartPoints = points
    }

    // MARK: - Headline

    private var headlineSection: some View {
        Group {
            if let reading = latestReading, let classification = latestClassification {
                HStack(alignment: .top, spacing: .zero) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(BpmDashboardStrings.mmhg)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, Layout.labelBottomTightening)

                        HStack(alignment: .lastTextBaseline, spacing: Layout.valueSpacing) {
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
                    }

                    Spacer()

                    VStack(alignment: .leading, spacing: 0) {
                        Text(BpmDashboardStrings.pulse)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, Layout.labelBottomTightening)

                        Text("\(reading.pulse)")
                            .fontOpenSans(.heading1)
                            .fontWeight(.heavy)
                            .foregroundColor(theme.textSubheading)
                    }
                    .frame(width: Layout.pulseColumnWidth, alignment: .leading)
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
        let yScale = calculateYAxisScale()
        let xDomain = weekXDomain()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks

        return Chart(cachedChartPoints, id: \.id) { point in
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
        guard let bounds = cachedSnapshotWindow?.bounds else { return Date()...Date() }
        return bounds.start...bounds.end
    }

    private func buildChartSeries(
        from ops: [BathScaleWeightSummary],
        classification: AhaPressureClass?
    ) -> [ChartPoint] {
        ops.flatMap { op -> [ChartPoint] in
            var pts: [ChartPoint] = []
            if let sys = op.systolic {
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: "systolic",
                    productType: .bpm,
                    theme: theme,
                    bpmClassification: classification
                )
                pts.append(ChartPoint(id: "sys_\(op.period)", date: op.date, value: sys, series: "systolic", color: colors.line))
            }
            if let dia = op.diastolic {
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: "diastolic",
                    productType: .bpm,
                    theme: theme,
                    bpmClassification: classification
                )
                pts.append(ChartPoint(id: "dia_\(op.period)", date: op.date, value: dia, series: "diastolic", color: colors.line))
            }
            if let pulse = op.pulse {
                let colors = DashboardChartStyleProvider.seriesColors(
                    for: "pulse",
                    productType: .bpm,
                    theme: theme,
                    bpmClassification: classification
                )
                pts.append(ChartPoint(id: "pulse_\(op.period)", date: op.date, value: pulse, series: "pulse", color: colors.line))
            }
            return pts
        }
    }

    private func calculateYAxisScale() -> YAxisScale {
        DashboardChartScaleProvider.bpmScale(from: cachedChartSummaries)
    }

    private var accessibilityLabel: String {
        if let reading = latestReading {
            return BpmDashboardStrings.bpSnapshotAccessibility(systolic: reading.systolic, diastolic: reading.diastolic, pulse: reading.pulse)
        }
        return BpmDashboardStrings.bloodPressureSnapshotNoReadings
    }
}
