/// Table: scale
///
/// | Column Name | Type    | Description                             |
/// | ----------- | ------- | --------------------------------------- |
/// | id          | string  | Unique scale ID (PK, FK to device.id)   |
/// | scaleType   | string  | Scale setup type (wifi, bluetooth,etc.) |
/// | bodyComp    | boolean | Supports body composition               |

import Foundation
import SwiftData

@Model
final class BathScale {
    @Attribute(.unique) var id: String
    var scaleType: String? 
    var bodyComp: Bool?
    
    init(id: String, scaleType: String?, bodyComp: Bool?) {
        self.id = id
        self.scaleType = scaleType
        self.bodyComp = bodyComp
    }
}
