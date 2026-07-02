import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct IntegrationRepositoryTests {

    // MARK: - Insert Operations

    @Test("setIntegrationData stores integration info for account")
    func insertStoresData() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let result = try sut.getIntegrationData(accountId: "acct-1")
        #expect(result == info)
    }

    @Test("setIntegrationData adds key to integration keys registry")
    func insertAddsKeyToRegistry() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        let expectedKey = KvStorageKeys.integrationInfoKey(for: "acct-1")
        #expect(keys.contains(expectedKey))
    }

    @Test("setIntegrationData stores multiple accounts independently")
    func insertMultipleAccounts() throws {
        let (sut, _) = makeSUT()
        let info1 = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        let info2 = IntegrationTestFixtures.makeIntegrationInfo(type: .google, assignedTo: "acct-2")

        try sut.setIntegrationData(accountId: "acct-1", info: info1)
        try sut.setIntegrationData(accountId: "acct-2", info: info2)

        #expect(try sut.getIntegrationData(accountId: "acct-1") == info1)
        #expect(try sut.getIntegrationData(accountId: "acct-2") == info2)
    }

    @Test("setIntegrationData with nil info clears stored data")
    func insertNilClearsData() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        try sut.setIntegrationData(accountId: "acct-1", info: nil)

        #expect(try sut.getIntegrationData(accountId: "acct-1") == nil)
    }

    @Test("setIntegrationData with nil removes key from registry")
    func insertNilRemovesKeyFromRegistry() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        try sut.setIntegrationData(accountId: "acct-1", info: nil)

        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        let expectedKey = KvStorageKeys.integrationInfoKey(for: "acct-1")
        #expect(!keys.contains(expectedKey))
    }

    // MARK: - Update Operations

    @Test("setIntegrationData overwrites existing data for same account")
    func updateOverwritesExisting() throws {
        let (sut, _) = makeSUT()
        let original = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true)
        try sut.setIntegrationData(accountId: "acct-1", info: original)

        let updated = IntegrationTestFixtures.makeIntegrationInfo(type: .google, isIntegrated: false)
        try sut.setIntegrationData(accountId: "acct-1", info: updated)

        let result = try sut.getIntegrationData(accountId: "acct-1")
        #expect(result == updated)
        #expect(result != original)
    }

    @Test("update does not create duplicate keys in registry")
    func updateNoDuplicateKeys() throws {
        let (sut, kv) = makeSUT()
        let info1 = IntegrationTestFixtures.makeIntegrationInfo()
        let info2 = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit)

        try sut.setIntegrationData(accountId: "acct-1", info: info1)
        try sut.setIntegrationData(accountId: "acct-1", info: info2)

        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        let expectedKey = KvStorageKeys.integrationInfoKey(for: "acct-1")
        #expect(keys.filter { $0 == expectedKey }.count == 1)
    }

    @Test("update for one account does not affect another")
    func updateDoesNotAffectOther() throws {
        let (sut, _) = makeSUT()
        let info1 = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        let info2 = IntegrationTestFixtures.makeIntegrationInfo(type: .google, assignedTo: "acct-2")
        try sut.setIntegrationData(accountId: "acct-1", info: info1)
        try sut.setIntegrationData(accountId: "acct-2", info: info2)

        let updated = IntegrationTestFixtures.makeIntegrationInfo(type: .fitbit, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: updated)

        #expect(try sut.getIntegrationData(accountId: "acct-2") == info2)
    }

    // MARK: - Delete Operations

    @Test("clearIntegrationStatus removes stored data")
    func deleteRemovesData() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        try sut.clearIntegrationStatus(accountId: "acct-1")

        #expect(try sut.getIntegrationData(accountId: "acct-1") == nil)
    }

    @Test("clearIntegrationStatus removes key from registry")
    func deleteRemovesKeyFromRegistry() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        try sut.clearIntegrationStatus(accountId: "acct-1")

        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        let expectedKey = KvStorageKeys.integrationInfoKey(for: "acct-1")
        #expect(!keys.contains(expectedKey))
    }

    @Test("clearIntegrationStatus does not affect other accounts")
    func deleteDoesNotAffectOthers() throws {
        let (sut, _) = makeSUT()
        let info1 = IntegrationTestFixtures.makeIntegrationInfo(assignedTo: "acct-1")
        let info2 = IntegrationTestFixtures.makeIntegrationInfo(assignedTo: "acct-2")
        try sut.setIntegrationData(accountId: "acct-1", info: info1)
        try sut.setIntegrationData(accountId: "acct-2", info: info2)

        try sut.clearIntegrationStatus(accountId: "acct-1")

        #expect(try sut.getIntegrationData(accountId: "acct-1") == nil)
        #expect(try sut.getIntegrationData(accountId: "acct-2") == info2)
    }

    @Test("clearIntegrationStatus is safe when no data exists")
    func deleteSafeForMissing() throws {
        let (sut, _) = makeSUT()

        try sut.clearIntegrationStatus(accountId: "nonexistent")

        #expect(try sut.getIntegrationData(accountId: "nonexistent") == nil)
    }

    // MARK: - Integration State Persistence

    @Test("getIntegrationData returns nil when no data exists")
    func getReturnsNilForMissing() throws {
        let (sut, _) = makeSUT()

        let result = try sut.getIntegrationData(accountId: "nonexistent")

        #expect(result == nil)
    }

    @Test("getIntegrationData returns nil when stored value is not Data")
    func getReturnsNilForWrongType() throws {
        let (sut, kv) = makeSUT()
        let key = KvStorageKeys.integrationInfoKey(for: "acct-1")
        kv.setValue("not-data", forKey: key)

        let result = try sut.getIntegrationData(accountId: "acct-1")

        #expect(result == nil)
    }

    @Test("stored integration preserves all fields accurately")
    func statePreservesAllFields() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationInfo(
            type: .fitbit,
            isIntegrated: false,
            assignedTo: "user-42",
            deIntegrated: "2026-01-15"
        )

        try sut.setIntegrationData(accountId: "acct-1", info: info)
        let result = try sut.getIntegrationData(accountId: "acct-1")

        #expect(result?.type == .fitbit)
        #expect(result?.isIntegrated == false)
        #expect(result?.assignedTo == "user-42")
        #expect(result?.deIntegrated == "2026-01-15")
    }

    @Test("all integration types round-trip correctly")
    func allTypesRoundTrip() throws {
        let (sut, _) = makeSUT()
        let types: [IntegrationType] = [.google, .fitbit, .myFitnessPal, .underArmour, .healthConnect, .healthKit]

        for (i, type) in types.enumerated() {
            let info = IntegrationTestFixtures.makeIntegrationInfo(type: type, assignedTo: "acct-\(i)")
            try sut.setIntegrationData(accountId: "acct-\(i)", info: info)
            let result = try sut.getIntegrationData(accountId: "acct-\(i)")
            #expect(result == info)
        }
    }

    // MARK: - Fetch Consistency

    @Test("repeated reads return same data")
    func fetchConsistencyRepeatedReads() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        for _ in 1...5 {
            #expect(try sut.getIntegrationData(accountId: "acct-1") == info)
        }
    }

    @Test("isIntegrationAlreadyUsed returns false when no integrations exist")
    func isUsedReturnsFalseWhenEmpty() throws {
        let (sut, _) = makeSUT()

        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-1", type: .healthKit)

        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed returns false for same account")
    func isUsedReturnsFalseForSameAccount() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-1", type: .healthKit)

        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed returns true when different account has same type")
    func isUsedReturnsTrueForConflict() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-2", type: .healthKit)

        #expect(result == true)
    }

    @Test("isIntegrationAlreadyUsed returns false for different type")
    func isUsedReturnsFalseForDifferentType() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-2", type: .google)

        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed returns false when integration is not active")
    func isUsedReturnsFalseWhenNotIntegrated() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: false, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-2", type: .healthKit)

        #expect(result == false)
    }

    @Test("isIntegrationAlreadyUsed skips corrupted keys gracefully")
    func isUsedSkipsCorruptedKeys() throws {
        let (sut, kv) = makeSUT()
        // Store valid integration for acct-1
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        // Manually corrupt another key
        let corruptKey = KvStorageKeys.integrationInfoKey(for: "acct-corrupt")
        kv.setValue(Data([0xFF, 0xFE]), forKey: corruptKey)
        var keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        keys.append(corruptKey)
        kv.setValue(keys, forKey: KvStorageKeys.integrationKeys.rawValue)

        // Should still detect conflict from valid key
        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-2", type: .healthKit)
        #expect(result == true)
    }

    @Test("isIntegrationAlreadyUsed skips keys with non-Data values")
    func isUsedSkipsNonDataValues() throws {
        let (sut, kv) = makeSUT()
        // Store a non-Data value at a registered key
        let badKey = KvStorageKeys.integrationInfoKey(for: "acct-bad")
        kv.setValue("not-data", forKey: badKey)
        kv.setValue([badKey], forKey: KvStorageKeys.integrationKeys.rawValue)

        let result = try sut.isIntegrationAlreadyUsed(accountId: "acct-1", type: .healthKit)

        #expect(result == false)
    }

    // MARK: - Duplicate Prevention

    @Test("repeated inserts for same account produce single registry entry")
    func duplicatePreventionSingleRegistryEntry() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        for _ in 1...5 {
            try sut.setIntegrationData(accountId: "acct-1", info: info)
        }

        let keys = kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? []
        let expectedKey = KvStorageKeys.integrationInfoKey(for: "acct-1")
        #expect(keys.filter { $0 == expectedKey }.count == 1)
    }

    @Test("different accounts each get their own registry entry")
    func duplicatePreventionSeparateAccounts() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        try sut.setIntegrationData(accountId: "acct-1", info: info)
        try sut.setIntegrationData(accountId: "acct-2", info: info)

        let keys = Set(kv.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] ?? [])
        #expect(keys.count == 2)
        #expect(keys.contains(KvStorageKeys.integrationInfoKey(for: "acct-1")))
        #expect(keys.contains(KvStorageKeys.integrationInfoKey(for: "acct-2")))
    }

    // MARK: - Persistence Failure Handling

    @Test("getIntegrationData throws when stored data is invalid JSON")
    func getThrowsForCorruptData() throws {
        let (sut, kv) = makeSUT()
        let key = KvStorageKeys.integrationInfoKey(for: "acct-1")
        kv.setValue(Data([0xFF, 0xFE, 0x00]), forKey: key)

        #expect(throws: (any Error).self) {
            try sut.getIntegrationData(accountId: "acct-1")
        }
    }

    @Test("getIntegrationData throws for partial/malformed JSON data")
    func getThrowsForMalformedJSON() throws {
        let (sut, kv) = makeSUT()
        let key = KvStorageKeys.integrationInfoKey(for: "acct-1")
        let badJSON = try JSONSerialization.data(withJSONObject: ["type": "healthkit"])
        kv.setValue(badJSON, forKey: key)

        #expect(throws: (any Error).self) {
            try sut.getIntegrationData(accountId: "acct-1")
        }
    }

    // MARK: - Re-run Safety

    @Test("repeated set-get cycles produce consistent results")
    func rerunSetGetConsistent() throws {
        let (sut, _) = makeSUT()

        for i in 1...10 {
            let info = IntegrationTestFixtures.makeIntegrationInfo(
                type: i.isMultiple(of: 2) ? .google : .healthKit,
                isIntegrated: true,
                assignedTo: "acct-1"
            )
            try sut.setIntegrationData(accountId: "acct-1", info: info)
            let result = try sut.getIntegrationData(accountId: "acct-1")
            #expect(result == info)
        }
    }

    @Test("set after clear restores value correctly")
    func rerunSetAfterClear() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        try sut.setIntegrationData(accountId: "acct-1", info: info)
        try sut.clearIntegrationStatus(accountId: "acct-1")
        #expect(try sut.getIntegrationData(accountId: "acct-1") == nil)

        try sut.setIntegrationData(accountId: "acct-1", info: info)
        #expect(try sut.getIntegrationData(accountId: "acct-1") == info)
    }

    @Test("clear is idempotent")
    func rerunClearIdempotent() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        try sut.setIntegrationData(accountId: "acct-1", info: info)

        try sut.clearIntegrationStatus(accountId: "acct-1")
        try sut.clearIntegrationStatus(accountId: "acct-1")
        try sut.clearIntegrationStatus(accountId: "acct-1")

        #expect(try sut.getIntegrationData(accountId: "acct-1") == nil)
    }

    @Test("full lifecycle: insert, read, update, check conflict, clear")
    func fullLifecycle() throws {
        let (sut, _) = makeSUT()

        // Insert
        let info1 = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info1)
        #expect(try sut.getIntegrationData(accountId: "acct-1") == info1)

        // Update
        let info2 = IntegrationTestFixtures.makeIntegrationInfo(type: .google, assignedTo: "acct-1")
        try sut.setIntegrationData(accountId: "acct-1", info: info2)
        #expect(try sut.getIntegrationData(accountId: "acct-1") == info2)

        // Add second account and check conflict
        let info3 = IntegrationTestFixtures.makeIntegrationInfo(type: .google, assignedTo: "acct-2")
        try sut.setIntegrationData(accountId: "acct-2", info: info3)
        #expect(try sut.isIntegrationAlreadyUsed(accountId: "acct-3", type: .google) == true)

        // Clear first, second still exists
        try sut.clearIntegrationStatus(accountId: "acct-1")
        #expect(try sut.getIntegrationData(accountId: "acct-1") == nil)
        #expect(try sut.getIntegrationData(accountId: "acct-2") == info3)

        // After clearing acct-1, only acct-2 has google — still used
        #expect(try sut.isIntegrationAlreadyUsed(accountId: "acct-3", type: .google) == true)

        // Clear acct-2 — now no conflict
        try sut.clearIntegrationStatus(accountId: "acct-2")
        #expect(try sut.isIntegrationAlreadyUsed(accountId: "acct-3", type: .google) == false)
    }

    // MARK: - Edge Cases

    @Test("handles empty accountId")
    func emptyAccountId() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        try sut.setIntegrationData(accountId: "", info: info)
        let result = try sut.getIntegrationData(accountId: "")

        #expect(result == info)
    }

    @Test("handles accountId with special characters")
    func specialCharacterAccountId() throws {
        let (sut, _) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()
        let specialId = "user@domain.com/123"

        try sut.setIntegrationData(accountId: specialId, info: info)
        let result = try sut.getIntegrationData(accountId: specialId)

        #expect(result == info)
    }

    @Test("key format uses KvStorageKeys helper")
    func keyFormatMatchesExpected() throws {
        let (sut, kv) = makeSUT()
        let info = IntegrationTestFixtures.makeIntegrationInfo()

        try sut.setIntegrationData(accountId: "acct-1", info: info)

        let expectedKey = KvStorageKeys.integrationInfoKey(for: "acct-1")
        #expect(kv.getValue(forKey: expectedKey) != nil)
    }

    // MARK: - Helpers

    private func makeSUT(kv: MockKvStorageService = MockKvStorageService()) -> (IntegrationRepository, MockKvStorageService) {
        let sut = IntegrationRepository(kvStorage: kv)
        return (sut, kv)
    }
}
