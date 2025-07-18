/// Table: r4_scale_preference
///
/// | Column Name              | Type     | Description                       |
/// | ------------------------ | -------- | --------------------------------- |
/// | id                       | string   | Unique scale ID (PK, FK to scale) |
/// | display_name             | string   | Display name                      |
/// | display_metrics          | string[] | Displayed metrics                 |
/// | should_factory_reset     | boolean  | Factory reset flag                |
/// | should_measure_impedance | boolean  | Impedance measurement flag        |
/// | should_measure_pulse     | boolean  | Pulse measurement flag            |
/// | time_format              | string   | Time format                       |
/// | tz_offset                | number   | Timezone offset                   |
/// | wifi_fota_schedule_time  | number   | FOTA schedule time                |
/// | updated_at               | string   | Last update timestamp             |

import Foundation
import SwiftData

@Model
final class R4ScalePreference {
    @Attribute(.unique) var id: String // Unique scale ID (PK, FK to scale)
    var displayName: String // Display name
    var displayMetrics: [String] // Displayed metrics
    var shouldFactoryReset: Bool // Factory reset flag
    var shouldMeasureImpedance: Bool // Impedance measurement flag
    var shouldMeasurePulse: Bool // Pulse measurement flag
    var timeFormat: String // Time format
    var tzOffset: Int // Timezone offset
    var wifiFotaScheduleTime: Int // FOTA schedule time
    var updatedAt: String? // Last update timestamp
    var isSynced: Bool = false // Flag to check if the preference is synced with the server

    init(from dto: R4ScalePreferenceDTO) {

        self.id = dto.scaleId ?? ""
        self.displayName = dto.displayName
        self.displayMetrics = dto.displayMetrics
        self.shouldFactoryReset = dto.shouldFactoryReset
        self.shouldMeasureImpedance = dto.shouldMeasureImpedance
        self.shouldMeasurePulse = dto.shouldMeasurePulse
        self.timeFormat = dto.timeFormat
        self.tzOffset = dto.tzOffset
        self.wifiFotaScheduleTime = dto.wifiFotaScheduleTime ?? 0
        self.updatedAt = dto.updatedAt
    }
    
    /// Convenience initializer for creating a preference with all required properties
    init(scaleId: String, displayName: String, displayMetrics: [String], shouldFactoryReset: Bool, shouldMeasureImpedance: Bool, shouldMeasurePulse: Bool, timeFormat: String, tzOffset: Int, wifiFotaScheduleTime: Int, updatedAt: String?) {
        self.id = scaleId
        self.displayName = displayName
        self.displayMetrics = displayMetrics
        self.shouldFactoryReset = shouldFactoryReset
        self.shouldMeasureImpedance = shouldMeasureImpedance
        self.shouldMeasurePulse = shouldMeasurePulse
        self.timeFormat = timeFormat
        self.tzOffset = tzOffset
        self.wifiFotaScheduleTime = wifiFotaScheduleTime
        self.updatedAt = updatedAt
    }

    func toDTO() -> R4ScalePreferenceDTO {
        return R4ScalePreferenceDTO(
            scaleId: self.id,
            displayName: self.displayName,
            displayMetrics: self.displayMetrics,
            shouldFactoryReset: self.shouldFactoryReset,
            shouldMeasureImpedance: self.shouldMeasureImpedance,
            shouldMeasurePulse: self.shouldMeasurePulse,
            timeFormat: self.timeFormat,
            tzOffset: self.tzOffset,
            wifiFotaScheduleTime: self.wifiFotaScheduleTime
        )
    }
}

/// Marked @unchecked Sendable due to SwiftData’s built-in thread safety, allowing async/concurrent use.
extension R4ScalePreference: @unchecked Sendable {}
