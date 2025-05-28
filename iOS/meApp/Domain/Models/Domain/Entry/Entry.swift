import Foundation
import SwiftData

@Model
final class Entry {
    @Attribute(.unique) var id: String
    var entryTimestamp: String
    var accountId: String?
    var operationType: String?
    var source: String?
    var unit: String?
    var serverTimestamp: String?
    var isSynced: Bool = false
    @Relationship var bathScaleMetrics: BathScaleEntry?

    init(id: String = UUID().uuidString,
         entryTimestamp: String,
         accountId: String? = nil,
         operationType: String? = nil,
         source: String? = nil,
         unit: String? = nil,
         serverTimestamp: String? = nil,
         isSynced: Bool = false,
         bathScaleMetrics: BathScaleEntry? = nil) {
        self.id = id
        self.entryTimestamp = entryTimestamp
        self.accountId = accountId
        self.operationType = operationType
        self.source = source
        self.unit = unit
        self.serverTimestamp = serverTimestamp
        self.isSynced = isSynced
        self.bathScaleMetrics = bathScaleMetrics
    }

    convenience init(from dto: BathScaleOperationDTO, isSynced: Bool = false, bathScaleMetrics: BathScaleEntry? = nil) {
        let bathScaleMetrics = BathScaleEntry(from: dto)
        let timestamp = dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
        self.init(
            id: UUID().uuidString,
            entryTimestamp: timestamp,
            accountId: dto.accountId,
            operationType: dto.operationType,
            source: dto.source,
            unit: dto.unit,
            serverTimestamp: dto.serverTimestamp,
            isSynced: isSynced,
            bathScaleMetrics: bathScaleMetrics
        )
    }

    func toOperationDTO() -> BathScaleOperationDTO {
        return BathScaleOperationDTO(
            accountId: self.accountId,
            bmr: self.bathScaleMetrics?.bmr,
            bmi: self.bathScaleMetrics?.bmi,
            bodyFat: self.bathScaleMetrics?.bodyFat,
            boneMass: self.bathScaleMetrics?.boneMass,
            entryTimestamp: self.entryTimestamp,
            impedance: self.bathScaleMetrics?.impedance,
            metabolicAge: self.bathScaleMetrics?.metabolicAge,
            muscleMass: self.bathScaleMetrics?.muscleMass,
            operationType: self.operationType,
            proteinPercent: self.bathScaleMetrics?.proteinPercent,
            pulse: self.bathScaleMetrics?.pulse,
            serverTimestamp: self.serverTimestamp,
            skeletalMusclePercent: self.bathScaleMetrics?.skeletalMusclePercent,
            source: self.source,
            subcutaneousFatPercent: self.bathScaleMetrics?.subcutaneousFatPercent,
            unit: self.unit,
            visceralFatLevel: self.bathScaleMetrics?.visceralFatLevel,
            water: self.bathScaleMetrics?.water,
            weight: self.bathScaleMetrics?.weight
        )
    }
}
