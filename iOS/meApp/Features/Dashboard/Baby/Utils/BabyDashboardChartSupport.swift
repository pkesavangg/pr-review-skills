//
//  BabyDashboardChartSupport.swift
//  meApp
//
//  Shared baby-dashboard chart helpers used by both snapshot cards and the
//  full dashboard trend graph.
//

import Foundation

/// Multi-unit baby weight display value. Primary is always populated; secondary (oz) is non-nil only for the lbs+oz format.
struct BabyWeightDisplay {
    let primary: String        // "7" / "7.7" / "3.520"
    let primaryUnit: String    // "lbs" / "lb" / "kg"
    let secondary: String?     // "12.0" for lbs+oz, nil otherwise
    let secondaryUnit: String? // "oz" for lbs+oz, nil otherwise
}

enum BabyDashboardChartSupport {
    private static let percentileSeriesPrefix = "baby_percentile_"
    static let heightSeriesName = "baby_height"   // MOB-1516: internal so ChartPrep/TrendChartHost can name it
    private static let defaultBabyAgeDays = 84
    private static let heightTickStep = 5.0
    private static let minimumHeightAxisMax = 25.0

    static func resolvedBirthday(
        for babyProfile: BabyProfile,
        calendar: Calendar = .current,
        today: Date = Date()
    ) -> Date {
        let normalizedToday = calendar.startOfDay(for: today)
        let fallbackBirthday = calendar.date(byAdding: .day, value: -defaultBabyAgeDays, to: normalizedToday)
            ?? normalizedToday
        return calendar.startOfDay(for: min(babyProfile.birthday ?? fallbackBirthday, normalizedToday))
    }

    /// MOB-1516 audit: whether valid WHO percentile output can be produced for this profile. Requires BOTH a
    /// known `birthday` (age drives every table lookup) AND a non-withheld sex. Smart Baby (babyApp) yields no
    /// curves / no % when the birthdate is missing (its day-of-life math NaNs out); we make that explicit here
    /// rather than let `resolvedBirthday`'s fallback fabricate an age and show a plausible-but-wrong percentile.
    static func canResolveGrowthPercentiles(for babyProfile: BabyProfile) -> Bool {
        babyProfile.birthday != nil && !BabyPercentileGrowthReference.isSexWithheld(babyProfile.biologicalSex)
    }

    /// mm → display inches for the length reference curves (the dashboard plots height in inches).
    private static func millimetersToInches(_ millimeters: Int) -> Double {
        Double(millimeters) / BabyPercentileGrowthReference.millimetersPerInch
    }

    static func percentileSeries(
        for babyProfile: BabyProfile,
        operations: [BathScaleWeightSummary],
        convertDecigramsToDisplay: (Int) -> Double,
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        guard let lowerBound = operations.map(\.date).min(),
              let upperBound = operations.map(\.date).max()
        else { return [] }
        return percentileSeries(
            for: babyProfile,
            dateRange: lowerBound...upperBound,
            convertDecigramsToDisplay: convertDecigramsToDisplay,
            calendar: calendar
        )
    }

    /// Weight percentile reference curves spanning an explicit date range (the visible chart
    /// window) rather than the operations' min/max date. With sparse real data (e.g. a single
    /// entry) the operations span collapses to a few days and the WHO/CDC curves shrink into a
    /// sliver on the left edge; spanning the visible window keeps the curves filling the chart.
    static func percentileSeries(
        for babyProfile: BabyProfile,
        dateRange: ClosedRange<Date>,
        convertDecigramsToDisplay: (Int) -> Double,
        calendar: Calendar = .current,
        // MOB-1726: TOTAL passes the birth-MONTH start here so the age-axis of the curves lines up with the
        // month-bucketed data (both on a month-start axis) and the curves begin at the leading edge. `nil`
        // (week/month/year) → the real birthday, unchanged.
        birthdayOverride: Date? = nil
    ) -> [GraphSeries] {
        guard canResolveGrowthPercentiles(for: babyProfile) else { return [] }
        let birthday = birthdayOverride ?? resolvedBirthday(for: babyProfile, calendar: calendar)

        return BabyPercentileGrowthReference.percentileChartPoints(
            biologicalSex: babyProfile.biologicalSex,
            birthday: birthday,
            dateRange: dateRange,
            convertDecigramsToDisplay: convertDecigramsToDisplay,
            calendar: calendar
        ).map { point in
            GraphSeries(
                date: point.date,
                value: point.value,
                series: percentileSeriesName(for: point.line)
            )
        }
    }

