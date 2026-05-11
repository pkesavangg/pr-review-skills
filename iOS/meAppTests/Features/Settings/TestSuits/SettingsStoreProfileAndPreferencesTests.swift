import Foundation
import Testing
@testable import meApp

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

        @Test("updateHeight rejects invalid values and shows error toast")
        func updateHeightRejectsInvalidValues() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)

            store.updateHeightInForm(fromMetric: false, values: ["9", "13"])

            #expect(notification.showToastCalls == 1)
            #expect(notification.toastData?.title == ToastStrings.errorUpdatingHeight)
        }

        @Test("updateHeight rejects invalid metric values and shows error toast")
        func updateHeightRejectsInvalidMetricValues() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)

            store.updateHeightInForm(fromMetric: true, values: ["4", "9", "9"])

            #expect(notification.showToastCalls == 1)
            #expect(notification.toastData?.title == ToastStrings.errorUpdatingHeight)
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

        @Test("updateHeight success persists body comp and shows toast")
        func updateHeightSuccessPersistsBodyComp() async {
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
                weightHeight: "680",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.lastUpdatedBodyComp?.height == Double(ConversionTools.convertCmToStoredHeight(173)))
            #expect(notification.showToastCalls == 1)
        }

        @Test("updateHeight success with scale sync success shows success toast")
        func updateHeightSuccessWithScaleSyncSuccessShowsToast() async {
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
                weightHeight: "680",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateBodyCompCalls == 1 &&
                bluetooth.updateUserProfileForR4ScalesCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.heightUpdated)
            #expect(notification.showAlertCalls == 0)
        }

        @Test("updateHeight failure shows error toast")
        func updateHeightFailureShowsErrorToast() async {
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
                weightHeight: "680",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.title == ToastStrings.errorUpdatingHeight)
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
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)
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
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)
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
            let bluetooth = MockBluetoothService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)
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

        @Test("updateWeightUnit same unit does nothing")
        func updateWeightUnitSameUnitDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.updateWeightUnit(.kg)
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateWeightUnit without active account does nothing")
        func updateWeightUnitWithoutActiveAccountDoesNothing() async {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            store.updateWeightUnit(.kg)
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateWeightUnit success saves body comp and shows success toast")
        func updateWeightUnitSuccessSavesBodyComp() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateWeightUnit(.kg)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.lastUpdatedBodyComp?.weightUnit == .kg)
            #expect(notification.showToastCalls == 1)
        }

        @Test("updateWeightUnit success with scale sync success shows success toast")
        func updateWeightUnitSuccessWithScaleSyncSuccessShowsToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateWeightUnit(.kg)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateBodyCompCalls == 1 &&
                bluetooth.updateUserProfileForR4ScalesCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.unitSettingUpdated)
            #expect(notification.showAlertCalls == 0)
        }

        @Test("updateWeightUnit failure shows unable to update toast")
        func updateWeightUnitFailureShowsToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateWeightUnit(.kg)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.message == ToastStrings.unableToUpdateAccountSettings)
        }

        @Test("updateActivityLevel success persists body comp")
        func updateActivityLevelSuccessPersistsBodyComp() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateActivityLevel(.normal)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.lastUpdatedBodyComp?.activityLevel == .normal)
            #expect(notification.showToastCalls == 1)
        }

        @Test("updateActivityLevel success with scale sync success shows success toast")
        func updateActivityLevelSuccessWithScaleSyncSuccessShowsToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateActivityLevel(.normal)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateBodyCompCalls == 1 &&
                bluetooth.updateUserProfileForR4ScalesCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.activitySettingUpdated)
            #expect(notification.showAlertCalls == 0)
        }

        @Test("updateActivityLevel same level does nothing")
        func updateActivityLevelSameLevelDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.updateActivityLevel(.athlete)
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateActivityLevel without active account does nothing")
        func updateActivityLevelWithoutActiveAccountDoesNothing() async {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            store.updateActivityLevel(.normal)
            await Task.yield()

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateNotificationPreference success persists notifications")
        func updateNotificationPreferenceSuccessPersistsNotifications() async {
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
                weightHeight: "681",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: false,
                shouldSendWeightInEntryNotifications: false
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateNotificationsResult = .success(())
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateNotificationPreference(.enableWithWeight)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateNotificationsCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.lastUpdatedNotifications?.shouldSendEntryNotifications == true)
            #expect(accountService.lastUpdatedNotifications?.shouldSendWeightInEntryNotifications == true)
            #expect(notification.showToastCalls == 1)
        }

        @Test("updateNotificationPreference same preference does nothing")
        func updateNotificationPreferenceSamePreferenceDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.updateNotificationPreference(.enableWithWeight)
            await Task.yield()

            #expect(accountService.updateNotificationsCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateNotificationPreference without active account does nothing")
        func updateNotificationPreferenceWithoutActiveAccountDoesNothing() async {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            store.updateNotificationPreference(.enable)
            await Task.yield()

            #expect(accountService.updateNotificationsCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("updateNotificationPreference failure shows unable to update toast")
        func updateNotificationPreferenceFailureShowsToast() async {
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
                weightHeight: "681",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: false,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateNotificationsResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateNotificationPreference(.enable)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateNotificationsCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.message == ToastStrings.unableToUpdateAccountSettings)
        }

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

        @Test("updateGender success persists profile")
        func updateGenderSuccessPersistsProfile() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateGenderInForm(.male)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.lastUpdatedProfile?.gender == .male)
            #expect(notification.showToastCalls == 1)
        }

        @Test("updateGender success with scale sync success shows success toast")
        func updateGenderSuccessWithScaleSyncSuccessShowsToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateGenderInForm(.male)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateProfileCalls == 1 &&
                bluetooth.updateUserProfileForR4ScalesCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.profileSaved)
            #expect(notification.showAlertCalls == 0)
        }

        @Test("updateGender failure shows unable to update toast")
        func updateGenderFailureShowsToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.updateGenderInForm(.male)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.toastData?.message == ToastStrings.unableToUpdateAccountSettings)
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
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)
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
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

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
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateActivityLevel(.normal)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }

        @Test("updateGender user selection in progress shows pending alert")
        func updateGenderUserSelectionInProgressShowsPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateProfileResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateGenderInForm(.male)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateProfileCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }

        @Test("updateHeight user selection in progress shows pending alert")
        func updateHeightUserSelectionInProgressShowsPendingAlert() async {
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
                weightHeight: "680",
                activityLevel: .athlete,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, bluetoothService: bluetooth)

            store.updateHeightInForm(fromMetric: true, values: ["1", "7", "3"])
            await SettingsStoreTestFixtures.waitUntil { accountService.updateBodyCompCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }
    }
}
