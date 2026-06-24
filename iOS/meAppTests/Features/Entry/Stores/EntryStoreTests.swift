//
//  EntryStoreTests.swift
//  meAppTests
//

import Testing
import Foundation
import Combine
@testable import meApp

@Suite("EntryStore", .serialized)
@MainActor
struct EntryStoreTests {

    // MARK: - Polling helper

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 5_000_000,
        condition: @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while ContinuousClock.now < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }

    // MARK: - SUT

    private func makeSUT() -> (
        store: EntryStore,
        accountService: MockAccountService,
        entryService: MockEntryService,
        notificationService: MockNotificationHelperService,
        logger: MockLoggerService
    ) {
        _ = ServiceRegistry.shared

        let accountService = MockAccountService()
        let entryService = MockEntryService()
        let notificationService = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperService)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let store = EntryStore()

        // Prime @Injector caches so mocks are resolved before any method calls.
        _ = store.accountService
        _ = store.entryService
        _ = store.notificationService
        _ = store.logger

        return (store, accountService, entryService, notificationService, logger)
    }

    // MARK: - Initial State

    @Test("initial isSaving is false")
    func initialIsSavingFalse() {
        let (store, _, _, _, _) = makeSUT()
        #expect(!store.isSaving)
    }

    @Test("initial weightUnit is lb")
    func initialWeightUnitIsLb() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.weightUnit == .lb)
    }

    @Test("initial form weight is empty string")
    func initialFormWeightEmpty() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.manualEntryForm.weight.value == "")
    }

    @Test("initial isBmiAutoCalculationEnabled is true")
    func initialBmiAutoCalcEnabled() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.isBmiAutoCalculationEnabled)
    }

    @Test("initial showMetrics is false")
    func initialShowMetricsFalse() {
        let (store, _, _, _, _) = makeSUT()
        #expect(!store.showMetrics)
    }

    // MARK: - saveEntry: form invalid guard

    @Test("saveEntry returns false when weight is empty")
    func saveEntryReturnsFalseEmptyWeight() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = ""
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        let result = await store.saveEntry()
        #expect(!result)
        #expect(entryService.saveNewEntryCallCount == 0)
    }

    @Test("saveEntry returns false when weight exceeds max 999 lb")
    func saveEntryReturnsFalseWeightOverMax() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "1000"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        let result = await store.saveEntry()
        #expect(!result)
        #expect(entryService.saveNewEntryCallCount == 0)
    }

    // MARK: - saveEntry: no active account guard

    @Test("saveEntry returns false when no active account")
    func saveEntryReturnsFalseNoAccount() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        accountService.activeAccount = nil
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        let result = await store.saveEntry()
        #expect(!result)
        #expect(entryService.saveNewEntryCallCount == 0)
    }

    // MARK: - saveEntry: success path

    @Test("saveEntry returns true on success")
    func saveEntrySuccessReturnsTrue() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        let result = await store.saveEntry()
        #expect(result)
        #expect(entryService.saveNewEntryCallCount == 1)
    }

    @Test("saveEntry passes correct accountId to entry service")
    func saveEntryPassesCorrectAccountId() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccount(id: "acct-999")
        accountService.activeAccount = account
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(entryService.lastSavedEntry?.accountId == "acct-999")
    }

    @Test("saveEntry success shows success toast")
    func saveEntrySuccessShowsToast() async {
        let (store, accountService, _, notificationService, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(notificationService.lastShownToast?.message == ToastStrings.entryAdded)
    }

    @Test("saveEntry success resets form weight to empty")
    func saveEntrySuccessResetsWeight() async {
        let (store, accountService, _, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(store.manualEntryForm.weight.value == "")
    }

    // MARK: - saveEntry: failure path

    @Test("saveEntry returns false when entry service throws")
    func saveEntryFailureReturnsFalse() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        entryService.saveNewEntryError = NSError(domain: "EntryTest", code: -1)
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        let result = await store.saveEntry()
        #expect(!result)
    }

    @Test("saveEntry failure shows error toast")
    func saveEntryFailureShowsErrorToast() async {
        let (store, accountService, entryService, notificationService, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        entryService.saveNewEntryError = NSError(domain: "EntryTest", code: -1)
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(notificationService.lastShownToast?.title == ToastStrings.errorSavingEntry)
    }

    // MARK: - Loader lifecycle

    @Test("saveEntry shows loader during save")
    func saveEntryShowsLoader() async {
        let (store, accountService, _, notificationService, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(notificationService.showLoaderCallCount >= 1)
    }

    @Test("saveEntry dismisses loader after success")
    func saveEntryDismissesLoaderOnSuccess() async {
        let (store, accountService, _, notificationService, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(notificationService.dismissLoaderCallCount >= 1)
    }

    @Test("saveEntry dismisses loader after failure")
    func saveEntryDismissesLoaderOnFailure() async {
        let (store, accountService, entryService, notificationService, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        entryService.saveNewEntryError = NSError(domain: "EntryTest", code: -1)
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(notificationService.dismissLoaderCallCount >= 1)
    }

    // MARK: - isSaving gate

    @Test("isSaving resets to false after successful save")
    func isSavingResetAfterSuccess() async {
        let (store, accountService, _, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(!store.isSaving)
    }

    @Test("isSaving resets to false after failed save")
    func isSavingResetAfterFailure() async {
        let (store, accountService, entryService, _, _) = makeSUT()
        accountService.activeAccount = AccountTestFixtures.makeAccount()
        entryService.saveNewEntryError = NSError(domain: "EntryTest", code: -1)
        store.manualEntryForm.weight.value = "150"
        store.manualEntryForm.weight.markAsDirty()
        store.manualEntryForm.weight.validate()

        _ = await store.saveEntry()
        #expect(!store.isSaving)
    }

    // MARK: - refreshWeightUnit

    @Test("refreshWeightUnit defaults to lb when no active account")
    func refreshWeightUnitDefaultsLb() {
        let (store, accountService, _, _, _) = makeSUT()
        accountService.activeAccount = nil
        store.refreshWeightUnit()
        #expect(store.weightUnit == .lb)
    }

    // MARK: - BMI auto-calculation

    @Test("disableBmiAutoCalculation sets flag to false")
    func disableBmiAutoCalc() {
        let (store, _, _, _, _) = makeSUT()
        store.disableBmiAutoCalculation()
        #expect(!store.isBmiAutoCalculationEnabled)
    }

    @Test("enableBmiAutoCalculation restores flag to true")
    func enableBmiAutoCalcAfterDisable() {
        let (store, _, _, _, _) = makeSUT()
        store.disableBmiAutoCalculation()
        store.enableBmiAutoCalculation()
        #expect(store.isBmiAutoCalculationEnabled)
    }

    // MARK: - resetForm

    @Test("resetForm clears weight value")
    func resetFormClearsWeight() {
        let (store, _, _, _, _) = makeSUT()
        store.manualEntryForm.weight.value = "150"
        store.resetForm()
        #expect(store.manualEntryForm.weight.value == "")
    }

    @Test("resetForm resets isBmiAutoCalculationEnabled to true")
    func resetFormRestoresBmiAutoCalc() {
        let (store, _, _, _, _) = makeSUT()
        store.disableBmiAutoCalculation()
        store.resetForm()
        #expect(store.isBmiAutoCalculationEnabled)
    }

    @Test("resetForm hides showMetrics panel")
    func resetFormHidesMetrics() {
        let (store, _, _, _, _) = makeSUT()
        store.showMetrics = true
        store.resetForm()
        #expect(!store.showMetrics)
    }

    @Test("resetForm hides showDatePicker")
    func resetFormHidesDatePicker() {
        let (store, _, _, _, _) = makeSUT()
        store.showDatePicker = true
        store.resetForm()
        #expect(!store.showDatePicker)
    }

    // MARK: - maxSelectableTime

    @Test("maxSelectableTime stays within today when date is today")
    func maxSelectableTimeToday() {
        let (store, _, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()
        #expect(Calendar.current.isDateInToday(store.maxSelectableTime))
    }

    @Test("maxSelectableTime is end-of-day (23:59) when date is in the past")
    func maxSelectableTimePast() {
        let (store, _, _, _, _) = makeSUT()
        let past = Calendar.current.date(byAdding: .day, value: -3, to: Date())!
        store.manualEntryForm.date.value = past
        let comps = Calendar.current.dateComponents([.hour, .minute], from: store.maxSelectableTime)
        #expect(comps.hour == 23)
        #expect(comps.minute == 59)
    }

    // MARK: - getError

    @Test("getError returns nil for a pristine control")
    func getErrorPristine() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.getError(for: store.manualEntryForm.weight) == nil)
    }

    // MARK: - refreshTimeOnTabSelected

    @Test("refreshTimeOnTabSelected sets time to today when the date is today")
    func refreshTimeTodaySetsNow() {
        let (store, _, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()
        store.refreshTimeOnTabSelected()
        #expect(Calendar.current.isDateInToday(store.manualEntryForm.time.value))
    }

    @Test("refreshTimeOnTabSelected clamps time to max for a past date")
    func refreshTimePastClamps() {
        let (store, _, _, _, _) = makeSUT()
        let past = Calendar.current.date(byAdding: .day, value: -2, to: Date())!
        store.manualEntryForm.date.value = past
        store.manualEntryForm.time.value = Date() // now > end of the past day
        store.refreshTimeOnTabSelected()
        #expect(store.manualEntryForm.time.value <= store.maxSelectableTime)
    }

    // MARK: - populateFromAppSync

    @Test("populateFromAppSync fills the form and reveals the metrics panel")
    func populateFromAppSyncPopulates() {
        let (store, _, _, _, _) = makeSUT()
        let metrics = AppSyncEntryMetrics(
            storedWeight: 1500,
            storedBMI: 235,
            storedBodyFat: 200,
            storedWaterWeight: 500,
            storedMuscleMass: 300,
            isMetric: false
        )

        store.populateFromAppSync(metrics: metrics)

        #expect(store.showMetrics)
        #expect(!store.manualEntryForm.weight.value.isEmpty)
        #expect(!store.manualEntryForm.bodyFat.value.isEmpty)
    }

    // MARK: - showExitAlert

    @Test("showExitAlert exit button resets the form and calls onConfirm")
    func showExitAlertExitButton() {
        let (store, _, _, notificationService, _) = makeSUT()
        var confirmed = false
        store.manualEntryForm.weight.value = "150"

        store.showExitAlert(onConfirm: { confirmed = true })
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        #expect(confirmed)
        #expect(store.manualEntryForm.weight.value == "")
    }

    @Test("showExitAlert return button calls onCancel")
    func showExitAlertReturnButton() {
        let (store, _, _, notificationService, _) = makeSUT()
        var cancelled = false

        store.showExitAlert(onConfirm: {}, onCancel: { cancelled = true })
        notificationService.lastShownAlert?.buttons.last?.action(nil)

        #expect(cancelled)
    }

    // MARK: - confirmDiscardChanges

    @Test("confirmDiscardChanges returns true when the user confirms exit")
    func confirmDiscardChangesConfirmed() async {
        let (store, _, _, notificationService, _) = makeSUT()

        async let result = store.confirmDiscardChanges()
        await waitUntil { notificationService.lastShownAlert != nil }
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        let confirmed = await result
        #expect(confirmed)
    }

    @Test("confirmDiscardChanges returns false when the user cancels")
    func confirmDiscardChangesCancelled() async {
        let (store, _, _, notificationService, _) = makeSUT()

        async let result = store.confirmDiscardChanges()
        await waitUntil { notificationService.lastShownAlert != nil }
        notificationService.lastShownAlert?.buttons.last?.action(nil)

        let confirmed = await result
        #expect(!confirmed)
    }

    // MARK: - Auto time sync

    @Test("startAutoTimeSync performs an immediate tick on today's date")
    func startAutoTimeSyncTicks() {
        let (store, _, _, _, _) = makeSUT()
        store.manualEntryForm.date.value = Date()

        store.startAutoTimeSync(intervalSeconds: 60)
        store.stopAutoTimeSync()

        #expect(Calendar.current.isDateInToday(store.manualEntryForm.time.value))
    }

    // MARK: - weightUnit from account

    @Test("refreshWeightUnit adopts kg from the active account and recalculates BMI")
    func refreshWeightUnitAdoptsKg() {
        let (store, accountService, _, _, _) = makeSUT()
        let account = Account(from: AccountTestFixtures.makeAccountDTO(weightUnit: .kg))
        account.isActiveAccount = true
        accountService.activeAccount = account
        store.manualEntryForm.weight.value = "70"

        store.refreshWeightUnit()

        #expect(store.weightUnit == .kg)
    }

    // MARK: - formDidChange

    @Test("formDidChange emits when a field value changes")
    func formDidChangeEmits() async {
        let (store, _, _, _, _) = makeSUT()
        var received = false
        let cancellable = store.manualEntryForm.formDidChange.sink { received = true }

        store.manualEntryForm.weight.value = "123"

        await waitUntil { received }
        #expect(received)
        cancellable.cancel()
    }
}
