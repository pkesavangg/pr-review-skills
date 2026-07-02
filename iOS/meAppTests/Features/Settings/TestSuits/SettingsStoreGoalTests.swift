import Foundation
@testable import meApp
import Testing

extension SettingsStoreTests {
    @Suite("Goals")
    @MainActor
    struct Goal {
        @Test("populateGoalFormIfNeeded loads stored goal values")
        func populateGoalFormLoadsStoredGoalValues() {
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
                goalType: .lose,
                goalWeight: 1500,
                initialWeight: 1800,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.resetGoalForm()

            #expect(store.goalForm.goalType.value == GoalTypeSegment.losegainValue)
            #expect(store.goalForm.currentWeight.value.isEmpty == false)
            #expect(store.goalForm.goalWeight.value.isEmpty == false)
        }

        @Test("populateGoalFormIfNeeded formats imperial values and validators")
        func populateGoalFormFormatsImperialValuesAndValidators() {
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1",
                email: "lakshmi@example.com",
                firstName: "Lakshmi",
                lastName: "Priya",
                gender: .female,
                dob: "1992-03-04",
                zipcode: "560001",
                isActiveAccount: true,
                weightUnit: .lb,
                weightHeight: "681",
                activityLevel: .athlete,
                goalType: .lose,
                goalWeight: 1500,
                initialWeight: 1800,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.resetGoalForm()
            store.goalForm.goalWeight.value = "1000"
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            #expect(store.goalForm.currentWeight.value == String(format: "%.1f", ConversionTools.convertStoredToLbs(1800)))
            #expect(store.goalForm.getError(for: store.goalForm.goalWeight, isMetric: false) == FormErrorMessages.maxWeightLb)
        }

        @Test("handleGoalExit dirty form shows confirmation alert")
        func handleGoalExitDirtyFormShowsConfirmation() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            let router = Router<SettingsRoute>()
            store.goalForm.goalType.value = GoalType.maintain.rawValue
            store.goalForm.goalType.markAsDirty()

            store.handleGoalExit(router: router)

            #expect(notification.showAlertCalls == 1)
        }

        @Test("handleGoalExit dirty form resets and navigates on exit")
        func handleGoalExitDirtyFormResetsAndNavigatesOnExit() {
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
                goalType: .lose,
                goalWeight: 1500,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .goal)
            store.goalForm.goalWeight.value = "123.0"
            store.goalForm.goalWeight.markAsDirty()

            store.handleGoalExit(router: router)
            notification.alertData?.buttons.first?.action(nil)

