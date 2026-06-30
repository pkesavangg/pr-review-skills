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
    let selectedPeriod: TimePeriod
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
            SnapshotSkeletonCardView(style: .weight)
        }
    }

    // MARK: - Cache Computation

    private var summariesTaskID: Int {
        var hasher = Hasher()
        hasher.combine(summaries.count)
        hasher.combine(selectedPeriod.rawValue)
        if let first = summaries.first { hasher.combine(first.entryTimestamp) }
        if let last = summaries.last { hasher.combine(last.entryTimestamp) }
        return hasher.finalize()
    }

    @MainActor
    // swiftlint:disable:next function_body_length
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

        let calendar = Calendar.current
        let today = Date()
        switch selectedPeriod {
        case .week:
            if let bounds = result.0?.bounds {
                let displayEnd = calendar.date(byAdding: .day, value: -1, to: bounds.end) ?? bounds.end
                cachedDateRangeLabel = Self.weekDateRangeLabel(start: bounds.start, displayEnd: displayEnd)
            } else {
                let daysToSunday = calendar.component(.weekday, from: today) - 1
                let start = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -daysToSunday, to: today) ?? today)
                let displayEnd = calendar.date(byAdding: .day, value: 6, to: start) ?? today
                cachedDateRangeLabel = Self.weekDateRangeLabel(start: start, displayEnd: displayEnd)
            }
        case .month:
            let fmt = DateFormatter()
            fmt.dateFormat = "MMM yyyy"
            cachedDateRangeLabel = fmt.string(from: today).lowercased()
        case .year, .total:
            let fmt = DateFormatter()
            fmt.dateFormat = "yyyy"
            cachedDateRangeLabel = fmt.string(from: today)
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
                    .fontOpenSans(.heading1)
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
                .position(
                    x: geo.size.width,
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

    private nonisolated static func convertStoredWeightToDisplay(_ storedWeight: Int, unit: WeightUnit) -> Double {
        Self.convertStoredWeightToDisplay(Double(storedWeight), unit: unit)
    }

    private nonisolated static func convertStoredWeightToDisplay(_ storedWeight: Double, unit: WeightUnit) -> Double {
        unit == .kg
            ? ConversionTools.convertStoredToKg(storedWeight)
            : ConversionTools.convertStoredToLbs(storedWeight)
    }
}
