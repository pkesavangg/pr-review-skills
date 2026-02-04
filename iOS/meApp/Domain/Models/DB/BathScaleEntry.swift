/// Stores scale-specific data for each entry.
///
/// | Column Name | Type   | Description                        |
/// |-------------|--------|------------------------------------|
/// | id          | int    | FK to entry.id (Primary Key)       |
/// | weight      | int    | Weight recorded in the entry       |
/// | bodyFat     | int    | Body fat percentage recorded       |
/// | muscleMass  | int    | Muscle mass recorded               |
/// | water       | int    | Water percentage recorded          |
/// | bmi         | int    | Body Mass Index                    |
/// | source      | string | Source data (e.g., manual, scale)  |

import Foundation
import SwiftData

@Model
final class BathScaleEntry {
    var weight: Int?
    var bodyFat: Int?
    var muscleMass: Int?
    var water: Int?
    var bmi: Int?
    var source: String?


    init(weight: Int? = nil,
         bodyFat: Int? = nil,
         muscleMass: Int? = nil,
         water: Int? = nil,
         bmi: Int? = nil,
         source: String? = nil) {
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.water = water
        self.bmi = bmi
        self.source = source
    }

    convenience init(from dto: BathScaleOperationDTO) {
        self.init(
            weight: dto.weight.map { Int($0) },
            bodyFat: dto.bodyFat.map { Int($0) },
            muscleMass: dto.muscleMass.map { Int($0) },
            water: dto.water.map { Int($0) },
            bmi: dto.bmi.map { Int($0) },
            source: dto.source
        )
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