            #expect(notification.showAlertCalls == 1)
            #expect(router.stack.isEmpty)
            #expect(store.goalForm.goalWeight.value.isEmpty == false)
        }

        @Test("handleGoalExit pristine navigates back immediately")
        func handleGoalExitPristineNavigatesBack() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            let router = Router<SettingsRoute>()
            router.navigate(to: .goal)

            store.handleGoalExit(router: router)

            #expect(router.stack.isEmpty)
        }

        @Test("handleGoalTypeChange marks form dirty and updates segment")
        func handleGoalTypeChangeMarksFormDirty() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.handleGoalTypeChange(.maintain)

            #expect(store.selectedSegment == .maintain)
            #expect(store.goalForm.goalType.value == GoalType.maintain.rawValue)
            #expect(store.goalForm.goalType.isDirty == true)
        }

        @Test("confirmDiscardGoalChanges returns true when pristine")
        func confirmDiscardGoalChangesReturnsTrueWhenPristine() async {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            let shouldLeave = await store.confirmDiscardGoalChanges()

            #expect(shouldLeave == true)
        }

        @Test("confirmDiscardGoalChanges returns true when user exits")
        func confirmDiscardGoalChangesReturnsTrueOnExit() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.goalForm.goalType.value = GoalType.maintain.rawValue
            store.goalForm.goalType.markAsDirty()

            let task = Task { await store.confirmDiscardGoalChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.first?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == true)
        }

        @Test("saveGoal invalid form does nothing")
        func saveGoalInvalidFormDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            let router = Router<SettingsRoute>()
            store.goalForm.goalType.value = GoalTypeSegment.losegainValue
            store.goalForm.currentWeight.value = ""
            store.goalForm.goalWeight.value = ""
            store.goalForm.validate()

            store.saveGoal(router: router)
            await Task.yield()

            #expect(accountService.createGoalCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("populateGoalFormIfNeeded preserves dirty edits")
        func populateGoalFormPreservesDirtyEdits() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            store.goalForm.goalWeight.value = "111.1"
            store.goalForm.goalWeight.markAsDirty()

            store.populateGoalFormIfNeeded()

            #expect(store.goalForm.goalWeight.value == "111.1")
        }

        @Test("resetGoalForm restores account goal values")
        func resetGoalFormRestoresAccountGoalValues() {
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
                goalType: .lose,
                goalWeight: 1500,
                isStreakOn: true,
                isWeightlessOn: true,
                weightlessWeight: 1550,
                shouldSendEntryNotifications: true,
                shouldSendWeightInEntryNotifications: true
            )
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            store.goalForm.goalWeight.value = "200.0"
            store.goalForm.goalWeight.markAsDirty()

            store.resetGoalForm()

            #expect(store.goalForm.goalWeight.value != "200.0")
            #expect(store.goalForm.isDirty == false)
        }

        @Test("confirmDiscardGoalChanges returns false when cancelled")
        func confirmDiscardGoalChangesReturnsFalseOnCancel() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.goalForm.goalType.value = GoalType.maintain.rawValue
            store.goalForm.goalType.markAsDirty()

            let task = Task { await store.confirmDiscardGoalChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.last?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == false)
        }

        @Test("saveGoal maintain uses latest entry weight and navigates back")
        func saveGoalMaintainUsesLatestEntryWeight() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.createGoalResult = .success(())
            let entryService = MockEntryService()
            entryService.getLatestEntryResult = .success(EntryTestFixtures.makeEntry(weight: 1720))
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                entryService: entryService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .goal)
            store.handleGoalTypeChange(.maintain)
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            store.saveGoal(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.createGoalCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(entryService.getLatestEntryCalls == 1)
            #expect(accountService.lastCreatedGoal?.goalType == .maintain)
            #expect(accountService.lastCreatedGoal?.initialWeight == 1720)
            #expect(router.stack.isEmpty)
            #expect(notification.showToastCalls == 1)
        }

        @Test("saveGoal success with scale sync success shows goal toast")
        func saveGoalSuccessWithScaleSyncSuccessShowsGoalToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.createGoalResult = .success(())
            let entryService = MockEntryService()
            entryService.getLatestEntryResult = .success(EntryTestFixtures.makeEntry(weight: 1720))
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success(["UPDATED"])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                entryService: entryService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .goal)
            store.handleGoalTypeChange(.maintain)
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            store.saveGoal(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.createGoalCalls == 1 &&
                bluetooth.updateUserProfileForR4ScalesCalls == 1 &&
                notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.goalSaved)
            #expect(notification.showAlertCalls == 0)
            #expect(router.stack.isEmpty)
        }

        @Test("saveGoal failure shows error toast")
        func saveGoalFailureShowsErrorToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.createGoalResult = .failure(AccountTestError.apiFailed)
            let entryService = MockEntryService()
            entryService.getLatestEntryResult = .success(nil)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                entryService: entryService
            )
            let router = Router<SettingsRoute>()
            store.handleGoalTypeChange(.maintain)
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            store.saveGoal(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.createGoalCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.title == ToastStrings.errorSettingGoal)
        }

        @Test("saveGoal user selection in progress shows pending alert")
        func saveGoalUserSelectionInProgressShowsPendingAlert() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.createGoalResult = .success(())
            let entryService = MockEntryService()
            entryService.getLatestEntryResult = .success(nil)
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .success([UserCreationResponse.userSelectionInProgress.rawValue])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                entryService: entryService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            store.handleGoalTypeChange(.maintain)
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            store.saveGoal(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.createGoalCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.showAlertCalls == 1)
            #expect(notification.showToastCalls == 0)
        }

        @Test("saveGoal lose flow uses current weight")
        func saveGoalLoseFlowUsesCurrentWeight() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.createGoalResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .goal)
            store.handleGoalTypeChange(.loseGain)
            store.goalForm.currentWeight.value = "180.0"
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.currentWeight.markAsDirty()
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            store.saveGoal(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.createGoalCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastCreatedGoal?.goalType == .lose)
            #expect(accountService.lastCreatedGoal?.initialWeight == ConversionTools.convertDisplayToStored(180.0, isMetric: true))
            #expect(accountService.lastCreatedGoal?.goalWeight == ConversionTools.convertDisplayToStored(150.0, isMetric: true))
        }

        @Test("saveGoal gain flow derives gain type")
        func saveGoalGainFlowDerivesGainType() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.createGoalResult = .success(())
            let bluetooth = MockBluetoothService()
            bluetooth.updateUserProfileForR4ScalesResult = .failure(.notImplemented)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                bluetoothService: bluetooth
            )
            let router = Router<SettingsRoute>()
            router.navigate(to: .goal)
            store.handleGoalTypeChange(.loseGain)
            store.goalForm.currentWeight.value = "150.0"
            store.goalForm.goalWeight.value = "180.0"
            store.goalForm.currentWeight.markAsDirty()
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()

            store.saveGoal(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.createGoalCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastCreatedGoal?.goalType == .gain)
        }

        @Test("handleGoalTypeChange lose gain marks entered weights dirty")
        func handleGoalTypeChangeLoseGainMarksEnteredWeightsDirty() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.currentWeight.value = "180.0"

            store.handleGoalTypeChange(.loseGain)

            #expect(store.goalForm.goalWeight.isDirty == true)
            #expect(store.goalForm.currentWeight.isDirty == true)
        }

        @Test("notifyGoalTypeChange posts notification")
        func notifyGoalTypeChangePostsNotification() async {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            var called = false
            let token = NotificationCenter.default.addObserver(
                forName: .goalTypeChanged,
                object: nil,
                queue: nil
            ) { _ in
                called = true
            }

            store.notifyGoalTypeChange()
            await Task.yield()
            NotificationCenter.default.removeObserver(token)

            #expect(called == true)
        }

        @Test("form validity helpers reflect current state")
        func formValidityHelpersReflectCurrentState() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            store.goalForm.goalType.value = GoalType.maintain.rawValue
            store.goalForm.goalWeight.value = "150.0"
            store.goalForm.goalWeight.markAsDirty()
            store.goalForm.validate()
            store.weightlessForm.isOn.value = true
            store.weightlessForm.isOn.markAsDirty()
            store.weightlessForm.weight.value = "1000"
            store.weightlessForm.validate()

            _ = store.isGoalFormValid
            _ = store.isGoalFormValid(focusedField: nil)

            #expect(store.isWeightLessFormValid == false)
        }

    }
}
