import Foundation
import Testing
@testable import meApp

extension SettingsStoreTests {
    @Suite("Weightless")
    @MainActor
    struct Weightless {
        @Test("populateWeightlessFormIfNeeded clears values when there is no active account")
        func populateWeightlessFormClearsValuesWithoutAccount() {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)
            store.weightlessForm.isOn.value = true
            store.weightlessForm.weight.value = "123.4"

            store.populateWeightlessFormIfNeeded()

            #expect(store.weightlessForm.isOn.value == false)
            #expect(store.weightlessForm.weight.value == "")
        }

        @Test("populateWeightlessFormIfNeeded turns off toggle when weight is missing")
        func populateWeightlessFormTurnsOffWhenWeightMissing() {
            let account = SettingsStoreTestFixtures.makeAccount()
            account.weightlessSettings?.isWeightlessOn = true
            account.weightlessSettings?.weightlessWeight = nil
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.resetWeightlessForm()

            #expect(store.weightlessForm.isOn.value == false)
            #expect(store.weightlessForm.weight.value == "")
        }

        @Test("populateWeightlessFormIfNeeded preserves dirty edits")
        func populateWeightlessFormPreservesDirtyEdits() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            store.weightlessForm.weight.value = "222.2"
            store.weightlessForm.weight.markAsDirty()

            store.populateWeightlessFormIfNeeded()

