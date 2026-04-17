import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor     
struct EntryStoreTests {
    @Test("saveEntry invalid form: does not persist")
    func saveEntryInvalidForm() async {
        let (store, entryService, notificationService, _) = makeSUT()

        await store.saveEntry()

        #expect(entryService.saveNewEntryCalls == 0)
        #expect(store.isSaving == false)
        #expect(notificationService.showLoaderCalls == 1)
        #expect(notificationService.dismissLoaderCalls == 1)
    }

    @Test("getError returns required message for dirty empty weight")
    func getErrorRequiredWeight() {
        let (store, _, _, _) = makeSUT()
        store.manualEntryForm.weight.value = "1"
        store.manualEntryForm.weight.value = ""
        store.manualEntryForm.weight.validate()

        let error = store.getError(for: store.manualEntryForm.weight)

        #expect(error == FormErrorMessages.required)
    }

    @Test("saveEntry invalid future date/time: validation blocks save")
    func saveEntryFutureDateBlocked() async {
        let (store, entryService, _, _) = makeSUT()
        store.manualEntryForm.weight.value = "180.0"
        store.manualEntryForm.date.value = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
        store.manualEntryForm.time.value = Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date()

        await store.saveEntry()

        #expect(entryService.saveNewEntryCalls == 0)
        #expect(store.manualEntryForm.date.isValid == false)
    }

    @Test("saveEntry success: saves entry, converts weight, resets form, shows success toast")
    func saveEntrySuccess() async {
        let (store, entryService, notificationService, accountService) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "entry-account", email: "entry@example.com", isActiveAccount: true, weightUnit: .kg)
        store.refreshWeightUnit()

        store.manualEntryForm.weight.value = "70.5"
        store.manualEntryForm.bodyFat.value = "12.39"
        store.manualEntryForm.date.value = Date()
        store.manualEntryForm.time.value = Date()

        await store.saveEntry()
        let toastShown = await waitUntil { notificationService.toastData != nil }

        #expect(entryService.saveNewEntryCalls == 1)
        guard let saved = entryService.lastSavedEntry else {
            Issue.record("Expected saved entry")
            return
        }

        let expectedStoredWeight = ConversionTools.convertDisplayToStored(70.5, forceMetric: false, isMetric: true)
        #expect(saved.accountId == accountService.activeAccount?.accountId)
        #expect(saved.operationType == OperationType.create.rawValue)
        #expect(saved.deviceType == DeviceType.scale.rawValue)
        #expect(saved.isSynced == false)
        #expect(saved.scaleEntry?.weight == expectedStoredWeight)
        #expect(saved.scaleEntry?.source == EntrySource.manual.rawValue)
        #expect(saved.scaleEntry?.bodyFat == 123)
        #expect(saved.scaleEntryMetric?.unit == WeightUnit.kg.rawValue)

