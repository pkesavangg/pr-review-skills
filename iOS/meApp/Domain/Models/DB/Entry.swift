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
    }

    convenience init(from dto: BathScaleOperationDTO, isSynced: Bool = false) {
        let timestamp = dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
        self.init(
            id: UUID(),
            entryTimestamp: timestamp,
            accountId: dto.accountId ?? "",
            operationType: dto.operationType ?? "",
            serverTimestamp: dto.serverTimestamp,
            isSynced: isSynced
        )
    }

    func toOperationDTO() -> BathScaleOperationDTO {
        return BathScaleOperationDTO(
            accountId: self.accountId,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: self.entryTimestamp,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: self.operationType,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: self.serverTimestamp,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: nil
        )
    }
}
