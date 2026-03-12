//
//  EntryRepositoryLocalTests.swift
//  meAppTests
//

import Foundation
import Testing

@testable import meApp

@Suite(.serialized)
@MainActor
struct EntryRepositoryLocalTests {

    private let expectedKeyPrefix = "entry_last_sync_timestamp"

    // MARK: - makeSUT

    private func makeSUT(kv: KvStorageServiceProtocol? = nil) -> (sut: EntryRepositoryLocal, kv: MockKvStorageService) {
        let kvStore = kv as? MockKvStorageService ?? MockKvStorageService()
        let sut = EntryRepositoryLocal(kv: kvStore)
        return (sut, kvStore)
    }

    // MARK: - getLastSyncTimestamp

    @Test("getLastSyncTimestamp returns nil when key not set")
    func getLastSyncTimestamp_notSet_returnsNil() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == nil)
    }

    @Test("getLastSyncTimestamp returns stored value after setLastSyncTimestamp")
    func getLastSyncTimestamp_afterSet_returnsValue() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "2025-01-15T10:00:00Z")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == "2025-01-15T10:00:00Z")
    }

    @Test("getLastSyncTimestamp returns nil for different account when one is set")
    func getLastSyncTimestamp_differentAccount_returnsNil() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-A", timestamp: "ts-A")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-B")
        #expect(result == nil)
    }

    @Test("getLastSyncTimestamp key is scoped per accountId")
    func getLastSyncTimestamp_keyScopedPerAccount() async throws {
        let (sut, kv) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-1")
        try await sut.setLastSyncTimestamp(accountId: "acct-2", timestamp: "ts-2")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "ts-1")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-2") == "ts-2")
        #expect(kv.getValue(forKey: "\(expectedKeyPrefix)-acct-1") as? String == "ts-1")
        #expect(kv.getValue(forKey: "\(expectedKeyPrefix)-acct-2") as? String == "ts-2")
    }

    // MARK: - setLastSyncTimestamp

    @Test("setLastSyncTimestamp persists string pass-through")
    func setLastSyncTimestamp_persistsValue() async throws {
        let (sut, kv) = makeSUT()
        let timestamp = "2025-03-09T12:34:56Z"
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: timestamp)
        #expect(kv.getValue(forKey: "\(expectedKeyPrefix)-acct-1") as? String == timestamp)
    }

    @Test("setLastSyncTimestamp repeated set overwrites previous value")
    func setLastSyncTimestamp_repeatedSet_overwrites() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "first")
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "second")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == "second")
    }

    @Test("setLastSyncTimestamp empty string is stored and retrieved")
    func setLastSyncTimestamp_emptyString_storedAndRetrieved() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result?.isEmpty == true)
    }

    // MARK: - clearLastSyncTimestamp

    @Test("clearLastSyncTimestamp after clear get returns nil")
    func clearLastSyncTimestamp_afterClear_getReturnsNil() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == nil)
    }

    @Test("clearLastSyncTimestamp only clears requested account")
    func clearLastSyncTimestamp_clearsOnlyRequestedAccount() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts-1")
        try await sut.setLastSyncTimestamp(accountId: "acct-2", timestamp: "ts-2")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == nil)
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-2") == "ts-2")
    }

    @Test("clearLastSyncTimestamp repeated clear is idempotent")
    func clearLastSyncTimestamp_repeatedClear_idempotent() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "ts")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == nil)
    }

    @Test("clearLastSyncTimestamp on never-set account does not throw")
    func clearLastSyncTimestamp_neverSet_doesNotThrow() async throws {
        let (sut, _) = makeSUT()
        try await sut.clearLastSyncTimestamp(accountId: "acct-none")
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-none")
        #expect(result == nil)
    }

    // MARK: - CRUD flow and consistency

    @Test("full CRUD flow set get clear get is consistent")
    func fullCrudFlow_consistent() async throws {
        let (sut, _) = makeSUT()
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == nil)
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: "2025-01-01T00:00:00Z")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == "2025-01-01T00:00:00Z")
        try await sut.clearLastSyncTimestamp(accountId: "acct-1")
        #expect(try await sut.getLastSyncTimestamp(accountId: "acct-1") == nil)
    }

    @Test("multiple accounts isolated and non-destructive")
    func multipleAccounts_isolatedAndNonDestructive() async throws {
        let (sut, _) = makeSUT()
        try await sut.setLastSyncTimestamp(accountId: "a1", timestamp: "t1")
        try await sut.setLastSyncTimestamp(accountId: "a2", timestamp: "t2")
        try await sut.setLastSyncTimestamp(accountId: "a1", timestamp: "t1-updated")
        try await sut.clearLastSyncTimestamp(accountId: "a2")
        #expect(try await sut.getLastSyncTimestamp(accountId: "a1") == "t1-updated")
        #expect(try await sut.getLastSyncTimestamp(accountId: "a2") == nil)
    }

    @Test("timestamp payload long string is stored and retrieved consistently")
    func timestampPayload_longString_consistent() async throws {
        let (sut, _) = makeSUT()
        let longTs = String(repeating: "2025-03-09T12:00:00.000Z", count: 5)
        try await sut.setLastSyncTimestamp(accountId: "acct-1", timestamp: longTs)
        let result = try await sut.getLastSyncTimestamp(accountId: "acct-1")
        #expect(result == longTs)
    }
}
