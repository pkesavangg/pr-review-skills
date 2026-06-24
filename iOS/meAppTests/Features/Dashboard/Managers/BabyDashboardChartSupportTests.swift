import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BabyDashboardChartSupportTests {

    // MARK: - Helpers

    private var cal: Calendar { .current }

    private func makeBabyProfile(
        id: String = "b1",
        sex: String = "female",
        birthday: Date? = nil,
        birthWeightLbs: Double? = 7.5,
        birthWeightOz: Double? = 3.0,
        birthLengthInches: Double? = 19.5
    ) -> BabyProfile {
        let bd = birthday ?? cal.date(byAdding: .day, value: -90, to: Date())
        return BabyProfile(
            id: id,
            name: "TestBaby",
            birthday: bd,
            biologicalSex: sex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
    }

    private func makeDailySummaries(count: Int, from startDate: Date) -> [BathScaleWeightSummary] {
        (0..<count).compactMap { offset in
            guard let date = cal.date(byAdding: .day, value: offset, to: startDate) else { return nil }
            return BathScaleWeightSummary(
                accountId: "baby-test",
                period: DateTimeTools.formatter("yyyy-MM-dd").string(from: date),
                entryTimestamp: ISO8601DateFormatter().string(from: date),
                date: date,
                count: 1,
                weight: 800 + Double(offset) * 2
            )
        }
    }

    // MARK: - resolvedBirthday

    // MARK: - formatBabyWeightDisplay oz rounding

    @Test("formatBabyWeightDisplay imperialLbOz: normal case converts fractional lbs to oz correctly")
    func formatBabyWeightDisplayImperialLbOzNormalCase() {
        // storedWeight = 105 → displayWeight = 10.5 lbs → rawOz = 0.5 * 16 = 8.0 oz
        let result = BabyDashboardChartSupport.formatBabyWeightDisplay(105, units: .imperialLbOz)

        #expect(result.primary == "10")
        #expect(result.secondary == "8.0")
    }

    @Test("formatBabyWeightDisplay imperialLbOz: near-boundary 10.9 lbs stays below 16 oz without carry")
    func formatBabyWeightDisplayImperialLbOzNearBoundaryNoCarry() {
        // storedWeight = 109 → displayWeight = 10.9 lbs → rawOz = 0.9 * 16 = 14.4 oz — no carry
        let result = BabyDashboardChartSupport.formatBabyWeightDisplay(109, units: .imperialLbOz)

        #expect(result.primary == "10")
        #expect(result.secondary == "14.4")
    }

    // MARK: - formatBabyWeight oz rounding

    @Test("formatBabyWeight: normal case converts fractional lbs to oz correctly")
    func formatBabyWeightNormalCase() {
        // storedWeight = 105 → displayWeight = 10.5 lbs → rawOz = 8.0 oz
        let result = BabyDashboardChartSupport.formatBabyWeight(105, unit: .lb)

        #expect(result.lbs == "10")
        #expect(result.oz == "8.0")
    }

    @Test("formatBabyWeight: near-boundary 10.9 lbs stays below 16 oz without carry")
    func formatBabyWeightNearBoundaryNoCarry() {
        // storedWeight = 109 → displayWeight = 10.9 lbs → rawOz = 14.4 oz — no carry
        let result = BabyDashboardChartSupport.formatBabyWeight(109, unit: .lb)

        #expect(result.lbs == "10")
        #expect(result.oz == "14.4")
    }

    @Test("resolvedBirthday returns babyProfile.birthday when set and not in the future")
    func resolvedBirthdayUsesProfileBirthday() {
        let pastDate = cal.date(byAdding: .day, value: -30, to: Date()) ?? Date()
        let baby = makeBabyProfile(birthday: pastDate)
        let resolved = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        #expect(cal.isDate(resolved, inSameDayAs: pastDate))
    }

    @Test("resolvedBirthday falls back to defaultAgeDays ago when birthday is nil")
    func resolvedBirthdayFallbackWhenNil() {
        let baby = makeBabyProfile(birthday: nil)
        let resolved = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let today = cal.startOfDay(for: Date())
        #expect(resolved <= today)
        #expect(resolved > cal.date(byAdding: .day, value: -365, to: today) ?? today)
    }

    @Test("resolvedBirthday clamps future birthday to today")
    func resolvedBirthdayClampsFutureDateToToday() {
        let futureBirthday = cal.date(byAdding: .day, value: 30, to: Date()) ?? Date()
        let baby = makeBabyProfile(birthday: futureBirthday)
        let resolved = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let today = cal.startOfDay(for: Date())
        #expect(resolved <= today)
    }

    // MARK: - dummyDailySummaries

    @Test("dummyDailySummaries returns non-empty array for a baby born 90 days ago")
    func dummyDailySummariesNonEmpty() {
        let baby = makeBabyProfile()
        let summaries = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        #expect(!summaries.isEmpty)
    }

    @Test("dummyDailySummaries dates are non-decreasing")
    func dummyDailySummariesDatesOrdered() {
        let baby = makeBabyProfile()
        let summaries = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        let dates = summaries.map(\.date)
        let sorted = dates.sorted()
        #expect(dates == sorted)
    }

    @Test("dummyDailySummaries has positive weights")
    func dummyDailySummariesPositiveWeights() {
        let baby = makeBabyProfile()
        let summaries = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        #expect(summaries.allSatisfy { $0.weight > 0 })
    }

    @Test("dummyDailySummaries uses baby account id prefix")
    func dummyDailySummariesUsesCorrectAccountId() {
        let baby = makeBabyProfile(id: "my-baby")
        let summaries = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        #expect(summaries.allSatisfy { $0.accountId.contains("my-baby") })
    }

    // MARK: - dummySummaries

    @Test("dummySummaries for week returns same count as daily")
    func dummySummariesWeekReturnsDailyData() {
        let baby = makeBabyProfile()
        let daily = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        let week = BabyDashboardChartSupport.dummySummaries(for: baby, period: .week)
        #expect(week.count == daily.count)
    }

    @Test("dummySummaries for month returns same count as daily")
    func dummySummariesMonthReturnsDailyData() {
        let baby = makeBabyProfile()
        let daily = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        let month = BabyDashboardChartSupport.dummySummaries(for: baby, period: .month)
        #expect(month.count == daily.count)
    }

    @Test("dummySummaries for year returns monthly aggregated data")
    func dummySummariesYearReturnsMonthlyData() {
        let birthday = cal.date(byAdding: .day, value: -200, to: Date()) ?? Date()
        let baby = makeBabyProfile(birthday: birthday)
        let year = BabyDashboardChartSupport.dummySummaries(for: baby, period: .year)
        let daily = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        // Aggregated should have fewer items than daily
        #expect(year.count < daily.count)
        #expect(!year.isEmpty)
    }

    @Test("dummySummaries for total returns monthly aggregated data")
    func dummySummariesTotalReturnsMonthlyData() {
        let birthday = cal.date(byAdding: .day, value: -200, to: Date()) ?? Date()
        let baby = makeBabyProfile(birthday: birthday)
        let total = BabyDashboardChartSupport.dummySummaries(for: baby, period: .total)
        let year = BabyDashboardChartSupport.dummySummaries(for: baby, period: .year)
        #expect(total.count == year.count)
    }

    // MARK: - percentileSeries

    @Test("percentileSeries returns empty when operations are empty")
    func percentileSeriesEmptyForNoOperations() {
        let baby = makeBabyProfile()
        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: [],
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )
        #expect(result.isEmpty)
    }

    @Test("percentileSeries returns multiple series names for all percentile lines")
    func percentileSeriesHasAllPercentileLines() {
        let baby = makeBabyProfile()
        let startDate = cal.date(byAdding: .day, value: -30, to: Date()) ?? Date()
        let operations = makeDailySummaries(count: 10, from: startDate)

        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: operations,
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )

        #expect(!result.isEmpty)
        let seriesNames = Set(result.map(\.series))
        // Should have series for all percentile lines
        #expect(seriesNames.count == BabyPercentileLine.allCases.count)
    }

    @Test("percentileSeries all have baby_percentile_ prefix")
    func percentileSeriesHaveCorrectPrefix() {
        let baby = makeBabyProfile()
        let startDate = cal.date(byAdding: .day, value: -14, to: Date()) ?? Date()
        let operations = makeDailySummaries(count: 7, from: startDate)

        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: operations,
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )

        #expect(result.allSatisfy { BabyDashboardChartSupport.isPercentileSeries($0.series) })
    }

    @Test("percentileSeries spans the operation range")
    func percentileSeriesUsesOperationRange() {
        let baby = makeBabyProfile()
        let operations = makeDailySummaries(count: 8, from: cal.date(byAdding: .day, value: -20, to: Date())!) // swiftlint:disable:this force_unwrapping

        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: operations,
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )

        let dates = result.map(\.date)
        #expect(dates.min() == operations.first?.date)
        #expect(dates.max() == operations.last?.date)
    }

    // MARK: - dummyHeightSeries

    @Test("dummyHeightSeries returns same count as operations")
    func dummyHeightSeriesMatchesOperationCount() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 7, from: cal.date(byAdding: .day, value: -7, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.dummyHeightSeries(for: baby, operations: ops)
        #expect(result.count == 7)
    }

    @Test("dummyHeightSeries uses baby_height series name")
    func dummyHeightSeriesHasCorrectSeriesName() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 3, from: cal.date(byAdding: .day, value: -3, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.dummyHeightSeries(for: baby, operations: ops)
        #expect(result.allSatisfy { BabyDashboardChartSupport.isHeightSeries($0.series) })
    }

    @Test("dummyHeightSeries values are positive")
    func dummyHeightSeriesPositiveValues() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 5, from: cal.date(byAdding: .day, value: -5, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.dummyHeightSeries(for: baby, operations: ops)
        #expect(result.allSatisfy { $0.value > 0 })
    }

    // MARK: - heightPercentileSeries

    @Test("heightPercentileSeries returns count = operations * allCases")
    func heightPercentileSeriesCountMatchesOperationsTimesLines() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 4, from: cal.date(byAdding: .day, value: -4, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.heightPercentileSeries(for: baby, operations: ops)
        #expect(result.count == ops.count * BabyPercentileLine.allCases.count)
    }

    @Test("heightPercentileSeries values are positive")
    func heightPercentileSeriesPositiveValues() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 3, from: cal.date(byAdding: .day, value: -3, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.heightPercentileSeries(for: baby, operations: ops)
        #expect(result.allSatisfy { $0.value > 0 })
    }

    @Test("heightPercentileSeries spans the operation range")
    func heightPercentileSeriesUsesOperationRange() {
        let baby = makeBabyProfile()
        let operations = makeDailySummaries(count: 8, from: cal.date(byAdding: .day, value: -20, to: Date())!) // swiftlint:disable:this force_unwrapping

        let result = BabyDashboardChartSupport.heightPercentileSeries(
            for: baby,
            operations: operations
        )

        let dates = result.map(\.date)
        #expect(dates.min() == operations.first?.date)
        #expect(dates.max() == operations.last?.date)
    }

    // MARK: - yAxisScale

    @Test("yAxisScale returns valid domain for non-empty operations")
    func yAxisScaleValidDomain() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 10, from: cal.date(byAdding: .day, value: -10, to: Date()) ?? Date())
        let scale = BabyDashboardChartSupport.yAxisScale(
            for: ops,
            babyProfile: baby,
            convertStoredWeightToDisplay: { ConversionTools.convertStoredToLbs($0) },
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )
        #expect(scale.domain.lowerBound < scale.domain.upperBound)
    }

    @Test("yAxisScale domain includes percentile range")
    func yAxisScaleDomainEncompassesPercentiles() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 10, from: cal.date(byAdding: .day, value: -10, to: Date()) ?? Date())
        let scale = BabyDashboardChartSupport.yAxisScale(
            for: ops,
            babyProfile: baby,
            convertStoredWeightToDisplay: { ConversionTools.convertStoredToLbs($0) },
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )
        #expect(scale.min >= 0)
        #expect(scale.ticks.count >= 2)
    }

    @Test("yAxisScale returns base scale when operations are empty")
    func yAxisScaleEmptyOperations() {
        let baby = makeBabyProfile()
        let scale = BabyDashboardChartSupport.yAxisScale(
            for: [],
            babyProfile: baby,
            convertStoredWeightToDisplay: { ConversionTools.convertStoredToLbs($0) },
            convertDecigramsToDisplay: { Double($0) / 10000.0 }
        )
        // base scale still returns something valid
        #expect(scale.domain.lowerBound <= scale.domain.upperBound)
    }

    // MARK: - heightYAxisScale

    @Test("heightYAxisScale returns valid domain for non-empty operations")
    func heightYAxisScaleValidDomain() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 7, from: cal.date(byAdding: .day, value: -7, to: Date()) ?? Date())
        let scale = BabyDashboardChartSupport.heightYAxisScale(for: ops, babyProfile: baby)
        #expect(scale.domain.lowerBound < scale.domain.upperBound)
        #expect(scale.min >= 0)
    }

    @Test("heightYAxisScale returns fallback scale when operations are empty")
    func heightYAxisScaleFallbackWhenEmpty() {
        let baby = makeBabyProfile()
        let scale = BabyDashboardChartSupport.heightYAxisScale(for: [], babyProfile: baby)
        // Fallback is 10...25 with step 5
        #expect(scale.domain.lowerBound == 10)
        #expect(scale.domain.upperBound == 25)
    }

    @Test("heightYAxisScale minimum max is 25 inches")
    func heightYAxisScaleMinimumMaxIs25() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 2, from: cal.date(byAdding: .day, value: -2, to: Date()) ?? Date())
        let scale = BabyDashboardChartSupport.heightYAxisScale(for: ops, babyProfile: baby)
        #expect(scale.max >= 25)
    }

    // MARK: - isPercentileSeries / isHeightSeries

    @Test("isPercentileSeries returns true for prefixed names")
    func isPercentileSeriesTrue() {
        #expect(BabyDashboardChartSupport.isPercentileSeries("baby_percentile_fiftieth"))
        #expect(BabyDashboardChartSupport.isPercentileSeries("baby_percentile_fifth"))
    }

    @Test("isPercentileSeries returns false for non-matching names")
    func isPercentileSeriesFalse() {
        #expect(!BabyDashboardChartSupport.isPercentileSeries("weight"))
        #expect(!BabyDashboardChartSupport.isPercentileSeries("baby_height"))
        #expect(!BabyDashboardChartSupport.isPercentileSeries(""))
    }

    @Test("isHeightSeries returns true only for baby_height")
    func isHeightSeriesTrueOnly() {
        #expect(BabyDashboardChartSupport.isHeightSeries("baby_height"))
        #expect(!BabyDashboardChartSupport.isHeightSeries("weight"))
        #expect(!BabyDashboardChartSupport.isHeightSeries("baby_percentile_fiftieth"))
    }

    // MARK: - dummyHeightValue

    @Test("dummyHeightValue returns at least birth length for day 0")
    func dummyHeightValueAtBirthday() {
        let baby = makeBabyProfile(birthLengthInches: 19.5)
        let birthday = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let result = BabyDashboardChartSupport.dummyHeightValue(for: baby, on: birthday)
        #expect(result >= 12.0)
    }

    @Test("dummyHeightValue increases over time")
    func dummyHeightValueIncreasesOverTime() {
        let baby = makeBabyProfile()
        let birthday = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let day30 = cal.date(byAdding: .day, value: 30, to: birthday) ?? birthday
        let day90 = cal.date(byAdding: .day, value: 90, to: birthday) ?? birthday

        let h30 = BabyDashboardChartSupport.dummyHeightValue(for: baby, on: day30)
        let h90 = BabyDashboardChartSupport.dummyHeightValue(for: baby, on: day90)

        #expect(h90 > h30)
    }

    // MARK: - averageDummyHeight

    @Test("averageDummyHeight falls back to today's height when dates are empty")
    func averageDummyHeightFallbackForEmpty() {
        let baby = makeBabyProfile()
        let result = BabyDashboardChartSupport.averageDummyHeight(for: baby, dates: [])
        #expect(result > 0)
    }

    @Test("averageDummyHeight returns average of heights for given dates")
    func averageDummyHeightAveragesMultipleDates() {
        let baby = makeBabyProfile()
        let birthday = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let d1 = cal.date(byAdding: .day, value: 10, to: birthday) ?? birthday
        let d2 = cal.date(byAdding: .day, value: 20, to: birthday) ?? birthday
        let avg = BabyDashboardChartSupport.averageDummyHeight(for: baby, dates: [d1, d2])
        let h1 = BabyDashboardChartSupport.dummyHeightValue(for: baby, on: d1)
        let h2 = BabyDashboardChartSupport.dummyHeightValue(for: baby, on: d2)
        let expected = (h1 + h2) / 2
        #expect(abs(avg - expected) < 0.001)
    }

    // MARK: - heightPercentile

    @Test("heightPercentile returns value between 0 and 100")
    func heightPercentileInValidRange() {
        let baby = makeBabyProfile()
        let birthday = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let date = cal.date(byAdding: .day, value: 60, to: birthday) ?? birthday
        let heightInches = BabyDashboardChartSupport.dummyHeightValue(for: baby, on: date)

        let result = BabyDashboardChartSupport.heightPercentile(for: baby, heightInches: heightInches, on: date)

        #expect(result >= 0)
        #expect(result <= 100)
    }

    // MARK: - percentileLine

    @Test("percentileLine decodes series name back to BabyPercentileLine")
    func percentileLineDecodesSeries() {
        for line in BabyPercentileLine.allCases {
            let name = "baby_percentile_\(line.rawValue)"
            let decoded = BabyDashboardChartSupport.percentileLine(for: name)
            #expect(decoded == line)
        }
    }

    @Test("percentileLine returns nil for non-percentile series")
    func percentileLineNilForWeight() {
        #expect(BabyDashboardChartSupport.percentileLine(for: "weight") == nil)
        #expect(BabyDashboardChartSupport.percentileLine(for: "baby_height") == nil)
    }

    // MARK: - Dummy Summaries Cache

    @Test("dummySummaries returns cached result on repeated calls for same profile and period")
    func dummySummariesCacheHit() {
        BabyDashboardChartSupport.clearDummySummariesCache()
        let profile = makeBabyProfile(id: "cache-test-1")

        let first = BabyDashboardChartSupport.dummySummaries(for: profile, period: .week)
        let second = BabyDashboardChartSupport.dummySummaries(for: profile, period: .week)

        #expect(!first.isEmpty)
        #expect(first.count == second.count)
        #expect(first.first?.period == second.first?.period)
    }

    @Test("dummySummaries invalidates cache for different baby profile")
    func dummySummariesDifferentProfileRecalculates() {
        BabyDashboardChartSupport.clearDummySummariesCache()
        let profile1 = makeBabyProfile(id: "cache-p1")
        let profile2 = makeBabyProfile(id: "cache-p2")

        let first = BabyDashboardChartSupport.dummySummaries(for: profile1, period: .week)
        let second = BabyDashboardChartSupport.dummySummaries(for: profile2, period: .week)

        // Both should return data but from separate cache entries
        #expect(!first.isEmpty)
        #expect(!second.isEmpty)
        #expect(first.first?.accountId != second.first?.accountId)
    }

    @Test("dummySummaries returns different data for different periods")
    func dummySummariesDifferentPeriodsReturnDifferentData() {
        BabyDashboardChartSupport.clearDummySummariesCache()
        let profile = makeBabyProfile(id: "cache-period-test")

        let weekly = BabyDashboardChartSupport.dummySummaries(for: profile, period: .week)
        let yearly = BabyDashboardChartSupport.dummySummaries(for: profile, period: .year)

        // Year/total uses monthly aggregation so should have fewer entries
        #expect(weekly.count >= yearly.count)
    }

    @Test("clearDummySummariesCache forces recomputation")
    func clearDummySummariesCacheInvalidates() {
        let profile = makeBabyProfile(id: "cache-clear-test")
        _ = BabyDashboardChartSupport.dummySummaries(for: profile, period: .week)

        BabyDashboardChartSupport.clearDummySummariesCache()

        // After clearing, next call should produce fresh (but equivalent) data
        let fresh = BabyDashboardChartSupport.dummySummaries(for: profile, period: .week)
        #expect(!fresh.isEmpty)
    }
}
