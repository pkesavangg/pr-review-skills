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
        accountService.activeAccount?.weightSettings?.weightUnit = .kg
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


    @Test("saveEntry time clamp for today: future time is adjusted and still saves")
    func saveEntryClampsFutureTimeToday() async {
        let (store, entryService, _, _) = makeSUT()

        store.manualEntryForm.weight.value = "160.0"
        store.manualEntryForm.date.value = Date()
        store.manualEntryForm.time.value = Calendar.current.date(byAdding: .hour, value: 2, to: Date()) ?? Date()

        await store.saveEntry()

        #expect(entryService.saveNewEntryCalls == 1)
    }

    @Test("weight validator switches with unit: kg max applied after refresh")
    func weightValidatorUsesKgMaxAfterRefresh() {
        let (store, _, _, accountService) = makeSUT()
        accountService.activeAccount?.weightSettings?.weightUnit = .kg

        store.refreshWeightUnit()
        store.manualEntryForm.weight.value = "451"
        store.manualEntryForm.weight.validate()

        #expect(store.weightUnit == .kg)
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
}

@MainActor
private func makeSUT() -> (EntryStore, MockEntryStoreEntryService, TestNotificationHelperService, MockAccountService) {
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
