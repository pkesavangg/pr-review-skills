import Foundation
import Security

/// Keychain-backed secure storage for account tokens.
/// Uses kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly so tokens are available after first unlock and not synced/restored to other devices.
/// Tokens are stored per accountId to support multiple accounts and account switching.
final class KeychainService: KeychainServiceProtocol, @unchecked Sendable {
    static let shared = KeychainService()

    @Injector private var logger: LoggerService

    private let serviceName: String
    private let tag = "KeychainService"

    init(serviceName: String? = nil) {
        self.serviceName = serviceName ?? (Bundle.main.bundleIdentifier ?? "meApp") + ".tokens"
    }

    // MARK: - KeychainServiceProtocol

    func setTokens(_ tokens: Tokens, for accountId: String) {
        guard let data = try? JSONEncoder().encode(tokens) else { return }
        let key = keyForAccount(accountId)
        deleteItem(accountKey: key)
        setData(data, accountKey: key)
    }

    func getTokens(for accountId: String) -> Tokens? {
        guard let data = getData(accountKey: keyForAccount(accountId)) else { return nil }
        return try? JSONDecoder().decode(Tokens.self, from: data)
    }

    func deleteTokens(for accountId: String) {
        deleteItem(accountKey: keyForAccount(accountId))
    }

    func setFCMToken(_ token: String, for accountId: String) {
        guard let data = token.data(using: .utf8) else { return }
        let key = fcmKeyForAccount(accountId)
        deleteItem(accountKey: key)
        setData(data, accountKey: key)
    }

    func getFCMToken(for accountId: String) -> String? {
        guard let data = getData(accountKey: fcmKeyForAccount(accountId)) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func deleteFCMToken(for accountId: String) {
        deleteItem(accountKey: fcmKeyForAccount(accountId))
    }

    // MARK: - Private (shared Keychain operations)

    private func setData(_ data: Data, accountKey: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: accountKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        var status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let searchQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: accountKey
            ]
            let updateQuery: [String: Any] = [kSecValueData as String: data]
            status = SecItemUpdate(searchQuery as CFDictionary, updateQuery as CFDictionary)
        }
        if status != errSecSuccess {
            Task { @MainActor in
                self.logger.log(level: .error, tag: self.tag, message: "Keychain setData failed: accountKey=\(accountKey), status=\(status)")
            }
        }
    }

    private func getData(accountKey: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: accountKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return data
    }

    private func deleteItem(accountKey: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: accountKey
        ]
        let status = SecItemDelete(query as CFDictionary)
        if status != errSecSuccess && status != errSecItemNotFound {
            Task { @MainActor in
                self.logger.log(level: .error, tag: self.tag, message: "Keychain deleteItem failed: accountKey=\(accountKey), status=\(status)")
            }
        }
    }

    private func keyForAccount(_ accountId: String) -> String {
        "tokens_\(accountId)"
    }

    private func fcmKeyForAccount(_ accountId: String) -> String {
        "fcm_\(accountId)"
    }
}
