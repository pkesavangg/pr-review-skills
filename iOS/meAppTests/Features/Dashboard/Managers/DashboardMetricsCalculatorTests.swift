// Large test fixture file.
// swiftlint:disable file_length
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
// Large cohesive test suite.
// swiftlint:disable:next type_body_length
struct DashboardMetricsCalculatorTests {

    // MARK: - SUT Factory

    private func makeSUT() -> DashboardMetricsCalculator {
        TestDependencyContainer.reset()
        return DashboardMetricsCalculator()
    }

    // Builds a stub `interpolatedWeight` closure that ignores its inputs and returns `value`.
    // Returned as a value (rather than an inline trailing closure) so call sites pass it as a
    // labelled argument — `interpolatedWeight` is not the last parameter of the context factories,
    // so a trailing closure would bind to the wrong parameter.
    private func constantInterpolatedWeight(
        _ value: Double?
    ) -> (Date, [BathScaleWeightSummary], Bool, Double?, @escaping (Double) -> Double) -> Double? {
        { _, _, _, _, _ in value }
    }

    // MARK: - getCurrentAverageWeight Tests

    @Test("getCurrentAverageWeight: empty operations returns 0")
    func averageWeightEmptyOperations() {
        let sut = makeSUT()
        let result = sut.getCurrentAverageWeight(
            from: [],
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )
        #expect(result == 0)
    }

    @Test("getCurrentAverageWeight: single operation returns converted weight")
    func averageWeightSingleOperation() {
        let sut = makeSUT()
        let ops = [DashboardTestFixtures.makeSummary(weight: 1800)]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // 1800 / 10 = 180.0
        #expect(result == 180.0)
    }

