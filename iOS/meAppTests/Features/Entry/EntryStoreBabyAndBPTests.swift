import Foundation
@testable import meApp
import Testing

/// Coverage for EntryStore's baby-scale and blood-pressure entry paths plus
/// form reset/validation helpers (the manual-entry variants beyond weight).
@Suite(.serialized)
@MainActor
struct EntryStoreBabyAndBPTests {

    // MARK: - SUT

    // Test factory return; labeled tuple is clearer than a one-off SUT struct.
    // swiftlint:disable:next large_tuple
    private func makeSUT() -> (
        store: EntryStore,
        entryService: MockEntryStoreEntryService,
        notificationService: TestNotificationHelperService,
        accountService: MockAccountService,
        productTypeStore: MockProductTypeStore
    ) {
        TestDependencyContainer.reset()

        let accountService = MockAccountService()
        accountService.activeAccount = EntryStoreTestFixtures.makeActiveAccount()
        let entryService = MockEntryStoreEntryService()
        let notificationService = TestNotificationHelperService()
        let logger = MockLoggerService()
        let keychain = MockKeychainService()
        let bluetooth = MockBluetoothService()
        let deviceService = MockScaleService()
        let productTypeStore = MockProductTypeStore()

        TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(notificationService)
        DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(deviceService as PairedDeviceServiceProtocol)

        let store = EntryStore()
        store.accountService = accountService
        store.notificationService = notificationService
        store.entryService = entryService
        store.logger = logger
        store.deviceService = deviceService
        store.productTypeStore = productTypeStore
        return (store, entryService, notificationService, accountService, productTypeStore)
    }

    private func makeBabyProfile(id: String = "baby-1", birthday: Date? = nil) -> BabyProfile {
        BabyProfile(id: id, name: "Aria", birthday: birthday)
    }

    private func fillBabyLbsOz(_ store: EntryStore) {
        store.babyWeightUnit = .lbsOz
        store.babyLengthUnit = .inches
        store.babyForm.pounds.value = "8"
        store.babyForm.ounces.value = "4"
        store.babyForm.inches.value = "20"
        store.babyForm.date.value = Date()
        store.babyForm.time.value = Date()
    }

    // MARK: - babyEntryMinimumDate (MOB-1567)

    @Test("babyEntryMinimumDate: returns the selected baby's birthday when set")
    func babyEntryMinimumDateUsesBirthday() {
        let (store, _, _, _, productTypeStore) = makeSUT()
        let birthday = Calendar.current.date(byAdding: .month, value: -3, to: Date()) ?? Date()
        productTypeStore.select(.baby(profile: makeBabyProfile(birthday: birthday)))

        #expect(store.babyEntryMinimumDate == birthday)
    }

    @Test("babyEntryMinimumDate: falls back to the default when no birthday is set")
    func babyEntryMinimumDateFallsBackWhenNoBirthday() {
        let (store, _, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: makeBabyProfile(birthday: nil)))

