//
//  MyKidsStore.swift
//  meApp
//

import Combine
import Foundation

@MainActor
final class MyKidsStore: ObservableObject {
    @Injector private var babyService: BabyServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var productTypeStore: ProductTypeStoreProtocol

    @Published var babies: [Baby] = []
    @Published var editingBaby: Baby?
    @Published var isShowingAddBaby = false
    @Published var showBabyDatePicker = false
    @Published var lastSavedBabyId: String?

    /// Form used by the add/edit baby sheet.
    @Published var babyProfileForm = BabyProfileSetupForm()

    /// Save is enabled when the form is valid, the name isn't a duplicate, AND
    /// (adding a new baby OR the form has been edited).
    var isSaveEnabled: Bool {
        guard babyProfileForm.isProfileValid, babyProfileForm.duplicateNameError == nil else { return false }
        if editingBaby != nil { return isFormDirty }
        return true
    }

    /// Whether any form field has been changed by the user since the form was populated.
    private var isFormDirty: Bool {
        babyProfileForm.name.isDirty
            || babyProfileForm.birthday.isDirty
            || babyProfileForm.biologicalSex.isDirty
            || babyProfileForm.birthLengthInches.isDirty
            || babyProfileForm.birthWeightLbs.isDirty
            || babyProfileForm.birthWeightOz.isDirty
            || babyProfileForm.birthLengthCm.isDirty
            || babyProfileForm.birthWeightKg.isDirty
    }

    private var cancellables = Set<AnyCancellable>()
    private let lang = MyKidsStrings.self

    init() {
        subscribeToBabies()
        subscribeToFormChanges()
    }

    // MARK: - Data Loading

    private func subscribeToBabies() {
        babyService.babiesPublisher
            .receive(on: DispatchQueue.main)
            .assign(to: &$babies)
    }

