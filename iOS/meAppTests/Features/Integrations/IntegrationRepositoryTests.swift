//
//  IntegrationRepositoryTests.swift
//  meAppTests
//

import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct IntegrationRepositoryTests {

    private let accountId = "acct-1"
    private let otherAccountId = "acct-2"

    // MARK: - makeSUT

    private func makeSUT() -> (sut: IntegrationRepository, kv: MockKvStorageService) {
        let kv = MockKvStorageService()
        let sut = IntegrationRepository(kvStorage: kv)
        return (sut, kv)
    }

    // MARK: - getIntegrationData

    @Test("getIntegrationData success: returns nil when no data stored")
    func getIntegrationData_noData_returnsNil() async throws {
        let (sut, _) = makeSUT()
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == nil)
    }

    @Test("getIntegrationData success: returns stored value after setIntegrationData")
    func getIntegrationData_afterSet_returnsStoredValue() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == info)
    }

    @Test("getIntegrationData success: returns nil for different account")
    func getIntegrationData_differentAccount_returnsNil() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        let result = try sut.getIntegrationData(accountId: otherAccountId)
        #expect(result == nil)
    }

    @Test("getIntegrationData success: returns correct data when multiple accounts stored")
    func getIntegrationData_multipleAccounts_returnsCorrectData() async throws {
        let (sut, _) = makeSUT()
        let infoA = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: accountId)
        let infoB = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: accountId, info: infoA)
        try sut.setIntegrationData(accountId: otherAccountId, info: infoB)
        #expect(try sut.getIntegrationData(accountId: accountId) == infoA)
        #expect(try sut.getIntegrationData(accountId: otherAccountId) == infoB)
    }

    @Test("getIntegrationData failure: throws on corrupted data")
    func getIntegrationData_corruptedData_throws() throws {
        let (sut, kv) = makeSUT()
        let key = KvStorageKeys.integrationInfoKey(for: accountId)
        kv.setValue(Data([0xDE, 0xAD, 0xBE, 0xEF]), forKey: key)
        #expect(throws: (any Error).self) {
            try sut.getIntegrationData(accountId: accountId)
        }
    }

    // MARK: - setIntegrationData

    @Test("setIntegrationData success: stores data retrievable via getIntegrationData")
    func setIntegrationData_storesData_retrievableViaGet() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == info)
    }

    @Test("setIntegrationData success: overwrites existing data (idempotent)")
    func setIntegrationData_repeatedSet_overwrites() async throws {
        let (sut, _) = makeSUT()
        let first = IntegrationTestFixtures.makeIntegrationInfo(isIntegrated: true)
        let second = IntegrationTestFixtures.makeIntegrationInfo(isIntegrated: false)
        try sut.setIntegrationData(accountId: accountId, info: first)
        try sut.setIntegrationData(accountId: accountId, info: second)
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == second)
    }

    @Test("setIntegrationData success: nil info clears the stored value")
    func setIntegrationData_nilInfo_clearsValue() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.setIntegrationData(accountId: accountId, info: nil)
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == nil)
    }

    @Test("setIntegrationData success: nil on unset account does not throw")
    func setIntegrationData_nilOnUnset_doesNotThrow() throws {
        let (sut, _) = makeSUT()
        #expect(throws: Never.self) {
            try sut.setIntegrationData(accountId: accountId, info: nil)
        }
    }

    @Test("setIntegrationData success: stores data under correct account-scoped key")
    func setIntegrationData_storesUnderCorrectKey() async throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        let expectedKey = KvStorageKeys.integrationInfoKey(for: accountId)
        #expect(kv.getValue(forKey: expectedKey) is Data)
    }

    @Test("setIntegrationData success: adds key to integrationKeys index on save")
    func setIntegrationData_addsKeyToIndex() async throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        let expectedKey = KvStorageKeys.integrationInfoKey(for: accountId)
        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String]
        #expect(keys?.contains(expectedKey) == true)
    }

    @Test("setIntegrationData success: removes key from integrationKeys index on nil")
    func setIntegrationData_nilRemovesKeyFromIndex() async throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.setIntegrationData(accountId: accountId, info: nil)
        let expectedKey = KvStorageKeys.integrationInfoKey(for: accountId)
        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        #expect(!keys.contains(expectedKey))
    }

    // MARK: - isIntegrationAlreadyUsed

    @Test("isIntegrationAlreadyUsed success: returns false when no integrations stored")
    func isIntegrationAlreadyUsed_noData_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .healthKit)
        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed success: returns false when integration assigned to same account")
    func isIntegrationAlreadyUsed_sameAccount_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: accountId)
        try sut.setIntegrationData(accountId: accountId, info: info)
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .healthKit)
        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed success: returns true when same type integrated by different account")
    func isIntegrationAlreadyUsed_sameTypeDifferentAccount_returnsTrue() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, isIntegrated: true, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: otherAccountId, info: info)
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .fitbit)
        #expect(result == true)
    }

    @Test("isIntegrationAlreadyUsed success: returns false when same type by different account but not integrated")
    func isIntegrationAlreadyUsed_sameTypeDifferentAccountNotIntegrated_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, isIntegrated: false, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: otherAccountId, info: info)
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .fitbit)
        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed success: returns false when different type integrated by different account")
    func isIntegrationAlreadyUsed_differentType_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .google, isIntegrated: true, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: otherAccountId, info: info)
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .fitbit)
        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed success: skips corrupted data and does not throw")
    func isIntegrationAlreadyUsed_corruptedData_skipsAndReturnsFalse() async throws {
        let (sut, kv) = makeSUT()
        let corruptKey = KvStorageKeys.integrationInfoKey(for: otherAccountId)
        kv.setValue(Data([0xFF, 0xFE]), forKey: corruptKey)
        kv.setValue([corruptKey], forKey: KvStorageKeys.integrationKeys.rawValue)
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .healthKit)
        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed success: returns false after integration cleared for other account")
    func isIntegrationAlreadyUsed_afterClear_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, isIntegrated: true, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: otherAccountId, info: info)
        try sut.clearIntegrationStatus(accountId: otherAccountId)
        let result = try sut.isIntegrationAlreadyUsed(accountId: accountId, type: .fitbit)
        #expect(result == false)
    }

    // MARK: - clearIntegrationStatus

    @Test("clearIntegrationStatus success: get returns nil after clear")
    func clearIntegrationStatus_afterClear_getReturnsNil() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.clearIntegrationStatus(accountId: accountId)
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == nil)
    }

    @Test("clearIntegrationStatus success: repeated clear is idempotent")
    func clearIntegrationStatus_repeatedClear_idempotent() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.clearIntegrationStatus(accountId: accountId)
        try sut.clearIntegrationStatus(accountId: accountId)
        let result = try sut.getIntegrationData(accountId: accountId)
        #expect(result == nil)
    }

    @Test("clearIntegrationStatus success: clears only specified account")
    func clearIntegrationStatus_clearsOnlySpecifiedAccount() async throws {
        let (sut, _) = makeSUT()
        let infoA = IntegrationTestFixtures.makeIntegrationInfo(assignedTo: accountId)
        let infoB = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: accountId, info: infoA)
        try sut.setIntegrationData(accountId: otherAccountId, info: infoB)
        try sut.clearIntegrationStatus(accountId: accountId)
        #expect(try sut.getIntegrationData(accountId: accountId) == nil)
        #expect(try sut.getIntegrationData(accountId: otherAccountId) == infoB)
    }

    @Test("clearIntegrationStatus success: does not throw when account was never set")
    func clearIntegrationStatus_neverSet_doesNotThrow() throws {
        let (sut, _) = makeSUT()
        #expect(throws: Never.self) {
            try sut.clearIntegrationStatus(accountId: accountId)
        }
    }

    @Test("clearIntegrationStatus success: removes key from integrationKeys index")
    func clearIntegrationStatus_removesKeyFromIndex() async throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.clearIntegrationStatus(accountId: accountId)
        let expectedKey = KvStorageKeys.integrationInfoKey(for: accountId)
        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        #expect(!keys.contains(expectedKey))
    }

    // MARK: - CRUD flow / idempotency

    @Test("full CRUD flow: set-get-clear-get is consistent")
    func fullCrudFlow_consistent() async throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        #expect(try sut.getIntegrationData(accountId: accountId) == nil)
        try sut.setIntegrationData(accountId: accountId, info: info)
        #expect(try sut.getIntegrationData(accountId: accountId) == info)
        try sut.clearIntegrationStatus(accountId: accountId)
        #expect(try sut.getIntegrationData(accountId: accountId) == nil)
    }

    @Test("repeated sync-style writes: do not duplicate integration records")
    func repeatedWrites_doNotDuplicateRecords() async throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.setIntegrationData(accountId: accountId, info: info)
        try sut.setIntegrationData(accountId: accountId, info: info)
        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        let expectedKey = KvStorageKeys.integrationInfoKey(for: accountId)
        let occurrences = keys.filter { $0 == expectedKey }.count
        #expect(occurrences == 1)
    }

    @Test("multiple accounts: isolated and non-destructive")
    func multipleAccounts_isolatedAndNonDestructive() async throws {
        let (sut, _) = makeSUT()
        let infoA = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: accountId)
        let infoB = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, assignedTo: otherAccountId)
        try sut.setIntegrationData(accountId: accountId, info: infoA)
        try sut.setIntegrationData(accountId: otherAccountId, info: infoB)
        let updatedInfoA = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: false, assignedTo: accountId)
        try sut.setIntegrationData(accountId: accountId, info: updatedInfoA)
        try sut.clearIntegrationStatus(accountId: otherAccountId)
        #expect(try sut.getIntegrationData(accountId: accountId) == updatedInfoA)
        #expect(try sut.getIntegrationData(accountId: otherAccountId) == nil)
    }
}
