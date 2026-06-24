import Foundation

extension R4ScalePreference {
    func toSnapshot() -> R4ScalePreferenceSnapshot {
        R4ScalePreferenceSnapshot(
            id: id,
            displayName: displayName,
            displayMetrics: displayMetrics,
            shouldFactoryReset: shouldFactoryReset,
            shouldMeasureImpedance: shouldMeasureImpedance,
            shouldMeasurePulse: shouldMeasurePulse,
            timeFormat: timeFormat,
            tzOffset: tzOffset,
            wifiFotaScheduleTime: wifiFotaScheduleTime,
            updatedAt: updatedAt,
            isSynced: isSynced
        )
    }
}
