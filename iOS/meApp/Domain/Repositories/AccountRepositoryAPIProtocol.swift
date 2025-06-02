import Foundation

/// Protocol for abstracting all remote (API) account data access and operations.
///
/// This protocol defines the contract for interacting with the backend account API endpoints (see /api/v3/account/*),
/// including account creation, authentication, profile/body updates, password management, notification settings,
/// dashboard configuration, streak/weightless updates, and scale token management.
///
/// Implementations of this protocol should handle all networking, serialization, and error handling for these operations.
@MainActor
protocol AccountRepositoryAPIProtocol {
    /// Creates a new account with the given email, password, and profile. (POST /account)
    /// - Parameters:
    ///   - email: The user's email address.
    ///   - password: The user's password.
    ///   - profile: The user's profile information.
    /// - Returns: AccountResponse (account + tokens)
    func createAccount(email: String, password: String, profile: Profile) async throws -> AccountResponse

    /// Logs in with the given email and password. (POST /account/login)
    /// - Parameters:
    ///   - email: The user's email address.
    ///   - password: The user's password.
    /// - Returns: AccountResponse (account + tokens)
    func logIn(email: String, password: String) async throws -> AccountResponse

    /// Logs out the account with the given ID and optional FCM token.
    /// - Parameters:
    ///   - fcmToken: The FCM token to unregister (optional).
    ///   - Parameter accessToken: The access token for authentication.
    func logOut(fcmToken: String?, accessToken: String?) async throws

    /// Fetches the current account's details from the backend. (GET /account)
    /// - Parameter accessToken: The access token for authentication, defaults to nil.
    /// - Returns: AccountDTO (from { account })
    func fetchAccount(accessToken: String?) async throws -> AccountDTO

    /// Edits the account with the given updated Account object (PUT /account).
    /// - Parameter updatedAccount: The updated Account object.
    /// - Returns: AccountResponse (from { account })
    func editAccount(_ updatedAccount: Account) async throws -> AccountResponse

    /// Partially updates the profile fields (PATCH /account/profile).
    /// - Parameter profile: The updated Profile object.
    /// - Returns: AccountResponse (from { account })
    func patchProfile(_ profile: Profile) async throws -> AccountResponse

    /// Partially updates the body composition fields (PATCH /account/bodycomp).
    /// - Parameter bodyComp: The updated BodyComp object.
    /// - Returns: AccountResponse (from { account })
    func patchBodyComp(_ bodyComp: BodyComp) async throws -> AccountResponse

    /// Partially updates notification settings (PATCH /account/notification).
    /// - Parameter notifications: The updated Notifications object.
    /// - Returns: AccountResponse (from { account })
    func patchNotification(_ notifications: Notifications) async throws -> AccountResponse

    /// Partially updates dashboard type (PATCH /account/dashboard-type).
    /// - Parameter type: The new dashboard type.
    /// - Returns: AccountResponse (from { account })
    func patchDashboardType(_ type: DashboardType) async throws -> AccountResponse

    /// Partially updates dashboard metrics (PATCH /account/dashboard-metrics).
    /// - Parameter metrics: The new dashboard metrics as an array of strings.
    /// - Returns: AccountResponse (from { account })
    func patchDashboardMetrics(_ metrics: [String]) async throws -> AccountResponse

    /// Partially updates streak (PATCH /account/streak).
    /// - Parameter isStreakOn: A boolean indicating if the streak is on or off.
    /// - Parameter streakTimestamp: The timestamp of the streak in ISO 8601 format.
    /// - Returns: AccountResponse (from { account })
    func patchStreak(_ isStreakOn: Bool, _ streakTimestamp: String) async throws -> AccountResponse

    /// Partially updates weightless (PATCH /account/weightless).
    /// - Parameters:
    ///   - isWeightlessOn: A boolean indicating if weightless mode is on or off.
    ///   - weightlessTimestamp: The timestamp of the weightless setting in ISO 8601 format.
    ///   - weightlessWeight: The weight value for weightless mode.
    /// - Returns: AccountResponse (from { account })
    func patchWeightless(_ isWeightlessOn: Bool, _ weightlessTimestamp: String, _ weightlessWeight: Int) async throws -> AccountResponse

    /// Deletes the account with the given ID (DELETE /account).
    /// - Parameter accountId: The ID of the account to delete.
    func deleteAccount(accountId: String) async throws

    /// Requests a password reset for the given email (POST /account/password-reset/request).
    /// - Parameter email: The email address to send the reset link to.
    func requestPasswordReset(email: String) async throws

    /// Updates the password for the account (PUT /account/password).
    /// - Parameters:
    ///   - oldPassword: The current password.
    ///   - newPassword: The new password to set.
    ///   - Returns: Tokens (access and refresh tokens)
    func updatePassword(oldPassword: String, newPassword: String) async throws -> Tokens
}

