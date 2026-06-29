import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct LandingStoreTests {

    private func makeSUT() -> (LandingStore, MockAccountService) {
        TestDependencyContainer.reset()
        let accountService = MockAccountService()
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        let store = LandingStore()
        return (store, accountService)
    }

    private func makeAccount(
        id: String,
        isLoggedIn: Bool = true,
        isExpired: Bool = false,
        lastActiveTime: String? = nil
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: "\(id)@example.com",
            isLoggedIn: isLoggedIn,
            isExpired: isExpired,
            isActiveAccount: false,
            lastActiveTime: lastActiveTime
        )
    }

    private func waitUntil(condition: @escaping @MainActor () -> Bool) async {
        let deadline = ContinuousClock.now + .nanoseconds(1_000_000_000)
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
    }

    // MARK: - Account Visibility (MOB-423)

    @Test("shows manually logged-out accounts flagged as needing login")
    func showsLoggedOutAccounts() async {
        let (store, accountService) = makeSUT()

        let active = makeAccount(id: "active", isLoggedIn: true, isExpired: false)
        let loggedOut = makeAccount(id: "logged-out", isLoggedIn: false, isExpired: false)
        accountService.seedAccounts([active, loggedOut], active: active)

        await waitUntil { store.accounts.count == 2 }

        #expect(Set(store.accounts.map(\.accountId)) == ["active", "logged-out"])
        // The logged-out account renders the "Logged out" card (isExpired drives that UI);
        // the active account stays tap-to-switch.
        #expect(store.userItems.first { $0.accountID == "logged-out" }?.isExpired == true)
        #expect(store.userItems.first { $0.accountID == "active" }?.isExpired == false)
    }

    @Test("shows auto-logged-out (expired) accounts flagged as needing login")
    func showsExpiredAccounts() async {
        let (store, accountService) = makeSUT()

        let active = makeAccount(id: "active", isLoggedIn: true, isExpired: false)
        let expired = makeAccount(id: "expired", isLoggedIn: true, isExpired: true)
        accountService.seedAccounts([active, expired], active: active)

        await waitUntil { store.accounts.count == 2 }

        #expect(Set(store.accounts.map(\.accountId)) == ["active", "expired"])
        #expect(store.userItems.first { $0.accountID == "expired" }?.isExpired == true)
    }

    @Test("shows every saved account regardless of login state")
    func showsAllSavedAccounts() async {
        let (store, accountService) = makeSUT()

        let a = makeAccount(id: "a", isLoggedIn: true, isExpired: false)
        let b = makeAccount(id: "b", isLoggedIn: false, isExpired: false)
        let c = makeAccount(id: "c", isLoggedIn: true, isExpired: true)
        let d = makeAccount(id: "d", isLoggedIn: true, isExpired: false)
        accountService.seedAccounts([a, b, c, d], active: a)

        await waitUntil { store.accounts.count == 4 }

        #expect(Set(store.accounts.map(\.accountId)) == ["a", "b", "c", "d"])
        // Only fully-active accounts are tap-to-switch; logged-out/expired need login.
        let needsLoginIDs = Set(store.userItems.filter { $0.isExpired }.map(\.accountID))
        #expect(needsLoginIDs == ["b", "c"])
    }

    @Test("sorts active accounts by most recent lastActiveTime")
    func sortsAccountsByLastActiveTime() async {
        let (store, accountService) = makeSUT()

        let older = makeAccount(id: "older", isLoggedIn: true, lastActiveTime: "2026-01-01T00:00:00Z")
        let newer = makeAccount(id: "newer", isLoggedIn: true, lastActiveTime: "2026-06-01T00:00:00Z")
        accountService.seedAccounts([older, newer], active: newer)

        await waitUntil { store.accounts.count == 2 }

        #expect(store.accounts.first?.accountId == "newer")
        #expect(store.accounts.last?.accountId == "older")
    }

    @Test("userItems maps accountId, email, and firstName as name")
    func userItemsMapsAccountFields() async throws {
        let (store, accountService) = makeSUT()

        let account = AccountTestFixtures.makeAccountSnapshot(
            id: "u1",
            email: "user@example.com",
            firstName: "Alice",
            isLoggedIn: true,
            isExpired: false,
            isActiveAccount: false
        )
        accountService.seedAccounts([account], active: account)

        await waitUntil { store.userItems.count == 1 }

        let item = try #require(store.userItems.first)
        #expect(item.accountID == "u1")
        #expect(item.email == "user@example.com")
        #expect(item.name == "Alice")
    }
}
