import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct AccountRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: AccountRepositoryAPI, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = AccountRepositoryAPI(httpClient: http)
        return (sut, http)
    }

    // MARK: - createAccount

    @Test("createAccount success: calls send with signup endpoint POST no-auth, returns AccountResponse")
    func createAccountSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse(accountId: "42", email: "new@example.com")

        let profile = AccountTestFixtures.makeProfile(email: "new@example.com")
        let result = try await sut.createAccount(email: "new@example.com", password: "secret", profile: profile)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == false)
        guard case .signup = http.lastSendEndpoint else {
            Issue.record("Expected .signup endpoint"); return
        }
        #expect(result.account.id == "42")
        #expect(result.account.email == "new@example.com")
    }

    @Test("createAccount failure: propagates error from http client")
    func createAccountFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.createAccount(
                email: "new@example.com",
                password: "secret",
                profile: AccountTestFixtures.makeProfile()
            )
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - logIn

    @Test("logIn success: calls send with login endpoint POST no-auth, returns AccountResponse")
    func logInSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse(accountId: "7", email: "user@example.com")

        let result = try await sut.logIn(email: "user@example.com", password: "password123")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == false)
        guard case .login = http.lastSendEndpoint else {
            Issue.record("Expected .login endpoint"); return
        }
        #expect(result.account.id == "7")
    }

    @Test("logIn failure: propagates unauthorized error")
    func logInFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.logIn(email: "user@example.com", password: "wrong")
        }
    }

    // MARK: - logOut

    @Test("logOut success: calls send with logout endpoint POST with auth")
    func logOutSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.logOut(fcmToken: "fcm-token-123", accountId: nil)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .logout = http.lastSendEndpoint else {
            Issue.record("Expected .logout endpoint"); return
        }
    }

    @Test("logOut with accountId: forwards accountId to http client")
    func logOutWithAccountId() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.logOut(fcmToken: nil, accountId: "account-99")

        #expect(http.lastSendAccountId == "account-99")
    }

    @Test("logOut failure: propagates error")
    func logOutFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.logOut(fcmToken: nil, accountId: nil)
        }
    }

    // MARK: - fetchAccount

    @Test("fetchAccount success: calls get with accountInfo endpoint with auth, returns AccountDTO")
    func fetchAccountSuccess() async throws {
        let (sut, http) = makeSUT()
        http.getResult = AccountTestFixtures.makeAccountDTO(id: "55", email: "fetch@example.com")

        let result = try await sut.fetchAccount(accountId: nil)

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .accountInfo = http.lastGetEndpoint else {
            Issue.record("Expected .accountInfo endpoint"); return
        }
        #expect(result.id == "55")
        #expect(result.email == "fetch@example.com")
    }

    @Test("fetchAccount with accountId: forwards accountId to http client")
    func fetchAccountWithAccountId() async throws {
        let (sut, http) = makeSUT()
        http.getResult = AccountTestFixtures.makeAccountDTO()

        _ = try await sut.fetchAccount(accountId: "specific-id")

        #expect(http.lastGetAccountId == "specific-id")
    }

    @Test("fetchAccount failure: propagates error")
    func fetchAccountFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.fetchAccount(accountId: nil)
        }
    }

    // MARK: - editAccount

    @Test("editAccount success: calls send with updateAccount endpoint PUT with auth, returns AccountResponse")
    func editAccountSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse(accountId: "100")

        let account = AccountTestFixtures.makeAccountModel(id: "100")
        let result = try await sut.editAccount(account)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .put)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateAccount = http.lastSendEndpoint else {
            Issue.record("Expected .updateAccount endpoint"); return
        }
        #expect(result.account.id == "100")
    }

    // MARK: - deleteAccount

    @Test("deleteAccount success: calls send with deleteAccount endpoint DELETE with auth")
    func deleteAccountSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.deleteAccount(accountId: "to-delete")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .deleteAccount = http.lastSendEndpoint else {
            Issue.record("Expected .deleteAccount endpoint"); return
        }
    }

    @Test("deleteAccount failure: propagates error")
    func deleteAccountFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.deleteAccount(accountId: "id")
        }
    }

    // MARK: - createGoal

    @Test("createGoal success: calls send with setGoal endpoint POST with auth, returns GoalResponse")
    func createGoalSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeGoalResponse()

        let result = try await sut.createGoal(AccountTestFixtures.makeGoal())

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .setGoal = http.lastSendEndpoint else {
            Issue.record("Expected .setGoal endpoint"); return
        }
        #expect(result.type == .lose)
    }

    // MARK: - patchProfile

    @Test("patchProfile success: calls send with updateProfile endpoint PATCH with auth")
    func patchProfileSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchProfile(AccountTestFixtures.makeProfile())

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateProfile = http.lastSendEndpoint else {
            Issue.record("Expected .updateProfile endpoint"); return
        }
    }

    @Test("patchProfile failure: propagates error")
    func patchProfileFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.patchProfile(AccountTestFixtures.makeProfile())
        }
    }

    // MARK: - patchBodyComp

    @Test("patchBodyComp success: calls send with updateBodyComp endpoint PATCH with auth")
    func patchBodyCompSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchBodyComp(AccountTestFixtures.makeBodyComp())

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateBodyComp = http.lastSendEndpoint else {
            Issue.record("Expected .updateBodyComp endpoint"); return
        }
    }

    // MARK: - patchNotification

    @Test("patchNotification success: calls send with updateNotifications endpoint PATCH with auth")
    func patchNotificationSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchNotification(AccountTestFixtures.makeNotifications())

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateNotifications = http.lastSendEndpoint else {
            Issue.record("Expected .updateNotifications endpoint"); return
        }
    }

    // MARK: - patchDashboardType

    @Test("patchDashboardType success: calls send with updateDashboardType endpoint PATCH with auth")
    func patchDashboardTypeSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchDashboardType(.dashboard4)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateDashboardType = http.lastSendEndpoint else {
            Issue.record("Expected .updateDashboardType endpoint"); return
        }
    }

    // MARK: - patchDashboardMetrics

    @Test("patchDashboardMetrics success: calls send with updateDashboardMetrics endpoint PATCH with auth")
    func patchDashboardMetricsSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchDashboardMetrics(["weight", "bmi"])

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateDashboardMetrics = http.lastSendEndpoint else {
            Issue.record("Expected .updateDashboardMetrics endpoint"); return
        }
    }

    // MARK: - patchProgressMetrics

    @Test("patchProgressMetrics success: calls send with updateProgressMetrics endpoint PATCH with auth")
    func patchProgressMetricsSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchProgressMetrics(["goal", "weeklyChange"])

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateProgressMetrics = http.lastSendEndpoint else {
            Issue.record("Expected .updateProgressMetrics endpoint"); return
        }
    }

    // MARK: - patchStreak

    @Test("patchStreak success: calls send with updateStreak endpoint PATCH with auth")
    func patchStreakSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchStreak(true, "2026-03-01T08:00:00Z")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateStreak = http.lastSendEndpoint else {
            Issue.record("Expected .updateStreak endpoint"); return
        }
    }

    // MARK: - patchWeightless

    @Test("patchWeightless success: calls send with updateWeightless endpoint PATCH with auth")
    func patchWeightlessSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.patchWeightless(true, "2026-03-01T08:00:00Z", 150)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .updateWeightless = http.lastSendEndpoint else {
            Issue.record("Expected .updateWeightless endpoint"); return
        }
    }

    // MARK: - requestPasswordReset

    @Test("requestPasswordReset success: calls send with requestPasswordReset endpoint POST no-auth")
    func requestPasswordResetSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.requestPasswordReset(email: "reset@example.com")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == false)
        guard case .requestPasswordReset = http.lastSendEndpoint else {
            Issue.record("Expected .requestPasswordReset endpoint"); return
        }
    }

    @Test("requestPasswordReset failure: propagates error")
    func requestPasswordResetFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.requestPasswordReset(email: "unknown@example.com")
        }
    }

    // MARK: - updatePassword

    @Test("updatePassword success: calls send with changePassword endpoint PUT with auth, returns Tokens")
    func updatePasswordSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeTokens(access: "new-access", refresh: "new-refresh")

        let tokens = try await sut.updatePassword(oldPassword: "old123", newPassword: "new456")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .put)
        #expect(http.lastSendNeedsAuth == true)
        guard case .changePassword = http.lastSendEndpoint else {
            Issue.record("Expected .changePassword endpoint"); return
        }
        #expect(tokens.accessToken == "new-access")
        #expect(tokens.refreshToken == "new-refresh")
    }

    @Test("updatePassword failure: propagates error")
    func updatePasswordFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.updatePassword(oldPassword: "wrong", newPassword: "new456")
        }
    }

    // MARK: - refreshToken

    @Test("refreshToken success: calls send with refreshToken endpoint POST with auth, returns Tokens")
    func refreshTokenSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeTokens(access: "refreshed-access", refresh: "refreshed-refresh")

        let tokens = try await sut.refreshToken(refreshToken: "old-refresh-token", accountId: nil)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .refreshToken = http.lastSendEndpoint else {
            Issue.record("Expected .refreshToken endpoint"); return
        }
        #expect(tokens.accessToken == "refreshed-access")
    }

    @Test("refreshToken with accountId: forwards accountId to http client")
    func refreshTokenWithAccountId() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = AccountTestFixtures.makeTokens()

        _ = try await sut.refreshToken(refreshToken: "some-token", accountId: "account-77")

        #expect(http.lastSendAccountId == "account-77")
    }

    @Test("refreshToken failure: propagates error")
    func refreshTokenFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.refreshToken(refreshToken: "expired-token", accountId: nil)
        }
    }

    // MARK: - Error Propagation

    @Test("noInternet error: propagated from send")
    func noInternetPropagated() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.logIn(email: "user@example.com", password: "pw")
        }
    }

    @Test("apiError with message: propagated from server")
    func apiErrorPropagated() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.apiError(message: "Email already exists", code: 409)

        await #expect(throws: HTTPError.apiError(message: "Email already exists", code: 409)) {
            try await sut.createAccount(
                email: "existing@example.com",
                password: "pw",
                profile: AccountTestFixtures.makeProfile()
            )
        }
    }

    @Test("timeout error: propagated from get")
    func timeoutErrorPropagated() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.fetchAccount(accountId: nil)
        }
    }
}
