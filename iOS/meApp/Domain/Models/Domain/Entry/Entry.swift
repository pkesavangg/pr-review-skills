import Foundation
import SwiftData

@Model
final class Entry {
    @Attribute(.unique) var entryTimestamp: String
    var accountId: String?
    var operationType: String?
    var weight: Double?
    var bodyFat: Double?
    var muscleMass: Double?
    var boneMass: Double?
    var water: Double?
    var bmi: Double?
    var source: String?
    var unit: String?
    var impedance: Double?
    var pulse: Double?
    var visceralFatLevel: Double?
    var subcutaneousFatPercent: Double?
    var proteinPercent: Double?
    var skeletalMusclePercent: Double?
    var bmr: Double?
    var metabolicAge: Double?
    var serverTimestamp: String?
    var isSynced: Bool = false

    init(from dto: OperationDTO, isSynced: Bool = false) {
        self.entryTimestamp = dto.entryTimestamp ?? UUID().uuidString
        self.accountId = dto.accountId
        self.operationType = dto.operationType
        self.weight = dto.weight
        self.bodyFat = dto.bodyFat
        self.muscleMass = dto.muscleMass
        self.boneMass = dto.boneMass
        self.water = dto.water
        self.bmi = dto.bmi
        self.source = dto.source
        self.unit = dto.unit
        self.impedance = dto.impedance
        self.pulse = dto.pulse
        self.visceralFatLevel = dto.visceralFatLevel
        self.subcutaneousFatPercent = dto.subcutaneousFatPercent
        self.proteinPercent = dto.proteinPercent
        self.skeletalMusclePercent = dto.skeletalMusclePercent
        self.bmr = dto.bmr
        self.metabolicAge = dto.metabolicAge
        self.serverTimestamp = dto.serverTimestamp
        self.isSynced = isSynced
    }

    toOperationDTO() -> OperationDTO {
        return OperationDTO(
            entryTimestamp: self.entryTimestamp,
            accountId: self.accountId,
            operationType: self.operationType,
            weight: self.weight,
            bodyFat: self.bodyFat,
            muscleMass: self.muscleMass,
            boneMass: self.boneMass,
            water: self.water,
            bmi: self.bmi,
            source: self.source,
            unit: self.unit,
            impedance: self.impedance,
            pulse: self.pulse,
            visceralFatLevel: self.visceralFatLevel,
            subcutaneousFatPercent: self.subcutaneousFatPercent,
            proteinPercent: self.proteinPercent,
            skeletalMusclePercent: self.skeletalMusclePercent,
            bmr: self.bmr,
            metabolicAge: self.metabolicAge,
            serverTimestamp: self.serverTimestamp
        )
    }
}
