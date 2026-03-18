import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct EntryRepositoryLocalTests {

    // MARK: - setLastSyncTimestamp (Save)

    @Test("setLastSyncTimestamp saves timestamp for account")
    func setTimestampSavesValue() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "2026-03-01T08:00:00Z")

        let stored = kv.getValue(forKey: "entry_last_sync_timestamp-acct-1") as? String
        #expect(stored == "2026-03-01T08:00:00Z")
    }

    @Test("setLastSyncTimestamp constructs key with correct prefix and accountId")
    func setTimestampKeyFormat() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)

        try await sut.setLastSyncTimestamp(accountId: "user-42", timestamp: "ts-1")

        #expect(kv.getAllKeys() == ["entry_last_sync_timestamp-user-42"])
    }

    // MARK: - getLastSyncTimestamp (Read)

    @Test("getLastSyncTimestamp returns saved timestamp")
    func getTimestampReturnsSavedValue() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "2026-03-01T08:00:00Z")

        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")

        #expect(result == "2026-03-01T08:00:00Z")
    }

    @Test("getLastSyncTimestamp returns nil when no timestamp exists")
    func getTimestampReturnsNilForMissing() async throws {
        let sut = makeSUT()

        let result = try await sut.getLastSyncTimestamp(accountId: "nonexistent")

        #expect(result == nil)
    }

    @Test("getLastSyncTimestamp returns nil when stored value is non-string type")
    func getTimestampReturnsNilForNonString() async throws {
        let kv = MockKvStorageService()
        // Manually insert a non-string value at the expected key
        kv.setValue(12345, forKey: "entry_last_sync_timestamp-acct-1")
        let sut = makeSUT(kv: kv)

        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")

        #expect(result == nil)
    }

    // MARK: - clearLastSyncTimestamp (Clear)

    @Test("clearLastSyncTimestamp removes stored timestamp")
    func clearTimestampRemovesValue() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "2026-03-01T08:00:00Z")

        try await sut.clearLastSyncTimestamp(accountId: "acct-1")

        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == nil)
    }

    @Test("clearLastSyncTimestamp is safe when no timestamp exists")
    func clearTimestampNoopForMissing() async throws {
        let sut = makeSUT()

        try await sut.clearLastSyncTimestamp(accountId: "nonexistent")

        let result = try await sut.getLastSyncTimestamp(accountId: "nonexistent")
        #expect(result == nil)
    }

    @Test("clearLastSyncTimestamp only removes target account key")
    func clearTimestampDoesNotAffectOtherAccounts() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-1")
        try await sut.setLastSyncTimestamp(accountId: "acct-2", timestamp: "ts-2")

        try await sut.clearLastSyncTimestamp(accountId: "acct-1")

        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == nil)
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-2") == "ts-2")
    }

    // MARK: - Account Scoping

    @Test("timestamps are isolated per account")
    func timestampsIsolatedPerAccount() async throws {
        let sut = makeSUT()

        try await sut.setLastSyncTimestamp(accountId: "acct-A", timestamp: "ts-A")
        try await sut.setLastSyncTimestamp(accountId: "acct-B", timestamp: "ts-B")

        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-A") == "ts-A")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-B") == "ts-B")
    }

    @Test("setting timestamp for one account does not affect another")
    func setTimestampDoesNotCrossContaminate() async throws {
        let sut = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "original")

        try await sut.setLastSyncTimestamp(accountId: "acct-2", timestamp: "different")

        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "original")
    }

    @Test("different accounts produce different storage keys")
    func differentAccountsDifferentKeys() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-1")
        try await sut.setLastSyncTimestamp(accountId: "acct-2", timestamp: "ts-2")

        let keys = kv.getAllKeys().sorted()
        #expect(keys == ["entry_last_sync_timestamp-acct-1", "entry_last_sync_timestamp-acct-2"])
    }

    // MARK: - Overwrite Behavior

    @Test("setting new timestamp overwrites previous value")
    func setTimestampOverwritesPrevious() async throws {
        let sut = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "old-ts")

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "new-ts")

        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == "new-ts")
    }

    @Test("overwrite does not create duplicate keys")
    func overwriteNoDuplicateKeys() async throws {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "first")
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "second")
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "third")

        #expect(kv.getAllKeys().count == 1)
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "third")
    }

    // MARK: - Re-run Safety (Repeated Operations)

    @Test("repeated set-get cycles produce consistent results")
    func repeatedSetGetConsistent() async throws {
        let sut = makeSUT()

        for i in 1...10 {
            let ts = "2026-03-\(String(format: "%02d", i))T00:00:00Z"
            try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: ts)
            let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
            #expect(result == ts)
        }
    }

    @Test("set after clear restores value correctly")
    func setAfterClearRestoresValue() async throws {
        let sut = makeSUT()

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-1")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == nil)

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-2")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "ts-2")
    }

    @Test("clear is idempotent")
    func clearIdempotent() async throws {
        let sut = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-1")

        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")

        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == nil)
    }

    // MARK: - Edge Cases

    @Test("handles empty accountId string")
    func emptyAccountId() async throws {
        let sut = makeSUT()

        try await sut.setLastSyncTimestamp(accountId: "", timestamp: "ts-empty")
        let result = try await sut.getLastSyncTimestamp(accountId: "")

        #expect(result == "ts-empty")
    }

    @Test("handles empty timestamp string")
    func emptyTimestamp() async throws {
        let sut = makeSUT()

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")

        #expect(result == "")
    }

    @Test("handles accountId with special characters")
    func specialCharacterAccountId() async throws {
        let sut = makeSUT()
        let specialId = "user@domain.com/123"

        try await sut.setLastSyncTimestamp(accountId: specialId, timestamp: "ts-special")
        let result = try await sut.getLastSyncTimestamp(accountId: specialId)

        #expect(result == "ts-special")
    }

    @Test("handles very long timestamp string")
    func longTimestamp() async throws {
        let sut = makeSUT()
        let longTs = String(repeating: "2026-01-01T00:00:00Z ", count: 100)

        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: longTs)
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")

        #expect(result == longTs)
    }

    // MARK: - Multiple Accounts Lifecycle

    @Test("full lifecycle across multiple accounts")
    func multiAccountLifecycle() async throws {
        let sut = makeSUT()
        let accounts = ["acct-1", "acct-2", "acct-3"]

        // Set timestamps for all accounts
        for (i, acct) in accounts.enumerated() {
            try await sut.setLastSyncTimestamp(accountId: acct, timestamp: "ts-\(i)")
        }

        // Verify all stored
        for (i, acct) in accounts.enumerated() {
            #expect(try await sut.getLastSyncTimestamp(accountId: acct) == "ts-\(i)")
        }

        // Clear middle account
        try await sut.clearLastSyncTimestamp(accountId: "acct-2")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "ts-0")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-2") == nil)
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-3") == "ts-2")

        // Update first account
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-updated")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "ts-updated")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-3") == "ts-2")
    }

    // MARK: - Persistence Failure Handling (Type Safety)

    @Test("getValue returns nil when backing store has wrong type for key")
    func wrongTypeInBackingStore() async throws {
        let kv = MockKvStorageService()
        kv.setValue(["array", "value"], forKey: "entry_last_sync_timestamp-acct-1")
        let sut = makeSUT(kv: kv)

        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")

        #expect(result == nil)
    }

    @Test("getValue returns nil when backing store has boolean for key")
    func booleanInBackingStore() async throws {
        let kv = MockKvStorageService()
        kv.setValue(true, forKey: "entry_last_sync_timestamp-acct-1")
        let sut = makeSUT(kv: kv)

        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")

        #expect(result == nil)
    }

    // MARK: - Helpers

    private func makeSUT(kv: KvStorageServiceProtocol = MockKvStorageService()) -> EntryRepositoryLocal {
        EntryRepositoryLocal(kv: kv)
    }
}
