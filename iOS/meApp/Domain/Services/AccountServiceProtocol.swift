import Combine
import Foundation

/// Protocol defining the service interface for managing user accounts, including authentication, state, updates, security, and sync/offline operations.
@MainActor
protocol AccountServiceProtocol {
    var activeAccount: AccountSnapshot? { get }
    var allAccounts: [AccountSnapshot] { get }
    var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { get }
    var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { get }
    var isSignupInProgress: Bool { get }
    var isSignupInProgressPublisher: Published<Bool>.Publisher { get }
    func markSignupInProgress(_ inProgress: Bool)
    // MARK: - Account Lifecycle

    /// Registers a new user account with the given email, password, and profile.
    /// - Parameters:
    ///   - email: The user's email address.
    ///   - password: The user's password.
    ///   - profile: The user's profile information.
    func signUp(email: String, password: String, profile: Profile) async throws

    /// Logs in a user with the given email and password.
    /// - Parameters:
    ///   - email: The user's email address.
    ///   - password: The user's password.
    func logIn(email: String, password: String) async throws

    /// Logs out the account with the specified ID.
    /// - Parameter accountId: The ID of the account to log out. If nil, logs out the currently active account.
    /// - Parameter isAutoLogout: Indicates if this is an automatic logout (e.g., due to inactivity).
    func logOut(accountId: String?, isAutoLogout: Bool) async throws

    /// Deletes the currently active account.
    func deleteAccount() async throws
    func deleteAllAccounts() async throws

    /// Removes an account from this device without deleting it server-side.
    /// For logged-in accounts, logs out via API first; for already-logged-out
    /// accounts, skips the API call. Always clears keychain tokens and the
    /// local SwiftData record.
    func removeAccountFromDevice(accountId: String) async throws

    /// Switches the active session to the account with the specified ID.
    /// - Parameter accountId: The ID of the account to switch to.
    func switchAccount(to accountId: String) async throws

    /// Sets the specified account as the active account.
    /// - Parameter accountId: The ID of the account to set as active.
    func setActiveAccount(accountId: String) async throws

    // MARK: - Account State

    /// Returns true if the app should defer showing the unauthenticated landing (e.g. during restore or initial load).
    func shouldDeferUnauthenticatedLanding() -> Bool

    /// Retrieves the currently active account snapshot, if any.
    /// - Returns: The active AccountSnapshot, or nil if none is active.
    func getActiveAccount() async throws -> AccountSnapshot?

    /// Retrieves all accounts that are currently logged in on the device.
    /// - Returns: An array of logged-in AccountSnapshot objects.
    func getAllLoggedInAccounts() async throws -> [AccountSnapshot]

    /// Fetches an account snapshot by its unique ID.
    /// - Parameter id: The ID of the account to fetch.
    /// - Returns: The AccountSnapshot, or nil if not found.
    func fetchAccount(byId id: String) async throws -> AccountSnapshot?

    /// Fetches all account snapshots stored on the device.
    /// - Returns: An array of all AccountSnapshot objects.
    func fetchAllAccounts() async throws -> [AccountSnapshot]

    // MARK: - Account Updates

    func createGoal(_ goal: Goal) async throws

    /// Updates the user's profile information.
    /// - Parameter profile: The updated Profile object.
    /// - Parameter canSaveOffline: Whether the profile can be saved offline.
    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws

    /// Updates the user's body composition information.
    /// - Parameter bodyComp: The updated BodyComp object.
    func updateBodyComp(_ bodyComp: BodyComp) async throws

    /// Updates the product types for the active account via PATCH /account/products, then persists locally.
    /// Used when signup or device/baby flows establish the authoritative product list.
    /// Note: this grows the set (it unions the sent value, the server response, and the existing
    /// local value) and never reduces — use `removeProductType(_:)` to drop a type.
    func updateProductTypes(_ productTypes: [String]) async throws

    /// Removes a single product type from the active account via PATCH /account/products, then
    /// persists locally. Unlike `updateProductTypes(_:)`, this is a *reducing* path: it sends the
    /// remaining types and adopts the server's authoritative response, deliberately not unioning
    /// with the prior local value (which would re-add the removed type). Used when the last baby
    /// profile is deleted and "baby" must be stripped.
    /// - Parameter productType: The product type to remove (e.g. "baby").
    func removeProductType(_ productType: String) async throws

