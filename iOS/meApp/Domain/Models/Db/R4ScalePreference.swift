/// Table: r4_scale_preference
///
/// | Column Name            | Type     | Description                       |
/// | ---------------------- | -------- | --------------------------------- |
/// | id                     | string   | Unique scale ID (PK, FK to scale) |
/// | displayName            | string   | Display name                      |
/// | displayMetrics         | string[] | Displayed metrics                 |
/// | shouldFactoryReset     | boolean  | Factory reset flag                |
/// | shouldMeasureImpedance | boolean  | Impedance measurement flag        |
/// | shouldMeasurePulse     | boolean  | Pulse measurement flag            |
/// | timeFormat             | string   | Time format                       |
/// | tzOffset               | number   | Timezone offset                   |
/// | wifiFotaScheduleTime   | number   | FOTA schedule time                |
/// | updatedAt              | string   | Last update timestamp             |

import Foundation
import SwiftData

@Model
final class R4ScalePreference {
    @Attribute(.unique) var scaleId: String
    var displayName: String
    var displayMetrics: [String]
    var shouldFactoryReset: Bool
    var shouldMeasureImpedance: Bool
    var shouldMeasurePulse: Bool
    var timeFormat: String
    var tzOffset: Int
    var wifiFotaScheduleTime: Int
    var updatedAt: String?

    init(from dto: R4ScalePreferenceDTO) {
        self.scaleId = dto.scaleId
        self.displayName = dto.displayName
        self.displayMetrics = dto.displayMetrics
        self.shouldFactoryReset = dto.shouldFactoryReset
        self.shouldMeasureImpedance = dto.shouldMeasureImpedance
        self.shouldMeasurePulse = dto.shouldMeasurePulse
        self.timeFormat = dto.timeFormat
        self.tzOffset = dto.tzOffset
        self.wifiFotaScheduleTime = dto.wifiFotaScheduleTime
        self.updatedAt = dto.updatedAt
    }

    func toDTO() -> R4ScalePreferenceDTO {
        return R4ScalePreferenceDTO(
            scaleId: self.scaleId,
            displayName: self.displayName,
            displayMetrics: self.displayMetrics,
            shouldFactoryReset: self.shouldFactoryReset,
            shouldMeasureImpedance: self.shouldMeasureImpedance,
            shouldMeasurePulse: self.shouldMeasurePulse,
            timeFormat: self.timeFormat,
            tzOffset: self.tzOffset,
            wifiFotaScheduleTime: self.wifiFotaScheduleTime,
            updatedAt: self.updatedAt
        )
    }
}
