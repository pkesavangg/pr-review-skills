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

        let accountId = accountService.activeAccount?.accountId ?? ""
        let deviceId = savedScale?.id

        do {
            let baby = try await babyService.saveBaby(
                name: name,
                accountId: accountId,
                deviceId: deviceId,
                birthday: babyProfileForm.birthday.value,
                biologicalSex: babyProfileForm.biologicalSex.value.isEmpty ? nil : babyProfileForm.biologicalSex.value,
                birthLengthInches: babyProfileForm.parsedBirthLengthInches,
                birthWeightLbs: babyProfileForm.parsedBirthWeightLbs,
                birthWeightOz: babyProfileForm.parsedBirthWeightOz
            )
            savedBabies.append(baby)
            LoggerService.shared.log(level: .info, tag: tag, message: "Baby profile saved: \(baby.name)")
            navigateToStep(.babyAdded)
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save baby profile: \(error)")
            scaleSetupError = .profileSaveFailed
        }
    }

    // MARK: - Add Another Baby

    func addAnotherBaby() {
        babyProfileForm.reset()
        navigateToStep(.babyProfile)
    }

    // MARK: - Delete Baby

    /// Shows a confirmation alert before deleting a baby from the list.
    func confirmDeleteBabyFromList(_ baby: Baby) {
        notificationService.showDeleteBabyConfirmation { [weak self] in
            self?.deleteBabyFromList(baby)
        }
    }

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

    /// Loads a baby's data into the form for editing and navigates to the baby profile step.
    func editBaby(_ baby: Baby) {
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
        editingBaby = baby
        navigateToStep(.babyProfile)
    }

    // MARK: - Skip Baby Profile

    func showSkipBabyProfileDialog() {
        showSkipDialog = true
    }

    func handleSkipConfirmed() {
        showSkipDialog = false
        handleFinish()
    }

    func handleSkipCancelled() {
        showSkipDialog = false
    }

    // MARK: - Save Device Locally

    /// Creates a local Device record for the baby scale so it appears in My Scales.
    func saveScaleLocally() async {
        guard !isScaleSaved else { return }
        guard let accountId = accountService.activeAccount?.accountId else {
            LoggerService.shared.log(level: .error, tag: tag, message: "No active account for baby scale device creation")
            return
        }

        let sku = scaleItem?.sku
        let nickname = scaleNicknameForm.nickname.value.trimmingCharacters(in: .whitespacesAndNewlines)
        let device = Device(
            id: UUID().uuidString,
            accountId: accountId,
            nickname: nickname.isEmpty ? nil : nickname,
            sku: sku,
            deviceType: DeviceType.scale.rawValue,
            createdAt: DateTimeTools.getCurrentDatetimeIsoString(),
            isSynced: false,
            hasServerID: false
        )

        do {
            _ = try await scaleService.createBluetoothScale(
                device: device,
                sku: sku,
                userNumber: "1",
                accountId: accountId,
                deviceMetadata: nil,
                skipDuplicateCheck: true
            )
            isScaleSaved = true
            await scaleService.syncAllScalesWithRemote()
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            LoggerService.shared.log(level: .info, tag: tag, message: "Baby scale saved locally with SKU: \(sku ?? "unknown")")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save baby scale locally: \(error)")
        }
    }

    // MARK: - Finish

    func handleFinish() {
        Task {
            await saveScaleLocally()
            performExitCleanup()
        }
    }
}
