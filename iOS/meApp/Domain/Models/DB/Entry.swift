/// Stores all user entry records with common properties for all device types.
///
/// | Column Name      | Type    | Description                                         |
/// |------------------|---------|-----------------------------------------------------|
/// | id               | int     | Unique entry ID (Primary Key)                       |
/// | accountId        | string  | Foreign key referencing account.accountId           |
/// | entryTimestamp   | string  | Timestamp when the entry was made                   |
/// | serverTimestamp  | string  | Server-generated timestamp of entry receipt         |
/// | opTimestamp      | string  | Operation timestamp                                |
/// | operationType    | string  | Type of operation (e.g., create, delete, note)      |
/// | deviceType       | string  | Device type (e.g., scale, bgm)                      |
/// | isSynced         | boolean | Whether entry is synced online                      |

import Foundation
import SwiftData

@Model
final class Entry {
    /// Unique entry ID (PK)
    @Attribute(.unique) var id: UUID
    /// Foreign key referencing account.accountId
    var accountId: String
    /// Timestamp when the entry was made
    var entryTimestamp: String
    /// Server-generated timestamp of entry receipt
    var serverTimestamp: String?
    /// Operation timestamp
    var opTimestamp: String?
    /// Type of operation (eg., 'create', 'delete', 'note')
    var operationType: String
    /// Device type (eg., 'scale', 'bgm' )
    var deviceType: String
    /// Whether entry is synced online
    var isSynced: Bool
    /// Number of attempts to sync the entry
    var attempts: Int
    /// Whether entry is failed to sync
    var isFailedToSync: Bool
    @Relationship var scaleEntry: BathScaleEntry?
    @Relationship var scaleEntryMetric: BathScaleMetric?

    init(id: UUID = UUID(),
         entryTimestamp: String,
         accountId: String,
         operationType: String,
         opTimestamp: String? = nil,
         serverTimestamp: String? = nil,
         deviceType: String = "scale",
         isSynced: Bool = false) {
        self.id = id
        self.entryTimestamp = entryTimestamp
        self.accountId = accountId
        self.operationType = operationType
        self.opTimestamp = opTimestamp
        self.serverTimestamp = serverTimestamp
        self.deviceType = deviceType
        self.isSynced = isSynced
        self.attempts = 0
        self.isFailedToSync = false
    }

    init(from dto: BathScaleOperationDTO, accountId: String, isSynced: Bool = false) {
            let timestamp = dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
            self.id = UUID()
            self.entryTimestamp = timestamp
            self.accountId = accountId
            self.operationType = dto.operationType ?? ""
            self.serverTimestamp = dto.serverTimestamp
            self.deviceType = DeviceType.scale.rawValue
            self.isSynced = isSynced
            self.attempts = 0
            self.isFailedToSync = false
            self.scaleEntry = BathScaleEntry(from: dto)
            self.scaleEntryMetric = BathScaleMetric(from: dto)
    }

    func toOperationDTO() -> BathScaleOperationDTO {
        return BathScaleOperationDTO(
            accountId: self.accountId,
            bmr: self.scaleEntryMetric?.bmr.map { Double($0) },
            bmi: self.scaleEntry?.bmi.map { Double($0) },
            bodyFat: self.scaleEntry?.bodyFat.map { Double($0) },
            boneMass: self.scaleEntryMetric?.boneMass.map { Double($0) },
            entryTimestamp: self.entryTimestamp,
            impedance: self.scaleEntryMetric?.impedance.map { Double($0) },
            metabolicAge: self.scaleEntryMetric?.metabolicAge.map { Double($0) },
            muscleMass: self.scaleEntry?.muscleMass.map { Double($0) },
            operationType: self.operationType,
            proteinPercent: self.scaleEntryMetric?.proteinPercent.map { Double($0) },
            pulse: self.scaleEntryMetric?.pulse.map { Double($0) },
            serverTimestamp: self.serverTimestamp,
            skeletalMusclePercent: self.scaleEntryMetric?.skeletalMusclePercent.map { Double($0) },
            source: self.scaleEntry?.source,
            subcutaneousFatPercent: self.scaleEntryMetric?.subcutaneousFatPercent.map { Double($0) },
            unit: self.scaleEntryMetric?.unit,
            visceralFatLevel: self.scaleEntryMetric?.visceralFatLevel.map { Double($0) },
            water: self.scaleEntry?.water.map { Double($0) },
            weight: self.scaleEntry?.weight.map { Double($0) },
        )
    }
}

/// @unchecked Sendable is used because Entry is a SwiftData @Model with thread-safe properties.
/// SwiftData handles synchronization internally, allowing safe use in async contexts like
/// HealthKit sync and background operations. We explicitly mark it Sendable to enable
/// concurrent access without data races.
extension Entry: @unchecked Sendable {}
