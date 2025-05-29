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
    @Attribute(.unique) var id: String // Unique scale ID (PK, FK to device.id)
    var scaleType: String? // Scale setup type (wifi, bluetooth,etc.)
    var bodyComp: Bool? // Supports body composition
    
    init(id: String, scaleType: String?, bodyComp: Bool?) {
        self.id = id
        self.scaleType = scaleType
        self.bodyComp = bodyComp
    }
}
