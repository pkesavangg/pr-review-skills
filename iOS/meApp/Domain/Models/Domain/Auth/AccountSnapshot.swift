import Foundation

/// A flat, immutable value-type copy of Account and all its child settings models.
/// Published by AccountService instead of the SwiftData @Model directly.
/// Safe to use across async boundaries and as Combine publisher payloads.
struct AccountSnapshot: Equatable, Sendable {

    // MARK: - Account core fields
    let accountId: String
    let email: String
    let firstName: String?
    let lastName: String?
    let gender: Sex?
    let height: String?
    let dob: String?
    let zipcode: String?
    let isLoggedIn: Bool
    let isExpired: Bool
    let isActiveAccount: Bool
    let fcmToken: String?
    let lastActiveTime: String?
    let isSynced: Bool
    let productTypes: [String]
    /// Preferred measurement units ("metric", "imperialLbOz", "imperialLbDecimal").
    /// Defaulted so existing call sites that predate the multi-product API stay source-compatible.
    var measurementUnits: String?

    // MARK: - Flattened from WeightCompSettings
    let weightUnit: WeightUnit
    let weightHeight: String
    let activityLevel: ActivityLevel?

    // MARK: - Flattened from GoalSettings
    let goalType: GoalType?
    let goalWeight: Double?
    let initialWeight: Double?
    let goalPercent: Double?
    let goalIsSynced: Bool

    // MARK: - Flattened from StreaksSettings
    let isStreakOn: Bool
    let streakTimestamp: String?

    // MARK: - Flattened from WeightlessSettings
    let isWeightlessOn: Bool
    let weightlessWeight: Double?
    let weightlessTimestamp: String?

    // MARK: - Flattened from NotificationSettings
    let shouldSendEntryNotifications: Bool
    let shouldSendWeightInEntryNotifications: Bool

    // MARK: - Flattened from DashboardSettings
    let dashboardType: String?
    let dashboardMetrics: String?
    let progressMetrics: String?

    // MARK: - Flattened from IntegrationSettings
    let isHealthKitOn: Bool
    let isFitbitOn: Bool
    let isFitbitValid: Bool
    let isHealthConnectOn: Bool
    let isMfpOn: Bool
    let isMfpValid: Bool

    // MARK: - Tokens (in-memory only — sourced from Keychain, never persisted)
    let accessToken: String?
    let refreshToken: String?
    let expiresAt: String?
}
