#if DEBUG
import Combine
import Foundation

@MainActor
final class UITestAccountService: AccountServiceProtocol {
    @Published var activeAccount: AccountSnapshot?
    @Published var allAccounts: [AccountSnapshot] = []
    @Published var isSignupInProgress: Bool = false
    var isSignupInProgressPublisher: Published<Bool>.Publisher { $isSignupInProgress }

    var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { $activeAccount }
    var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { $allAccounts }

    private let scenario: UITestLaunchOptions.Scenario

    init(scenario: UITestLaunchOptions.Scenario) {
        self.scenario = scenario
    }

    func signUp(email: String, password: String, profile: Profile) async throws {
        throw UITestAccountServiceError.unsupported("signUp")
    }

    func logIn(email: String, password: String) async throws {
        switch scenario {
        case .loginUnauthorized:
            throw HTTPError.unauthorized
        case .loginNetworkError:
            throw HTTPError.noInternet
        case .loggedOut, .loginSuccess:
            let snapshot = makeSnapshot(email: email)
            allAccounts = [snapshot]
            activeAccount = nil
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

    func removeAccountFromDevice(accountId: String) async throws {
        throw UITestAccountServiceError.unsupported("removeAccountFromDevice")
    }

    func switchAccount(to accountId: String) async throws {
        throw UITestAccountServiceError.unsupported("switchAccount")
    }

    func setActiveAccount(accountId: String) async throws {
        activeAccount = allAccounts.first { $0.accountId == accountId }
    }

    func shouldDeferUnauthenticatedLanding() -> Bool {
        false
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
        throw UITestAccountServiceError.unsupported("createGoal")
    }

    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws {
        throw UITestAccountServiceError.unsupported("updateProfile")
    }

    func updateBodyComp(_ bodyComp: BodyComp) async throws {
        throw UITestAccountServiceError.unsupported("updateBodyComp")
    }

    func updateProductTypes(_ productTypes: [String]) async throws {
        throw UITestAccountServiceError.unsupported("updateProductTypes")
    }

    func updateMeasurementUnits(_ measurementUnits: MeasurementUnits) async throws {
        throw UITestAccountServiceError.unsupported("updateMeasurementUnits")
    }

    func checkEmailAvailability(email: String) async throws -> Bool {
        throw UITestAccountServiceError.unsupported("checkEmailAvailability")
    }

    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws {
        throw UITestAccountServiceError.unsupported("updateTokens")
    }

    func updateDashboardType(type: DashboardType) async throws {
        throw UITestAccountServiceError.unsupported("updateDashboardType")
    }

    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable]) async throws {
        throw UITestAccountServiceError.unsupported("updateIntegrations")
    }

    func updateNotifications(notifications: Notifications) async throws {
        throw UITestAccountServiceError.unsupported("updateNotifications")
    }

    func updateDashboardMetrics(metrics: [String]) async throws {
        throw UITestAccountServiceError.unsupported("updateDashboardMetrics")
    }

    func updateProgressMetrics(metrics: [String]) async throws {
        throw UITestAccountServiceError.unsupported("updateProgressMetrics")
    }

    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws {
        throw UITestAccountServiceError.unsupported("updateStreak")
    }

    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws {
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

    func refreshAccount(accountId: String?) async throws {
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

    func updatePublishedState() async throws {
        if scenario == .loggedOut || scenario == .loginUnauthorized || scenario == .loginNetworkError {
            activeAccount = nil
        }
    }

    private func makeSnapshot(email: String) -> AccountSnapshot {
        AccountSnapshot(
            accountId: "ui-test-account",
            email: email,
            firstName: "UI",
            lastName: "Tester",
            gender: .male,
            height: "70",
            dob: "1990-01-01",
            zipcode: "00000",
            isLoggedIn: true,
            isExpired: false,
            isActiveAccount: false,
            fcmToken: nil,
            lastActiveTime: nil,
            isSynced: true,
            productTypes: [],
            weightUnit: .lb,
            weightHeight: "70",
            activityLevel: .normal,
            goalType: nil,
            goalWeight: nil,
            initialWeight: nil,
            goalPercent: nil,
            goalIsSynced: false,
            isStreakOn: false,
            streakTimestamp: nil,
            isWeightlessOn: false,
            weightlessWeight: nil,
            weightlessTimestamp: nil,
            shouldSendEntryNotifications: true,
            shouldSendWeightInEntryNotifications: false,
            dashboardType: nil,
            dashboardMetrics: nil,
            progressMetrics: nil,
            isHealthKitOn: false,
            isFitbitOn: false,
            isFitbitValid: false,
            isHealthConnectOn: false,
            isMfpOn: false,
            isMfpValid: false,
            accessToken: nil,
            refreshToken: nil,
            expiresAt: nil
        )
    }
}

enum UITestAccountServiceError: Error {
    case unsupported(String)
}
#endif
