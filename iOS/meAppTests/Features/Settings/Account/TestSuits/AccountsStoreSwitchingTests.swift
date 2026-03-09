import Foundation
import Testing
@testable import meApp

extension AccountsStoreTests {
    @Suite("Switch Active Account")
    @MainActor
    struct SwitchActiveAccount {
        @Test("switchActiveAccount with unknown account ID logs and exits")
        func switchActiveAccountUnknownIdLogsAndReturns() {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active], activeAccount: active)
            let store = harness.store

            store.switchActiveAccount(to: "missing")

            #expect(harness.accountService.switchAccountCalls == 0)
            #expect(harness.notification.showLoaderCalls == 0)
            #expect(harness.logger.messages.contains { $0.contains("does not exist") })
        }

        @Test("switchActiveAccount to current active account logs and exits")
        func switchActiveAccountSameActiveLogsAndReturns() {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active], activeAccount: active)
            let store = harness.store

            store.switchActiveAccount(to: "active")

            #expect(harness.accountService.switchAccountCalls == 0)
            #expect(harness.notification.showLoaderCalls == 0)
            #expect(harness.logger.messages.contains { $0.contains("same active account") })
        }

        @Test("switchActiveAccount success shows loader and success toast")
        func switchActiveAccountSuccess() async {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let target = AccountsStoreTestFixtures.makeAccount(
                id: "target",
                email: "target@example.com",
                firstName: "Target",
                isLoggedIn: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active, target], activeAccount: active)
            harness.accountService.switchAccountResult = .success(())
            let store = harness.store

            store.switchActiveAccount(to: "target")
            await AccountsStoreTestFixtures.waitUntil {
                harness.accountService.switchAccountCalls == 1 &&
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.showLoaderCalls == 1)
            #expect(harness.accountService.lastSwitchedAccountId == "target")
            #expect(harness.notification.toastData?.message == ToastStrings.switchingAccount("Target"))
            #expect(harness.accountService.activeAccount?.accountId == "target")
        }

        @Test("switchActiveAccount success falls back to email when first name is empty")
        func switchActiveAccountSuccessUsesEmailFallbackName() async {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let target = AccountsStoreTestFixtures.makeAccount(
                id: "target",
                email: "fallback@example.com",
                firstName: "",
                isLoggedIn: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active, target], activeAccount: active)
            harness.accountService.switchAccountResult = .success(())
            let store = harness.store

            store.switchActiveAccount(to: "target")
            await AccountsStoreTestFixtures.waitUntil {
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.toastData?.message == ToastStrings.switchingAccount("fallback@example.com"))
        }

        @Test("switchActiveAccount no-internet error shows unable-to-connect toast")
        func switchActiveAccountNoInternetError() async {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let target = AccountsStoreTestFixtures.makeAccount(
                id: "target",
                email: "target@example.com",
                firstName: "Target",
                isLoggedIn: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active, target], activeAccount: active)
            harness.accountService.switchAccountResult = .failure(HTTPError.noInternet)
            let store = harness.store

            store.switchActiveAccount(to: "target")
            await AccountsStoreTestFixtures.waitUntil {
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.toastData?.message == ToastStrings.unableToConnect)
        }

        @Test("switchActiveAccount timeout error shows unable-to-connect toast")
        func switchActiveAccountTimeoutError() async {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let target = AccountsStoreTestFixtures.makeAccount(
                id: "target",
                email: "target@example.com",
                firstName: "Target",
                isLoggedIn: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active, target], activeAccount: active)
            harness.accountService.switchAccountResult = .failure(HTTPError.timeout)
            let store = harness.store

            store.switchActiveAccount(to: "target")
            await AccountsStoreTestFixtures.waitUntil {
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.toastData?.message == ToastStrings.unableToConnect)
        }

        @Test("switchActiveAccount generic service error shows fallback error toast")
        func switchActiveAccountGenericError() async {
            let active = AccountsStoreTestFixtures.makeAccount(
                id: "active",
                email: "active@example.com",
                firstName: "Active",
                isLoggedIn: true,
                isActive: true
            )
            let target = AccountsStoreTestFixtures.makeAccount(
                id: "target",
                email: "target@example.com",
                firstName: "Target",
                isLoggedIn: true
            )
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: [active, target], activeAccount: active)
            harness.accountService.switchAccountResult = .failure(AccountTestError.apiFailed)
            let store = harness.store

            store.switchActiveAccount(to: "target")
            await AccountsStoreTestFixtures.waitUntil {
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.notification.toastData?.message == ToastStrings.somethingWentWrong)
        }

        @Test("switchActiveAccount is allowed even when max logged-in accounts are reached")
        func switchActiveAccountAtMaxUsersStillSwitches() async {
            let maxCount = AppConstants.Account.maxAccounts
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: maxCount)
            let active = accounts[0]
            let target = accounts[1]
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: active)
            harness.accountService.switchAccountResult = .success(())
            let store = harness.store

            store.switchActiveAccount(to: target.accountId)
            await AccountsStoreTestFixtures.waitUntil {
                harness.accountService.switchAccountCalls == 1 &&
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.accountService.lastSwitchedAccountId == target.accountId)
            #expect(harness.notification.showAlertCalls == 0)
            #expect(harness.accountService.activeAccount?.accountId == target.accountId)
        }
    }
}
