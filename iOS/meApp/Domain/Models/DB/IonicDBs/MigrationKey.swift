import Foundation

/// Public keys used for migrating data from the legacy Ionic/Capacitor app
public enum MigrationKey: String {
    /// Active account JSON blob stored by the Ionic app
    case activeAccount = "CapacitorStorage.activeAccountKey"
    /// Prefix for per-account offline data keys in the Ionic app
    case offlineAccountPrefix = "CapacitorStorage.offlineAccount"
    /// The paired scales key suffix used by the Ionic app, namespaced per account
    case pairedScalesKey = "pairedScalesKey"
    /// Common Capacitor storage prefix used by the Ionic app
    case capacitorPrefix = "CapacitorStorage."

    /// Builds the full paired scales storage key for the given account id
    /// - Parameter accountId: The account identifier
    /// - Returns: The full Capacitor storage key for paired scales
    public static func scaleKey(for accountId: String) -> String {
        return Self.capacitorPrefix.rawValue + "\(accountId)-" + Self.pairedScalesKey.rawValue
    }
}