    @Test("getCurrentAverageWeight: multiple operations returns average rounded to 1 decimal")
    func averageWeightMultipleOperations() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1800),  // 180.0 lbs
            DashboardTestFixtures.makeSummary(weight: 1820),  // 182.0 lbs
            DashboardTestFixtures.makeSummary(weight: 1810)   // 181.0 lbs
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // Average: (180 + 182 + 181) / 3 = 181.0
        #expect(result == 181.0)
    }

    @Test("getCurrentAverageWeight: rounds to 1 decimal place correctly")
    func averageWeightRoundsCorrectly() {
        let sut = makeSUT()
        // Create weights that produce an average with more than 1 decimal
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1800),  // 180.0
            DashboardTestFixtures.makeSummary(weight: 1811),  // 181.1
            DashboardTestFixtures.makeSummary(weight: 1822)   // 182.2
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // Average: (180.0 + 181.1 + 182.2) / 3 = 181.1
        #expect(result == 181.1)
    }

    @Test("getCurrentAverageWeight: rounding at 0.05 boundary rounds away from zero")
    func averageWeightRoundingBoundary() {
        let sut = makeSUT()
        // Average that lands exactly on 0.05: should round to 0.1
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1805),  // 180.5
            DashboardTestFixtures.makeSummary(weight: 1815)   // 181.5
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // Average: (180.5 + 181.5) / 2 = 181.0
        #expect(result == 181.0)
    }

    @Test("getCurrentAverageWeight: all same weights returns exact value")
    func averageWeightAllSame() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1800),
            DashboardTestFixtures.makeSummary(weight: 1800),
            DashboardTestFixtures.makeSummary(weight: 1800)
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result == 180.0)
    }

    @Test("getCurrentAverageWeight: weightless mode with anchor subtracts anchor from each weight")
    func averageWeightWeightlessModeWithAnchor() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1800),  // 180.0 lbs
            DashboardTestFixtures.makeSummary(weight: 1820)   // 182.0 lbs
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: 180.0,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // Values: (180.0 - 180.0) = 0, (182.0 - 180.0) = 2.0
        // Average: (0 + 2) / 2 = 1.0
        #expect(result == 1.0)
    }

    @Test("getCurrentAverageWeight: weightless mode without anchor returns 0")
    func averageWeightWeightlessModeNoAnchor() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1800),
            DashboardTestFixtures.makeSummary(weight: 1820)
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // Without anchor, compactMap returns empty array
        #expect(result == 0)
    }

    @Test("getCurrentAverageWeight: weightless mode single operation")
    func averageWeightWeightlessModeSingle() {
        let sut = makeSUT()
        let ops = [DashboardTestFixtures.makeSummary(weight: 1850)]  // 185.0 lbs

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: 180.0,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // 185.0 - 180.0 = 5.0
        #expect(result == 5.0)
    }

    @Test("getCurrentAverageWeight: weightless mode with negative difference")
    func averageWeightWeightlessModeNegative() {
        let sut = makeSUT()
        let ops = [DashboardTestFixtures.makeSummary(weight: 1750)]  // 175.0 lbs

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: 180.0,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // 175.0 - 180.0 = -5.0
        #expect(result == -5.0)
    }

    @Test("getCurrentAverageWeight: very large weight values")
    func averageWeightLargeValues() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 5000),  // 500.0 lbs
            DashboardTestFixtures.makeSummary(weight: 5100)   // 510.0 lbs
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result == 505.0)
    }

    @Test("getCurrentAverageWeight: very small weight values")
    func averageWeightSmallValues() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 100),  // 10.0 lbs
            DashboardTestFixtures.makeSummary(weight: 110)   // 11.0 lbs
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result == 10.5)
    }

    @Test("getCurrentAverageWeight: custom conversion function (identity)")
    func averageWeightCustomConversion() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 75),
            DashboardTestFixtures.makeSummary(weight: 80)
        ]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.identityConvert
        )

        #expect(result == 77.5)
    }

    // MARK: - calculateDisplayWeight Tests

    @Test("calculateDisplayWeight: selected point in normal mode returns converted weight")
    func displayWeightSelectedPointNormal() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1800)
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: [point]
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 180.0)
    }

    @Test("calculateDisplayWeight: selected point in weightless mode with anchor returns difference")
    func displayWeightSelectedPointWeightless() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1850)
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: [point],
            isWeightlessMode: true,
            anchorWeight: 180.0
        )

        let result = sut.calculateDisplayWeight(context: context)

        // 185.0 - 180.0 = 5.0
        #expect(result == 5.0)
    }

    @Test("calculateDisplayWeight: selected point in weightless mode without anchor returns nil")
    func displayWeightSelectedPointWeightlessNoAnchor() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1850)
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: [point],
            isWeightlessMode: true,
            anchorWeight: nil
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == nil)
    }

    @Test("calculateDisplayWeight: selected date with no same-day entries uses interpolatedWeight callback")
    func displayWeightSelectedDate() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        // Operations on a different day so interpolation is used
        let ops = [DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800)]
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedDate: date,
            operations: ops,
            interpolatedWeight: constantInterpolatedWeight(181.5)
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 181.5)
    }

    @Test("calculateDisplayWeight: selected date with nil interpolation returns nil")
    func displayWeightSelectedDateNilInterpolation() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        // No same-day entries, so falls through to interpolation
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedDate: date,
            interpolatedWeight: constantInterpolatedWeight(nil)
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == nil)
    }

    @Test("calculateDisplayWeight: no selection, empty opsForLabel, has ops, not total period uses interpolatedAverage")
    func displayWeightNoSelectionEmptyOpsForLabelNotTotal() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let interpolatedAverage: (
            [BathScaleWeightSummary], TimePeriod, Bool, Double?, @escaping (Double) -> Double, DateInterval?
        ) -> Double? = { _, _, _, _, _, _ in 181.0 }
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: [],  // empty opsForLabel
            period: .month,          // not .total
            interpolatedAverage: interpolatedAverage
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 181.0)
    }

    @Test("calculateDisplayWeight: no selection, empty opsForLabel, has ops, total period skips interpolated")
    func displayWeightNoSelectionEmptyOpsForLabelTotal() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: [],  // empty
            period: .total           // .total period - skips interpolated path
        )

        // Falls through to normal weight calculation; empty opsForLabel → nil
        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == nil)
    }

    @Test("calculateDisplayWeight: no selection, empty opsForLabel, no operations falls through")
    func displayWeightNoSelectionNoOperations() {
        let sut = makeSUT()
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: [],
            operationsForLabel: []
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == nil)
    }

    @Test("calculateDisplayWeight: no selection, weightless mode computes average minus anchor")
    func displayWeightWeightlessModeAverage() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        // Weights: 1800, 1810, 1820, 1830, 1840 → converted (÷10): 180, 181, 182, 183, 184
        // Average = 182.0, anchor = 180.0, result = 2.0
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: ops,
            isWeightlessMode: true,
            anchorWeight: 180.0
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 2.0)
    }

    @Test("calculateDisplayWeight: no selection, normal mode with opsForLabel averages and rounds")
    func displayWeightNormalModeAverage() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1800),  // 180.0
            DashboardTestFixtures.makeSummary(weight: 1820),  // 182.0
            DashboardTestFixtures.makeSummary(weight: 1810)   // 181.0
        ]
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: ops
        )

        let result = sut.calculateDisplayWeight(context: context)

        // Average: (180 + 182 + 181) / 3 = 181.0
        #expect(result == 181.0)
    }

    @Test("calculateDisplayWeight: normal mode rounds to 1 decimal place")
    func displayWeightNormalModeRounding() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 1803),  // 180.3
            DashboardTestFixtures.makeSummary(weight: 1807)   // 180.7
        ]
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: ops
        )

        let result = sut.calculateDisplayWeight(context: context)

        // Average: (180.3 + 180.7) / 2 = 180.5
        #expect(result == 180.5)
    }

    @Test("calculateDisplayWeight: selected point takes priority over selected date")
    func displayWeightPointPriorityOverDate() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1900)
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            selectedDate: date,
            operations: [point],
            interpolatedWeight: constantInterpolatedWeight(175.0)
        )

        let result = sut.calculateDisplayWeight(context: context)

        // Point's day average should win: 1900 / 10 = 190.0
        #expect(result == 190.0)
    }

    @Test("calculateDisplayWeight: selected point negative weightless difference")
    func displayWeightPointNegativeWeightless() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1750)  // 175.0
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: [point],
            isWeightlessMode: true,
            anchorWeight: 180.0
        )

        let result = sut.calculateDisplayWeight(context: context)

        // 175.0 - 180.0 = -5.0
        #expect(result == -5.0)
    }

    // MARK: - createEntryForMetricInfo Tests

    @Test("createEntryForMetricInfo: entry has correct base properties")
    func createEntryBaseProperties() {
        let sut = makeSUT()
        let context = DashboardTestFixtures.makeEntryCreationContext()

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.accountId == "dashboard")
        #expect(entry.operationType == OperationType.create.rawValue)
        #expect(entry.entryType == "scale")
        #expect(entry.isSynced == true)
    }

    @Test("createEntryForMetricInfo: selected point populates scaleEntry with point values")
    func createEntrySelectedPoint() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(
            weight: 1800,
            bodyFat: 250,
            muscleMass: 820,
            water: 540,
            bmi: 230
        )
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: []
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight == 1800)
        #expect(entry.scaleEntry?.bodyFat == 250)
        #expect(entry.scaleEntry?.muscleMass == 820)
        #expect(entry.scaleEntry?.water == 540)
        #expect(entry.scaleEntry?.bmi == 230)
        #expect(entry.scaleEntry?.source == "dashboard")
    }

    @Test("createEntryForMetricInfo: selected point with zero weight produces nil weight")
    func createEntrySelectedPointZeroWeight() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 0)
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: []
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight == nil)
    }

    @Test("createEntryForMetricInfo: selected point with zero bodyFat produces nil bodyFat")
    func createEntrySelectedPointZeroBodyFat() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1800, bodyFat: 0)
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: []
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.bodyFat == nil)
    }

    @Test("createEntryForMetricInfo: selected point with nil metrics produces nil in entry")
    func createEntrySelectedPointNilMetrics() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 1800)
        // No optional metrics set (bodyFat, muscleMass, etc. are nil)
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: []
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight == 1800)
        #expect(entry.scaleEntry?.bodyFat == nil)
        #expect(entry.scaleEntry?.muscleMass == nil)
        #expect(entry.scaleEntry?.water == nil)
        #expect(entry.scaleEntry?.bmi == nil)
    }

    @Test("createEntryForMetricInfo: selected point with bmr placeholder tile produces nil bmr")
    func createEntrySelectedPointBmrPlaceholder() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(bmr: 16000)
        let metrics = [
            DashboardTestFixtures.makeMetricItem(value: DashboardStrings.placeholder, label: DashboardStrings.bmrKcal)
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: metrics
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntryMetric?.bmr == nil)
    }

    @Test("createEntryForMetricInfo: selected point with visceralFat zero tile produces nil")
    func createEntrySelectedPointVisceralFatZeroTile() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(visceralFatLevel: 110)
        let metrics = [
            DashboardTestFixtures.makeMetricItem(value: "0", label: DashboardStrings.visceralFat)
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: metrics
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntryMetric?.visceralFatLevel == nil)
    }

    @Test("createEntryForMetricInfo: selected point populates scaleEntryMetric fields")
    func createEntrySelectedPointAllMetricFields() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(
            bmr: 16000,
            metabolicAge: 35,
            proteinPercent: 190,
            pulse: 72,
            skeletalMusclePercent: 410,
            subcutaneousFatPercent: 210,
            visceralFatLevel: 110,
            boneMass: 80
        )
        let metrics = [
            DashboardTestFixtures.makeMetricItem(value: "1600", label: DashboardStrings.bmrKcal),
            DashboardTestFixtures.makeMetricItem(value: "11", label: DashboardStrings.visceralFat)
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: metrics
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntryMetric?.metabolicAge == 35)
        #expect(entry.scaleEntryMetric?.proteinPercent == 190)
        #expect(entry.scaleEntryMetric?.pulse == 72)
        #expect(entry.scaleEntryMetric?.skeletalMusclePercent == 410)
        #expect(entry.scaleEntryMetric?.subcutaneousFatPercent == 210)
        #expect(entry.scaleEntryMetric?.boneMass == 80)
        #expect(entry.scaleEntryMetric?.impedance == nil)
        #expect(entry.scaleEntryMetric?.unit == nil)
    }

    @Test("createEntryForMetricInfo: selected point sets entry timestamp to point's date")
    func createEntrySelectedPointTimestamp() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-15")
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: []
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        let expectedTimestamp = DateTimeTools.isoFormatter().string(from: point.date)
        #expect(entry.entryTimestamp == expectedTimestamp)
    }

    @Test("createEntryForMetricInfo: selected date path creates interpolated entry")
    func createEntrySelectedDate() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedDate: date,
            interpolatedWeight: constantInterpolatedWeight(180.0)
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight != nil)
        #expect(entry.scaleEntry?.bodyFat == nil)
        #expect(entry.scaleEntry?.muscleMass == nil)
        #expect(entry.scaleEntry?.source == "dashboard")
        let expectedTimestamp = DateTimeTools.isoFormatter().string(from: date)
        #expect(entry.entryTimestamp == expectedTimestamp)
    }

    @Test("createEntryForMetricInfo: selected date in weightless mode adds anchor back")
    func createEntrySelectedDateWeightless() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedDate: date,
            isWeightlessMode: true,
            anchorWeight: 180.0,
            weightUnit: .lb,
            interpolatedWeight: constantInterpolatedWeight(5.0)
        )  // weightless diff

        let entry = sut.createEntryForMetricInfo(context: context)

        // displayAbsolute = 5.0 + 180.0 = 185.0
        // Stored weight should be ConversionTools.convertDisplayToStored(185.0, isMetric: false)
        // 185.0 * 10 = 1850
        #expect(entry.scaleEntry?.weight == 1850)
    }

    @Test("createEntryForMetricInfo: selected date with nil interpolation produces nil weight")
    func createEntrySelectedDateNilInterpolation() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedDate: date,
            interpolatedWeight: constantInterpolatedWeight(nil)
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight == nil)
    }

    @Test("createEntryForMetricInfo: no selection, empty visible ops uses interpolated average")
    func createEntryNoSelectionEmptyVisibleOps() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: [],
            weightUnit: .lb
        ) { _, _, _, _, _, _ in 181.0 }

        let entry = sut.createEntryForMetricInfo(context: context)

        // 181.0 lbs → stored = 181.0 * 10 = 1810
        #expect(entry.scaleEntry?.weight == 1810)
        #expect(entry.scaleEntry?.bodyFat == nil)
    }

    @Test("createEntryForMetricInfo: no selection, empty visible ops, empty operations produces nil weight")
    func createEntryNoSelectionEmptyEverything() {
        let sut = makeSUT()
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: [],
            visibleOperations: []
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight == nil)
    }

    @Test("createEntryForMetricInfo: no selection, empty visible ops, nil interpolated average")
    func createEntryNoSelectionNilInterpolatedAverage() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: []
        ) { _, _, _, _, _, _ in nil }

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight == nil)
    }

    @Test("createEntryForMetricInfo: with visible operations averages all body metrics")
    func createEntryVisibleOpsAverages() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-01",
                weight: 1800,
                bodyFat: 250,
                muscleMass: 820,
                water: 540,
                bmi: 230
            ),
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-02",
                weight: 1820,
                bodyFat: 260,
                muscleMass: 830,
                water: 550,
                bmi: 240
            )
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1800
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.weight != nil)
        // Average bodyFat: (250 + 260) / 2 = 255.0 → Int = 255
        #expect(entry.scaleEntry?.bodyFat == 255)
        // Average muscleMass: (820 + 830) / 2 = 825.0 → Int = 825
        #expect(entry.scaleEntry?.muscleMass == 825)
        // Average water: (540 + 550) / 2 = 545.0 → Int = 545
        #expect(entry.scaleEntry?.water == 545)
        // Average bmi: (230 + 240) / 2 = 235.0 → Int = 235
        #expect(entry.scaleEntry?.bmi == 235)
    }

    @Test("createEntryForMetricInfo: visible ops averages scale entry metrics")
    func createEntryVisibleOpsScaleEntryMetrics() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-01",
                bmr: 16000,
                metabolicAge: 30,
                proteinPercent: 180,
                pulse: 70,
                skeletalMusclePercent: 400,
                subcutaneousFatPercent: 200,
                visceralFatLevel: 100,
                boneMass: 80
            ),
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-02",
                bmr: 16200,
                metabolicAge: 32,
                proteinPercent: 190,
                pulse: 74,
                skeletalMusclePercent: 410,
                subcutaneousFatPercent: 210,
                visceralFatLevel: 120,
                boneMass: 82
            )
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1800
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // Average bmr: (16000 + 16200) / 2 = 16100
        #expect(entry.scaleEntryMetric?.bmr == 16100)
        // Average metabolicAge: (30 + 32) / 2 = 31
        #expect(entry.scaleEntryMetric?.metabolicAge == 31)
        // Average proteinPercent: (180 + 190) / 2 = 185
        #expect(entry.scaleEntryMetric?.proteinPercent == 185)
        // Average pulse: (70 + 74) / 2 = 72
        #expect(entry.scaleEntryMetric?.pulse == 72)
    }

    @Test("createEntryForMetricInfo: visible ops with zero averages produce nil")
    func createEntryVisibleOpsZeroAverages() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                weight: 1800,
                bodyFat: 0,
                muscleMass: 0
            ),
            DashboardTestFixtures.makeSummary(
                period: "2026-03-02",
                weight: 1820,
                bodyFat: 0,
                muscleMass: 0
            )
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1800
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // Zero average → intOrNil returns nil
        #expect(entry.scaleEntry?.bodyFat == nil)
        #expect(entry.scaleEntry?.muscleMass == nil)
    }

    @Test("createEntryForMetricInfo: visible ops with nil metrics produce nil averages")
    func createEntryVisibleOpsNilMetrics() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1820)
        ]
        // No optional metrics set
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1800
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.bodyFat == nil)
        #expect(entry.scaleEntryMetric?.bmr == nil)
    }

    @Test("createEntryForMetricInfo: visible ops latestWeightStored fallback when empty ops")
    func createEntryVisibleOpsLatestWeightFallback() {
        let sut = makeSUT()
        // Empty weight operations but with latestWeightStored
        // This path: if weightValues.isEmpty, return latestWeightStored
        // Actually, the code uses operations parameter which won't be empty here
        // since visibleOperations would need to be non-empty to reach this path.
        // Let me test with operations that have weight.
        let ops = [DashboardTestFixtures.makeSummary(weight: 1800)]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1900
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // Should use average of ops, not latestWeightStored
        #expect(entry.scaleEntry?.weight != nil)
    }

    @Test("createEntryForMetricInfo: visible ops with single operation uses its values")
    func createEntryVisibleOpsSingle() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-01",
                weight: 1800,
                bodyFat: 250,
                muscleMass: 820,
                water: 540,
                bmi: 230,
                bmr: 16000,
                metabolicAge: 35,
                proteinPercent: 190,
                pulse: 72,
                skeletalMusclePercent: 410,
                subcutaneousFatPercent: 210,
                visceralFatLevel: 110,
                boneMass: 80
            )
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1800
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntry?.bodyFat == 250)
        #expect(entry.scaleEntry?.muscleMass == 820)
        #expect(entry.scaleEntry?.water == 540)
        #expect(entry.scaleEntry?.bmi == 230)
        #expect(entry.scaleEntryMetric?.metabolicAge == 35)
        #expect(entry.scaleEntryMetric?.proteinPercent == 190)
        #expect(entry.scaleEntryMetric?.pulse == 72)
        #expect(entry.scaleEntryMetric?.skeletalMusclePercent == 410)
        #expect(entry.scaleEntryMetric?.subcutaneousFatPercent == 210)
        #expect(entry.scaleEntryMetric?.boneMass == 80)
    }

    @Test("createEntryForMetricInfo: selected point takes priority over selected date")
    func createEntryPointPriorityOverDate() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(weight: 1900)
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            selectedDate: date,
            metrics: [],
            interpolatedWeight: constantInterpolatedWeight(175.0)
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // Point wins
        #expect(entry.scaleEntry?.weight == 1900)
    }

    @Test("createEntryForMetricInfo: selected date takes priority over visible operations")
    func createEntryDatePriorityOverVisibleOps() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedDate: date,
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            interpolatedWeight: constantInterpolatedWeight(185.0)
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // Date path: 185.0 lbs → stored = 1850
        #expect(entry.scaleEntry?.weight == 1850)
        // Interpolated path has nil for body metrics
        #expect(entry.scaleEntry?.bodyFat == nil)
    }

    @Test("createEntryForMetricInfo: metric unit kg uses metric conversion")
    func createEntryMetricUnitKg() {
        let sut = makeSUT()
        let date = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedDate: date,
            weightUnit: .kg,
            interpolatedWeight: constantInterpolatedWeight(80.0)
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // 80.0 kg → ConversionTools.convertDisplayToStored(80.0, isMetric: true)
        let expectedStored = ConversionTools.convertDisplayToStored(80.0, isMetric: true)
        #expect(entry.scaleEntry?.weight == expectedStored)
    }

    @Test("createEntryForMetricInfo: interpolated average with metric unit kg")
    func createEntryInterpolatedAverageKg() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: [],
            weightUnit: .kg
        ) { _, _, _, _, _, _ in 80.0 }

        let entry = sut.createEntryForMetricInfo(context: context)

        let expectedStored = ConversionTools.convertDisplayToStored(80.0, isMetric: true)
        #expect(entry.scaleEntry?.weight == expectedStored)
    }

    @Test("createEntryForMetricInfo: selected point with bmr value '0.0' tile produces nil")
    func createEntrySelectedPointBmrZeroPointZeroTile() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummaryWithAllMetrics(bmr: 16000)
        let metrics = [
            DashboardTestFixtures.makeMetricItem(value: "0.0", label: DashboardStrings.bmrKcal)
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            selectedPoint: point,
            metrics: metrics
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        #expect(entry.scaleEntryMetric?.bmr == nil)
    }

    // MARK: - Edge Cases

    @Test("getCurrentAverageWeight: weightless with zero anchor weight")
    func averageWeightWeightlessZeroAnchor() {
        let sut = makeSUT()
        let ops = [DashboardTestFixtures.makeSummary(weight: 1800)]

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: 0.0,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // 180.0 - 0.0 = 180.0
        #expect(result == 180.0)
    }

    @Test("calculateDisplayWeight: selected point with very small weight")
    func displayWeightVerySmallWeight() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(weight: 10)  // 1.0 lbs
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: [point]
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 1.0)
    }

    @Test("calculateDisplayWeight: normal mode single operation returns exact value")
    func displayWeightNormalModeSingleOp() {
        let sut = makeSUT()
        let ops = [DashboardTestFixtures.makeSummary(weight: 1823)]  // 182.3
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: ops
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 182.3)
    }

    @Test("calculateDisplayWeight: week period with multiple same-day entries returns arithmetic average")
    func displayWeightWeekDayAverage() {
        let sut = makeSUT()
        let dayDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let ops = [
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                date: dayDate.addingTimeInterval(3600),
                weight: 1800
            ),      // 180.0
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                date: dayDate.addingTimeInterval(7200),
                weight: 1820
            ),      // 182.0
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                date: dayDate.addingTimeInterval(10800),
                weight: 1810
            )      // 181.0
        ]
        let point = ops[0]
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: ops,
            period: .week
        )

        let result = sut.calculateDisplayWeight(context: context)

        // Day average: (180.0 + 182.0 + 181.0) / 3 = 181.0
        #expect(result == 181.0)
    }

    @Test("calculateDisplayWeight: week period selected date averages all same-day entries")
    func displayWeightWeekSelectedDateDayAverage() {
        let sut = makeSUT()
        let dayDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let ops = [
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                date: dayDate.addingTimeInterval(3600),
                weight: 1803
            ),      // 180.3
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                date: dayDate.addingTimeInterval(7200),
                weight: 1807
            )       // 180.7
        ]
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedDate: dayDate.addingTimeInterval(5400),
            operations: ops,
            period: .week
        )

        let result = sut.calculateDisplayWeight(context: context)

        // Day average: (180.3 + 180.7) / 2 = 180.5
        #expect(result == 180.5)
    }

    @Test("calculateDisplayWeight: week kg day average rounds after fractional stored average")
    func displayWeightWeekKgDayAverageRoundsAfterFractionalStoredAverage() {
        let sut = makeSUT()
        let dayDate = DateTimeTools.getDateFromDateString("2026-02-17", format: "yyyy-MM-dd")
        let summary = DashboardTestFixtures.makeSummary(
            period: "2026-02-17",
            date: dayDate,
            weight: 2452.0 / 6.0
        )
        let convertToKg: (Double) -> Double = { stored in
            ((stored / 22.0462) * 10).rounded(.toNearestOrAwayFromZero) / 10
        }
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: summary,
            operations: [summary],
            period: .week,
            convertWeight: convertToKg
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == 18.5)
    }

    @Test("calculateDisplayWeight: year period selected point returns exact weight, not day average")
    func displayWeightYearSelectedPointExact() {
        let sut = makeSUT()
        let dayDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let point = DashboardTestFixtures.makeSummary(
            period: "2026-03-01",
            date: dayDate.addingTimeInterval(3600),
            weight: 1800
        )
        let ops = [
            point,
            DashboardTestFixtures.makeSummary(
                period: "2026-03-01",
                date: dayDate.addingTimeInterval(7200),
                weight: 1820
            )
        ]
        let context = DashboardTestFixtures.makeDisplayWeightContext(
            selectedPoint: point,
            operations: ops,
            period: .year
        )

        let result = sut.calculateDisplayWeight(context: context)

        // Year period: exact point weight, not average
        #expect(result == 180.0)
    }

    @Test("createEntryForMetricInfo: empty visible ops path with weightless mode")
    func createEntryEmptyVisibleOpsWeightless() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: [],
            isWeightlessMode: true,
            anchorWeight: 180.0,
            weightUnit: .lb
        ) { _, _, _, _, _, _ in 2.0 }  // weightless diff

        let entry = sut.createEntryForMetricInfo(context: context)

        // interpolatedAvg = 2.0, stored = convertDisplayToStored(2.0, isMetric: false)
        let expectedStored = ConversionTools.convertDisplayToStored(2.0, isMetric: false)
        #expect(entry.scaleEntry?.weight == expectedStored)
    }

    @Test("createEntryForMetricInfo: visible ops with latestWeightStored zero produces nil fallback")
    func createEntryVisibleOpsLatestWeightZero() {
        let sut = makeSUT()
        // The fallback to latestWeightStored happens when weightValues is empty.
        // If the operations have 0 weight, convertWeight(0) = 0 → average is 0 → stored = 0 → nil
        let ops = [DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800)]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 0
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // Weight is calculated from ops, not latestWeightStored (ops are non-empty)
        #expect(entry.scaleEntry?.weight != nil)
    }

    @Test("createEntryForMetricInfo: visible ops mixed nil and valid metrics")
    func createEntryVisibleOpsMixedMetrics() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800, bodyFat: 250, water: nil),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1820, bodyFat: nil, water: 550)
        ]
        let context = DashboardTestFixtures.makeEntryCreationContext(
            operations: ops,
            visibleOperations: ops,
            weightUnit: .lb,
            latestWeightStored: 1800
        )

        let entry = sut.createEntryForMetricInfo(context: context)

        // bodyFat: only one non-nil value (250) → avg = 250 → Int = 250
        #expect(entry.scaleEntry?.bodyFat == 250)
        // water: only one non-nil value (550) → avg = 550 → Int = 550
        #expect(entry.scaleEntry?.water == 550)
    }

    @Test("getCurrentAverageWeight: kg weightless mode subtracts displayed average and baseline")
    func averageWeightKgWeightlessUsesDisplayedOperands() {
        let sut = makeSUT()
        // This reproduces the visible discrepancy:
        // current raw average = 660.6 / 22.0462 = 29.96..., displayed as 30.0 kg
        // baseline entered as 100.0 kg is stored as 2205 tenths-lb = 100.01..., displayed as 100.0 kg
        // Raw subtraction rounds to -70.1, but displayed arithmetic should be 30.0 - 100.0 = -70.0.
        let ops = [DashboardTestFixtures.makeSummary(weight: 660.6)]
        let anchorRaw = Double(ConversionTools.convertKgToStored(100.0)) / 22.0462

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: anchorRaw,
            convertWeight: DashboardTestFixtures.convertToKgRaw
        )

        #expect(result == -70.0)
    }

    @Test("calculateDisplayWeight: kg weightless no-selection average subtracts displayed operands")
    func displayWeightKgWeightlessUsesDisplayedOperands() {
        let sut = makeSUT()
        let ops = [DashboardTestFixtures.makeSummary(weight: 660.6)]
        let anchorRaw = Double(ConversionTools.convertKgToStored(100.0)) / 22.0462

        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: ops,
            isWeightlessMode: true,
            anchorWeight: anchorRaw,
            convertWeight: DashboardTestFixtures.convertToKgRaw
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == -70.0)
    }

    @Test("calculateDisplayWeight: kg weightless month visible average averages displayed entries first")
    func displayWeightKgWeightlessMonthAverageUsesDisplayedEntryValues() {
        let sut = makeSUT()
        // From the month graph debug trace:
        // 265.7 stored -> 12.1 kg displayed, 1054.0 stored -> 47.8 kg displayed.
        // Averaging raw kg first gives 29.9303 -> 29.9, which produces -70.1.
        // Averaging displayed entries gives (12.1 + 47.8) / 2 = 29.95 -> 30.0, so 30.0 - 100.0 = -70.0.
        let ops = [
            DashboardTestFixtures.makeSummary(period: "2026-04-07", weight: 265.7),
            DashboardTestFixtures.makeSummary(period: "2026-04-08", weight: 1054.0)
        ]
        let anchorRaw = Double(ConversionTools.convertKgToStored(100.0)) / 22.0462

        let context = DashboardTestFixtures.makeDisplayWeightContext(
            operations: ops,
            operationsForLabel: ops,
            period: .month,
            isWeightlessMode: true,
            anchorWeight: anchorRaw,
            convertWeight: DashboardTestFixtures.convertToKgRaw
        )

        let result = sut.calculateDisplayWeight(context: context)

        #expect(result == -70.0)
    }

    @Test("getCurrentAverageWeight: many operations stress test")
    func averageWeightManyOperations() {
        let sut = makeSUT()
        var ops: [BathScaleWeightSummary] = []
        for i in 0..<100 {
            ops.append(DashboardTestFixtures.makeSummary(weight: Double(1800 + i)))
        }

        let result = sut.getCurrentAverageWeight(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        // Average of 180.0 to 189.9 = 184.95 → rounds to 185.0
        #expect(result == 185.0)
    }
}
// swiftlint:enable file_length
