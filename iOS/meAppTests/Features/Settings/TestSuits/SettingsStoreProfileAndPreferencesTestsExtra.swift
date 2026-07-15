import Foundation
@testable import meApp
import Testing

extension SettingsStoreTests {
    @Suite("Profile Preferences Updates")
    @MainActor
    struct ProfilePreferences {
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
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

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
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

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

        // MARK: - Unit Type dialog (My Weight + My Kids)

        @Test("selectedMeasurementUnits defaults to imperialLbOz when account value is unset")
        func selectedMeasurementUnitsDefaultsToImperialLbOz() async {
            let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true, measurementUnits: nil, weightUnit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)
            await SettingsStoreTestFixtures.waitUntil { store.activeAccount?.accountId == account.accountId }

            #expect(store.selectedMeasurementUnits == .imperialLbOz)
        }

        @Test("selectedMeasurementUnits reflects the active account value")
        func selectedMeasurementUnitsReflectsAccountValue() async {
            let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true, measurementUnits: "metric", weightUnit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)
            await SettingsStoreTestFixtures.waitUntil { store.activeAccount?.accountId == account.accountId }

            #expect(store.selectedMeasurementUnits == .metric)
        }

        @Test("saveUnitSelections with no changes does nothing")
        func saveUnitSelectionsNoChangeDoesNothing() async {
            let notification = TestNotificationHelperService()
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1",
                isActiveAccount: true,
                measurementUnits: "imperialLbOz",
                weightUnit: .lb
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                seedDefaultAccount: false
            )
            await SettingsStoreTestFixtures.waitUntil { store.activeAccount?.accountId == account.accountId }

            store.saveUnitSelections(weightUnit: .lb, measurementUnits: .imperialLbOz)
            // Give any erroneously-spawned task a chance to run.
            try? await Task.sleep(nanoseconds: 200_000_000)

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(accountService.updateMeasurementUnitsCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("saveUnitSelections updates only measurement units when weight unit is unchanged")
        func saveUnitSelectionsUpdatesOnlyMeasurementUnits() async {
            let notification = TestNotificationHelperService()
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1",
                isActiveAccount: true,
                measurementUnits: "imperialLbOz",
                weightUnit: .lb
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateMeasurementUnitsResult = .success(())
            let bluetooth = MockBluetoothService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth,
                seedDefaultAccount: false
            )
            await SettingsStoreTestFixtures.waitUntil { store.activeAccount?.accountId == account.accountId }

            // "My Kids" must be editable (baby present) for measurement units to persist.
            guard let productTypeStore = DependencyContainer.shared.dependencies["ProductTypeStoreProtocol"] as? MockProductTypeStore else {
                Issue.record("Expected the store's product-type store to be a MockProductTypeStore")
                return
            }
            productTypeStore.availableItems = [.myWeight, .baby(profile: BabyProfile(id: "baby-1", name: "Aria"))]

            store.saveUnitSelections(weightUnit: .lb, measurementUnits: .metric)
            await SettingsStoreTestFixtures.waitUntil { accountService.updateMeasurementUnitsCalls == 1 && notification.dismissLoaderCalls == 1 }

            #expect(accountService.updateBodyCompCalls == 0)
            #expect(accountService.lastUpdatedMeasurementUnits == .metric)
            #expect(bluetooth.updateUserProfileForR4ScalesCalls == 0)
            #expect(notification.toastData?.message == ToastStrings.unitSettingUpdated)
        }

        @Test("saveUnitSelections updates both weight unit and measurement units")
        func saveUnitSelectionsUpdatesBoth() async {
            let notification = TestNotificationHelperService()
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1",
                isActiveAccount: true,
                measurementUnits: "imperialLbOz",
                weightUnit: .lb
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateBodyCompResult = .success(())
            accountService.updateMeasurementUnitsResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth,
                seedDefaultAccount: false
            )
            await SettingsStoreTestFixtures.waitUntil { store.activeAccount?.accountId == account.accountId }

            // Both sections editable: weight scale + baby present.
            guard let productTypeStore = DependencyContainer.shared.dependencies["ProductTypeStoreProtocol"] as? MockProductTypeStore else {
                Issue.record("Expected the store's product-type store to be a MockProductTypeStore")
                return
            }
            productTypeStore.availableItems = [.myWeight, .baby(profile: BabyProfile(id: "baby-1", name: "Aria"))]

            store.saveUnitSelections(weightUnit: .kg, measurementUnits: .metric)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateBodyCompCalls == 1 &&
                accountService.updateMeasurementUnitsCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastUpdatedBodyComp?.weightUnit == .kg)
            #expect(accountService.lastUpdatedMeasurementUnits == .metric)
            #expect(notification.showToastCalls == 1)
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
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

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
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )

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
    }
}

extension SettingsStoreTests {
    @Suite("Unit Type Visibility")
    @MainActor
    struct UnitTypeVisibility {
        private static func productTypeStore() -> MockProductTypeStore? {
            DependencyContainer.shared.dependencies["ProductTypeStoreProtocol"] as? MockProductTypeStore
        }

        private static func babyProfile() -> BabyProfile {
            BabyProfile(id: "baby-1", name: "Aria")
        }

        @Test("Unit Type row is hidden when Blood Pressure is the active product")
        func hiddenWhenBpmActive() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            guard let productTypeStore = Self.productTypeStore() else {
                Issue.record("Expected a MockProductTypeStore")
                return
            }
            // Weight scale present, but BPM is the selected product → still hidden.
            productTypeStore.availableItems = [.myWeight, .myBloodPressure]
            productTypeStore.selectedItem = .myBloodPressure

            #expect(store.isBloodPressureSelected == true)
            #expect(store.shouldShowUnitType == false)
        }

        @Test("Unit Type row is hidden for a Blood-Pressure-only account")
        func hiddenWhenBpOnly() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            guard let productTypeStore = Self.productTypeStore() else {
                Issue.record("Expected a MockProductTypeStore")
                return
            }
            productTypeStore.availableItems = [.myBloodPressure]
            productTypeStore.selectedItem = .myBloodPressure

            #expect(store.shouldShowUnitType == false)
        }

        @Test("Unit Type row is shown for a weight-scale account")
        func shownForWeightScale() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            guard let productTypeStore = Self.productTypeStore() else {
                Issue.record("Expected a MockProductTypeStore")
                return
            }
            productTypeStore.availableItems = [.myWeight]
            productTypeStore.selectedItem = .myWeight

            #expect(store.shouldShowUnitType == true)
        }

        @Test("Unit Type row is shown for a baby account")
        func shownForBaby() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            guard let productTypeStore = Self.productTypeStore() else {
                Issue.record("Expected a MockProductTypeStore")
                return
            }
            productTypeStore.availableItems = [.baby(profile: Self.babyProfile())]
            productTypeStore.selectedItem = .baby(profile: Self.babyProfile())

            #expect(store.shouldShowUnitType == true)
        }

        @Test("presentUnitPicker is a no-op while Blood Pressure is active")
        func presentUnitPickerNoOpWhenBpmActive() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            guard let productTypeStore = Self.productTypeStore() else {
                Issue.record("Expected a MockProductTypeStore")
                return
            }
            productTypeStore.availableItems = [.myWeight, .myBloodPressure]
            productTypeStore.selectedItem = .myBloodPressure

            store.presentUnitPicker()

            #expect(notification.showModalCalls == 0)
        }
    }
}
