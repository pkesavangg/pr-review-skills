import Foundation
@testable import meApp
import Testing

extension AccountsStoreTests {
    @Suite("List Mapping And Ordering")
    @MainActor
    struct ListMappingAndOrdering {
        @Test("maps and orders accounts by most recent activity with logged-out accounts last")
        func mapsAndOrdersAccountsAndExcludesLoggedOut() async {
            let recent = AccountsStoreTestFixtures.makeAccount(
                id: "recent",
                email: "recent@example.com",
                firstName: "Recent",
                isLoggedIn: true,
                lastActiveTime: "2026-03-05T10:00:00.000Z"
            )
            let older = AccountsStoreTestFixtures.makeAccount(
                id: "older",
                email: "older@example.com",
                firstName: "Older",
                isLoggedIn: true,
                lastActiveTime: "2026-03-01T10:00:00.000Z"
            )
            let invalidDate = AccountsStoreTestFixtures.makeAccount(
                id: "invalid-date",
                email: "invalid@example.com",
                firstName: "Invalid",
                isLoggedIn: true,
                lastActiveTime: "not-a-date"
            )
            let loggedOut = AccountsStoreTestFixtures.makeAccount(
                id: "logged-out",
                email: "loggedout@example.com",
                firstName: "LoggedOut",
                isLoggedIn: false,
                lastActiveTime: "2026-03-06T10:00:00.000Z"
            )

            let harness = AccountsStoreTestFixtures.makeSUT(
                accounts: [older, loggedOut, invalidDate, recent],
                activeAccount: recent
            )
            let store = harness.store
            await AccountsStoreTestFixtures.waitUntil {
                store.accounts.count == 4 && store.userItems.count == 4
            }

            // MA-3283: the store shows every saved account. Logged-in accounts come first
            // (sorted by last-active desc), followed by logged-out / expired accounts.
            #expect(store.accounts.map(\.accountId) == ["recent", "older", "invalid-date", "logged-out"])
            #expect(store.userItems.map(\.accountID) == ["recent", "older", "invalid-date", "logged-out"])
            #expect(store.accounts.contains { $0.accountId == "logged-out" } == true)
        }

        @Test("maps user item fields with name fallback, selected state, and expired login CTA state")
        func mapsUserItemFields() async throws {
            let selectedExpired = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "selected@example.com",
                firstName: nil,
                isLoggedIn: true,
                isActive: true,
                isExpired: true,
                lastActiveTime: "2026-03-05T10:00:00.000Z"
            )
            let harness = AccountsStoreTestFixtures.makeSUT(
                accounts: [selectedExpired],
                activeAccount: selectedExpired
            )
            let store = harness.store

            await AccountsStoreTestFixtures.waitUntil {
                store.userItems.count == 1
            }
            let item = try #require(store.userItems.first)

            #expect(item.accountID == "acct-1")
            #expect(item.name == "selected@example.com")
            #expect(item.email == "selected@example.com")
            #expect(item.isSelected == true)
            #expect(item.isExpired == true)
            #expect(item.canShowSelection == true)
        }

        @Test("active account publisher updates store active account")
        func activeAccountPublisherUpdatesStore() async {
            let accountA = AccountsStoreTestFixtures.makeAccount(
                id: "acct-a",
                email: "a@example.com",
                firstName: "A",
                isLoggedIn: true,
                isActive: true
            )
            let accountB = AccountsStoreTestFixtures.makeAccount(
                id: "acct-b",
                email: "b@example.com",
                firstName: "B",
                isLoggedIn: true
            )

            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [accountA, accountB], activeAccount: accountA)
            let store = harness.store

            harness.accountService.activeAccount = accountB
            await AccountsStoreTestFixtures.waitUntil {
                store.activeAccount?.accountId == "acct-b"
            }

            #expect(store.activeAccount?.accountId == "acct-b")
        }

        @Test("all accounts publisher updates mapped list and rows")
        func allAccountsPublisherUpdatesMappedRows() async {
            let first = AccountsStoreTestFixtures.makeAccount(
                id: "acct-first",
                email: "first@example.com",
                firstName: "First",
                isLoggedIn: true,
                lastActiveTime: "2026-03-02T10:00:00.000Z"
            )
            let second = AccountsStoreTestFixtures.makeAccount(
                id: "acct-second",
                email: "second@example.com",
                firstName: "Second",
                isLoggedIn: true,
                lastActiveTime: "2026-03-03T10:00:00.000Z"
            )

            let harness = AccountsStoreTestFixtures.makeSUT()
            let store = harness.store
            harness.accountService.seedAccounts([first, second], active: second)

            await AccountsStoreTestFixtures.waitUntil {
                store.accounts.map(\.accountId) == ["acct-second", "acct-first"]
            }

            #expect(store.userItems.map(\.accountID) == ["acct-second", "acct-first"])
            #expect(store.activeAccount?.accountId == "acct-second")
        }

        @Test("accounts with nil last active time sort behind parseable dates")
        func nilLastActiveTimeSortsLast() async {
            let dated = AccountsStoreTestFixtures.makeAccount(
                id: "dated",
                email: "dated@example.com",
                isLoggedIn: true,
                lastActiveTime: "2026-03-05T10:00:00.000Z"
            )
            let noDate = AccountsStoreTestFixtures.makeAccount(
                id: "no-date",
                email: "nodate@example.com",
                isLoggedIn: true,
                lastActiveTime: nil
            )

            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [noDate, dated], activeAccount: dated)
            let store = harness.store
            await AccountsStoreTestFixtures.waitUntil {
                store.accounts.count == 2
            }

            #expect(store.accounts.map(\.accountId) == ["dated", "no-date"])
        }
    }
}
