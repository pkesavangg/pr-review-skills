/// Stores additional scale metrics for each entry.
///
/// | Column Name            | Type   | Description                          |
/// |-----------------------|--------|--------------------------------------|
/// | id                    | int    | FK to entry.id (Primary Key)         |
/// | bmr                   | float  | Basal Metabolic Rate                 |
/// | metabolicAge          | float  | Calculated metabolic age             |
/// | proteinPercent        | float  | Protein percentage in the body       |
/// | pulse                 | float  | Heart rate or pulse                  |
/// | skeletalMusclePercent | float  | Percentage of skeletal muscle        |
/// | subcutaneousFatPercent| float  | Subcutaneous fat percentage          |
/// | visceralFatLevel      | float  | Visceral fat level                   |
/// | boneMass              | float  | Bone mass                            |
/// | impedance             | float  | Bioelectrical impedance              |
/// | unit                  | string | Unit of measurement                  |

import Foundation
import SwiftData

@Model
final class BathScaleMetric {
    var bmi: Double?
    var impedance: Double?
    var pulse: Double?
    var visceralFatLevel: Double?
    var subcutaneousFatPercent: Double?
    var proteinPercent: Double?
    var skeletalMusclePercent: Double?
    var bmr: Double?
    var metabolicAge: Double?
    var unit: String?

    init(impedance: Double? = nil,
         pulse: Double? = nil,
         visceralFatLevel: Double? = nil,
         subcutaneousFatPercent: Double? = nil,
         proteinPercent: Double? = nil,
         skeletalMusclePercent: Double? = nil,
         bmr: Double? = nil,
         metabolicAge: Double? = nil,
         unit: String? = nil) {
        self.impedance = impedance
        self.pulse = pulse
        self.visceralFatLevel = visceralFatLevel
        self.subcutaneousFatPercent = subcutaneousFatPercent
        self.proteinPercent = proteinPercent
        self.skeletalMusclePercent = skeletalMusclePercent
        self.bmr = bmr
        self.metabolicAge = metabolicAge
        self.unit = unit
    }

    convenience init(from dto: BathScaleOperationDTO) {
        self.init(
            impedance: dto.impedance,
            pulse: dto.pulse,
            visceralFatLevel: dto.visceralFatLevel,
            subcutaneousFatPercent: dto.subcutaneousFatPercent,
            proteinPercent: dto.proteinPercent,
            skeletalMusclePercent: dto.skeletalMusclePercent,
            bmr: dto.bmr,
            metabolicAge: dto.metabolicAge,
            unit: dto.unit
        )
    }
}
