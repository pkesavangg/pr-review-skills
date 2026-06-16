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
        static let pulseColumnWidth: CGFloat = 120
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
    @State private var cachedDateRangeLabel: String = ""
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

                Text(cachedDateRangeLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.horizontal, .spacingSM)
                    .padding(.top, .spacingXS)

                snapshotChart
                    .frame(height: 240)
                    .padding(.top, .spacingXS)
                    .padding(.bottom, .spacingSM)
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

        let calendar = Calendar.current
        if let bounds = result.0?.bounds {
            let displayEnd = calendar.date(byAdding: .day, value: -1, to: bounds.end) ?? bounds.end
            cachedDateRangeLabel = Self.weekDateRangeLabel(start: bounds.start, displayEnd: displayEnd)
        } else {
            let today = Date()
            let daysToSunday = calendar.component(.weekday, from: today) - 1
            let start = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -daysToSunday, to: today) ?? today)
            let displayEnd = calendar.date(byAdding: .day, value: 6, to: start) ?? today
            cachedDateRangeLabel = Self.weekDateRangeLabel(start: start, displayEnd: displayEnd)
        }
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
                            .fixedSize(horizontal: true, vertical: false)
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
                            .fixedSize(horizontal: true, vertical: false)
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

    // MARK: - Chart Data Helpers

    private func weekXDomain() -> ClosedRange<Date> {
        if let bounds = cachedSnapshotWindow?.bounds {
            return bounds.start...bounds.end
        }
        let calendar = Calendar.current
        let today = Date()
        let daysToSunday = calendar.component(.weekday, from: today) - 1
        let startOfWeek = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -daysToSunday, to: today) ?? today)
        let endOfWeek = calendar.date(byAdding: .day, value: 7, to: startOfWeek) ?? today
        return startOfWeek...endOfWeek
    }

    private func calculateYAxisScale() -> YAxisScale {
        DashboardChartScaleProvider.bpmScale(from: cachedChartSummaries)
    }

    private static func weekDateRangeLabel(start: Date, displayEnd: Date) -> String {
        let calendar = Calendar.current
        let sy = calendar.component(.year, from: start)
        let ey = calendar.component(.year, from: displayEnd)
        let sm = calendar.component(.month, from: start)
        let em = calendar.component(.month, from: displayEnd)
        let startFmt = DateFormatter()
        startFmt.dateFormat = "MMM d"
        let endFmt = DateFormatter()
        endFmt.dateFormat = "MMM d, yyyy"
        if sy != ey {
            return "\(endFmt.string(from: start)) - \(endFmt.string(from: displayEnd))".lowercased()
        }
        if sm != em {
            return "\(startFmt.string(from: start)) - \(endFmt.string(from: displayEnd))".lowercased()
        }
        let endDay = calendar.component(.day, from: displayEnd)
        return "\(startFmt.string(from: start)) - \(endDay), \(sy)".lowercased()
    }

    private var accessibilityLabel: String {
        if let reading = latestReading {
            return BpmDashboardStrings.bpSnapshotAccessibility(systolic: reading.systolic, diastolic: reading.diastolic, pulse: reading.pulse)
        }
        return BpmDashboardStrings.bloodPressureSnapshotNoReadings
    }
}
