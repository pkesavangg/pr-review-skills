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
    let orderedSeriesNames: [String]
    let cachedPlottedPoints: [String: [PlottedGraphSeries]]
    let yAxisDomain: ClosedRange<Double>
    let scrollPosition: Date
    let visibleDomainLength: TimeInterval
    let visibleGridRange: ClosedRange<Date>?
    let selectedPlottedDate: Date?
    let showCrosshair: Bool
    let isScrolling: Bool
    let lineWidth: CGFloat
    let timePeriod: TimePeriod
    let productType: EntryType
    let activeMonthInterval: DateInterval?
    let bpmClassification: AhaPressureClass?
    let theme: AppColors.Palette
    let babyProfile: BabyProfile?

    let pointArea: (_ isSelected: Bool) -> CGFloat

    // MARK: - Body

    var body: some ChartContent {
        ForEach(orderedSeriesNames, id: \.self) { seriesName in
            if let seriesPoints = cachedPlottedPoints[seriesName] {
                let pointsToRender = visiblePoints(from: seriesPoints, seriesName: seriesName)
                chartContentForSeries(seriesName: seriesName, seriesPoints: pointsToRender)
            }
        }
    }

    // MARK: - Private Helpers

    private func visiblePoints(from points: [PlottedGraphSeries], seriesName: String) -> [PlottedGraphSeries] {
        // Percentile reference curves are drawn edge-to-edge across the full visible grid range
        // (not the tighter scroll window), so they take the boundary-extended path below rather
        // than `pointsToRender`. Each percentile series is already downsampled to ~150 points at
        // generation (BabyPercentileGrowthReference), so `pointsToRender`'s ≤200-point cap would
        // never fire for them — the real per-frame cost was the O(n) `.filter` + linear neighbour
        // scans, now replaced with O(log n) binary search (MOB-518 / H1).
        if BabyDashboardChartSupport.isPercentileSeries(seriesName), let visibleGridRange {
            return PercentileChartWindowing.boundaryExtendedPoints(from: points, visibleGridRange: visibleGridRange)
        }
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
        let percentileLine = BabyDashboardChartSupport.percentileLine(for: seriesName)
        let isBabyPercentileSeries = percentileLine != nil
        let resolvedLineWidth = isBabyPercentileSeries
            ? BabyDashboardChartStyle.percentileLineWidth(for: percentileLine)
            : lineWidth
        let plottedSelectedDate = showCrosshair ? selectedPlottedDate : nil
        let regularColors = resolveColors(
            forSeriesNamed: seriesName,
            isOutsideMonthInterval: false
        )
        let outsideMonthColors = resolveColors(
            forSeriesNamed: seriesName,
            isOutsideMonthInterval: true
        )

        ForEach(seriesPoints) { plottedPoint in
            let point = plottedPoint.original
            let xDate = plottedPoint.xDate

            let domainLower = yAxisDomain.lowerBound
            let domainUpper = yAxisDomain.upperBound
            let clampedValue = min(max(point.value, domainLower), domainUpper)
            let isWithinDomain = point.value >= domainLower && point.value <= domainUpper

            let isThisPointSelected = showCrosshair && (plottedSelectedDate.map { xDate == $0 } ?? false)

            let isOutsideMonth = isOutsideActiveMonth(date: point.date)
            let colors = isOutsideMonth ? outsideMonthColors : regularColors

            LineMark(
                x: .value("Date", xDate),
                y: .value(point.series, clampedValue),
                series: .value("Series", point.series)
            )
            .foregroundStyle(colors.line)
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: resolvedLineWidth))

            PointMark(
                x: .value("Date", xDate),
                y: .value(point.series, isWithinDomain ? point.value : clampedValue)
            )
            // A dot is hidden when its value is outside the Y-domain, EXCEPT while scrolling:
            // the Y-axis is frozen mid-gesture, so show the dot at its clamped (edge) position
            // rather than letting it blink out as the curve crosses the frozen boundary.
            .symbolSize(
                isBabyPercentileSeries
                    ? 0
                    : ((isWithinDomain || isScrolling) ? pointArea(isThisPointSelected) : 0)
            )
            .foregroundStyle(colors.point)
        }
    }

    private func resolveColors(
        forSeriesNamed seriesName: String,
        isOutsideMonthInterval: Bool
    ) -> (line: Color, point: Color) {
        if let percentileLine = BabyDashboardChartSupport.percentileLine(for: seriesName) {
            let color = BabyDashboardChartStyle.percentileLineColor(for: percentileLine, theme: theme)
            return (color, color)
        }
        if babyProfile != nil,
           seriesName == DashboardStrings.weight || BabyDashboardChartSupport.isHeightSeries(seriesName) {
            return (BabyDashboardChartStyle.weightColor, BabyDashboardChartStyle.weightColor)
        }
        return DashboardChartStyleProvider.seriesColors(
            for: seriesName,
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
                // High-contrast neutral (white on dark / black on light). `actionSecondary`
                // is neutral-100, which in dark mode is the chart background color — the
                // crosshair rendered invisible. This keeps the focus line visible like 5.0.3.
                .foregroundStyle(theme.actionPrimary)
                .lineStyle(StrokeStyle(lineWidth: 1))
        }
    }

    @ChartContentBuilder
    private var horizontalCrosshair: some ChartContent {
        if showCrosshair, let yValue = horizontalYValue {
            RuleMark(y: .value("SelectedY", yValue))
                .zIndex(-100)
                // Same high-contrast token as the vertical crosshair (see above).
                .foregroundStyle(theme.actionPrimary)
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

// MARK: - PercentileChartWindowing

/// Pure windowing for baby/BPM percentile reference curves, extracted from `ChartSeriesContent`
/// so it can be unit-tested for point-set parity (MOB-518 / H1).
///
/// The percentile lines are drawn edge-to-edge across the full visible grid range, so instead of
/// `pointsToRender` (which windows to the scroll range) they return the in-grid-range slice plus
/// interpolated points exactly on the grid edges. Input `points` MUST be sorted ascending by
/// `xDate` (guaranteed by `BaseGraphViewCacheSupport.makeCacheUpdate`), which lets the slice and
/// the bracketing neighbours be found with O(log n) binary search rather than O(n) scans.
enum PercentileChartWindowing {

    /// Points within `visibleGridRange`, plus a boundary-interpolated point at each grid edge when
    /// no real point sits exactly on it. Returns all `points` when none fall inside the range —
    /// identical behaviour to the previous `filter`-based implementation.
    static func boundaryExtendedPoints(
        from points: [PlottedGraphSeries],
        visibleGridRange: ClosedRange<Date>
    ) -> [PlottedGraphSeries] {
        guard let lowerIndex = SortedArrayIndex.first(in: points, where: { $0.xDate >= visibleGridRange.lowerBound }),
              let upperIndex = SortedArrayIndex.last(in: points, where: { $0.xDate <= visibleGridRange.upperBound }),
              lowerIndex <= upperIndex else {
            return points
        }

        var result = Array(points[lowerIndex...upperIndex])

        if result.first?.xDate != visibleGridRange.lowerBound,
           let leadingBoundaryPoint = interpolatedBoundaryPoint(from: points, at: visibleGridRange.lowerBound) {
            result.insert(leadingBoundaryPoint, at: 0)
        }

        if result.last?.xDate != visibleGridRange.upperBound,
           let trailingBoundaryPoint = interpolatedBoundaryPoint(from: points, at: visibleGridRange.upperBound) {
            result.append(trailingBoundaryPoint)
        }

        return result
    }

    /// Returns the exact point at `boundary`, or a linearly-interpolated point on the segment that
    /// brackets it (extrapolating from the nearest segment when `boundary` lies outside the data).
    static func interpolatedBoundaryPoint(
        from points: [PlottedGraphSeries],
        at boundary: Date
    ) -> PlottedGraphSeries? {
        if let matchIndex = SortedArrayIndex.first(in: points, where: { $0.xDate >= boundary }),
           points[matchIndex].xDate == boundary {
            return points[matchIndex]
        }

        let previousPoint = SortedArrayIndex.last(in: points, where: { $0.xDate < boundary }).map { points[$0] }
        let nextPoint = SortedArrayIndex.first(in: points, where: { $0.xDate > boundary }).map { points[$0] }

        let segment: (start: PlottedGraphSeries, end: PlottedGraphSeries)?
        switch (previousPoint, nextPoint) {
        case let (.some(previous), .some(next)):
            segment = (previous, next)
        case let (.some(lastPoint), .none):
            guard let priorPoint = points.dropLast().last else { return nil }
            segment = (priorPoint, lastPoint)
        case let (.none, .some(firstPoint)):
            guard let followingPoint = points.dropFirst().first else { return nil }
            segment = (firstPoint, followingPoint)
        case (.none, .none):
            return nil
        }

        guard let segment else { return nil }

        let interpolatedValue = BabyDashboardChartSupport.interpolatedValue(
            at: boundary,
            from: segment.start.xDate,
            startValue: segment.start.original.value,
            to: segment.end.xDate,
            endValue: segment.end.original.value
        )

        let interpolatedSeriesPoint = GraphSeries(
            date: boundary,
            value: interpolatedValue,
            series: segment.start.original.series
        )
        return PlottedGraphSeries(original: interpolatedSeriesPoint, xDate: boundary)
    }
}
