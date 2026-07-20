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
        /// Gap between the headline value and the date-range label (tightened from spacingXS).
        static let rangeLabelTopSpacing: CGFloat = 2
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
    @State private var cachedWeekAverageDisplay: BabyWeightDisplay?
    @State private var cachedWeightUnit: WeightUnit = .lb
    @State private var cachedPercentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
    @State private var cachedEffectiveBounds: (start: Date, end: Date)?
    @State private var cachedDateRangeLabel: String = ""
    @State private var hasCacheLoaded = false

    private var babyName: String { babyProfile.name }

    /// True only when the baby has at least one real weight reading. Drives the empty
    /// state — without entries we show neither a value nor a plotted chart.
    private var hasEntries: Bool { cachedWeekAverageDisplay != nil }

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

                Group {
                    if hasEntries {
                        snapshotChart
                            .frame(height: 240)
                    } else {
                        // No real readings — render an empty Chart (no percentile curves, no data
                        // points) rather than plotting the synthetic WHO curves. Built from the same
                        // Chart machinery as the weight/BPM empty snapshots so the plot insets (leading
                        // margin + reserved trailing y-axis column) and weekday axis line up across all
                        // three cards. MOB-1591.
                        emptySnapshotChart
                            .frame(height: 240)
                    }
                }
                .padding(.top, .spacingXS)
                .padding(.bottom, .spacingSM)
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
        hasher.combine(viewModel.activeAccount?.measurementUnits)
        if let first = summaries.first { hasher.combine(first.entryTimestamp) }
        if let last = summaries.last { hasher.combine(last.entryTimestamp) }
        return hasher.finalize()
    }

    @MainActor
    // swiftlint:disable:next function_body_length
    private func recomputeCache() async {
        let inputSummaries = summaries
        let profile = babyProfile
        let weightUnit = viewModel.activeAccount?.weightUnit ?? .lb
        let measurementUnits = viewModel.measurementUnits

        let result = await Task.detached(priority: .utility) {
            let window = DashboardSnapshotChartWindow.make(summaries: inputSummaries) { $0.weight > 0 }
            let chart = window?.chartSummaries ?? []
            let recent = window?.visibleSummaries ?? []
            let avgSource = recent.isEmpty ? chart.suffix(1).map { $0 } : recent
            let avg = chart.isEmpty ? nil : BabyDashboardChartSupport.weekAverageDisplay(from: avgSource, units: measurementUnits)

            // Always compute effective bounds — use window bounds or current week when no entries
            let effectiveBounds: (start: Date, end: Date)
            if let windowBounds = window?.bounds {
                effectiveBounds = windowBounds
            } else {
                let cal = Calendar.current
                let today = Date()
                let daysToSunday = cal.component(.weekday, from: today) - 1
                let weekStart = cal.startOfDay(for: cal.date(byAdding: .day, value: -daysToSunday, to: today) ?? today)
                let weekEnd = cal.date(byAdding: .day, value: 7, to: weekStart) ?? today
                effectiveBounds = (start: weekStart, end: weekEnd)
            }

            let birthday = BabyDashboardChartSupport.resolvedBirthday(for: profile)
            let cacheKey = BabySnapshotPercentileCache.Key(
                babyId: profile.id,
                biologicalSex: profile.biologicalSex,
                birthday: birthday.timeIntervalSinceReferenceDate,
                rangeStart: effectiveBounds.start.timeIntervalSinceReferenceDate,
                rangeEnd: effectiveBounds.end.timeIntervalSinceReferenceDate,
                weightUnit: weightUnit
            )

            // MOB-1516 audit: only build the WHO percentile band when a valid birthday + sex are present —
            // otherwise no curves (parity with the dashboard graph / Smart Baby), never a fabricated age.
            var groupedPercentiles: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
            if BabyDashboardChartSupport.canResolveGrowthPercentiles(for: profile) {
                groupedPercentiles = await BabySnapshotPercentileCache.shared.groupedPoints(for: cacheKey) {
                    let allPoints = BabyPercentileGrowthReference.percentileChartPoints(
                        biologicalSex: profile.biologicalSex,
                        birthday: birthday,
                        dateRange: effectiveBounds.start...effectiveBounds.end
                    ) { BabyDashboardChartSupport.convertDecigramsToDisplay($0, unit: weightUnit) }
                    let byLine = Dictionary(grouping: allPoints, by: \.line)
                    var thinnedByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] = [:]
                    for (line, points) in byLine {
                        thinnedByLine[line] = BabyDashboardChartSupport.thinnedPercentilePoints(points, stride: 3)
                    }
                    return thinnedByLine
                }
            }
            return (window, chart, recent, avg, weightUnit, groupedPercentiles, effectiveBounds)
        }.value

        cachedSnapshotWindow = result.0
        cachedChartSummaries = result.1
        cachedRecentWeekSummaries = result.2
        cachedWeekAverageDisplay = result.3
        cachedWeightUnit = result.4
        cachedPercentilePointsByLine = result.5
        cachedEffectiveBounds = result.6

        let eb = result.6
        let calendar = Calendar.current
        let displayEnd = calendar.date(byAdding: .day, value: -1, to: eb.end) ?? eb.end
        cachedDateRangeLabel = DashboardSnapshotLabel.weekRange(start: eb.start, displayEnd: displayEnd)
        hasCacheLoaded = true
    }

    // MARK: - Headline

    private var headlineSection: some View {
        VStack(alignment: .leading, spacing: Layout.headlineSpacing) {
            if let avg = cachedWeekAverageDisplay {
                Text(BabyDashboardStrings.babyWeightLabel(name: babyName))
                    .fontOpenSans(.subHeading1)
                    .foregroundColor(theme.textSubheading)

                babyWeightRow(display: avg)
            } else {
                // No readings — "no entries" label with a zeroed placeholder value (per design mock).
                // The chart itself stays empty (BabyEmptyGraphView); only the value is zeroed, not plotted.
                Text(DashboardStrings.noEntries)
                    .fontOpenSans(.subHeading1)
                    .foregroundColor(theme.textSubheading)

                babyWeightRow(display: BabyDashboardChartSupport.emptyWeightDisplay(for: viewModel.measurementUnits))
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
                // `horizontalSpacing: 0` makes the fixed-width label column start flush at the plot edge,
                // so the reserved trailing column is exactly `yAxisLabelWidth` — identical to the weight
                // and BPM cards. MOB-1591.
                AxisValueLabel(horizontalSpacing: 0) {
                    if let val = value.as(Double.self) {
                        Text(yAxisFormatter.formatYAxisTickLabel(val))
                            .font(.caption)
                            .foregroundColor(theme.textSubheading)
                            .frame(width: DashboardSnapshotStyle.yAxisLabelWidth, alignment: .center)
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

    /// Empty-state chart: same axes / plot border as `snapshotChart` but with no marks, so the
    /// baby empty snapshot shares the exact plot geometry of the weight/BPM empty cards (leading
    /// margin + reserved trailing y-axis column). The y-axis numbers are hidden (drawn transparent)
    /// because the fallback ticks are arbitrary and would read as real weight data. MOB-1591.
    private var emptySnapshotChart: some View {
        let yScale = calculateYAxisScale()
        let yRange = yScale.domain
        let yTickValues = yScale.ticks
        // Use the raw week bounds (no ±30min edge padding) so the weekday marks match the
        // weight/BPM empty charts exactly — the padding would surface an extra 8th weekday label.
        let xDomain: ClosedRange<Date> = cachedEffectiveBounds.map { $0.start...$0.end } ?? Date()...Date()

        return Chart {
            // Invisible anchor so the plot + axes render with no data.
            PointMark(
                x: .value("Date", xDomain.lowerBound),
                y: .value("Weight", yRange.lowerBound)
            )
            .foregroundStyle(.clear)
            .symbolSize(0)
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
        // Empty state hides the y-axis NUMBERS but keeps the reserved column via a fixed-width
        // placeholder, so this plot's trailing gap matches the weight/BPM empty cards exactly (their
        // real ticks differ in digit-count "100"/"200"/"30", which sized the columns differently).
        .chartYAxis {
            AxisMarks(position: .trailing, values: yTickValues) { value in
                // `horizontalSpacing: 0` keeps the empty-state label column flush + exactly
                // `yAxisLabelWidth` wide, matching the populated baby chart and the weight/BPM cards. MOB-1591.
                AxisValueLabel(horizontalSpacing: 0) {
                    if value.as(Double.self) != nil {
                        Text(DashboardSnapshotStyle.emptyYAxisPlaceholder)
                            .font(.caption)
                            .foregroundColor(theme.textSubheading)
                            .frame(width: DashboardSnapshotStyle.yAxisLabelWidth, alignment: .center)
                            .opacity(0)
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
        guard let bounds = cachedEffectiveBounds else { return Date()...Date() }
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
        guard let bounds = cachedEffectiveBounds else { return cachedChartSummaries }
        return cachedChartSummaries.filter { $0.date <= bounds.end }
    }

    private var rightClippedWeightPoints: [(date: Date, value: Double)] {
        let unit = cachedWeightUnit
        let points = cachedChartSummaries
            .map { (date: $0.date, value: BabyDashboardChartSupport.convertStoredWeightToDisplay(Int($0.weight), unit: unit)) }
            .sorted { $0.date < $1.date }

        guard let bounds = cachedEffectiveBounds else { return points }
        return BabyDashboardChartSupport.rightClippedPoints(points, endDate: bounds.end)
    }

    private var rightClippedPercentilePointsByLine: [BabyPercentileLine: [BabyPercentileChartPoint]] {
        guard let bounds = cachedEffectiveBounds else { return cachedPercentilePointsByLine }

        return Dictionary(uniqueKeysWithValues: BabyPercentileLine.allCases.map { line in
            let points = cachedPercentilePointsByLine[line] ?? []
            return (line, BabyDashboardChartSupport.rightClippedPercentilePoints(points, endDate: bounds.end))
        })
    }

    @ViewBuilder
    private func babyWeightRow(display: BabyWeightDisplay) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: .zero) {
            Text(display.primary)
                .fontOpenSans(.heading2)
                .fontWeight(.heavy)
                .foregroundColor(babyColor)

            Text(display.primaryUnit)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, Layout.unitSpacing)

            if let secondary = display.secondary, let secondaryUnit = display.secondaryUnit {
                Text(secondary)
                    .fontOpenSans(.heading2)
                    .fontWeight(.heavy)
                    .foregroundColor(babyColor)
                    .padding(.leading, .spacingMD)

                Text(secondaryUnit)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.leading, Layout.unitSpacing)
            }
        }
    }

    private var accessibilityLabel: String {
        if let avg = cachedWeekAverageDisplay {
            let weightText = avg.secondary != nil
                ? "\(avg.primary) \(avg.primaryUnit) \(avg.secondary ?? "") \(avg.secondaryUnit ?? "")"
                : "\(avg.primary) \(avg.primaryUnit)"
            return "\(babyName) snapshot, weight \(weightText)"
        }
        return BabyDashboardStrings.babySnapshotNoReadings
    }
}
