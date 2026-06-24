import Foundation
@testable import meApp

final class MockEntrySyncStore: EntrySyncStoreProtocol {
    private(set) var clearedAccountIds: [String] = []
    private(set) var setCalls = 0

    var timestamps: [String: String] = [:]

    func getLastSyncTimestamp(accountId: String) async throws -> String? {
        timestamps[accountId]
    }

    func setLastSyncTimestamp(accountId: String, timestamp: String) async throws {
        setCalls += 1
        timestamps[accountId] = timestamp
    }

    func clearLastSyncTimestamp(accountId: String) async throws {
        clearedAccountIds.append(accountId)
        timestamps.removeValue(forKey: accountId)
    }
}
