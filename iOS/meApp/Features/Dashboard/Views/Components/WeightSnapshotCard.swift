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
        /// Gap between the headline value and the date-range label (tightened from spacingXS).
        static let rangeLabelTopSpacing: CGFloat = 2
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
    @State private var cachedDateRangeLabel: String = ""
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
            SnapshotSkeletonCardView(style: .weight)
        }
    }

    // MARK: - Cache Computation

    private var summariesTaskID: Int {
        var hasher = Hasher()
        hasher.combine(summaries.count)
        if let first = summaries.first { hasher.combine(first.entryTimestamp) }
        if let last = summaries.last { hasher.combine(last.entryTimestamp) }
        // recomputeCache() also derives the goal chip + y-axis and the unit-converted average from
        // the account, so a goal add/change or a lb↔kg unit switch must re-run the task even when the
        // entries themselves are unchanged — otherwise cachedGoalWeightDisplay stays stale. MOB-1591.
        hasher.combine(viewModel.activeAccount?.goalWeight)
        hasher.combine(viewModel.activeAccount?.weightUnit)
        return hasher.finalize()
    }

    @MainActor
    private func recomputeCache() async {
        // Capture inputs on the main actor, then compute off-thread.
        let inputSummaries = summaries
        let weightUnit = viewModel.activeAccount?.weightUnit ?? .lb
        let goalWeightStored = viewModel.activeAccount?.goalWeight

        let result = await Task.detached(priority: .utility) {
            let window = DashboardSnapshotChartWindow.make(summaries: inputSummaries) { $0.weight > 0 }
            let chart = window?.chartSummaries ?? []
            let recent = window?.visibleSummaries ?? []

            let weights = recent.map(\.weight).filter { $0 > 0 }
            let avg: String
            if !weights.isEmpty {
                let avgStored = Int((weights.reduce(0, +) / Double(weights.count)).rounded())
                let avgDisplay = Self.convertStoredWeightToDisplay(avgStored, unit: weightUnit)
                avg = String(format: "%.1f", avgDisplay)
            } else if let last = chart.last(where: { $0.weight > 0 }) {
                let display = Self.convertStoredWeightToDisplay(Int(last.weight), unit: weightUnit)
                avg = String(format: "%.1f", display)
            } else {
                avg = "--"
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

        // The snapshot is always a week view (week graph + "week average" headline), so the range
        // label always shows the week — never the dashboard's month/year period. Matches BPM/baby.
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
        VStack(alignment: .leading, spacing: Layout.headlineSpacing) {
            Text(cachedWeekAverage == "--" ? DashboardStrings.noEntries : "week average")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)

            HStack(alignment: .lastTextBaseline, spacing: Layout.unitSpacing) {
                Text(cachedWeekAverage == "--" ? "000.0" : cachedWeekAverage)
                    .fontOpenSans(.heading2)
                    .fontWeight(.heavy)
                    .foregroundColor(theme.brandWgPrimary)

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
        // Empty + no goal → hide the y-axis NUMBERS but keep the reserved column via a fixed-width
        // placeholder, so all three empty snapshot cards show the same trailing gap. A set goal keeps
        // the real axis so the goal chip has context, mirroring the main graph's `hidesYAxis`. MOB-1591.
        let hideYAxisNumbers = cachedWeekAverage == "--" && cachedGoalWeightDisplay == nil

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
                // `horizontalSpacing: 0` removes the default gap Swift Charts inserts between the plot
                // and the label box, so the fixed-width label column starts flush at the plot edge. That
                // makes the reserved column exactly `yAxisLabelWidth` (identical across all three cards)
                // AND lets the goal chip's `plotWidth + yAxisLabelWidth/2` land dead-centre on the number
                // column — parity with the main graph, which anchors its chip from the chart's trailing
                // edge instead. MOB-1591.
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
                ZStack {
                    SnapshotChartPlotBorderView(
                        color: theme.textSubheading.opacity(0.3),
                        yDomain: yRange,
                        yTicks: yTickValues
                    )
                    goalChipOverlay(yRange: yRange)
                }
            }
        }
        .padding(.horizontal, .spacingXS)
    }

    // MARK: - Goal Chip

    @ViewBuilder
    private func goalChipOverlay(yRange: ClosedRange<Double>) -> some View {
        if let goal = cachedGoalWeightDisplay {
            GeometryReader { geo in
                GoalWeightChipView(
                    label: yAxisFormatter.formatYAxisTickLabel(goal),
                    theme: theme
                )
                // Center the chip ON the trailing y-axis number column. With `horizontalSpacing: 0`
                // on the axis label (above), the fixed-width label box starts flush at the plot's right
                // edge, so `plotWidth + yAxisLabelWidth/2` is the box centre — the chip sits exactly on
                // the number, reading as an on-axis goal marker rather than straddling the plot border.
                // Parity with the main graph's `width - yAxisLabelWidth/2` placement. MOB-1591.
                .position(
                    x: geo.size.width + DashboardSnapshotStyle.yAxisLabelWidth / 2,
                    y: goalChipY(goal: goal, yRange: yRange, plotHeight: geo.size.height)
                )
            }
        }
    }

    // Offsets keep the chip clear of the top/bottom y-axis tick label when the goal
    // falls outside the visible domain. The top label is centered on plot_top (y=0),
    // so the chip center needs to sit above the label + half-chip; the bottom label
    // sits on plot_bottom and the x-axis tick labels live just below that.
    private func goalChipY(goal: Double, yRange: ClosedRange<Double>, plotHeight: CGFloat) -> CGFloat {
        let range = yRange.upperBound - yRange.lowerBound
        guard range > 0, plotHeight > 0 else { return plotHeight / 2 }
        if goal > yRange.upperBound { return -goalChipTopOffset }
        if goal < yRange.lowerBound { return plotHeight + goalChipBottomOffset }
        let ratio = (goal - yRange.lowerBound) / range
        return plotHeight * (1 - ratio)
    }

    private var goalChipTopOffset: CGFloat { 22 }
    private var goalChipBottomOffset: CGFloat { 22 }

    // MARK: - Helpers

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

    private nonisolated static func convertStoredWeightToDisplay(_ storedWeight: Int, unit: WeightUnit) -> Double {
        Self.convertStoredWeightToDisplay(Double(storedWeight), unit: unit)
    }

    private nonisolated static func convertStoredWeightToDisplay(_ storedWeight: Double, unit: WeightUnit) -> Double {
        unit == .kg
            ? ConversionTools.convertStoredToKg(storedWeight)
            : ConversionTools.convertStoredToLbs(storedWeight)
    }
}
