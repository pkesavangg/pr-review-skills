/// Stores all user entry records with common properties for all device types.
///
/// | Column Name      | Type    | Description                                         |
/// |------------------|---------|-----------------------------------------------------|
/// | id               | int     | Unique entry ID (Primary Key)                       |
/// | userId           | string  | Foreign key referencing account.id                  |
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
    @Attribute(.unique) var id: String
    var entryTimestamp: String
    var userId: String
    var operationType: String
    var opTimestamp: String?
    var serverTimestamp: String?
    var deviceType: String
    var isSynced: Bool = false
    @Relationship var bathScaleEntry: BathScaleEntry?
    @Relationship var bodyMetricEntry: BathScaleMetric?

    init(id: String = UUID().uuidString,
         entryTimestamp: String,
         userId: String,
         operationType: String,
         opTimestamp: String? = nil,
         serverTimestamp: String? = nil,
         deviceType: String = "scale",
         isSynced: Bool = false,
         bathScaleEntry: BathScaleEntry? = nil,
         bodyMetricEntry: BathScaleMetric? = nil) {
        self.id = id
        self.entryTimestamp = entryTimestamp
        self.userId = userId
        self.operationType = operationType
        self.opTimestamp = opTimestamp
        self.serverTimestamp = serverTimestamp
        self.deviceType = deviceType
        self.isSynced = isSynced
        self.bathScaleEntry = bathScaleEntry
        self.bodyMetricEntry = bodyMetricEntry
    }

    convenience init(from dto: BathScaleOperationDTO, isSynced: Bool = false, bathScaleMetrics: BathScaleEntry? = nil) {
        let bathScaleEntry = BathScaleEntry(from: dto)
        let bodyMetricEntry = BathScaleMetric(from: dto)
        let timestamp = dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
        self.init(
            id: UUID().uuidString,
            entryTimestamp: timestamp,
            userId: dto.accountId ?? "",
            operationType: dto.operationType ?? "",
            serverTimestamp: dto.serverTimestamp,
            isSynced: isSynced,
            bathScaleEntry: bathScaleEntry,
            bodyMetricEntry: bodyMetricEntry
        )
    }

    func toOperationDTO() -> BathScaleOperationDTO {
        return BathScaleOperationDTO(
            accountId: self.userId,
            bmr: self.bodyMetricEntry?.bmr,
            bmi: self.bathScaleEntry?.bmi,
            bodyFat: self.bathScaleEntry?.bodyFat,
            boneMass: self.bathScaleEntry?.boneMass,
            entryTimestamp: self.entryTimestamp,
            impedance: self.bodyMetricEntry?.impedance,
            metabolicAge: self.bodyMetricEntry?.metabolicAge,
            muscleMass: self.bathScaleEntry?.muscleMass,
            operationType: self.operationType,
            proteinPercent: self.bodyMetricEntry?.proteinPercent,
            pulse: self.bodyMetricEntry?.pulse,
            serverTimestamp: self.serverTimestamp,
            skeletalMusclePercent: self.bodyMetricEntry?.skeletalMusclePercent,
            source: self.bathScaleEntry?.source,
            subcutaneousFatPercent: self.bodyMetricEntry?.subcutaneousFatPercent,
            unit: self.bodyMetricEntry?.unit,
            visceralFatLevel: self.bodyMetricEntry?.visceralFatLevel,
            water: self.bathScaleEntry?.water,
            weight: self.bathScaleEntry?.weight
        )
    }
}
