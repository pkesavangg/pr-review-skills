/// Stores additional BPM metrics for each entry.
///
/// | Column Name    | Type   | Description                              |
/// |----------------|--------|------------------------------------------|
/// | irregularHb    | bool   | Whether irregular heartbeat was detected  |
/// | source         | string | Source of the measurement (e.g., manual)  |
/// | unit           | string | Unit of measurement                       |

import Foundation
import SwiftData

@Model
final class BpmMetric {
    /// Whether irregular heartbeat was detected
    var irregularHb: Bool?
    /// Source of the measurement (e.g., manual, device)
    var source: String?
    /// Unit of measurement
    var unit: String?

    init(irregularHb: Bool? = nil,
         source: String? = nil,
         unit: String? = nil) {
        self.irregularHb = irregularHb
        self.source = source
        self.unit = unit
    }

    convenience init(from dto: BpmOperationDTO) {
        self.init(
            irregularHb: dto.irregularHb,
            source: dto.source,
            unit: dto.unit
        )
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
