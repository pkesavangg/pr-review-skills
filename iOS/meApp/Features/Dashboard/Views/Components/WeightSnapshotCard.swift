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
    @State private var cachedWeightUnit: WeightUnit = .lb
    @State private var cachedGoalWeightDisplay: Double?
    @State private var hasCacheLoaded = false

    private var unitText: String {
        cachedWeightUnit.rawValue
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
        } else {
            SnapshotSkeletonCardView(style: .weight)
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
        // Capture inputs on the main actor, then compute off-thread.
        let inputSummaries = summaries
        let weightUnit = viewModel.activeAccount?.weightSettings?.weightUnit ?? .lb
        let goalWeightStored = viewModel.activeAccount?.goalSettings?.goalWeight

        let result = await Task.detached(priority: .utility) {
            let window = DashboardSnapshotChartWindow.make(summaries: inputSummaries) { $0.weight > 0 }
            let chart = window?.chartSummaries ?? []
            let recent = window?.visibleSummaries ?? []

            let weights = recent.map(\.weight).filter { $0 > 0 }
            let avg: String
            if weights.isEmpty {
                avg = "--"
            } else {
                let avgStored = Int((weights.reduce(0, +) / Double(weights.count)).rounded())
                let avgDisplay = Self.convertStoredWeightToDisplay(avgStored, unit: weightUnit)
                avg = String(format: "%.1f", avgDisplay)
            }
            let goalWeightDisplay = goalWeightStored.map { Self.convertStoredWeightToDisplay(Int($0), unit: weightUnit) }
            return (window, chart, recent, avg, weightUnit, goalWeightDisplay)
        }.value

        cachedSnapshotWindow = result.0
        cachedChartSummaries = result.1
        cachedRecentWeekSummaries = result.2
        cachedWeekAverage = result.3
        cachedWeightUnit = result.4
        cachedGoalWeightDisplay = result.5
        hasCacheLoaded = true
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
        let displayWeights = cachedChartSummaries.map { ($0.date, convertStoredWeightToDisplay(Int($0.weight))) }
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
            goalWeight: cachedGoalWeightDisplay,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )
    }

    private var accessibilityLabel: String {
        if cachedWeekAverage != "--" {
            return "Weight snapshot, week average \(cachedWeekAverage) \(unitText)"
        }
        return "Weight snapshot, no readings yet"
    }

    private func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        Self.convertStoredWeightToDisplay(storedWeight, unit: cachedWeightUnit)
    }

    private func convertStoredWeightToDisplay(_ storedWeight: Double) -> Double {
        Self.convertStoredWeightToDisplay(storedWeight, unit: cachedWeightUnit)
    }

    private static func convertStoredWeightToDisplay(_ storedWeight: Int, unit: WeightUnit) -> Double {
        Self.convertStoredWeightToDisplay(Double(storedWeight), unit: unit)
    }

    private static func convertStoredWeightToDisplay(_ storedWeight: Double, unit: WeightUnit) -> Double {
        unit == .kg
            ? ConversionTools.convertStoredToKg(storedWeight)
            : ConversionTools.convertStoredToLbs(storedWeight)
    }
}
