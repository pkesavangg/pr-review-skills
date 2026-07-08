//
//  BtWifiScaleSetupStoreCompleteProfile.swift
//  meApp
//
//  Complete Profile Setup step (MOB-1388). Collects the biological sex + height that
//  the weight product requires and an optional weight goal, but only when the active
//  account is missing those details (e.g. a baby-only account that just paired a
//  weight scale). Accounts that already have them skip the step entirely.
//

import Foundation
import SwiftUI

@MainActor
extension BtWifiScaleSetupStore {
    // MARK: - Display Helpers

    /// Trailing detail for the biological-sex row.
    var profileGenderText: String {
        profileGender.rawValue.capitalized
    }

    /// Trailing detail for the height row, formatted for the account's unit preference.
    var profileHeightText: String {
        guard let storedDouble = Double(profileHeightStored) else { return "" }
        return ConversionTools.convertToFormattedHeight(Int(round(storedDouble)), isMetric: isProfileMetric)
    }

    /// Whether the active account prefers metric units.
    var isProfileMetric: Bool {
        (accountService.activeAccount?.weightUnit ?? .lb) == .kg
    }

    /// Trailing label for the goal weight fields, e.g. "(kg)" / "(lb)".
    var profileWeightUnitLabel: String {
        isProfileMetric ? "(kg)" : "(lb)"
    }

    // MARK: - Skip Guard

    /// The required profile details (biological sex + height) are already available, so the
    /// Complete Profile step is redundant and should be skipped. Missing account ⇒ treat as
    /// complete so we never block setup.
    func isProfileComplete() -> Bool {
        guard let account = accountService.activeAccount else { return true }
        let hasGender = account.gender != nil
        let hasHeight = (Double(account.weightHeight) ?? 0) > 0
        return hasGender && hasHeight
    }

    // MARK: - Prefill

    /// Pre-fills the form from the active account, falling back to sensible defaults
    /// (Male, 6'5") when a value is missing. Called on entry to the step.
    func prefillCompleteProfile() {
        let account = accountService.activeAccount
        profileGender = account?.gender ?? .male

        let storedDouble = Double(account?.weightHeight ?? "") ?? 0
        // Default of 770 == 6'5" mirrors the signup form's default height.
        let stored = storedDouble > 0 ? Int(round(storedDouble)) : 770
        profileHeightStored = String(stored)
        let selections = ConversionTools.pickerSelections(from: stored)
        selectedProfileHeightInches = selections.inches
        selectedProfileHeightCm = selections.cm

        if let goalType = account?.goalType {
            profileGoalSegment = goalType == .maintain ? .maintain : .loseGain
        }
    }

    // MARK: - Field Updates

    /// Updates the biological sex selection (no API call until Next).
    func updateProfileGender(_ sex: Sex) {
        profileGender = sex
    }

    /// Updates the height selection from a picker (no API call until Next).
    func updateProfileHeight(fromMetric: Bool, values: [String]) {
        guard ConversionTools.isValidHeightPickerValues(fromMetric: fromMetric, values: values) else {
            LoggerService.shared.log(level: .error, tag: tag, message: "Invalid height values rejected: \(values)")
            return
        }

        let storedHeight: Int
        if fromMetric {
            let cm = Int(values.joined()) ?? 178
            guard ConversionTools.isValidHeightCm(cm) else { return }
            storedHeight = ConversionTools.convertCmToStoredHeight(cm)
        } else {
            let feet = Int(values[0]) ?? 5
            let inches = Int(values[1]) ?? 10
            guard ConversionTools.isValidHeightInches(feet: feet, inches: inches) else { return }
            let totalInches = (feet * 12) + inches
            storedHeight = ConversionTools.convertInchesToStoredHeight(totalInches)
        }

        profileHeightStored = String(storedHeight)
        let selections = ConversionTools.pickerSelections(from: storedHeight)
        selectedProfileHeightInches = selections.inches
        selectedProfileHeightCm = selections.cm
    }

    /// Presents the height picker matching the account's unit preference.
    func presentProfileHeightPicker() {
        if isProfileMetric {
            showProfileHeightCmPicker = true
        } else {
            showProfileHeightInchesPicker = true
        }
    }

    // MARK: - Save / Skip

    /// Persists the collected profile (and optional goal) to the account, then advances.
    /// Mirrors `SettingsStore.saveProfile`: profile patch + body composition, plus an
    /// optional goal when a goal weight was entered.
    func saveCompleteProfileAndProceed() {
        let account = accountService.activeAccount
        let weightUnit = account?.weightUnit ?? .lb
        let activityLevel = account?.activityLevel ?? .normal
        let heightDouble = Double(profileHeightStored) ?? 770

        let profile = Profile(
            firstName: account?.firstName ?? "",
            lastName: account?.lastName ?? "",
            email: account?.email,
            gender: profileGender,
            zipcode: account?.zipcode ?? "",
            dob: account?.dob ?? "",
            weightUnit: weightUnit,
            height: heightDouble,
            activityLevel: activityLevel
        )
        let bodyComp = BodyComp(weightUnit: weightUnit, height: heightDouble, activityLevel: activityLevel)
        let goal = buildGoalIfProvided()

        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
            do {
                _ = try await accountService.updateProfile(profile)
                _ = try await accountService.updateBodyComp(bodyComp)
                if let goal {
                    _ = try await accountService.createGoal(goal)
                }
                LoggerService.shared.log(level: .info, tag: tag, message: "Complete Profile saved during scale setup")
            } catch {
                LoggerService.shared.log(
                    level: .error,
                    tag: tag,
                    message: "Complete Profile save failed",
                    data: error.localizedDescription
                )
            }
            notificationService.dismissLoader()
            moveToNextStep()
        }
    }

    /// Advances without saving any changes, carrying forward existing/default values.
    func handleSkipCompleteProfile() {
        moveToNextStep()
    }

    /// Builds a `Goal` only when the optional goal section has a usable goal weight.
    private func buildGoalIfProvided() -> Goal? {
        let trimmed = profileGoalWeight.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, let goalValue = Double(trimmed) else { return nil }

        let isMetric = isProfileMetric
        let goalStored = ConversionTools.convertDisplayToStored(goalValue, isMetric: isMetric)

        if profileGoalSegment == .maintain {
            return Goal(type: .maintain, goalWeight: goalStored, initialWeight: goalStored, goalType: .maintain)
        }

        let currentValue = Double(profileCurrentWeight) ?? 0
        let initialStored = ConversionTools.convertDisplayToStored(currentValue, isMetric: isMetric)
        let derivedType: GoalType = goalStored > initialStored ? .gain : .lose
        return Goal(type: derivedType, goalWeight: goalStored, initialWeight: initialStored, goalType: derivedType)
    }
}
