import Foundation

protocol EntrySyncStoreProtocol {
    func getLastSyncTimestamp(accountId: String) async throws -> String?
    func setLastSyncTimestamp(accountId: String, timestamp: String) async throws
    func clearLastSyncTimestamp(accountId: String) async throws
}
