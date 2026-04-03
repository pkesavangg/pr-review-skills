/// Stores additional scale and BPM metrics for each entry.
///
/// | Column Name              | Type   | Description                    |
/// |--------------------------|--------|--------------------------------|
/// | id                       | int    | FK to entry.id (Primary Key)   |
/// | bmr                      | int    | Basal Metabolic Rate           |
/// | metabolicAge             | int    | Calculated metabolic age       |
/// | proteinPercent           | int    | Protein percentage in the body |
/// | pulse                    | int    | Heart rate or pulse            |
/// | skeletalMusclePercent    | int    | Percentage of skeletal muscle  |
/// | subcutaneousFatPercent   | int    | Subcutaneous fat percentage    |
/// | visceralFatLevel         | int    | Visceral fat level             |
/// | boneMass                 | int    | Bone mass                      |
/// | impedance                | int    | Bioelectrical impedance        |
/// | unit                     | string | Unit of measurement            |


import Foundation
import SwiftData

@Model
final class BathScaleMetric {

    /// Basal Metabolic Rate
    var bmr: Int?
    /// Calculated metabolic age
    var metabolicAge: Int?
    /// Protein percentage in the body
    var proteinPercent: Int?
    /// Heart rate or pulse
    var pulse: Int?
    /// Percentage of skeletal muscle
    var skeletalMusclePercent: Int?
    /// Subcutaneous fat percentage
    var subcutaneousFatPercent: Int?
    /// Visceral fat level
    var visceralFatLevel: Int?
    /// Bone mass
    var boneMass: Int?
    /// Bioelectrical impedance
    var impedance: Int?
    /// Unit of measurement
    var unit: String?
    init(
         bmr: Int? = nil,
         metabolicAge: Int? = nil,
         proteinPercent: Int? = nil,
         pulse: Int? = nil,
         skeletalMusclePercent: Int? = nil,
         subcutaneousFatPercent: Int? = nil,
         visceralFatLevel: Int? = nil,
         boneMass: Int? = nil,
         impedance: Int? = nil,
         unit: String? = nil) {
        self.bmr = bmr
        self.metabolicAge = metabolicAge
        self.proteinPercent = proteinPercent
        self.pulse = pulse
        self.skeletalMusclePercent = skeletalMusclePercent
        self.subcutaneousFatPercent = subcutaneousFatPercent
        self.visceralFatLevel = visceralFatLevel
        self.boneMass = boneMass
        self.impedance = impedance
        self.unit = unit
    }

    convenience init(from dto: BathScaleOperationDTO) {
        self.init(
            bmr: dto.bmr.map { Int($0) },
            metabolicAge: dto.metabolicAge.map { Int($0) },
            proteinPercent: dto.proteinPercent.map { Int($0) },
            pulse: dto.pulse.map { Int($0) },
            skeletalMusclePercent: dto.skeletalMusclePercent.map { Int($0) },
            subcutaneousFatPercent: dto.subcutaneousFatPercent.map { Int($0) },
            visceralFatLevel: dto.visceralFatLevel.map { Int($0) },
            boneMass: dto.boneMass.map { Int($0) },
            impedance: dto.impedance.map { Int($0) },
            unit: dto.unit
        )
    }

    convenience init(from dto: BpmOperationDTO) {
        self.init(
            pulse: dto.pulse.map { Int($0) },
            unit: dto.unit
        )
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
