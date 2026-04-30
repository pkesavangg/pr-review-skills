//
//  EntryRepositoryData.swift
//  meApp
//

import Foundation

/// Sendable data structures for crossing actor boundaries in EntryRepository.

struct EntrySyncData: Sendable {
    let id: UUID
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    let operationType: String
    let entryType: String
    let isSynced: Bool
    let isFailedToSync: Bool
    let attempts: Int
    let scaleEntry: ScaleEntryData?
    let scaleEntryMetric: ScaleMetricData?
    // BPM scalars
    let bpmSystolic: Int?
    let bpmDiastolic: Int?
    let bpmMeanArterial: String?
    let bpmPulse: Int?
    // Entry-level note (shared across all entry types)
    let note: String?
    // Baby scalars
    let babyEntryBabyId: String?
    let babyEntryLength: Int?
    let babyEntryWeight: Int?
}

struct ScaleEntryData: Sendable {
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let source: String?
}

struct ScaleMetricData: Sendable {
    let bmr: Int?
    let metabolicAge: Int?
    let proteinPercent: Int?
    let pulse: Int?
    let skeletalMusclePercent: Int?
    let subcutaneousFatPercent: Int?
    let visceralFatLevel: Int?
    let boneMass: Int?
    let impedance: Int?
    let unit: String?
}
