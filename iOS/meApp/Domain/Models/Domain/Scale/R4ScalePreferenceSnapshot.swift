import Foundation

/// Value-type snapshot of R4ScalePreference. Sendable, safe across async boundaries.
struct R4ScalePreferenceSnapshot: Equatable, Sendable {
    let id: String
    let displayName: String
    let displayMetrics: [String]
    let shouldFactoryReset: Bool
    let shouldMeasureImpedance: Bool
    let shouldMeasurePulse: Bool
    let timeFormat: String
    let tzOffset: Int
    let wifiFotaScheduleTime: Int?
    let updatedAt: String?
    let isSynced: Bool
}
