import Foundation
@testable import meApp
import Testing

extension SettingsStoreTests {
    @Suite("Profile And Preferences")
    @MainActor
    struct Profile {
        @Test("populateEditFormIfNeeded loads account values when form is pristine")
        func populateEditFormLoadsAccountValuesWhenPristine() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.resetEditProfileForm()

            #expect(store.editProfileForm.firstName.value == "Lakshmi")
            #expect(store.editProfileForm.lastName.value == "Priya")
            #expect(store.editProfileForm.email.value == "lakshmi@example.com")
            #expect(store.editProfileForm.zipcode.value == "560001")
        }

        @Test("populateEditFormIfNeeded preserves dirty edits")
        func populateEditFormPreservesDirtyEdits() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            store.editProfileForm.firstName.value = "Edited"
            store.editProfileForm.firstName.markAsDirty()

            store.populateEditFormIfNeeded()

            #expect(store.editProfileForm.firstName.value == "Edited")
        }

        @Test("populateEditFormIfNeeded ignores invalid birthday strings")
        func populateEditFormIgnoresInvalidBirthdayStrings() {
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1",
                email: "lakshmi@example.com",
                firstName: "Lakshmi",
                lastName: "Priya",
                gender: .female,
                dob: "not-a-date",
                zipcode: "560001",
                isActiveAccount: true,
                weightUnit: .kg,
                weightHeight: "681",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            let originalBirthday = store.editProfileForm.birthday.value

            store.resetEditProfileForm()

            #expect(store.editProfileForm.birthday.value == originalBirthday)
        }

        @Test("updateHeightInForm rejects invalid imperial values without changing the form")
        func updateHeightRejectsInvalidValues() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)

            store.updateHeightInForm(fromMetric: false, values: ["9", "13"])

            // The form-only mutator logs and returns on invalid input — no toast, no form change.
            #expect(notification.showToastCalls == 0)
            #expect(!store.editProfileForm.height.isDirty)
        }

        @Test("updateHeightInForm rejects invalid metric values without changing the form")
        func updateHeightRejectsInvalidMetricValues() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)

            store.updateHeightInForm(fromMetric: true, values: ["4", "9", "9"])

            #expect(notification.showToastCalls == 0)
            #expect(!store.editProfileForm.height.isDirty)
        }

        @Test("updateHeight without current stored height does nothing")
        func updateHeightWithoutCurrentStoredHeightDoesNothing() async {
            let notification = TestNotificationHelperService()
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1",
                email: "lakshmi@example.com",
                firstName: "Lakshmi",
                lastName: "Priya",
                gender: .female,
                dob: "1992-03-04",
                zipcode: "560001",
                isActiveAccount: true,
                weightUnit: .kg,
                weightHeight: "",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateHeight unchanged value does nothing")
        func updateHeightUnchangedValueDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateHeightInForm valid metric values update the form height and mark it dirty")
        func updateHeightValidMetricUpdatesForm() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .kg)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await Task.yield()

            // The form-only mutator updates the form; persistence happens later in saveProfile().
            #expect(store.editProfileForm.height.value == String(ConversionTools.convertCmToStoredHeight(173)))
            #expect(store.editProfileForm.height.isDirty)
            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showToastCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateHeightInForm does not call services or show a toast")
        func updateHeightDoesNotCallServices() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .kg)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let bluetooth = MockBluetoothService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 0)
            #expect(notification.showToastCalls == 0)
            #expect(notification.showAlertCalls == 0)
        }

        @Test("updateHeightInForm valid imperial values update the form height and mark it dirty")
        func updateHeightValidImperialUpdatesForm() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateHeightInForm(fromMetric: false, values: ["5", "9"])
            await Task.yield()

            let expected = ConversionTools.convertInchesToStoredHeight((5 * 12) + 9)
            #expect(store.editProfileForm.height.value == String(expected))
            #expect(store.editProfileForm.height.isDirty)
            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showToastCalls == 0)
        }

        @Test("confirmDiscardProfileChanges returns true when user exits")
        func confirmDiscardProfileChangesReturnsTrueOnExit() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.editProfileForm.firstName.value = "edited"
            store.editProfileForm.firstName.markAsDirty()

            let task = Task { await store.confirmDiscardProfileChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.first?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == true)
        }

        @Test("handleEditProfileExit pristine navigates back immediately")
        func handleEditProfileExitPristineNavigatesBack() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            let router = Router<SettingsRoute>()
            router.navigate(to: .editProfile)

            store.handleEditProfileExit(router: router)

            #expect(router.stack.isEmpty)
        }

        @Test("handleEditProfileExit dirty form resets and navigates on exit")
        func handleEditProfileExitDirtyFormResetsAndNavigatesOnExit() {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .editProfile)
            store.editProfileForm.firstName.value = "Edited"
            store.editProfileForm.firstName.markAsDirty()

            store.handleEditProfileExit(router: router)
            notification.alertData?.buttons.first?.action(nil)

            #expect(notification.showAlertCalls == 1)
            #expect(router.stack.isEmpty)
            #expect(store.editProfileForm.firstName.value == "Lakshmi")
        }

        @Test("saveProfile success trims fields and navigates back")
        func saveProfileSuccessTrimsFieldsAndNavigatesBack() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .editProfile)
            store.editProfileForm.firstName.value = "  Lakshmi  "
            store.editProfileForm.lastName.value = "  Priya  "
            store.editProfileForm.email.value = " lakshmi@example.com "
            store.editProfileForm.zipcode.value = " 560001 "
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.lastUpdatedProfile?.firstName == "Lakshmi")
            #expect(accountService.lastUpdatedProfile?.lastName == "Priya")
            #expect(accountService.lastUpdatedProfile?.email == "lakshmi@example.com")
            #expect(router.stack.isEmpty)
            #expect(notification.showToastCalls == 1)
        }

        @Test("saveProfile success with scale sync success shows profile toast")
        func saveProfileSuccessWithScaleSyncSuccessShowsProfileToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .editProfile)
            store.editProfileForm.firstName.value = "Lakshmi Updated"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.birthday.value = DateTimeTools.parse("1992-03-04") ?? Date()
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateProfileCalls == 1 &&
                bluetooth.updateUserProfileForR4ScalesCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.profileSaved)
            #expect(notification.showAlertCalls == 0)
            #expect(router.stack.isEmpty)
        }

        @Test("saveProfile unchanged scale fields skips scale sync")
        func saveProfileUnchangedScaleFieldsSkipsScaleSync() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .editProfile)
            store.editProfileForm.firstName.value = "Lakshmi"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.birthday.value = DateTimeTools.parse("1992-03-04") ?? Date()
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 0)
            #expect(notification.toastData?.message == ToastStrings.profileSaved)
        }

        @Test("saveProfile email conflict shows in-use toast")
        func saveProfileEmailConflictShowsInUseToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .failure(HTTPError.statusCode(409))
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.editProfileForm.firstName.value = "Lakshmi"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.message == ToastStrings.emailInUse)
        }

        @Test("saveProfile api error shows generic toast")
        func saveProfileApiErrorShowsGenericToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .failure(HTTPError.apiError(message: "bad", code: 400))
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.editProfileForm.firstName.value = "Lakshmi"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.message == ToastStrings.somethingWentWrong)
        }

        @Test("saveProfile server error shows server toast")
        func saveProfileServerErrorShowsServerToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .failure(HTTPError.serverError)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.editProfileForm.firstName.value = "Lakshmi"
            store.editProfileForm.lastName.value = "Priya"
            store.editProfileForm.email.value = "lakshmi@example.com"
            store.editProfileForm.zipcode.value = "560001"
            store.editProfileForm.validate()

            store.saveProfile(router: router)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.message == ToastStrings.serverError)
        }
    }
}
