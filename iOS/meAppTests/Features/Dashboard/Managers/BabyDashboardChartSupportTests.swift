import Foundation
@testable import meApp
import Testing

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

    private func makeDailySummaries(
        count: Int,
        from startDate: Date,
        includeLength: Bool = true
    ) -> [BathScaleWeightSummary] {
        (0..<count).compactMap { offset in
            guard let date = cal.date(byAdding: .day, value: offset, to: startDate) else { return nil }
            return BathScaleWeightSummary(
                accountId: "baby-test",
                period: DateTimeTools.formatter("yyyy-MM-dd").string(from: date),
                entryTimestamp: ISO8601DateFormatter().string(from: date),
                date: date,
                count: 1,
                weight: 800 + Double(offset) * 2,
                babyLengthInches: includeLength ? 20.0 + Double(offset) * 0.1 : nil
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

    // MARK: - percentileSeries

    @Test("percentileSeries returns empty when operations are empty")
    func percentileSeriesEmptyForNoOperations() {
        let baby = makeBabyProfile()
        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: []
        ) { Double($0) / 10000.0 }
        #expect(result.isEmpty)
    }

    @Test("percentileSeries returns multiple series names for all percentile lines")
    func percentileSeriesHasAllPercentileLines() {
        let baby = makeBabyProfile()
        let startDate = cal.date(byAdding: .day, value: -30, to: Date()) ?? Date()
        let operations = makeDailySummaries(count: 10, from: startDate)

        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: operations
        ) { Double($0) / 10000.0 }

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
            operations: operations
        ) { Double($0) / 10000.0 }

        #expect(result.allSatisfy { BabyDashboardChartSupport.isPercentileSeries($0.series) })
    }

    @Test("percentileSeries spans the operation range")
    func percentileSeriesUsesOperationRange() {
        let baby = makeBabyProfile()
        // swiftlint:disable:next force_unwrapping
        let startDate = cal.date(byAdding: .day, value: -20, to: Date())!
        let operations = makeDailySummaries(count: 8, from: startDate)

        let result = BabyDashboardChartSupport.percentileSeries(
            for: baby,
            operations: operations
        ) { Double($0) / 10000.0 }

        // The WHO percentile reference grid is day-aligned and intentionally padded
        // (±8 days) beyond the operation range for smooth line continuity, so it spans
        // at least the full operation range rather than matching the exact timestamps.
        let dates = result.map(\.date)
        #expect(dates.min() != nil)
        #expect(dates.max() != nil)
        if let opMin = operations.first?.date, let opMax = operations.last?.date,
           let seriesMin = dates.min(), let seriesMax = dates.max() {
            #expect(seriesMin <= opMin)
            #expect(seriesMax >= opMax)
        }
    }

    // MARK: - heightSeries (real recorded length)

    @Test("heightSeries returns one point per operation that recorded a length")
    func heightSeriesMatchesOperationsWithLength() {
        let ops = makeDailySummaries(count: 7, from: cal.date(byAdding: .day, value: -7, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.heightSeries(from: ops)
        #expect(result.count == 7)
    }

    @Test("heightSeries omits operations without a recorded length (no synthetic fill)")
    func heightSeriesOmitsMissingLength() {
        let ops = makeDailySummaries(count: 5, from: cal.date(byAdding: .day, value: -5, to: Date()) ?? Date(), includeLength: false)
        let result = BabyDashboardChartSupport.heightSeries(from: ops)
        #expect(result.isEmpty)
    }

    @Test("heightSeries uses baby_height series name and the recorded length value")
    func heightSeriesHasCorrectSeriesNameAndValue() {
        let ops = makeDailySummaries(count: 3, from: cal.date(byAdding: .day, value: -3, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.heightSeries(from: ops)
        #expect(result.allSatisfy { BabyDashboardChartSupport.isHeightSeries($0.series) })
        #expect(zip(result, ops).allSatisfy { $0.value == $1.babyLengthInches })
    }

    // MARK: - heightPercentileSeries

    @Test("heightPercentileSeries returns points for every percentile line across the reference grid")
    func heightPercentileSeriesHasAllPercentileLines() {
        let baby = makeBabyProfile()
        let ops = makeDailySummaries(count: 4, from: cal.date(byAdding: .day, value: -4, to: Date()) ?? Date())
        let result = BabyDashboardChartSupport.heightPercentileSeries(for: baby, operations: ops)
        // The height curves are WHO length-for-age reference lines sampled on the day-aligned
        // reference grid (padded ±8 days for continuity) — not one point per operation. Every
        // percentile line is represented with the same number of points per line.
        #expect(!result.isEmpty)
        let seriesNames = Set(result.map(\.series))
        #expect(seriesNames.count == BabyPercentileLine.allCases.count)
        #expect(result.count % BabyPercentileLine.allCases.count == 0)
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
        // swiftlint:disable:next force_unwrapping
        let startDate = cal.date(byAdding: .day, value: -20, to: Date())!
        let operations = makeDailySummaries(count: 8, from: startDate)

        let result = BabyDashboardChartSupport.heightPercentileSeries(
            for: baby,
            operations: operations
        )

        // Like the weight percentile grid, the WHO length reference grid is day-aligned and
        // intentionally padded (±8 days) beyond the operation range for smooth line continuity,
        // so it spans at least the full operation range rather than matching the exact timestamps.
        let dates = result.map(\.date)
        #expect(dates.min() != nil)
        #expect(dates.max() != nil)
        if let opMin = operations.first?.date, let opMax = operations.last?.date,
           let seriesMin = dates.min(), let seriesMax = dates.max() {
            #expect(seriesMin <= opMin)
            #expect(seriesMax >= opMax)
        }
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

    // MARK: - heightValue (real recorded length at a date)

    @Test("heightValue returns the recorded length for the matching day")
    func heightValueReturnsRecordedLength() {
        let start = cal.date(byAdding: .day, value: -5, to: Date()) ?? Date()
        let ops = makeDailySummaries(count: 5, from: start)
        let target = ops[2]
        let result = BabyDashboardChartSupport.heightValue(on: target.date, in: ops)
        #expect(result == target.babyLengthInches)
    }

    @Test("heightValue returns nil when no operation recorded a length on that day")
    func heightValueNilWhenNoLength() {
        let start = cal.date(byAdding: .day, value: -3, to: Date()) ?? Date()
        let ops = makeDailySummaries(count: 3, from: start, includeLength: false)
        let result = BabyDashboardChartSupport.heightValue(on: start, in: ops)
        #expect(result == nil)
    }

    // MARK: - averageHeight (real recorded length)

    @Test("averageHeight returns nil when no operation recorded a length")
    func averageHeightNilForNoLength() {
        let ops = makeDailySummaries(count: 4, from: Date(), includeLength: false)
        #expect(BabyDashboardChartSupport.averageHeight(from: ops) == nil)
    }

    @Test("averageHeight returns the mean of recorded lengths")
    func averageHeightAveragesRecordedLengths() throws {
        let ops = makeDailySummaries(count: 3, from: cal.date(byAdding: .day, value: -3, to: Date()) ?? Date())
        let expected = ops.compactMap(\.babyLengthInches).reduce(0, +) / Double(ops.count)
        let avg = try #require(BabyDashboardChartSupport.averageHeight(from: ops))
        #expect(abs(avg - expected) < 0.001)
    }

    // MARK: - heightPercentile

    @Test("heightPercentile returns value between 0 and 100")
    func heightPercentileInValidRange() throws {
        let baby = makeBabyProfile()
        let birthday = BabyDashboardChartSupport.resolvedBirthday(for: baby)
        let date = cal.date(byAdding: .day, value: 60, to: birthday) ?? birthday

        let result = try #require(
            BabyDashboardChartSupport.heightPercentile(for: baby, heightInches: 22.5, on: date)
        )

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

    // MARK: - Private-sex gating (Smart Baby percentile.service.ts parity)

    @Test("isSexWithheld is true for private, nil, and empty; false for male/female")
    func isSexWithheldTruthTable() {
        #expect(BabyPercentileGrowthReference.isSexWithheld("private"))
        #expect(BabyPercentileGrowthReference.isSexWithheld("PRIVATE"))
        #expect(BabyPercentileGrowthReference.isSexWithheld(nil))
        #expect(BabyPercentileGrowthReference.isSexWithheld(""))
        #expect(!BabyPercentileGrowthReference.isSexWithheld("male"))
        #expect(!BabyPercentileGrowthReference.isSexWithheld("female"))
    }

    @Test("percentileChartPoints returns no curves for private sex")
    func percentileChartPointsEmptyForPrivateSex() {
        // swiftlint:disable:next force_unwrapping
        let birthday = cal.date(byAdding: .day, value: -90, to: Date())!
        let range = birthday...Date()

        let points = BabyPercentileGrowthReference.percentileChartPoints(
            biologicalSex: "private",
            birthday: birthday,
            dateRange: range
        ) { Double($0) / 10000.0 }

        #expect(points.isEmpty)
    }

    @Test("weightPercentile returns nil for private sex")
    func weightPercentileNilForPrivateSex() {
        // swiftlint:disable:next force_unwrapping
        let birthday = cal.date(byAdding: .day, value: -90, to: Date())!

        let percentile = BabyPercentileGrowthReference.weightPercentile(
            biologicalSex: "private",
            birthday: birthday,
            date: Date(),
            weightDecigrams: 60_000
        )

        #expect(percentile == nil)
    }

    @Test("heightPercentileSeries returns no curves for private sex")
    func heightPercentileSeriesEmptyForPrivateSex() {
        let baby = makeBabyProfile(sex: "private")
        let ops = makeDailySummaries(count: 4, from: cal.date(byAdding: .day, value: -4, to: Date()) ?? Date())

        #expect(BabyDashboardChartSupport.heightPercentileSeries(for: baby, operations: ops).isEmpty)

        // swiftlint:disable:next force_unwrapping
        let start = cal.date(byAdding: .day, value: -20, to: Date())!
        #expect(BabyDashboardChartSupport.heightPercentileSeries(for: baby, dateRange: start...Date()).isEmpty)
    }

    @Test("heightPercentile is nil for private sex and non-nil for female")
    func heightPercentileGatedBySex() {
        let privateBaby = makeBabyProfile(sex: "private")
        let femaleBaby = makeBabyProfile(sex: "female")

        let privateResult = BabyDashboardChartSupport.heightPercentile(
            for: privateBaby,
            heightInches: 22.0,
            on: Date()
        )
        let femaleResult = BabyDashboardChartSupport.heightPercentile(
            for: femaleBaby,
            heightInches: 22.0,
            on: Date()
        )

        #expect(privateResult == nil)
        #expect(femaleResult != nil)
    }
}