    /// Real baby length series in inches — one point per period that actually recorded a length.
    /// Periods without a recorded length are omitted (no synthetic fill), mirroring how the weight
    /// series only plots real weigh-ins.
    static func heightSeries(
        from operations: [BathScaleWeightSummary]
    ) -> [GraphSeries] {
        operations.compactMap { summary in
            guard let lengthInches = summary.babyLengthInches, lengthInches > 0 else { return nil }
            return GraphSeries(
                date: summary.date,
                value: lengthInches,
                series: heightSeriesName
            )
        }
    }

    static func heightPercentileSeries(
        for babyProfile: BabyProfile,
        operations: [BathScaleWeightSummary],
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        guard let lowerBound = operations.map(\.date).min(),
              let upperBound = operations.map(\.date).max()
        else { return [] }
        return heightPercentileSeries(
            for: babyProfile,
            dateRange: lowerBound...upperBound,
            calendar: calendar
        )
    }

    /// MOB-1516: WHO length-for-age percentile reference curves over an explicit date range. Now driven by
    /// the real WHO length tables (mm, converted to display inches) — parity with Smart Baby's
    /// `*LengthMMLine` curves — replacing the earlier modeled-from-birth-length approximation.
    static func heightPercentileSeries(
        for babyProfile: BabyProfile,
        dateRange: ClosedRange<Date>,
        calendar: Calendar = .current,
        birthdayOverride: Date? = nil   // MOB-1726: TOTAL → birth-month start (see `percentileSeries`)
    ) -> [GraphSeries] {
        guard canResolveGrowthPercentiles(for: babyProfile) else { return [] }
        let birthday = birthdayOverride ?? resolvedBirthday(for: babyProfile, calendar: calendar)

        return BabyPercentileGrowthReference.lengthPercentileChartPoints(
            biologicalSex: babyProfile.biologicalSex,
            birthday: birthday,
            dateRange: dateRange,
            convertMillimetersToDisplay: millimetersToInches,
            calendar: calendar
        ).map { point in
            GraphSeries(
                date: point.date,
                value: point.value,
                series: percentileSeriesName(for: point.line)
            )
        }
    }

    static func yAxisScale(
        for operations: [BathScaleWeightSummary],
        babyProfile: BabyProfile,
        convertStoredWeightToDisplay: (Int) -> Double,
        convertDecigramsToDisplay: (Int) -> Double,
        calendar: Calendar = .current
    ) -> YAxisScale {
        let baseScale = DashboardChartScaleProvider.babyWeightScale(
            operations: operations,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )
        let percentileValues = percentileSeries(
            for: babyProfile,
            operations: operations,
            convertDecigramsToDisplay: convertDecigramsToDisplay,
            calendar: calendar
        ).map(\.value)
        return expandedWeightScale(baseScale: baseScale, percentileValues: percentileValues)
    }

    /// Weight Y-axis scale that sizes the domain to the percentile curves across an explicit
    /// date range (the visible chart window), so the curves never clip when real data is sparse.
    static func yAxisScale(
        for operations: [BathScaleWeightSummary],
        babyProfile: BabyProfile,
        dateRange: ClosedRange<Date>,
        convertStoredWeightToDisplay: (Int) -> Double,
        convertDecigramsToDisplay: (Int) -> Double,
        calendar: Calendar = .current,
        birthdayOverride: Date? = nil   // MOB-1726: keep the axis in sync with the (possibly re-anchored) curves
    ) -> YAxisScale {
        let baseScale = DashboardChartScaleProvider.babyWeightScale(
            operations: operations,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )
        let percentileValues = percentileSeries(
            for: babyProfile,
            dateRange: dateRange,
            convertDecigramsToDisplay: convertDecigramsToDisplay,
            calendar: calendar,
            birthdayOverride: birthdayOverride
        ).map(\.value)
        return expandedWeightScale(baseScale: baseScale, percentileValues: percentileValues)
    }

