// swiftlint:disable file_length
//
//  BaseGraphChartContent.swift
//  meApp
//
//  Extracted ChartContent structs from BaseGraphView.
//  Each struct accepts only its required inputs so SwiftUI can skip
//  re-evaluation when those specific inputs are unchanged.
//

import Charts
import SwiftUI

// MARK: - ChartSeriesContent

/// Renders the data series (line + point marks) inside the parent Chart.
/// Accepts only the data and display inputs it directly needs so that
/// unrelated state changes in BaseGraphView do not force a re-render.
struct ChartSeriesContent: ChartContent {

    // MARK: - Inputs
    let cachedPlottedPoints: [String: [PlottedGraphSeries]]
    let yAxisDomain: ClosedRange<Double>
    let scrollPosition: Date
    let visibleDomainLength: TimeInterval
    let selectedPoint: BathScaleWeightSummary?
    let showCrosshair: Bool
    let isScrolling: Bool
    let lineWidth: CGFloat
    let timePeriod: TimePeriod
    let productType: EntryType
    let activeMonthInterval: DateInterval?
    let bpmClassification: AhaPressureClass?
    let theme: AppColors.Palette
    let babyProfile: BabyProfile?

    // Closures are referenced only during body evaluation, not for equality.
    // Body is re-evaluated when any `let` input above changes.
    let plotXDate: (Date) -> Date
    let pointArea: (_ isSelected: Bool) -> CGFloat

    // MARK: - Body

    var body: some ChartContent {
        ForEach(orderedSeriesNames, id: \.self) { seriesName in
            if let seriesPoints = cachedPlottedPoints[seriesName] {
                let pointsToRender = visiblePoints(from: seriesPoints)
                chartContentForSeries(seriesName: seriesName, seriesPoints: pointsToRender)
            }
        }
    }

    // MARK: - Private Helpers

    private var orderedSeriesNames: [String] {
        cachedPlottedPoints.keys.sorted { lhs, rhs in
            renderPriority(lhs) < renderPriority(rhs)
        }
    }

    private func renderPriority(_ name: String) -> Int {
        if BabyDashboardChartSupport.isPercentileSeries(name) { return 0 }
        if name == DashboardStrings.weight || BabyDashboardChartSupport.isHeightSeries(name) { return 1 }
        return 2
    }

    private func visiblePoints(from points: [PlottedGraphSeries]) -> [PlottedGraphSeries] {
        let visibleEnd = scrollPosition.addingTimeInterval(visibleDomainLength)
        return BaseGraphViewCacheSupport.pointsToRender(
            from: points,
            visibleStart: scrollPosition,
            visibleEnd: visibleEnd
        )
    }

    private func isOutsideActiveMonth(date: Date) -> Bool {
        guard !isScrolling, let interval = activeMonthInterval else { return false }
        return date < interval.start || date >= interval.end
    }