            #expect(store.weightlessForm.weight.value == "222.2")
        }

        @Test("populateWeightlessFormIfNeeded formats imperial weight")
        func populateWeightlessFormFormatsImperialWeight() {
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            account.weightlessSettings?.weightlessWeight = 1550
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.resetWeightlessForm()

            #expect(store.weightlessForm.isOn.value == true)
            #expect(store.weightlessForm.weight.value == String(format: "%.1f", ConversionTools.convertStoredToLbs(1550)))
        }

        @Test("populateWeightlessFormIfNeeded applies imperial max validator")
        func populateWeightlessFormAppliesImperialMaxValidator() {
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            store.resetWeightlessForm()
            store.weightlessForm.isOn.value = true
            store.weightlessForm.weight.value = "1000"
            store.weightlessForm.weight.markAsDirty()
            store.weightlessForm.validate()

            #expect(store.weightlessForm.getWeightError(for: store.weightlessForm.weight, unit: .lb) == FormErrorMessages.maxWeightLb)
        }

        @Test("resetWeightlessForm restores account values")
        func resetWeightlessFormRestoresAccountValues() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            let expected = String(format: "%.1f", ConversionTools.convertStoredToKg(1550))
            store.weightlessForm.isOn.value = false
            store.weightlessForm.weight.value = ""
            store.weightlessForm.isOn.markAsDirty()

            store.resetWeightlessForm()

            #expect(store.weightlessForm.isOn.value == true)
            #expect(store.weightlessForm.weight.value == expected)
            #expect(store.weightlessForm.isDirty == false)
        }

        @Test("saveWeightless unchanged values navigates back without request")
        func saveWeightlessUnchangedValuesNavigatesBack() async {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .weightless)
            store.populateWeightlessFormIfNeeded()
            store.weightlessForm.isOn.markAsDirty()

            store.saveWeightless(router: router)
            await Task.yield()

            #expect(accountService.updateWeightlessCalls == 0)
            #expect(router.stack.isEmpty)
        }

        @Test("saveWeightless success updates settings and navigates back")
        func saveWeightlessSuccessUpdatesSettings() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateWeightlessResult = .success(account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .weightless)
            store.weightlessForm.isOn.value = false
            store.weightlessForm.isOn.markAsDirty()
            store.weightlessForm.weight.value = ""

            store.saveWeightless(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateWeightlessCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastUpdatedWeightlessOn == false)
            #expect(router.stack.isEmpty)
            #expect(notification.showToastCalls == 1)
        }

        @Test("saveWeightless on state converts entered metric weight")
        func saveWeightlessOnStateConvertsMetricWeight() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(unit: .kg)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateWeightlessResult = .success(account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .weightless)
            store.weightlessForm.isOn.value = true
            store.weightlessForm.isOn.markAsDirty()
            store.weightlessForm.weight.value = "72.5"
            store.weightlessForm.weight.markAsDirty()

            store.saveWeightless(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateWeightlessCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastUpdatedWeightlessOn == true)
            #expect(accountService.lastUpdatedWeightlessWeight == Double(ConversionTools.convertDisplayToStored(72.5, isMetric: true)))
        }

        @Test("saveWeightless without active account does nothing")
        func saveWeightlessWithoutActiveAccountDoesNothing() async {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)
            let router = Router<SettingsRoute>()
            store.weightlessForm.isOn.value = true
            store.weightlessForm.isOn.markAsDirty()
            store.weightlessForm.weight.value = "72.5"
            store.weightlessForm.weight.markAsDirty()

            store.saveWeightless(router: router)
            await Task.yield()

            #expect(accountService.updateWeightlessCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("saveWeightless invalid form does nothing")
        func saveWeightlessInvalidFormDoesNothing() async {
            let accountService = MockAccountService()
            let (store, notification, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            let router = Router<SettingsRoute>()
            store.weightlessForm.isOn.value = true
            store.weightlessForm.weight.value = "1000"

            store.saveWeightless(router: router)
            await Task.yield()

            #expect(accountService.updateWeightlessCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("saveWeightless failure shows error toast")
        func saveWeightlessFailureShowsErrorToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.updateWeightlessResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.weightlessForm.isOn.value = false
            store.weightlessForm.isOn.markAsDirty()
            store.weightlessForm.weight.value = ""

            store.saveWeightless(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updateWeightlessCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.title == ToastStrings.errorUpdatingWeightless)
        }

        @Test("handleWeightlessExit dirty form shows confirmation alert")
        func handleWeightlessExitDirtyFormShowsConfirmation() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            let router = Router<SettingsRoute>()
            store.weightlessForm.isOn.value = true
            store.weightlessForm.isOn.markAsDirty()

            store.handleWeightlessExit(router: router)

            #expect(notification.showAlertCalls == 1)
        }

        @Test("handleWeightlessExit dirty form resets and navigates on exit")
        func handleWeightlessExitDirtyFormResetsAndNavigatesOnExit() {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .weightless)
            store.weightlessForm.isOn.value = false
            store.weightlessForm.isOn.markAsDirty()

            store.handleWeightlessExit(router: router)
            notification.alertData?.buttons.first?.action(nil)

            #expect(notification.showAlertCalls == 1)
            #expect(router.stack.isEmpty)
            #expect(store.weightlessForm.isOn.value == true)
        }

        @Test("handleWeightlessExit pristine navigates back immediately")
        func handleWeightlessExitPristineNavigatesBack() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            let router = Router<SettingsRoute>()
            router.navigate(to: .weightless)

            store.handleWeightlessExit(router: router)

            #expect(router.stack.isEmpty)
        }

        @Test("confirmDiscardWeightlessChanges returns false when cancelled")
        func confirmDiscardWeightlessChangesReturnsFalseOnCancel() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.weightlessForm.isOn.value = true
            store.weightlessForm.isOn.markAsDirty()

            let task = Task { await store.confirmDiscardWeightlessChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.last?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == false)
        }

        @Test("confirmDiscardWeightlessChanges returns true when user exits")
        func confirmDiscardWeightlessChangesReturnsTrueOnExit() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.weightlessForm.isOn.value = true
            store.weightlessForm.isOn.markAsDirty()

            let task = Task { await store.confirmDiscardWeightlessChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.first?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == true)
        }

        @Test("confirmDiscardWeightlessChanges returns true when pristine")
        func confirmDiscardWeightlessChangesReturnsTrueWhenPristine() async {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            let shouldLeave = await store.confirmDiscardWeightlessChanges()

            #expect(shouldLeave == true)
        }
    }
}