    private static func expandedWeightScale(
        baseScale: YAxisScale,
        percentileValues: [Double]
    ) -> YAxisScale {
        guard let percentileMin = percentileValues.min(),
              let percentileMax = percentileValues.max()
        else { return baseScale }

        let allMin = min(baseScale.domain.lowerBound, percentileMin)
        let allMax = max(baseScale.domain.upperBound, percentileMax)
        guard allMin < baseScale.domain.lowerBound || allMax > baseScale.domain.upperBound else {
            return baseScale
        }

        let padding = max((allMax - allMin) * 0.1, 1.0)
        let paddedMin = max(0, floor(allMin - padding))
        let paddedMax = ceil(allMax + padding)
        let range = paddedMax - paddedMin
        let step = max(1, ceil(range / 4.0))
        let niceMin = floor(paddedMin / step) * step
        let niceMax = ceil(paddedMax / step) * step

        var ticks: [Double] = []
        var tick = niceMin
        while tick <= niceMax {
            ticks.append(tick)
            tick += step
        }

        return YAxisScale(
            min: niceMin,
            max: niceMax,
            step: step,
            ticks: ticks,
            domain: niceMin...niceMax,
            average: baseScale.average
        )
    }

    static func heightYAxisScale(
        for operations: [BathScaleWeightSummary],
        babyProfile: BabyProfile,
        calendar: Calendar = .current
    ) -> YAxisScale {
        let primaryValues = heightSeries(from: operations).map(\.value)
        let percentileValues = heightPercentileSeries(
            for: babyProfile,
            operations: operations,
            calendar: calendar
        ).map(\.value)
        return heightScale(primaryValues: primaryValues, percentileValues: percentileValues)
    }

    /// Height Y-axis scale sized to the percentile curves across an explicit date range (the
    /// visible chart window), keeping the curves on-screen when real data is sparse.
    static func heightYAxisScale(
        for operations: [BathScaleWeightSummary],
        babyProfile: BabyProfile,
        dateRange: ClosedRange<Date>,
        calendar: Calendar = .current,
        birthdayOverride: Date? = nil   // MOB-1726: keep the axis in sync with the (possibly re-anchored) curves
    ) -> YAxisScale {
        let primaryValues = heightSeries(from: operations).map(\.value)
        let percentileValues = heightPercentileSeries(
            for: babyProfile,
            dateRange: dateRange,
            calendar: calendar,
            birthdayOverride: birthdayOverride
        ).map(\.value)
        return heightScale(primaryValues: primaryValues, percentileValues: percentileValues)
    }

    private static func heightScale(
        primaryValues: [Double],
        percentileValues: [Double]
    ) -> YAxisScale {
        let allValues = primaryValues + percentileValues

        guard let allMin = allValues.min(),
              let allMax = allValues.max() else {
            return YAxisScale(
                min: 10,
                max: minimumHeightAxisMax,
                step: heightTickStep,
                ticks: stride(from: 10.0, through: minimumHeightAxisMax, by: heightTickStep).map { $0 },
                domain: 10...minimumHeightAxisMax,
                average: 0
            )
        }

        let niceMin = max(0, floor((allMin - 5.0) / heightTickStep) * heightTickStep)
        let niceMax = max(
            minimumHeightAxisMax,
            ceil((allMax + 2.0) / heightTickStep) * heightTickStep
        )
        let ticks = stride(from: niceMin, through: niceMax, by: heightTickStep).map { $0 }
        let average = primaryValues.isEmpty ? 0 : primaryValues.reduce(0, +) / Double(primaryValues.count)

        return YAxisScale(
            min: niceMin,
            max: niceMax,
            step: heightTickStep,
            ticks: ticks,
            domain: niceMin...niceMax,
            average: average
        )
    }

