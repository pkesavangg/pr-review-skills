//
//  MockAccountAPIRepository.swift
//  meAppTests
//

import Foundation
@testable import meApp

/// Mock for AccountRepositoryAPIProtocol. Configure stubs before each test.
@MainActor
final class MockAccountAPIRepository: AccountRepositoryAPIProtocol {

    // MARK: - createAccount
    var createAccountResult: AccountResponse?
    var createAccountError: Error?
    var createAccountCallCount = 0
    var lastCreateAccountEmail: String?
    var lastCreateAccountPassword: String?
    var lastCreateAccountProfile: Profile?

    func createAccount(email: String, password: String, profile: Profile) async throws -> AccountResponse {
        createAccountCallCount += 1
        lastCreateAccountEmail = email
        lastCreateAccountPassword = password
        lastCreateAccountProfile = profile
        if let error = createAccountError { throw error }
        return createAccountResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - logIn
    var logInResult: AccountResponse?
    var logInError: Error?

    func logIn(email: String, password: String) async throws -> AccountResponse {
        if let error = logInError { throw error }
        return logInResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - logOut
    var logOutError: Error?

    func logOut(fcmToken: String?, accountId: String?) async throws {
        if let error = logOutError { throw error }
    }

    // MARK: - fetchAccount
    var fetchAccountResult: AccountDTO?
    var fetchAccountError: Error?

    func fetchAccount(accountId: String?) async throws -> AccountDTO {
        if let error = fetchAccountError { throw error }
        return fetchAccountResult ?? AccountTestFixtures.makeAccountDTO()
    }

    // MARK: - editAccount
    var editAccountResult: AccountResponse?
    var editAccountError: Error?

    func editAccount(_ updatedAccount: Account) async throws -> AccountResponse {
        if let error = editAccountError { throw error }
        return editAccountResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - createGoal
    var createGoalResult: GoalResponse?
    var createGoalError: Error?
    var createGoalCallCount = 0

    func createGoal(_ goal: Goal) async throws -> GoalResponse {
        createGoalCallCount += 1
        if let error = createGoalError { throw error }
        return createGoalResult ?? AccountTestFixtures.makeGoalResponse()
    }

    // MARK: - patchProfile
    var patchProfileResult: AccountResponse?
    var patchProfileError: Error?

    func patchProfile(_ profile: Profile) async throws -> AccountResponse {
        if let error = patchProfileError { throw error }
        return patchProfileResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchBodyComp
    var patchBodyCompResult: AccountResponse?
    var patchBodyCompError: Error?

    func patchBodyComp(_ bodyComp: BodyComp) async throws -> AccountResponse {
        if let error = patchBodyCompError { throw error }
        return patchBodyCompResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchNotification
    var patchNotificationResult: AccountResponse?
    var patchNotificationError: Error?

    func patchNotification(_ notifications: Notifications) async throws -> AccountResponse {
        if let error = patchNotificationError { throw error }
        return patchNotificationResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchDashboardType
    var patchDashboardTypeResult: AccountResponse?
    var patchDashboardTypeError: Error?

    func patchDashboardType(_ type: DashboardType) async throws -> AccountResponse {
        if let error = patchDashboardTypeError { throw error }
        return patchDashboardTypeResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchDashboardMetrics
    var patchDashboardMetricsResult: AccountResponse?
    var patchDashboardMetricsError: Error?

    func patchDashboardMetrics(_ metrics: [String]) async throws -> AccountResponse {
        if let error = patchDashboardMetricsError { throw error }
        return patchDashboardMetricsResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchProgressMetrics
    var patchProgressMetricsResult: AccountResponse?
    var patchProgressMetricsError: Error?

    func patchProgressMetrics(_ metrics: [String]) async throws -> AccountResponse {
        if let error = patchProgressMetricsError { throw error }
        return patchProgressMetricsResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchStreak
    var patchStreakResult: AccountResponse?
    var patchStreakError: Error?

    func patchStreak(_ isStreakOn: Bool, _ streakTimestamp: String) async throws -> AccountResponse {
        if let error = patchStreakError { throw error }
        return patchStreakResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - patchWeightless
    var patchWeightlessResult: AccountResponse?
    var patchWeightlessError: Error?

    func patchWeightless(_ isWeightlessOn: Bool, _ weightlessTimestamp: String, _ weightlessWeight: Int) async throws -> AccountResponse {
        if let error = patchWeightlessError { throw error }
        return patchWeightlessResult ?? AccountTestFixtures.makeAccountResponse()
    }

    // MARK: - deleteAccount
    var deleteAccountError: Error?

    func deleteAccount(accountId: String) async throws {
        if let error = deleteAccountError { throw error }
    }

    // MARK: - requestPasswordReset
    var requestPasswordResetError: Error?

    func requestPasswordReset(email: String) async throws {
        if let error = requestPasswordResetError { throw error }
    }

    // MARK: - updatePassword
    var updatePasswordResult: Tokens?
    var updatePasswordError: Error?

    func updatePassword(oldPassword: String, newPassword: String) async throws -> Tokens {
        if let error = updatePasswordError { throw error }
        return updatePasswordResult ?? Tokens(accessToken: "new-token", refreshToken: "new-refresh", expiresAt: "2099-01-01")
    }

    // MARK: - refreshToken
    var refreshTokenResult: Tokens?
    var refreshTokenError: Error?

    func refreshToken(refreshToken: String, accountId: String?) async throws -> Tokens {
        if let error = refreshTokenError { throw error }
        return refreshTokenResult ?? Tokens(accessToken: "refreshed-token", refreshToken: "refreshed-refresh", expiresAt: "2099-01-01")
    }
}
