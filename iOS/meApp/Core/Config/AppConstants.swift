import Foundation

/// Application-wide constants
struct AppConstants {
    static let logRetentionDays = 5 // Number of days to retain logs
    struct Account {
        /// Maximum number of accounts that can be stored locally
        static let maxAccounts = 10
        static let tokenExpirationBuffer: TimeInterval = 60 * 5 // 5 minutes
        static let tokenRefreshMaxRetries = 3
    }
    /// Constants for legal URLs
    struct LegalURLs {
        static let privacyPolicy = URL(string: "https://greatergoods.com/legal/privacy-policy")!
        static let termsOfService = URL(string: "https://greatergoods.com/legal/weight-gurus-tos")!
    }
}