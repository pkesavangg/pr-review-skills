import Foundation

/// User-selected default tab for the dashboard graph (Week / Month / Year / Total).
/// Persisted locally per account in UserDefaults; not synced to backend.
///
/// The preference is consulted only when the dashboard is initialized (typically
/// at app launch) — changing it from Settings does not retarget an already-open
/// graph.
enum DefaultGraphPeriodPreference {
    static let fallback: TimePeriod = .month

    /// Stored preference for the given account, or `fallback` if none has been set.
    /// Falls back to a global value when no account is active (e.g., before login).
    static func current(for accountId: String?) -> TimePeriod {
        let key = storageKey(for: accountId)
        guard
            let raw = KvStorageService.shared.getValue(forKey: key) as? String,
            let period = TimePeriod(rawValue: raw)
        else {
            return fallback
        }
        return period
    }

    /// Persists the preference for the given account.
    static func set(_ period: TimePeriod, for accountId: String?) {
        KvStorageService.shared.setValue(period.rawValue, forKey: storageKey(for: accountId))
    }

    private static func storageKey(for accountId: String?) -> String {
        guard let accountId, !accountId.isEmpty else {
            return KvStorageKeys.defaultGraphPeriod.rawValue
        }
        return KvStorageKeys.defaultGraphPeriodKey(for: accountId)
    }
}
