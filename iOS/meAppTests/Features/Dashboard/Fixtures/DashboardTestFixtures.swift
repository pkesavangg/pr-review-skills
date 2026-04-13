import Combine
import Foundation
import Testing
@testable import meApp

// MARK: - Dashboard Test Error

enum DashboardTestError: Error, Equatable {
    case simulatedFailure
    case repoFailure
}

// MARK: - Dashboard Test Fixtures

@MainActor
enum DashboardTestFixtures {

    // MARK: - BathScaleWeightSummary Factory

    static func makeSummary(
        id: UUID = UUID(),
        accountId: String = "acct-1",
        period: String = "2026-03-01",
        entryTimestamp: String = "2026-03-01T08:00:00Z",
        date: Date? = nil,
        count: Int = 1,
        weight: Double = 1800,
        bodyFat: Double? = nil,
        muscleMass: Double? = nil,
        water: Double? = nil,
        bmi: Double? = nil,
        bmr: Double? = nil,
        metabolicAge: Double? = nil,
        proteinPercent: Double? = nil,
        pulse: Double? = nil,
        skeletalMusclePercent: Double? = nil,
        subcutaneousFatPercent: Double? = nil,
        visceralFatLevel: Double? = nil,
        boneMass: Double? = nil,
        impedance: Double? = nil
    ) -> BathScaleWeightSummary {
        let resolvedDate: Date
        if let date = date {
            resolvedDate = date
        } else if period.count <= 7 {
            // Monthly period format "yyyy-MM" — append "-01" for parsing
            resolvedDate = DateTimeTools.getDateFromDateString(period + "-01", format: "yyyy-MM-dd")
        } else {
            resolvedDate = DateTimeTools.getDateFromDateString(period, format: "yyyy-MM-dd")
        }
        return BathScaleWeightSummary(
            id: id,
            accountId: accountId,
            period: period,
            entryTimestamp: entryTimestamp,
            date: resolvedDate,
            count: count,
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass,
            impedance: impedance
        )
    }

    /// Summary with all body metrics populated
    static func makeSummaryWithAllMetrics(
        period: String = "2026-03-01",
        weight: Double = 1800,
        bodyFat: Double = 250,
        muscleMass: Double = 820,
        water: Double = 540,
        bmi: Double = 230,
        bmr: Double = 16000,
        metabolicAge: Double = 35,
        proteinPercent: Double = 190,
        pulse: Double = 72,
        skeletalMusclePercent: Double = 410,
        subcutaneousFatPercent: Double = 210,
        visceralFatLevel: Double = 110,
        boneMass: Double = 80
    ) -> BathScaleWeightSummary {
        makeSummary(
            period: period,
            entryTimestamp: "\(period)T08:00:00Z",
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass
        )
    }

    // MARK: - Multiple Summaries

    /// Creates a set of daily summaries spread across several days (unsorted for testing sort logic)
    static func makeUnsortedDailySummaries() -> [BathScaleWeightSummary] {
        [
            makeSummary(period: "2026-03-03", entryTimestamp: "2026-03-03T08:00:00Z", weight: 1820),
            makeSummary(period: "2026-03-01", entryTimestamp: "2026-03-01T08:00:00Z", weight: 1800),
            makeSummary(period: "2026-03-05", entryTimestamp: "2026-03-05T08:00:00Z", weight: 1840),
            makeSummary(period: "2026-03-02", entryTimestamp: "2026-03-02T08:00:00Z", weight: 1810),
            makeSummary(period: "2026-03-04", entryTimestamp: "2026-03-04T08:00:00Z", weight: 1830)
        ]
    }

    /// Creates a set of sorted daily summaries
    static func makeSortedDailySummaries() -> [BathScaleWeightSummary] {
        [
            makeSummary(period: "2026-03-01", entryTimestamp: "2026-03-01T08:00:00Z", weight: 1800),
            makeSummary(period: "2026-03-02", entryTimestamp: "2026-03-02T08:00:00Z", weight: 1810),
            makeSummary(period: "2026-03-03", entryTimestamp: "2026-03-03T08:00:00Z", weight: 1820),
            makeSummary(period: "2026-03-04", entryTimestamp: "2026-03-04T08:00:00Z", weight: 1830),
            makeSummary(period: "2026-03-05", entryTimestamp: "2026-03-05T08:00:00Z", weight: 1840)
        ]
    }

    /// Creates a set of monthly summaries (unsorted)
    static func makeUnsortedMonthlySummaries() -> [BathScaleWeightSummary] {
        [
            makeSummary(period: "2026-03", entryTimestamp: "2026-03-01T00:00:00Z", weight: 1820),
            makeSummary(period: "2026-01", entryTimestamp: "2026-01-01T00:00:00Z", weight: 1800),
            makeSummary(period: "2026-02", entryTimestamp: "2026-02-01T00:00:00Z", weight: 1810)
        ]
    }

    /// Creates a set of sorted monthly summaries
    static func makeSortedMonthlySummaries() -> [BathScaleWeightSummary] {
        [
            makeSummary(period: "2026-01", entryTimestamp: "2026-01-01T00:00:00Z", weight: 1800),
            makeSummary(period: "2026-02", entryTimestamp: "2026-02-01T00:00:00Z", weight: 1810),
            makeSummary(period: "2026-03", entryTimestamp: "2026-03-01T00:00:00Z", weight: 1820)
        ]
    }

