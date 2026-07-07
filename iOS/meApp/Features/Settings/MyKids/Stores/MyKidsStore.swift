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

    @Published var babies: [Baby] = []
    @Published var editingBaby: Baby?
    @Published var isShowingAddBaby = false
    @Published var showBabyDatePicker = false
    @Published var lastSavedBabyId: String?

    /// Form used by the add/edit baby sheet.
    @Published var babyProfileForm = BabyProfileSetupForm()

    /// Save is enabled when the form is valid AND (adding a new baby OR the form has been edited).
    var isSaveEnabled: Bool {
        guard babyProfileForm.isProfileValid else { return false }
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
            }
            notificationService.dismissLoader()
            isShowingAddBaby = false
            editingBaby = nil
        } catch {
            // Surface the failure instead of silently swallowing it — otherwise SAVE looks unresponsive.
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: lang.saveFailed))
            LoggerService.shared.log(level: .error, tag: "MyKidsStore", message: "Failed to save baby: \(error)")
        }
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

    private var preferredWeightUnit: BabyWeightUnit {
        accountService.activeAccount?.weightUnit == .kg ? .kg : .lbsOz
    }
}
