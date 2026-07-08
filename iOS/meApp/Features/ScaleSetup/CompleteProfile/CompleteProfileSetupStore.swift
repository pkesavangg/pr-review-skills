//
//  CompleteProfileSetupStore.swift
//  meApp
//
//  Complete Profile Setup step. Collects the biological sex + height that the weight
//  product requires and an optional weight goal (e.g. for a baby-only account that just
//  paired a weight scale). Existing account values are pre-filled so the user can confirm
//  or adjust them.
//
//  This store is shared across the Bluetooth scale-setup flows (A6/LCBT, Bluetooth/A3
//  and, historically, BtWifi/R4). The A6 and Bluetooth flows always present the step;
//  only the BtWifi flow auto-skips it (via its coordinator's `canSkipCompleteProfile`
//  guard, driven by `isProfileComplete()`) when the account is already complete.
//  Navigation stays with the owning flow store: `save` and `skip` invoke a completion
//  closure once their work is done. The logic mirrors `BtWifiScaleSetupStore`'s original
//  Complete Profile implementation (MOB-1388).
//

import Foundation
import SwiftUI

@MainActor
final class CompleteProfileSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol

    // MARK: - Published State
    /// Biological sex selected on the Complete Profile step.
    @Published var profileGender: Sex = .male
    /// Height selected on the Complete Profile step, stored as tenths-of-inches (matches `weightHeight`).
    @Published var profileHeightStored: String = ""
    /// Controls the biological-sex picker sheet.
    @Published var showProfileGenderPicker: Bool = false
    /// Controls the imperial (feet & inches) height picker sheet.
    @Published var showProfileHeightInchesPicker: Bool = false
    /// Controls the metric (centimetres) height picker sheet.
    @Published var showProfileHeightCmPicker: Bool = false
    /// Selected imperial height components (feet, inches).
    @Published var selectedProfileHeightInches: [String] = ["5", "10"]
    /// Selected metric height components (centimetres digits).
    @Published var selectedProfileHeightCm: [String] = ["1", "7", "8"]
    /// Selected goal type on the optional "Set a Goal" section.
    @Published var profileGoalSegment: GoalTypeSegment = .maintain
    /// Optional goal weight entered on the Complete Profile step (display units).
    @Published var profileGoalWeight: String = ""
    /// Optional starting weight entered when the goal type is Lose / Gain (display units).
    @Published var profileCurrentWeight: String = ""

    private let tag = "CompleteProfileSetupStore"

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

    /// Whether the required profile details (biological sex + height) are already available.
    /// Consulted only by the BtWifi flow's `canSkipCompleteProfile` guard to skip the step;
    /// the A6/Bluetooth flows always present it. Missing account ⇒ treat as complete so we
    /// never block setup.
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

    /// Persists the collected profile (and optional goal) to the account, then invokes
    /// `onComplete` so the owning flow can advance. Mirrors `SettingsStore.saveProfile`:
    /// profile patch + body composition, plus an optional goal when a goal weight was entered.
    func saveCompleteProfile(onComplete: @escaping @MainActor () -> Void) {
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
            onComplete()
        }
    }

    /// Advances without saving any changes, carrying forward existing/default values.
    func skipCompleteProfile(onComplete: @MainActor () -> Void) {
        onComplete()
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