    // MARK: - MetricItem Factory

    static func makeMetricItem(
        value: String = "75.0",
        label: String = "weight",
        unit: String? = "kg",
        preLabel: String? = nil,
        icon: String? = nil
    ) -> MetricItem {
        MetricItem(value: value, label: label, unit: unit, preLabel: preLabel, icon: icon)
    }

    // MARK: - Conversion Helpers

    /// Simple stored-to-display conversion: stored weight is in tenths of pounds
    static let convertToLbs: (Double) -> Double = { $0 / 10.0 }

    /// Identity conversion for testing (no conversion applied)
    static let identityConvert: (Double) -> Double = { $0 }

    // MARK: - DisplayWeightContext Factory

    static func makeDisplayWeightContext(
        selectedPoint: BathScaleWeightSummary? = nil,
        selectedDate: Date? = nil,
        operations: [BathScaleWeightSummary] = [],
        visibleOperations: [BathScaleWeightSummary] = [],
        operationsForLabel: [BathScaleWeightSummary] = [],
        isWeightlessMode: Bool = false,
        anchorWeight: Double? = nil,
        period: TimePeriod = .week,
        convertWeight: @escaping (Double) -> Double = convertToLbs,
        interpolatedWeight: @escaping (Date, [BathScaleWeightSummary], Bool, Double?, @escaping (Double) -> Double) -> Double? = { _, _, _, _, _ in nil },
        interpolatedAverage: @escaping ([BathScaleWeightSummary], TimePeriod, Bool, Double?, @escaping (Double) -> Double, DateInterval?) -> Double? = { _, _, _, _, _, _ in nil },
        weightlessDisplay: @escaping ([BathScaleWeightSummary], Double?, TimePeriod, @escaping (Double) -> Double) -> Double? = { _, _, _, _ in nil },
        labelRangeForPeriod: @escaping (TimePeriod) -> DateInterval? = { _ in nil }
    ) -> DisplayWeightContext {
        DisplayWeightContext(
            selectedPoint: selectedPoint,
            selectedDate: selectedDate,
            operations: operations,
            visibleOperations: visibleOperations,
            operationsForLabel: operationsForLabel,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            period: period,
            convertWeight: convertWeight,
            interpolatedWeight: interpolatedWeight,
            interpolatedAverage: interpolatedAverage,
            weightlessDisplay: weightlessDisplay,
            labelRangeForPeriod: labelRangeForPeriod
        )
    }

    // MARK: - EntryCreationContext Factory

    static func makeEntryCreationContext(
        selectedPoint: BathScaleWeightSummary? = nil,
        selectedDate: Date? = nil,
        operations: [BathScaleWeightSummary] = [],
        visibleOperations: [BathScaleWeightSummary] = [],
        metrics: [MetricItem] = [],
        isWeightlessMode: Bool = false,
        anchorWeight: Double? = nil,
        period: TimePeriod = .week,
        weightUnit: WeightUnit = .lb,
        latestWeightStored: Int = 0,
        convertWeight: @escaping (Double) -> Double = convertToLbs,
        interpolatedWeight: @escaping (Date, [BathScaleWeightSummary], Bool, Double?, @escaping (Double) -> Double) -> Double? = { _, _, _, _, _ in nil },
        interpolatedAverage: @escaping ([BathScaleWeightSummary], TimePeriod, Bool, Double?, @escaping (Double) -> Double, DateInterval?) -> Double? = { _, _, _, _, _, _ in nil }
    ) -> EntryCreationContext {
        EntryCreationContext(
            selectedPoint: selectedPoint,
            selectedDate: selectedDate,
            operations: operations,
            visibleOperations: visibleOperations,
            metrics: metrics,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            period: period,
            weightUnit: weightUnit,
            latestWeightStored: latestWeightStored,
            convertWeight: convertWeight,
            interpolatedWeight: interpolatedWeight,
            interpolatedAverage: interpolatedAverage
        )
    }

    // MARK: - Async Helpers

    /// Polls until `condition` is true or `timeout` elapses (default 1 s).
    static func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }

    // MARK: - DashboardDataManager SUT Factory

    /// Creates a DashboardDataManager with a fully configured DI container.
    /// Returns the SUT, the EntryService instance (same one resolved by @Injector),
    /// and the MockEntryRepository for data injection.
    static func makeDataManagerSUT(
        entries: [Entry] = [],
        hasActiveAccount: Bool = true
    ) -> (sut: DashboardDataManager, entryService: EntryService, entryRepo: MockEntryRepository) {
        TestDependencyContainer.reset()

        let mockAccount = MockAccountService()
        if hasActiveAccount {
            mockAccount.activeAccount = AccountTestFixtures.makeAccountModel(
                id: "acct-1", email: "test@example.com", isActive: true
            )
        }

        let entryRepo = MockEntryRepository()
        entryRepo.entries = entries
        let entryService = EntryService(
            accountService: mockAccount,
            localRepo: entryRepo,
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: MockEntryRepositoryAPI()
        )

        // Overwrite the EntryService registered by reset() with our configurable one
        DependencyContainer.shared.register(entryService as EntryService)

        let sut = DashboardDataManager()
        return (sut, entryService, entryRepo)
    }
}
