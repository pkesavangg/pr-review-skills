/// Stores blood pressure monitor measurement data for each BPM entry.
///
/// | Column Name    | Type   | Description                              |
/// |----------------|--------|------------------------------------------|
/// | systolic       | int    | Systolic blood pressure reading          |
/// | diastolic      | int    | Diastolic blood pressure reading         |
/// | meanArterial   | string | Mean arterial pressure                   |
/// | pulse          | int    | Heart rate or pulse                      |

import Foundation
import SwiftData

@Model
final class BPMEntry {
    /// Systolic blood pressure reading
    var systolic: Int
    /// Diastolic blood pressure reading
    var diastolic: Int
    /// Mean arterial pressure
    var meanArterial: String
    /// Heart rate or pulse
    var pulse: Int

    init(
        systolic: Int,
        diastolic: Int,
        meanArterial: String,
        pulse: Int
    ) {
        self.systolic = systolic
        self.diastolic = diastolic
        self.meanArterial = meanArterial
        self.pulse = pulse
    }

    convenience init(from dto: BpmOperationDTO) {
        self.init(
            systolic: dto.systolic.map { Int($0) } ?? 0,
            diastolic: dto.diastolic.map { Int($0) } ?? 0,
            meanArterial: dto.meanArterial ?? "",
            pulse: dto.pulse.map { Int($0) } ?? 0
        )
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
