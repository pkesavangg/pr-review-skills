import Foundation
@testable import meApp

final class MockKeychainService: KeychainServiceProtocol, @unchecked Sendable {
    private var tokensByAccountId: [String: Tokens] = [:]
    private var fcmByAccountId: [String: String] = [:]

    private(set) var setTokensCalls = 0
    private(set) var deleteTokensCalls = 0
    private(set) var setFCMTokenCalls = 0
    private(set) var deleteFCMTokenCalls = 0

    func setTokens(_ tokens: Tokens, for accountId: String) {
        setTokensCalls += 1
        tokensByAccountId[accountId] = tokens
    }

    func getTokens(for accountId: String) -> Tokens? {
        tokensByAccountId[accountId]
    }

    func deleteTokens(for accountId: String) {
        deleteTokensCalls += 1
        tokensByAccountId.removeValue(forKey: accountId)
    }

    func setFCMToken(_ token: String, for accountId: String) {
        setFCMTokenCalls += 1
        fcmByAccountId[accountId] = token
    }

    func getFCMToken(for accountId: String) -> String? {
        fcmByAccountId[accountId]
    }

    func deleteFCMToken(for accountId: String) {
        deleteFCMTokenCalls += 1
        fcmByAccountId.removeValue(forKey: accountId)
    }
}
