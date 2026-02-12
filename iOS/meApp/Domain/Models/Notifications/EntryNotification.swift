//
//  EntryNotification.swift
//  meApp
//
//  Sendable notification type for Entry model changes.
//  Use this to safely pass entry data across actor boundaries.
//

import Foundation
import SwiftData

/// A Sendable notification containing extracted Entry data.
/// Use this instead of passing Entry @Model objects across actor boundaries.
///
/// ## Usage
/// ```swift
/// // In EntryService (MainActor context):
/// let notification = EntryNotification(from: entry)
/// entrySaved.send(notification)
///
/// // In subscriber:
/// entryService.entrySaved.sink { notification in
///     // Safe to use notification.weight, notification.entryTimestamp, etc.
/// }
/// ```
struct EntryNotification: Sendable, Identifiable, Equatable {
    // MARK: - Core Properties
    let id: UUID
    /// The PersistentIdentifier for refetching the Entry on MainActor.
    /// May be nil if created from DTO without a persisted Entry.
    let persistentId: PersistentIdentifier?
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    let operationType: String
    let deviceType: String
    let isSynced: Bool
    let isFailedToSync: Bool
    let attempts: Int

    // MARK: - Scale Entry Data (from BathScaleEntry relationship)
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let source: String?

    // MARK: - Scale Metric Data (from BathScaleMetric relationship)
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

    // MARK: - Initializers

    /// Creates a notification by extracting all data from an Entry.
    /// Must be called on MainActor to safely access Entry relationships.
    @MainActor
    init(from entry: Entry) {
        self.id = entry.id
        self.persistentId = entry.persistentModelID
        self.accountId = entry.accountId
        self.entryTimestamp = entry.entryTimestamp
        self.serverTimestamp = entry.serverTimestamp
        self.operationType = entry.operationType
        self.deviceType = entry.deviceType
        self.isSynced = entry.isSynced
        self.isFailedToSync = entry.isFailedToSync
        self.attempts = entry.attempts

        // Extract scale entry data (relationship)
        self.weight = entry.scaleEntry?.weight
        self.bodyFat = entry.scaleEntry?.bodyFat
        self.muscleMass = entry.scaleEntry?.muscleMass
        self.water = entry.scaleEntry?.water
        self.bmi = entry.scaleEntry?.bmi
        self.source = entry.scaleEntry?.source

        // Extract scale metric data (relationship)
        self.bmr = entry.scaleEntryMetric?.bmr
        self.metabolicAge = entry.scaleEntryMetric?.metabolicAge
        self.proteinPercent = entry.scaleEntryMetric?.proteinPercent
        self.pulse = entry.scaleEntryMetric?.pulse
        self.skeletalMusclePercent = entry.scaleEntryMetric?.skeletalMusclePercent
        self.subcutaneousFatPercent = entry.scaleEntryMetric?.subcutaneousFatPercent
        self.visceralFatLevel = entry.scaleEntryMetric?.visceralFatLevel
        self.boneMass = entry.scaleEntryMetric?.boneMass
        self.impedance = entry.scaleEntryMetric?.impedance
        self.unit = entry.scaleEntryMetric?.unit
    }

    /// Creates a notification from a DTO (for cases where Entry is not available).
    init(from dto: BathScaleOperationDTO, id: UUID = UUID(), persistentId: PersistentIdentifier? = nil) {
        self.id = id
        self.persistentId = persistentId
        self.accountId = dto.accountId ?? ""
        self.entryTimestamp = dto.entryTimestamp ?? ""
        self.serverTimestamp = dto.serverTimestamp
        self.operationType = dto.operationType ?? ""
        self.deviceType = "scale"
        self.isSynced = true
        self.isFailedToSync = false
        self.attempts = 0

        self.weight = dto.weight.map { Int($0) }
        self.bodyFat = dto.bodyFat.map { Int($0) }
        self.muscleMass = dto.muscleMass.map { Int($0) }
        self.water = dto.water.map { Int($0) }
        self.bmi = dto.bmi.map { Int($0) }
        self.source = nil

        self.bmr = dto.bmr.map { Int($0) }
        self.metabolicAge = dto.metabolicAge.map { Int($0) }
        self.proteinPercent = dto.proteinPercent.map { Int($0) }
        self.pulse = dto.pulse.map { Int($0) }
        self.skeletalMusclePercent = dto.skeletalMusclePercent.map { Int($0) }
        self.subcutaneousFatPercent = dto.subcutaneousFatPercent.map { Int($0) }
        self.visceralFatLevel = dto.visceralFatLevel.map { Int($0) }
        self.boneMass = dto.boneMass.map { Int($0) }
        self.impedance = dto.impedance.map { Int($0) }
        self.unit = dto.unit
    }

    // MARK: - Conversion Methods

    /// Converts this notification to a BathScaleOperationDTO.
    func toDTO() -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: accountId,
            bmr: bmr.map(Double.init),
            bmi: bmi.map(Double.init),
            bodyFat: bodyFat.map(Double.init),
            boneMass: boneMass.map(Double.init),
            entryTimestamp: entryTimestamp,
            impedance: impedance.map(Double.init),
            metabolicAge: metabolicAge.map(Double.init),
            muscleMass: muscleMass.map(Double.init),
            operationType: operationType,
            proteinPercent: proteinPercent.map(Double.init),
            pulse: pulse.map(Double.init),
            serverTimestamp: serverTimestamp,
            skeletalMusclePercent: skeletalMusclePercent.map(Double.init),
            source: source,
            subcutaneousFatPercent: subcutaneousFatPercent.map(Double.init),
            unit: unit,
            visceralFatLevel: visceralFatLevel.map(Double.init),
            water: water.map(Double.init),
            weight: weight.map(Double.init)
        )
    }

    /// Parsed date from entryTimestamp.
    var date: Date? {
        guard !entryTimestamp.isEmpty else { return nil }
        return ISO8601DateFormatter().date(from: entryTimestamp)
    }
}
