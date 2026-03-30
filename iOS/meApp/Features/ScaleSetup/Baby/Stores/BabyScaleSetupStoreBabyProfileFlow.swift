//
//  BabyScaleSetupStoreBabyProfileFlow.swift
//  meApp
//

import Foundation

@MainActor
extension BabyScaleSetupStore {

    // MARK: - Save Baby Profile

    func saveBabyProfile() async {
        let name = babyProfileForm.name.value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        let birthday = babyProfileForm.birthday.value
        let biologicalSex = babyProfileForm.biologicalSex.value.isEmpty ? nil : babyProfileForm.biologicalSex.value
        let lengthInches = babyProfileForm.parsedBirthLengthInches
        let weightLbs = babyProfileForm.parsedBirthWeightLbs
        let weightOz = babyProfileForm.parsedBirthWeightOz

        do {
            if let existing = editingBaby {
                // Update existing baby
                try await babyService.updateBabyProfile(
                    existing,
                    name: name,
                    birthday: birthday,
                    biologicalSex: biologicalSex,
                    birthLengthInches: lengthInches,
                    birthWeightLbs: weightLbs,
                    birthWeightOz: weightOz
                )
                if let index = savedBabies.firstIndex(where: { $0.id == existing.id }) {
                    savedBabies[index] = existing
                }
                LoggerService.shared.log(level: .info, tag: tag, message: "Baby profile updated: \(existing.name)")
                editingBaby = nil
            } else {
                // Create new baby
                let accountId = accountService.activeAccount?.accountId ?? ""
                let deviceId = savedScale?.id
                let baby = try await babyService.saveBaby(
                    name: name,
                    accountId: accountId,
                    deviceId: deviceId,
                    birthday: birthday,
                    biologicalSex: biologicalSex,
                    birthLengthInches: lengthInches,
                    birthWeightLbs: weightLbs,
                    birthWeightOz: weightOz
                )
                savedBabies.append(baby)
                LoggerService.shared.log(level: .info, tag: tag, message: "Baby profile saved: \(baby.name)")
            }
            navigateToStep(.babyAdded)
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save baby profile: \(error)")
            scaleSetupError = .profileSaveFailed
        }
    }

    // MARK: - Edit Baby

    func editBaby(_ baby: Baby) {
        editingBaby = baby
        babyProfileForm.name.value = baby.name
        if let birthday = baby.birthday {
            babyProfileForm.birthday.value = birthday
        }
        babyProfileForm.biologicalSex.value = baby.biologicalSex ?? ""
        if let length = baby.birthLengthInches {
            babyProfileForm.birthLengthInches.value = String(length)
        } else {
            babyProfileForm.birthLengthInches.value = ""
        }
        if let lbs = baby.birthWeightLbs {
            babyProfileForm.birthWeightLbs.value = String(Int(lbs))
        } else {
            babyProfileForm.birthWeightLbs.value = ""
        }
        if let oz = baby.birthWeightOz {
            babyProfileForm.birthWeightOz.value = String(oz)
        } else {
            babyProfileForm.birthWeightOz.value = ""
        }
        navigateToStep(.babyProfile)
    }

    // MARK: - Add Another Baby

    func addAnotherBaby() {
        babyProfileForm.reset()
        navigateToStep(.babyProfile)
    }

    // MARK: - Delete Baby

    func deleteBabyFromList(_ baby: Baby) {
        savedBabies.removeAll { $0.id == baby.id }
        Task {
            do {
                try await babyService.deleteBaby(baby)
                LoggerService.shared.log(level: .info, tag: tag, message: "Baby deleted: \(baby.name)")
            } catch {
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to delete baby: \(error)")
            }
        }
        // If no babies left, go back to profile form
        if savedBabies.isEmpty {
            navigateToStep(.babyProfile)
        }
    }

    // MARK: - Finish

    func handleFinish() {
        performExitCleanup()
    }
}
