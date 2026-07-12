// Coverage target: 80% (Store)

import Combine
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct MyKidsStoreTests {

    // MARK: - isSaveEnabled (Dirty Tracking)

    @Test("isSaveEnabled in add mode with valid form returns true")
    func isSaveEnabled_addMode_validForm_returnsTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.babyProfileForm.name.value = "Baby"
        store.babyProfileForm.birthday.value = Date()
        store.babyProfileForm.biologicalSex.value = "male"
        #expect(store.editingBaby == nil)
        #expect(store.isSaveEnabled == true)
    }

    @Test("isSaveEnabled in add mode with invalid form returns false")
    func isSaveEnabled_addMode_invalidForm_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        store.babyProfileForm.name.value = ""
        #expect(store.isSaveEnabled == false)
    }

    @Test("isSaveEnabled in edit mode with pristine form returns false")
    func isSaveEnabled_editMode_formNotDirty_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(accountId: "acct-1", name: "Test", birthday: Date(), biologicalSex: "male")
        store.editBaby(baby)
        #expect(store.isSaveEnabled == false)
    }

    @Test("isSaveEnabled in edit mode with dirty valid form returns true")
    func isSaveEnabled_editMode_formDirtyAndValid_returnsTrue() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(accountId: "acct-1", name: "Test", birthday: Date(), biologicalSex: "male")
        store.editBaby(baby)
        store.babyProfileForm.name.value = "Updated Name"
        #expect(store.isSaveEnabled == true)
    }

    @Test("isSaveEnabled in edit mode with dirty invalid form returns false")
    func isSaveEnabled_editMode_formDirtyButInvalid_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(accountId: "acct-1", name: "Test", birthday: Date(), biologicalSex: "male")
        store.editBaby(baby)
        store.babyProfileForm.name.value = ""
        #expect(store.isSaveEnabled == false)
    }

    @Test("isSaveEnabled detects dirty state on each individual field")
    func isSaveEnabled_editMode_eachFieldDirtyEnablesSave() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(
            accountId: "acct-1",
            name: "Test",
            birthday: Date(),
            biologicalSex: "male",
            birthLengthInches: 20.0,
            birthWeightLbs: 7,
            birthWeightOz: 4.0
        )
        let fields: [(String, (MyKidsStore) -> Void)] = [
            ("name", { $0.babyProfileForm.name.value = "Changed" }),
            ("birthday", { $0.babyProfileForm.birthday.value = Date.distantPast }),
            ("biologicalSex", { $0.babyProfileForm.biologicalSex.value = "female" }),
            ("birthLengthInches", { $0.babyProfileForm.birthLengthInches.value = "22" }),
            ("birthWeightLbs", { $0.babyProfileForm.birthWeightLbs.value = "8" }),
            ("birthWeightOz", { $0.babyProfileForm.birthWeightOz.value = "5.0" })
        ]
        for (fieldName, mutate) in fields {
            store.editBaby(baby)
            #expect(store.isSaveEnabled == false, "Expected save disabled before changing \(fieldName)")
            mutate(store)
            #expect(store.isSaveEnabled == true, "Expected save enabled after changing \(fieldName)")
        }
    }

    @Test("editBaby populates form and form is not dirty")
    func editBaby_populatesForm_andFormIsNotDirty() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(
            accountId: "acct-1",
            name: "Baby",
            birthday: Date(),
            biologicalSex: "male",
            birthLengthInches: 20.0,
            birthWeightLbs: 7,
            birthWeightOz: 4.0
        )
        store.editBaby(baby)
        #expect(store.isSaveEnabled == false)
    }

    // MARK: - addBaby

    @Test("addBaby resets form fields")
    func addBaby_resetsForm() {
        let sut = makeSUT()
        let store = sut.store
        store.babyProfileForm.name.value = "Leftover"
        store.addBaby()
        #expect(store.babyProfileForm.name.value.isEmpty)
    }

    @Test("addBaby sets editingBaby to nil")
    func addBaby_setsEditingBabyToNil() {
        let sut = makeSUT()
        let store = sut.store
        store.editingBaby = Baby(accountId: "acct-1", name: "X")
        store.addBaby()
        #expect(store.editingBaby == nil)
    }

    @Test("addBaby sets isShowingAddBaby to true")
    func addBaby_setsIsShowingAddBabyTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.addBaby()
        #expect(store.isShowingAddBaby == true)
    }

    // MARK: - editBaby

    @Test("editBaby sets editingBaby")
    func editBaby_setsEditingBaby() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(accountId: "acct-1", name: "My Baby")
        store.editBaby(baby)
        #expect(store.editingBaby?.id == baby.id)
    }

    @Test("editBaby populates all form fields")
    func editBaby_populatesAllFields() {
        let sut = makeSUT()
        let store = sut.store
        let birthday = Date()
        let baby = Baby(
            accountId: "acct-1",
            name: "My Baby",
            birthday: birthday,
            biologicalSex: "female",
            birthLengthInches: 19.5,
            birthWeightLbs: 8,
            birthWeightOz: 3.5
        )
        store.editBaby(baby)
        #expect(store.babyProfileForm.name.value == "My Baby")
        #expect(store.babyProfileForm.biologicalSex.value == "female")
        #expect(store.babyProfileForm.birthLengthInches.value == "19.5")
        #expect(store.babyProfileForm.birthWeightLbs.value == "8")
        #expect(store.babyProfileForm.birthWeightOz.value == "3.5")
        #expect(store.isShowingAddBaby == true)
    }

    @Test("editBaby leaves optional fields empty when nil on baby")
    func editBaby_populatesOptionalFieldsWhenNil() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(accountId: "acct-1", name: "Minimal Baby")
        store.editBaby(baby)
        #expect(store.babyProfileForm.biologicalSex.value.isEmpty)
        #expect(store.babyProfileForm.birthLengthInches.value.isEmpty)
        #expect(store.babyProfileForm.birthWeightLbs.value.isEmpty)
        #expect(store.babyProfileForm.birthWeightOz.value.isEmpty)
    }

    // MARK: - saveBabyProfile

    @Test("saveBabyProfile for new baby calls saveBaby on service")
    func saveBabyProfile_newBaby_callsSaveBaby() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.babyProfileForm.name.value = "New Baby"
        store.babyProfileForm.birthday.value = Date()
        store.babyProfileForm.biologicalSex.value = "male"

        await store.saveBabyProfile()

        #expect(babyService.saveBabyCalls == 1)
        #expect(babyService.updateBabyProfileCalls == 0)
    }

    @Test("saveBabyProfile for new baby passes correct parameters")
    func saveBabyProfile_newBaby_passesCorrectParams() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        let account = sut.accountService
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(email: "test@test.com", isActiveAccount: true)
        store.babyProfileForm.name.value = "  Baby Name  "
        store.babyProfileForm.birthday.value = Date()
        store.babyProfileForm.biologicalSex.value = "female"

        await store.saveBabyProfile()

        #expect(babyService.lastSavedName == "Baby Name")
        #expect(babyService.lastSavedAccountId == account.activeAccount?.accountId)
    }

    @Test("saveBabyProfile for existing baby calls updateBabyProfile")
    func saveBabyProfile_existingBaby_callsUpdateBabyProfile() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        let baby = Baby(accountId: "acct-1", name: "Old Name", birthday: Date(), biologicalSex: "male")
        store.editBaby(baby)
        store.babyProfileForm.name.value = "New Name"

        await store.saveBabyProfile()

        #expect(babyService.updateBabyProfileCalls == 1)
        #expect(babyService.saveBabyCalls == 0)
        #expect(babyService.lastUpdatedBaby?.id == baby.id)
    }

    @Test("saveBabyProfile with empty name does not call service")
    func saveBabyProfile_emptyName_doesNotCallService() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.babyProfileForm.name.value = ""

        await store.saveBabyProfile()

        #expect(babyService.saveBabyCalls == 0)
        #expect(babyService.updateBabyProfileCalls == 0)
    }

    @Test("saveBabyProfile with whitespace-only name does not call service")
    func saveBabyProfile_whitespaceName_doesNotCallService() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.babyProfileForm.name.value = "   "

        await store.saveBabyProfile()

        #expect(babyService.saveBabyCalls == 0)
    }

    @Test("saveBabyProfile success dismisses sheet and clears editing baby")
    func saveBabyProfile_success_dismissesSheet() async {
        let sut = makeSUT()
        let store = sut.store
        store.babyProfileForm.name.value = "Baby"
        store.babyProfileForm.birthday.value = Date()
        store.babyProfileForm.biologicalSex.value = "male"
        store.isShowingAddBaby = true

        await store.saveBabyProfile()

        #expect(store.isShowingAddBaby == false)
        #expect(store.editingBaby == nil)
    }

    @Test("saveBabyProfile failure does not dismiss sheet")
    func saveBabyProfile_failure_doesNotDismissSheet() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        babyService.saveBabyError = MyKidsTestError.genericFailure
        store.babyProfileForm.name.value = "Baby"
        store.babyProfileForm.birthday.value = Date()
        store.babyProfileForm.biologicalSex.value = "male"
        store.isShowingAddBaby = true

        await store.saveBabyProfile()

        #expect(store.isShowingAddBaby == true)
    }

    // MARK: - loadBabies

    @Test("loadBabies with active account calls service")
    func loadBabies_withActiveAccount_callsService() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        let account = sut.accountService
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(email: "test@test.com", isActiveAccount: true)

        await store.loadBabies()

        #expect(babyService.loadBabiesCalls == 1)
        #expect(babyService.lastLoadAccountId == account.activeAccount?.accountId)
    }

    @Test("loadBabies without active account does not call service")
    func loadBabies_noActiveAccount_doesNotCallService() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService

        await store.loadBabies()

        #expect(babyService.loadBabiesCalls == 0)
    }

    // MARK: - confirmDeleteBaby / deleteBaby

    @Test("confirmDeleteBaby shows alert via notification service")
    func confirmDeleteBaby_showsAlert() {
        let sut = makeSUT()
        let store = sut.store
        let notification = sut.notificationService
        let baby = Baby(accountId: "acct-1", name: "Delete Me")

        store.confirmDeleteBaby(baby)

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData != nil)
    }

    @Test("deleteBaby calls service delete when triggered from alert")
    func deleteBaby_callsServiceDelete() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        let notification = sut.notificationService
        let baby = Baby(accountId: "acct-1", name: "Delete Me")

        store.confirmDeleteBaby(baby)

        // Simulate tapping the delete button in the alert
        if let deleteButton = notification.alertData?.buttons.last {
            deleteButton.action(deleteButton.title)
        }

        let deleted = await waitUntil { babyService.deleteBabyCalls == 1 }
        #expect(deleted == true)
        #expect(babyService.lastDeletedBaby?.id == baby.id)
    }

    @Test("deleteBaby failure does not crash")
    func deleteBaby_failure_doesNotCrash() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        let notification = sut.notificationService
        babyService.deleteBabyError = MyKidsTestError.genericFailure
        let baby = Baby(accountId: "acct-1", name: "Delete Me")

        store.confirmDeleteBaby(baby)

        if let deleteButton = notification.alertData?.buttons.last {
            deleteButton.action(deleteButton.title)
        }

        let attempted = await waitUntil { babyService.deleteBabyCalls == 1 }
        #expect(attempted == true)
    }

    // MARK: - refreshDuplicateBabyNameError

    @Test("refreshDuplicateBabyNameError flags a name that matches an existing baby")
    func refreshDuplicate_matchesExisting_setsError() {
        let sut = makeSUT()
        let store = sut.store
        store.babies = [Baby(accountId: "acct-1", name: "Aria")]
        store.babyProfileForm.name.value = "aria"

        let isDuplicate = store.refreshDuplicateBabyNameError()

        #expect(isDuplicate == true)
        #expect(store.babyProfileForm.duplicateNameError == BabyScaleSetupStrings.BabyProfile.duplicateNameError)
    }

    @Test("refreshDuplicateBabyNameError excludes the baby currently being edited")
    func refreshDuplicate_excludesEditingBaby() {
        let sut = makeSUT()
        let store = sut.store
        let baby = Baby(accountId: "acct-1", name: "Aria")
        store.babies = [baby]
        store.editBaby(baby)
        store.babyProfileForm.name.value = "Aria"

        let isDuplicate = store.refreshDuplicateBabyNameError()

        #expect(isDuplicate == false)
        #expect(store.babyProfileForm.duplicateNameError == nil)
    }

    @Test("refreshDuplicateBabyNameError allows a unique name")
    func refreshDuplicate_uniqueName_clearsError() {
        let sut = makeSUT()
        let store = sut.store
        store.babies = [Baby(accountId: "acct-1", name: "Aria")]
        store.babyProfileForm.name.value = "Bella"

        #expect(store.refreshDuplicateBabyNameError() == false)
        #expect(store.babyProfileForm.duplicateNameError == nil)
    }

    @Test("isSaveEnabled is false while the name duplicates an existing baby")
    func isSaveEnabled_falseWhenDuplicateNameError() {
        let sut = makeSUT()
        let store = sut.store
        store.babies = [Baby(accountId: "acct-1", name: "Aria")]
        store.babyProfileForm.name.value = "Aria"
        store.babyProfileForm.birthday.value = Date()
        store.babyProfileForm.biologicalSex.value = "male"

        store.refreshDuplicateBabyNameError()

        #expect(store.isSaveEnabled == false)
    }

    // MARK: - preferredWeightUnit (Edit a Baby unit derives from baby measurementUnits, MOB-1471)

    /// Editing a baby must derive the form's weight unit from the account's "My Kids"
    /// `measurementUnits` (which the baby Unit Type dialog writes) — not the adult
    /// "My Weight" `weightUnit`. Verified through the observable `selectedWeightUnit`
    /// that `editBaby` -> `populateStoredMeasurements` sets.
    private func editBabyWeightUnit(forMeasurementUnits measurementUnits: String?) -> BabyWeightUnit {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            email: "kids@test.com",
            isActiveAccount: true,
            measurementUnits: measurementUnits
        )
        let sut = makeSUT(accountService: account)
        let baby = Baby(
            accountId: "acct-1",
            name: "Kid",
            birthday: Date(),
            biologicalSex: "female",
            birthLengthInches: 20.0,
            birthWeightLbs: 7,
            birthWeightOz: 4.0
        )
        sut.store.editBaby(baby)
        return sut.store.babyProfileForm.selectedWeightUnit
    }

    @Test("editBaby with metric measurementUnits derives the kg unit")
    func preferredWeightUnit_metric_isKg() {
        #expect(editBabyWeightUnit(forMeasurementUnits: MeasurementUnits.metric.rawValue) == .kg)
    }

    @Test("editBaby with imperialLbDecimal measurementUnits derives the lb (decimal) unit")
    func preferredWeightUnit_imperialLbDecimal_isLb() {
        #expect(editBabyWeightUnit(forMeasurementUnits: MeasurementUnits.imperialLbDecimal.rawValue) == .lb)
    }

    @Test("editBaby with imperialLbOz measurementUnits derives the lbs/oz unit")
    func preferredWeightUnit_imperialLbOz_isLbsOz() {
        #expect(editBabyWeightUnit(forMeasurementUnits: MeasurementUnits.imperialLbOz.rawValue) == .lbsOz)
    }

    @Test("editBaby with no measurementUnits defaults to the lbs/oz unit")
    func preferredWeightUnit_nil_defaultsToLbsOz() {
        #expect(editBabyWeightUnit(forMeasurementUnits: nil) == .lbsOz)
    }

    @Test("editBaby with an unrecognised measurementUnits string defaults to the lbs/oz unit")
    func preferredWeightUnit_invalid_defaultsToLbsOz() {
        #expect(editBabyWeightUnit(forMeasurementUnits: "not-a-real-unit") == .lbsOz)
    }
}

