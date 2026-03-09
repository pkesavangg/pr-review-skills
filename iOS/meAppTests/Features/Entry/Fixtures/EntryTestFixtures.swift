import Foundation
@testable import meApp

enum EntryTestError: Error, Equatable {
    case localFailure
    case remoteFailure
}

enum EntryTestFixtures {
    static func makeEntry(
        id: UUID = UUID(),
        accountId: String = "acct-1",
        timestamp: String = "2026-03-01T08:00:00Z",
        weight: Int? = 1800,
        bodyFat: Int? = 250,
        muscleMass: Int? = 820,
        water: Int? = 540,
        bmi: Int? = 230,
        source: String? = "manual",
        bmr: Int? = 1600,
        metabolicAge: Int? = 35,
        proteinPercent: Int? = 190,
        pulse: Int? = 72,
        skeletalMusclePercent: Int? = 410,
        subcutaneousFatPercent: Int? = 210,
        visceralFatLevel: Int? = 11,
        boneMass: Int? = 80,
        impedance: Int? = 510,
        unit: String? = "lb",
        operationType: OperationType = .create,
        serverTimestamp: String? = nil,
        isSynced: Bool = false
    ) -> Entry {
        let entry = Entry(
            id: id,
            entryTimestamp: timestamp,
            accountId: accountId,
            operationType: operationType.rawValue,
            serverTimestamp: serverTimestamp,
            isSynced: isSynced
        )
        entry.scaleEntry = BathScaleEntry(
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: source
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass,
            impedance: impedance,
            unit: unit
        )
        return entry
    }
}