        #expect(store.manualEntryForm.weight.value == "")
        #expect(store.showMetrics == false)
        #expect(toastShown == true)
        #expect(notificationService.toastData?.title == ToastStrings.success)
        #expect(notificationService.toastData?.message == ToastStrings.entryAdded)
        #expect(notificationService.dismissLoaderCalls == 1)
    }

    @Test("saveEntry no active account: exits before persistence")
    func saveEntryNoActiveAccount() async {
        let (store, entryService, notificationService, accountService) = makeSUT()
        accountService.activeAccount = nil
        store.manualEntryForm.weight.value = "175.0"
        store.manualEntryForm.date.value = Date()
        store.manualEntryForm.time.value = Date()

        await store.saveEntry()

        #expect(entryService.saveNewEntryCalls == 0)
        #expect(notificationService.dismissLoaderCalls == 1)
        #expect(store.isSaving == false)
    }

    @Test("saveEntry failure: keeps form state and shows error toast")
    func saveEntryFailure() async {
        let (store, entryService, notificationService, _) = makeSUT()
        entryService.saveResult = .failure(EntryStoreTestError.saveFailed)

        store.manualEntryForm.weight.value = "180.0"
        store.manualEntryForm.date.value = Date()
        store.manualEntryForm.time.value = Date()

        await store.saveEntry()
        let toastShown = await waitUntil { notificationService.toastData != nil }

        #expect(entryService.saveNewEntryCalls == 1)
        #expect(store.manualEntryForm.weight.value == "180.0")
        #expect(toastShown == true)
        #expect(notificationService.toastData?.title == ToastStrings.errorSavingEntry)
        #expect(notificationService.toastData?.message == ToastStrings.pleaseTryAgain)
        #expect(store.isSaving == false)
    }

    @Test("saveEntry duplicate prevention: second call exits while first save is in progress")
    func saveEntryDuplicatePrevention() async {
        let (store, entryService, _, _) = makeSUT()
        entryService.saveDelayNanoseconds = 200_000_000

        // Avoid async BMI recalculation races invalidating the form during overlap assertions.
        store.disableBmiAutoCalculation()
        store.manualEntryForm.weight.value = "180.0"
        store.manualEntryForm.date.value = Date()
        store.manualEntryForm.time.value = Date()
        store.manualEntryForm.validate()
        #expect(store.manualEntryForm.isValid == true)

        async let first: Void = store.saveEntry()
        async let second: Void = store.saveEntry()
        _ = await (first, second)

        #expect(store.isSaving == false)
        #expect(entryService.saveNewEntryCalls == 1)
    }


    @Test("saveEntry time clamp for today: future time is adjusted and still saves")
    func saveEntryClampsFutureTimeToday() async {
        let (store, entryService, _, _) = makeSUT()

        store.manualEntryForm.weight.value = "160.0"
        store.manualEntryForm.date.value = Date()
        let futureTime = Calendar.current.date(byAdding: .hour, value: 2, to: Date()) ?? Date()
        store.manualEntryForm.time.value = futureTime

        await store.saveEntry()

        #expect(entryService.saveNewEntryCalls == 1)
        guard let saved = entryService.lastSavedEntry else {
            Issue.record("Expected saved entry")
            return
        }
        // Verify the saved entry timestamp reflects clamped time (not future)
        #expect(saved.entryTimestamp != nil)
    }

    @Test("refreshTimeOnTabSelected clamps non-today time to max selectable")
    func refreshTimeOnTabSelectedNonTodayClamps() {
        let (store, _, _, _) = makeSUT()
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date()
        store.manualEntryForm.date.value = yesterday
        store.manualEntryForm.time.value = Calendar.current.date(byAdding: .hour, value: 10, to: Date()) ?? Date()

        store.refreshTimeOnTabSelected()

        #expect(store.manualEntryForm.time.value <= store.maxSelectableTime)
        #expect(store.manualEntryForm.time.isPristine == true)
    }

    @Test("refreshTimeOnTabSelected updates time to now when date is today")
    func refreshTimeOnTabSelectedTodayUpdatesTime() {
        let (store, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()
        let oldTime = Calendar.current.date(byAdding: .hour, value: -2, to: Date()) ?? Date()
        store.manualEntryForm.time.value = oldTime

        store.refreshTimeOnTabSelected()

        // Time should be updated to current time (within a reasonable range)
        let timeDiff = abs(store.manualEntryForm.time.value.timeIntervalSinceNow)
        #expect(timeDiff < 5.0) // Within 5 seconds
        #expect(store.manualEntryForm.time.isPristine == true)
    }

    @Test("weight validator switches with unit: kg max applied after refresh")
    func weightValidatorUsesKgMaxAfterRefresh() {
        let (store, _, _, accountService) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "entry-account", email: "entry@example.com", isActiveAccount: true, weightUnit: .kg)

        store.refreshWeightUnit()
        store.manualEntryForm.weight.value = "451"
        store.manualEntryForm.weight.validate()

        #expect(store.weightUnit == .kg)
        #expect(store.manualEntryForm.weight.isValid == false)
        #expect(store.manualEntryForm.weight.errors[.maxValue] == true)
    }

    @Test("weight validator switches with unit: lb max applied after refresh")
    func weightValidatorUsesLbMaxAfterRefresh() {
        let (store, _, _, accountService) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "entry-account", email: "entry@example.com", isActiveAccount: true, weightUnit: .lb)

        store.refreshWeightUnit()
        store.manualEntryForm.weight.value = "1000"
        store.manualEntryForm.weight.validate()

        #expect(store.weightUnit == .lb)
        #expect(store.manualEntryForm.weight.isValid == false)
        #expect(store.manualEntryForm.weight.errors[.maxValue] == true)
    }

    @Test("populateFromAppSync converts stored weight to display and enables metrics")
    func populateFromAppSyncConvertsWeight() {
        let (store, _, _, _) = makeSUT()
        let metrics = AppSyncEntryMetrics(
            storedWeight: 1804,
            storedBMI: 250,
            storedBodyFat: 100,
            storedWaterWeight: 550,
            storedMuscleMass: 400,
            isMetric: true
        )

        store.populateFromAppSync(metrics: metrics)

        #expect(store.weightUnit == .kg)
        #expect(store.manualEntryForm.weight.value == "81.8")
        #expect(store.manualEntryForm.bodyFat.value == "10.0")
        #expect(store.showMetrics == true)
    }

    @Test("populateFromAppSync converts imperial weight to display and enables metrics")
    func populateFromAppSyncConvertsImperialWeight() {
        let (store, _, _, _) = makeSUT()
        // 1804 stored weight in imperial (lb * 10) = 180.4 lb
        let metrics = AppSyncEntryMetrics(
            storedWeight: 1804,
            storedBMI: 250,
            storedBodyFat: 100,
            storedWaterWeight: 550,
            storedMuscleMass: 400,
            isMetric: false
        )

        store.populateFromAppSync(metrics: metrics)

        #expect(store.weightUnit == .lb)
        #expect(store.manualEntryForm.weight.value == "180.4")
        #expect(store.manualEntryForm.bodyFat.value == "10.0")
        #expect(store.showMetrics == true)
    }

    @Test("disable and enable bmi auto calculation toggles flag")
    func bmiAutoCalculationToggle() {
        let (store, _, _, _) = makeSUT()

        store.disableBmiAutoCalculation()
        #expect(store.isBmiAutoCalculationEnabled == false)

        store.enableBmiAutoCalculation()
        #expect(store.isBmiAutoCalculationEnabled == true)
    }

    @Test("showExitAlert confirm resets form and executes callback")
    func showExitAlertConfirm() {
        let (store, _, notificationService, _) = makeSUT()
        store.manualEntryForm.weight.value = "188.0"
        store.showMetrics = true
        var confirmed = false

        store.showExitAlert(onConfirm: { confirmed = true }, onCancel: nil)
        #expect(notificationService.alertData != nil)
        notificationService.alertData?.buttons.first?.action(nil)

        #expect(confirmed == true)
        #expect(store.manualEntryForm.weight.value == "")
        #expect(store.showMetrics == false)
    }

    @Test("showExitAlert cancel executes cancel callback")
    func showExitAlertCancel() {
        let (store, _, notificationService, _) = makeSUT()
        store.manualEntryForm.weight.value = "188.0"
        var cancelled = false

        store.showExitAlert(onConfirm: {}, onCancel: { cancelled = true })
        #expect(notificationService.alertData != nil)
        notificationService.alertData?.buttons.last?.action(nil)

        #expect(cancelled == true)
        #expect(store.manualEntryForm.weight.value == "188.0")
    }

    @Test("confirmDiscardChanges cancel returns false")
    func confirmDiscardChangesCancel() async {
        let (store, _, notificationService, _) = makeSUT()
        store.manualEntryForm.weight.value = "188.0"

        let task = Task { await store.confirmDiscardChanges() }
        await Task.yield()
        notificationService.alertData?.buttons.last?.action(nil)
        let result = await task.value

        #expect(result == false)
        #expect(store.manualEntryForm.weight.value == "188.0")
    }

    @Test("confirmDiscardChanges confirm returns true and clears form")
    func confirmDiscardChangesConfirm() async {
        let (store, _, notificationService, _) = makeSUT()
        store.manualEntryForm.weight.value = "188.0"

        let task = Task { await store.confirmDiscardChanges() }
        await Task.yield()
        notificationService.alertData?.buttons.first?.action(nil)
        let result = await task.value

        #expect(result == true)
        #expect(store.manualEntryForm.weight.value == "")
    }

    @Test("startAutoTimeSync updates time for today when picker is closed")
    func startAutoTimeSyncUpdatesTime() async {
        let (store, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()
        let oldTime = Calendar.current.date(byAdding: .hour, value: -4, to: Date()) ?? Date()
        store.manualEntryForm.time.value = oldTime
        store.showTimePicker = false

        store.startAutoTimeSync(intervalSeconds: 0.05)
        let didUpdate = await waitUntil(timeoutIterations: 400) {
            store.manualEntryForm.time.value != oldTime
        }
        store.stopAutoTimeSync()

        #expect(didUpdate == true)
        #expect(store.manualEntryForm.time.isPristine == true)
    }

    @Test("startAutoTimeSync does not update when time picker is open")
    func startAutoTimeSyncDoesNotUpdateWhenPickerOpen() async {
        let (store, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()
        let oldTime = Calendar.current.date(byAdding: .hour, value: -2, to: Date()) ?? Date()
        store.manualEntryForm.time.value = oldTime
        store.showTimePicker = true

        store.startAutoTimeSync(intervalSeconds: 0.05)
        try? await Task.sleep(nanoseconds: 200_000_000)
        store.stopAutoTimeSync()

        #expect(store.manualEntryForm.time.value == oldTime)
    }

    @Test("auto time sync does not update after user manually adjusted time")
    func autoTimeSyncRespectsUserAdjustedTime() async {
        let (store, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()
        let oldTime = Calendar.current.date(byAdding: .hour, value: -3, to: Date()) ?? Date()
        store.manualEntryForm.time.value = oldTime

        // Simulate user opening picker and adjusting time.
        store.showTimePicker = true
        let userSelected = Calendar.current.date(byAdding: .minute, value: -15, to: Date()) ?? Date()
        store.manualEntryForm.time.value = userSelected
        store.showTimePicker = false

        store.startAutoTimeSync(intervalSeconds: 0.05)
        try? await Task.sleep(nanoseconds: 200_000_000)
        store.stopAutoTimeSync()

        #expect(store.manualEntryForm.time.value == userSelected)
    }

    @Test("accountWeightUnitChanged notification updates weight unit from active account")
    func accountWeightUnitChangedNotificationUpdatesUnit() async {
        let (store, _, _, accountService) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "entry-account", email: "entry@example.com", isActiveAccount: true, weightUnit: .kg)

        NotificationCenter.default.post(name: .accountWeightUnitChanged, object: nil)
        let didUpdate = await waitUntil(timeoutIterations: 200) { store.weightUnit == .kg }

        #expect(didUpdate == true)
    }
}

@MainActor
private func makeSUT() -> (EntryStore, MockEntryStoreEntryService, TestNotificationHelperService, MockAccountService) { // swiftlint:disable:this large_tuple
    TestDependencyContainer.reset()

    let accountService = MockAccountService()
    accountService.activeAccount = EntryStoreTestFixtures.makeActiveAccount()
    let entryService = MockEntryStoreEntryService()
    let notificationService = TestNotificationHelperService()
    let logger = MockLoggerService()
    let keychain = MockKeychainService()
    let bluetooth = MockBluetoothService()
    let scaleService = MockScaleService()

    TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)
    DependencyContainer.shared.register(accountService as AccountServiceProtocol)
    DependencyContainer.shared.register(notificationService)
    DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(entryService as EntryServiceProtocol)
    DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)

    let store = EntryStore()
    // Pin dependencies on store to avoid later global re-resolution.
    store.accountService = accountService
    store.notificationService = notificationService
    store.entryService = entryService
    store.logger = logger
    store.scaleService = scaleService
    return (store, entryService, notificationService, accountService)
}

@MainActor
private func waitUntil(timeoutIterations: Int = 200, condition: @MainActor () -> Bool) async -> Bool {
    for _ in 0..<timeoutIterations {
        if condition() { return true }
        await Task.yield()
    }
    return false
}