        #expect(store.babyEntryMinimumDate == AppConstants.Entry.babyDatePickerMinimum)
    }

    @Test("babyEntryMinimumDate: ignores a future birthday so the picker range stays valid")
    func babyEntryMinimumDateIgnoresFutureBirthday() {
        let (store, _, _, _, productTypeStore) = makeSUT()
        let future = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
        productTypeStore.select(.baby(profile: makeBabyProfile(birthday: future)))

        #expect(store.babyEntryMinimumDate == AppConstants.Entry.babyDatePickerMinimum)
    }

    @Test("babyEntryMinimumDate: falls back to the default for a non-baby selection")
    func babyEntryMinimumDateNonBabySelection() {
        let (store, _, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.myWeight)

        #expect(store.babyEntryMinimumDate == AppConstants.Entry.babyDatePickerMinimum)
    }

    // MARK: - saveBabyEntry

    @Test("saveBabyEntry success (lbs/oz): persists, converts weight/length, resets form")
    func saveBabyEntrySuccessLbsOz() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: makeBabyProfile()))
        fillBabyLbsOz(store)

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 1)
        #expect(entryService.lastBabyEntry?.babyId == "baby-1")
        #expect((entryService.lastBabyEntry?.weight ?? 0) > 0)
        #expect((entryService.lastBabyEntry?.length ?? 0) > 0)
        #expect(store.babyForm.pounds.value.isEmpty)
    }

    @Test("saveBabyEntry success (kg/cm): persists with metric units")
    func saveBabyEntrySuccessKgCm() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: makeBabyProfile(id: "baby-kg")))
        store.babyWeightUnit = .kg
        store.babyLengthUnit = .cm
        store.babyForm.kg.value = "3.5"
        store.babyForm.cm.value = "52"
        store.babyForm.date.value = Date()

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 1)
        #expect(entryService.lastBabyEntry?.babyId == "baby-kg")
    }

    @Test("saveBabyEntry success (lb): persists with pound-only unit")
    func saveBabyEntrySuccessLb() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: makeBabyProfile(id: "baby-lb")))
        store.babyWeightUnit = .lb
        store.babyLengthUnit = .inches
        store.babyForm.lb.value = "9"
        store.babyForm.inches.value = "21"
        store.babyForm.date.value = Date()

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 1)
    }

    @Test("saveBabyEntry invalid form: does not persist")
    func saveBabyEntryInvalidForm() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: makeBabyProfile()))
        // Form left empty -> invalid

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 0)
    }

    @Test("saveBabyEntry with non-baby selection: does not persist")
    func saveBabyEntryNonBabySelection() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.myWeight)
        fillBabyLbsOz(store)

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 0)
    }

    @Test("saveBabyEntry pending baby selection: does not persist")
    func saveBabyEntryPendingSelection() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: BabyProfile(id: BabyProfile.pendingSelectionId, name: "")))
        fillBabyLbsOz(store)

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 0)
    }

    @Test("saveBabyEntry zero weight: skips persistence")
    func saveBabyEntryZeroWeight() async {
        let (store, entryService, _, _, productTypeStore) = makeSUT()
        productTypeStore.select(.baby(profile: makeBabyProfile()))
        store.babyWeightUnit = .lbsOz
        store.babyLengthUnit = .inches
        store.babyForm.pounds.value = "0"
        store.babyForm.ounces.value = "0"
        store.babyForm.inches.value = "20"
        store.babyForm.date.value = Date()

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 0)
    }

    @Test("saveBabyEntry failure: shows error toast and does not reset form")
    func saveBabyEntryFailure() async {
        let (store, entryService, notificationService, _, productTypeStore) = makeSUT()
        entryService.createBabyEntryError = EntryStoreBabyTestError.generic
        productTypeStore.select(.baby(profile: makeBabyProfile()))
        fillBabyLbsOz(store)

        await store.saveBabyEntry()

        #expect(entryService.createBabyEntryCalls == 1)
        #expect(notificationService.toastData?.title == ToastStrings.errorSavingEntry)
        #expect(store.babyForm.pounds.value == "8")
    }

    // MARK: - saveBPEntry

    private func fillBP(_ store: EntryStore) {
        store.bpForm.systolic.value = "120"
        store.bpForm.diastolic.value = "80"
        store.bpForm.pulse.value = "70"
        store.bpForm.date.value = Date()
        store.bpForm.time.value = Date()
    }

    @Test("saveBPEntry success: persists DTO and resets form")
    func saveBPEntrySuccess() async {
        let (store, entryService, _, _, _) = makeSUT()
        fillBP(store)

        await store.saveBPEntry()

        #expect(entryService.createBpmEntryCalls == 1)
        #expect(entryService.lastBpmEntry?.systolic == 120)
        #expect(entryService.lastBpmEntry?.diastolic == 80)
        #expect(entryService.lastBpmEntry?.pulse == 70)
        #expect(store.bpForm.systolic.value.isEmpty)
    }

    @Test("saveBPEntry invalid form: does not persist")
    func saveBPEntryInvalid() async {
        let (store, entryService, _, _, _) = makeSUT()
        // Empty form -> invalid

        await store.saveBPEntry()

        #expect(entryService.createBpmEntryCalls == 0)
    }

    @Test("saveBPEntry failure: shows error toast")
    func saveBPEntryFailure() async {
        let (store, entryService, notificationService, _, _) = makeSUT()
        entryService.createBpmEntryError = EntryStoreBabyTestError.generic
        fillBP(store)

        await store.saveBPEntry()

        #expect(entryService.createBpmEntryCalls == 1)
        #expect(notificationService.toastData?.title == ToastStrings.errorSavingEntry)
    }

    // MARK: - Validation & errors

    @Test("isBabyFormValid: true when filled, false when empty")
    func isBabyFormValidity() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.isBabyFormValid == false)

        fillBabyLbsOz(store)
        #expect(store.isBabyFormValid == true)
    }

    // Baby-CREATE gating (MOB-1548): SAVE gates on weight only. In lb/oz mode the pounds
    // field is the required one — ounces and length are optional, so pounds-empty must be
    // invalid even when ounces and/or length are filled. This supersedes the earlier
    // MOB-1172 "weight OR length" relaxation.
    @Test("isBabyFormValid: pounds required; ounces-only, length-only, and both-empty are invalid")
    func isBabyFormValidGatesOnPounds() {
        let (store, _, _, _, _) = makeSUT()
        store.babyWeightUnit = .lbsOz
        store.babyLengthUnit = .inches
        store.babyForm.date.value = Date()
        store.babyForm.time.value = Date()

        // Both empty -> invalid.
        #expect(store.isBabyFormValid == false)

        // Ounces only (pounds empty), e.g. "0 lb 0.4 oz" -> invalid.
        store.babyForm.ounces.value = "0.4"
        #expect(store.isBabyFormValid == false)

        // Length only (pounds empty) -> invalid.
        store.babyForm.ounces.value = ""
        store.babyForm.inches.value = "20"
        #expect(store.isBabyFormValid == false)

        // Ounces + length, pounds empty -> still invalid.
        store.babyForm.ounces.value = "0.4"
        #expect(store.isBabyFormValid == false)

        // Pounds present -> valid (ounces/length optional and are allowed alongside).
        store.babyForm.pounds.value = "8"
        #expect(store.isBabyFormValid == true)

        // Pounds only, no ounces/length -> valid.
        store.babyForm.ounces.value = ""
        store.babyForm.inches.value = ""
        #expect(store.isBabyFormValid == true)
    }

    @Test("babyWeightError reflects the selected weight unit")
    func babyWeightErrorByUnit() {
        let (store, _, _, _, _) = makeSUT()

        store.babyWeightUnit = .kg
        store.babyForm.kg.value = "0.1"   // below min
        store.babyForm.kg.markAsTouched()
        store.babyForm.kg.validate()
        #expect(store.babyWeightError != nil)

        store.babyWeightUnit = .lb
        store.babyForm.lb.value = "0.1"
        store.babyForm.lb.markAsTouched()
        store.babyForm.lb.validate()
        #expect(store.babyWeightError != nil)

        store.babyWeightUnit = .lbsOz
        store.babyForm.ounces.value = "99"  // above max 15.9
        store.babyForm.ounces.markAsTouched()
        store.babyForm.ounces.validate()
        #expect(store.babyWeightError != nil)
    }

    @Test("babyLengthError reflects the selected length unit")
    func babyLengthErrorByUnit() {
        let (store, _, _, _, _) = makeSUT()

        store.babyLengthUnit = .cm
        store.babyForm.cm.value = "999"  // above max
        store.babyForm.cm.markAsTouched()
        store.babyForm.cm.validate()
        #expect(store.babyLengthError != nil)

        store.babyLengthUnit = .inches
        store.babyForm.inches.value = "999"
        store.babyForm.inches.markAsTouched()
        store.babyForm.inches.validate()
        #expect(store.babyLengthError != nil)
    }

    @Test("getBabyError returns nil for a clean control")
    func getBabyErrorClean() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.getBabyError(for: store.babyForm.pounds) == nil)
    }

    @Test("getBPError and getBPWarning are callable for BP controls")
    func bpErrorAndWarning() {
        let (store, _, _, _, _) = makeSUT()
        store.bpForm.systolic.value = "300"
        store.bpForm.systolic.markAsTouched()
        store.bpForm.systolic.validate()

        // Just exercising the accessors; values depend on validators/warnings.
        _ = store.getBPError(for: store.bpForm.systolic)
        _ = store.getBPWarning(for: store.bpForm.systolic)
        #expect(store.bpForm.systolic.value == "300")
    }

    // MARK: - Form resets

    @Test("resetBabyForm clears fields and applies account-derived unit")
    func resetBabyFormClears() {
        let (store, _, _, _, _) = makeSUT()
        fillBabyLbsOz(store)

        store.resetBabyForm()

        #expect(store.babyForm.pounds.value.isEmpty)
        #expect(store.babyForm.inches.value.isEmpty)
    }

    @Test("resetBPForm clears BP fields")
    func resetBPFormClears() {
        let (store, _, _, _, _) = makeSUT()
        fillBP(store)

        store.resetBPForm()

        #expect(store.bpForm.systolic.value.isEmpty)
        #expect(store.bpForm.diastolic.value.isEmpty)
    }

    @Test("resetWeightForm clears weight form and hides metrics")
    func resetWeightFormClears() {
        let (store, _, _, _, _) = makeSUT()
        store.manualEntryForm.weight.value = "180"
        store.showMetrics = true

        store.resetWeightForm()

        #expect(store.manualEntryForm.weight.value.isEmpty)
        #expect(store.showMetrics == false)
    }
}

private enum EntryStoreBabyTestError: Error {
    case generic
}
