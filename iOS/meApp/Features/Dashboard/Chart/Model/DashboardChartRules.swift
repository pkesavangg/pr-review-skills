import SwiftUI

struct DashboardSnapshotChartWindow {
    let bounds: (start: Date, end: Date)
    let visibleSummaries: [BathScaleWeightSummary]
    let chartSummaries: [BathScaleWeightSummary]

    static func make(
        summaries: [BathScaleWeightSummary],
        include: (BathScaleWeightSummary) -> Bool,
        calendar: Calendar = .current
    ) -> DashboardSnapshotChartWindow? {
        let filtered = summaries
            .filter(include)
            .sorted { $0.date < $1.date }

        guard let latestDate = filtered.map(\.date).max() else { return nil }
        // Right-align: 7-day window ending on the day after the latest entry so the
        // latest point sits in the rightmost day slot. A calendar sun–sat window
        // leaves large empty stretches when the week has few entries (e.g. only Monday).
        let latestDayStart = calendar.startOfDay(for: latestDate)
        guard let weekEnd = calendar.date(byAdding: .day, value: 1, to: latestDayStart),
              let weekStart = calendar.date(byAdding: .day, value: -6, to: latestDayStart) else { return nil }

        let visible = filtered.filter { $0.date >= weekStart && $0.date < weekEnd }
        let previous = filtered.last { $0.date < weekStart }
        let next = filtered.first { $0.date >= weekEnd }

        var combined: [BathScaleWeightSummary] = []
        if let previous { combined.append(previous) }
        combined.append(contentsOf: visible)
        if let next, combined.last?.entryTimestamp != next.entryTimestamp { combined.append(next) }

        return DashboardSnapshotChartWindow(
            bounds: (start: weekStart, end: weekEnd),
            visibleSummaries: visible,
            chartSummaries: combined
        )
    }
}

/// Shared date-range label for the snapshot cards (weight / BPM / baby). Single-sourced here so the
/// three cards can't drift in format — they previously each carried a private copy.
enum DashboardSnapshotLabel {
    /// Formats a week window's date range, e.g. "jul 19 - jul 25, 2026" (same month) or
    /// "jun 28 - jul 4, 2026" (month crossing). The end date always repeats the month —
    /// never the collapsed "jul 19 - 25, 2026" form. `displayEnd` is the last *visible* day
    /// (inclusive), i.e. the exclusive window end minus one day.
    static func weekRange(start: Date, displayEnd: Date, calendar: Calendar = .current) -> String {
        let startYear = calendar.component(.year, from: start)
        let endYear = calendar.component(.year, from: displayEnd)
        let startFmt = DateFormatter()
        startFmt.dateFormat = "MMM d"
        let endFmt = DateFormatter()
        endFmt.dateFormat = "MMM d, yyyy"
        // Cross-year → year on both ends. Otherwise → "MMM d - MMM d, yyyy" (month always repeated).
        if startYear != endYear {
            return "\(endFmt.string(from: start)) - \(endFmt.string(from: displayEnd))".lowercased()
        }
        return "\(startFmt.string(from: start)) - \(endFmt.string(from: displayEnd))".lowercased()
    }
}

/// Rendering constants shared by the snapshot cards (weight / BPM / baby).
enum DashboardSnapshotStyle {
    /// Transparent y-axis placeholder rendered in the empty state. The three cards' real fallback ticks
    /// differ in digit count (weight "100" / BPM "200" / baby "30"), which sized their reserved trailing
    /// y-axis columns differently. Rendering this fixed 3-digit placeholder (hidden via opacity, so the
    /// column width is still reserved) makes the empty-state gap identical across all three cards.
    static let emptyYAxisPlaceholder = "000"

    /// Fixed width reserved for the trailing y-axis tick-label column. The label `Text` is boxed to this
    /// width so the plot area is the EXACT same width whether the numbers are shown or hidden, and
    /// identical across the weight / BPM / baby cards. Without a fixed box Swift Charts sizes the column
    /// to its widest label — and the cards' ticks differ in digit count ("104" / "200" / "30") and even
    /// glyph width (proportional digits) — which drifted each card's plot width and each card's
    /// empty-vs-populated width. Mirrors the main graph's `yAxisLabelWidth` box. MOB-1591.
    static let yAxisLabelWidth: CGFloat = 40
}

enum DashboardChartScaleProvider {
    static func weightScale(
        operations: [BathScaleWeightSummary],
        goalWeight: Double?,
        convertStoredWeightToDisplay: (Double) -> Double
    ) -> YAxisScale {
        YAxisCalculator.calculateYAxis(
            operations: operations,
            goalWeight: goalWeight,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )
    }

    static func babyWeightScale(
        operations: [BathScaleWeightSummary],
        convertStoredWeightToDisplay: (Int) -> Double
    ) -> YAxisScale {
        let displayWeights = operations.map { convertStoredWeightToDisplay(Int($0.weight)) }.filter { $0 > 0 }
        guard let minVal = displayWeights.min(), let maxVal = displayWeights.max() else {
            return YAxisScale(min: 0, max: 30, step: 10, ticks: [0, 10, 20, 30], domain: 0...30, average: 15)
        }
        let padding = max((maxVal - minVal) * 0.15, 1.0)
        let paddedMin = max(0, floor(minVal - padding))
        let paddedMax = ceil(maxVal + padding)
        let range = paddedMax - paddedMin
        let rawStep = range / 4.0
        let step = max(1, ceil(rawStep))
        let niceMin = floor(paddedMin / step) * step
        let niceMax = ceil(paddedMax / step) * step
        var ticks: [Double] = []
        var tick = niceMin
        while tick <= niceMax {
            ticks.append(tick)
            tick += step
        }
        let avg = displayWeights.reduce(0, +) / Double(displayWeights.count)
        return YAxisScale(min: niceMin, max: niceMax, step: step, ticks: ticks, domain: niceMin...niceMax, average: avg)
    }