    @ChartContentBuilder
    private func chartContentForSeries(seriesName: String, seriesPoints: [PlottedGraphSeries]) -> some ChartContent {
        ForEach(seriesPoints) { plottedPoint in
            let point = plottedPoint.original
            let xDate = plottedPoint.xDate
            let percentileLine = BabyDashboardChartSupport.percentileLine(for: point.series)
            let isBabyPercentileSeries = percentileLine != nil

            let domainLower = yAxisDomain.lowerBound
            let domainUpper = yAxisDomain.upperBound
            let clampedValue = min(max(point.value, domainLower), domainUpper)
            let isWithinDomain = point.value >= domainLower && point.value <= domainUpper

            let nearestSelectedDate = selectedPoint?.date
            let plottedSelectedDate = nearestSelectedDate.map { plotXDate($0) }
            let isThisPointSelected = showCrosshair && (plottedSelectedDate.map { xDate == $0 } ?? false)

            let isOutsideMonth = isOutsideActiveMonth(date: point.date)
            let colors = resolveColors(for: point, isOutsideMonthInterval: isOutsideMonth)

            LineMark(
                x: .value("Date", xDate),
                y: .value(point.series, clampedValue),
                series: .value("Series", point.series)
            )
            .foregroundStyle(colors.line)
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(
                lineWidth: isBabyPercentileSeries
                    ? BabyDashboardChartStyle.percentileLineWidth(for: percentileLine)
                    : lineWidth
            ))

            PointMark(
                x: .value("Date", xDate),
                y: .value(point.series, isWithinDomain ? point.value : clampedValue)
            )
            .symbolSize(
                isBabyPercentileSeries
                    ? 0
                    : (isWithinDomain ? pointArea(isThisPointSelected) : 0)
            )
            .foregroundStyle(colors.point)
        }
    }

    private func resolveColors(
        for point: GraphSeries,
        isOutsideMonthInterval: Bool
    ) -> (line: Color, point: Color) {
        if let percentileLine = BabyDashboardChartSupport.percentileLine(for: point.series) {
            let color = BabyDashboardChartStyle.percentileLineColor(for: percentileLine, theme: theme)
            return (color, color)
        }
        if babyProfile != nil,
           point.series == DashboardStrings.weight || BabyDashboardChartSupport.isHeightSeries(point.series) {
            return (BabyDashboardChartStyle.weightColor, BabyDashboardChartStyle.weightColor)
        }
        return DashboardChartStyleProvider.seriesColors(
            for: point.series,
            productType: productType,
            theme: theme,
            bpmClassification: bpmClassification,
            isOutsideMonthInterval: isOutsideMonthInterval
        )
    }
}

// MARK: - CrosshairContent

/// Renders the vertical (and optional horizontal) crosshair rule marks.
/// Only re-evaluated when selection or baby-profile crosshair inputs change.
struct CrosshairContent: ChartContent {

    // MARK: - Inputs
    let selectedDate: Date?
    let showCrosshair: Bool
    let crosshairDate: Date?         // resolved crosshair date (baby snapping)
    let horizontalYValue: Double?    // non-nil for baby charts only
    let timePeriod: TimePeriod
    let selectedBabyPercentile: Int?
    let theme: AppColors.Palette

    let plotXDate: (Date) -> Date

    // MARK: - Body

    var body: some ChartContent {
        verticalCrosshair
        horizontalCrosshair
    }

    @ChartContentBuilder
    private var verticalCrosshair: some ChartContent {
        if let date = selectedDate, showCrosshair {
            let snapped = crosshairDate ?? date
            let xDate = plotXDate(snapped)
            RuleMark(x: .value("Date", xDate))
                .zIndex(-100)
                .foregroundStyle(theme.actionSecondary)
                .lineStyle(StrokeStyle(lineWidth: 1))
        }
    }

    @ChartContentBuilder
    private var horizontalCrosshair: some ChartContent {
        if showCrosshair, let yValue = horizontalYValue {
            RuleMark(y: .value("SelectedY", yValue))
                .zIndex(-100)
                .foregroundStyle(theme.actionSecondary)
                .lineStyle(StrokeStyle(lineWidth: 1))
                .annotation(position: .top, alignment: .leading, spacing: 6) {
                    if timePeriod == .total, let percentile = selectedBabyPercentile {
                        Text("\(percentile)%")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.leading, 8)
                    }
                }
        }
    }
}

// MARK: - BpmReferenceLines

/// Renders the systolic/diastolic reference dashed lines for BPM charts.
/// Only re-evaluated when productType or theme changes.
struct BpmReferenceLines: ChartContent {

    // MARK: - Inputs
    let productType: EntryType
    let theme: AppColors.Palette

    // MARK: - Body

    var body: some ChartContent {
        if productType == .bpm {
            RuleMark(y: .value("SysRef", Double(BpmConstants.normalSystolic)))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                .foregroundStyle(theme.textSubheading.opacity(0.4))
            RuleMark(y: .value("DiaRef", Double(BpmConstants.normalDiastolic)))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                .foregroundStyle(theme.textSubheading.opacity(0.4))
        }
    }
}
