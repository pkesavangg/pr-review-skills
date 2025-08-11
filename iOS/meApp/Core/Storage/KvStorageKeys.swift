import Foundation

/// Centralized keys for KvStorage (UserDefaults) - excludes migration keys
public enum KvStorageKeys: String {
    // MARK: - Integration Keys
    /// Base key for integration data storage
    case integrationInfo = "integration_info"
    /// Key for storing list of integration keys
    case integrationKeys = "integration_keys"
    
    // MARK: - Appearance/Theme Keys
    /// Global appearance mode key (when no account is active)
    case appearanceMode = "appearanceMode"
    
    // MARK: - HealthKit Modal Keys
    /// Base key for "has seen add Apple Health integration modal" flag
    case addAppleHealthModalBase = "hasSeenAddAppleHealthIntegrationModal"
    /// Base key for "has seen finish adding Apple Health modal" flag
    case finishAppleHealthModalBase = "hasSeenFinishAddingAppleHealthModal"
    /// Base key for "has seen out of sync Apple Health modal" flag
    case outOfSyncAppleHealthModalBase = "hasSeenOutOfSyncAppleHealthModal"
    
    // MARK: - Settings Modal Keys
    /// Key for "has seen add multiple accounts modal" flag
    case addMultipleAccountsModal = "hasSeenAddMultipleAccountsModal"
    
    // MARK: - Account-Scoped Keys
    /// Goal met flag key suffix (per account)
    case goalMetFlag = "goalMetFlag"
    /// Notification only alert shown flag suffix (per account)
    case notificationOnlyAlertShown = "notificationOnlyAlertShown"
    
    // MARK: - Helper Methods
    
    /// Creates an account-scoped key for appearance mode
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for appearance mode storage
    public static func appearanceModeKey(for accountId: String) -> String {
        return "appearanceMode_\(accountId)"
    }
    
    /// Creates an account-scoped key for integration data
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for integration data storage
    public static func integrationInfoKey(for accountId: String) -> String {
        return Self.integrationInfo.rawValue + "_" + accountId
    }
    
    /// Creates an account-scoped key for goal met flag
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for goal met flag storage
    public static func goalMetFlagKey(for accountId: String) -> String {
        return "\(accountId)_\(Self.goalMetFlag.rawValue)"
    }
    
    /// Creates an account-scoped key for notification alert shown flag
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for notification alert shown flag storage
    public static func notificationOnlyAlertShownKey(for accountId: String) -> String {
        return "\(Self.notificationOnlyAlertShown.rawValue)_\(accountId)"
    }
    
    /// Creates an account-scoped key for HealthKit modal flags
    /// - Parameters:
    ///   - baseKey: The base key (e.g., addAppleHealthModalBase)
    ///   - accountId: The account identifier (optional)
    /// - Returns: The full scoped key
    public static func scopedHealthKitModalKey(_ baseKey: KvStorageKeys, accountId: String?) -> String {
        guard let id = accountId, !id.isEmpty else { return baseKey.rawValue }
        return "\(baseKey.rawValue)_\(id)"
    }
}

