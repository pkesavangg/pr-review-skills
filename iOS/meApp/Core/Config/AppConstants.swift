import Foundation

/// Application-wide constants
struct AppConstants {
    struct TimeoutsAndRetention {
        static let logRetentionDays = 5 // Number of days to retain logs
        static let bluetoothTimeoutNs = 5 * 60 * 1_000_000_000 // Timeout for Bluetooth operations in seconds 5 minutes
        static let discoveredScaleModalTimeout = 30 * 1_000_000_000 // Timeout for discovered scale modal in nanoseconds (30 seconds)
        static let discoveredAlertTimeout = 15 * 1_000_000_000 // Timeout for discovered alert in nanoseconds (15 seconds)
    }
    
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
        static let greaterGoodsWebsite = URL(string: "https://greatergoods.com")!
        static let notFound = URL(string: "https://greatergoods.com/not-found")!
        static let serviceBase = URL(string: "https://greatergoods.com/service/")!
    }
    /// Constants for help/contact info
    struct Help {
        static let phoneNumber = "1-866-991-8494"
        static let email = "info@greatergoods.com"
    }
    /// Constants related to product support URLs
    struct Product {
        /// Base URL for product manuals (e.g. https://www.greatergoods.com/0412)
        static let baseURL = "https://www.greatergoods.com"
    }
    /// OAuth URLs for third-party integrations
    struct OAuthURLs {
        static func fitbit(accountId: String) -> String {
            return "https://www.fitbit.com/oauth2/authorize?response_type=code&client_id=22B2QV&redirect_uri=https%3A%2F%2Fapi.weightgurus.com%2Fv2%2Fauth%2Ffitbit&scope=profile%20weight&state=v3-\(accountId)"
        }
        static func mfPal(accountId: String) -> String {
            return "https://api.myfitnesspal.com/v2/oauth2/auth?client_id=weightguru&scope=measurements&response_type=code&redirect_uri=https%3a%2f%2fapi.weightgurus.com%2fv2%2fauth%2fmyfitnesspal?&state=v3-\(accountId)"
        }
    }
}