    /// Updates the active account's preferred measurement units (PATCH /account/measurement-units).
    /// - Parameter measurementUnits: The new measurement units.
    func updateMeasurementUnits(_ measurementUnits: MeasurementUnits) async throws

    /// Checks whether an email is available for registration (POST /account/email-check).
    /// - Parameter email: The email address to check.
    /// - Returns: `true` if the email is not already registered.
    func checkEmailAvailability(email: String) async throws -> Bool

    /// Updates the user's authentication tokens.
    /// - Parameter tokens: The updated Tokens object.
    /// - Parameter accountId: The ID of the account to update. If nil, updates the currently active account.
    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws

    /// Updates the dashboard type for the specified account.
    /// - Parameters:
    ///   - type: The new dashboard type.
    func updateDashboardType(type: DashboardType) async throws

    /// Updates the integration settings for the specified account.
    /// - Parameters:
    ///  - integrationType: The type of integration to update.
    ///  - preferences: A dictionary of preferences for the integration.
    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws

    /// Updates the notification settings for the specified account.
    /// - Parameters:
    ///   - notifications: The new Notifications object.
    func updateNotifications(notifications: Notifications) async throws

    /// Updates the dashboard metrics for the specified account.
    /// - Parameters:
    ///   - metrics: Array of metric strings to display on dashboard.
    func updateDashboardMetrics(metrics: [String]) async throws

    /// Updates the progress metrics for the active account.
    /// - Parameter metrics: The new progress metrics as an array of strings. Allowed values:
    ///   "goal", "currentStreak", "longestStreak", "weeklyChange", "monthlyChange", "yearlyChange", "totalChange"
    func updateProgressMetrics(metrics: [String]) async throws

    /// Updates the streak status for the specified account.
    /// - Parameters:
    ///   - isStreakOn: Whether streak tracking is enabled.
    ///   - streakTimestamp: The timestamp of the last streak update.
    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws

    /// Updates the weightless mode settings for the specified account.
    /// - Parameters:
    ///   - isWeightlessOn: Whether weightless mode is enabled.
    ///   - weightlessTimestamp: The timestamp of the last weightless update.
    ///   - weightlessWeight: The weight value for weightless mode.
    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws

    // MARK: - Password & Security

    /// Requests a password reset for the specified email address.
    /// - Parameter email: The email address to send the reset link to.
    func requestPasswordReset(email: String) async throws

    /// Updates the user's password.
    /// - Parameters:
    ///   - oldPassword: The current password.
    ///   - newPassword: The new password to set.
    func updatePassword(oldPassword: String, newPassword: String) async throws

    // MARK: - Sync & Offline

    /// Refreshes all accounts from the backend.
    /// - Note: This should be called on app launch to ensure all accounts are up-to-date.
    func refreshAllAccounts() async throws

    /// Refreshes the account data from the backend for the specified account ID.
    /// - Parameter accountId: The ID of the account to refresh. If nil, refreshes the currently active account.
    func refreshAccount(accountId: String?) async throws

    /// Deletes all accounts stored locally on the device.
    func logOutAllAccounts() async throws

    /// Syncs all unsynced accounts with the backend.
    /// - Note: This should be called on app launch to ensure all local changes are synchronized.
    func syncUnsyncedAccounts() async throws

    // MARK: - Authentication Tokens

    /// Retrieves the currently active authentication tokens.
    /// - Returns: The Tokens object containing access and refresh tokens.
    func getActiveTokens() async throws -> Tokens

    /// Refreshes the authentication tokens for the specified account ID.
    /// - Parameter accountId: The ID of the account to refresh tokens for. If nil, uses the currently active account.
    /// - Returns: The refreshed Tokens object.
    func refreshTokens(accountId: String?) async throws -> Tokens
    func deleteHealthIntegration(_ type: IntegrationType) async throws
    func updatePublishedState() async throws
}

extension AccountServiceProtocol {
    func logOut() async throws {
        try await logOut(accountId: nil, isAutoLogout: false)
    }

    func logOut(accountId: String?) async throws {
        try await logOut(accountId: accountId, isAutoLogout: false)
    }

    func refreshAccount() async throws {
        try await refreshAccount(accountId: nil)
    }

    func updateProfile(_ profile: Profile) async throws {
        try await updateProfile(profile, canSaveOffline: false)
    }

    func updateIntegrations(integrationType: IntegrationType) async throws {
        try await updateIntegrations(integrationType: integrationType, preferences: [:])
    }
}
