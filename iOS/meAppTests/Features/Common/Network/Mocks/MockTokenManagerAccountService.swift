import Combine
import Foundation
@testable import meApp

@MainActor
final class MockTokenManagerAccountService: AccountServiceProtocol {
    @Published var activeAccount: Account?
    @Published private(set) var allAccounts: [Account] = []

    var activeAccountPublisher: Published<Account?>.Publisher { $activeAccount }
    var allAccountsPublisher: Published<[Account]>.Publisher { $allAccounts }

    var refreshTokensResult: Result<Tokens, Error> = .failure(UnexpectedCallError.methodCalled("refreshTokens"))
    var refreshTokensResultsQueue: [Result<Tokens, Error>] = []
    var getActiveTokensResult: Result<Tokens, Error> = .failure(UnexpectedCallError.methodCalled("getActiveTokens"))
    var updateTokensError: Error?
    var logOutError: Error?
    var refreshDelayNs: UInt64 = 0

    private(set) var refreshTokensCalls = 0
    private(set) var updateTokensCalls = 0
    private(set) var getActiveTokensCalls = 0
    private(set) var logOutCalls = 0
    private(set) var lastRefreshAccountId: String?
    private(set) var lastUpdateTokens: Tokens?
    private(set) var lastUpdateAccountId: String?
    private(set) var lastLogoutAccountId: String?
    private(set) var lastLogoutIsAutoLogout: Bool?

    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        throw UnexpectedCallError.methodCalled("signUp")
    }

    func logIn(email: String, password: String) async throws -> Account {
        throw UnexpectedCallError.methodCalled("logIn")
    }

    func logOut(accountId: String?, isAutoLogout: Bool) async throws {
        logOutCalls += 1
        lastLogoutAccountId = accountId
        lastLogoutIsAutoLogout = isAutoLogout
        if let logOutError { throw logOutError }
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
        throw UnexpectedCallError.methodCalled("setActiveAccount")
    }

    func shouldDeferUnauthenticatedLanding() -> Bool {
        false
    }

    func getActiveAccount() async throws -> Account? {
        throw UnexpectedCallError.methodCalled("getActiveAccount")
    }

    func getAllLoggedInAccounts() async throws -> [Account] {
        throw UnexpectedCallError.methodCalled("getAllLoggedInAccounts")
    }

    func fetchAccount(byId id: String) async throws -> Account? {
        throw UnexpectedCallError.methodCalled("fetchAccount")
    }

    func fetchAllAccounts() async throws -> [Account] {
        throw UnexpectedCallError.methodCalled("fetchAllAccounts")
    }

    func createGoal(_ goal: Goal) async throws -> Account {
        throw UnexpectedCallError.methodCalled("createGoal")
    }

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateProfile")
    }

    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateBodyComp")
    }

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        updateTokensCalls += 1
        lastUpdateTokens = tokens
        lastUpdateAccountId = accountId
        if let updateTokensError { throw updateTokensError }
    }

    func updateDashboardType(type: DashboardType) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateDashboardType")
    }

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws -> Account {
        throw UnexpectedCallError.methodCalled("updateIntegrations")
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
        throw UnexpectedCallError.methodCalled("requestPasswordReset")
    }

    func updatePassword(oldPassword: String, newPassword: String) async throws {
        throw UnexpectedCallError.methodCalled("updatePassword")
    }

    func refreshAllAccounts() async throws {
        throw UnexpectedCallError.methodCalled("refreshAllAccounts")
    }

    func refreshAccount(accountId: String?) async throws -> Account {
        throw UnexpectedCallError.methodCalled("refreshAccount")
    }

    func logOutAllAccounts() async throws {
        throw UnexpectedCallError.methodCalled("logOutAllAccounts")
    }

    func syncUnsyncedAccounts() async throws {
        throw UnexpectedCallError.methodCalled("syncUnsyncedAccounts")
    }

    func getActiveTokens() async throws -> Tokens {
        getActiveTokensCalls += 1
        return try getActiveTokensResult.get()
    }

    func refreshTokens(accountId: String?) async throws -> Tokens {
        refreshTokensCalls += 1
        lastRefreshAccountId = accountId
        if refreshDelayNs > 0 {
            try? await Task.sleep(nanoseconds: refreshDelayNs)
        }
        if !refreshTokensResultsQueue.isEmpty {
            return try refreshTokensResultsQueue.removeFirst().get()
        }
        return try refreshTokensResult.get()
    }

    func deleteHealthIntegration(_ type: IntegrationType) async throws {
        throw UnexpectedCallError.methodCalled("deleteHealthIntegration")
    }

    func updatePublishedState(forceRefresh: Bool) async throws {
        throw UnexpectedCallError.methodCalled("updatePublishedState")
    }
}
