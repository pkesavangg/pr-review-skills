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

    static func makeBpmEntry(
        id: UUID = UUID(),
        accountId: String = "acct-1",
        timestamp: String = "2026-03-01T08:00:00Z",
        systolic: Int? = 120,
        diastolic: Int? = 80,
        pulse: Int? = 72,
        meanArterial: String? = "93.3",
        note: String? = nil,
        source: String? = "manual",
        unit: String? = "mmHg",
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
            deviceType: DeviceType.bpm.rawValue,
            entryType: EntryType.bpm.rawValue,
            isSynced: isSynced
        )
        entry.scaleEntry = BathScaleEntry(
            source: source,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: meanArterial,
            note: note
        )
        entry.scaleEntryMetric = BathScaleMetric(
            pulse: pulse,
            unit: unit
        )
        return entry
    }

    static func makeBpmDTO(
        accountId: String? = "acct-1",
        systolic: Double? = 120.0,
        diastolic: Double? = 80.0,
        pulse: Double? = 72.0,
        meanArterial: String? = "93.3",
        note: String? = nil,
        source: String? = "manual",
        unit: String? = "mmHg",
        entryTimestamp: String? = "2026-03-01T08:00:00Z",
        operationType: String? = "create",
        serverTimestamp: String? = nil
    ) -> BpmOperationDTO {
        BpmOperationDTO(
            accountId: accountId,
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            meanArterial: meanArterial,
            note: note,
            source: source,
            unit: unit,
            entryTimestamp: entryTimestamp,
            operationType: operationType,
            serverTimestamp: serverTimestamp
        )
    }
}
