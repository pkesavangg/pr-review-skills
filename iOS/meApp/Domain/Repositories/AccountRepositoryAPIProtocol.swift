import Foundation

/// Protocol for abstracting all remote (API) account data access and operations.
///
/// This protocol defines the contract for interacting with the backend account API endpoints (see /api/v3/account/*),
/// including account creation, authentication, profile/body updates, password management, notification settings,
/// dashboard configuration, streak/weightless updates, and scale token management.
///
/// Implementations of this protocol should handle all networking, serialization, and error handling for these operations.
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
    ///   - accountId: The ID of the account to log out.
    ///   - fcmToken: The FCM token to unregister (optional).
    func logOut(accountId: String, fcmToken: String?) async throws

    /// Fetches the current account's details from the backend. (GET /account)
    /// - Parameter accountId: The ID of the account to fetch.
    /// - Returns: AccountDTO
    func fetchAccount(accountId: String) async throws -> AccountDTO

    /// Edits the account with the given updated Account object (PUT /account).
    /// - Parameter updatedAccount: The updated Account object.
    /// - Returns: AccountDTO (from { account })
    func editAccount(_ updatedAccount: Account) async throws -> AccountDTO

    /// Partially updates the profile fields (PATCH /account/profile).
    /// - Parameter profile: The updated Profile object.
    /// - Returns: AccountDTO (from { account })
    func patchProfile(_ profile: Profile) async throws -> AccountDTO

    /// Partially updates the body composition fields (PATCH /account/bodycomp).
    /// - Parameter bodyComp: The updated BodyComp object.
    /// - Returns: AccountDTO (from { account })
    func patchBodyComp(_ bodyComp: BodyComp) async throws -> AccountDTO

    /// Partially updates notification settings (PATCH /account/notification).
    /// - Parameter notifications: The updated Notifications object.
    /// - Returns: AccountDTO (from { account })
    func patchNotification(_ notifications: Notifications) async throws -> AccountDTO

    /// Partially updates dashboard type (PATCH /account/dashboard-type).
    /// - Parameter type: The new dashboard type.
    /// - Returns: AccountDTO (from { account })
    func patchDashboardType(_ type: DashboardType) async throws -> AccountDTO

    /// Partially updates dashboard metrics (PATCH /account/dashboard-metrics).
    /// - Parameter metrics: The new dashboard metrics as an array of strings.
    /// - Returns: AccountDTO (from { account })
    func patchDashboardMetrics(_ metrics: [String]) async throws -> AccountDTO

    /// Partially updates streak (PATCH /account/streak).
    /// - Parameter streak: The new streak value.
    /// - Returns: AccountDTO (from { account })
    func patchStreak(_ streak: Int) async throws -> AccountDTO

    /// Partially updates weightless (PATCH /account/weightless).
    /// - Parameter weightless: The new weightless value.
    /// - Returns: AccountDTO (from { account })
    func patchWeightless(_ weightless: Bool) async throws -> AccountDTO

    /// Deletes the account with the given ID (DELETE /account).
    /// - Parameter accountId: The ID of the account to delete.
    func deleteAccount(accountId: String) async throws

    /// Requests a password reset for the given email (POST /account/password-reset/request).
    /// - Parameter email: The email address to send the reset link to.
    func requestPasswordReset(email: String) async throws

    /// Checks a password reset token (POST /account/password-reset/check).
    /// - Parameter token: The password reset token to check.
    func checkPasswordResetToken(token: String) async throws

    /// Confirms a password reset (POST /account/password-reset/confirm).
    /// - Parameters:
    ///   - token: The password reset token to confirm.
    ///   - newPassword: The new password to set.
    func confirmPasswordReset(token: String, newPassword: String) async throws

    /// Updates the password for the account (PUT /account/password).
    /// - Parameters:
    ///   - oldPassword: The current password.
    ///   - newPassword: The new password to set.
    func updatePassword(oldPassword: String, newPassword: String) async throws

    /// Gets a scale token for the account (GET /account/scale).
    /// - Parameters:
    ///   - accountId: The ID of the account.
    ///   - revision: The revision/version of the scale token (optional).
    /// - Returns: The scale token as a string.
    func getScaleToken(accountId: String, revision: String?) async throws -> String
}

