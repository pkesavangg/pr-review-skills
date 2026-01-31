/// Table: scale
///
/// Stores user scale details for connected scales.
///
/// | Column Name   | Type    | Description                             |
/// | ------------- | ------- | --------------------------------------- |
/// | id            | string  | Unique scale ID (PK, FK to device.id)   |
/// | scale_type    | string  | Scale setup type (wifi, bluetooth,etc.) |
/// | body_comp     | boolean | Supports body composition               |

import Foundation
import SwiftData

@Model
final class BathScale {
    var scaleType: String? // Scale setup type (wifi, bluetooth,etc.)
    var bodyComp: Bool? // Supports body composition
    // Inverse relationship to Device
    var device: Device?

    init(scaleType: String?, bodyComp: Bool?, device: Device? = nil) {
        self.scaleType = scaleType
        self.bodyComp = bodyComp
        self.device = device
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