    static func isPercentileSeries(_ seriesName: String) -> Bool {
        seriesName.hasPrefix(percentileSeriesPrefix)
    }

    static func isHeightSeries(_ seriesName: String) -> Bool {
        seriesName == heightSeriesName
    }

    /// The real recorded length (inches) for the period whose date matches `date` (same day),
    /// or nil when that period recorded no length. Used for the selected-point height readout.
    static func heightValue(
        on date: Date,
        in operations: [BathScaleWeightSummary],
        calendar: Calendar = .current
    ) -> Double? {
        let targetDay = calendar.startOfDay(for: date)
        return operations.first {
            calendar.isDate($0.date, inSameDayAs: targetDay) && ($0.babyLengthInches ?? 0) > 0
        }?.babyLengthInches
    }

    /// Average of the real recorded lengths (inches) across `operations`, or nil when none recorded a length.
    static func averageHeight(from operations: [BathScaleWeightSummary]) -> Double? {
        let values = operations.compactMap { $0.babyLengthInches }.filter { $0 > 0 }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / Double(values.count)
    }

    static func heightPercentile(
        for babyProfile: BabyProfile,
        heightInches: Double,
        on date: Date,
        calendar: Calendar = .current
    ) -> Int? {
        guard canResolveGrowthPercentiles(for: babyProfile) else { return nil }
        // MOB-1516: real WHO length-for-age z-score (inches → mm), mirroring the weight percentile — replaces
        // the earlier interpolate-against-modeled-curves approximation.
        return BabyPercentileGrowthReference.lengthPercentile(
            biologicalSex: babyProfile.biologicalSex,
            birthday: resolvedBirthday(for: babyProfile, calendar: calendar),
            date: date,
            lengthMillimeters: heightInches * BabyPercentileGrowthReference.millimetersPerInch,
            calendar: calendar
        )
    }

    static func percentileLine(for seriesName: String) -> BabyPercentileLine? {
        guard isPercentileSeries(seriesName) else { return nil }
        let rawValue = String(seriesName.dropFirst(percentileSeriesPrefix.count))
        return BabyPercentileLine.allCases.first { $0.rawValue == rawValue }
    }

    private static func percentileSeriesName(for line: BabyPercentileLine) -> String {
        percentileSeriesPrefix + line.rawValue
    }

    // MARK: - Weight Conversion Helpers

    static func convertStoredWeightToDisplay(_ storedWeight: Int, unit: WeightUnit) -> Double {
        unit == .kg
            ? ConversionTools.convertStoredToKg(storedWeight)
            : ConversionTools.convertStoredToLbs(storedWeight)
    }

    static func convertDecigramsToDisplay(_ decigrams: Int, unit: WeightUnit) -> Double {
        let kg = Double(decigrams) / BabyPercentileGrowthReference.decigramsToKgFactor
        let stored = ConversionTools.convertKgToStored(kg)
        return unit == .kg
            ? ConversionTools.convertStoredToKg(stored)
            : ConversionTools.convertStoredToLbs(stored)
    }

    static func formatBabyWeight(_ storedWeight: Int, unit: WeightUnit) -> (lbs: String, oz: String) {
        let displayWeight = convertStoredWeightToDisplay(storedWeight, unit: unit)
        var wholeLbs = Int(displayWeight)
        let rawOz = (displayWeight - Double(wholeLbs)) * 16.0
        let roundedOz = (rawOz * 10).rounded() / 10
        var remainingOz = roundedOz
        if roundedOz >= 16.0 { wholeLbs += 1; remainingOz = 0.0 }
        return (lbs: "\(wholeLbs)", oz: String(format: "%.1f", remainingOz))
    }

