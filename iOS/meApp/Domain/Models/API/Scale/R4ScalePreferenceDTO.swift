import Foundation

struct R4ScalePreferenceDTO: Codable {
    var scaleId: String
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
}
