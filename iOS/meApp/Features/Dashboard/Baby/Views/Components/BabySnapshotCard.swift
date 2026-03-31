//
//  BabySnapshotCard.swift
//  meApp
//
//  Non-interactive baby snapshot card for the multi-device dashboard.
//  Shows baby weight headline (lbs + oz) and a static week graph.
//

import Charts
import SwiftUI

struct BabySnapshotCard: View {
    private enum Layout {
        static let headlineSpacing: CGFloat = 2
        static let unitSpacing: CGFloat = 4
    }

    private let babyColor = BabyDashboardChartStyle.weightColor

    @StateObject private var viewModel = BabySnapshotCardViewModel()
    let babyProfile: BabyProfile
    let summaries: [BathScaleWeightSummary]
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme
    private let yAxisFormatter = DashboardFormatter()

    private var babyName: String { babyProfile.name }

    private var snapshotWindow: DashboardSnapshotChartWindow? {
        DashboardSnapshotChartWindow.make(summaries: summaries) { $0.weight > 0 }
    }

    private var chartSummaries: [BathScaleWeightSummary] {
        snapshotWindow?.chartSummaries ?? []
    }

    private var recentWeekSummaries: [BathScaleWeightSummary] {
        snapshotWindow?.visibleSummaries ?? []
    }

    private var weekAverageLbsOz: (lbs: String, oz: String)? {
        viewModel.weekAverageLbsOz(from: recentWeekSummaries)
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
        VStack(alignment: .leading, spacing: Layout.headlineSpacing) {
            Text(BabyDashboardStrings.babyWeightLabel(name: babyName))
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)

            if let avg = weekAverageLbsOz {
                HStack(alignment: .lastTextBaseline, spacing: .zero) {
                    Text(avg.lbs)
                        .fontOpenSans(.heading1)
                        .fontWeight(.heavy)
                        .foregroundColor(babyColor)

                    Text(BabyDashboardStrings.lbs)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.leading, Layout.unitSpacing)

                    Text(avg.oz)
                        .fontOpenSans(.heading1)
                        .fontWeight(.heavy)
                        .foregroundColor(babyColor)
                        .padding(.leading, .spacingMD)

                    Text(BabyDashboardStrings.oz)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.leading, Layout.unitSpacing)
                }
            } else {
                Text("--")
                    .fontOpenSans(.heading1)
                    .fontWeight(.heavy)
                    .foregroundColor(babyColor)
            }
        }
    }

    // MARK: - Percentile Data

    private var percentilePoints: [BabyPercentileChartPoint] {
        guard let bounds = snapshotWindow?.bounds else { return [] }
        return BabyPercentileGrowthReference.percentileChartPoints(
            biologicalSex: babyProfile.biologicalSex,
            birthday: BabyDashboardChartSupport.resolvedBirthday(for: babyProfile),
            dateRange: bounds.start...bounds.end,
            convertDecigramsToDisplay: viewModel.convertDecigramsToDisplay
        )
    }

    /// Percentile points grouped by line for efficient chart rendering.
    private var percentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] {
        Dictionary(grouping: percentilePoints, by: \.line)
    }

    // MARK: - Chart

    private var snapshotChart: some View {
        let weightPoints = chartSummaries.map {
            (date: $0.date, weight: viewModel.convertStoredWeightToDisplay(Int($0.weight)))
        }
        let yScale = calculateYAxisScale()
        let xDomain = weekXDomain()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks
        let groupedPercentiles = percentilePointsByLine

        return Chart {
            // WHO percentile reference curves (rendered behind weight data)
            ForEach(BabyPercentileLine.allCases, id: \.rawValue) { line in
                if let points = groupedPercentiles[line] {
                    ForEach(points) { point in
                        LineMark(
                            x: .value("Date", point.date),
                            y: .value("Percentile", point.value),
                            series: .value("Series", point.line.rawValue)
                        )
                        .foregroundStyle(BabyDashboardChartStyle.percentileLineColor(for: point.line, theme: theme))
                        .interpolationMethod(.monotone)
                        .lineStyle(StrokeStyle(
                            lineWidth: BabyDashboardChartStyle.percentileLineWidth(for: point.line)
                        ))
                    }
                }
            }

            // Baby's actual weight measurements
            ForEach(Array(weightPoints.enumerated()), id: \.offset) { _, point in
                LineMark(
                    x: .value("Date", point.date),
                    y: .value("Weight", point.weight),
                    series: .value("Series", "weight")
                )
                .foregroundStyle(babyColor)
                .interpolationMethod(.monotone)
                .lineStyle(StrokeStyle(lineWidth: 2))

                PointMark(
                    x: .value("Date", point.date),
                    y: .value("Weight", point.weight)
                )
                .foregroundStyle(babyColor)
                .symbolSize(30)
            }
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
            plot
                .clipped()
                .overlay {
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
            Text(BabyDashboardStrings.noReadingsYet)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textSubheading)
            Spacer()
        }
        .frame(height: 200)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Helpers

    private func weekXDomain() -> ClosedRange<Date> {
        guard let bounds = snapshotWindow?.bounds else { return Date()...Date() }
        let edgePadding: TimeInterval = 30 * 60
        return bounds.start.addingTimeInterval(-edgePadding)...bounds.end.addingTimeInterval(edgePadding)
    }

    private func calculateYAxisScale() -> YAxisScale {
        BabyDashboardChartSupport.yAxisScale(
            for: chartSummaries,
            babyProfile: babyProfile,
            convertStoredWeightToDisplay: viewModel.convertStoredWeightToDisplay,
            convertDecigramsToDisplay: viewModel.convertDecigramsToDisplay
        )
    }

    private var accessibilityLabel: String {
        if let avg = weekAverageLbsOz {
            return BabyDashboardStrings.babyWeightSnapshotAccessibility(
                name: babyName,
                lbs: avg.lbs,
                oz: avg.oz
            )
        }
        return BabyDashboardStrings.babySnapshotNoReadings
    }
}