    static func weekAverageLbsOz(
        from summaries: [BathScaleWeightSummary],
        unit: WeightUnit
    ) -> (lbs: String, oz: String)? {
        let weights = summaries.map(\.weight).filter { $0 > 0 }
        guard !weights.isEmpty else { return nil }
        let avgStored = Int((weights.reduce(0, +) / Double(weights.count)).rounded())
        return formatBabyWeight(avgStored, unit: unit)
    }

    // MARK: - MeasurementUnits-aware display formatting

    static func formatBabyWeightDisplay(_ storedWeight: Int, units: MeasurementUnits) -> BabyWeightDisplay {
        let displayWeight = convertStoredWeightToDisplay(storedWeight, unit: units == .metric ? .kg : .lb)
        switch units {
        case .metric:
            return BabyWeightDisplay(
                primary: String(format: "%.3f", displayWeight),
                primaryUnit: BabyDashboardStrings.kg,
                secondary: nil,
                secondaryUnit: nil
            )
        case .imperialLbDecimal:
            return BabyWeightDisplay(
                primary: String(format: "%.1f", displayWeight),
                primaryUnit: BabyDashboardStrings.lb,
                secondary: nil,
                secondaryUnit: nil
            )
        case .imperialLbOz:
            var wholeLbs = Int(displayWeight)
            let rawOz = (displayWeight - Double(wholeLbs)) * 16.0
            let roundedOz = (rawOz * 10).rounded() / 10
            var remainingOz = roundedOz
            if roundedOz >= 16.0 { wholeLbs += 1; remainingOz = 0.0 }
            return BabyWeightDisplay(
                primary: "\(wholeLbs)",
                primaryUnit: BabyDashboardStrings.lb,
                secondary: String(format: "%.1f", remainingOz),
                secondaryUnit: BabyDashboardStrings.oz
            )
        }
    }

    static func weekAverageDisplay(
        from summaries: [BathScaleWeightSummary],
        units: MeasurementUnits
    ) -> BabyWeightDisplay? {
        let weights = summaries.map(\.weight).filter { $0 > 0 }
        guard !weights.isEmpty else { return nil }
        let avgStored = Int((weights.reduce(0, +) / Double(weights.count)).rounded())
        return formatBabyWeightDisplay(avgStored, units: units)
    }

    /// Placeholder display shown when no readings are available. Shows a zero value (matching
    /// each unit's populated format) rather than a "--" dash, per the baby graph empty state.
    static func emptyWeightDisplay(for units: MeasurementUnits) -> BabyWeightDisplay {
        switch units {
        case .metric:
            return BabyWeightDisplay(primary: "0.000", primaryUnit: BabyDashboardStrings.kg, secondary: nil, secondaryUnit: nil)
        case .imperialLbDecimal:
            return BabyWeightDisplay(primary: "0.0", primaryUnit: BabyDashboardStrings.lb, secondary: nil, secondaryUnit: nil)
        case .imperialLbOz:
            return BabyWeightDisplay(primary: "00", primaryUnit: BabyDashboardStrings.lb, secondary: "0.0", secondaryUnit: BabyDashboardStrings.oz)
        }
    }

    // MARK: - Snapshot Card Helpers

    /// Returns every `stride`-th point, always including the first and last point
    /// to preserve line continuity at the edges.
    static func thinnedPercentilePoints(
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

    /// Clips a sorted array of date/value points at `endDate`, inserting an
    /// interpolated boundary point when the last visible point falls before the clip edge.
    static func rightClippedPoints(
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

    /// Clips percentile chart points at `endDate` with interpolation.
    static func rightClippedPercentilePoints(
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

    /// Linearly interpolates a value at `targetDate` between two known points.
    static func interpolatedValue(
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

    /// Returns only the first and last Y-axis tick values for boundary grid lines.
    static func boundaryYTicks(from ticks: [Double]) -> [Double] {
        guard let first = ticks.first else { return [] }
        guard let last = ticks.last,
              abs(last - first) > AppConstants.Precision.doubleEqualityEpsilon else {
            return [first]
        }
        return [first, last]
    }
}
