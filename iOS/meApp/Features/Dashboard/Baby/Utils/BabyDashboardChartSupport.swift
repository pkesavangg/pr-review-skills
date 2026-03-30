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
    private static let defaultBabyAgeDays = 84
    private static let dummyHistoryWindowDays = 180
    private static let defaultBirthWeightLbs = 7.4

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

    static func isPercentileSeries(_ seriesName: String) -> Bool {
        seriesName.hasPrefix(percentileSeriesPrefix)
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
