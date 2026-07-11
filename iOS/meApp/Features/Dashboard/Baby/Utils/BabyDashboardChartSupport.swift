//
//  BabyDashboardChartSupport.swift
//  meApp
//
//  Shared baby-dashboard chart helpers used by both snapshot cards and the
//  full dashboard trend graph.
//

import Foundation

// swiftlint:disable file_length

/// Multi-unit baby weight display value. Primary is always populated; secondary (oz) is non-nil only for the lbs+oz format.
struct BabyWeightDisplay {
    let primary: String        // "7" / "7.7" / "3.520"
    let primaryUnit: String    // "lbs" / "lb" / "kg"
    let secondary: String?     // "12.0" for lbs+oz, nil otherwise
    let secondaryUnit: String? // "oz" for lbs+oz, nil otherwise
}

// swiftlint:disable:next type_body_length
enum BabyDashboardChartSupport {
    private static let percentileSeriesPrefix = "baby_percentile_"
    private static let heightSeriesName = "baby_height"
    private static let defaultBabyAgeDays = 84
    private static let dummyHistoryWindowDays = 180
    private static let defaultBirthWeightLbs = 7.4
    private static let defaultBirthLengthInches = 19.5
    private static let heightTickStep = 5.0
    private static let minimumHeightAxisMax = 25.0
    private static let heightPercentileOffsets: [BabyPercentileLine: Double] = [
        .fifth: -1.3,
        .tenth: -0.95,
        .twentyFifth: -0.5,
        .fiftieth: 0.0,
        .seventyFifth: 0.5,
        .ninetieth: 0.95,
        .ninetyFifth: 1.3
    ]

    static func clearDummySummariesCache() {
        // Compatibility hook kept for callers that still reset baby chart state.
    }

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

    static func dummySummaries(
        for babyProfile: BabyProfile,
        period: TimePeriod,
        calendar: Calendar = .current
    ) -> [BathScaleWeightSummary] {
        switch period {
        case .week:
            return dummyDailySummaries(
                for: babyProfile,
                calendar: calendar,
                endDate: upcomingSunday(from: Date(), calendar: calendar)
            )
        case .month:
            return dummyDailySummaries(for: babyProfile, calendar: calendar)
        case .year, .total:
            let daily = dummyDailySummaries(for: babyProfile, calendar: calendar)
            return aggregateMonthly(daily, calendar: calendar)
        }
    }

    static func dummyDailySummaries(
        for babyProfile: BabyProfile,
        calendar: Calendar = .current,
        endDate: Date? = nil
    ) -> [BathScaleWeightSummary] {
        let today = calendar.startOfDay(for: endDate ?? Date())
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar, today: today)
        let startDate = max(
            birthday,
            calendar.date(byAdding: .day, value: -dummyHistoryWindowDays, to: today) ?? birthday
        )
        let birthWeightStored = storedBirthWeight(for: babyProfile)
        let dayFormatter = DateFormatter()
        dayFormatter.calendar = calendar
        dayFormatter.dateFormat = "yyyy-MM-dd"
        let isoFormatter = ISO8601DateFormatter()

