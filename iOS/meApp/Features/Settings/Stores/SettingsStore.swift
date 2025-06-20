//
//  SettingsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import Foundation
import Combine
import SwiftUI

// MARK: - Settings Store
/// A store to manage user settings and account actions.
@MainActor
class SettingsStore: ObservableObject {
    @Injector var accountService: AccountService
    @Injector var notificationService: NotificationHelperService
    @Injector var entryService: EntryService
    @Injector var logger: LoggerService
    var theme = Theme.shared
    
    @Published var activeAccount: Account?
    
    // Edit-Profile flow
    @Published var editProfileForm = EditProfileForm()
    // Change-Password flow
    @Published var changePasswordForm = ChangePasswordForm()
    // Weightless-mode form
    @Published var weightlessForm = WeightlessForm()
    // Goal-setting form
    @Published var goalForm = GoalForm()
    
    var cancellables = Set<AnyCancellable>()
    
    // Localization strings
    private let toastLang = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let legalURLs = AppConstants.LegalURLs.self
    
    let tag = "SettingsStore"
    
    // MARK: - In-App Browser State
    @Published var showPrivacyBrowser: Bool = false
    @Published var showTermsBrowser: Bool = false
    @Published var showGreaterGoodsBrowser: Bool = false
    @Published var browserURL: URL? = nil
    
    // MARK: - Weightless Page State
    @Published var showWeightLessPage: Bool = false
    // MARK: - Goal Page State
    @Published var showGoalPage: Bool = false
    @Published var selectedSegment: GoalTypeSegment = .loseGain

    
    /// Main browser presentation binding for the view
    var isBrowserPresented: Binding<Bool> {
        Binding(
            get: { self.showPrivacyBrowser || self.showTermsBrowser || self.showGreaterGoodsBrowser },
            set: { newValue in
                if !newValue {
                    self.showPrivacyBrowser = false
                    self.showTermsBrowser = false
                    self.showGreaterGoodsBrowser = false
                    self.browserURL = nil
                }
            }
        )
    }
    
    /// Browser URL used by the view
    var presentingBrowserURL: URL {
        browserURL ?? legalURLs.greaterGoodsWebsite
    }
    
    // MARK: - Height Picker State
    /// Selected height components when the user prefers imperial units (feet & inches).
    @Published var selectedHeightInches: [String] = ["5", "10"]
    /// Selected height components when the user prefers metric units (centimetres).
    @Published var selectedHeightCm: [String] = ["1", "7", "8"]
    /// Controls the presentation of the imperial picker sheet.
    @Published var showHeightInchesPicker: Bool = false
    /// Controls the presentation of the metric picker sheet.
    @Published var showHeightCmPicker: Bool = false

    /// Shared picker options
    let heightInchesOptions = ConversionTools.heightInchesOptions
    let heightCmOptions     = ConversionTools.heightCmOptions
    
    init() {
        accountService.$activeAccount
            .sink { [weak self] account in
                self?.activeAccount = account
                self?.populateEditFormIfNeeded()
                self?.populateWeightlessFormIfNeeded()
                self?.syncHeightPickers()
                self?.populateGoalFormIfNeeded()
            }
            .store(in: &accountService.cancellables)
            self.populateWeightlessFormIfNeeded()
    }
    
