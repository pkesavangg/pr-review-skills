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

    init(scaleType: String?, bodyComp: Bool?) {
        self.scaleType = scaleType
        self.bodyComp = bodyComp
    }
}

/// Marked @unchecked Sendable due to SwiftData’s built-in thread safety, allowing async/concurrent use.
extension BathScale: @unchecked Sendable {}
