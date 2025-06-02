import Foundation

/// Protocol defining the service interface for managing user accounts, including authentication, state, updates, security, and sync/offline operations.
@MainActor
protocol AccountServiceProtocol {
    var activeAccount : Account? { get set }
    // MARK: - Account Lifecycle

    /// Registers a new user account with the given email, password, and profile.
    /// - Parameters:
    ///   - email: The user's email address.
    ///   - password: The user's password.
    ///   - profile: The user's profile information.
    /// - Returns: The newly created Account object.
    func signUp(email: String, password: String, profile: Profile) async throws -> Account

    /// Logs in a user with the given email and password.
    /// - Parameters:
    ///   - email: The user's email address.
    ///   - password: The user's password.
    /// - Returns: The authenticated Account object.
    func logIn(email: String, password: String) async throws -> Account

    /// Logs out the account with the specified ID.
    /// - Parameter accountId: The ID of the account to log out. If nil, logs out the currently active account.
    func logOut(accountId: String?) async throws

    /// Deletes the currently active account.
    func deleteAccount() async throws

    /// Switches the active session to the specified account.
    /// - Parameter account: The account to switch to.
    func switchAccount(to account: Account) async throws

    /// Sets the specified account as the active account.
    /// - Parameter account: The account to set as active.
    func setActiveAccount(_ account: Account) async throws

    // MARK: - Account State

    /// Retrieves the currently active account, if any.
    /// - Returns: The active Account object, or nil if none is active.
    func getActiveAccount() async throws -> Account?

    /// Retrieves all accounts that are currently logged in on the device.
    /// - Returns: An array of logged-in Account objects.
    func getAllLoggedInAccounts() async throws -> [Account]

    /// Fetches an account by its unique ID.
    /// - Parameter id: The ID of the account to fetch.
    /// - Returns: The Account object, or nil if not found.
    func fetchAccount(byId id: String) async throws -> Account?

    /// Fetches all accounts stored on the device.
    /// - Returns: An array of all Account objects.
    func fetchAllAccounts() async throws -> [Account]

    // MARK: - Account Updates

    /// Updates the entire account object in the data store and/or backend.
    /// - Parameter updatedAccount: The updated Account object.
    func updateAccount(_ updatedAccount: Account) async throws

    /// Updates the user's profile information.
    /// - Parameter profile: The updated Profile object.
    /// - Returns: The updated Account object.
    func updateProfile(_ profile: Profile) async throws -> Account

    /// Updates the user's body composition information.
    /// - Parameter bodyComp: The updated BodyComp object.
    /// - Returns: The updated Account object.
    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account

    /// Updates the user's authentication tokens.
    /// - Parameter tokens: The updated Tokens object.
    /// - Parameter accountId: The ID of the account to update. If nil, updates the currently active account.
    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws

    /// Updates the dashboard type for the specified account.
    /// - Parameters:
    ///   - accountId: The ID of the account to update.
    ///   - type: The new dashboard type.
    func updateDashboardType(accountId: String, type: DashboardType) async throws

    /// Updates the integrations for the specified account.
    /// - Parameters:
    ///   - accountId: The ID of the account to update.
    ///   - integrations: The new Integrations object.
    func updateIntegrations(accountId: String, integrations: Integrations) async throws

    /// Updates the notification settings for the specified account.
    /// - Parameters:
    ///   - accountId: The ID of the account to update.
    ///   - notifications: The new Notifications object.
    func updateNotifications(accountId: String, notifications: Notifications) async throws

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

    /// Refreshes the account data from the backend for the specified account ID.
    /// - Parameter accountId: The ID of the account to refresh. If nil, refreshes the currently active account.
    /// - Returns: The refreshed Account object.
    func refreshAccount(accountId: String?) async throws -> Account

    /// Clears all offline data for the specified account.
    /// - Parameter account: The account whose offline data should be cleared.
    func clearOfflineData(for account: Account) async throws

    /// Deletes all accounts stored locally on the device.
    func deleteAllAccountsLocally() async throws
}
