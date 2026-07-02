//
//  EntrySnapshot.swift
//  meApp
//
//  Value-type copy of Entry + its child relationships.
//  Published by EntryService / HistoryStore instead of the SwiftData @Model.
//

import Foundation

/// A value-type copy of `Entry` and its child relationships.
/// Published by `EntryService` / `HistoryStore` instead of the SwiftData `@Model` directly.
///
/// Safe to use across async boundaries and as Combine publisher payloads.
/// Reading `snapshot.scaleEntry?.weight` is a plain `let Int?` access — no
/// SwiftData backing store involved.
///
/// ## Relationship with `EntryData` (SwiftDataWorker)
///
/// `EntryData` is a slim projection used by progress calculations and DTO
/// conversion (scale fields only). `EntrySnapshot` is the richer, UI-facing
/// value type covering every relationship including BPM and baby. They can
/// coexist — `EntryData` stays where it is used; consumers of
/// `@Published [Entry]` switch to `[EntrySnapshot]`.
struct EntrySnapshot: Equatable, Sendable, Identifiable {

    // MARK: - Core Entry fields
    let id: UUID
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    /// Server-assigned entry identifier. Stable identity used to dedup History rows
    /// so distinct entries sharing an `entryTimestamp` are not collapsed. Nil for
    /// local-only entries not yet synced.
    let serverEntryId: String?
    let opTimestamp: String?
    let operationType: String
    let entryType: String
    let isSynced: Bool
    let note: String?
    let attempts: Int
    let isFailedToSync: Bool

    // MARK: - Child relationship snapshots
    let scaleEntry: BathScaleEntrySnapshot?
    let scaleEntryMetric: BathScaleMetricSnapshot?
    let bpmEntry: BPMEntrySnapshot?
    let babyEntry: BabyEntrySnapshot?

    // MARK: - Computed (mirrors Entry.metricItems)

    /// Returns an array of (value, metric) pairs for all available body metrics.
    /// Mirrors `Entry.metricItems` so views switching from `Entry` to
    /// `EntrySnapshot` get identical output.
    var metricItems: [(value: Int, metric: BodyMetric)] {
        var arr: [(Int, BodyMetric)] = []
        if let bmi = scaleEntry?.bmi {
            arr.append((bmi, .bmi))
        }
        if let bodyFat = scaleEntry?.bodyFat, bodyFat != 0 {
            arr.append((bodyFat, .bodyFat))
        }
        if let muscleMass = scaleEntry?.muscleMass, muscleMass != 0 {
            arr.append((muscleMass, .muscleMass))
        }
        if let water = scaleEntry?.water, water != 0 {
            arr.append((water, .water))
        }
        if let heartRate = scaleEntryMetric?.pulse, heartRate != 0 {
            arr.append((heartRate, .pulse))
        }
        if let boneMass = scaleEntryMetric?.boneMass, boneMass != 0 {
            arr.append((boneMass, .boneMass))
        }
        if let visceralFat = scaleEntryMetric?.visceralFatLevel, visceralFat != 0 {
            arr.append((visceralFat, .visceralFatLevel))
        }
        if let subcutaneousFat = scaleEntryMetric?.subcutaneousFatPercent, subcutaneousFat != 0 {
            arr.append((subcutaneousFat, .subcutaneousFatPercent))
        }
        if let proteinPercent = scaleEntryMetric?.proteinPercent, proteinPercent != 0 {
            arr.append((proteinPercent, .proteinPercent))
        }
        if let skeletalMuscles = scaleEntryMetric?.skeletalMusclePercent, skeletalMuscles != 0 {
            arr.append((skeletalMuscles, .skeletalMusclePercent))
        }
        if let bmr = scaleEntryMetric?.bmr, bmr != 0 {
            arr.append((bmr, .bmr))
        }
        if let metabolicAge = scaleEntryMetric?.metabolicAge, metabolicAge != 0 {
            arr.append((metabolicAge, .metabolicAge))
        }
        return arr
    }
}
