import Foundation

/// Protocol defining secure storage for account tokens and credentials.
/// Implementations use the system keychain so tokens are encrypted and cleared on uninstall.
/// Use this protocol for testability (e.g. inject a mock in unit tests).
public protocol KeychainServiceProtocol: Sendable {
    /// Stores tokens for the given account.
    /// - Parameters:
    ///   - tokens: The access, refresh, and expiration to store.
    ///   - accountId: Account-scoped key (supports multiple accounts / account switching).
    func setTokens(_ tokens: Tokens, for accountId: String)

    /// Retrieves tokens for the given account.
    /// - Parameter accountId: The account identifier.
    /// - Returns: Stored tokens, or nil if none or on error.
    func getTokens(for accountId: String) -> Tokens?

    /// Removes stored tokens for the given account (e.g. on logout or account deletion).
    /// - Parameter accountId: The account identifier.
    func deleteTokens(for accountId: String)

    /// Stores the FCM (Firebase Cloud Messaging) token for the given account.
    func setFCMToken(_ token: String, for accountId: String)

    /// Retrieves the stored FCM token for the given account.
    func getFCMToken(for accountId: String) -> String?

    /// Removes the stored FCM token for the given account (e.g. on logout or account deletion).
    func deleteFCMToken(for accountId: String)
}
