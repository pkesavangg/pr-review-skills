import Foundation
import Testing
@testable import meApp

extension AccountsStoreTests {
    @Suite("Remove User And Errors")
    @MainActor
    struct RemoveUserAndErrors {
        @Test("userRemoveHandler shows confirmation alert with remove and cancel actions")
        func userRemoveHandlerShowsConfirmationAlert() {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [account], activeAccount: account)
            let store = harness.store
            let user = AccountsStoreTestFixtures.makeUserItem(
                accountId: "acct-1",
                name: "User One",
                email: "user1@example.com"
            )

            store.userRemoveHandler(user: user)

            #expect(harness.notification.showAlertCalls == 1)
            #expect(harness.notification.alertData?.buttons.count == 2)
        }

        @Test("remove user with unknown account logs and exits")
        func removeUserUnknownAccountLogsAndReturns() {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [account], activeAccount: account)
            let store = harness.store
            let unknownUser = AccountsStoreTestFixtures.makeUserItem(
                accountId: "missing-acct",
                name: "Missing User",
                email: "missing@example.com"
            )

            store.userRemoveHandler(user: unknownUser)
            harness.notification.alertData?.buttons.first?.action(nil)

            #expect(harness.accountService.removeAccountFromDeviceCalls == 0)
            #expect(harness.notification.showLoaderCalls == 0)
            #expect(harness.logger.messages.contains { $0.contains("does not exist") })
        }

        @Test("remove user offline shows unable-to-connect toast and skips service call")
        func removeUserOfflineShowsUnableToConnect() {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(
                accounts: [account],
                activeAccount: account,
                networkConnected: false
            )
            let store = harness.store
            let user = AccountsStoreTestFixtures.makeUserItem(
                accountId: "acct-1",
                name: "User One",
                email: "user1@example.com"
            )

            store.userRemoveHandler(user: user)
            harness.notification.alertData?.buttons.first?.action(nil)

            #expect(harness.accountService.removeAccountFromDeviceCalls == 0)
            #expect(harness.notification.showLoaderCalls == 0)
            #expect(harness.notification.toastData?.message == ToastStrings.unableToConnect)
        }

        @Test("remove user success logs out account and dismisses loader")
        func removeUserSuccess() async {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(
                accounts: [account],
                activeAccount: account,
                networkConnected: true
            )
            harness.accountService.removeAccountFromDeviceResult = .success(())
            let store = harness.store
            let user = AccountsStoreTestFixtures.makeUserItem(
                accountId: "acct-1",
                name: "User One",
                email: "user1@example.com"
            )

            store.userRemoveHandler(user: user)
            harness.notification.alertData?.buttons.first?.action(nil)
            await AccountsStoreTestFixtures.waitUntil {
                harness.accountService.removeAccountFromDeviceCalls == 1 &&
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.showLoaderCalls == 1)
            // The store removes the account from the device (MA-3283), not a plain logout.
            #expect(harness.accountService.lastRemovedFromDeviceAccountId == "acct-1")
        }

        @Test("remove user cancel action does not call logout")
        func removeUserCancelDoesNotCallLogout() async {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [account], activeAccount: account)
            let store = harness.store
            let user = AccountsStoreTestFixtures.makeUserItem(
                accountId: "acct-1",
                name: "User One",
                email: "user1@example.com"
            )

            store.userRemoveHandler(user: user)
            harness.notification.alertData?.buttons.last?.action(nil)
            await Task.yield()

            #expect(harness.accountService.removeAccountFromDeviceCalls == 0)
        }

        @Test("remove user no-internet service error shows unable-to-connect toast")
        func removeUserNoInternetServiceError() async {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [account], activeAccount: account)
            harness.accountService.removeAccountFromDeviceResult = .failure(HTTPError.noInternet)
            let store = harness.store
            let user = AccountsStoreTestFixtures.makeUserItem(
                accountId: "acct-1",
                name: "User One",
                email: "user1@example.com"
            )

            store.userRemoveHandler(user: user)
            harness.notification.alertData?.buttons.first?.action(nil)
            await AccountsStoreTestFixtures.waitUntil {
                harness.accountService.removeAccountFromDeviceCalls == 1 &&
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.toastData?.message == ToastStrings.unableToConnect)
        }

        @Test("remove user generic service error shows fallback error toast")
        func removeUserGenericServiceError() async {
            let account = AccountsStoreTestFixtures.makeAccount(
                id: "acct-1",
                email: "user1@example.com",
                firstName: "User One",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [account], activeAccount: account)
            harness.accountService.removeAccountFromDeviceResult = .failure(AccountTestError.apiFailed)
            let store = harness.store
            let user = AccountsStoreTestFixtures.makeUserItem(
                accountId: "acct-1",
                name: "User One",
                email: "user1@example.com"
            )

            store.userRemoveHandler(user: user)
            harness.notification.alertData?.buttons.first?.action(nil)
            await AccountsStoreTestFixtures.waitUntil {
                harness.accountService.removeAccountFromDeviceCalls == 1 &&
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.toastData?.message == ToastStrings.somethingWentWrong)
        }
    }
}
