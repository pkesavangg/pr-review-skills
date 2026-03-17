/// Stores BPM (Blood Pressure Monitor) data for each entry.
///
/// | Column Name    | Type   | Description                              |
/// |----------------|--------|------------------------------------------|
/// | systolic       | int    | Systolic blood pressure reading           |
/// | diastolic      | int    | Diastolic blood pressure reading          |
/// | pulse          | int    | Pulse/heart rate reading                  |
/// | meanArterial   | string | Mean arterial pressure (optional)         |
/// | note           | string | User note for the entry (optional)        |

import Foundation
import SwiftData

@Model
final class BpmEntry {
    var systolic: Int?
    var diastolic: Int?
    var pulse: Int?
    var meanArterial: String?
    var note: String?

    init(systolic: Int? = nil,
         diastolic: Int? = nil,
         pulse: Int? = nil,
         meanArterial: String? = nil,
         note: String? = nil) {
        self.systolic = systolic
        self.diastolic = diastolic
        self.pulse = pulse
        self.meanArterial = meanArterial
        self.note = note
    }

    convenience init(from dto: BpmOperationDTO) {
        self.init(
            systolic: dto.systolic.map { Int($0) },
            diastolic: dto.diastolic.map { Int($0) },
            pulse: dto.pulse.map { Int($0) },
            meanArterial: dto.meanArterial,
            note: dto.note
        )
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
