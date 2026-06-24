//
//  EntryChildSnapshots.swift
//  meApp
//
//  Value-type snapshots of Entry's child @Model relationships.
//  Sendable and safe to cross async boundaries.
//

import Foundation

/// Value-type snapshot of BathScaleEntry. Sendable, safe across async boundaries.
struct BathScaleEntrySnapshot: Equatable, Sendable {
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let source: String?
    let systolic: Int?
    let diastolic: Int?
    let meanArterial: String?
}

/// Value-type snapshot of BathScaleMetric. Sendable, safe across async boundaries.
struct BathScaleMetricSnapshot: Equatable, Sendable {
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

/// Value-type snapshot of BPMEntry. Sendable, safe across async boundaries.
struct BPMEntrySnapshot: Equatable, Sendable {
    let systolic: Int
    let diastolic: Int
    let meanArterial: String
    let pulse: Int
}

/// Value-type snapshot of BabyEntry. Sendable, safe across async boundaries.
struct BabyEntrySnapshot: Equatable, Sendable {
    let babyId: String
    let length: Int
    let weight: Int
    let source: String?
}
