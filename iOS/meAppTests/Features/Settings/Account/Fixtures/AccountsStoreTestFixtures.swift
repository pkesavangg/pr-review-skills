import Foundation
@testable import meApp

@MainActor
struct AccountsStoreHarness {
    let store: AccountsStore
    let accountService: MockAccountService
    let notification: TestNotificationHelperService
    let entryService: MockEntryService
    let logger: MockLoggerService
    let feedService: MockFeedService
    let networkMonitor: MockNetworkMonitor
}

enum AccountsStoreTestFixtures {
    @MainActor
    static func makeSUT(
        accounts: [Account] = [],
        activeAccount: Account? = nil,
        networkConnected: Bool = true
    ) -> AccountsStoreHarness {
        let accountService = MockAccountService()
        accountService.seedAccounts(accounts, active: activeAccount)
        let notification = TestNotificationHelperService()
        let entryService = MockEntryService()
        let logger = MockLoggerService()
        let feedService = MockFeedService()
        let networkMonitor = MockNetworkMonitor(isConnected: networkConnected)

        let store = AccountsStore(
            injectedAccountService: accountService,
            injectedNotificationService: notification,
            injectedEntryService: entryService,
            injectedLogger: logger,
            injectedFeedService: feedService,
            networkMonitor: networkMonitor
        )

        return AccountsStoreHarness(
            store: store,
            accountService: accountService,
            notification: notification,
            entryService: entryService,
            logger: logger,
            feedService: feedService,
            networkMonitor: networkMonitor
        )
    }

    @MainActor
    static func makeAccount(
        id: String,
        email: String,
        firstName: String? = "User",
        isLoggedIn: Bool = true,
        isActive: Bool = false,
        isExpired: Bool = false,
        lastActiveTime: String? = nil
    ) -> Account {
        let account = AccountTestFixtures.makeAccountModel(
            id: id,
            email: email,
            firstName: firstName ?? "",
            isLoggedIn: isLoggedIn,
            isActive: isActive
        )
        account.firstName = firstName
        account.isExpired = isExpired
        account.lastActiveTime = lastActiveTime
        return account
    }

    @MainActor
    static func makeUserItem(
        accountId: String,
        name: String = "User",
        email: String = "user@example.com",
        isSelected: Bool = false,
        isExpired: Bool = false
    ) -> UserItemInfo {
        UserItemInfo(
            accountID: accountId,
            name: name,
            email: email,
            isSelected: isSelected,
            isExpired: isExpired,
            canShowSelection: true
        )
    }

    @MainActor
    static func makeLoggedInAccounts(count: Int) -> [Account] {
        (0..<count).map { index in
            makeAccount(
                id: "acct-\(index)",
                email: "user\(index)@example.com",
                firstName: "User\(index)",
                isLoggedIn: true,
                isActive: index == 0,
                lastActiveTime: "2026-03-\(String(format: "%02d", index + 1))T10:00:00.000Z"
            )
        }
    }

    @MainActor
    static func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }
}