        let totalDays = max(0, calendar.dateComponents([.day], from: startDate, to: today).day ?? 0)
        return (0...totalDays).compactMap { offset in
            guard let date = calendar.date(byAdding: .day, value: offset, to: startDate) else { return nil }
            let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: date).day ?? 0)
            return BathScaleWeightSummary(
                accountId: "dummy_baby_\(babyProfile.id)",
                period: dayFormatter.string(from: date),
                entryTimestamp: isoFormatter.string(from: date),
                date: date,
                count: 1,
                weight: Double(dummyStoredWeight(forDayOfLife: dayOfLife, birthWeightStored: birthWeightStored))
            )
        }
    }

    private static func upcomingSunday(
        from date: Date,
        calendar: Calendar = .current
    ) -> Date {
        let normalizedDate = calendar.startOfDay(for: date)
        let weekday = calendar.component(.weekday, from: normalizedDate)
        let daysUntilSunday = (8 - weekday) % 7
        return calendar.date(byAdding: .day, value: daysUntilSunday, to: normalizedDate) ?? normalizedDate
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
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar)

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

    static func dummyHeightSeries(
        for babyProfile: BabyProfile,
        operations: [BathScaleWeightSummary],
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        operations.map { summary in
            GraphSeries(
                date: summary.date,
                value: dummyHeightValue(for: babyProfile, on: summary.date, calendar: calendar),
                series: heightSeriesName
            )
        }
    }

    static func heightPercentileSeries(
        for babyProfile: BabyProfile,
        operations: [BathScaleWeightSummary],
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        guard !BabyPercentileGrowthReference.isSexWithheld(babyProfile.biologicalSex) else { return [] }
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar)
        let birthLengthInches = resolvedBirthLengthInches(for: babyProfile)

        return operations.flatMap { summary in
            let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: summary.date).day ?? 0)
            return BabyPercentileLine.allCases.map { line in
                GraphSeries(
                    date: summary.date,
                    value: percentileHeightInches(
                        forDayOfLife: dayOfLife,
                        birthLengthInches: birthLengthInches,
                        line: line
                    ),
                    series: percentileSeriesName(for: line)
                )
            }
        }
    }

    /// Height percentile reference curves spanning an explicit date range (the visible chart
    /// window), sampled per day and downsampled to ~150 points. Mirrors the weight percentile
    /// curves so the lines fill the chart even when only one real entry exists.
    static func heightPercentileSeries(
        for babyProfile: BabyProfile,
        dateRange: ClosedRange<Date>,
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        guard !BabyPercentileGrowthReference.isSexWithheld(babyProfile.biologicalSex) else { return [] }
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar)
        let birthLengthInches = resolvedBirthLengthInches(for: babyProfile)

        let totalDays = max(0, calendar.dateComponents([.day], from: dateRange.lowerBound, to: dateRange.upperBound).day ?? 0)
        let step = max(1, totalDays / 150)
        var offsets = Array(Swift.stride(from: 0, through: totalDays, by: step))
        if offsets.last != totalDays { offsets.append(totalDays) }

        return offsets.flatMap { offset -> [GraphSeries] in
            guard let date = calendar.date(byAdding: .day, value: offset, to: dateRange.lowerBound) else { return [] }
            let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: date).day ?? 0)
            return BabyPercentileLine.allCases.map { line in
                GraphSeries(
                    date: date,
                    value: percentileHeightInches(
                        forDayOfLife: dayOfLife,
                        birthLengthInches: birthLengthInches,
                        line: line
                    ),
                    series: percentileSeriesName(for: line)
                )
            }
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
        calendar: Calendar = .current
    ) -> YAxisScale {
        let baseScale = DashboardChartScaleProvider.babyWeightScale(
            operations: operations,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )
        let percentileValues = percentileSeries(
            for: babyProfile,
            dateRange: dateRange,
            convertDecigramsToDisplay: convertDecigramsToDisplay,
            calendar: calendar
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
        let primaryValues = dummyHeightSeries(
            for: babyProfile,
            operations: operations,
            calendar: calendar
        ).map(\.value)
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
        calendar: Calendar = .current
    ) -> YAxisScale {
        let primaryValues = dummyHeightSeries(
            for: babyProfile,
            operations: operations,
            calendar: calendar
        ).map(\.value)
        let percentileValues = heightPercentileSeries(
            for: babyProfile,
            dateRange: dateRange,
            calendar: calendar
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

    static func dummyHeightValue(
        for babyProfile: BabyProfile,
        on date: Date,
        calendar: Calendar = .current
    ) -> Double {
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar, today: date)
        let normalizedDate = calendar.startOfDay(for: date)
        let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: normalizedDate).day ?? 0)
        return dummyHeightInches(
            forDayOfLife: dayOfLife,
            birthLengthInches: resolvedBirthLengthInches(for: babyProfile)
        )
    }

    static func averageDummyHeight(
        for babyProfile: BabyProfile,
        dates: [Date],
        calendar: Calendar = .current
    ) -> Double {
        guard !dates.isEmpty else {
            return dummyHeightValue(for: babyProfile, on: Date(), calendar: calendar)
        }

        let values = dates.map { dummyHeightValue(for: babyProfile, on: $0, calendar: calendar) }
        return values.reduce(0, +) / Double(values.count)
    }

    static func heightPercentile(
        for babyProfile: BabyProfile,
        heightInches: Double,
        on date: Date,
        calendar: Calendar = .current
    ) -> Int? {
        guard !BabyPercentileGrowthReference.isSexWithheld(babyProfile.biologicalSex) else { return nil }
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar, today: date)
        let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: date).day ?? 0)
        let birthLengthInches = resolvedBirthLengthInches(for: babyProfile)

        let percentileHeights = BabyPercentileLine.allCases.map { line in
            (
                percentile: percentileValue(for: line),
                value: percentileHeightInches(
                    forDayOfLife: dayOfLife,
                    birthLengthInches: birthLengthInches,
                    line: line
                )
            )
        }

        return interpolatedPercentile(for: heightInches, percentileHeights: percentileHeights)
    }

    static func percentileLine(for seriesName: String) -> BabyPercentileLine? {
        guard isPercentileSeries(seriesName) else { return nil }
        let rawValue = String(seriesName.dropFirst(percentileSeriesPrefix.count))
        return BabyPercentileLine.allCases.first { $0.rawValue == rawValue }
    }

    private static func percentileSeriesName(for line: BabyPercentileLine) -> String {
        percentileSeriesPrefix + line.rawValue
    }

    private static func percentileValue(for line: BabyPercentileLine) -> Int {
        switch line {
        case .fifth: return 5
        case .tenth: return 10
        case .twentyFifth: return 25
        case .fiftieth: return 50
        case .seventyFifth: return 75
        case .ninetieth: return 90
        case .ninetyFifth: return 95
        }
    }

    private static func interpolatedPercentile(
        for measurement: Double,
        percentileHeights: [(percentile: Int, value: Double)]
    ) -> Int {
        guard let first = percentileHeights.first else { return 0 }
        guard let last = percentileHeights.last else { return 0 }

        if measurement <= first.value {
            return first.percentile
        }

        if measurement >= last.value {
            return last.percentile
        }

        for index in 1..<percentileHeights.count {
            let lower = percentileHeights[index - 1]
            let upper = percentileHeights[index]
            guard measurement <= upper.value else { continue }
            let range = upper.value - lower.value
            guard range > AppConstants.Precision.doubleEqualityEpsilon else {
                return lower.percentile
            }

            let progress = (measurement - lower.value) / range
            let percentile = Double(lower.percentile) + (Double(upper.percentile - lower.percentile) * progress)
            return Int(percentile.rounded())
        }

        return last.percentile
    }

    private static func storedBirthWeight(for babyProfile: BabyProfile) -> Int {
        let lbs = babyProfile.birthWeightLbs ?? floor(defaultBirthWeightLbs)
        let oz = babyProfile.birthWeightOz ?? ((defaultBirthWeightLbs - floor(defaultBirthWeightLbs)) * 16.0)
        return ConversionTools.convertLbsToStored(lbs + (oz / 16.0))
    }

    private static func dummyStoredWeight(forDayOfLife day: Int, birthWeightStored: Int) -> Int {
        let birthWeightLbs = ConversionTools.convertStoredToLbs(birthWeightStored)
        let earlyDip: Double
        if day <= 5 {
            earlyDip = -0.04 * Double(day)
        } else if day <= 12 {
            earlyDip = -0.20 + (Double(day - 5) * 0.028)
        } else {
            earlyDip = 0
        }

        let earlyGrowth = min(Double(max(day - 12, 0)), 90) * 0.045
        let laterGrowth = max(Double(day - 102), 0) * 0.018
        let weeklyVariation = sin(Double(day) / 8.0) * 0.06
        let lbs = max(4.0, birthWeightLbs + earlyDip + earlyGrowth + laterGrowth + weeklyVariation)
        return ConversionTools.convertLbsToStored(lbs)
    }

    private static func resolvedBirthLengthInches(for babyProfile: BabyProfile) -> Double {
        max(12.0, babyProfile.birthLengthInches ?? defaultBirthLengthInches)
    }

    private static func dummyHeightInches(
        forDayOfLife day: Int,
        birthLengthInches: Double
    ) -> Double {
        let earlyGrowth = min(Double(day), 90) * 0.026
        let laterGrowth = max(Double(day - 90), 0) * 0.010
        let weeklyVariation = sin(Double(day) / 11.0) * 0.08
        return max(12.0, birthLengthInches + earlyGrowth + laterGrowth + weeklyVariation)
    }

    private static func percentileHeightInches(
        forDayOfLife day: Int,
        birthLengthInches: Double,
        line: BabyPercentileLine
    ) -> Double {
        let spreadMultiplier = 1.0 + (min(Double(day), 365) / 365.0) * 0.2
        let offset = (heightPercentileOffsets[line] ?? 0) * spreadMultiplier
        return dummyHeightInches(forDayOfLife: day, birthLengthInches: birthLengthInches) + offset
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

    private static func aggregateMonthly(
        _ dailySummaries: [BathScaleWeightSummary],
        calendar: Calendar = .current
    ) -> [BathScaleWeightSummary] {
        let monthFormatter = DateFormatter()
        monthFormatter.calendar = calendar
        monthFormatter.dateFormat = "yyyy-MM"
        let isoFormatter = ISO8601DateFormatter()

        let grouped = Dictionary(grouping: dailySummaries) { summary in
            calendar.date(from: calendar.dateComponents([.year, .month], from: summary.date)) ?? summary.date
        }

        return grouped.keys.sorted().compactMap { monthStart in
            guard let summaries = grouped[monthStart], !summaries.isEmpty else { return nil }
            let averageWeight = summaries.map(\.weight).reduce(0, +) / Double(summaries.count)
            return BathScaleWeightSummary(
                accountId: summaries.first?.accountId ?? "dummy_baby",
                period: monthFormatter.string(from: monthStart),
                entryTimestamp: isoFormatter.string(from: monthStart),
                date: monthStart,
                count: summaries.count,
                weight: averageWeight
            )
        }
    }
}
// swiftlint:enable file_length
