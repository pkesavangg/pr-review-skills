import Foundation
@testable import meApp
import Testing

/// Additional `AccountService` (Data/Services, ≥85% bar) coverage for the logout,
/// remove-from-device, switch, and bulk-delete paths (MOB-1396).
@Suite(.serialized)
@MainActor
struct AccountServiceCoverageTests {

    private func makeSUT(
        api: MockAccountAPIRepository? = nil,
        local: MockAccountRepository? = nil,
        networkMonitor: MockNetworkMonitor? = nil
    ) -> (AccountService, MockAccountAPIRepository, MockAccountRepository) {
        let api = api ?? MockAccountAPIRepository()
        let local = local ?? MockAccountRepository()
        let logger = MockLoggerService()
        let keychain = MockKeychainService()
        let bluetooth = MockBluetoothService()
        let networkMonitor = networkMonitor ?? MockNetworkMonitor(isConnected: true)

        TestDependencyContainer.reset()
        TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)

        let sut = AccountService(
            apiRepo: api,
            localRepo: local,
            integrationApiRepo: MockIntegrationAPIRepository(),
            networkMonitor: networkMonitor,
            performInitialLoad: false
        )
        DependencyContainer.shared.register(sut as AccountServiceProtocol)
        return (sut, api, local)
    }

    // MARK: - logOut

    @Test("logOut: throws when there is no active account and no id is given")
    func logOutNoActiveAccountThrows() async {
        let (sut, _, _) = makeSUT()

        await #expect(throws: AccountError.self) {
            try await sut.logOut()
        }
    }

    @Test("logOut: throws accountNotFound when the id has no local record")
    func logOutAccountNotFoundThrows() async {
        let (sut, _, _) = makeSUT()

        await #expect(throws: AccountError.self) {
            try await sut.logOut(accountId: "missing")
        }
    }

    @Test("logOut: completes for a seeded logged-in account")
    func logOutSuccess() async throws {
        let local = MockAccountRepository()
        local.seed([AccountTestFixtures.makeAccountModel(id: "acct-1", isLoggedIn: true, isActive: true)])
        let (sut, _, _) = makeSUT(local: local)

        try await sut.logOut(accountId: "acct-1")

        // The account is no longer marked as active/logged-in after logout.
        let stored = try await local.fetchAccount(byId: "acct-1")
        #expect(stored?.isLoggedIn == false || stored == nil)
    }

    // MARK: - removeAccountFromDevice

    @Test("removeAccountFromDevice: no-ops when the account is not on the device")
    func removeAccountNotFound() async throws {
        let local = MockAccountRepository()
        let (sut, _, _) = makeSUT(local: local)

        try await sut.removeAccountFromDevice(accountId: "ghost")

        #expect(try await local.fetchAllAccounts().isEmpty)
    }

    @Test("removeAccountFromDevice: deletes the local record for a seeded account")
    func removeAccountSuccess() async throws {
        let local = MockAccountRepository()
        local.seed([AccountTestFixtures.makeAccountModel(id: "acct-1", isLoggedIn: true, isActive: false)])
        let (sut, _, _) = makeSUT(local: local)

        try await sut.removeAccountFromDevice(accountId: "acct-1")

        #expect(try await local.fetchAccount(byId: "acct-1") == nil)
    }

    // MARK: - switchAccount

    @Test("switchAccount: throws noInternet when offline")
    func switchAccountNoNetworkThrows() async {
        let (sut, _, _) = makeSUT(networkMonitor: MockNetworkMonitor(isConnected: false))

        await #expect(throws: HTTPError.self) {
            try await sut.switchAccount(to: "acct-1")
        }
    }

    // MARK: - bulk delete

    @Test("deleteAllAccounts: clears every local account")
    func deleteAllAccountsClearsLocal() async throws {
        let local = MockAccountRepository()
        local.seed([
            AccountTestFixtures.makeAccountModel(id: "a1"),
            AccountTestFixtures.makeAccountModel(id: "a2")
        ])
        let (sut, _, _) = makeSUT(local: local)

        try await sut.deleteAllAccounts()

        #expect(try await local.fetchAllAccounts().isEmpty)
    }

    @Test("deleteAccount: throws when there is no active account")
    func deleteAccountNoActiveThrows() async {
        let (sut, _, _) = makeSUT()

        await #expect(throws: AccountError.self) {
            try await sut.deleteAccount()
        }
    }
}
