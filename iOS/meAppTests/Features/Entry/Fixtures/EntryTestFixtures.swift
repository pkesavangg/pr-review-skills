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
            entryType: EntryType.bpm.rawValue,
            isSynced: isSynced
        )
        entry.scaleEntry = BathScaleEntry(
            source: source,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: meanArterial
        )
        entry.scaleEntryMetric = BathScaleMetric(
            pulse: pulse,
            unit: unit
        )
        entry.bpmEntry = BPMEntry(
            systolic: systolic ?? 0,
            diastolic: diastolic ?? 0,
            meanArterial: meanArterial ?? "",
            pulse: pulse ?? 0
        )
        entry.note = note
        return entry
    }

    // MARK: - Snapshot Factories

    static func makeEntrySnapshot(
        id: UUID = UUID(),
        accountId: String = "acct-1",
        entryTimestamp: String = "2026-03-01T08:00:00Z",
        serverTimestamp: String? = nil,
        opTimestamp: String? = nil,
        operationType: OperationType = .create,
        entryType: EntryType = .scale,
        isSynced: Bool = false,
        note: String? = nil,
        attempts: Int = 0,
        isFailedToSync: Bool = false,
        // Scale child
        weight: Int? = 1800,
        bodyFat: Int? = 250,
        muscleMass: Int? = 820,
        water: Int? = 540,
        bmi: Int? = 230,
        source: String? = "manual",
        // Metric child
        bmr: Int? = 1600,
        metabolicAge: Int? = 35,
        proteinPercent: Int? = 190,
        pulse: Int? = 72,
        skeletalMusclePercent: Int? = 410,
        subcutaneousFatPercent: Int? = 210,
        visceralFatLevel: Int? = 11,
        boneMass: Int? = 80,
        impedance: Int? = 510,
        unit: String? = "lb"
    ) -> EntrySnapshot {
        EntrySnapshot(
            id: id,
            accountId: accountId,
            entryTimestamp: entryTimestamp,
            serverTimestamp: serverTimestamp,
            opTimestamp: opTimestamp,
            operationType: operationType.rawValue,
            entryType: entryType.rawValue,
            isSynced: isSynced,
            note: note,
            attempts: attempts,
            isFailedToSync: isFailedToSync,
            scaleEntry: BathScaleEntrySnapshot(
                weight: weight,
                bodyFat: bodyFat,
                muscleMass: muscleMass,
                water: water,
                bmi: bmi,
                source: source,
                systolic: nil,
                diastolic: nil,
                meanArterial: nil
            ),
            scaleEntryMetric: BathScaleMetricSnapshot(
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
            ),
            bpmEntry: nil,
            babyEntry: nil
        )
    }

    static func makeBpmEntrySnapshot(
        id: UUID = UUID(),
        accountId: String = "acct-1",
        entryTimestamp: String = "2026-03-01T08:00:00Z",
        systolic: Int = 120,
        diastolic: Int = 80,
        pulse: Int = 72,
        meanArterial: String = "93.3",
        note: String? = nil,
        source: String? = "manual",
        unit: String? = "mmHg",
        operationType: OperationType = .create,
        serverTimestamp: String? = nil,
        isSynced: Bool = false
    ) -> EntrySnapshot {
        EntrySnapshot(
            id: id,
            accountId: accountId,
            entryTimestamp: entryTimestamp,
            serverTimestamp: serverTimestamp,
            opTimestamp: nil,
            operationType: operationType.rawValue,
            entryType: EntryType.bpm.rawValue,
            isSynced: isSynced,
            note: note,
            attempts: 0,
            isFailedToSync: false,
            scaleEntry: BathScaleEntrySnapshot(
                weight: nil, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
                source: source, systolic: systolic, diastolic: diastolic, meanArterial: meanArterial
            ),
            scaleEntryMetric: BathScaleMetricSnapshot(
                bmr: nil, metabolicAge: nil, proteinPercent: nil, pulse: pulse,
                skeletalMusclePercent: nil, subcutaneousFatPercent: nil, visceralFatLevel: nil,
                boneMass: nil, impedance: nil, unit: unit
            ),
            bpmEntry: BPMEntrySnapshot(
                systolic: systolic,
                diastolic: diastolic,
                meanArterial: meanArterial,
                pulse: pulse
            ),
            babyEntry: nil
        )
    }

    static func makeBabyEntrySnapshot(
        id: UUID = UUID(),
        accountId: String = "acct-1",
        entryTimestamp: String = "2026-03-01T08:00:00Z",
        babyId: String = "baby-1",
        weight: Int = 5000,
        length: Int = 500,
        source: String? = "manual",
        operationType: OperationType = .create,
        serverTimestamp: String? = nil,
        isSynced: Bool = false
    ) -> EntrySnapshot {
        EntrySnapshot(
            id: id,
            accountId: accountId,
            entryTimestamp: entryTimestamp,
            serverTimestamp: serverTimestamp,
            opTimestamp: nil,
            operationType: operationType.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: isSynced,
            note: nil,
            attempts: 0,
            isFailedToSync: false,
            scaleEntry: nil,
            scaleEntryMetric: nil,
            bpmEntry: nil,
            babyEntry: BabyEntrySnapshot(
                babyId: babyId,
                length: length,
                weight: weight,
                source: source
            )
        )
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
