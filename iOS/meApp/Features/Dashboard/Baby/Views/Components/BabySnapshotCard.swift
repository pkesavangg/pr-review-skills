//
//  BabySnapshotCard.swift
//  meApp
//
//  Non-interactive baby snapshot card for the multi-device dashboard.
//  Shows baby weight headline (lbs + oz) and a static week graph.
//

import Charts
import SwiftUI

private actor BabySnapshotPercentileCache {
    private static let maxEntries = 32

    struct Key: Hashable {
        let babyId: String
        let biologicalSex: String?
        let birthday: TimeInterval
        let rangeStart: TimeInterval
        let rangeEnd: TimeInterval
        let weightUnit: WeightUnit
    }

    static let shared = BabySnapshotPercentileCache()

    private var cache: [Key: [BabyPercentileLine: [BabyPercentileChartPoint]]] = [:]
    private var accessOrder: [Key] = []

    func groupedPoints(
        for key: Key,
        build: () -> [BabyPercentileLine: [BabyPercentileChartPoint]]
    ) -> [BabyPercentileLine: [BabyPercentileChartPoint]] {
        if let cached = cache[key] {
            markAccess(for: key)
            return cached
        }
        let groupedPoints = build()
        cache[key] = groupedPoints
        markAccess(for: key)
        trimIfNeeded()
        return groupedPoints
    }

    private func markAccess(for key: Key) {
        accessOrder.removeAll { $0 == key }
        accessOrder.append(key)
    }

    private func trimIfNeeded() {
        while cache.count > Self.maxEntries, let oldestKey = accessOrder.first {
            accessOrder.removeFirst()
            cache.removeValue(forKey: oldestKey)
        }
    }
}

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

    @State private var cachedSnapshotWindow: DashboardSnapshotChartWindow?
    @State private var cachedChartSummaries: [BathScaleWeightSummary] = []
    @State private var cachedRecentWeekSummaries: [BathScaleWeightSummary] = []
    @State private var cachedWeekAverageLbsOz: (lbs: String, oz: String)?
    @State private var cachedWeightUnit: WeightUnit = .lb
    @State private var cachedPercentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
    @State private var hasCacheLoaded = false

    private var babyName: String { babyProfile.name }

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
            SnapshotSkeletonCardView(style: .baby)
        }
    }

    // MARK: - Cache Computation

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
        let inputSummaries = summaries
        let profile = babyProfile
        let weightUnit = viewModel.activeAccount?.weightUnit ?? .lb

        let result = await Task.detached(priority: .utility) {
            let window = DashboardSnapshotChartWindow.make(summaries: inputSummaries) { $0.weight > 0 }
            let chart = window?.chartSummaries ?? []
            let recent = window?.visibleSummaries ?? []
            let avgSource = recent.isEmpty ? chart.suffix(1).map { $0 } : recent
            let avg = BabyDashboardChartSupport.weekAverageLbsOz(from: avgSource, unit: weightUnit)

            var groupedPercentiles: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
            if let bounds = window?.bounds {
                let birthday = BabyDashboardChartSupport.resolvedBirthday(for: profile)
                let cacheKey = BabySnapshotPercentileCache.Key(
                    babyId: profile.id,
                    biologicalSex: profile.biologicalSex,
                    birthday: birthday.timeIntervalSinceReferenceDate,
                    rangeStart: bounds.start.timeIntervalSinceReferenceDate,
                    rangeEnd: bounds.end.timeIntervalSinceReferenceDate,
                    weightUnit: weightUnit
                )

                groupedPercentiles = await BabySnapshotPercentileCache.shared.groupedPoints(for: cacheKey) {
                    let allPoints = BabyPercentileGrowthReference.percentileChartPoints(
                        biologicalSex: profile.biologicalSex,
                        birthday: birthday,
                        dateRange: bounds.start...bounds.end,
                        convertDecigramsToDisplay: { BabyDashboardChartSupport.convertDecigramsToDisplay($0, unit: weightUnit) }
                    )
                    let byLine = Dictionary(grouping: allPoints, by: \.line)
                    var thinnedByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
                    for (line, points) in byLine {
                        thinnedByLine[line] = BabyDashboardChartSupport.thinnedPercentilePoints(points, stride: 3)
                    }
                    return thinnedByLine
                }
            }
            return (window, chart, recent, avg, weightUnit, groupedPercentiles)
        }.value

        cachedSnapshotWindow = result.0
        cachedChartSummaries = result.1
        cachedRecentWeekSummaries = result.2
        cachedWeekAverageLbsOz = result.3
        cachedWeightUnit = result.4
        cachedPercentilePointsByLine = result.5
        hasCacheLoaded = true
    }

    // MARK: - Headline

    private var headlineSection: some View {
        let hasAnyData = !cachedChartSummaries.isEmpty
        return VStack(alignment: .leading, spacing: Layout.headlineSpacing) {
            Text(hasAnyData ? BabyDashboardStrings.babyWeightLabel(name: babyName) : BpmDashboardStrings.noEntries)
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
                HStack(alignment: .lastTextBaseline, spacing: .zero) {
                    Text("00")
                        .fontOpenSans(.heading1)
                        .fontWeight(.heavy)
                        .foregroundColor(babyColor)

                    Text(BabyDashboardStrings.lbs)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.leading, Layout.unitSpacing)

                    Text("0.0")
                        .fontOpenSans(.heading1)
                        .fontWeight(.heavy)
                        .foregroundColor(babyColor)
                        .padding(.leading, .spacingMD)

                    Text(BabyDashboardStrings.oz)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.leading, Layout.unitSpacing)
                }
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
                        visibleHorizontalTicks: BabyDashboardChartSupport.boundaryYTicks(from: yTickValues)
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

    // MARK: - Chart Data Helpers

    private func weekXDomain() -> ClosedRange<Date> {
        guard let bounds = cachedSnapshotWindow?.bounds else { return Date()...Date() }
        let edgePadding: TimeInterval = 30 * 60
        return bounds.start.addingTimeInterval(-edgePadding)...bounds.end.addingTimeInterval(edgePadding)
    }

    private func calculateYAxisScale() -> YAxisScale {
        BabyDashboardChartSupport.yAxisScale(
            for: rightClippedChartSummaries,
            babyProfile: babyProfile,
            convertStoredWeightToDisplay: { BabyDashboardChartSupport.convertStoredWeightToDisplay($0, unit: cachedWeightUnit) },
            convertDecigramsToDisplay: { BabyDashboardChartSupport.convertDecigramsToDisplay($0, unit: cachedWeightUnit) }
        )
    }

    private var rightClippedChartSummaries: [BathScaleWeightSummary] {
        guard let bounds = cachedSnapshotWindow?.bounds else { return cachedChartSummaries }
        return cachedChartSummaries.filter { $0.date <= bounds.end }
    }

    private var rightClippedWeightPoints: [(date: Date, value: Double)] {
        let unit = cachedWeightUnit
        let points = cachedChartSummaries
            .map { (date: $0.date, value: BabyDashboardChartSupport.convertStoredWeightToDisplay(Int($0.weight), unit: unit)) }
            .sorted { $0.date < $1.date }

        guard let bounds = cachedSnapshotWindow?.bounds else { return points }
        return BabyDashboardChartSupport.rightClippedPoints(points, endDate: bounds.end)
    }

    private var rightClippedPercentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] {
        guard let bounds = cachedSnapshotWindow?.bounds else { return cachedPercentilePointsByLine }

        return Dictionary(uniqueKeysWithValues: BabyPercentileLine.allCases.map { line in
            let points = cachedPercentilePointsByLine[line] ?? []
            return (line, BabyDashboardChartSupport.rightClippedPercentilePoints(points, endDate: bounds.end))
        })
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
