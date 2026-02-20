import Foundation

/// Repository for managing the last sync timestamp for entries per account, using KvStorageService.
final class EntryRepositoryLocal {
    private let kv: KvStorageService
    private let timestampKey = "entry_last_sync_timestamp"

    init(kv: KvStorageService = .shared) {
        self.kv = kv
    }

    /// Returns the last sync timestamp for the given account, or nil if not set.
    func getLastSyncTimestamp(accountId: String) async throws -> String? {
        let key = makeKey(accountId: accountId)
        return kv.getValue(forKey: key) as? String
    }

    /// Sets the last sync timestamp for the given account.
    func setLastSyncTimestamp(accountId: String, timestamp: String) async throws {
        let key = makeKey(accountId: accountId)
        kv.setValue(timestamp, forKey: key)
    }

    /// Clears the last sync timestamp for the given account.
    func clearLastSyncTimestamp(accountId: String) async throws {
        let key = makeKey(accountId: accountId)
        kv.clearValue(forKey: key)
    }

    /// Helper to build the KvStorageService key.
    private func makeKey(accountId: String) -> String {
        return "\(timestampKey)-\(accountId)"
    }
}
