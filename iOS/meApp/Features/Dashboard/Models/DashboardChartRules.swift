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
        let weekday = calendar.component(.weekday, from: latestDate)
        let daysToSunday = weekday - calendar.firstWeekday
        guard let weekStart = calendar.date(byAdding: .day, value: -daysToSunday, to: calendar.startOfDay(for: latestDate)),
              let weekEnd = calendar.date(byAdding: .day, value: 7, to: weekStart) else { return nil }

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

enum DashboardChartScaleProvider {
    static func weightScale(
        operations: [BathScaleWeightSummary],
        goalWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double
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

        let isWeight = seriesName == DashboardStrings.weight
        let lineColor = isWeight ? theme.actionPrimary : theme.actionSecondary
        let pointColor = isWeight
            ? (isOutsideMonthInterval ? theme.actionPrimaryDisabled : theme.actionPrimary)
            : (isOutsideMonthInterval ? theme.actionSecondaryDisabled : theme.actionSecondary)
        return (lineColor, pointColor)
    }
}

struct SnapshotChartPlotBorderView: View {
    let color: Color
    let yDomain: ClosedRange<Double>
    let yTicks: [Double]
    private let leftExtension: CGFloat = 16

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height
            let range = yDomain.upperBound - yDomain.lowerBound

            if range > 0 {
                Path { path in
                    for tick in yTicks {
                        let fraction = (tick - yDomain.lowerBound) / range
                        let rawY = height * (1 - fraction)
                        let pixelY = min(max(rawY, 0), height)
                        path.move(to: CGPoint(x: -leftExtension, y: pixelY))
                        path.addLine(to: CGPoint(x: width, y: pixelY))
                    }
                }
                .stroke(color, lineWidth: 0.5)
            }

            Path { path in
                path.move(to: CGPoint(x: width, y: 0))
                path.addLine(to: CGPoint(x: width, y: height))
            }
            .stroke(color, lineWidth: 0.5)
        }
    }
}
