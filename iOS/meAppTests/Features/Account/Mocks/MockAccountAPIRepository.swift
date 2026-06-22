import Foundation
@testable import meApp

@MainActor
final class MockAccountAPIRepository: AccountRepositoryAPIProtocol {
    var createAccountResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var logInResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var logOutResult: Result<Void, Error> = .success(())
    var fetchAccountResult: Result<AccountDTO, Error> = .success(AccountTestFixtures.makeAccountDTO())
    var editAccountResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var createGoalResult: Result<GoalResponse, Error> = .success(AccountTestFixtures.makeGoalResponse())
    var patchProfileResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchBodyCompResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchNotificationResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchDashboardMetricsResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchProgressMetricsResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchStreakResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchWeightlessResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var deleteAccountResult: Result<Void, Error> = .success(())
    var requestPasswordResetResult: Result<Void, Error> = .success(())
    var updatePasswordResult: Result<Tokens, Error> = .success(AccountTestFixtures.makeTokens())
    var refreshTokenResult: Result<Tokens, Error> = .success(AccountTestFixtures.makeTokens())
    var checkEmailAvailabilityResult: Result<Bool, Error> = .success(true)
    var updateMeasurementUnitsResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    var patchProductTypesResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())

    private(set) var createAccountCalls = 0
    private(set) var logInCalls = 0
    private(set) var logOutCalls = 0
    private(set) var fetchAccountCalls = 0
    private(set) var editAccountCalls = 0
    private(set) var createGoalCalls = 0
    private(set) var patchProfileCalls = 0
    private(set) var patchBodyCompCalls = 0
    private(set) var patchNotificationCalls = 0
    private(set) var patchDashboardMetricsCalls = 0
    private(set) var patchProgressMetricsCalls = 0
    private(set) var patchStreakCalls = 0
    private(set) var patchWeightlessCalls = 0
    private(set) var deleteAccountCalls = 0
    private(set) var requestPasswordResetCalls = 0
    private(set) var updatePasswordCalls = 0
    private(set) var refreshTokenCalls = 0
    private(set) var checkEmailAvailabilityCalls = 0
    private(set) var updateMeasurementUnitsCalls = 0
    private(set) var patchProductTypesCalls = 0
    private(set) var lastPatchProductTypes: [String]?

    private(set) var lastCheckEmailAvailabilityEmail: String?
    private(set) var lastUpdatedMeasurementUnits: String?
    private(set) var lastCreateAccountEmail: String?
    private(set) var lastCreateAccountPassword: String?
    private(set) var lastCreateAccountProfile: Profile?
    private(set) var lastLogInEmail: String?
    private(set) var lastLogInPassword: String?
    private(set) var lastLogOutAccountId: String?
    private(set) var lastDeleteAccountId: String?
    private(set) var lastRequestPasswordResetEmail: String?
    private(set) var lastRefreshToken: String?
    private(set) var lastPatchDashboardMetrics: [String]?
    private(set) var lastPatchProgressMetrics: [String]?

    func resetCapturedMetrics() {
        lastPatchDashboardMetrics = nil
        lastPatchProgressMetrics = nil
    }

    func createAccount(email: String, password: String, profile: Profile) async throws -> AccountResponse {
        createAccountCalls += 1
        lastCreateAccountEmail = email
        lastCreateAccountPassword = password
        lastCreateAccountProfile = profile
        return try createAccountResult.get()
    }

    func checkEmailAvailability(email: String) async throws -> Bool {
        checkEmailAvailabilityCalls += 1
        lastCheckEmailAvailabilityEmail = email
        return try checkEmailAvailabilityResult.get()
    }

    func updateMeasurementUnits(_ measurementUnits: String) async throws -> AccountResponse {
        updateMeasurementUnitsCalls += 1
        lastUpdatedMeasurementUnits = measurementUnits
        return try updateMeasurementUnitsResult.get()
    }

    func patchProductTypes(_ productTypes: [String]) async throws -> AccountResponse {
        patchProductTypesCalls += 1
        lastPatchProductTypes = productTypes
        return try patchProductTypesResult.get()
    }

    func logIn(email: String, password: String) async throws -> AccountResponse {
        logInCalls += 1
        lastLogInEmail = email
        lastLogInPassword = password
        return try logInResult.get()
    }

    func logOut(fcmToken: String?, accountId: String?) async throws {
        logOutCalls += 1
        lastLogOutAccountId = accountId
        _ = try logOutResult.get()
    }

    func fetchAccount(accountId: String?) async throws -> AccountDTO {
        fetchAccountCalls += 1
        return try fetchAccountResult.get()
    }

    func editAccount(_ updatedAccount: Account) async throws -> AccountResponse {
        editAccountCalls += 1
        return try editAccountResult.get()
    }

    func createGoal(_ goal: Goal) async throws -> GoalResponse {
        createGoalCalls += 1
        return try createGoalResult.get()
    }

    func patchProfile(_ profile: Profile) async throws -> AccountResponse {
        patchProfileCalls += 1
        return try patchProfileResult.get()
    }

    func patchBodyComp(_ bodyComp: BodyComp) async throws -> AccountResponse {
        patchBodyCompCalls += 1
        return try patchBodyCompResult.get()
    }

    func patchNotification(_ notifications: Notifications) async throws -> AccountResponse {
        patchNotificationCalls += 1
        return try patchNotificationResult.get()
    }

    func patchDashboardType(_ type: DashboardType) async throws -> AccountResponse {
        throw UnexpectedCallError.methodCalled("patchDashboardType")
    }

    func patchDashboardMetrics(_ metrics: [String]) async throws -> AccountResponse {
        patchDashboardMetricsCalls += 1
        lastPatchDashboardMetrics = metrics
        return try patchDashboardMetricsResult.get()
    }

    func patchProgressMetrics(_ metrics: [String]) async throws -> AccountResponse {
        patchProgressMetricsCalls += 1
        lastPatchProgressMetrics = metrics
        return try patchProgressMetricsResult.get()
    }

    func patchStreak(_ isStreakOn: Bool, _ streakTimestamp: String) async throws -> AccountResponse {
        patchStreakCalls += 1
        return try patchStreakResult.get()
    }

    func patchWeightless(_ isWeightlessOn: Bool, _ weightlessTimestamp: String, _ weightlessWeight: Int) async throws -> AccountResponse {
        patchWeightlessCalls += 1
        return try patchWeightlessResult.get()
    }

    func deleteAccount(accountId: String) async throws {
        deleteAccountCalls += 1
        lastDeleteAccountId = accountId
        _ = try deleteAccountResult.get()
    }

    func requestPasswordReset(email: String) async throws {
        requestPasswordResetCalls += 1
        lastRequestPasswordResetEmail = email
        _ = try requestPasswordResetResult.get()
    }

    func updatePassword(oldPassword: String, newPassword: String) async throws -> Tokens {
        updatePasswordCalls += 1
        return try updatePasswordResult.get()
    }

    func refreshToken(refreshToken: String, accountId: String?) async throws -> Tokens {
        refreshTokenCalls += 1
        lastRefreshToken = refreshToken
        return try refreshTokenResult.get()
    }

    var patchProductTypesResult: Result<AccountResponse, Error> = .success(AccountTestFixtures.makeAccountResponse())
    private(set) var patchProductTypesCalls = 0
    private(set) var lastPatchProductTypes: [String]?

    func patchProductTypes(_ productTypes: [String]) async throws -> AccountResponse {
        patchProductTypesCalls += 1
        lastPatchProductTypes = productTypes
        return try patchProductTypesResult.get()
    }
}
