import Foundation

/// Application-wide constants
struct AppConstants {
    struct Account {
        /// Maximum number of accounts that can be stored locally
        static let maxAccounts = 10
        static let tokenExpirationBuffer: TimeInterval = 60 * 5 // 5 minutes
        static let tokenRefreshMaxRetries = 3
    }
}