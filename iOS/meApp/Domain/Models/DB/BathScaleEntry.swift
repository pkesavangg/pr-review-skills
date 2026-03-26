/// Stores scale and BPM measurement data for each entry.
///
/// | Column Name    | Type   | Description                              |
/// |----------------|--------|------------------------------------------|
/// | id             | int    | FK to entry.id (Primary Key)             |
/// | weight         | int    | Weight recorded in the entry             |
/// | bodyFat        | int    | Body fat percentage recorded             |
/// | muscleMass     | int    | Muscle mass recorded                     |
/// | water          | int    | Water percentage recorded                |
/// | bmi            | int    | Body Mass Index                          |
/// | source         | string | Source data (e.g., manual, scale)        |
/// | systolic       | int    | Systolic blood pressure reading          |
/// | diastolic      | int    | Diastolic blood pressure reading         |
/// | meanArterial   | string | Mean arterial pressure                   |
/// | note           | string | User note for the entry                  |

import Foundation
import SwiftData

@Model
final class BathScaleEntry {
    // MARK: - Weight fields
    var weight: Int?
    var bodyFat: Int?
    var muscleMass: Int?
    var water: Int?
    var bmi: Int?
    var source: String?
    var systolic: Int?
    var diastolic: Int?
    var meanArterial: String?
    var note: String?

    // MARK: - BP fields
    var systolic: Int?
    var diastolic: Int?
    var meanArterial: Int?
    var note: String?

    init(weight: Int? = nil,
         bodyFat: Int? = nil,
         muscleMass: Int? = nil,
         water: Int? = nil,
         bmi: Int? = nil,
         source: String? = nil,
         systolic: Int? = nil,
         diastolic: Int? = nil,
         meanArterial: String? = nil,
         note: String? = nil) {
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.water = water
        self.bmi = bmi
        self.source = source
        self.systolic = systolic
        self.diastolic = diastolic
        self.meanArterial = meanArterial
        self.note = note
    }

    convenience init(from dto: BathScaleOperationDTO) {
        self.init(
            weight: dto.weight.map { Int($0) },
            bodyFat: dto.bodyFat.map { Int($0) },
            muscleMass: dto.muscleMass.map { Int($0) },
            water: dto.water.map { Int($0) },
            bmi: dto.bmi.map { Int($0) },
            source: dto.source,
            systolic: dto.systolic.map { Int($0) },
            diastolic: dto.diastolic.map { Int($0) },
            meanArterial: dto.meanArterial.map { Int($0) }
        )
    }

    convenience init(from dto: BpmOperationDTO) {
        self.init(
            source: dto.source,
            systolic: dto.systolic.map { Int($0) },
            diastolic: dto.diastolic.map { Int($0) },
            meanArterial: dto.meanArterial,
            note: dto.note
        )
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
