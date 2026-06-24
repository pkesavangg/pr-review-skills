import Foundation

/// Public keys used for migrating data from the legacy Ionic/Capacitor app
public enum MigrationKey: String {
    /// Active account JSON blob stored by the Ionic app
    case activeAccount = "CapacitorStorage.activeAccountKey"
    /// Prefix for per-account offline data keys in the Ionic app
    case offlineAccountPrefix = "CapacitorStorage.offlineAccount"
    /// The paired scales key suffix used by the Ionic app, namespaced per account
    case pairedScalesKey
    /// Goal alert flag key suffix used by the Ionic app, namespaced per account
    case goalAlertKey = "hasSeenSetNewGoal"
    case setAGoalCardStatus = "goalCardStatus"
    /// Appearance/color mode key suffix used by the Ionic app, namespaced per account
    case appearanceKey = "colorMode"
    /// HealthKit integration status key used by the Ionic app (per account)
    case healthKitIntegrated
    /// HealthKit assigned account key used by the Ionic app (global)
    case healthKitAssignedTo = "CapacitorStorage.healthKitIntegratedAssignedTo"
    /// HealthKit deintegration flag used by the Ionic app (per account)
    case healthKitDeintegrated
    /// Notification alert viewed flag used by the Ionic app (per account)
    case notificationAlertViewed
    /// Notification only alert shown flag used by the Ionic app (global key, not per account)
    case notificationOnlyAlertShown = "CapacitorStorage.notificationOnlyAlertShown"
    
    case feedSettingsInfo = "feedInfo"
    case feedLastTriggered = "feedLastTriggeredAt"
    /// Common Capacitor storage prefix used by the Ionic app
    case capacitorPrefix = "CapacitorStorage."

    /// Builds the full paired scales storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for paired scales
    public static func scaleKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + "\(accountId)-" + Self.pairedScalesKey.rawValue
    }
    
    /// Builds the full goal alert storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for goal alert flag
    public static func goalMetAlertKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + "\(accountId)-" + Self.goalAlertKey.rawValue
    }
    
    /// Builds the full goal alert storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for goal alert flag
    public static func setAGoalCardViewedKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + "\(accountId)_" + Self.setAGoalCardStatus.rawValue
    }
    
    /// Builds the full appearance storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for appearance mode
    public static func appearanceKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + "\(accountId)-" + Self.appearanceKey.rawValue
    }
    
    /// Builds the full HealthKit integration storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for HealthKit integration status
    public static func healthKitIntegratedKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + "\(accountId)-" + Self.healthKitIntegrated.rawValue
    }
    
    /// Builds the full HealthKit deintegration storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for HealthKit deintegration flag
    public static func healthKitDeintegratedKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + Self.healthKitDeintegrated.rawValue + "-\(accountId)"
    }
    
    /// Builds the full notification alert viewed storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full storage key for notification alert viewed flag
    public static func notificationAlertViewedKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + Self.notificationAlertViewed.rawValue + "_\(accountId)"
    }
    
    public static func feedSettingsInfoKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + Self.feedSettingsInfo.rawValue + "_\(accountId)"
    }
    
    public static func feedLastTriggeredAtKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + Self.feedLastTriggered.rawValue + "_\(accountId)"
    }
}
