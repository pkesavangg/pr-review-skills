#if DEBUG
import Combine
import Foundation

@MainActor
final class UITestAccountService: AccountServiceProtocol {
    @Published var activeAccount: Account?
    @Published var allAccounts: [Account] = []

    var activeAccountPublisher: Published<Account?>.Publisher { $activeAccount }
    var allAccountsPublisher: Published<[Account]>.Publisher { $allAccounts }

    private let scenario: UITestLaunchOptions.Scenario

    init(scenario: UITestLaunchOptions.Scenario) {
        self.scenario = scenario
    }

    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        throw UITestAccountServiceError.unsupported("signUp")
    }

    func logIn(email: String, password: String) async throws -> Account {
        switch scenario {
        case .loginUnauthorized:
            throw HTTPError.unauthorized
        case .loginNetworkError:
            throw HTTPError.noInternet
        case .loggedOut, .loginSuccess:
            let account = makeAccount(email: email)
            allAccounts = [account]
            // Keep active account nil in UI tests to avoid triggering full startup sync paths.
            activeAccount = nil
            return account
        }
    }

    func logOut(accountId: String?, isAutoLogout: Bool) async throws {
        activeAccount = nil
        allAccounts = []
    }

    func deleteAccount() async throws {
        throw UITestAccountServiceError.unsupported("deleteAccount")
    }

    func deleteAllAccounts() async throws {
        activeAccount = nil
        allAccounts = []
    }

    func switchAccount(to account: Account) async throws {
        throw UITestAccountServiceError.unsupported("switchAccount")
    }

    func setActiveAccount(_ account: Account) async throws {
        activeAccount = account
    }

    func shouldDeferUnauthenticatedLanding() -> Bool {
        false
    }

    func getActiveAccount() async throws -> Account? {
        activeAccount
    }

    func getAllLoggedInAccounts() async throws -> [Account] {
        allAccounts.filter { $0.isLoggedIn == true }
    }

    func fetchAccount(byId id: String) async throws -> Account? {
        allAccounts.first { $0.accountId == id }
    }

    func fetchAllAccounts() async throws -> [Account] {
        allAccounts
    }

    func createGoal(_ goal: Goal) async throws -> Account {
        throw UITestAccountServiceError.unsupported("createGoal")
    }

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateProfile")
    }

    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateBodyComp")
    }

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        throw UITestAccountServiceError.unsupported("updateTokens")
    }

    func updateDashboardType(type: DashboardType) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateDashboardType")
    }

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateIntegrations")
    }

    func updateNotifications(notifications: Notifications) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateNotifications")
    }

    func updateDashboardMetrics(metrics: [String]) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateDashboardMetrics")
    }

    func updateProgressMetrics(metrics: [String]) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateProgressMetrics")
    }

    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateStreak")
    }

    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws -> Account {
        throw UITestAccountServiceError.unsupported("updateWeightless")
    }

    func requestPasswordReset(email: String) async throws {
        // Keep reset flow deterministic in UI tests.
    }

    func updatePassword(oldPassword: String, newPassword: String) async throws {
        throw UITestAccountServiceError.unsupported("updatePassword")
    }

    func refreshAllAccounts() async throws {
        throw UITestAccountServiceError.unsupported("refreshAllAccounts")
    }

    func refreshAccount(accountId: String?) async throws -> Account {
        throw UITestAccountServiceError.unsupported("refreshAccount")
    }

    func logOutAllAccounts() async throws {
        activeAccount = nil
        allAccounts = []
    }

    func syncUnsyncedAccounts() async throws {
        // No-op in UI tests.
    }

    func getActiveTokens() async throws -> Tokens {
        throw AccountError.noActiveAccount
    }

    func refreshTokens(accountId: String?) async throws -> Tokens {
        throw UITestAccountServiceError.unsupported("refreshTokens")
    }

    func deleteHealthIntegration(_ type: IntegrationType) async throws {
        throw UITestAccountServiceError.unsupported("deleteHealthIntegration")
    }

    func updatePublishedState(forceRefresh: Bool) async throws {
        if scenario == .loggedOut || scenario == .loginUnauthorized || scenario == .loginNetworkError {
            activeAccount = nil
        }
    }

    private func makeAccount(email: String) -> Account {
        let dto = AccountDTO(
            id: "ui-test-account",
            email: email,
            firstName: "UI",
            lastName: "Tester",
            gender: .male,
            zipcode: "00000",
            weightUnit: .lb,
            isWeightlessOn: false,
            height: 70,
            activityLevel: .normal,
            dob: "1990-01-01",
            weightlessTimestamp: nil,
            weightlessWeight: nil,
            isStreakOn: false,
            streakTimestamp: nil,
            dashboardType: nil,
            dashboardMetrics: nil,
            progressMetrics: nil,
            goalType: nil,
            goalWeight: nil,
            goalPercent: nil,
            initialWeight: nil,
            shouldSendEntryNotifications: true,
            shouldSendWeightInEntryNotifications: false,
            isFitbitOn: false,
            isFitbitValid: false,
            isMFPOn: false,
            isMFPValid: false,
            isHealthKitOn: false,
            isHealthConnectOn: false
        )
        let account = Account(from: dto)
        account.isLoggedIn = true
        account.isExpired = false
        account.isActiveAccount = false
        return account
    }
}

enum UITestAccountServiceError: Error {
    case unsupported(String)
}
#endif
