//
//  BabyDashboardChartSupport.swift
//  meApp
//
//  Shared baby-dashboard chart helpers used by both snapshot cards and the
//  full dashboard trend graph.
//

import Foundation

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
        let daily = dummyDailySummaries(for: babyProfile, calendar: calendar)
        switch period {
        case .week, .month:
            return daily
        case .year, .total:
            return aggregateMonthly(daily, calendar: calendar)
        }
    }

    static func dummyDailySummaries(
        for babyProfile: BabyProfile,
        calendar: Calendar = .current
    ) -> [BathScaleWeightSummary] {
        let today = calendar.startOfDay(for: Date())
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

    static func percentileSeries(
        for babyProfile: BabyProfile,
        operations: [BathScaleWeightSummary],
        convertDecigramsToDisplay: (Int) -> Double,
        calendar: Calendar = .current
    ) -> [GraphSeries] {
        guard let lowerBound = operations.map(\.date).min(),
              let upperBound = operations.map(\.date).max()
        else { return [] }
        let birthday = resolvedBirthday(for: babyProfile, calendar: calendar)

        return BabyPercentileGrowthReference.percentileChartPoints(
            biologicalSex: babyProfile.biologicalSex,
            birthday: birthday,
            dateRange: lowerBound...upperBound,
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

    static func percentileLine(for seriesName: String) -> BabyPercentileLine? {
        guard isPercentileSeries(seriesName) else { return nil }
        let rawValue = String(seriesName.dropFirst(percentileSeriesPrefix.count))
        return BabyPercentileLine.allCases.first { $0.rawValue == rawValue }
    }

    private static func percentileSeriesName(for line: BabyPercentileLine) -> String {
        percentileSeriesPrefix + line.rawValue
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
