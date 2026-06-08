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
    case appearanceMode

    // MARK: - Dashboard Keys
    /// User-selected default tab for the dashboard graph (week/month/year/total)
    case defaultGraphPeriod = "defaultGraphPeriod"

    // MARK: - HealthKit Modal Keys
    /// Base key for "has seen add Apple Health integration modal" flag
    case addAppleHealthModalBase = "hasSeenAddAppleHealthIntegrationModal"
    /// Base key for "has seen finish adding Apple Health modal" flag
    case finishAppleHealthModalBase = "hasSeenFinishAddingAppleHealthModal"
    /// Base key for "has seen out of sync Apple Health modal" flag
    case outOfSyncAppleHealthModalBase = "hasSeenOutOfSyncAppleHealthModal"
    /// Base key for tracking when we're waiting for permissions to be restored after out-of-sync
    case waitingForHKPermissionsRestoredBase = "waitingForHKPermissionsRestored"
    /// Base key for tracking if the first-time Apple Health connect screen has been shown
    case hasShownFirstTimeConnectScreenBase = "hasShownFirstTimeAppleHealthConnectScreen"

    // MARK: - Settings Modal Keys
    /// Key for "has seen add multiple accounts modal" flag
    case addMultipleAccountsModal = "hasSeenAddMultipleAccountsModal"

    // MARK: - Migration Keys
    /// Key for tracking if Ionic to native app migration has been completed
    case ionicToNativeAppMigrationCompleted = "ionicToNativeAppMigrationCompleted"
    /// Key for tracking if account tokens have been migrated from SwiftData to Keychain
    case tokensMigratedToKeychain = "tokensMigratedToKeychain"
    /// Key for tracking if baby entry weight/length have been migrated to decigrams/mm
    case babyEntryDecigramsMigrated = "babyEntryDecigramsMigrated"

    // MARK: - Account-Scoped Keys
    /// Goal met flag key suffix (per account)
    case goalMetFlag
    /// Notification only alert shown flag suffix (per account)
    case notificationOnlyAlertShown
    /// Notification only permission alert shown flag suffix (per account)
    case notificationOnlyPermAlertShown
    /// Feed info settings key suffix (per account)
    case feedInfo
    /// Feed last triggered at timestamp key suffix (per account)
    case feedLastTriggeredAt
    case setAGoalCardViewed
    /// "Scrollable graph" first-time hint modal viewed flag suffix (per account)
    case graphScrollHintViewed = "graphScrollHintViewed"

    // MARK: - AppSync Camera Keys
    /// MA-3863: single key storing a dictionary of accountId → last-used camera zoom level
    case appSyncCameraZoomMap = "appSyncCameraZoomMap"

    // MARK: - Product Type Selection
    /// Selected product type key suffix (per account)
    case selectedProductType
    /// Selected signup device type key suffix (per account)
    case selectedSignupDeviceType
    /// HealthKit permission-scope device types key suffix (per account)
    case healthKitPermissionScopeDeviceTypes
    /// Backup account id for the most recent signup on this device
    case recentSignupAccountId
    /// Backup signup device type for the most recent signup on this device
    case recentSignupDeviceType

    // MARK: - FCM Token Key
    /// FCM token storage key (device-scoped, not account-scoped)
    case fcmToken = "fcmToken"

    // MARK: - Helper Methods

    /// Creates an account-scoped key for appearance mode
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for appearance mode storage
    public static func appearanceModeKey(for accountId: String) -> String {
        return "appearanceMode_\(accountId)"
    }

    /// Creates an account-scoped key for the default graph period preference
    public static func defaultGraphPeriodKey(for accountId: String) -> String {
        return "\(Self.defaultGraphPeriod.rawValue)_\(accountId)"
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

    /// Creates an account-scoped key for notification only permission alert shown flag
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for notification only permission alert shown flag storage
    public static func notificationOnlyPermAlertShownKey(for accountId: String) -> String {
        return "\(Self.notificationOnlyPermAlertShown.rawValue)_\(accountId)"
    }

    /// Creates an account-scoped key for feed info settings
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for feed info settings storage
    public static func feedInfoKey(for accountId: String) -> String {
        return "\(Self.feedInfo.rawValue)_\(accountId)"
    }

    /// Creates an account-scoped key for feed last triggered at timestamp
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for feed last triggered at timestamp storage
    public static func feedLastTriggeredAtKey(for accountId: String) -> String {
        return "\(Self.feedLastTriggeredAt.rawValue)_\(accountId)"
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

    /// Creates an account-scoped key for "set a goal" card viewed flag
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for goal met flag storage
    public static func setAGoalModalFlagKey(for accountId: String) -> String {
        return "\(accountId)_\(Self.setAGoalCardViewed.rawValue)"
    }

    /// Creates an account-scoped key for the graph scroll hint modal viewed flag
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for graph scroll hint viewed flag storage
    public static func graphScrollHintViewedKey(for accountId: String) -> String {
        return "\(accountId)_\(Self.graphScrollHintViewed.rawValue)"
    }

    /// Creates an account-scoped key for selected product type
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for selected product type storage
    public static func selectedProductTypeKey(for accountId: String) -> String {
        return "\(accountId)_\(Self.selectedProductType.rawValue)"
    }

    /// Creates an account-scoped key for the signup-selected device type.
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for signup-selected device type storage
    public static func selectedSignupDeviceTypeKey(for accountId: String) -> String {
        return "\(accountId)_\(Self.selectedSignupDeviceType.rawValue)"
    }

    /// Creates an account-scoped key for the device types currently driving HealthKit permissions.
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for HealthKit permission-scope device type storage
    public static func healthKitPermissionScopeDeviceTypesKey(for accountId: String) -> String {
        return "\(accountId)_\(Self.healthKitPermissionScopeDeviceTypes.rawValue)"
    }

    /// Creates an account-scoped key for FCM token storage
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for FCM token storage
    public static func fcmTokenKey(for accountId: String) -> String {
        return "\(Self.fcmToken.rawValue)_\(accountId)"
    }

    /// Creates an account-scoped key for baby entry decigrams migration
    /// - Parameter accountId: The account identifier
    /// - Returns: The full key for baby entry decigrams migration flag
    public static func babyEntryDecigramsMigratedKey(for accountId: String) -> String {
        return "\(Self.babyEntryDecigramsMigrated.rawValue)_\(accountId)"
    }
}
