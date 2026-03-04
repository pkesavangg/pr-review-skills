import Foundation
import Testing
@testable import meApp
@Suite(.serialized)
@MainActor
struct AccountServiceTests {
    // MARK: - Already Covered + Auth

    @Test("signup success: calls API, saves account, returns mapped account")
    func signUpSuccess() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let logger = MockLoggerService()
        let keychain = MockKeychainService()
        let sut = makeSUT(api: api, local: local, logger: logger, keychain: keychain)

        api.createAccountResult = .success(
            AccountTestFixtures.makeAccountResponse(accountId: "202", email: "new@example.com", firstName: "New")
        )

        let account = try await sut.signUp(
            email: "new@example.com",
            password: "secret",
            profile: AccountTestFixtures.makeProfile(email: "new@example.com", firstName: "New")
        )

        #expect(api.createAccountCalls == 1)
        #expect(local.saveAccountCalls == 1)
        #expect(account.accountId == "202")
        #expect(account.firstName == "New")
        #expect(account.isLoggedIn == true)
        #expect(account.isActiveAccount == true)
    }

    @Test("signup API failure: throws and does not save")
    func signUpFailure() async {
        let api = MockAccountAPIRepository()
        api.createAccountResult = .failure(AccountTestError.apiFailed)
        let local = MockAccountRepository()

        let sut = makeSUT(api: api, local: local)

        do {
            _ = try await sut.signUp(
                email: "new@example.com",
                password: "secret",
                profile: AccountTestFixtures.makeProfile(email: "new@example.com")
            )
            Issue.record("Expected signUp to throw")
        } catch {
            #expect(error as? AccountTestError == .apiFailed)
        }

        #expect(local.saveAccountCalls == 0)
    }

    @Test("signup save failure: throws after API success")
    func signUpSaveFailure() async {
        let api = MockAccountAPIRepository()
        api.createAccountResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "202", email: "new@example.com"))

        let local = MockAccountRepository()
        local.saveAccountError = AccountTestError.persistenceFailed

        let sut = makeSUT(api: api, local: local)

        do {
            _ = try await sut.signUp(
                email: "new@example.com",
                password: "secret",
                profile: AccountTestFixtures.makeProfile(email: "new@example.com")
            )
            Issue.record("Expected signUp to throw for save failure")
        } catch {
            #expect(error as? AccountTestError == .persistenceFailed)
        }

        #expect(api.createAccountCalls == 1)
        #expect(local.saveAccountCalls == 1)
    }

    @Test("login success: calls API, persists account, refreshes account")
    func loginSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.logInResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))
        api.fetchAccountResult = .success(AccountTestFixtures.makeAccountDTO(id: "101", email: "user@example.com", firstName: "Updated"))

        let local = MockAccountRepository()
        let sut = makeSUT(api: api, local: local)

        let account = try await sut.logIn(email: "user@example.com", password: "secret")

        #expect(api.logInCalls == 1)
        #expect(api.fetchAccountCalls == 1)
        #expect(local.saveAccountCalls == 1)
        #expect(account.accountId == "101")
    }

    @Test("login API failure: throws and does not save")
    func loginFailure() async {
        let api = MockAccountAPIRepository()
        api.logInResult = .failure(AccountTestError.apiFailed)

        let local = MockAccountRepository()
        let sut = makeSUT(api: api, local: local)

        do {
            _ = try await sut.logIn(email: "user@example.com", password: "secret")
            Issue.record("Expected logIn to throw")
        } catch {
            #expect(error as? AccountTestError == .apiFailed)
        }

        #expect(local.saveAccountCalls == 0)
    }

    @Test("login save failure: throws after API success")
    func loginSaveFailure() async {
        let api = MockAccountAPIRepository()
        api.logInResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))

        let local = MockAccountRepository()
        local.saveAccountError = AccountTestError.persistenceFailed

        let sut = makeSUT(api: api, local: local)

        do {
            _ = try await sut.logIn(email: "user@example.com", password: "secret")
            Issue.record("Expected logIn to throw for save failure")
        } catch {
            #expect(error as? AccountTestError == .persistenceFailed)
        }

        #expect(api.logInCalls == 1)
        #expect(local.saveAccountCalls == 1)
    }

    @Test("login refresh failure: throws after local save")
    func loginRefreshFailure() async {
        let api = MockAccountAPIRepository()
        api.logInResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))
        api.fetchAccountResult = .failure(AccountTestError.apiFailed)

        let local = MockAccountRepository()
        let sut = makeSUT(api: api, local: local)

        do {
            _ = try await sut.logIn(email: "user@example.com", password: "secret")
            Issue.record("Expected logIn to throw for refresh failure")
        } catch {
            #expect(error as? AccountTestError == .apiFailed)
        }

        #expect(local.saveAccountCalls == 1)
        #expect(api.fetchAccountCalls == 1)
    }

    @Test("signup maxAccountsReached: throws when at limit and email is new")
    func signUpMaxAccountsReached() async {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let accounts = (1...AppConstants.Account.maxAccounts).map {
            AccountTestFixtures.makeAccountModel(id: "id-\($0)", email: "u\($0)@example.com", isLoggedIn: true, isActive: $0 == 1)
        }
        local.seed(accounts)

        let sut = makeSUT(api: api, local: local)

        do {
            _ = try await sut.signUp(email: "new-user@example.com", password: "secret", profile: AccountTestFixtures.makeProfile(email: "new-user@example.com"))
            Issue.record("Expected maxAccountsReached")
        } catch {
            assertMaxAccountsReached(error)
        }

        #expect(api.createAccountCalls == 0)
    }

    // MARK: - Logout / Deletion

    @Test("logOut noActiveAccount: throws")
    func logOutNoActiveAccount() async {
        let sut = makeSUT()

        do {
            try await sut.logOut()
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("logOut accountNotFound: throws")
    func logOutAccountNotFound() async {
        let sut = makeSUT()

        do {
            try await sut.logOut(accountId: "missing")
            Issue.record("Expected accountNotFound")
        } catch {
            assertAccountNotFound(error, id: "missing")
        }
    }

    @Test("logOut success: calls API and updates local")
    func logOutSuccess() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let keychain = MockKeychainService()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isLoggedIn: true, isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local, keychain: keychain)
        sut.activeAccount = account

        try await sut.logOut(accountId: "101")

        guard let updated = try await local.fetchAccount(byId: "101") else {
            Issue.record("Expected account to exist after logout")
            return
        }
        #expect(api.logOutCalls == 1)
        #expect(updated.isLoggedIn == false)
        #expect(updated.isActiveAccount == false)
    }

    @Test("deleteAccount noActiveAccount: throws")
    func deleteAccountNoActiveAccount() async {
        let sut = makeSUT()

        do {
            try await sut.deleteAccount()
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("deleteAccount success: deletes via API and local")
    func deleteAccountSuccess() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let keychain = MockKeychainService()

        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isLoggedIn: true, isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local, keychain: keychain)
        sut.activeAccount = account

        try await sut.deleteAccount()

        #expect(api.deleteAccountCalls == 1)
        #expect(api.lastDeleteAccountId == "101")
        #expect(local.containsAccount(id: "101") == false)
        #expect(keychain.deleteTokensCalls == 1)
        #expect(keychain.deleteFCMTokenCalls == 1)
    }

    @Test("deleteAllAccounts success: clears local and keychain")
    func deleteAllAccountsSuccess() async throws {
        let local = MockAccountRepository()
        let keychain = MockKeychainService()
        let a = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true)
        let b = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true)
        local.seed([a, b])

        let sut = makeSUT(local: local, keychain: keychain)

        try await sut.deleteAllAccounts()

        #expect(local.all().isEmpty)
        #expect(local.deleteAllAccountsCalls == 1)
        #expect(keychain.deleteTokensCalls == 2)
        #expect(keychain.deleteFCMTokenCalls == 2)
    }

    @Test("logOutAllAccounts success: logs out all accounts and clears active")
    func logOutAllAccountsSuccess() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let a = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true, isActive: false)
        let b = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true, isActive: true)
        let c = AccountTestFixtures.makeAccountModel(id: "103", email: "c@example.com", isLoggedIn: true, isActive: false)
        local.seed([a, b, c])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = b

        try await sut.logOutAllAccounts()

        #expect(api.logOutCalls == 3)
        #expect(local.all().allSatisfy { $0.isActiveAccount == false })
        #expect(sut.activeAccount == nil)
    }

    @Test("logOutAllAccounts continues when API logout fails")
    func logOutAllAccountsApiFailureStillCompletes() async throws {
        let api = MockAccountAPIRepository()
        api.logOutResult = .failure(AccountTestError.apiFailed)
        let local = MockAccountRepository()
        let a = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true, isActive: true)
        let b = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true, isActive: false)
        local.seed([a, b])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = a

        try await sut.logOutAllAccounts()

        #expect(api.logOutCalls == 2)
        #expect(local.all().allSatisfy { $0.isActiveAccount == false })
    }

    // MARK: - Account State

    @Test("getActiveAccount returns current active")
    func getActiveAccountReturnsCurrent() async throws {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isLoggedIn: true, isActive: true)
        sut.activeAccount = account

        let active = try await sut.getActiveAccount()
        #expect(active?.accountId == "101")
    }

    @Test("getAllLoggedInAccounts returns only logged-in accounts")
    func getAllLoggedInAccountsFilters() async throws {
        let local = MockAccountRepository()
        local.seed([
            AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true),
            AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: false)
        ])
        let sut = makeSUT(local: local)

        let result = try await sut.getAllLoggedInAccounts()
        #expect(result.count == 1)
        #expect(result.first?.accountId == "101")
    }

    @Test("fetchAccount byId returns account and hydrates tokens")
    func fetchAccountHydratesTokens() async throws {
        let local = MockAccountRepository()
        let keychain = MockKeychainService()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com")
        local.seed([account])
        keychain.setTokens(AccountTestFixtures.makeTokens(access: "a1", refresh: "r1", expiresAt: "e1"), for: "101")

        let sut = makeSUT(local: local, keychain: keychain)

        let result = try await sut.fetchAccount(byId: "101")

        #expect(result?.accountId == "101")
        #expect(result?.accessToken == "a1")
        #expect(result?.refreshToken == "r1")
        #expect(result?.expiresAt == "e1")
    }

    @Test("fetchAccount byId returns nil when missing")
    func fetchAccountMissingReturnsNil() async throws {
        let sut = makeSUT()
        let result = try await sut.fetchAccount(byId: "missing")
        #expect(result == nil)
    }

    @Test("fetchAllAccounts returns all and hydrates tokens")
    func fetchAllAccountsHydratesTokens() async throws {
        let local = MockAccountRepository()
        let keychain = MockKeychainService()

        let a = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com")
        let b = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com")
        local.seed([a, b])

        keychain.setTokens(AccountTestFixtures.makeTokens(access: "a101", refresh: "r101", expiresAt: "e101"), for: "101")
        keychain.setTokens(AccountTestFixtures.makeTokens(access: "a102", refresh: "r102", expiresAt: "e102"), for: "102")

        let sut = makeSUT(local: local, keychain: keychain)
        let result = try await sut.fetchAllAccounts()

        #expect(result.count == 2)
        #expect(result.contains(where: { $0.accountId == "101" && $0.accessToken == "a101" }))
        #expect(result.contains(where: { $0.accountId == "102" && $0.accessToken == "a102" }))
    }

    // MARK: - Active Account Switching

    @Test("switchAccount success: refreshes target and sets active")
    func switchAccountSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .success(AccountTestFixtures.makeAccountDTO(id: "102", email: "b@example.com", firstName: "B"))
        let local = MockAccountRepository()
        let bluetooth = MockBluetoothService()
        let network = MockNetworkMonitor(isConnected: true)

        let active = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true, isActive: true)
        let target = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true, isActive: false)
        local.seed([active, target])

        let sut = makeSUT(api: api, local: local, bluetooth: bluetooth, networkMonitor: network)
        sut.activeAccount = active

        try await sut.switchAccount(to: target)

        #expect(api.fetchAccountCalls == 1)
        #expect(bluetooth.disconnectConnectedScalesCalls == 1)
        #expect(sut.activeAccount?.accountId == "102")
    }

    @Test("switchAccount noInternet: throws")
    func switchAccountNoInternet() async {
        let local = MockAccountRepository()
        let network = MockNetworkMonitor(isConnected: false)
        let active = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true, isActive: true)
        let target = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true, isActive: false)
        local.seed([active, target])

        let sut = makeSUT(local: local, networkMonitor: network)
        sut.activeAccount = active

        do {
            try await sut.switchAccount(to: target)
            Issue.record("Expected HTTPError.noInternet")
        } catch {
            guard case HTTPError.noInternet = error else {
                Issue.record("Expected HTTPError.noInternet, got: \(error)")
                return
            }
        }
    }

    @Test("setActiveAccount success: makes others inactive")
    func setActiveAccountSuccess() async throws {
        let local = MockAccountRepository()
        let a = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true, isActive: true)
        let b = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true, isActive: false)
        local.seed([a, b])

        let sut = makeSUT(local: local)

        try await sut.setActiveAccount(b)

        let all = local.all()
        let updatedA = try #require(all.first(where: { $0.accountId == "101" }))
        let updatedB = try #require(all.first(where: { $0.accountId == "102" }))
        #expect(updatedA.isActiveAccount == false)
        #expect(updatedB.isActiveAccount == true)
        #expect(sut.activeAccount?.accountId == "102")
    }

    // MARK: - Goal / Profile

    @Test("createGoal noActiveAccount: throws")
    func createGoalNoActiveAccount() async {
        let sut = makeSUT()
        do {
            _ = try await sut.createGoal(AccountTestFixtures.makeGoal())
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("createGoal API success: updates local")
    func createGoalSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.createGoalResult = .success(AccountTestFixtures.makeGoalResponse(type: .lose, goalWeight: 145, initialWeight: 180))

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.createGoal(AccountTestFixtures.makeGoal(type: .lose, goalWeight: 145, initialWeight: 180))

        #expect(api.createGoalCalls == 1)
        #expect(result.goalSettings?.goalWeight == 145.0)
    }

    @Test("createGoal network error: saves offline")
    func createGoalNetworkError() async throws {
        let api = MockAccountAPIRepository()
        api.createGoalResult = .failure(HTTPError.noInternet)

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.createGoal(AccountTestFixtures.makeGoal(type: .gain, goalWeight: 190, initialWeight: 180))

        #expect(result.goalSettings?.goalType == .gain)
        #expect(result.goalSettings?.goalWeight == 190.0)
        #expect(result.goalSettings?.isSynced == false)
    }

    @Test("updateProfile noActiveAccount: throws")
    func updateProfileNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.updateProfile(AccountTestFixtures.makeProfile())
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("updateProfile API success: updates local")
    func updateProfileSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchProfileResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "new@example.com", firstName: "ProfileNew"))

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "old@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateProfile(AccountTestFixtures.makeProfile(email: "new@example.com", firstName: "ProfileNew"))

        #expect(api.patchProfileCalls == 1)
        #expect(result.firstName == "ProfileNew")
        #expect(result.isSynced == true)
    }

    @Test("updateProfile network error + canSaveOffline: saves offline")
    func updateProfileOfflineSave() async throws {
        let api = MockAccountAPIRepository()
        api.patchProfileResult = .failure(HTTPError.noInternet)

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "old@example.com", firstName: "Old", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateProfile(
            AccountTestFixtures.makeProfile(email: "new@example.com", firstName: "Offline"),
            canSaveOffline: true
        )

        #expect(result.firstName == "Offline")
        #expect(result.isSynced == false)
    }

    @Test("updateBodyComp API success: updates local")
    func updateBodyCompSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchBodyCompResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateBodyComp(AccountTestFixtures.makeBodyComp(weightUnit: .lb, height: 180, activityLevel: .athlete))

        #expect(api.patchBodyCompCalls == 1)
        #expect(result.isSynced == true)
    }

    @Test("updateBodyComp network error: saves offline")
    func updateBodyCompOfflineSave() async throws {
        let api = MockAccountAPIRepository()
        api.patchBodyCompResult = .failure(HTTPError.noInternet)

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateBodyComp(AccountTestFixtures.makeBodyComp(weightUnit: .lb, height: 181, activityLevel: .athlete))

        #expect(result.weightSettings?.weightUnit == .lb)
        #expect(result.isSynced == false)
    }

    // MARK: - Tokens

    @Test("updateTokens success: keychain and local updated")
    func updateTokensSuccess() async throws {
        let local = MockAccountRepository()
        let keychain = MockKeychainService()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(local: local, keychain: keychain)
        sut.activeAccount = account

        try await sut.updateTokens(AccountTestFixtures.makeTokens(access: "newA", refresh: "newR", expiresAt: "newE"))

        #expect(keychain.setTokensCalls == 1)
        let stored = keychain.getTokens(for: "101")
        #expect(stored?.accessToken == "newA")
        #expect(stored?.refreshToken == "newR")
    }

    @Test("updateTokens noActiveAccount: throws")
    func updateTokensNoActiveAccount() async {
        let sut = makeSUT()

        do {
            try await sut.updateTokens(AccountTestFixtures.makeTokens())
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("updateTokens accountNotFound: throws")
    func updateTokensAccountNotFound() async {
        let sut = makeSUT()
        sut.activeAccount = AccountTestFixtures.makeAccountModel(id: "missing", email: "x@example.com", isActive: true)

        do {
            try await sut.updateTokens(AccountTestFixtures.makeTokens())
            Issue.record("Expected accountNotFound")
        } catch {
            assertAccountNotFound(error, id: "missing")
        }
    }

    @Test("refreshTokens success from keychain refresh token")
    func refreshTokensSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.refreshTokenResult = .success(AccountTestFixtures.makeTokens(access: "freshA", refresh: "freshR", expiresAt: "freshE"))

        let local = MockAccountRepository()
        let keychain = MockKeychainService()

        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])
        keychain.setTokens(AccountTestFixtures.makeTokens(access: "oldA", refresh: "refresh-key", expiresAt: "oldE"), for: "101")

        let sut = makeSUT(api: api, local: local, keychain: keychain)
        sut.activeAccount = account

        let tokens = try await sut.refreshTokens()

        #expect(api.refreshTokenCalls == 1)
        #expect(api.lastRefreshToken == "refresh-key")
        #expect(tokens.accessToken == "freshA")
    }

    @Test("refreshTokens noActiveAccount: throws")
    func refreshTokensNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.refreshTokens()
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("refreshTokens missing refresh token: throws") 
    func refreshTokensMissingRefreshToken() async {
        let sut = makeSUT()
        sut.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)

        do {
            _ = try await sut.refreshTokens()
            Issue.record("Expected missing refresh token to throw")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("refreshTokens with explicit accountId: success")
    func refreshTokensExplicitAccountIdSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.refreshTokenResult = .success(AccountTestFixtures.makeTokens(access: "freshA", refresh: "freshR", expiresAt: "freshE"))
        let local = MockAccountRepository()
        let keychain = MockKeychainService()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: false)
        local.seed([account])
        keychain.setTokens(AccountTestFixtures.makeTokens(access: "oldA", refresh: "refresh-explicit", expiresAt: "oldE"), for: "101")

        let sut = makeSUT(api: api, local: local, keychain: keychain)

        let tokens = try await sut.refreshTokens(accountId: "101")

        #expect(api.refreshTokenCalls == 1)
        #expect(api.lastRefreshToken == "refresh-explicit")
        #expect(tokens.accessToken == "freshA")
    }

    @Test("getActiveTokens success from keychain")
    func getActiveTokensFromKeychain() async throws {
        let keychain = MockKeychainService()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        keychain.setTokens(AccountTestFixtures.makeTokens(access: "a", refresh: "r", expiresAt: "e"), for: "101")

        let sut = makeSUT(keychain: keychain)
        sut.activeAccount = account

        let tokens = try await sut.getActiveTokens()
        #expect(tokens.accessToken == "a")
        #expect(tokens.refreshToken == "r")
    }

    @Test("getActiveTokens fallback from account when keychain empty")
    func getActiveTokensFallbackFromAccount() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        account.accessToken = "fallbackA"
        account.refreshToken = "fallbackR"
        account.expiresAt = "fallbackE"

        let sut = makeSUT()
        sut.activeAccount = account

        let tokens = try await sut.getActiveTokens()
        #expect(tokens.accessToken == "fallbackA")
        #expect(tokens.refreshToken == "fallbackR")
    }

    @Test("getActiveTokens noActiveAccount: throws")
    func getActiveTokensNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.getActiveTokens()
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    // MARK: - Refresh

    @Test("refreshAccount noActiveAccount: throws")
    func refreshAccountNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.refreshAccount()
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("refreshAccount accountNotFound: throws")
    func refreshAccountAccountNotFound() async {
        let sut = makeSUT()
        sut.activeAccount = AccountTestFixtures.makeAccountModel(id: "missing", email: "x@example.com", isActive: true)

        do {
            _ = try await sut.refreshAccount()
            Issue.record("Expected accountNotFound")
        } catch {
            assertAccountNotFound(error, id: "missing")
        }
    }

    @Test("refreshAccount API success: updates local")
    func refreshAccountSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .success(AccountTestFixtures.makeAccountDTO(id: "101", email: "fresh@example.com", firstName: "Fresh"))

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "old@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let refreshed = try await sut.refreshAccount()

        #expect(api.fetchAccountCalls == 1)
        #expect(refreshed.email == "fresh@example.com")
        #expect(refreshed.isSynced == true)
    }

    @Test("refreshAccount network error: returns local without throw")
    func refreshAccountNetworkErrorReturnsLocal() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .failure(HTTPError.noInternet)

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "local@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let refreshed = try await sut.refreshAccount()

        #expect(refreshed.email == "local@example.com")
        #expect(api.fetchAccountCalls == 1)
    }

    @Test("refreshAllAccounts success: completes and updates state")
    func refreshAllAccountsSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .success(AccountTestFixtures.makeAccountDTO(id: "101", email: "fresh@example.com", firstName: "Fresh"))

        let local = MockAccountRepository()
        local.seed([AccountTestFixtures.makeAccountModel(id: "101", email: "old@example.com", isActive: true)])

        let sut = makeSUT(api: api, local: local)

        try await sut.refreshAllAccounts()

        #expect(api.fetchAccountCalls == 1)
        #expect(sut.allAccounts.count == 1)
    }

    @Test("refreshAllAccounts network error: continues without throw")
    func refreshAllAccountsNetworkErrorContinues() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .failure(HTTPError.noInternet)
        let local = MockAccountRepository()
        local.seed([
            AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: true),
            AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isActive: false)
        ])
        let sut = makeSUT(api: api, local: local)

        try await sut.refreshAllAccounts()

        #expect(api.fetchAccountCalls == 2)
        #expect(local.all().allSatisfy { $0.isExpired != true })
    }

    @Test("refreshAllAccounts non-network error: marks accounts expired")
    func refreshAllAccountsNonNetworkErrorMarksExpired() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .failure(AccountTestError.apiFailed)
        let local = MockAccountRepository()
        local.seed([
            AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: true),
            AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isActive: false)
        ])
        let sut = makeSUT(api: api, local: local)

        try await sut.refreshAllAccounts()

        #expect(api.fetchAccountCalls == 2)
        #expect(local.all().allSatisfy { $0.isExpired == true })
    }

    // MARK: - Sync

    @Test("syncUnsyncedAccounts no connectivity: returns early")
    func syncUnsyncedAccountsNoConnectivity() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let network = MockNetworkMonitor(isConnected: false)
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: true, isSynced: false)
        local.seed([account])

        let sut = makeSUT(api: api, local: local, networkMonitor: network)
        try await sut.syncUnsyncedAccounts()

        #expect(api.patchProfileCalls == 0)
        #expect(api.patchBodyCompCalls == 0)
        #expect(api.patchNotificationCalls == 0)
    }

    @Test("syncUnsyncedAccounts no active local account: returns early")
    func syncUnsyncedAccountsNoActiveLocalAccount() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let network = MockNetworkMonitor(isConnected: true)
        local.seed([AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: false, isSynced: false)])

        let sut = makeSUT(api: api, local: local, networkMonitor: network)
        try await sut.syncUnsyncedAccounts()

        #expect(api.patchProfileCalls == 0)
    }

    @Test("syncUnsyncedAccounts healthKit on: calls integration create and marks synced")
    func syncUnsyncedAccountsHealthKitOn() async throws {
        let api = MockAccountAPIRepository()
        let local = MockAccountRepository()
        let network = MockNetworkMonitor(isConnected: true)
        let integration = MockIntegrationAPIRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: true, isSynced: false)
        account.streaksSettings?.isStreakOn = true
        account.streaksSettings?.streakTimestamp = "2026-01-01T00:00:00Z"
        account.weightlessSettings?.isWeightlessOn = true
        account.weightlessSettings?.weightlessTimestamp = "2026-01-01T00:00:00Z"
        account.weightlessSettings?.weightlessWeight = 150
        account.integrationSettings?.isHealthKitOn = true
        local.seed([account])

        let sut = makeSUT(api: api, local: local, integration: integration, networkMonitor: network)
        try await sut.syncUnsyncedAccounts()

        #expect(integration.createHealthIntegrationCalls == 1)
        #expect(account.isSynced == true)
    }

    @Test("syncUnsyncedAccounts healthKit off: calls integration delete and marks synced")
    func syncUnsyncedAccountsHealthKitOff() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .success(AccountTestFixtures.makeAccountDTO(id: "101", email: "a@example.com"))
        let local = MockAccountRepository()
        let network = MockNetworkMonitor(isConnected: true)
        let integration = MockIntegrationAPIRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: true, isSynced: false)
        account.integrationSettings?.isHealthKitOn = false
        local.seed([account])

        let sut = makeSUT(api: api, local: local, integration: integration, networkMonitor: network)
        try await sut.syncUnsyncedAccounts()

        #expect(integration.deleteHealthIntegrationCalls == 1)
        #expect(account.isSynced == true)
    }

    @Test("syncUnsyncedAccounts non-network API error: throws")
    func syncUnsyncedAccountsNonNetworkErrorThrows() async {
        let api = MockAccountAPIRepository()
        api.patchProfileResult = .failure(AccountTestError.apiFailed)
        let local = MockAccountRepository()
        let network = MockNetworkMonitor(isConnected: true)
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isActive: true, isSynced: false)
        local.seed([account])

        let sut = makeSUT(api: api, local: local, networkMonitor: network)
        do {
            try await sut.syncUnsyncedAccounts()
            Issue.record("Expected syncUnsyncedAccounts to throw non-network errors")
        } catch {
            #expect(error as? AccountTestError == .apiFailed)
        }
    }

    // MARK: - Password and API passthrough

    @Test("requestPasswordReset success: calls API")
    func requestPasswordResetSuccess() async throws {
        let api = MockAccountAPIRepository()
        let sut = makeSUT(api: api)

        try await sut.requestPasswordReset(email: "reset@example.com")

        #expect(api.requestPasswordResetCalls == 1)
        #expect(api.lastRequestPasswordResetEmail == "reset@example.com")
    }

    @Test("requestPasswordReset API failure: throws")
    func requestPasswordResetFailure() async {
        let api = MockAccountAPIRepository()
        api.requestPasswordResetResult = .failure(AccountTestError.apiFailed)
        let sut = makeSUT(api: api)

        do {
            try await sut.requestPasswordReset(email: "reset@example.com")
            Issue.record("Expected requestPasswordReset to throw")
        } catch {
            #expect(error as? AccountTestError == .apiFailed)
        }
    }

    @Test("updatePassword success: updates tokens")
    func updatePasswordSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.updatePasswordResult = .success(AccountTestFixtures.makeTokens(access: "newA", refresh: "newR", expiresAt: "newE"))

        let local = MockAccountRepository()
        let keychain = MockKeychainService()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local, keychain: keychain)
        sut.activeAccount = account

        try await sut.updatePassword(oldPassword: "old", newPassword: "new")

        #expect(api.updatePasswordCalls == 1)
        #expect(keychain.setTokensCalls == 1)
    }

    @Test("updatePassword API failure: throws")
    func updatePasswordFailure() async {
        let api = MockAccountAPIRepository()
        api.updatePasswordResult = .failure(AccountTestError.apiFailed)

        let sut = makeSUT(api: api)

        do {
            try await sut.updatePassword(oldPassword: "old", newPassword: "new")
            Issue.record("Expected updatePassword to throw")
        } catch {
            #expect(error as? AccountTestError == .apiFailed)
        }
    }

    // MARK: - Dashboard / Notifications / Integrations

    @Test("updateDashboardType noActiveAccount: throws")
    func updateDashboardTypeNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.updateDashboardType(type: .dashboard12)
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("updateDashboardType success")
    func updateDashboardTypeSuccess() async throws {
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(local: local)
        sut.activeAccount = account

        let result = try await sut.updateDashboardType(type: .dashboard12)

        #expect(result.dashboardSettings?.dashboardType == DashboardType.dashboard12.rawValue)
        #expect(result.isSynced == true)
    }

    @Test("updateDashboardType local update failure: throws")
    func updateDashboardTypeLocalUpdateFailure() async {
        let local = MockAccountRepository()
        local.updateAccountError = HTTPError.noInternet
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(local: local)
        sut.activeAccount = account

        do {
            _ = try await sut.updateDashboardType(type: .dashboard12)
            Issue.record("Expected updateDashboardType to throw on local update failure")
        } catch {
            guard case HTTPError.noInternet = error else {
                Issue.record("Expected HTTPError.noInternet, got: \(error)")
                return
            }
        }
    }

    @Test("updateNotifications noActiveAccount: throws")
    func updateNotificationsNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.updateNotifications(notifications: AccountTestFixtures.makeNotifications())
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("updateNotifications success")
    func updateNotificationsSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchNotificationResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateNotifications(notifications: AccountTestFixtures.makeNotifications(entry: false, weighIn: true))

        #expect(api.patchNotificationCalls == 1)
        #expect(result.isSynced == true)
    }

    @Test("updateNotifications network error: saves offline")
    func updateNotificationsNetworkError() async throws {
        let api = MockAccountAPIRepository()
        api.patchNotificationResult = .failure(HTTPError.noInternet)
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateNotifications(notifications: AccountTestFixtures.makeNotifications(entry: false, weighIn: true))

        #expect(result.notificationSettings?.shouldSendEntryNotifications == false)
        #expect(result.notificationSettings?.shouldSendWeightInEntryNotifications == true)
        #expect(result.isSynced == false)
    }

    @Test("updateIntegrations noActiveAccount: throws")
    func updateIntegrationsNoActiveAccount() async {
        let sut = makeSUT()

        do {
            _ = try await sut.updateIntegrations(integrationType: .healthKit)
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("updateIntegrations API success")
    func updateIntegrationsSuccess() async throws {
        let local = MockAccountRepository()
        let integration = MockIntegrationAPIRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(local: local, integration: integration)
        sut.activeAccount = account

        let result = try await sut.updateIntegrations(integrationType: .healthKit)

        #expect(integration.createHealthIntegrationCalls == 1)
        #expect(result.integrationSettings?.isHealthKitOn == true)
    }

    @Test("updateIntegrations network error: saves offline")
    func updateIntegrationsNetworkError() async throws {
        let local = MockAccountRepository()
        let integration = MockIntegrationAPIRepository()
        integration.createHealthIntegrationResult = .failure(HTTPError.noInternet)
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(local: local, integration: integration)
        sut.activeAccount = account

        let result = try await sut.updateIntegrations(integrationType: .healthKit)

        #expect(integration.createHealthIntegrationCalls == 1)
        #expect(result.integrationSettings?.isHealthKitOn == true)
    }

    @Test("deleteHealthIntegration noActiveAccount: throws")
    func deleteHealthIntegrationNoActiveAccount() async {
        let sut = makeSUT()

        do {
            try await sut.deleteHealthIntegration(.healthKit)
            Issue.record("Expected noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("deleteHealthIntegration success")
    func deleteHealthIntegrationSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.fetchAccountResult = .success(AccountTestFixtures.makeAccountDTO(id: "101", email: "fresh@example.com"))

        let integration = MockIntegrationAPIRepository()
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        account.integrationSettings?.isHealthKitOn = true
        local.seed([account])

        let sut = makeSUT(api: api, local: local, integration: integration)
        sut.activeAccount = account

        try await sut.deleteHealthIntegration(.healthKit)

        #expect(integration.deleteHealthIntegrationCalls == 1)
    }

    @Test("deleteHealthIntegration network error: saves local state and rethrows")
    func deleteHealthIntegrationNetworkError() async {
        let integration = MockIntegrationAPIRepository()
        integration.deleteHealthIntegrationResult = .failure(HTTPError.noInternet)

        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        account.integrationSettings?.isHealthKitOn = true
        local.seed([account])

        let sut = makeSUT(local: local, integration: integration)
        sut.activeAccount = account

        do {
            try await sut.deleteHealthIntegration(.healthKit)
            Issue.record("Expected network error to rethrow")
        } catch {
            guard case HTTPError.noInternet = error else {
                Issue.record("Expected HTTPError.noInternet, got: \(error)")
                return
            }
        }

        #expect(account.integrationSettings?.isHealthKitOn == false)
        #expect(account.isSynced == false)
    }

    @Test("updateDashboardMetrics success: preserves sent order")
    func updateDashboardMetricsSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchDashboardMetricsResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateDashboardMetrics(metrics: ["bmi", "weight"])
        #expect(result.dashboardSettings?.dashboardMetrics == "bmi,weight")
        #expect(api.patchDashboardMetricsCalls == 1)
    }

    @Test("updateDashboardMetrics network error: marks unsynced and throws")
    func updateDashboardMetricsNetworkError() async {
        let api = MockAccountAPIRepository()
        api.patchDashboardMetricsResult = .failure(HTTPError.noInternet)
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        do {
            _ = try await sut.updateDashboardMetrics(metrics: ["bmi", "weight"])
            Issue.record("Expected dashboard metrics update to throw")
        } catch {
            guard case HTTPError.noInternet = error else {
                Issue.record("Expected HTTPError.noInternet, got: \(error)")
                return
            }
        }

        #expect(account.isSynced == false)
    }

    @Test("updateProgressMetrics success: preserves sent order")
    func updateProgressMetricsSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchProgressMetricsResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateProgressMetrics(metrics: ["goal", "monthlyChange"])
        #expect(result.dashboardSettings?.progressMetrics == "goal,monthlyChange")
        #expect(api.patchProgressMetricsCalls == 1)
    }

    @Test("updateProgressMetrics network error: marks unsynced and throws")
    func updateProgressMetricsNetworkError() async {
        let api = MockAccountAPIRepository()
        api.patchProgressMetricsResult = .failure(HTTPError.noInternet)
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])

        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        do {
            _ = try await sut.updateProgressMetrics(metrics: ["goal", "monthlyChange"])
            Issue.record("Expected progress metrics update to throw")
        } catch {
            guard case HTTPError.noInternet = error else {
                Issue.record("Expected HTTPError.noInternet, got: \(error)")
                return
            }
        }

        #expect(account.isSynced == false)
    }

    @Test("updateStreak success")
    func updateStreakSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchStreakResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])
        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateStreak(isStreakOn: true, streakTimestamp: "2026-01-01T00:00:00Z")
        #expect(api.patchStreakCalls == 1)
        #expect(result.accountId == "101")
    }

    @Test("updateStreak network error: saves offline")
    func updateStreakNetworkError() async throws {
        let api = MockAccountAPIRepository()
        api.patchStreakResult = .failure(HTTPError.noInternet)
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])
        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateStreak(isStreakOn: true, streakTimestamp: "2026-01-01T00:00:00Z")
        #expect(result.streaksSettings?.isStreakOn == true)
        #expect(result.isSynced == false)
    }

    @Test("updateWeightless success")
    func updateWeightlessSuccess() async throws {
        let api = MockAccountAPIRepository()
        api.patchWeightlessResult = .success(AccountTestFixtures.makeAccountResponse(accountId: "101", email: "user@example.com"))
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])
        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateWeightless(isWeightlessOn: true, weightlessTimestamp: "2026-01-01T00:00:00Z", weightlessWeight: 150)
        #expect(api.patchWeightlessCalls == 1)
        #expect(result.isSynced == true)
    }

    @Test("updateWeightless network error: saves offline")
    func updateWeightlessNetworkError() async throws {
        let api = MockAccountAPIRepository()
        api.patchWeightlessResult = .failure(HTTPError.noInternet)
        let local = MockAccountRepository()
        let account = AccountTestFixtures.makeAccountModel(id: "101", email: "user@example.com", isActive: true)
        local.seed([account])
        let sut = makeSUT(api: api, local: local)
        sut.activeAccount = account

        let result = try await sut.updateWeightless(isWeightlessOn: true, weightlessTimestamp: "2026-01-01T00:00:00Z", weightlessWeight: 150)
        #expect(result.weightlessSettings?.isWeightlessOn == true)
        #expect(result.isSynced == false)
    }

    // MARK: - Remaining

    @Test("updatePublishedState success")
    func updatePublishedStateSuccess() async throws {
        let local = MockAccountRepository()
        let active = AccountTestFixtures.makeAccountModel(id: "101", email: "a@example.com", isLoggedIn: true, isActive: true)
        let inactive = AccountTestFixtures.makeAccountModel(id: "102", email: "b@example.com", isLoggedIn: true, isActive: false)
        local.seed([active, inactive])

        let sut = makeSUT(local: local)

        try await sut.updatePublishedState()

        #expect(sut.allAccounts.count == 2)
        #expect(sut.activeAccount?.accountId == "101")
    }

    // MARK: - Helpers

    private func makeSUT(
        api: MockAccountAPIRepository? = nil,
        local: MockAccountRepository? = nil,
        logger: MockLoggerService? = nil,
        keychain: MockKeychainService? = nil,
        integration: MockIntegrationAPIRepository? = nil,
        bluetooth: MockBluetoothService? = nil,
        networkMonitor: MockNetworkMonitor? = nil
    ) -> AccountService {
        let api = api ?? MockAccountAPIRepository()
        let local = local ?? MockAccountRepository()
        let logger = logger ?? MockLoggerService()
        let keychain = keychain ?? MockKeychainService()
        let integration = integration ?? MockIntegrationAPIRepository()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let networkMonitor = networkMonitor ?? MockNetworkMonitor(isConnected: true)

        TestDependencyContainer.reset()
        TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)

        let sut = AccountService(
            apiRepo: api,
            localRepo: local,
            integrationApiRepo: integration,
            networkMonitor: networkMonitor,
            performInitialLoad: false
        )
        DependencyContainer.shared.register(sut as AccountServiceProtocol)
        return sut
    }

    private func assertNoActiveAccount(_ error: Error) {
        guard case AccountError.noActiveAccount = error else {
            Issue.record("Expected AccountError.noActiveAccount, got: \(error)")
            return
        }
    }

    private func assertMaxAccountsReached(_ error: Error) {
        guard case AccountError.maxAccountsReached = error else {
            Issue.record("Expected AccountError.maxAccountsReached, got: \(error)")
            return
        }
    }

    private func assertAccountNotFound(_ error: Error, id: String) {
        guard case AccountError.accountNotFound(let missingId) = error else {
            Issue.record("Expected AccountError.accountNotFound(\(id)), got: \(error)")
            return
        }
        #expect(missingId == id)
    }
}
