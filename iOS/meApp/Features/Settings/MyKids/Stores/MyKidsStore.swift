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

    /// Form used by the add/edit baby sheet.
    @Published var babyProfileForm = BabyProfileSetupForm()

    private var cancellables = Set<AnyCancellable>()
    private let lang = MyKidsStrings.self

    init() {
        subscribeToBabies()
    }

    // MARK: - Data Loading

    private func subscribeToBabies() {
        babyService.babiesPublisher
            .receive(on: DispatchQueue.main)
            .assign(to: &$babies)
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
                _ = try await babyService.saveBaby(
                    name: name,
                    accountId: accountId,
                    deviceId: nil,
                    birthday: birthday,
                    biologicalSex: biologicalSex,
                    birthLengthInches: lengthInches,
                    birthWeightLbs: weightLbs,
                    birthWeightOz: weightOz
                )
            }
            isShowingAddBaby = false
            editingBaby = nil
        } catch {
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
        if let length = baby.birthLengthInches {
            babyProfileForm.birthLengthInches.value = String(length)
        }
        if let lbs = baby.birthWeightLbs {
            babyProfileForm.birthWeightLbs.value = String(Int(lbs))
        }
        if let oz = baby.birthWeightOz {
            babyProfileForm.birthWeightOz.value = String(oz)
        }
    }
}
