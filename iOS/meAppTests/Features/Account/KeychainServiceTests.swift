import Foundation
@testable import meApp
import Security
import Testing

@Suite(.serialized)
@MainActor
struct KeychainServiceTests {
    @Test("tokens returns nil when stored payload cannot be decoded")
    func getTokensReturnsNilForMalformedStoredPayload() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)
        insertRawKeychainItem(
            serviceName: serviceName,
            accountKey: "tokens_account-1",
            data: Data("not-json".utf8)
        )

        #expect(sut.getTokens(for: "account-1") == nil)

        clearAllItems(for: serviceName)
    }

    @Test("tokens set and get are scoped per account")
    func tokensSetAndGetPerAccount() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)
        // swiftlint:disable:next no_hardcoded_credentials
        let accountOneTokens = makeTokens(accessToken: "access-1", refreshToken: "refresh-1", expiresAt: "2099-01-01T00:00:00Z")
        // swiftlint:disable:next no_hardcoded_credentials
        let accountTwoTokens = makeTokens(accessToken: "access-2", refreshToken: "refresh-2", expiresAt: "2099-02-02T00:00:00Z")

        sut.setTokens(accountOneTokens, for: "account-1")
        sut.setTokens(accountTwoTokens, for: "account-2")

        #expect(sut.getTokens(for: "account-1") == accountOneTokens)
        #expect(sut.getTokens(for: "account-2") == accountTwoTokens)
        #expect(sut.getTokens(for: "missing-account") == nil)

        clearAllItems(for: serviceName)
    }

    @Test("setting tokens again replaces existing value for the same account")
    func setTokensReplacesExistingValue() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)
        // swiftlint:disable:next no_hardcoded_credentials
        let originalTokens = makeTokens(accessToken: "access-old", refreshToken: "refresh-old", expiresAt: "2099-01-01T00:00:00Z")
        // swiftlint:disable:next no_hardcoded_credentials
        let updatedTokens = makeTokens(accessToken: "access-new", refreshToken: "refresh-new", expiresAt: "2099-12-31T00:00:00Z")

        sut.setTokens(originalTokens, for: "account-1")
        sut.setTokens(updatedTokens, for: "account-1")

        #expect(sut.getTokens(for: "account-1") == updatedTokens)

        clearAllItems(for: serviceName)
    }

    @Test("deleting tokens clears only the requested account")
    func deleteTokensClearsOnlyRequestedAccount() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)
        // swiftlint:disable:next no_hardcoded_credentials
        let accountOneTokens = makeTokens(accessToken: "access-1", refreshToken: "refresh-1", expiresAt: "2099-01-01T00:00:00Z")
        // swiftlint:disable:next no_hardcoded_credentials
        let accountTwoTokens = makeTokens(accessToken: "access-2", refreshToken: "refresh-2", expiresAt: "2099-02-02T00:00:00Z")

        sut.setTokens(accountOneTokens, for: "account-1")
        sut.setTokens(accountTwoTokens, for: "account-2")

        sut.deleteTokens(for: "account-1")

        #expect(sut.getTokens(for: "account-1") == nil)
        #expect(sut.getTokens(for: "account-2") == accountTwoTokens)

        clearAllItems(for: serviceName)
    }

    @Test("FCM token returns nil when stored payload is not utf8")
    func getFCMReturnsNilForMalformedStoredPayload() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)
        insertRawKeychainItem(
            serviceName: serviceName,
            accountKey: "fcm_account-1",
            data: Data([0xFF, 0xD8, 0xFF])
        )

        #expect(sut.getFCMToken(for: "account-1") == nil)

        clearAllItems(for: serviceName)
    }

    @Test("FCM token set get and delete are scoped per account")
    func fcmTokensSetGetAndDeletePerAccount() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)

        sut.setFCMToken("fcm-1", for: "account-1")
        sut.setFCMToken("fcm-2", for: "account-2")

        #expect(sut.getFCMToken(for: "account-1") == "fcm-1")
        #expect(sut.getFCMToken(for: "account-2") == "fcm-2")
        #expect(sut.getFCMToken(for: "missing-account") == nil)

        sut.deleteFCMToken(for: "account-1")

        #expect(sut.getFCMToken(for: "account-1") == nil)
        #expect(sut.getFCMToken(for: "account-2") == "fcm-2")

        clearAllItems(for: serviceName)
    }

    @Test("logout style cleanup removes both auth tokens and FCM token for an account")
    func logoutCleanupRemovesTokensAndFCMToken() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)
        let tokens = makeTokens()

        sut.setTokens(tokens, for: "account-1")
        sut.setFCMToken("fcm-1", for: "account-1")
        // swiftlint:disable:next no_hardcoded_credentials
        sut.setTokens(makeTokens(accessToken: "other-access", refreshToken: "other-refresh", expiresAt: "2099-03-03T00:00:00Z"), for: "account-2")
        sut.setFCMToken("fcm-2", for: "account-2")

        sut.deleteTokens(for: "account-1")
        sut.deleteFCMToken(for: "account-1")

        #expect(sut.getTokens(for: "account-1") == nil)
        #expect(sut.getFCMToken(for: "account-1") == nil)
        #expect(sut.getTokens(for: "account-2")?.accessToken == "other-access")
        #expect(sut.getFCMToken(for: "account-2") == "fcm-2")

        clearAllItems(for: serviceName)
    }

    @Test("deleting missing account items is a no-op")
    func deletingMissingAccountItemsIsNoOp() {
        let serviceName = makeServiceName()
        clearAllItems(for: serviceName)

        let sut = KeychainService(serviceName: serviceName)

        sut.deleteTokens(for: "missing-account")
        sut.deleteFCMToken(for: "missing-account")

        #expect(sut.getTokens(for: "missing-account") == nil)
        #expect(sut.getFCMToken(for: "missing-account") == nil)

        clearAllItems(for: serviceName)
    }

    @Test("setTokens updates an existing item when add reports duplicate")
    func setTokensFallsBackToUpdateWhenItemExists() {
        let logger = MockLoggerService()
        let keychainAccess = MockKeychainAccess()
        keychainAccess.addStatus = errSecDuplicateItem
        let sut = KeychainService(
            serviceName: makeServiceName(),
            keychainAccess: keychainAccess,
            logger: logger
        )

        sut.setTokens(makeTokens(), for: "account-1")

        #expect(keychainAccess.addCalls == 1)
        #expect(keychainAccess.updateCalls == 1)
        #expect(logger.messages.isEmpty)
    }

    @Test("setTokens logs when keychain write fails")
    func setTokensLogsWhenWriteFails() async {
        let logger = MockLoggerService()
        let keychainAccess = MockKeychainAccess()
        keychainAccess.addStatus = errSecParam
        let sut = KeychainService(
            serviceName: makeServiceName(),
            keychainAccess: keychainAccess,
            logger: logger
        )

        sut.setTokens(makeTokens(), for: "account-1")
        await Task.yield()

        #expect(keychainAccess.addCalls == 1)
        #expect(logger.messages.contains { $0.contains("Keychain setData failed: accountKey=tokens_account-1") })
    }

    @Test("deleteTokens logs when keychain delete fails")
    func deleteTokensLogsWhenDeleteFails() async {
        let logger = MockLoggerService()
        let keychainAccess = MockKeychainAccess()
        keychainAccess.deleteStatus = errSecParam
        let sut = KeychainService(
            serviceName: makeServiceName(),
            keychainAccess: keychainAccess,
            logger: logger
        )

        sut.deleteTokens(for: "account-1")
        await Task.yield()

        #expect(keychainAccess.deleteCalls == 1)
        #expect(logger.messages.contains { $0.contains("Keychain deleteItem failed: accountKey=tokens_account-1") })
    }

    @Test("default service name and shared instance store tokens correctly")
    func defaultServiceNameAndSharedInstanceWork() {
        let defaultServiceName = (Bundle.main.bundleIdentifier ?? "meApp") + ".tokens"
        clearAllItems(for: defaultServiceName)

        // swiftlint:disable:next no_hardcoded_credentials
        let tokens = makeTokens(accessToken: "shared-access", refreshToken: "shared-refresh", expiresAt: "2099-06-01T00:00:00Z")
        KeychainService.shared.setTokens(tokens, for: "shared-account")

        let fetchedFromShared = KeychainService.shared.getTokens(for: "shared-account")
        let fetchedFromFreshInstance = KeychainService().getTokens(for: "shared-account")

        #expect(fetchedFromShared == tokens)
        #expect(fetchedFromFreshInstance == tokens)

        KeychainService.shared.deleteTokens(for: "shared-account")
        #expect(KeychainService.shared.getTokens(for: "shared-account") == nil)

        clearAllItems(for: defaultServiceName)
    }

    private func makeServiceName() -> String {
        "KeychainServiceTests.\(UUID().uuidString)"
    }

    private func makeTokens(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        expiresAt: String = "2099-01-01T00:00:00Z"
    ) -> Tokens {
        Tokens(accessToken: accessToken, refreshToken: refreshToken, expiresAt: expiresAt)
    }

    private func clearAllItems(for serviceName: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName
        ]
        let status = SecItemDelete(query as CFDictionary)
        #expect(status == errSecSuccess || status == errSecItemNotFound)
    }

    private func insertRawKeychainItem(serviceName: String, accountKey: String, data: Data) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: accountKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        #expect(status == errSecSuccess)
    }
}

private final class MockKeychainAccess: KeychainAccessing, @unchecked Sendable {
    var addStatus: OSStatus = errSecSuccess
    var updateStatus: OSStatus = errSecSuccess
    var copyMatchingStatus: OSStatus = errSecItemNotFound
    var deleteStatus: OSStatus = errSecSuccess
    var copyMatchingResult: AnyObject?

    private(set) var addCalls = 0
    private(set) var updateCalls = 0
    private(set) var deleteCalls = 0

    func add(_ query: [String: Any]) -> OSStatus {
        addCalls += 1
        return addStatus
    }

    func update(searchQuery: [String: Any], attributes: [String: Any]) -> OSStatus {
        updateCalls += 1
        return updateStatus
    }

    func copyMatching(_ query: [String: Any], result: inout AnyObject?) -> OSStatus {
        result = copyMatchingResult
        return copyMatchingStatus
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        deleteCalls += 1
        return deleteStatus
    }
}
