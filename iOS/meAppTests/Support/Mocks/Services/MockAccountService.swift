import Combine
import Foundation
@testable import meApp

@MainActor
final class MockAccountService: AccountServiceProtocol {
    @Published var activeAccount: Account?
    @Published private(set) var allAccounts: [Account] = []

    var activeAccountPublisher: Published<Account?>.Publisher { $activeAccount }
    var allAccountsPublisher: Published<[Account]>.Publisher { $allAccounts }

    var logInResult: Result<Account, Error> = .failure(UnexpectedCallError.methodCalled("logIn"))
    var signUpResult: Result<Account, Error> = .failure(UnexpectedCallError.methodCalled("signUp"))
    var createGoalResult: Result<Account, Error> = .failure(UnexpectedCallError.methodCalled("createGoal"))
    var requestPasswordResetResult: Result<Void, Error> = .success(())
    var updateIntegrationsResult: Result<Account, Error> = .failure(UnexpectedCallError.methodCalled("updateIntegrations"))
    var deleteHealthIntegrationResult: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("deleteHealthIntegration"))
    var refreshAccountResult: Result<Account, Error> = .failure(UnexpectedCallError.methodCalled("refreshAccount"))
    var updatePublishedStateError: Error?
    var shouldDeferUnauthenticatedLandingResult = false

    private(set) var logInCalls = 0
    private(set) var signUpCalls = 0
    private(set) var createGoalCalls = 0
    private(set) var requestPasswordResetCalls = 0
    private(set) var updateIntegrationsCalls = 0
    private(set) var deleteHealthIntegrationCalls = 0
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
    private(set) var lastRefreshAccountId: String?

    func seedAccounts(_ accounts: [Account], active: Account? = nil) {
        allAccounts = accounts
        activeAccount = active
    }

    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        signUpCalls += 1
        lastSignUpEmail = email
        lastSignUpPassword = password
        lastSignUpProfile = profile

        let account = try signUpResult.get()
        activeAccount = account
        return account
    }

    func logIn(email: String, password: String) async throws -> Account {
        logInCalls += 1
        lastLoginEmail = email
        lastLoginPassword = password

        let account = try logInResult.get()
        activeAccount = account
        return account
    }

    func logOut(accountId: String?, isAutoLogout: Bool) async throws {
        throw UnexpectedCallError.methodCalled("logOut")
    }

    func deleteAccount() async throws {
        throw UnexpectedCallError.methodCalled("deleteAccount")
    }

    func deleteAllAccounts() async throws {
        throw UnexpectedCallError.methodCalled("deleteAllAccounts")
    }

    func switchAccount(to account: Account) async throws {
        throw UnexpectedCallError.methodCalled("switchAccount")
    }

    func setActiveAccount(_ account: Account) async throws {
        activeAccount = account
    }

    func shouldDeferUnauthenticatedLanding() -> Bool {
        shouldDeferUnauthenticatedLandingResult
    }

    func getActiveAccount() async throws -> Account? {
        activeAccount
    }

    func getAllLoggedInAccounts() async throws -> [Account] {
        allAccounts.filter { $0.isLoggedIn ?? false }
    }

    func fetchAccount(byId id: String) async throws -> Account? {
        allAccounts.first { $0.accountId == id }
    }

    func fetchAllAccounts() async throws -> [Account] {
        allAccounts
    }

    func createGoal(_ goal: Goal) async throws -> Account {
        createGoalCalls += 1
        lastCreatedGoal = goal
        return try createGoalResult.get()
    }

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateProfile")
    }

    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateBodyComp")
    }

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        throw UnexpectedCallError.methodCalled("updateTokens")
    }

    func updateDashboardType(type: DashboardType) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateDashboardType")
    }

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws -> Account {
        updateIntegrationsCalls += 1
        lastIntegrationType = integrationType
        lastIntegrationPreferences = preferences
        return try updateIntegrationsResult.get()
    }

    func updateNotifications(notifications: Notifications) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateNotifications")
    }

    func updateDashboardMetrics(metrics: [String]) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateDashboardMetrics")
    }

    func updateProgressMetrics(metrics: [String]) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateProgressMetrics")
    }

    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateStreak")
    }

    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateWeightless")
    }

    func requestPasswordReset(email: String) async throws {
        requestPasswordResetCalls += 1
        lastPasswordResetEmail = email
        _ = try requestPasswordResetResult.get()
    }

    func updatePassword(oldPassword: String, newPassword: String) async throws {
        throw UnexpectedCallError.methodCalled("updatePassword")
    }

    func refreshAllAccounts() async throws {
        throw UnexpectedCallError.methodCalled("refreshAllAccounts")
    }

    func refreshAccount(accountId: String?) async throws -> Account {
        refreshAccountCalls += 1
        lastRefreshAccountId = accountId
        let account = try refreshAccountResult.get()
        activeAccount = account
        return account
    }

    func logOutAllAccounts() async throws {
        throw UnexpectedCallError.methodCalled("logOutAllAccounts")
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

    func updatePublishedState(forceRefresh: Bool) async throws {
        updatePublishedStateCalls += 1
        if let updatePublishedStateError { throw updatePublishedStateError }
    }
}
