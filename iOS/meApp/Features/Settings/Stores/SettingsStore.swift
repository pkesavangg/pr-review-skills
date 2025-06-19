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
    
    init() {
        accountService.$activeAccount
            .sink { [weak self] account in
                self?.activeAccount = account
                self?.populateEditFormIfNeeded()
            }
            .store(in: &accountService.cancellables)
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
        (activeAccount?.weightlessSettings?.isWeightlessOn ?? false) ? commonLang.on : commonLang.off
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
}
