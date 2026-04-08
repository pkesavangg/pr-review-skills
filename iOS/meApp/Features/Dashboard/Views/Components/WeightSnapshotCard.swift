//
//  WeightSnapshotCard.swift
//  meApp
//
//  Non-interactive weight snapshot card for the multi-device dashboard.
//  Shows week average headline and a static week graph with axes.
//

import Charts
import SwiftUI

struct WeightSnapshotCard: View {
    private enum Layout {
        static let headlineSpacing: CGFloat = 2
        static let unitSpacing: CGFloat = 4
    }

    @StateObject private var viewModel = WeightSnapshotCardViewModel()
    let summaries: [BathScaleWeightSummary]
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme
    private let yAxisFormatter = DashboardFormatter()

    // MARK: - Cached State (Performance Optimization)
    // Pre-computed in .task{} so unrelated parent state changes don't re-derive these.

    @State private var cachedSnapshotWindow: DashboardSnapshotChartWindow?
    @State private var cachedChartSummaries: [BathScaleWeightSummary] = []
    @State private var cachedRecentWeekSummaries: [BathScaleWeightSummary] = []
    @State private var cachedWeekAverage: String = "--"

    private var unitText: String {
        viewModel.unitText
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
        let window = DashboardSnapshotChartWindow.make(summaries: summaries) { $0.weight > 0 }
        let chart = window?.chartSummaries ?? []
        let recent = window?.visibleSummaries ?? []

        let weights = recent.map(\.weight).filter { $0 > 0 }
        let avg: String
        if weights.isEmpty {
            avg = "--"
        } else {
            let avgStored = Int((weights.reduce(0, +) / Double(weights.count)).rounded())
            let avgDisplay = viewModel.convertStoredWeightToDisplay(avgStored)
            avg = String(format: "%.1f", avgDisplay)
        }

        cachedSnapshotWindow = window
        cachedChartSummaries = chart
        cachedRecentWeekSummaries = recent
        cachedWeekAverage = avg
    }

    // MARK: - Headline

    private var headlineSection: some View {
        VStack(alignment: .leading, spacing: Layout.headlineSpacing) {
            Text("week average")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)

            HStack(alignment: .lastTextBaseline, spacing: Layout.unitSpacing) {
                Text(cachedWeekAverage)
                    .fontOpenSans(.heading1)
                    .fontWeight(.heavy)
                    .foregroundColor(theme.textHeading)

                Text(unitText)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
        }
    }

    // MARK: - Chart

    private var snapshotChart: some View {
        let displayWeights = cachedChartSummaries.map { ($0.date, viewModel.convertStoredWeightToDisplay(Int($0.weight))) }
        let yScale = calculateYAxisScale()
        let xDomain = weekXDomain()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks

        return Chart(displayWeights, id: \.0) { date, weight in
            LineMark(
                x: .value("Date", date),
                y: .value("Weight", weight)
            )
            .foregroundStyle(theme.actionPrimary)
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: 2))

            PointMark(
                x: .value("Date", date),
                y: .value("Weight", weight)
            )
            .foregroundStyle(theme.actionPrimary)
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

    private func weekXDomain() -> ClosedRange<Date> {
        guard let bounds = cachedSnapshotWindow?.bounds else { return Date()...Date() }
        return bounds.start...bounds.end
    }

    private func calculateYAxisScale() -> YAxisScale {
        DashboardChartScaleProvider.weightScale(
            operations: cachedChartSummaries,
            goalWeight: viewModel.goalWeightForDisplay(),
            convertStoredWeightToDisplay: viewModel.convertStoredWeightToDisplay
        )
    }

    private var accessibilityLabel: String {
        if cachedWeekAverage != "--" {
            return "Weight snapshot, week average \(cachedWeekAverage) \(unitText)"
        }
        return "Weight snapshot, no readings yet"
    }
}
