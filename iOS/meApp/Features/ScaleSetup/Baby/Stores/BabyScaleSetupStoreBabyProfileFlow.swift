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

        // Clear any stale duplicate-name error before a new attempt.
        babyProfileForm.duplicateNameError = nil

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
            if isDuplicateNameError(error) {
                babyProfileForm.duplicateNameError = BabyScaleSetupStrings.BabyProfile.duplicateNameError
            } else {
                scaleSetupError = .profileSaveFailed
            }
        }
    }

    /// Returns true when the server rejected the request because the baby name is already taken (HTTP 409 Conflict).
    private func isDuplicateNameError(_ error: Error) -> Bool {
        switch error as? HTTPError {
        case .statusCode(409): return true
        case .apiError(_, let code): return code == 409
        default: return false
        }
    }

    // MARK: - Edit Baby

    func editBaby(_ baby: Baby) {
        editingBaby = baby
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
            preferredWeightUnit: accountService.activeAccount?.weightUnit == .kg ? .kg : .lbsOz
        )
        navigateToStep(.babyProfile)
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

    // MARK: - Skip Baby Profile

    func showSkipBabyProfileDialog() {
        if editingBaby != nil {
            showSkipEditDialog = true
        } else {
            showSkipDialog = true
        }
    }

    // MARK: Skip Add Baby

    func handleSkipConfirmed() {
        showSkipDialog = false
        navigateToDoneScreen()
    }

    func handleSkipCancelled() {
        showSkipDialog = false
    }

    // MARK: Skip Edit Baby

    func handleSkipEditConfirmed() {
        showSkipEditDialog = false
        editingBaby = nil
        babyProfileForm.reset()
        navigateToStep(.babyAdded)
    }

    func handleSkipEditCancelled() {
        showSkipEditDialog = false
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
                skipDuplicateCheck: true,
                deviceType: .babyScale
            )
            isScaleSaved = true
            await scaleService.syncAllScalesWithRemote()
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            let pendingProfile = BabyProfile(id: BabyProfile.pendingSelectionId, name: ProductTypeStrings.babyScale)
            productTypeStore.selectLastAdded(.baby(profile: pendingProfile))
            LoggerService.shared.log(level: .info, tag: tag, message: "Baby scale saved locally with SKU: \(sku ?? "unknown")")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save baby scale locally: \(error)")
        }
    }

    // MARK: - Navigate to Done

    func navigateToDoneScreen() {
        guard discoveredScale != nil, discoveryEvent != nil, !isScaleSaved else {
            navigateToStep(.done)
            return
        }
        Task {
            await saveScale()
            navigateToStep(.done)
        }
    }
}
