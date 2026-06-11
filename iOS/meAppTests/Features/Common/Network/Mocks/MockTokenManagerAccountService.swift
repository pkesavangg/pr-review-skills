import Combine
import Foundation
@testable import meApp

@MainActor
final class MockTokenManagerAccountService: AccountServiceProtocol {
    @Published var activeAccount: AccountSnapshot?
    @Published private(set) var allAccounts: [AccountSnapshot] = []
    @Published var isSignupInProgress: Bool = false
    var isSignupInProgressPublisher: Published<Bool>.Publisher { $isSignupInProgress }

    var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { $activeAccount }
    var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { $allAccounts }

    var refreshTokensResult: Result<Tokens, Error> = .failure(UnexpectedCallError.methodCalled("refreshTokens"))
    var refreshTokensResultsQueue: [Result<Tokens, Error>] = []
    var getActiveTokensResult: Result<Tokens, Error> = .failure(UnexpectedCallError.methodCalled("getActiveTokens"))
    var updateTokensError: Error?
    var logOutError: Error?
    var refreshDelayNs: UInt64 = 0
    var fetchAccountById: [String: AccountSnapshot] = [:]

    private(set) var refreshTokensCalls = 0
    private(set) var updateTokensCalls = 0
    private(set) var getActiveTokensCalls = 0
    private(set) var logOutCalls = 0
    private(set) var lastRefreshAccountId: String?
    private(set) var lastUpdateTokens: Tokens?
    private(set) var lastUpdateAccountId: String?
    private(set) var lastLogoutAccountId: String?
    private(set) var lastLogoutIsAutoLogout: Bool?
    private(set) var fetchAccountCalls = 0
    private(set) var lastFetchAccountId: String?

    func signUp(email: String, password: String, profile: Profile) async throws {
        throw UnexpectedCallError.methodCalled("signUp")
    }

    func logIn(email: String, password: String) async throws {
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

    func switchAccount(to accountId: String) async throws {
        throw UnexpectedCallError.methodCalled("switchAccount")
    }

    func setActiveAccount(accountId: String) async throws {
        throw UnexpectedCallError.methodCalled("setActiveAccount")
    }

    func shouldDeferUnauthenticatedLanding() -> Bool {
        false
    }

    func getActiveAccount() async throws -> AccountSnapshot? {
        throw UnexpectedCallError.methodCalled("getActiveAccount")
    }

    func getAllLoggedInAccounts() async throws -> [AccountSnapshot] {
        throw UnexpectedCallError.methodCalled("getAllLoggedInAccounts")
    }

    func fetchAccount(byId id: String) async throws -> AccountSnapshot? {
        fetchAccountCalls += 1
        lastFetchAccountId = id
        return fetchAccountById[id]
    }

    func fetchAllAccounts() async throws -> [AccountSnapshot] {
        throw UnexpectedCallError.methodCalled("fetchAllAccounts")
    }

    func createGoal(_ goal: Goal) async throws {
        throw UnexpectedCallError.methodCalled("createGoal")
    }

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws {
        throw UnexpectedCallError.methodCalled("updateProfile")
    }

    func updateBodyComp(_ bodyComp: BodyComp) async throws {
        throw UnexpectedCallError.methodCalled("updateBodyComp")
    }

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        updateTokensCalls += 1
        lastUpdateTokens = tokens
        lastUpdateAccountId = accountId
        if let updateTokensError { throw updateTokensError }
    }

    func updateDashboardType(type: DashboardType) async throws {
        throw UnexpectedCallError.methodCalled("updateDashboardType")
    }

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws {
        throw UnexpectedCallError.methodCalled("updateIntegrations")
    }

    func updateNotifications(notifications: Notifications) async throws {
        throw UnexpectedCallError.methodCalled("updateNotifications")
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

    func refreshAccount(accountId: String?) async throws {
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

    func removeAccountFromDevice(accountId: String) async throws {
        throw UnexpectedCallError.methodCalled("removeAccountFromDevice")
    }

    func updatePublishedState() async throws {
        throw UnexpectedCallError.methodCalled("updatePublishedState")
    }

    func updateProductTypes(_ productTypes: [String]) async throws {
        throw UnexpectedCallError.methodCalled("updateProductTypes")
    }

    func updateMeasurementUnits(_ measurementUnits: MeasurementUnits) async throws {
        throw UnexpectedCallError.methodCalled("updateMeasurementUnits")
    }

    func checkEmailAvailability(email: String) async throws -> Bool {
        throw UnexpectedCallError.methodCalled("checkEmailAvailability")
    }
}
