import Foundation
@testable import meApp
import Testing

extension SettingsStoreTests {
    @Suite("Account Actions")
    @MainActor
    struct AccountActions {
        @Test("handleLogout shows alert and primary action logs out")
        func handleLogoutRunsLogoutFlow() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.handleLogout()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.logOutCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.showAlertCalls == 1)
            #expect(accountService.logOutCalls == 1)
            #expect(notification.showLoaderCalls == 1)
        }

        @Test("handleLogout cancel does not log out")
        func handleLogoutCancelDoesNotLogOut() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.handleLogout()
            notification.alertData?.buttons.last?.action(nil)
            await Task.yield()

            #expect(accountService.logOutCalls == 0)
        }

        @Test("handleLogout failure still dismisses loader")
        func handleLogoutFailureStillDismissesLoader() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.logOutResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.handleLogout()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.logOutCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.showLoaderCalls == 1)
        }

        @Test("handleLogoutForAllAccounts shows alert and primary action logs out all accounts")
        func handleLogoutForAllAccountsRunsFlow() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.handleLogoutForAllAccounts()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.logOutAllAccountsCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.showAlertCalls == 1)
            #expect(accountService.logOutAllAccountsCalls == 1)
        }

        @Test("handleLogoutForAllAccounts cancel does not log out all")
        func handleLogoutForAllAccountsCancelDoesNotLogOutAll() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.handleLogoutForAllAccounts()
            notification.alertData?.buttons.last?.action(nil)
            await Task.yield()

            #expect(accountService.logOutAllAccountsCalls == 0)
        }

        @Test("handleLogoutForAllAccounts failure still dismisses loader")
        func handleLogoutForAllAccountsFailureStillDismissesLoader() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.logOutAllAccountsResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.handleLogoutForAllAccounts()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.logOutAllAccountsCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.showLoaderCalls == 1)
        }

        @Test("handleDeleteAccount failure shows toast and still dismisses loader")
        func handleDeleteAccountFailureShowsToast() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.deleteAccountResult = .failure(AccountTestError.apiFailed)
            let bluetooth = MockBluetoothService()
            bluetooth.deleteR4ScalesResult = .success(())
            let integration = MockIntegrationService()
            integration.clearIntegrationError = AccountTestError.apiFailed
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth,
                integrationService: integration
            )

            store.handleDeleteAccount()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                integration.clearIntegrationCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.showAlertCalls == 1)
            #expect(bluetooth.deleteR4ScalesCalls == 1)
            #expect(integration.clearIntegrationCalls == 1)
            #expect(accountService.deleteAccountCalls == 0)
            #expect(notification.showToastCalls == 1)
        }

        @Test("handleDeleteAccount cancel does not start delete flow")
        func handleDeleteAccountCancelDoesNotStartDeleteFlow() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let bluetooth = MockBluetoothService()
            let integration = MockIntegrationService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth,
                integrationService: integration
            )

            store.handleDeleteAccount()
            notification.alertData?.buttons.last?.action(nil)
            await Task.yield()

            #expect(bluetooth.deleteR4ScalesCalls == 0)
            #expect(integration.clearIntegrationCalls == 0)
            #expect(accountService.deleteAccountCalls == 0)
        }

        @Test("handleDeleteAccount success deletes scales clears integration and deletes account")
        func handleDeleteAccountSuccessRunsFlow() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let bluetooth = MockBluetoothService()
            bluetooth.deleteR4ScalesResult = .success(())
            let integration = MockIntegrationService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth,
                integrationService: integration
            )

            store.handleDeleteAccount()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.deleteAccountCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(bluetooth.deleteR4ScalesCalls == 1)
            #expect(integration.clearIntegrationCalls == 1)
            #expect(accountService.deleteAccountCalls == 1)
        }

        @Test("handleDeleteAccount continues when deleting scales fails")
        func handleDeleteAccountContinuesWhenDeletingScalesFails() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let bluetooth = MockBluetoothService()
            bluetooth.deleteR4ScalesResult = .failure(.notImplemented)
            let integration = MockIntegrationService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth,
                integrationService: integration
            )

            store.handleDeleteAccount()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.deleteAccountCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(bluetooth.deleteR4ScalesCalls == 1)
            #expect(integration.clearIntegrationCalls == 1)
            #expect(accountService.deleteAccountCalls == 1)
        }
    }
}
