//
//  MockAccountService.swift
//  meAppTests
//

import Foundation
import Combine
@testable import meApp

/// Mock for AccountServiceProtocol used in SignupStore and related tests.
@MainActor
final class MockAccountService: AccountServiceProtocol {

    // MARK: - activeAccount
    var activeAccount: Account? {
        get { _activeAccount }
        set { _activeAccount = newValue; activeAccountSubject.send(newValue) }
    }
    private var _activeAccount: Account?
    private let activeAccountSubject = CurrentValueSubject<Account?, Never>(nil)
    var activeAccountPublisher: AnyPublisher<Account?, Never> {
        activeAccountSubject.eraseToAnyPublisher()
    }

    // MARK: - signUp
    var signUpResult: Account?
    var signUpError: Error?
    var signUpCallCount = 0
    var lastSignUpEmail: String?
    var lastSignUpPassword: String?
    var lastSignUpProfile: Profile?

    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        signUpCallCount += 1
        lastSignUpEmail = email
        lastSignUpPassword = password
        lastSignUpProfile = profile
        if let error = signUpError { throw error }
        let account = signUpResult ?? AccountTestFixtures.makeAccount()
        activeAccount = account
        return account
    }

    // MARK: - logIn
    var logInResult: Account?
    var logInError: Error?

    func logIn(email: String, password: String) async throws -> Account {
        if let error = logInError { throw error }
        return logInResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - logOut
    var logOutError: Error?

    func logOut(accountId: String?, isAutoLogout: Bool) async throws {
        if let error = logOutError { throw error }
    }

    // MARK: - deleteAccount
    var deleteAccountError: Error?

    func deleteAccount() async throws {
        if let error = deleteAccountError { throw error }
    }

    // MARK: - deleteAllAccounts
    var deleteAllAccountsError: Error?
    var deleteAllAccountsCallCount = 0

    func deleteAllAccounts() async throws {
        deleteAllAccountsCallCount += 1
        if let error = deleteAllAccountsError { throw error }
    }

    // MARK: - switchAccount
    var switchAccountError: Error?

    func switchAccount(to account: Account) async throws {
        if let error = switchAccountError { throw error }
    }

    // MARK: - setActiveAccount
    var setActiveAccountError: Error?

    func setActiveAccount(_ account: Account) async throws {
        if let error = setActiveAccountError { throw error }
        activeAccount = account
    }

    // MARK: - getActiveAccount
    var getActiveAccountResult: Account?
    var getActiveAccountError: Error?

    func getActiveAccount() async throws -> Account? {
        if let error = getActiveAccountError { throw error }
        return getActiveAccountResult ?? activeAccount
    }

    // MARK: - getAllLoggedInAccounts
    var getAllLoggedInAccountsResult: [Account] = []
    var getAllLoggedInAccountsError: Error?

    func getAllLoggedInAccounts() async throws -> [Account] {
        if let error = getAllLoggedInAccountsError { throw error }
        return getAllLoggedInAccountsResult
    }

    // MARK: - fetchAccount
    var fetchAccountResult: Account?
    var fetchAccountError: Error?

    func fetchAccount(byId id: String) async throws -> Account? {
        if let error = fetchAccountError { throw error }
        return fetchAccountResult
    }

    // MARK: - fetchAllAccounts
    var fetchAllAccountsResult: [Account] = []
    var fetchAllAccountsError: Error?

    func fetchAllAccounts() async throws -> [Account] {
        if let error = fetchAllAccountsError { throw error }
        return fetchAllAccountsResult
    }

    // MARK: - updateAccount
    var updateAccountResult: Account?
    var updateAccountError: Error?

    func updateAccount(_ updatedAccount: Account) async throws -> Account {
        if let error = updateAccountError { throw error }
        return updateAccountResult ?? updatedAccount
    }

    // MARK: - updateProfile
    var updateProfileResult: Account?
    var updateProfileError: Error?

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws -> Account {
        if let error = updateProfileError { throw error }
        return updateProfileResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateBodyComp
    var updateBodyCompResult: Account?
    var updateBodyCompError: Error?

    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account {
        if let error = updateBodyCompError { throw error }
        return updateBodyCompResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateTokens
    var updateTokensError: Error?

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        if let error = updateTokensError { throw error }
    }

    // MARK: - updateDashboardType
    var updateDashboardTypeResult: Account?
    var updateDashboardTypeError: Error?

    func updateDashboardType(type: DashboardType) async throws -> Account {
        if let error = updateDashboardTypeError { throw error }
        return updateDashboardTypeResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateIntegrations
    var updateIntegrationsResult: Account?
    var updateIntegrationsError: Error?

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws -> Account {
        if let error = updateIntegrationsError { throw error }
        return updateIntegrationsResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateNotifications
    var updateNotificationsResult: Account?
    var updateNotificationsError: Error?

    func updateNotifications(notifications: Notifications) async throws -> Account {
        if let error = updateNotificationsError { throw error }
        return updateNotificationsResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateDashboardMetrics
    var updateDashboardMetricsResult: Account?
    var updateDashboardMetricsError: Error?

    func updateDashboardMetrics(metrics: [String]) async throws -> Account {
        if let error = updateDashboardMetricsError { throw error }
        return updateDashboardMetricsResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateProgressMetrics
    var updateProgressMetricsResult: Account?
    var updateProgressMetricsError: Error?

    func updateProgressMetrics(metrics: [String]) async throws -> Account {
        if let error = updateProgressMetricsError { throw error }
        return updateProgressMetricsResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateStreak
    var updateStreakResult: Account?
    var updateStreakError: Error?

    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws -> Account {
        if let error = updateStreakError { throw error }
        return updateStreakResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - updateWeightless
    var updateWeightlessResult: Account?
    var updateWeightlessError: Error?

    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws -> Account {
        if let error = updateWeightlessError { throw error }
        return updateWeightlessResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - requestPasswordReset
    var requestPasswordResetError: Error?

    func requestPasswordReset(email: String) async throws {
        if let error = requestPasswordResetError { throw error }
    }

    // MARK: - updatePassword
    var updatePasswordError: Error?

    func updatePassword(oldPassword: String, newPassword: String) async throws {
        if let error = updatePasswordError { throw error }
    }

    // MARK: - refreshAllAccounts
    var refreshAllAccountsError: Error?

    func refreshAllAccounts() async throws {
        if let error = refreshAllAccountsError { throw error }
    }

    // MARK: - refreshAccount
    var refreshAccountResult: Account?
    var refreshAccountError: Error?

    func refreshAccount(accountId: String?) async throws -> Account {
        if let error = refreshAccountError { throw error }
        return refreshAccountResult ?? AccountTestFixtures.makeAccount()
    }

    // MARK: - clearOfflineData
    var clearOfflineDataError: Error?

    func clearOfflineData(for account: Account) async throws {
        if let error = clearOfflineDataError { throw error }
    }

    // MARK: - logOutAllAccounts
    var logOutAllAccountsError: Error?

    func logOutAllAccounts() async throws {
        if let error = logOutAllAccountsError { throw error }
    }

    // MARK: - syncUnsyncedAccounts
    var syncUnsyncedAccountsError: Error?

    func syncUnsyncedAccounts() async throws {
        if let error = syncUnsyncedAccountsError { throw error }
    }

    // MARK: - getActiveTokens
    var getActiveTokensResult: Tokens?
    var getActiveTokensError: Error?

    func getActiveTokens() async throws -> Tokens {
        if let error = getActiveTokensError { throw error }
        return getActiveTokensResult ?? Tokens(accessToken: "access", refreshToken: "refresh", expiresAt: "2099-01-01")
    }

    // MARK: - refreshTokens
    var refreshTokensResult: Tokens?
    var refreshTokensError: Error?

    func refreshTokens(accountId: String?) async throws -> Tokens {
        if let error = refreshTokensError { throw error }
        return refreshTokensResult ?? Tokens(accessToken: "access", refreshToken: "refresh", expiresAt: "2099-01-01")
    }

    // MARK: - createGoal
    var createGoalResult: Account?
    var createGoalError: Error?
    var createGoalCallCount = 0
    var lastCreateGoal: Goal?

    @discardableResult
    func createGoal(_ goal: Goal) async throws -> Account {
        createGoalCallCount += 1
        lastCreateGoal = goal
        if let error = createGoalError { throw error }
        return createGoalResult ?? AccountTestFixtures.makeAccount()
    }
}
