//
//  AccountServiceTests.swift
//  meAppTests
//
//  Tests for AccountService.signUp using injected mocks (no heavy init Tasks).
//

import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct AccountServiceTests {

    // MARK: - Helpers

    private func makeSUT() -> (AccountService, MockAccountAPIRepository, MockAccountRepository) {
        let apiRepo = MockAccountAPIRepository()
        let localRepo = MockAccountRepository()
        let logger = MockLoggerService()
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        let sut = AccountService(apiRepo: apiRepo, localRepo: localRepo)
        _ = sut.logger  // prime @Injector cache
        return (sut, apiRepo, localRepo)
    }

    private var validProfile: Profile { AccountTestFixtures.makeProfile() }

    // MARK: - signUp: success - new account

    @Test("signUp creates and saves a new account when not already local")
    func signUpSuccessNewAccount() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let response = AccountTestFixtures.makeAccountResponse(
            id: "new-id",
            email: "new@example.com",
            accessToken: "tok-new"
        )
        apiRepo.createAccountResult = response
        // localRepo has no existing account → fetchAccount returns nil

        let account = try await sut.signUp(
            email: "new@example.com",
            password: "pass123",
            profile: validProfile
        )

        #expect(account.accountId == "new-id")
        #expect(account.email == "new@example.com")
        #expect(account.accessToken == "tok-new")
        #expect(account.isLoggedIn == true)
        #expect(account.isActiveAccount == true)
        #expect(localRepo.saveAccountCallCount == 1)
        #expect(apiRepo.createAccountCallCount == 1)
    }

    @Test("signUp updates existing local account when same accountId already stored")
    func signUpUpdatesExistingAccount() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let existingAccount = AccountTestFixtures.makeAccount(id: "existing-id", email: "old@example.com")
        localRepo.seed(existingAccount)

        let response = AccountTestFixtures.makeAccountResponse(id: "existing-id", email: "old@example.com")
        apiRepo.createAccountResult = response

        let account = try await sut.signUp(
            email: "old@example.com",
            password: "pass",
            profile: validProfile
        )

        #expect(account.accountId == "existing-id")
        // update path: saveAccountCallCount stays 0, updateAccountCallCount is 1+
        #expect(localRepo.saveAccountCallCount == 0)
        #expect(localRepo.updateAccountCallCount >= 1)
    }

    @Test("signUp sets activeAccount after success")
    func signUpSetsActiveAccount() async throws {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.createAccountResult = AccountTestFixtures.makeAccountResponse(id: "act-id")

        _ = try await sut.signUp(
            email: "test@example.com",
            password: "pass",
            profile: validProfile
        )

        #expect(sut.activeAccount?.accountId == "act-id")
    }

    // MARK: - signUp: maxAccountsReached

    @Test("signUp throws maxAccountsReached when account limit is hit")
    func signUpThrowsMaxAccountsReached() async {
        let (sut, _, localRepo) = makeSUT()
        // Seed enough accounts to hit the limit
        for i in 0..<AppConstants.Account.maxAccounts {
            let acct = AccountTestFixtures.makeAccount(
                id: "acct-\(i)",
                email: "user\(i)@example.com"
            )
            localRepo.seed(acct)
        }

        var caught: Error?
        do {
            _ = try await sut.signUp(
                email: "newcomer@example.com",
                password: "pass",
                profile: validProfile
            )
        } catch {
            caught = error
        }

        guard let accountErr = caught as? AccountError,
              case .maxAccountsReached = accountErr else {
            Issue.record("Expected AccountError.maxAccountsReached but got \(String(describing: caught))")
            return
        }
        #expect(Bool(true)) // explicitly reached error case
    }

    @Test("signUp allows re-login when email already exists at limit")
    func signUpAllowsReLoginAtLimit() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let existingEmail = "existing@example.com"
        // Fill up to the max with one account matching the signUp email
        for i in 0..<AppConstants.Account.maxAccounts {
            let email = i == 0 ? existingEmail : "user\(i)@example.com"
            let acct = AccountTestFixtures.makeAccount(id: "acct-\(i)", email: email)
            localRepo.seed(acct)
        }
        apiRepo.createAccountResult = AccountTestFixtures.makeAccountResponse(
            id: "acct-0",
            email: existingEmail
        )

        // Should not throw since the email already belongs to a stored account
        let account = try await sut.signUp(
            email: existingEmail,
            password: "pass",
            profile: validProfile
        )
        #expect(account.email == existingEmail)
    }

    // MARK: - signUp: API failure

    @Test("signUp rethrows API error")
    func signUpRethrowsAPIError() async {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.createAccountError = HTTPError.serverError

        await #expect(throws: HTTPError.self) {
            _ = try await sut.signUp(
                email: "test@example.com",
                password: "pass",
                profile: validProfile
            )
        }
    }

    @Test("signUp rethrows noInternet error")
    func signUpRethrowsNoInternet() async {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.createAccountError = HTTPError.noInternet

        var caught: Error?
        do {
            _ = try await sut.signUp(email: "t@e.com", password: "p", profile: validProfile)
        } catch {
            caught = error
        }
        #expect(caught != nil)
    }

    @Test("signUp rethrows save failure from local repository")
    func signUpRethrowsSaveFailure() async {
        let (sut, apiRepo, localRepo) = makeSUT()
        apiRepo.createAccountResult = AccountTestFixtures.makeAccountResponse(id: "new-id")
        localRepo.saveAccountError = NSError(domain: "SwiftData", code: 1, userInfo: [NSLocalizedDescriptionKey: "Disk full"])

        await #expect(throws: Error.self) {
            _ = try await sut.signUp(
                email: "test@example.com",
                password: "pass",
                profile: validProfile
            )
        }
    }

    // MARK: - signUp: badRequest (email in use)

    @Test("signUp rethrows badRequest (email in use)")
    func signUpRethrowsBadRequest() async {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.createAccountError = HTTPError.badRequest

        var caught: Error?
        do {
            _ = try await sut.signUp(email: "used@example.com", password: "p", profile: validProfile)
        } catch {
            caught = error
        }
        guard let httpErr = caught as? HTTPError, case .badRequest = httpErr else {
            Issue.record("Expected HTTPError.badRequest")
            return
        }
        #expect(Bool(true))
    }

    // MARK: - createGoal

    @Test("createGoal throws noActiveAccount when no active account is set")
    func createGoalThrowsNoActiveAccount() async {
        let (sut, _, _) = makeSUT()
        // No activeAccount set
        let goal = AccountTestFixtures.makeGoal()

        await #expect(throws: AccountError.self) {
            _ = try await sut.createGoal(goal)
        }
    }

    @Test("createGoal succeeds when activeAccount exists in local repo")
    func createGoalSucceeds() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let account = AccountTestFixtures.makeAccount(id: "goal-account")
        localRepo.seed(account)
        sut.activeAccount = account
        apiRepo.createGoalResult = AccountTestFixtures.makeGoalResponse()

        let result = try await sut.createGoal(AccountTestFixtures.makeGoal())
        #expect(result.accountId == "goal-account")
        #expect(apiRepo.createGoalCallCount == 1)
    }

    // MARK: - logIn: success - new account

    @Test("logIn creates and saves a new account")
    func logInSuccessNewAccount() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let response = AccountTestFixtures.makeAccountResponse(
            id: "login-id",
            email: "login@example.com",
            accessToken: "tok-login"
        )
        apiRepo.logInResult = response

        let account = try await sut.logIn(
            email: "login@example.com",
            password: "pass123"
        )

        #expect(account.accountId == "login-id")
        #expect(account.email == "login@example.com")
        #expect(account.accessToken == "tok-login")
        #expect(account.isLoggedIn == true)
        #expect(account.isActiveAccount == true)
        #expect(localRepo.saveAccountCallCount == 1)
        #expect(apiRepo.logInCallCount == 1)
    }

    @Test("logIn updates existing local account")
    func logInUpdatesExistingAccount() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let existing = AccountTestFixtures.makeAccount(id: "existing-id", email: "login@example.com")
        localRepo.seed(existing)
        apiRepo.logInResult = AccountTestFixtures.makeAccountResponse(
            id: "existing-id",
            email: "login@example.com"
        )

        let account = try await sut.logIn(email: "login@example.com", password: "pass")

        #expect(account.accountId == "existing-id")
        #expect(localRepo.saveAccountCallCount == 0)
        #expect(localRepo.updateAccountCallCount >= 1)
    }

    @Test("logIn sets activeAccount after success")
    func logInSetsActiveAccount() async throws {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.logInResult = AccountTestFixtures.makeAccountResponse(id: "active-login-id")

        _ = try await sut.logIn(email: "test@example.com", password: "pass")

        #expect(sut.activeAccount?.accountId == "active-login-id")
    }

    // MARK: - logIn: maxAccountsReached

    @Test("logIn throws maxAccountsReached when account limit is hit")
    func logInThrowsMaxAccountsReached() async {
        let (sut, _, localRepo) = makeSUT()
        for i in 0..<AppConstants.Account.maxAccounts {
            let acct = AccountTestFixtures.makeAccount(
                id: "acct-\(i)",
                email: "user\(i)@example.com"
            )
            localRepo.seed(acct)
        }

        var caught: Error?
        do {
            _ = try await sut.logIn(email: "newcomer@example.com", password: "pass")
        } catch {
            caught = error
        }

        guard let accountErr = caught as? AccountError,
              case .maxAccountsReached = accountErr else {
            Issue.record("Expected AccountError.maxAccountsReached but got \(String(describing: caught))")
            return
        }
        #expect(Bool(true))
    }

    @Test("logIn allows existing email even at account limit")
    func logInAllowsExistingEmailAtLimit() async throws {
        let (sut, apiRepo, localRepo) = makeSUT()
        let existingEmail = "existing@example.com"
        for i in 0..<AppConstants.Account.maxAccounts {
            let email = i == 0 ? existingEmail : "user\(i)@example.com"
            localRepo.seed(AccountTestFixtures.makeAccount(id: "acct-\(i)", email: email))
        }
        apiRepo.logInResult = AccountTestFixtures.makeAccountResponse(
            id: "acct-0",
            email: existingEmail
        )

        let account = try await sut.logIn(email: existingEmail, password: "pass")
        #expect(account.email == existingEmail)
    }

    // MARK: - logIn: API failures

    @Test("logIn rethrows unauthorized error")
    func logInRethrowsUnauthorized() async {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.logInError = HTTPError.unauthorized

        await #expect(throws: HTTPError.self) {
            _ = try await sut.logIn(email: "test@example.com", password: "wrong")
        }
    }

    @Test("logIn rethrows noInternet error")
    func logInRethrowsNoInternet() async {
        let (sut, apiRepo, _) = makeSUT()
        apiRepo.logInError = HTTPError.noInternet

        var caught: Error?
        do { _ = try await sut.logIn(email: "t@e.com", password: "p") } catch { caught = error }
        #expect(caught != nil)
    }

    @Test("logIn rethrows save failure")
    func logInRethrowsSaveFailure() async {
        let (sut, apiRepo, localRepo) = makeSUT()
        apiRepo.logInResult = AccountTestFixtures.makeAccountResponse(id: "save-fail-id")
        localRepo.saveAccountError = NSError(domain: "SwiftData", code: 1)

        await #expect(throws: Error.self) {
            _ = try await sut.logIn(email: "test@example.com", password: "pass")
        }
    }
}