    private func subscribeToFormChanges() {
        babyProfileForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.refreshDuplicateBabyNameError()
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    func loadBabies() async {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        try? await babyService.loadBabies(for: accountId)
    }

    // MARK: - Add / Edit

    func addBaby() {
        editingBaby = nil
        babyProfileForm.reset()
        isShowingAddBaby = true
    }

    func editBaby(_ baby: Baby) {
        editingBaby = baby
        populateForm(from: baby)
        isShowingAddBaby = true
    }

    func saveBabyProfile() async {
        let name = babyProfileForm.name.value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        // A locally-detected duplicate already disables SAVE, but guard here too in case
        // the check hasn't re-run yet. This also clears any stale duplicate-name error
        // (refreshDuplicateNameError sets duplicateNameError to nil when there's no match).
        guard !refreshDuplicateBabyNameError() else { return }

        let birthday = babyProfileForm.birthday.value
        let biologicalSex = babyProfileForm.biologicalSex.value.isEmpty
            ? nil : babyProfileForm.biologicalSex.value
        let lengthInches = babyProfileForm.parsedBirthLengthInches
        let weightLbs = babyProfileForm.parsedBirthWeightLbs
        let weightOz = babyProfileForm.parsedBirthWeightOz

        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
        do {
            if let existing = editingBaby {
                try await babyService.updateBabyProfile(
                    existing,
                    name: name,
                    birthday: birthday,
                    biologicalSex: biologicalSex,
                    birthLengthInches: lengthInches,
                    birthWeightLbs: weightLbs,
                    birthWeightOz: weightOz
                )
            } else {
                let accountId = accountService.activeAccount?.accountId ?? ""
                let newBaby = try await babyService.saveBaby(
                    name: name,
                    accountId: accountId,
                    deviceId: nil,
                    birthday: birthday,
                    biologicalSex: biologicalSex,
                    birthLengthInches: lengthInches,
                    birthWeightLbs: weightLbs,
                    birthWeightOz: weightOz
                )
                lastSavedBabyId = newBaby.id
                // Switch the active product to the newly added baby (MOB-686): adding a product
                // makes it the selected one. Robust to rebuild timing — select() sets the item
                // directly and rebuild() reconciles it by id once babiesPublisher fires.
                productTypeStore.selectLastAdded(.baby(profile: newBaby.toBabyProfile()))
            }
            notificationService.dismissLoader()
            isShowingAddBaby = false
            editingBaby = nil
        } catch {
            notificationService.dismissLoader()
            LoggerService.shared.log(level: .error, tag: "MyKidsStore", message: "Failed to save baby: \(error)")
            if HTTPError.isConflict(error) {
                // Surface the server 409 as an inline field error rather than a generic toast.
                babyProfileForm.duplicateNameError = BabyScaleSetupStrings.BabyProfile.duplicateNameError
            } else {
                // Surface the failure instead of silently swallowing it — otherwise SAVE looks unresponsive.
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: lang.saveFailed))
            }
        }
    }

    /// Re-evaluates whether the current name duplicates an already-saved baby's name and sets or
    /// clears `duplicateNameError` accordingly. Called on every name change so the error surfaces
    /// (and SAVE disables) as soon as a duplicate is typed — without waiting for the server 409.
    /// Delegates the comparison to the shared `BabyProfileSetupForm` helper.
    @discardableResult
    func refreshDuplicateBabyNameError() -> Bool {
        let otherNames = babies
            .filter { $0.id != editingBaby?.id }
            .map(\.name)
        return babyProfileForm.refreshDuplicateNameError(against: otherNames)
    }

    // MARK: - Expanded Detail Rows

    /// The account's baby measurement preference, defaulting to lb-oz when unset.
    private var measurementUnits: MeasurementUnits {
        accountService.activeAccount?.measurementUnits.flatMap(MeasurementUnits.init(rawValue:)) ?? .imperialLbOz
    }

    /// The four profile fields shown when a baby row is expanded (MOB-1605), formatted
    /// for the account's current measurement units.
    func detailRows(for baby: Baby) -> [BabyProfileDetail] {
        let units = measurementUnits
        return [
            BabyProfileDetail(
                label: lang.Details.birthday,
                value: BabyProfileDisplayFormatter.birthday(baby.birthday)
            ),
            BabyProfileDetail(
                label: lang.Details.biologicalSex,
                value: BabyProfileDisplayFormatter.biologicalSex(baby.biologicalSex)
            ),
            BabyProfileDetail(
                label: lang.Details.birthLength,
                value: BabyProfileDisplayFormatter.birthLength(inches: baby.birthLengthInches, units: units)
            ),
            BabyProfileDetail(
                label: lang.Details.birthWeight,
                value: BabyProfileDisplayFormatter.birthWeight(
                    lbs: baby.birthWeightLbs,
                    oz: baby.birthWeightOz,
                    units: units
                )
            )
        ]
    }

    // MARK: - Delete

    func confirmDeleteBaby(_ baby: Baby) {
        let alert = AlertModel(
            title: lang.RemoveBaby.title,
            message: lang.RemoveBaby.message,
            buttons: [
                AlertButtonModel(title: lang.RemoveBaby.cancel, type: .secondary) { _ in },
                AlertButtonModel(title: lang.RemoveBaby.delete, type: .danger) { [weak self] _ in
                    Task { [weak self] in
                        await self?.deleteBaby(baby)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func deleteBaby(_ baby: Baby) async {
        do {
            try await babyService.deleteBaby(baby)
        } catch {
            LoggerService.shared.log(level: .error, tag: "MyKidsStore", message: "Failed to delete baby: \(error)")
        }
    }

    // MARK: - Helpers

    private func populateForm(from baby: Baby) {
        babyProfileForm.reset()
        babyProfileForm.name.value = baby.name
        if let birthday = baby.birthday {
            babyProfileForm.birthday.value = birthday
        }
        babyProfileForm.biologicalSex.value = baby.biologicalSex ?? ""
        babyProfileForm.populateStoredMeasurements(
            birthLengthInches: baby.birthLengthInches,
            birthWeightLbs: baby.birthWeightLbs,
            birthWeightOz: baby.birthWeightOz,
            preferredWeightUnit: preferredWeightUnit
        )
        markFormAsPristine()
    }

    private func markFormAsPristine() {
        babyProfileForm.name.markAsPristine()
        babyProfileForm.birthday.markAsPristine()
        babyProfileForm.biologicalSex.markAsPristine()
        babyProfileForm.birthLengthInches.markAsPristine()
        babyProfileForm.birthWeightLbs.markAsPristine()
        babyProfileForm.birthWeightOz.markAsPristine()
        babyProfileForm.birthLengthCm.markAsPristine()
        babyProfileForm.birthWeightKg.markAsPristine()
    }

    /// Baby weight unit derived from the account's "My Kids" `measurementUnits`, NOT the
    /// adult "My Weight" `weightUnit`. The baby Unit Type dialog writes `measurementUnits`,
    /// so reading `weightUnit` here left the Edit a Baby form on the previous unit after a
    /// baby unit change (MOB-1471). Also honours `.lb` (decimal) which the old check dropped.
    private var preferredWeightUnit: BabyWeightUnit {
        switch accountService.activeAccount?.measurementUnits.flatMap(MeasurementUnits.init(rawValue:)) {
        case .metric:
            return .kg
        case .imperialLbDecimal:
            return .lb
        case .imperialLbOz, .none:
            return .lbsOz
        }
    }
}
