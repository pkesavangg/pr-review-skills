import Foundation

struct R4ScalePreferenceDTO: Codable, Sendable {
    var scaleId: String?
    var displayName: String
    var displayMetrics: [String]
    var shouldFactoryReset: Bool
    var shouldMeasureImpedance: Bool
    var shouldMeasurePulse: Bool
    var timeFormat: String
    var tzOffset: Int
    var wifiFotaScheduleTime: Int?
    var updatedAt: String?
    var isTemporary: Bool?
    var isSynced: Bool? // Local-only field — not sent to API

    enum CodingKeys: String, CodingKey {
        case scaleId, displayName, displayMetrics, shouldFactoryReset
        case shouldMeasureImpedance, shouldMeasurePulse, timeFormat
        case tzOffset, wifiFotaScheduleTime, updatedAt, isTemporary
        // isSynced intentionally excluded — local-only field
    }
}
