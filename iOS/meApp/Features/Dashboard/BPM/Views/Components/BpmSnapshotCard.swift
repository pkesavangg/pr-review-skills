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

    @State private var cachedSnapshotWindow: DashboardSnapshotChartWindow?
    @State private var cachedChartSummaries: [BathScaleWeightSummary] = []
    @State private var cachedRecentWeekSummaries: [BathScaleWeightSummary] = []
    @State private var cachedChartPoints: [BpmChartPoint] = []
    @State private var hasCacheLoaded = false

    private var latestClassification: AhaPressureClass? {
        let source = cachedRecentWeekSummaries.last ?? cachedChartSummaries.last
        guard let latest = source,
              let sys = latest.systolic,
              let dia = latest.diastolic else { return nil }
        return AhaPressureClass.classify(systolic: Int(sys), diastolic: Int(dia))
    }

    private var latestReading: BpmLatestReading? {
        let source = cachedRecentWeekSummaries.last ?? cachedChartSummaries.last
        guard let latest = source,
              let sys = latest.systolic,
              let dia = latest.diastolic else { return nil }
        return BpmLatestReading(
            systolic: Int(sys),
            diastolic: Int(dia),
            pulse: Int(latest.pulse ?? 0)
        )
    }

    var body: some View {
        Button(action: onTap) {
            content
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
        .task(id: summariesTaskID) {
            await recomputeCache()
        }
    }

    @ViewBuilder
    private var content: some View {
        if hasCacheLoaded {
            VStack(alignment: .leading, spacing: .zero) {
                headlineSection
                    .padding(.horizontal, .spacingSM)
                    .padding(.top, .spacingSM)

                if !cachedChartSummaries.isEmpty {
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
        } else {
            SnapshotSkeletonCardView(style: .bloodPressure)
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
        let inputSummaries = summaries
        let currentTheme = theme

        let result = await Task.detached(priority: .utility) {
            let window = DashboardSnapshotChartWindow.make(summaries: inputSummaries) { $0.systolic != nil }
            let chart = window?.chartSummaries ?? []
            let recent = window?.visibleSummaries ?? []

            let classification: AhaPressureClass? = {
                guard let latest = recent.last,
                      let sys = latest.systolic,
                      let dia = latest.diastolic else { return nil }
                return AhaPressureClass.classify(systolic: Int(sys), diastolic: Int(dia))
            }()

            let points = chart.flatMap { op -> [BpmChartPoint] in
                var pts: [BpmChartPoint] = []
                if let sys = op.systolic {
                    let colors = DashboardChartStyleProvider.seriesColors(
                        for: "systolic", productType: .bpm, theme: currentTheme, bpmClassification: classification
                    )
                    pts.append(BpmChartPoint(id: "sys_\(op.period)", date: op.date, value: sys, series: "systolic", color: colors.line))
                }
                if let dia = op.diastolic {
                    let colors = DashboardChartStyleProvider.seriesColors(
                        for: "diastolic", productType: .bpm, theme: currentTheme, bpmClassification: classification
                    )
                    pts.append(BpmChartPoint(id: "dia_\(op.period)", date: op.date, value: dia, series: "diastolic", color: colors.line))
                }
                if let pulse = op.pulse {
                    let colors = DashboardChartStyleProvider.seriesColors(
                        for: "pulse", productType: .bpm, theme: currentTheme, bpmClassification: classification
                    )
                    pts.append(BpmChartPoint(id: "pulse_\(op.period)", date: op.date, value: pulse, series: "pulse", color: colors.line))
                }
                return pts
            }

            return (window, chart, recent, points)
        }.value

        cachedSnapshotWindow = result.0
        cachedChartSummaries = result.1
        cachedRecentWeekSummaries = result.2
        cachedChartPoints = result.3
        hasCacheLoaded = true
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
                HStack(alignment: .top, spacing: .zero) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(BpmDashboardStrings.mmhg)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, Layout.labelBottomTightening)
                        Text(BpmDashboardStrings.noEntries)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        HStack(alignment: .lastTextBaseline, spacing: Layout.valueSpacing) {
                            Text(BpmDashboardStrings.bpSystolicZeroPlaceholder)
                                .fontOpenSans(.heading1).fontWeight(.heavy)
                                .foregroundColor(AhaPressureClass.classify(systolic: 0, diastolic: 0).color(theme: theme))
                            slashDivider
                            Text(BpmDashboardStrings.bpDiastolicZeroPlaceholder)
                                .fontOpenSans(.heading1).fontWeight(.heavy)
                                .foregroundColor(AhaPressureClass.classify(systolic: 0, diastolic: 0).color(theme: theme))
                        }
                    }
                    Spacer()
                    VStack(alignment: .leading, spacing: 0) {
                        Text(BpmDashboardStrings.pulse)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, Layout.labelBottomTightening)
                        Text(BpmDashboardStrings.bpPulseZeroPlaceholder)
                            .fontOpenSans(.heading1).fontWeight(.heavy)
                            .foregroundColor(theme.textSubheading)
                    }
                    .frame(width: Layout.pulseColumnWidth, alignment: .leading)
                }
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

    // MARK: - Chart Data Helpers

    private func weekXDomain() -> ClosedRange<Date> {
        guard let bounds = cachedSnapshotWindow?.bounds else { return Date()...Date() }
        return bounds.start...bounds.end
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
