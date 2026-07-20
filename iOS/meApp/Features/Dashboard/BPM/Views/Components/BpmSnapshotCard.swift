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
        /// Gap between the headline values and the date-range label (tightened from spacingXS).
        static let rangeLabelTopSpacing: CGFloat = 2
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

                // Always show the week range (matching the weight/baby cards). The empty state is
                // already signalled by the 00/00 sys/dia + pulse headline, so the range slot keeps
                // showing the current week rather than swapping in "no entries".
                Text(cachedDateRangeLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.horizontal, .spacingSM)
                    .padding(.top, Layout.rangeLabelTopSpacing)

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
    // swiftlint:disable:next function_body_length
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
            cachedDateRangeLabel = DashboardSnapshotLabel.weekRange(start: bounds.start, displayEnd: displayEnd)
        } else {
            let today = Date()
            let daysToSunday = calendar.component(.weekday, from: today) - 1
            let start = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -daysToSunday, to: today) ?? today)
            let displayEnd = calendar.date(byAdding: .day, value: 6, to: start) ?? today
            cachedDateRangeLabel = DashboardSnapshotLabel.weekRange(start: start, displayEnd: displayEnd)
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
                            headlineValue("\(reading.systolic)", color: classification.color(theme: theme))
                            slashDivider
                            headlineValue("\(reading.diastolic)", color: classification.color(theme: theme))
                        }
                    }

                    Spacer()

                    VStack(alignment: .leading, spacing: 0) {
                        Text(BpmDashboardStrings.pulse)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, Layout.labelBottomTightening)

                        headlineValue("\(reading.pulse)", color: theme.textSubheading)
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
                            headlineValue(BpmDashboardStrings.bpSystolicZeroPlaceholder, color: zeroPlaceholderColor)
                            slashDivider
                            headlineValue(BpmDashboardStrings.bpDiastolicZeroPlaceholder, color: zeroPlaceholderColor)
                        }
                    }
                    Spacer()
                    VStack(alignment: .leading, spacing: 0) {
                        Text(BpmDashboardStrings.pulse)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, Layout.labelBottomTightening)
                        headlineValue(BpmDashboardStrings.bpPulseZeroPlaceholder, color: theme.textSubheading)
                    }
                    .frame(width: Layout.pulseColumnWidth, alignment: .leading)
                }
            }
        }
    }

    private var slashDivider: some View {
        SlashDividerView(color: theme.textSubheading.opacity(0.45))
    }

    /// Single source of truth for a headline sys/dia/pulse value, so the populated and empty
    /// branches can never drift apart (a font change to one used to miss the other). Shrinks to
    /// fit on one line (down to 60%) instead of wrapping/collapsing when a reading is wide
    /// (e.g. "180 / 120") or the device is narrow. MOB-1591.
    private func headlineValue(_ text: String, color: Color) -> some View {
        Text(text)
            .fontOpenSans(.heading2)
            .fontWeight(.heavy)
            .foregroundColor(color)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
    }

    /// Colour for the "000 / 00" zero-reading placeholder (AHA class for a 0/0 reading).
    private var zeroPlaceholderColor: Color {
        AhaPressureClass.classify(systolic: 0, diastolic: 0).color(theme: theme)
    }

    // MARK: - Chart

    private var snapshotChart: some View {
        let yScale = calculateYAxisScale()
        let xDomain = weekXDomain()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks
        // No readings → hide the y-axis NUMBERS but keep the reserved column via a fixed-width
        // placeholder, so all three empty snapshot cards show the same trailing gap. The real numbers
        // return with the first reading. MOB-1591.
        let hideYAxisNumbers = cachedChartPoints.isEmpty

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
                // `horizontalSpacing: 0` makes the fixed-width label column start flush at the plot edge,
                // so the reserved trailing column is exactly `yAxisLabelWidth` — identical to the weight
                // and baby cards. MOB-1591.
                AxisValueLabel(horizontalSpacing: 0) {
                    if let val = value.as(Double.self) {
                        // Empty → transparent fixed-width placeholder; keeps the reserved column so the
                        // gap matches the other cards, with the arbitrary fallback numbers hidden.
                        Text(hideYAxisNumbers ? DashboardSnapshotStyle.emptyYAxisPlaceholder : yAxisFormatter.formatYAxisTickLabel(val))
                            .font(.caption)
                            .foregroundColor(theme.textSubheading)
                            .frame(width: DashboardSnapshotStyle.yAxisLabelWidth, alignment: .center)
                            .opacity(hideYAxisNumbers ? 0 : 1)
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

    private var accessibilityLabel: String {
        if let reading = latestReading {
            return BpmDashboardStrings.bpSnapshotAccessibility(systolic: reading.systolic, diastolic: reading.diastolic, pulse: reading.pulse)
        }
        return BpmDashboardStrings.bloodPressureSnapshotNoReadings
    }
}
