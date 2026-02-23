import Foundation
import Security

/// Keychain-backed secure storage for account tokens.
/// Uses kSecAttrAccessibleAfterFirstUnlock so tokens are available after first unlock and when locked (e.g. background refresh).
/// Tokens are stored per accountId to support multiple accounts and account switching.
final class KeychainService: KeychainServiceProtocol, @unchecked Sendable {
    static let shared = KeychainService()

    private let serviceName: String
    private let jsonEncoder = JSONEncoder()
    private let jsonDecoder = JSONDecoder()

    init(serviceName: String? = nil) {
        self.serviceName = serviceName ?? (Bundle.main.bundleIdentifier ?? "meApp") + ".tokens"
    }

    // MARK: - KeychainServiceProtocol

    func setTokens(_ tokens: Tokens, for accountId: String) {
        let key = keyForAccount(accountId)
        guard let data = try? jsonEncoder.encode(tokens) else { return }
        deleteTokens(for: accountId)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    func getTokens(for accountId: String) -> Tokens? {
        let key = keyForAccount(accountId)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess,
              let data = result as? Data,
              let tokens = try? jsonDecoder.decode(Tokens.self, from: data) else {
            return nil
        }
        return tokens
    }

    func deleteTokens(for accountId: String) {
        let key = keyForAccount(accountId)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Private

    private func keyForAccount(_ accountId: String) -> String {
        "tokens_\(accountId)"
    }
}