    func handleLogout() {
        let logoutAlert = alertLang.LogoutAlert
        let alert = AlertModel(
            title: logoutAlert.title,
            message: logoutAlert.message,
            buttons: [
                AlertButtonModel(title: logoutAlert.logoutButton, type: .primary) { _ in
                    self.logout()
                },
                AlertButtonModel(title: logoutAlert.cancelButton, type: .secondary) { _ in
                    
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func handleDeleteAccount() {
        let deleteAccountAlert = alertLang.DeleteAccountAlert
        let alert = AlertModel(
            title: deleteAccountAlert.title,
            message: deleteAccountAlert.message,
            buttons: [
                AlertButtonModel(title: deleteAccountAlert.deleteButton, type: .primary) { _ in
                    self.deleteAccount()
                },
                AlertButtonModel(title: deleteAccountAlert.cancelButton, type: .secondary) { _ in
                    
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    
    private func logout() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loggingOut ))
            do {
                try await accountService.logOut()
            } catch {
                logger.log(level: .error, tag: tag, message: "Logout failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    private func deleteAccount() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingAccount ))
            do {
                try await accountService.deleteAccount()
                // TODO: Need to clear all the connected scales and integrated entries to health kit and clear appearance choice
            } catch  {
                logger.log(level: .error, tag: tag, message: "Delete account failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Support Link Handlers
    func openPrivacy() {
        browserURL = legalURLs.privacyPolicy
        showPrivacyBrowser = true
    }
    
    func openTerms() {
        browserURL = legalURLs.termsOfService
        showTermsBrowser = true
    }
    
    func openHelp() {
        // TODO: Need to handle this
    }
    
    func openGreaterGoods() {
        browserURL = legalURLs.greaterGoodsWebsite
        showGreaterGoodsBrowser = true
    }
    
    // MARK: - Computed Profile Info
    var profileInitial: String {
        if let firstInitial = activeAccount?.firstName?.first {
            return String(firstInitial)
        }
        return ""
    }
    
    var profileName: String {
        if let firstName = activeAccount?.firstName {
            return firstName
        }
        return activeAccount?.firstName ?? ""
    }
    
    var profileEmail: String {
        activeAccount?.email ?? ""
    }
    
    // Derived setting values
    var biologicalSexText: String {
        guard let sex = activeAccount?.gender else { return "" }
        return sex.rawValue.capitalized
    }
    
    var activityLevelText: String {
        activeAccount?.weightSettings?.activityLevel?.rawValue.capitalized ?? ""
    }
    
    var heightText: String {
        // Height is stored as tenths-of-inches (e.g. "681" == 5′8″ / 173 cm)
        guard let heightStr = activeAccount?.weightSettings?.height,
              let storedHeightDouble = Double(heightStr) else {
            return ""
        }
        
        let storedHeight = Int(round(storedHeightDouble))
        
        switch activeAccount?.weightSettings?.weightUnit {
        case .kg: // Metric preference – show centimeters
            let cm = ConversionTools.convertStoredHeightToCm(storedHeight)
            return "\(cm) cm"
        case .lb: // Imperial preference – show feet & inches
            let feet = ConversionTools.convertStoredHeightToFeet(storedHeight)
            return "\(feet[0])′ \(feet[1])″"  // → 5′ 8″
        case .none:
            return ""
        }
    }
    
    var unitTypeText: String {
        switch activeAccount?.weightSettings?.weightUnit {
        case .kg: return commonLang.unitKgCm
        case .lb: return commonLang.unitLbsFeet
        case .none: return ""
        }
    }
    
    var weightlessText: String {
        (activeAccount?.weightlessSettings?.isWeightlessOn ?? false) ? "\(commonLang.on) - \(weightlessForm.weight.value) \(activeAccount?.weightSettings?.weightUnit?.rawValue ?? WeightUnit.lb.rawValue)" : commonLang.off
    }
    
    var notificationsOnText: String {
        guard let settings = activeAccount?.notificationSettings else {
            return commonLang.off
        }
        if settings.shouldSendEntryNotifications {
            return settings.shouldSendWeightInEntryNotifications ? "w/ Weight" : commonLang.on
        } else {
            return commonLang.off
        }
    }
    
    var streaksOnText: String { (activeAccount?.streaksSettings?.isStreakOn ?? false) ? commonLang.on : commonLang.off }
    
    var appearanceModeText: String {
        switch theme.appearanceMode {
        case .light:
            return commonLang.light
        case .dark:
            return commonLang.dark
        case .system:
            return commonLang.system
        }
    }
    
    // MARK: - Handle export
    func handleExport() {
        let alert = AlertModel(
            title: alertLang.CsvExportAlert.title,
            message: alertLang.CsvExportAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.CsvExportAlert.sendButton, type: .primary) { _ in
                    self.exportData()
                },
                AlertButtonModel(title: alertLang.CsvExportAlert.cancelButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    // MARK: - Edit Profile Helpers
    
    func handleEditProfileExit(router: Router<SettingsRoute>) {
        // If the form is not dirty, simply navigate back else show an alert
        if !editProfileForm.isDirty {
            resetEditProfileForm() // Reset form to pristine state
            router.navigateBack()
            return
        }
        let alert = AlertModel(
            title: alertLang.EditProfileExitAlert.title,
            message: alertLang.EditProfileExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.EditProfileExitAlert.exitButton, type: .primary) { _ in
                    self.resetEditProfileForm() // Reset form to pristine state
                    router.navigateBack()
                },
                AlertButtonModel(title: alertLang.EditProfileExitAlert.returnButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Populates the form with existing profile data (only once, on first load).
    func populateEditFormIfNeeded() {
        guard let account = activeAccount else { return }
        
        // Only populate if the user hasn't started editing (keep any in-flight changes).
        if !editProfileForm.isDirty {
            editProfileForm.firstName.value = account.firstName ?? ""
            editProfileForm.lastName.value  = account.lastName ?? ""
            editProfileForm.email.value     = account.email
            editProfileForm.zipcode.value   = account.zipcode ?? ""
            
            if let dobString = account.dob, let dob = DateTimeTools.parse(dobString) {
                editProfileForm.birthday.value = dob
            }
            editProfileForm.firstName.markAsPristine()
            editProfileForm.lastName.markAsPristine()
            editProfileForm.email.markAsPristine()
            editProfileForm.zipcode.markAsPristine()
            editProfileForm.birthday.markAsPristine()
            editProfileForm.validate()
        }
    }
    
    /// Persists the edited profile via `AccountService`, showing loader / toast as appropriate.
    func saveProfile(router: Router<SettingsRoute>) {
        guard editProfileForm.isValid else { return }
        
        let profile = Profile(
            firstName: removeWhiteSpace(editProfileForm.firstName.value),
            lastName:  removeWhiteSpace(editProfileForm.lastName.value),
            email:     removeWhiteSpace(editProfileForm.email.value),
            gender:  activeAccount?.gender ?? .male,
            zipcode:  removeWhiteSpace(editProfileForm.zipcode.value),
            dob: DateTimeTools.formatDateToYMD_Local(editProfileForm.birthday.value),
            weightUnit: activeAccount?.weightSettings?.weightUnit ?? .lb,
            height: activeAccount?.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0,
            activityLevel: activeAccount?.weightSettings?.activityLevel ?? .normal
        )
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
            do {
                let _ = try await accountService.updateProfile(profile)
                // Reset dirty flags so the form becomes pristine again.
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.profileSaved))
                resetEditProfileForm()
                router.navigateBack()
                logger.log(level: .info, tag: tag, message: "Profile updated successfully")
            } catch {
                var toastMessage: String?
                let toastTitle: String = toastLang.errorUpdatingProfile
                switch error {
                case HTTPError.badRequest:
                    toastMessage = toastLang.emailInUse
                case HTTPError.noInternet:
                    break
                case HTTPError.serverError:
                    toastMessage = toastLang.serverError
                default:
                    toastMessage = toastLang.somethingWentWrong
                }
                if let message = toastMessage {
                    notificationService.showToast(ToastModel(title: toastTitle, message: message))
                }
                logger.log(level: .error, tag: tag, message: "Profile update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Reset Helpers
    
    /// Resets the edit-profile form back to a pristine state while keeping current account data pre-filled.
    /// Useful when the user discards changes or after a successful save so future edits start clean.
    func resetEditProfileForm() {
        // Replace with a brand-new form instance (drops any Combine subscriptions tied to the old one).
        editProfileForm = EditProfileForm()
        
        // Re-populate with the latest account data so the screen isn't blank.
        populateEditFormIfNeeded()
    }
    
    // MARK: - Change Password Helpers
    
    func handleChangePasswordExit(router: Router<SettingsRoute>) {
        if !changePasswordForm.isDirty {
            resetChangePasswordForm()
            router.navigateBack()
            return
        }
        
        let alert = AlertModel(
            title: AlertStrings.ChangePasswordExitAlert.title,
            message: AlertStrings.ChangePasswordExitAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.ChangePasswordExitAlert.exitButton, type: .primary) { _ in
                    self.resetChangePasswordForm()
                    router.navigateBack()
                },
                AlertButtonModel(title: AlertStrings.ChangePasswordExitAlert.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Persists the password change via `AccountService`.
    func savePassword(router: Router<SettingsRoute>) {
        guard changePasswordForm.isValid else { return }
        
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.saving))
            do {
                try await accountService.updatePassword(
                    oldPassword: changePasswordForm.currentPassword.value,
                    newPassword: changePasswordForm.newPassword.value
                )
                notificationService.showToast(ToastModel(message: toastLang.passwordUpdated))
                resetChangePasswordForm()
                router.navigateBack()
                logger.log(level: .info, tag: tag, message: "Password updated successfully")
            } catch {
                var toastMessage: String = ToastStrings.somethingWentWrong
                let toastTitle: String = ToastStrings.errorUpdatingPassword
                switch error {
                case HTTPError.badRequest, HTTPError.unauthorized:
                    toastMessage = toastLang.restartAndTryAgain
                case HTTPError.noInternet:
                    break
                case HTTPError.serverError:
                    toastMessage = toastLang.serverError
                default:
                    toastMessage = toastLang.somethingWentWrong
                }
                notificationService.showToast(ToastModel(title: toastTitle, message: toastMessage))
                logger.log(level: .error, tag: tag, message: "Password update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Export Data
    private func exportData() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingCsv))
            do {
                try await entryService.exportCSV()
                notificationService.showToast(ToastModel(message: toastLang.csvExported))
            } catch {
                logger.log(level: .error, tag: tag, message: "CSV export failed:", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet:
                    break
                default:
                    notificationService.showToast(ToastModel(
                        message: toastLang.csvExportError)
                    )
                }
            }
            notificationService.dismissLoader()
        }
    }
    
    /// Resets the change-password form.
    func resetChangePasswordForm() {
        changePasswordForm = ChangePasswordForm()
    }
    
    // MARK: - Weight Unit Helpers
    /// Updates the user's preferred weight/height unit and persists it via `AccountService`.
    /// - Parameter unit: The newly selected `WeightUnit`.
    func updateWeightUnit(_ unit: WeightUnit) {
        guard let account = activeAccount else { return }
        // Skip if no change
        guard account.weightSettings?.weightUnit != unit else { return }
        
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let bodyComp = BodyComp(
                    weightUnit: unit,
                    height: account.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0,
                    activityLevel: account.weightSettings?.activityLevel ?? .normal)
                _ = try await accountService.updateBodyComp(bodyComp)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.unitSettingUpdated))
                logger.log(level: .info, tag: tag, message: "Weight unit updated to \(unit.rawValue)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Weight unit update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Activity Level Helpers
    func updateActivityLevel(_ level: ActivityLevel) {
        guard let account = activeAccount else { return }
        guard account.weightSettings?.activityLevel != level else { return }
        
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let bodyComp = BodyComp(
                    weightUnit: account.weightSettings?.weightUnit ?? .lb,
                    height: account.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0,
                    activityLevel: level
                )
                _ = try await accountService.updateBodyComp(bodyComp)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.activitySettingUpdated))
                logger.log(level: .info, tag: tag, message: "Activity level updated to \(level.rawValue)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Activity level update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Notification Preference Helpers
    func updateNotificationPreference(_ preference: NotificationPreference) {
        guard let account = activeAccount else { return }
        let currentPref: NotificationPreference = {
            let settings = account.notificationSettings
            if settings?.shouldSendEntryNotifications == true {
                return settings?.shouldSendWeightInEntryNotifications == true ? .enableWithWeight : .enable
            } else {
                return .disable
            }
        }()
        guard currentPref != preference else { return }
        
        let notifications = Notifications(
            shouldSendEntryNotifications: preference != .disable,
            shouldSendWeightInEntryNotifications: preference == .enableWithWeight
        )
        
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                _ = try await accountService.updateNotifications(notifications: notifications)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.notificationSettingUpdated))
                logger.log(level: .info, tag: tag, message: "Notification preference updated to \(preference)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Notification preference update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Streak Helpers
    func updateStreakStatus(_ isOn: Bool) {
        guard let account = activeAccount else { return }
        guard account.streaksSettings?.isStreakOn != isOn else { return }
        
        let timestamp = DateTimeTools.getCurrentDatetimeIsoString()
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                _ = try await accountService.updateStreak(isStreakOn: isOn, streakTimestamp: timestamp)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.streakSettingUpdated))
                logger.log(level: .info, tag: tag, message: "Streak status updated to \(isOn)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Streak status update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Gender Helpers
    func updateGender(_ sex: Sex) {
        guard let account = activeAccount else { return }
        guard account.gender != sex else { return }
        
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let profile = Profile(
                    firstName: account.firstName ?? "",
                    lastName: account.lastName ?? "",
                    email: account.email,
                    gender: sex,
                    zipcode: account.zipcode ?? "",
                    dob: account.dob ?? "",
                    weightUnit: account.weightSettings?.weightUnit ?? .lb,
                    height: account.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0,
                    activityLevel: account.weightSettings?.activityLevel ?? .normal
                )
                _ = try await accountService.updateProfile(profile, canSaveOffline: true)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.profileSaved))
                logger.log(level: .info, tag: tag, message: "Gender updated to \(sex.rawValue)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Gender update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Weightless Helpers
    /// Persists weightless mode changes.
    /// - Parameters:
    ///   - isOn: Whether weightless mode is enabled.
    ///   - storedWeight: Anchor weight in *stored units* (tenths-of-lbs).
    ///   - onSuccess: optional completion handler after successful save.
    func updateWeightlessMode(isOn: Bool, storedWeight: Int, dismiss: DismissAction) {
        guard let account = activeAccount else { return }
        let currentOn = account.weightlessSettings?.isWeightlessOn ?? false
        let currentWeightStored = Int(account.weightlessSettings?.weightlessWeight ?? 0)
        if currentOn == isOn && currentWeightStored == storedWeight { return }
        
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let timestamp = DateTimeTools.getCurrentDatetimeIsoString()
                _ = try await accountService.updateWeightless(isWeightlessOn: isOn, weightlessTimestamp: timestamp, weightlessWeight: Double(storedWeight))
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.weightlessUpdated))
                logger.log(level: .info, tag: tag, message: "Weightless settings updated")
                dismiss() // Dismiss the view after successful save
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.errorUpdatingWeightless, message: toastLang.restartAndTryAgain))
                logger.log(level: .error, tag: tag, message: "Weightless update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Weightless Form Helpers
    
    /// Populates the Weightless settings form with the current account values (only once, when pristine).
    func populateWeightlessFormIfNeeded() {
        guard let account = activeAccount else { return }
        
        if let isOn = account.weightlessSettings?.isWeightlessOn {
            weightlessForm.isOn.value = isOn
            weightlessForm.isOn.markAsPristine()
        }
        
        if let storedWeight = account.weightlessSettings?.weightlessWeight {
            // Convert stored tenths-of-lbs value to display unit.
            let unit = account.weightSettings?.weightUnit ?? .lb
            let display: Double = unit == .kg
            ? ConversionTools.convertStoredToKg(Int(storedWeight))
            : ConversionTools.convertStoredToLbs(Int(storedWeight))
            
            weightlessForm.weight.value = String(format: "%.1f", display)
            weightlessForm.weight.markAsPristine()
        }
        let maxWeight = account.weightSettings?.weightUnit ?? .lb == .kg ? 450.0 : 999.0

        // Remove old validator
        weightlessForm.weight.removeValidator(ofType: .maxValue)

        // Add new validator
        let validator = Validator.maxValue(maxWeight)
        weightlessForm.weight.addValidator(validator)
        weightlessForm.validate()
    }
    
    /// Handles the exit action from the Weightless screen. Shows an alert if there are unsaved changes.
    func handleWeightlessExit(dismiss: DismissAction) {
        if !weightlessForm.isDirty {
            resetWeightlessForm()
            dismiss()
            return
        }
        
        let alert = AlertModel(
            title: alertLang.WeightLessExitAlert.title,
            message: alertLang.WeightLessExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.WeightLessExitAlert.exitButton, type: .primary) { _ in
                    self.resetWeightlessForm()
                    dismiss()
                },
                AlertButtonModel(title: alertLang.WeightLessExitAlert.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Validates and saves Weightless settings to the server.
    func saveWeightless(dismiss: DismissAction)  {
        // Run validation first.
        weightlessForm.validate()
        
        // No changes – simply dismiss.
        guard weightlessForm.isDirty else {
            return
        }
        
        // If toggle is on, ensure the weight field is valid.
        if weightlessForm.isOn.value && weightlessForm.weight.isInvalid { return }
        
        // Convert display value to stored tenths-of-lbs (server format).
        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        let storedWeight: Int = {
            if let val = Double(weightlessForm.weight.value) {
                return ConversionTools.convertDisplayToStored(val, isMetric: unit == .kg)
            }
            return 0
        }()
        updateWeightlessMode(isOn: weightlessForm.isOn.value, storedWeight: storedWeight, dismiss: dismiss)
    }
    
    /// Resets the Weightless form to a pristine state.
    func resetWeightlessForm() {
        weightlessForm = WeightlessForm()
        populateWeightlessFormIfNeeded()
    }

    // MARK: - Height Helpers
    /// Syncs the picker selections with the currently stored height.
    private func syncHeightPickers() {
        guard let storedString = activeAccount?.weightSettings?.height,
              let storedDouble = Double(storedString) else { return }
        let stored = Int(round(storedDouble))
        let selections = ConversionTools.pickerSelections(from: stored)
        selectedHeightInches = selections.inches
        selectedHeightCm     = selections.cm
    }

    /// Presents the correct picker sheet based on the user's current unit preference.
    func showHeightPicker() {
        if activeAccount?.weightSettings?.weightUnit == .kg {
            showHeightCmPicker = true
        } else {
            showHeightInchesPicker = true
        }
    }

    /// Converts the chosen picker values to stored format and persists via `updateBodyComp`.
    /// - Parameters:
    ///   - fromMetric: `true` if the picker values are metric (cm), `false` for imperial.
    ///   - values: Picker column values chosen by the user.
    func updateHeight(fromMetric: Bool, values: [String]) {
        let storedHeight: Int
        if fromMetric {
            let cm = Int(values.joined()) ?? 178
            storedHeight = ConversionTools.convertCmToStoredHeight(cm)
        } else {
            let feet = Int(values[0]) ?? 5
            let inches = Int(values[1]) ?? 10
            let totalInches = (feet * 12) + inches
            storedHeight = ConversionTools.convertInchesToStoredHeight(totalInches)
        }

        // Persist change only if it differs
        guard let account = activeAccount,
              let currentStoredStr = account.weightSettings?.height,
              let currentStoredDouble = Double(currentStoredStr) else { return }

        let currentStored = Int(round(currentStoredDouble))
        guard currentStored != storedHeight else { return }

        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            let bodyComp = BodyComp(
                weightUnit: account.weightSettings?.weightUnit ?? .lb,
                height: Double(storedHeight),
                activityLevel: account.weightSettings?.activityLevel ?? .normal
            )
            do {
                _ = try await accountService.updateBodyComp(bodyComp)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.heightUpdated))
                logger.log(level: .info, tag: tag, message: "Height updated to stored value: \(storedHeight)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.errorUpdatingHeight, message: toastLang.pleaseTryAgain))
                logger.log(level: .error, tag: tag, message: "Height update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }

    // MARK: - Goal Form Helpers
    /// Populates the Goal settings form with existing account goal values *once* (when the form is pristine).
    func populateGoalFormIfNeeded() {
        guard let account = activeAccount else { return }

        // Skip if user has already started editing.
        guard !goalForm.isDirty else { return }

        // Goal type
        if let gType = account.goalSettings?.goalType {
            goalForm.goalType.value = gType == .maintain ? GoalType.maintain.rawValue : GoalTypeSegment.losegainValue
            goalForm.goalType.markAsPristine()
        }
        Task {
            do {
                let latestEntry = try await entryService.getLatestEntry()
                if let latestWeight = latestEntry?.scaleEntry?.weight {
                    let unit = account.weightSettings?.weightUnit ?? .lb
                    let display = unit == .kg ? ConversionTools.convertStoredToKg(Int(latestWeight)) : ConversionTools.convertStoredToLbs(Int(latestWeight))
                    goalForm.currentWeight.value = String(format: "%.1f", display)
                    goalForm.currentWeight.markAsPristine()
                }

                if let goalW = account.goalSettings?.goalWeight {
                    let unit = account.weightSettings?.weightUnit ?? .lb
                    let display = unit == .kg ? ConversionTools.convertStoredToKg(Int(goalW)) : ConversionTools.convertStoredToLbs(Int(goalW))
                    goalForm.goalWeight.value = String(format: "%.1f", display)
                    goalForm.goalWeight.markAsPristine()
                }

                // Update max-value validator according to unit.
                updateGoalWeightValidators()

                goalForm.validate()
            } catch {
                
            }
        }

    }

    /// Updates the max-weight validator whenever the preferred unit changes.
    private func updateGoalWeightValidators() {
        let isMetric = (activeAccount?.weightSettings?.weightUnit ?? .lb) == .kg
        let maxWeight = isMetric ? 450.0 : 999.0

        [goalForm.currentWeight, goalForm.goalWeight].forEach { ctrl in
            ctrl.removeValidator(ofType: .maxValue)
            let validator = Validator.maxValue(maxWeight)
            ctrl.addValidator(validator)
        }
    }

    /// Handles dismissal of the Goal sheet, showing an alert if there are unsaved changes.
    func handleGoalExit(dismiss: DismissAction) {
        if !goalForm.isDirty {
            resetGoalForm()
            dismiss()
            return
        }

        let alert = AlertModel(
            title: alertLang.WeightLessExitAlert.title, // Re-use generic exit alert strings
            message: alertLang.WeightLessExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.WeightLessExitAlert.exitButton, type: .primary) { _ in
                    self.resetGoalForm()
                    dismiss()
                },
                AlertButtonModel(title: alertLang.WeightLessExitAlert.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Validates the form and persists the goal via `AccountService`.
    func saveGoal(dismiss: DismissAction) {
        goalForm.validate()

        guard goalForm.isDirty, goalForm.isValid else { return }

        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        let isMetric = unit == .kg

        let convert = { (valString: String) -> Int in
            let val = Double(valString) ?? 0.0
            return ConversionTools.convertDisplayToStored(val, isMetric: isMetric)
        }

        // Build Goal payload
        let goalTypeValue = goalForm.goalType.value
        let currentDisplay = goalForm.currentWeight.value
        let targetDisplay  = goalForm.goalWeight.value

        let goalStored    = convert(targetDisplay)
        let initialStored: Int = {
            if goalTypeValue == GoalType.maintain.rawValue {
                return goalStored
            } else {
                return convert(currentDisplay)
            }
        }()

        let goalPayload: Goal
        if goalTypeValue == GoalType.maintain.rawValue {
            goalPayload = Goal(type: .maintain, goalWeight: goalStored, initialWeight: goalStored, goalType: .maintain)
        } else {
            let derivedType: GoalType = goalStored > initialStored ? .gain : .lose
            goalPayload = Goal(type: derivedType, goalWeight: goalStored, initialWeight: initialStored, goalType: derivedType)
        }

        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.saving))
            do {
                _ = try await accountService.createGoal(goalPayload)
                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.profileSaved))
                logger.log(level: .info, tag: tag, message: "Goal updated successfully")
                resetGoalForm()
                dismiss()
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.somethingWentWrong))
                logger.log(level: .error, tag: tag, message: "Goal update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }

    /// Resets to pristine state and re-sync with account.
    private func resetGoalForm() {
        goalForm = GoalForm()
        populateGoalFormIfNeeded()
    }
}
