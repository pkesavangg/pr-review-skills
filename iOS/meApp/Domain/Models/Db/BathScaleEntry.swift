/// Stores scale-specific data for each entry.
///
/// | Column Name | Type   | Description                        |
/// |-------------|--------|------------------------------------|
/// | id          | int    | FK to entry.id (Primary Key)       |
/// | weight      | float  | Weight recorded in the entry       |
/// | bodyFat     | float  | Body fat percentage recorded       |
/// | muscleMass  | float  | Muscle mass recorded               |
/// | boneMass    | float  | Bone mass recorded                 |
/// | water       | float  | Water percentage recorded          |
/// | bmi         | float  | Body Mass Index                    |
/// | source      | string | Source data (e.g., manual, scale)  |

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
    var source: String?


    init(weight: Double? = nil,
         bodyFat: Double? = nil,
         muscleMass: Double? = nil,
         boneMass: Double? = nil,
         water: Double? = nil,
         bmi: Double? = nil,
         source: String? = nil) {
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.boneMass = boneMass
        self.water = water
        self.bmi = bmi
        self.source = source
    }

    convenience init(from dto: BathScaleOperationDTO) {
        self.init(
            weight: dto.weight,
            bodyFat: dto.bodyFat,
            muscleMass: dto.muscleMass,
            boneMass: dto.boneMass,
            water: dto.water,
            bmi: dto.bmi,
            source: dto.source
        )
    }
}