// MARK: - Test Errors

private enum MyKidsTestError: Error {
    case genericFailure
}

// MARK: - makeSUT

private struct MyKidsStoreSUT {
    let store: MyKidsStore
    let babyService: MockBabyService
    let accountService: MockAccountService
    let notificationService: MockNotificationHelperService
}

@MainActor
private func makeSUT(
    babyService: MockBabyService? = nil,
    accountService: MockAccountService? = nil,
    notificationService: MockNotificationHelperService? = nil
) -> MyKidsStoreSUT {
    let baby = babyService ?? MockBabyService()
    let account = accountService ?? MockAccountService()
    let notification = notificationService ?? MockNotificationHelperService()

    TestDependencyContainer.reset()
    DependencyContainer.shared.register(baby as BabyServiceProtocol)
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)

    let store = MyKidsStore()
    return MyKidsStoreSUT(store: store, babyService: baby, accountService: account, notificationService: notification)
}

// MARK: - Helpers

@MainActor
private func waitUntil(
    timeoutNanoseconds: UInt64 = 2_000_000_000,
    pollIntervalNanoseconds: UInt64 = 10_000_000,
    condition: @MainActor () -> Bool
) async -> Bool {
    let start = DispatchTime.now().uptimeNanoseconds
    while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
        if condition() { return true }
        try? await Task.sleep(nanoseconds: pollIntervalNanoseconds)
    }
    return false
}
