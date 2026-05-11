import Combine
import Foundation
@testable import meApp

@MainActor
final class MockAccountService: AccountServiceProtocol {
    @Published var activeAccount: AccountSnapshot?
    @Published private(set) var allAccounts: [AccountSnapshot] = []

    var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { $activeAccount }
    var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { $allAccounts }

    var logInResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("logIn"))
    var signUpResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("signUp"))
    var createGoalResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("createGoal"))
    var updateProductTypesResult: Result<Void, Error> = .success(())
    var requestPasswordResetResult: Result<Void, Error> = .success(())
    var updateIntegrationsResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("updateIntegrations"))
    var deleteHealthIntegrationResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("deleteHealthIntegration"))
    var updateProfileResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("updateProfile"))
    var updateBodyCompResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("updateBodyComp"))
    var updateNotificationsResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("updateNotifications"))
    var updateWeightlessResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("updateWeightless"))
    var updatePasswordResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("updatePassword"))
    var logOutResult: Result<Void, Error> = .success(())
    var logOutAllAccountsResult: Result<Void, Error> = .success(())
    var deleteAccountResult: Result<Void, Error> = .success(())
    var switchAccountResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("switchAccount"))
    var refreshAccountResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("refreshAccount"))
    var updatePublishedStateError: Error?
    var shouldDeferUnauthenticatedLandingResult = false
    var deleteAllAccountsError: Error?

    private(set) var logInCalls = 0
    private(set) var deleteAllAccountsCalls = 0
    private(set) var signUpCalls = 0
    private(set) var createGoalCalls = 0
    private(set) var updateProductTypesCalls = 0
    private(set) var lastUpdatedProductTypes: [String]?
    private(set) var allUpdatedProductTypes: [[String]] = []
    private(set) var requestPasswordResetCalls = 0
    private(set) var updateIntegrationsCalls = 0
    private(set) var deleteHealthIntegrationCalls = 0
    private(set) var updateProfileCalls = 0
    private(set) var updateBodyCompCalls = 0
    private(set) var updateNotificationsCalls = 0
    private(set) var updateWeightlessCalls = 0
    private(set) var updatePasswordCalls = 0
    private(set) var logOutCalls = 0
    private(set) var logOutAllAccountsCalls = 0
    private(set) var deleteAccountCalls = 0
    private(set) var switchAccountCalls = 0
    private(set) var refreshAccountCalls = 0
    private(set) var updatePublishedStateCalls = 0
    private(set) var lastLoginEmail: String?
    private(set) var lastLoginPassword: String?
    private(set) var lastSignUpEmail: String?
    private(set) var lastSignUpPassword: String?
    private(set) var lastSignUpProfile: Profile?
    private(set) var lastCreatedGoal: Goal?
    private(set) var lastPasswordResetEmail: String?
    private(set) var lastIntegrationType: IntegrationType?
    private(set) var lastIntegrationPreferences: [String: AnyCodable]?
    private(set) var lastDeletedHealthIntegrationType: IntegrationType?
    private(set) var lastUpdatedProfile: Profile?
    private(set) var lastUpdatedBodyComp: BodyComp?
    private(set) var lastUpdatedNotifications: Notifications?
    private(set) var lastUpdatedWeightlessOn: Bool?
    private(set) var lastUpdatedWeightlessWeight: Double?
    private(set) var lastUpdatedOldPassword: String?
    private(set) var lastUpdatedNewPassword: String?
    private(set) var lastLoggedOutAccountId: String?
    private(set) var lastIsAutoLogout: Bool?
    private(set) var lastSwitchedAccountId: String?
    private(set) var lastRefreshAccountId: String?

    func seedAccounts(_ accounts: [AccountSnapshot], active: AccountSnapshot? = nil) {
        allAccounts = accounts
        activeAccount = active
    }

    func signUp(email: String, password: String, profile: Profile) async throws {
        signUpCalls += 1
        lastSignUpEmail = email
        lastSignUpPassword = password
        lastSignUpProfile = profile

        try signUpResult.get()
        // On success, set a stub activeAccount so createUser can proceed to device saves
        activeAccount = AccountTestFixtures.makeAccountSnapshot(email: email, isActiveAccount: true)
    }

    func logIn(email: String, password: String) async throws {
        logInCalls += 1
        lastLoginEmail = email
        lastLoginPassword = password

        try logInResult.get()
    }

    func logOut(accountId: String?, isAutoLogout: Bool) async throws {
        logOutCalls += 1
        lastLoggedOutAccountId = accountId
        lastIsAutoLogout = isAutoLogout
        _ = try logOutResult.get()
    }

    func deleteAccount() async throws {
        deleteAccountCalls += 1
        _ = try deleteAccountResult.get()
    }

    func deleteAllAccounts() async throws {
        deleteAllAccountsCalls += 1
        if let error = deleteAllAccountsError { throw error }
    }

    func removeAccountFromDevice(accountId: String) async throws {
        throw UnexpectedCallError.methodCalled("removeAccountFromDevice")
    }

    func switchAccount(to accountId: String) async throws {
        switchAccountCalls += 1
        lastSwitchedAccountId = accountId
        _ = try switchAccountResult.get()
    }

    func setActiveAccount(accountId: String) async throws {
    }

    func shouldDeferUnauthenticatedLanding() -> Bool {
        shouldDeferUnauthenticatedLandingResult
    }

    func getActiveAccount() async throws -> AccountSnapshot? {
        activeAccount
    }

    func getAllLoggedInAccounts() async throws -> [AccountSnapshot] {
        allAccounts.filter { $0.isLoggedIn }
    }

    func fetchAccount(byId id: String) async throws -> AccountSnapshot? {
        allAccounts.first { $0.accountId == id }
    }

    func fetchAllAccounts() async throws -> [AccountSnapshot] {
        allAccounts
    }

    func createGoal(_ goal: Goal) async throws {
        createGoalCalls += 1
        lastCreatedGoal = goal
        try createGoalResult.get()
    }

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws {
        updateProfileCalls += 1
        lastUpdatedProfile = profile
        try updateProfileResult.get()
    }

    func updateBodyComp(_ bodyComp: BodyComp) async throws {
        updateBodyCompCalls += 1
        lastUpdatedBodyComp = bodyComp
        try updateBodyCompResult.get()
    }

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        throw UnexpectedCallError.methodCalled("updateTokens")
    }

    func updateDashboardType(type: DashboardType) async throws {
        throw UnexpectedCallError.methodCalled("updateDashboardType")
    }

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws {
        updateIntegrationsCalls += 1
        lastIntegrationType = integrationType
        lastIntegrationPreferences = preferences
        try updateIntegrationsResult.get()
    }

    func updateNotifications(notifications: Notifications) async throws {
        updateNotificationsCalls += 1
        lastUpdatedNotifications = notifications
        try updateNotificationsResult.get()
    }

    func updateDashboardMetrics(metrics: [String]) async throws {
        throw UnexpectedCallError.methodCalled("updateDashboardMetrics")
    }

    func updateProgressMetrics(metrics: [String]) async throws {
        throw UnexpectedCallError.methodCalled("updateProgressMetrics")
    }

    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws {
        throw UnexpectedCallError.methodCalled("updateStreak")
    }

    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws {
        updateWeightlessCalls += 1
        lastUpdatedWeightlessOn = isWeightlessOn
        lastUpdatedWeightlessWeight = weightlessWeight
        try updateWeightlessResult.get()
    }

    func requestPasswordReset(email: String) async throws {
        requestPasswordResetCalls += 1
        lastPasswordResetEmail = email
        _ = try requestPasswordResetResult.get()
    }

    func updatePassword(oldPassword: String, newPassword: String) async throws {
        updatePasswordCalls += 1
        lastUpdatedOldPassword = oldPassword
        lastUpdatedNewPassword = newPassword
        _ = try updatePasswordResult.get()
    }

    func refreshAllAccounts() async throws {
        throw UnexpectedCallError.methodCalled("refreshAllAccounts")
    }

    func refreshAccount(accountId: String?) async throws {
        refreshAccountCalls += 1
        lastRefreshAccountId = accountId
        try refreshAccountResult.get()
    }

    func logOutAllAccounts() async throws {
        logOutAllAccountsCalls += 1
        _ = try logOutAllAccountsResult.get()
    }

    func syncUnsyncedAccounts() async throws {
        throw UnexpectedCallError.methodCalled("syncUnsyncedAccounts")
    }

    func getActiveTokens() async throws -> Tokens {
        throw UnexpectedCallError.methodCalled("getActiveTokens")
    }

    func refreshTokens(accountId: String?) async throws -> Tokens {
        throw UnexpectedCallError.methodCalled("refreshTokens")
    }

    func deleteHealthIntegration(_ type: IntegrationType) async throws {
        deleteHealthIntegrationCalls += 1
        lastDeletedHealthIntegrationType = type
        _ = try deleteHealthIntegrationResult.get()
    }

    func updatePublishedState() async throws {
        updatePublishedStateCalls += 1
        if let updatePublishedStateError { throw updatePublishedStateError }
    }

    func updateProductTypes(_ productTypes: [String]) async throws {
        updateProductTypesCalls += 1
        lastUpdatedProductTypes = productTypes
        allUpdatedProductTypes.append(productTypes)
        try updateProductTypesResult.get()
    }
}