    static func bpmScale(from operations: [BathScaleWeightSummary]) -> YAxisScale {
        let allValues = operations.flatMap { op -> [Double] in
            [op.systolic, op.diastolic, op.pulse].compactMap { $0 }
        }
        guard let minVal = allValues.min(), let maxVal = allValues.max() else {
            let defaultTicks = stride(from: BpmConstants.defaultYMin, through: BpmConstants.defaultYMax, by: 40).map { $0 }
            return YAxisScale(
                min: BpmConstants.defaultYMin,
                max: BpmConstants.defaultYMax,
                step: 40,
                ticks: defaultTicks,
                domain: BpmConstants.defaultYMin...BpmConstants.defaultYMax,
                average: 120
            )
        }

        let paddedMin = max(0, floor(minVal - BpmConstants.yAxisPadding))
        let paddedMax = ceil(maxVal + BpmConstants.yAxisPadding)
        let range = paddedMax - paddedMin
        let rawStep = range / 4.0
        let step = max(10, ceil(rawStep / 10) * 10)
        let niceMin = floor(paddedMin / step) * step
        let niceMax = ceil(paddedMax / step) * step
        var ticks: [Double] = []
        var tick = niceMin
        while tick <= niceMax {
            ticks.append(tick)
            tick += step
        }
        let avg = allValues.reduce(0, +) / Double(allValues.count)
        return YAxisScale(min: niceMin, max: niceMax, step: step, ticks: ticks, domain: niceMin...niceMax, average: avg)
    }
}

enum DashboardChartStyleProvider {
    static func seriesColors(
        for seriesName: String,
        productType: EntryType,
        theme: AppColors.Palette,
        bpmClassification: AhaPressureClass? = nil,
        isOutsideMonthInterval: Bool = false
    ) -> (line: Color, point: Color) {
        if productType == .bpm {
            if seriesName == "pulse" {
                return (theme.textSubheading, theme.textSubheading)
            }
            let ahaColor = (bpmClassification ?? .normal).color(theme: theme)
            return (ahaColor, isOutsideMonthInterval ? ahaColor.opacity(0.4) : ahaColor)
        }

        // MOB-1516: baby — the 7 WHO/CDC percentile curves share one neutral colour; the real data series
        // (weight or height) is baby purple. Parity with the legacy `BabyDashboardChartStyle`
        // (percentileLineColor → statusUtilityPrimary; weightColor → babyScale). (v2 path only — the legacy
        // baby renderer colours via `BaseGraphChartContent.resolveColors`, unaffected.)
        if productType == .baby {
            if BabyDashboardChartSupport.isPercentileSeries(seriesName) {
                return (theme.statusUtilityPrimary, theme.statusUtilityPrimary)
            }
            return (theme.babyScaleColor, theme.babyScaleColor)
        }

        // Weight line uses the weight-scale brand blue; the co-plotted selected-metric line uses
        // the high-contrast neutral (white on dark / black on light). `actionSecondary` is
        // `neutral-100`, which in dark mode is the chart background color — so the metric line
        // rendered invisible ("not shown"). These tokens keep both lines visible and distinct in
        // either appearance.
        let isWeight = seriesName == DashboardStrings.weight
        let lineColor = isWeight ? theme.weightScaleColor : theme.actionPrimary
        let pointColor = isWeight
            ? (isOutsideMonthInterval ? theme.weightScaleColor.opacity(0.4) : theme.weightScaleColor)
            : (isOutsideMonthInterval ? theme.actionPrimaryDisabled : theme.actionPrimary)
        return (lineColor, pointColor)
    }
}

struct SnapshotChartPlotBorderView: View {
    let color: Color
    let yDomain: ClosedRange<Double>
    let yTicks: [Double]
    var showHorizontalGridLines: Bool = true
    var visibleHorizontalTicks: [Double]?
    var showTrailingBorder: Bool = true
    private let leftExtension: CGFloat = 16

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height
            let range = yDomain.upperBound - yDomain.lowerBound
            let ticksToDraw = visibleHorizontalTicks ?? (showHorizontalGridLines ? yTicks : [])

            if !ticksToDraw.isEmpty, range > 0 {
                Path { path in
                    for tick in ticksToDraw {
                        let fraction = (tick - yDomain.lowerBound) / range
                        let rawY = height * (1 - fraction)
                        let pixelY = min(max(rawY, 0), height)
                        path.move(to: CGPoint(x: -leftExtension, y: pixelY))
                        path.addLine(to: CGPoint(x: width, y: pixelY))
                    }
                }
                .stroke(color, lineWidth: 0.5)
            }

            if showTrailingBorder {
                Path { path in
                    path.move(to: CGPoint(x: width, y: 0))
                    path.addLine(to: CGPoint(x: width, y: height))
                }
                .stroke(color, lineWidth: 0.5)
            }
        }
    }
}
