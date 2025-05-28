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
    var isTemporary: Bool?

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
        self.isTemporary = dto.isTemporary 
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
            updatedAt: self.updatedAt,
            isTemporary: self.isTemporary
        )
    }
}
