import Foundation

@MainActor
final class AccountRepositoryAPI: AccountRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared

    func createAccount(email: String, password: String, profile: Profile) async throws -> AccountResponse {
        struct RegisterRequest: Codable {
            let email: String
            let password: String
            let firstName: String
            let lastName: String
            let gender: String
            let zipcode: String
            let dob: String
            let weightUnit: String
            let height: Double
            let activityLevel: String
        }
        let createAccountRequest = RegisterRequest(
            email: email,
            password: password,
            firstName: profile.firstName,
            lastName: profile.lastName,
            gender: profile.gender.rawValue,
            zipcode: profile.zipcode,
            dob: profile.dob,
            weightUnit: profile.weightUnit.rawValue,
            height: profile.height,
            activityLevel: profile.activityLevel.rawValue)
        return try await httpClient.send(.signup, method: .post, body: createAccountRequest)
    }

    func logIn(email: String, password: String) async throws -> AccountResponse {
        struct LoginRequest: Codable {
            let email: String
            let password: String
        }
        let req = LoginRequest(email: email, password: password)
        return try await httpClient.send(.login, method: .post, body: req)
    }

    func logOut(fcmToken: String?, accountId: String? = nil) async throws {
        struct LogoutRequest: Codable {
            let fcmToken: String?
        }
        let req = LogoutRequest(fcmToken: fcmToken)
        _ = try await httpClient.send(.logout, method: .post, body: req, needsAuth: true, accountId: accountId) as EmptyResponse
    }

    func fetchAccount(accountId: String? = nil) async throws -> AccountDTO {
        return try await httpClient.get(.accountInfo, needsAuth: true, accountId: accountId)
    }

    func editAccount(_ updatedAccount: Account) async throws -> AccountResponse {
        let dto = updatedAccount.toAccountDTO()
        return try await httpClient.send(.updateAccount, method: .put, body: dto, needsAuth: true)
    }
    
    func createGoal(_ goal: Goal) async throws -> GoalResponse {
        return try await httpClient.send(.setGoal, method: .post, body: goal, needsAuth: true)
    }

    func patchProfile(_ profile: Profile) async throws -> AccountResponse {
        return try await httpClient.send(.updateProfile, method: .patch, body: profile, needsAuth: true)
    }

    func patchBodyComp(_ bodyComp: BodyComp) async throws -> AccountResponse {
        return try await httpClient.send(.updateBodyComp, method: .patch, body: bodyComp, needsAuth: true)
    }

    func patchNotification(_ notifications: Notifications) async throws -> AccountResponse {
        return try await httpClient.send(.updateNotifications, method: .patch, body: notifications, needsAuth: true)
    }

    func patchDashboardType(_ type: DashboardType) async throws -> AccountResponse {
        struct DashboardTypeRequest: Codable { let dashboardType: DashboardType }
        return try await httpClient.send(.updateDashboardType, method: .patch, body: DashboardTypeRequest(dashboardType: type), needsAuth: true)
    }

    func patchDashboardMetrics(_ metrics: [String]) async throws -> AccountResponse {
        struct DashboardMetricsRequest: Codable { let dashboardMetrics: [String] }
        return try await httpClient.send(.updateDashboardMetrics, method: .patch, body: DashboardMetricsRequest(dashboardMetrics: metrics), needsAuth: true)
    }

    func patchStreak(_ isStreakOn: Bool, _ streakTimestamp: String) async throws -> AccountResponse {
        struct StreakRequest: Codable { let isStreakOn: Bool, streakTimestamp: String }
        return try await httpClient.send(.updateStreak, method: .patch, body: StreakRequest(isStreakOn: isStreakOn, streakTimestamp: streakTimestamp), needsAuth: true)
    }

    func patchWeightless(_ isWeightlessOn: Bool, _ weightlessTimestamp: String, _ weightlessWeight: Int) async throws -> AccountResponse {
        struct WeightlessRequest: Codable {
            let isWeightlessOn: Bool
            let weightlessTimestamp: String
            let weightlessWeight: Int
        }
        return try await httpClient.send(.updateWeightless, method: .patch, body: WeightlessRequest(isWeightlessOn: isWeightlessOn, weightlessTimestamp: weightlessTimestamp, weightlessWeight: weightlessWeight), needsAuth: true)
    }

    func deleteAccount(accountId: String) async throws {
        _ = try await httpClient.send(.deleteAccount, method: .delete, body: EmptyBody(), needsAuth: true) as EmptyResponse
    }

    func requestPasswordReset(email: String) async throws {
        struct Request: Codable { let email: String }
        _ = try await httpClient.send(.requestPasswordReset, method: .post, body: Request(email: email)) as EmptyResponse
    }

    func updatePassword(oldPassword: String, newPassword: String) async throws -> Tokens {
        struct Request: Codable { let oldPassword: String; let newPassword: String }
        return try await httpClient.send(.changePassword, method: .put, body: Request(oldPassword: oldPassword, newPassword: newPassword), needsAuth: true)
    }
    
    func refreshToken(refreshToken: String, accountId: String?) async throws -> Tokens {
        struct Request: Codable { let refreshToken: String }
        return try await httpClient.send(
            .refreshToken,
            method: .post,
            body: Request(refreshToken: refreshToken),
            needsAuth: true,
            accountId: accountId
        )
    }
}
