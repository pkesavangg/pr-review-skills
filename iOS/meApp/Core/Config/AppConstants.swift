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
}
