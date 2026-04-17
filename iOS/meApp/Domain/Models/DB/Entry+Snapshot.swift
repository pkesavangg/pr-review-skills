//
//  Entry+Snapshot.swift
//  meApp
//
//  Conversion extensions from Entry + child @Models to Sendable snapshots.
//  Call on the main actor while the ModelContext is valid.
//

import Foundation

extension BathScaleEntry {
    func toSnapshot() -> BathScaleEntrySnapshot {
        BathScaleEntrySnapshot(
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: source,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: meanArterial
        )
    }
}

extension BathScaleMetric {
    func toSnapshot() -> BathScaleMetricSnapshot {
        BathScaleMetricSnapshot(
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
    }
}

extension BPMEntry {
    func toSnapshot() -> BPMEntrySnapshot {
        BPMEntrySnapshot(
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: meanArterial,
            pulse: pulse
        )
    }
}

extension BabyEntry {
    func toSnapshot() -> BabyEntrySnapshot {
        BabyEntrySnapshot(
            babyId: babyId,
            length: length,
            weight: weight,
            source: source
        )
    }
}

extension Entry {
    /// Converts the SwiftData `Entry` and all its child models into a
    /// Sendable `EntrySnapshot`. Must be called on the main actor while the
    /// model context is valid and before any await boundary.
    func toSnapshot() -> EntrySnapshot {
        EntrySnapshot(
            id: id,
            accountId: accountId,
            entryTimestamp: entryTimestamp,
            serverTimestamp: serverTimestamp,
            opTimestamp: opTimestamp,
            operationType: operationType,
            entryType: entryType,
            isSynced: isSynced,
            note: note,
            attempts: attempts,
            isFailedToSync: isFailedToSync,
            scaleEntry: scaleEntry?.toSnapshot(),
            scaleEntryMetric: scaleEntryMetric?.toSnapshot(),
            bpmEntry: bpmEntry?.toSnapshot(),
            babyEntry: babyEntry?.toSnapshot()
        )
    }
}
