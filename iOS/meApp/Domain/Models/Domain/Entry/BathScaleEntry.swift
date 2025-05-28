import Foundation
import SwiftData

@Model
final class BathScaleEntry {
    var weight: Double?
    var bodyFat: Double?
    var muscleMass: Double?
    var boneMass: Double?
    var water: Double?
    var bmi: Double?
    var impedance: Double?
    var pulse: Double?
    var visceralFatLevel: Double?
    var subcutaneousFatPercent: Double?
    var proteinPercent: Double?
    var skeletalMusclePercent: Double?
    var bmr: Double?
    var metabolicAge: Double?

    init(weight: Double? = nil,
         bodyFat: Double? = nil,
         muscleMass: Double? = nil,
         boneMass: Double? = nil,
         water: Double? = nil,
         bmi: Double? = nil,
         impedance: Double? = nil,
         pulse: Double? = nil,
         visceralFatLevel: Double? = nil,
         subcutaneousFatPercent: Double? = nil,
         proteinPercent: Double? = nil,
         skeletalMusclePercent: Double? = nil,
         bmr: Double? = nil,
         metabolicAge: Double? = nil) {
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.boneMass = boneMass
        self.water = water
        self.bmi = bmi
        self.impedance = impedance
        self.pulse = pulse
        self.visceralFatLevel = visceralFatLevel
        self.subcutaneousFatPercent = subcutaneousFatPercent
        self.proteinPercent = proteinPercent
        self.skeletalMusclePercent = skeletalMusclePercent
        self.bmr = bmr
        self.metabolicAge = metabolicAge
    }

    convenience init(from dto: BathScaleOperationDTO) {
        self.init(
            weight: dto.weight,
            bodyFat: dto.bodyFat,
            muscleMass: dto.muscleMass,
            boneMass: dto.boneMass,
            water: dto.water,
            bmi: dto.bmi,
            impedance: dto.impedance,
            pulse: dto.pulse,
            visceralFatLevel: dto.visceralFatLevel,
            subcutaneousFatPercent: dto.subcutaneousFatPercent,
            proteinPercent: dto.proteinPercent,
            skeletalMusclePercent: dto.skeletalMusclePercent,
            bmr: dto.bmr,
            metabolicAge: dto.metabolicAge
        )
    }
}
