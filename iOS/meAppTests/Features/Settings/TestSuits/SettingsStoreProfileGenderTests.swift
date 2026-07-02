import Foundation
@testable import meApp
import Testing

extension SettingsStoreTests {
    @Suite("Profile Gender And Pending Alerts")
    @MainActor
    struct ProfileGenderAndAlerts {
        @Test("updateGender same value does nothing")
        func updateGenderSameValueDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.updateGenderInForm(.female)
            await Task.yield()

            #expect(accountService.updateProfileCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateGender without active account does nothing")
        func updateGenderWithoutActiveAccountDoesNothing() async {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            store.updateGenderInForm(.male)
            await Task.yield()

            #expect(accountService.updateProfileCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateGenderInForm updates the form gender and marks it dirty")
        func updateGenderInFormUpdatesFormValue() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateGenderInForm(.male)
            await Task.yield()

            // The form-only mutator updates the form; persistence happens later in saveProfile().
            #expect(store.editProfileForm.gender.value == .male)
            #expect(store.editProfileForm.gender.isDirty)
            #expect(accountService.updateProfileCalls == 0)
            #expect(notification.showToastCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateGenderInForm does not call services or show a toast")
        func updateGenderInFormDoesNotCallServices() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let bluetooth = MockBluetoothService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

            store.updateGenderInForm(.male)
            await Task.yield()

            #expect(accountService.updateProfileCalls == 0)
            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 0)
            #expect(notification.showToastCalls == 0)
            #expect(notification.showAlertCalls == 0)
        }

        @Test("updateGenderInForm reflects the latest selection")
        func updateGenderInFormReflectsLatestSelection() async {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.updateGenderInForm(.male)
            store.updateGenderInForm(.female)
            await Task.yield()

            #expect(store.editProfileForm.gender.value == .female)
            #expect(store.editProfileForm.gender.isDirty)
            #expect(accountService.updateProfileCalls == 0)
        }

        @Test("confirmDiscardProfileChanges returns false when user cancels")
        func confirmDiscardProfileChangesReturnsFalseOnCancel() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.editProfileForm.firstName.value = "edited"
            store.editProfileForm.firstName.markAsDirty()

            let task = Task { await store.confirmDiscardProfileChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.last?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == false)
        }

        @Test("saveProfile no internet shows no toast")
        func saveProfileNoInternetShowsNoToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .failure(HTTPError.noInternet)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.editProfileForm.firstName.value = "Lakshmi"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.showToastCalls == 0)
        }

        @Test("saveProfile user selection in progress shows pending alert")
        func saveProfileUserSelectionInProgressShowsPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            store.editProfileForm.firstName.value = "Lakshmi Updated"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 1)
            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }

        @Test("updateWeightUnit user selection in progress shows pending alert")
        func updateWeightUnitUserSelectionInProgressShowsPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

            store.updateWeightUnit(.kg)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }

        @Test("updateActivityLevel user selection in progress shows pending alert")
        func updateActivityLevelUserSelectionInProgressShowsPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

            store.updateActivityLevel(.normal)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }

        @Test("updateGenderInForm does not present the scale-pending alert")
        func updateGenderInFormDoesNotPresentPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

            store.updateGenderInForm(.male)
            await Task.yield()

            // No R4 sync happens here, so the pending alert is never shown.
            #expect(notification.showAlertCalls == 0)
            #expect(notification.showToastCalls == 0)
            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 0)
        }

        @Test("updateHeightInForm does not present the scale-pending alert")
        func updateHeightInFormDoesNotPresentPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .kg)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await Task.yield()

            #expect(notification.showAlertCalls == 0)
            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 0)
            #expect(store.editProfileForm.height.isDirty)
        }
    }
}
