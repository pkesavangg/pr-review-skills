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

    // MARK: - Cached State (Performance Optimization)
    // These properties are expensive to compute on every body evaluation.
    // They are pre-computed once in .task{} and stored in @State so
    // unrelated parent state changes don't re-derive them.

    @State private var cachedSnapshotWindow: DashboardSnapshotChartWindow?
    @State private var cachedChartSummaries: [BathScaleWeightSummary] = []
    @State private var cachedRecentWeekSummaries: [BathScaleWeightSummary] = []
    @State private var cachedWeekAverageLbsOz: (lbs: String, oz: String)?
    /// Percentile points grouped by line, thinned for render efficiency.
    @State private var cachedPercentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]

    private var babyName: String { babyProfile.name }

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

    /// Stable task ID — only re-runs when summaries or profile identity changes.
    private var summariesTaskID: Int {
        var hasher = Hasher()
        hasher.combine(summaries.count)
        hasher.combine(babyProfile.id)
        if let first = summaries.first { hasher.combine(first.entryTimestamp) }
        if let last = summaries.last { hasher.combine(last.entryTimestamp) }
        return hasher.finalize()
    }

    @MainActor
    private func recomputeCache() async {
        let window = DashboardSnapshotChartWindow.make(summaries: summaries) { $0.weight > 0 }
        let chart = window?.chartSummaries ?? []
        let recent = window?.visibleSummaries ?? []
        let avg = viewModel.weekAverageLbsOz(from: recent)

        // Build downsampled percentile points grouped by line
        var groupedPercentiles: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
        if let bounds = window?.bounds {
            let allPoints = BabyPercentileGrowthReference.percentileChartPoints(
                biologicalSex: babyProfile.biologicalSex,
                birthday: BabyDashboardChartSupport.resolvedBirthday(for: babyProfile),
                dateRange: bounds.start...bounds.end,
                convertDecigramsToDisplay: viewModel.convertDecigramsToDisplay
            )
            let byLine = Dictionary(grouping: allPoints, by: \.line)
            // Thin the series: keep every 3rd point for percentile reference curves.
            // .monotone interpolation is visually identical with far fewer marks.
            for (line, points) in byLine {
                groupedPercentiles[line] = thinnedPercentilePoints(points, stride: 3)
            }
        }

        cachedSnapshotWindow = window
        cachedChartSummaries = chart
        cachedRecentWeekSummaries = recent
        cachedWeekAverageLbsOz = avg
        cachedPercentilePointsByLine = groupedPercentiles
    }

    /// Returns every `stride`-th point, always including the first and last point
    /// to preserve line continuity at the edges.
    private func thinnedPercentilePoints(
        _ points: [BabyPercentileChartPoint],
        stride strideN: Int
    ) -> [BabyPercentileChartPoint] {
        guard points.count > strideN * 2, strideN > 1 else { return points }
        var result: [BabyPercentileChartPoint] = []
        result.reserveCapacity(points.count / strideN + 2)
        for (index, point) in points.enumerated() {
            if index == 0 || index == points.count - 1 || index % strideN == 0 {
                result.append(point)
            }
        }
        return result
    }

    // MARK: - Headline

    private var headlineSection: some View {
        VStack(alignment: .leading, spacing: Layout.headlineSpacing) {
            Text(BabyDashboardStrings.babyWeightLabel(name: babyName))
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)

            if let avg = cachedWeekAverageLbsOz {
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

    // MARK: - Chart

    private var snapshotChart: some View {
        let weightPoints = rightClippedWeightPoints
        let yScale = calculateYAxisScale()
        let xDomain = weekXDomain()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks
        let groupedPercentiles = rightClippedPercentilePointsByLine

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
                    y: .value("Weight", point.value),
                    series: .value("Series", "weight")
                )
                .foregroundStyle(babyColor)
                .interpolationMethod(.monotone)
                .lineStyle(StrokeStyle(lineWidth: 2))

                PointMark(
                    x: .value("Date", point.date),
                    y: .value("Weight", point.value)
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
                .overlay {
                    SnapshotChartPlotBorderView(
                        color: theme.textSubheading.opacity(0.3),
                        yDomain: yRange,
                        yTicks: yTickValues,
                        showHorizontalGridLines: false,
                        visibleHorizontalTicks: boundaryYTicks(from: yTickValues)
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
        guard let bounds = cachedSnapshotWindow?.bounds else { return Date()...Date() }
        let edgePadding: TimeInterval = 30 * 60
        return bounds.start.addingTimeInterval(-edgePadding)...bounds.end.addingTimeInterval(edgePadding)
    }

    private func calculateYAxisScale() -> YAxisScale {
        BabyDashboardChartSupport.yAxisScale(
            for: rightClippedChartSummaries,
            babyProfile: babyProfile,
            convertStoredWeightToDisplay: viewModel.convertStoredWeightToDisplay,
            convertDecigramsToDisplay: viewModel.convertDecigramsToDisplay
        )
    }

    private var rightClippedChartSummaries: [BathScaleWeightSummary] {
        guard let bounds = cachedSnapshotWindow?.bounds else { return cachedChartSummaries }
        return cachedChartSummaries.filter { $0.date <= bounds.end }
    }

    private var rightClippedWeightPoints: [(date: Date, value: Double)] {
        guard let bounds = cachedSnapshotWindow?.bounds else {
            return cachedChartSummaries.map {
                (date: $0.date, value: viewModel.convertStoredWeightToDisplay(Int($0.weight)))
            }
        }

        let points = cachedChartSummaries
            .map { (date: $0.date, value: viewModel.convertStoredWeightToDisplay(Int($0.weight))) }
            .sorted { $0.date < $1.date }

        return rightClippedPoints(points, endDate: bounds.end)
    }

    private var rightClippedPercentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] {
        guard let bounds = cachedSnapshotWindow?.bounds else { return cachedPercentilePointsByLine }

        return Dictionary(uniqueKeysWithValues: BabyPercentileLine.allCases.map { line in
            let points = cachedPercentilePointsByLine[line] ?? []
            return (line, rightClippedPercentilePoints(points, endDate: bounds.end))
        })
    }

    private func rightClippedPercentilePoints(
        _ points: [BabyPercentileChartPoint],
        endDate: Date
    ) -> [BabyPercentileChartPoint] {
        let clipped = rightClippedPoints(
            points.map { (date: $0.date, value: $0.value) },
            endDate: endDate
        )

        guard let line = points.first?.line else { return [] }
        return clipped.map { BabyPercentileChartPoint(date: $0.date, value: $0.value, line: line) }
    }

    private func rightClippedPoints(
        _ points: [(date: Date, value: Double)],
        endDate: Date
    ) -> [(date: Date, value: Double)] {
        let sortedPoints = points.sorted { $0.date < $1.date }
        let visiblePoints = sortedPoints.filter { $0.date <= endDate }

        guard let lastVisiblePoint = visiblePoints.last else { return [] }
        guard lastVisiblePoint.date < endDate else { return visiblePoints }
        guard let nextPoint = sortedPoints.first(where: { $0.date > endDate }) else { return visiblePoints }

        let boundaryValue = interpolatedValue(
            at: endDate,
            from: lastVisiblePoint.date,
            startValue: lastVisiblePoint.value,
            to: nextPoint.date,
            endValue: nextPoint.value
        )

        return visiblePoints + [(date: endDate, value: boundaryValue)]
    }

    private func interpolatedValue(
        at targetDate: Date,
        from startDate: Date,
        startValue: Double,
        to endDate: Date,
        endValue: Double
    ) -> Double {
        let totalInterval = endDate.timeIntervalSince(startDate)
        guard totalInterval > 0 else { return startValue }

        let elapsedInterval = targetDate.timeIntervalSince(startDate)
        let progress = elapsedInterval / totalInterval
        return startValue + ((endValue - startValue) * progress)
    }

    private func boundaryYTicks(from ticks: [Double]) -> [Double] {
        guard let first = ticks.first else { return [] }
        guard let last = ticks.last,
              abs(last - first) > AppConstants.Precision.doubleEqualityEpsilon else {
            return [first]
        }
        return [first, last]
    }

    private var accessibilityLabel: String {
        if let avg = cachedWeekAverageLbsOz {
            return BabyDashboardStrings.babyWeightSnapshotAccessibility(
                name: babyName,
                lbs: avg.lbs,
                oz: avg.oz
            )
        }
        return BabyDashboardStrings.babySnapshotNoReadings
    }
}
