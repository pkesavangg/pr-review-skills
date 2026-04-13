//
//  SettingsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import Combine

// swiftlint:disable file_length
// This file intentionally aggregates all settings management logic.
// Breaking it into smaller files would fragment related functionality and reduce maintainability.
import Foundation
import SwiftUI

// MARK: - Settings Store

/// A store to manage user settings and account actions.
@MainActor
// swiftlint:disable:next type_body_length
class SettingsStore: ObservableObject {
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var feedService: FeedServiceProtocol
    @Injector var goalAlertService: GoalAlertServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var integrationService: IntegrationServiceProtocol
    @Injector var productTypeStore: ProductTypeStoreProtocol
    private let httpClient = HTTPClient.shared
    var theme = Theme.shared
    let kvStore = KvStorageService.shared
    var useModalPicker = DeviceUtils.useModalPicker

    @Published var activeAccount: Account?

    // Edit-Profile flow
    @Published var editProfileForm = EditProfileForm()
    // Change-Password flow
    @Published var changePasswordForm = ChangePasswordForm()
    // Weightless-mode form
    @Published var weightlessForm = WeightlessForm()
    // Track initial toggle state to detect actual changes
    private var initialWeightlessToggleState: Bool = false
    // Goal-setting form
    @Published var goalForm = GoalForm()

    var cancellables = Set<AnyCancellable>()

    // Localization strings
    private let toastLang = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let legalURLs = AppConstants.LegalURLs.self

    private let hasSeenAddMultipleAccountsModalKey = KvStorageKeys.addMultipleAccountsModal.rawValue

    let tag = "SettingsStore"

    // MARK: - In-App Browser State

    @Published var showPrivacyBrowser: Bool = false
    @Published var showTermsBrowser: Bool = false
    @Published var showGreaterGoodsBrowser: Bool = false
    @Published var browserURL: URL?

    // MARK: - Weightless Page State

    @Published var showWeightLessPage: Bool = false

    // MARK: - Goal Page State

    @Published var showGoalPage: Bool = false
    @Published var selectedSegment: GoalTypeSegment = .loseGain
    @Published var latestWeight: Int = 0

    // MARK: - Message Indicators

    @Published var canShowFeedNotificationBadge: Bool = true

    // MARK: - Log Out All Accounts

    @Published var canShowLogOutAllItems = false

    // MARK: - Entry State

    @Published var hasEntries: Bool = false

    // MARK: - Device-Type Visibility (driven by ProductTypeStore.availableItems)

    private var availableItems: [ProductSelection] {
        productTypeStore.availableItems
    }

    var hasWeightScale: Bool {
        availableItems.contains(.myWeight)
    }

    var hasBpmDevice: Bool {
        availableItems.contains(.myBloodPressure)
    }

    var hasBabyScale: Bool {
        availableItems.contains { if case .baby = $0 { return true } else { return false } }
    }

    private var signedUpWithBabyScale: Bool {
        guard let accountId = activeAccount?.accountId,
              let rawValue = kvStore.getValue(forKey: KvStorageKeys.selectedSignupDeviceTypeKey(for: accountId)) as? String
        else { return false }
        return SignupDeviceType(rawValue: rawValue) == .babyScale
    }

    var shouldShowIntegrations: Bool {
        hasWeightScale || hasBpmDevice
    }

    var shouldShowUnitType: Bool {
        hasWeightScale || hasBabyScale || hasBpmDevice
    }

    var shouldShowNotifications: Bool {
        hasWeightScale || hasBabyScale || hasBpmDevice
    }

    var shouldShowWeightScaleSection: Bool {
        hasWeightScale
    }

    var shouldShowMyKids: Bool {
        hasBabyScale || signedUpWithBabyScale
    }

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
    let heightCmOptions = ConversionTools.heightCmOptions

    // MARK: - Dialog state controls moved to store

    /// Controls the presentation of the appearance picker (sheet fallback or centered modal).
    @Published var showAppearancePicker: Bool = false
    /// Controls the presentation of the notification preference picker (sheet fallback or centered modal).
    @Published var showNotificationPicker: Bool = false
    /// Controls the presentation of the gender picker (sheet fallback or centered modal).
    @Published var showGenderPicker: Bool = false
    /// Controls the presentation of the unit picker (sheet fallback or centered modal).
    @Published var showUnitPicker: Bool = false
    /// Controls the presentation of the activity level picker (sheet fallback or centered modal).
    @Published var showActivityPicker: Bool = false

    init() {
        accountService.activeAccountPublisher
            .sink { [weak self] account in
                self?.activeAccount = account
                self?.populateEditFormIfNeeded()
                self?.populateWeightlessFormIfNeeded()
                self?.syncHeightPickers()
            }
            .store(in: &cancellables)

        accountService.allAccountsPublisher
            .sink { [weak self] allAccounts in
                self?.canShowLogOutAllItems = allAccounts.filter { $0.isLoggedIn == true }.count > 1
            }
            .store(in: &cancellables)

        self.populateWeightlessFormIfNeeded()

        // Listen to theme appearance changes so SettingsScreen refreshes immediately
        Theme.shared.$appearanceMode
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                // Broadcast a manual change so any computed properties that depend on Theme refresh
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)

        // Listen to product type changes to update conditional settings visibility
        ProductTypeStore.shared.$availableItems
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)

        observeNotificationBadgeChanges()
        Task { await self.checkEntries() }

        // Listen to entry changes to update hasEntries
        entryService.entrySaved
            .sink { [weak self] _ in
                Task { await self?.checkEntries() }
            }
            .store(in: &cancellables)

        entryService.entryDeleted
            .sink { [weak self] _ in
                Task { await self?.checkEntries() }
            }
            .store(in: &cancellables)
    }

    func handleLogout() {
        let logoutAlert = alertLang.LogoutAlert
        let alert = AlertModel(
            title: logoutAlert.title,
            message: logoutAlert.message,
            buttons: [
                AlertButtonModel(title: logoutAlert.logoutButton, type: .danger) { _ in
                    self.logout()
                },
                AlertButtonModel(title: logoutAlert.cancelButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func handleLogoutForAllAccounts() {
        let logoutAlert = alertLang.LogoutAllAccountAlert
        let alert = AlertModel(
            title: logoutAlert.title,
            message: logoutAlert.message,
            buttons: [
                AlertButtonModel(title: logoutAlert.logoutButton, type: .danger) { _ in
                    self.logoutAllAccounts()
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
                AlertButtonModel(title: deleteAccountAlert.deleteButton, type: .danger) { _ in
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
            logger.log(level: .info, tag: tag, message: "Settings logout started. accountId=\(activeAccount?.accountId ?? "nil")")
            notificationService.showLoader(LoaderModel(text: loaderLang.loggingOut))
            do {
                try await accountService.logOut()
                logger.log(level: .success, tag: tag, message: "Settings logout succeeded")
            } catch {
                logger.log(level: .error, tag: tag, message: "Logout failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }

    private func logoutAllAccounts() {
        Task {
            logger.log(level: .info, tag: tag, message: "Settings logout-all started")
            notificationService.showLoader(LoaderModel(text: loaderLang.loggingOut))
            do {
                try await accountService.logOutAllAccounts()
                logger.log(level: .success, tag: tag, message: "Settings logout-all succeeded")
            } catch {
                logger.log(level: .error, tag: tag, message: "Logout all failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }

    private func deleteAccount() {
        Task {
            logger.log(level: .info, tag: tag, message: "Settings delete-account started. accountId=\(activeAccount?.accountId ?? "nil")")
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingAccount))
            do {
                // Delete connected R4 scales before deleting account
                await deleteConnectedR4Scales()

                // Clear appearance settings for the account being deleted
                let accountId = activeAccount?.accountId
                if let accountId = accountId {
                    // Clear account-specific appearance key
                    let appearanceKey = KvStorageKeys.appearanceModeKey(for: accountId)
                    kvStore.clearValue(forKey: appearanceKey)
                    theme.loadAppearanceModeForAccount()
                }

                // Clear integration data (HealthKit, etc.) before deleting account
                try await integrationService.clearIntegration()

                try await accountService.deleteAccount()
                logger.log(level: .success, tag: tag, message: "Settings delete-account succeeded")
            } catch {
                let toastMessage: String = ToastStrings.somethingWentWrong
                notificationService.showToast(ToastModel(message: toastMessage))
                logger.log(level: .error, tag: tag, message: "Delete account failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }

    /// Deletes all connected R4 scales during account deletion process.
    /// This method handles the cleanup of scale connections before account deletion.
    private func deleteConnectedR4Scales() async {
        let result = await bluetoothService.deleteR4Scales()
        switch result {
        case .success:
            logger.log(level: .info, tag: tag, message: "Successfully deleted connected R4 scales")
        case let .failure(error):
            logger.log(level: .error, tag: tag, message: "Failed to delete connected R4 scales: \(error.localizedDescription)")
            // Continue with account deletion even if scale deletion fails
        }
    }

    // MARK: - Support Link Handlers

    func openPrivacy() {
        browserURL = legalURLs.privacyPolicy
        showPrivacyBrowser = true
        logger.log(level: .info, tag: tag, message: "Opening settings privacy policy browser modal. url=\(browserURL?.absoluteString ?? "nil")")
    }

    func openTerms() {
        browserURL = legalURLs.termsOfService
        showTermsBrowser = true
        logger.log(level: .info, tag: tag, message: "Opening settings terms browser modal. url=\(browserURL?.absoluteString ?? "nil")")
    }

    func openGreaterGoods() {
        browserURL = legalURLs.greaterGoodsWebsite
        showGreaterGoodsBrowser = true
        logger.log(level: .info, tag: tag, message: "Opening Greater Goods browser modal. url=\(browserURL?.absoluteString ?? "nil")")
    }

    // MARK: - Computed Profile Info

    var profileInitial: String {
        if let firstName = activeAccount?.firstName {
            return firstName.firstAlphabeticCharacter()
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
              let storedHeightDouble = Double(heightStr)
        else {
            return ""
        }

        let storedHeight = Int(round(storedHeightDouble))

        let isMetric = activeAccount?.weightSettings?.weightUnit == .kg
        return ConversionTools.convertToFormattedHeight(storedHeight, isMetric: isMetric)
    }

    var unitTypeText: String {
        switch activeAccount?.weightSettings?.weightUnit {
        case .kg: return commonLang.unitKgCm
        case .lb: return commonLang.unitLbsFeet
        case .none: return ""
        }
    }

    // Edit profile form specific display values
    var editBiologicalSexText: String {
        editProfileForm.gender.value.rawValue.capitalized
    }

    var editHeightText: String {
        let heightStr = editProfileForm.height.value
        guard !heightStr.isEmpty,
              let storedDouble = Double(heightStr)
        else {
            return ""
        }

        let storedHeight = Int(round(storedDouble))
        let isMetric = activeAccount?.weightSettings?.weightUnit == .kg
        return ConversionTools.convertToFormattedHeight(storedHeight, isMetric: isMetric)
    }

    var weightlessText: String {
        let isOn = activeAccount?.weightlessSettings?.isWeightlessOn ?? false
        let storedWeight = activeAccount?.weightlessSettings?.weightlessWeight

        guard isOn else {
            return commonLang.off
        }

        guard let storedWeight = storedWeight else {
            return "\(commonLang.on) - Not Set"
        }

        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        let display: Double = unit == .kg
            ? ConversionTools.convertStoredToKg(Int(storedWeight))
            : ConversionTools.convertStoredToLbs(Int(storedWeight))

        let formattedValue = String(format: "%.1f", display)
        let unitLabel = WeightValueConvertor.unitForDisplay(value: display, unit: unit)
        let result = "\(commonLang.on) - \(formattedValue) \(unitLabel)"
        return result
    }

    var notificationsOnText: String {
        guard let settings = activeAccount?.notificationSettings else {
            return commonLang.off
        }
        if settings.shouldSendEntryNotifications {
            return settings.shouldSendWeightInEntryNotifications ? "\(commonLang.on) w/ Weight" : "\(commonLang.on) w/o Weight"
        } else {
            return commonLang.off
        }
    }

    var streaksOnText: String { (activeAccount?.streaksSettings?.isStreakOn ?? false) ? commonLang.on : commonLang.off }

    /// Dynamic title for the Messages row. When the feed badge is visible, append unread count.
    var messagesTitleText: String {
        guard canShowFeedNotificationBadge else { return SettingsStrings.messages }
        let count = feedService.getUnreadFeedCount()
        return SettingsStrings.messagesWithNew(count)
    }

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

    var isGoalFormValid: Bool {
        goalForm.isValidForSave()
    }

    func isGoalFormValid(focusedField: FocusField?) -> Bool {
        goalForm.isValidForSave(focusedField: focusedField)
    }

    /// Determines if the Weightless form is valid for saving
    /// Save enabled only when toggle changed OR valid form changes exist
    var isWeightLessFormValid: Bool {
        let hasToggleChanged = weightlessForm.isOn.value != initialWeightlessToggleState

        // If turned OFF → only toggle change matters
        guard weightlessForm.isOn.value else {
            return hasToggleChanged
        }

        // If turned ON → form must be valid, and allow either toggle change OR valid form changes
        let weightValue = Double(weightlessForm.weight.value) ?? 0.0
        return weightlessForm.isValid &&
            (hasToggleChanged ||
             (weightlessForm.isDirty && weightValue != 0.0))
    }

    /// Checks if there are actual unsaved changes (for exit confirmation)
    var hasWeightlessChanges: Bool {
        (weightlessForm.isOn.value != initialWeightlessToggleState) || weightlessForm.isDirty
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

    /// Populates the form with existing profile data (only once, on first load).
    func populateEditFormIfNeeded() {
        guard let account = activeAccount else { return }

        // Only populate if the user hasn't started editing (keep any in-flight changes).
        if !editProfileForm.isDirty {
            editProfileForm.firstName.value = account.firstName ?? ""
            editProfileForm.lastName.value = account.lastName ?? ""
            editProfileForm.email.value = account.email
            editProfileForm.zipcode.value = account.zipcode ?? ""
            editProfileForm.gender.value = account.gender ?? .male

            if let dobString = account.dob, let dob = DateTimeTools.parse(dobString) {
                editProfileForm.birthday.value = dob
            }

            // Populate height from account (convert to displayed format)
            if let heightStr = account.weightSettings?.height,
               let heightDouble = Double(heightStr) {
                editProfileForm.height.value = String(Int(heightDouble))
            }

            editProfileForm.firstName.markAsPristine()
            editProfileForm.lastName.markAsPristine()
            editProfileForm.email.markAsPristine()
            editProfileForm.zipcode.markAsPristine()
            editProfileForm.birthday.markAsPristine()
            editProfileForm.gender.markAsPristine()
            editProfileForm.height.markAsPristine()
            editProfileForm.validate()
        }
    }

    // Persists the edited profile via `AccountService`, showing loader / toast as appropriate.
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    func saveProfile(router: Router<SettingsRoute>) {
        guard editProfileForm.isValid else { return }

        let firstNameValue = removeWhiteSpace(editProfileForm.firstName.value)
        let dobValue = DateTimeTools.formatDateToYMD_Local(editProfileForm.birthday.value)

        // Check if firstName or dob changed (only these affect R4 scale profile)
        let firstNameChanged = firstNameValue != (activeAccount?.firstName ?? "")
        let dobChanged = dobValue != (activeAccount?.dob ?? "")
        let shouldUpdateR4Profile = firstNameChanged || dobChanged

        // Convert form height to Double for the profile
        let formHeightDouble = Double(editProfileForm.height.value) ?? (activeAccount?.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0)

        let profile = Profile(
            firstName: firstNameValue,
            lastName: removeWhiteSpace(editProfileForm.lastName.value),
            email: removeWhiteSpace(editProfileForm.email.value),
            gender: editProfileForm.gender.value,
            zipcode: removeWhiteSpace(editProfileForm.zipcode.value),
            dob: dobValue,
            weightUnit: activeAccount?.weightSettings?.weightUnit ?? .lb,
            height: formHeightDouble,
            activityLevel: activeAccount?.weightSettings?.activityLevel ?? .normal
        )
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
            do {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "SettingsStore: Saving profile weightUnit=\(profile.weightUnit) activityLevel=\(profile.activityLevel)"
                )
                _ = try await accountService.updateProfile(profile)
                // Also update body composition (height, weightUnit, activityLevel)
                let bodyComp = BodyComp(
                    weightUnit: activeAccount?.weightSettings?.weightUnit ?? .lb,
                    height: formHeightDouble,
                    activityLevel: activeAccount?.weightSettings?.activityLevel ?? .normal
                )
                _ = try await accountService.updateBodyComp(bodyComp)
                // Only update R4 scales profile if firstName or dob changed
                if shouldUpdateR4Profile {
                    // Update R4 scales profile and check for USER_SELECTION_IN_PROGRESS status
                    let profileUpdateResult = await bluetoothService.updateUserProfileForR4Scales()
                    logger.log(level: .info, tag: tag, message: "updateUserProfileForR4Scales result updateProfile: \(profileUpdateResult)")
                    switch profileUpdateResult {
                    case let .success(statusArray):
                        // Suppress success toast during user selection to prevent misleading feedback,
                        // since the scale profile isn't updated at that time.
                        if hasUserSelectionInProgress(statusArray: statusArray) {
                            // Show updates pending alert instead
                            showUpdatesPendingAlert()
                        } else {
                            notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.profileSaved))
                        }
                    case .failure:
                        // If update fails (e.g., already in progress from subscription),
                        // still show success toast since profile was saved successfully
                        notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.profileSaved))
                    }
                } else {
                    // No R4 profile update needed, just show success toast
                    notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.profileSaved))
                }

                resetEditProfileForm()
                router.navigateBack()
                logger.log(level: .info, tag: tag, message: "Profile updated successfully")
            } catch {
                var toastMessage: String?
                let toastTitle: String = toastLang.errorUpdatingProfile
                switch error {
                case let HTTPError.apiError(message, _) where message == commonLang.emailAlreadyInUse:
                    toastMessage = toastLang.emailInUse
                case HTTPError.badRequest, HTTPError.statusCode(409):
                    toastMessage = toastLang.emailInUse
                case HTTPError.apiError:
                    toastMessage = toastLang.somethingWentWrong
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

    // MARK: - Field Touch / Validation

    /// Marks a specific field as touched and triggers validation.
    /// Used by input views to show field errors as soon as the user leaves a field
    /// or presses the keyboard "Next/Done" button.
    /// - Parameter field: The field to touch and validate.
    func touchAndValidate(field: FocusField) {
        var didUpdate = true

        switch field {
        case .currentPassword:
            changePasswordForm.currentPassword.markAsTouched()
            changePasswordForm.currentPassword.validate()
            changePasswordForm.validate()
        case .newPassword:
            changePasswordForm.newPassword.markAsTouched()
            changePasswordForm.newPassword.validate()
            changePasswordForm.validate()
        case .confirmNewPassword:
            changePasswordForm.confirmNewPassword.markAsTouched()
            changePasswordForm.confirmNewPassword.validate()
            changePasswordForm.validate()
        default:
            didUpdate = false
        }

        // Trigger view update to show error messages only if we processed a field
        if didUpdate {
            objectWillChange.send()
        }
    }

    /// Call this from `onEditingChanged` for fields where we want to validate on blur.
    func handleEditingChanged(_ isEditing: Bool, field: FocusField) {
        guard !isEditing else { return }

        switch field {
        case .currentPassword where changePasswordForm.currentPassword.isTouched,
             .newPassword where changePasswordForm.newPassword.isTouched,
             .confirmNewPassword where changePasswordForm.confirmNewPassword.isTouched:
            return
        default:
            break
        }
        touchAndValidate(field: field)
    }

    /// Presents a Change-Password exit confirmation alert using shared strings.
    /// - Parameters:
    ///   - onExit:    Closure to execute when user confirms exiting.
    ///   - onCancel:  Optional closure when user cancels.
    private func presentChangePasswordExitAlert(onExit: @escaping () -> Void,
                                                onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: alertLang.ChangePasswordExitAlert.title,
            message: alertLang.ChangePasswordExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.ChangePasswordExitAlert.exitButton, type: .primary) { _ in
                    onExit()
                },
                AlertButtonModel(title: alertLang.ChangePasswordExitAlert.returnButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func handleChangePasswordExit(router: Router<SettingsRoute>) {
        // If form is pristine just pop and bail.
        guard changePasswordForm.isDirty else {
            router.navigateBack()
            resetChangePasswordForm()
            return
        }

        presentChangePasswordExitAlert {
            self.resetChangePasswordForm()
            router.navigateBack()
        }
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
                        message: toastLang.csvExportError))
                }
            }
            notificationService.dismissLoader()
        }
    }

    /// Resets the change-password form.
    func resetChangePasswordForm() {
        changePasswordForm = ChangePasswordForm()
    }

    func confirmDiscardPasswordChanges() async -> Bool {
        // Fast-path: no changes → allow exit.
        guard changePasswordForm.isDirty else { return true }

        return await withCheckedContinuation { continuation in
            presentChangePasswordExitAlert(onExit: {
                continuation.resume(returning: true)
            }, onCancel: {
                continuation.resume(returning: false)
            })
        }
    }

    // MARK: - Edit Profile Exit Helpers

    /// Presents an Edit-Profile exit confirmation alert using shared strings.
    /// - Parameters:
    ///   - onExit:    Closure executed when the user confirms leaving.
    ///   - onCancel:  Optional closure executed when the user cancels.
    private func presentEditProfileExitAlert(onExit: @escaping () -> Void,
                                             onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: alertLang.EditProfileExitAlert.title,
            message: alertLang.EditProfileExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.EditProfileExitAlert.exitButton, type: .primary) { _ in
                    onExit()
                },
                AlertButtonModel(title: alertLang.EditProfileExitAlert.returnButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func handleEditProfileExit(router: Router<SettingsRoute>) {
        // Fast path: if form is pristine just pop the screen and bail.
        guard editProfileForm.isDirty else {
            router.navigateBack()
            resetEditProfileForm()
            return
        }

        presentEditProfileExitAlert {
            self.resetEditProfileForm()
            router.navigateBack()
        }
    }

    /// Async variant used by tab-deactivation; returns a `Bool` indicating whether it is safe to leave.
    func confirmDiscardProfileChanges() async -> Bool {
        // Allow exit immediately when no changes.
        guard editProfileForm.isDirty else { return true }

        return await withCheckedContinuation { continuation in
            presentEditProfileExitAlert(onExit: {
                continuation.resume(returning: true)
            }, onCancel: {
                continuation.resume(returning: false)
            })
        }
    }

    // MARK: - Weight Unit Helpers

    /// Updates the user's preferred weight/height unit and persists it via `AccountService`.
    /// - Parameter unit: The newly selected `WeightUnit`.
    func updateWeightUnit(_ unit: WeightUnit) {
        guard let account = activeAccount else { return }
        // Skip if no change
        guard account.weightSettings?.weightUnit != unit else { return }

        Task {
            httpClient.skipCheckNetwork = true
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let bodyComp = BodyComp(
                    weightUnit: unit,
                    height: account.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0,
                    activityLevel: account.weightSettings?.activityLevel ?? .normal
                )
                _ = try await accountService.updateBodyComp(bodyComp)

                // Update R4 scales profile and check for USER_SELECTION_IN_PROGRESS status
                let profileUpdateResult = await bluetoothService.updateUserProfileForR4Scales()
                logger.log(level: .info, tag: tag, message: "updateUserProfileForR4Scales result updateWeightUnit: \(profileUpdateResult)")

                switch profileUpdateResult {
                case let .success(statusArray):
                    // Suppress success toast during user selection to prevent misleading feedback,
                    // since the scale profile isn't updated at that time.
                    if hasUserSelectionInProgress(statusArray: statusArray) {
                        // Show updates pending alert instead
                        showUpdatesPendingAlert()
                    } else {
                        notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.unitSettingUpdated))
                    }
                case .failure:
                    // If update fails (e.g., already in progress from subscription),
                    // still show success toast since setting was saved successfully
                    notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.unitSettingUpdated))
                }

                logger.log(level: .info, tag: tag, message: "Weight unit updated to \(unit.rawValue)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Weight unit update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
            httpClient.skipCheckNetwork = false
        }
    }

    // MARK: - Activity Level Helpers

    func updateActivityLevel(_ level: ActivityLevel) {
        guard let account = activeAccount else { return }
        guard account.weightSettings?.activityLevel != level else { return }

        Task {
            httpClient.skipCheckNetwork = true
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let bodyComp = BodyComp(
                    weightUnit: account.weightSettings?.weightUnit ?? .lb,
                    height: account.weightSettings.flatMap { Double($0.height ?? "0") } ?? 0.0,
                    activityLevel: level
                )
                _ = try await accountService.updateBodyComp(bodyComp)

                // Update R4 scales profile and check for USER_SELECTION_IN_PROGRESS status
                let profileUpdateResult = await bluetoothService.updateUserProfileForR4Scales()
                logger.log(level: .info, tag: tag, message: "updateUserProfileForR4Scales result updateActivityLevel: \(profileUpdateResult)")
                switch profileUpdateResult {
                case let .success(statusArray):
                    // Suppress success toast during user selection to prevent misleading feedback,
                    // since the scale profile isn't updated at that time.
                    if hasUserSelectionInProgress(statusArray: statusArray) {
                        // Show updates pending alert instead
                        showUpdatesPendingAlert()
                    } else {
                        notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.activitySettingUpdated))
                    }
                case .failure:
                    // If update fails (e.g., already in progress from subscription),
                    // still show success toast since setting was saved successfully
                    notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.activitySettingUpdated))
                }

                logger.log(level: .info, tag: tag, message: "Activity level updated to \(level.rawValue)")
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.unableToUpdateAccountSettings))
                logger.log(level: .error, tag: tag, message: "Activity level update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
            httpClient.skipCheckNetwork = false
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
            httpClient.skipCheckNetwork = true
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
            httpClient.skipCheckNetwork = false
        }
    }

    // MARK: - Form-Only Update Helpers (for Edit Profile)

    /// Updates gender in the edit form only – does NOT make an API call.
    /// The API call happens during saveProfile() when the user clicks the Save button.
    func updateGenderInForm(_ sex: Sex) {
        editProfileForm.gender.value = sex
        editProfileForm.gender.markAsDirty()
    }

    /// Updates height in the edit form only – does NOT make an API call.
    /// The API call happens during saveProfile() when the user clicks the Save button.
    func updateHeightInForm(fromMetric: Bool, values: [String]) {
        // Validate height before updating
        guard ConversionTools.isValidHeightPickerValues(fromMetric: fromMetric, values: values) else {
            logger.log(level: .error, tag: tag, message: "Invalid height values rejected: \(values)")
            return
        }

        let storedHeight: Int
        if fromMetric {
            let cm = Int(values.joined()) ?? 178
            guard ConversionTools.isValidHeightCm(cm) else {
                logger.log(level: .error, tag: tag, message: "Invalid cm height rejected: \(cm)")
                return
            }
            storedHeight = ConversionTools.convertCmToStoredHeight(cm)
        } else {
            let feet = Int(values[0]) ?? 5
            let inches = Int(values[1]) ?? 10
            guard ConversionTools.isValidHeightInches(feet: feet, inches: inches) else {
                logger.log(level: .error, tag: tag, message: "Invalid feet/inches height rejected: \(feet)'\(inches)\"")
                return
            }
            let totalInches = (feet * 12) + inches
            storedHeight = ConversionTools.convertInchesToStoredHeight(totalInches)
        }

        editProfileForm.height.value = String(storedHeight)
        editProfileForm.height.markAsDirty()
    }

    /// Checks if the status array contains USER_SELECTION_IN_PROGRESS
    /// - Parameter statusArray: Array of status strings from updateUserProfileForR4Scales
    /// - Returns: True if any status contains USER_SELECTION_IN_PROGRESS
    private func hasUserSelectionInProgress(statusArray: [String]) -> Bool {
        return statusArray.contains { $0.contains(UserCreationResponse.userSelectionInProgress.rawValue) }
    }

    /// Shows the updates pending alert when scale settings can't be updated
    private func showUpdatesPendingAlert() {
        let alert = AlertModel(
            title: alertLang.UpdatesPendingAlert.title,
            message: alertLang.UpdatesPendingAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.UpdatesPendingAlert.okButton, type: .primary) { _ in
                    // Alert dismissed
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Weightless Exit Helpers

    /// Presents a Weightless exit confirmation alert using shared strings.
    /// - Parameters:
    ///   - onExit:    Closure executed when the user confirms leaving.
    ///   - onCancel:  Optional closure executed when the user cancels.
    private func presentWeightlessExitAlert(onExit: @escaping () -> Void,
                                            onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: alertLang.WeightLessExitAlert.title,
            message: alertLang.WeightLessExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.WeightLessExitAlert.exitButton, type: .primary) { _ in
                    onExit()
                },
                AlertButtonModel(title: alertLang.WeightLessExitAlert.returnButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Variant of `handleWeightlessExit` that works with `Router` based navigation (push page instead of sheet).
    func handleWeightlessExit(router: Router<SettingsRoute>) {
        // Fast path: if there are no actual changes, simply pop and bail
        guard hasWeightlessChanges else {
            router.navigateBack()
            resetWeightlessForm()
            return
        }

        presentWeightlessExitAlert {
            self.resetWeightlessForm()
            router.navigateBack()
        }
    }

    /// Async variant used by tab-deactivation; returns a Bool indicating whether it is safe to leave.
    func confirmDiscardWeightlessChanges() async -> Bool {
        // Allow immediate exit when there are no actual changes
        guard hasWeightlessChanges else { return true }

        return await withCheckedContinuation { continuation in
            presentWeightlessExitAlert(onExit: {
                continuation.resume(returning: true)
            }, onCancel: {
                continuation.resume(returning: false)
            })
        }
    }

    /// Saves Weightless settings when presented via navigation push (not sheet).
    func saveWeightless(router: Router<SettingsRoute>) {
        // Validate form first.
        weightlessForm.validate()

        guard hasWeightlessChanges, isWeightLessFormValid else { return }
        if weightlessForm.isOn.value && weightlessForm.weight.isInvalid { return }

        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        let storedWeight: Int = {
            if let val = Double(weightlessForm.weight.value) {
                return ConversionTools.convertDisplayToStored(val, isMetric: unit == .kg)
            }
            return 0
        }()

        updateWeightlessMode(isOn: weightlessForm.isOn.value, storedWeight: storedWeight) {
            router.navigateBack()
        }
    }

    /// Handles the networking call for updating Weightless settings and executes `onSuccess` on completion.
    private func updateWeightlessMode(isOn: Bool, storedWeight: Int, onSuccess: @escaping () -> Void) {
        guard let account = activeAccount else { return }

        let currentOn = account.weightlessSettings?.isWeightlessOn ?? false
        let currentWeightStored = Int(account.weightlessSettings?.weightlessWeight ?? 0)
        if currentOn == isOn, currentWeightStored == storedWeight {
            onSuccess()
            return
        }

        Task {
            httpClient.skipCheckNetwork = true
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                let timestamp = DateTimeTools.getCurrentDatetimeIsoString()
                _ = try await accountService.updateWeightless(
                    isWeightlessOn: isOn,
                    weightlessTimestamp: timestamp,
                    weightlessWeight: Double(storedWeight)
                )

                // Refresh account to ensure latest state is available when user returns
                _ = try? await accountService.refreshAccount()

                notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.weightlessUpdated))
                logger.log(level: .info, tag: tag, message: "Weightless settings updated")

                // Mark form as pristine after successful save
                await MainActor.run {
                    self.weightlessForm.isOn.markAsPristine()
                    self.weightlessForm.weight.markAsPristine()
                    // Update initial toggle state to match saved state
                    self.initialWeightlessToggleState = isOn
                }

                onSuccess()
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.errorUpdatingWeightless, message: toastLang.restartAndTryAgain))
                logger.log(level: .error, tag: tag, message: "Weightless update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
            httpClient.skipCheckNetwork = false
        }
    }

    // MARK: - Weightless Form Helpers

    /// Populates the Weightless settings form with the current account values (only once, when pristine).
    func populateWeightlessFormIfNeeded() {
        guard let account = activeAccount else {
            // Clear form when no account
            weightlessForm.isOn.value = false
            weightlessForm.weight.value = ""
            weightlessForm.isOn.markAsPristine()
            weightlessForm.weight.markAsPristine()
            initialWeightlessToggleState = false
            return
        }

        // Skip if user has already started editing (keep any in-flight changes).
        guard !weightlessForm.isDirty else { return }

        // If weightlessWeight is null, toggle should be OFF regardless of isWeightlessOn
        // This handles cases where API returns inconsistent data
        let hasWeight = account.weightlessSettings?.weightlessWeight != nil
        let isWeightlessOn = account.weightlessSettings?.isWeightlessOn ?? false

        // Toggle should be ON only if both isWeightlessOn is true AND weightlessWeight exists
        let shouldBeOn = isWeightlessOn && hasWeight

        weightlessForm.isOn.value = shouldBeOn
        weightlessForm.isOn.markAsPristine()
        // Store initial toggle state to detect changes
        initialWeightlessToggleState = shouldBeOn

        // Set weight field value
        if let storedWeight = account.weightlessSettings?.weightlessWeight, shouldBeOn {
            // Convert stored tenths-of-lbs value to display unit.
            let unit = account.weightSettings?.weightUnit ?? .lb
            let display: Double = unit == .kg
                ? ConversionTools.convertStoredToKg(Int(storedWeight))
                : ConversionTools.convertStoredToLbs(Int(storedWeight))

            let formattedValue = String(format: "%.1f", display)
            weightlessForm.weight.value = formattedValue
            weightlessForm.weight.markAsPristine()
        } else {
            // Clear weight field when weightlessWeight is null or toggle is OFF
            weightlessForm.weight.value = ""
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

    /// Resets the Weightless form to a pristine state.
    func resetWeightlessForm() {
        weightlessForm = WeightlessForm()
        initialWeightlessToggleState = false
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
        selectedHeightCm = selections.cm
    }

    /// Presents the correct picker sheet based on the user's current unit preference.
    func showHeightPicker() {
        if activeAccount?.weightSettings?.weightUnit == .kg {
            showHeightCmPicker = true
        } else {
            showHeightInchesPicker = true
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
        if let goalW = account.goalSettings?.initialWeight {
            let unit = account.weightSettings?.weightUnit ?? .lb
            let display = unit == .kg ? ConversionTools.convertStoredToKg(Int(goalW)) : ConversionTools.convertStoredToLbs(Int(goalW))
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
    }

    /// Updates the max-weight validator whenever the preferred unit changes.
    private func updateGoalWeightValidators() {
        let isMetric = (activeAccount?.weightSettings?.weightUnit ?? .lb) == .kg
        let maxWeight = isMetric ? 450.0 : 999.0

        for ctrl in [goalForm.currentWeight, goalForm.goalWeight] {
            ctrl.removeValidator(ofType: .maxValue)
            let validator = Validator.maxValue(maxWeight)
            ctrl.addValidator(validator)
        }
    }

    // MARK: - Goal Exit Helpers

    /// Presents a Goal exit confirmation alert using shared strings.
    /// - Parameters:
    ///   - onExit:    Closure executed when the user confirms leaving.
    ///   - onCancel:  Optional closure executed when the user cancels.
    private func presentGoalExitAlert(onExit: @escaping () -> Void,
                                      onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: alertLang.GoalExitAlert.title,
            message: alertLang.GoalExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.GoalExitAlert.exitButton, type: .primary) { _ in
                    onExit()
                },
                AlertButtonModel(title: alertLang.GoalExitAlert.returnButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Variant for navigation push presentation.
    func handleGoalExit(router: Router<SettingsRoute>) {
        // Fast path: if form is pristine simply pop.
        guard goalForm.isDirty else {
            router.navigateBack()
            resetGoalForm()
            return
        }

        presentGoalExitAlert {
            self.resetGoalForm()
            router.navigateBack()
        }
    }

    /// Async variant used by tab-deactivation; returns whether it is safe to leave.
    func confirmDiscardGoalChanges() async -> Bool {
        guard goalForm.isDirty else { return true }

        return await withCheckedContinuation { continuation in
            presentGoalExitAlert(onExit: {
                continuation.resume(returning: true)
            }, onCancel: {
                continuation.resume(returning: false)
            })
        }
    }

    // Saves Goal when presented via navigation push.
    // swiftlint:disable:next function_body_length
    func saveGoal(router: Router<SettingsRoute>) {
        goalForm.validate()
        guard goalForm.isDirty, isGoalFormValid else { return }

        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        let isMetric = unit == .kg
        let convert = { (valString: String) -> Int in
            let val = Double(valString) ?? 0.0
            return ConversionTools.convertDisplayToStored(val, isMetric: isMetric)
        }
        let goalTypeValue = goalForm.goalType.value
        let currentDisplay = goalForm.currentWeight.value
        let targetDisplay = goalForm.goalWeight.value
        let goalStored = convert(targetDisplay)
        let initialStored: Int = {
            if goalTypeValue == GoalType.maintain.rawValue {
                return goalStored
            } else {
                return convert(currentDisplay)
            }
        }()

        Task {
            httpClient.skipCheckNetwork = true
            let latestEntry = try await entryService.getLatestEntry()
            let latestWeight = latestEntry?.scaleEntry?.weight ?? 0
            let goalPayload: Goal
            if goalTypeValue == GoalType.maintain.rawValue {
                goalPayload = Goal(type: .maintain, goalWeight: goalStored, initialWeight: latestWeight, goalType: .maintain)
            } else {
                let derivedType: GoalType = goalStored > initialStored ? .gain : .lose
                goalPayload = Goal(type: derivedType, goalWeight: goalStored, initialWeight: initialStored, goalType: derivedType)
            }
            notificationService.showLoader(LoaderModel(text: loaderLang.saving))
            do {
                _ = try await accountService.createGoal(goalPayload)

                // Update R4 scales profile and check for USER_SELECTION_IN_PROGRESS status
                let profileUpdateResult = await bluetoothService.updateUserProfileForR4Scales()
                logger.log(level: .info, tag: tag, message: "updateUserProfileForR4Scales result createGoal: \(profileUpdateResult)")

                switch profileUpdateResult {
                case let .success(statusArray):
                    // Suppress success toast during user selection to prevent misleading feedback,
                    // since the scale profile isn't updated at that time.
                    if hasUserSelectionInProgress(statusArray: statusArray) {
                        // Show updates pending alert instead
                        showUpdatesPendingAlert()
                    } else {
                        notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.goalSaved))
                    }
                case .failure:
                    // If update fails (e.g., already in progress from subscription),
                    // still show success toast since goal was saved successfully
                    notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.goalSaved))
                }

                goalAlertService.resetGoalMetFlag()
                logger.log(level: .info, tag: tag, message: "Goal updated successfully")
                resetGoalForm()
                router.navigateBack()
            } catch {
                notificationService.showToast(ToastModel(title: toastLang.errorSettingGoal, message: toastLang.pleaseTryAgain))
                logger.log(level: .error, tag: tag, message: "Goal update failed:", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
            httpClient.skipCheckNetwork = false
        }
    }

    /// Resets to pristine state and re-sync with account.
    func resetGoalForm() {
        goalForm = GoalForm()
        populateGoalFormIfNeeded()
    }

    /// Handles goal type segment changes and ensures proper form state
    func handleGoalTypeChange(_ newSegment: GoalTypeSegment) {
        selectedSegment = newSegment
        let newGoalTypeValue = newSegment.goalTypeValue

        // Only update if the value is actually different
        if goalForm.goalType.value != newGoalTypeValue {
            goalForm.goalType.value = newGoalTypeValue
            // Explicitly mark as dirty to ensure the form recognizes the change
            goalForm.goalType.markAsDirty()
            // Mark as touched so form is considered interacted with
            goalForm.goalType.markAsTouched()
        }
        if newSegment == .loseGain {
            [goalForm.goalWeight, goalForm.currentWeight]
                .filter { !$0.value.isEmpty }
                .forEach { $0.markAsDirty() }
        }

        // Force form validation to update computed properties
        goalForm.validate()

        // Trigger UI update by sending objectWillChange
        objectWillChange.send()
    }

    /// Notifies other components about goal type changes
    func notifyGoalTypeChange() {
        NotificationCenter.default.post(name: .goalTypeChanged, object: nil)
        logger.log(level: .info, tag: tag, message: "Goal type change notification sent")
    }

    /// Current notification preference derived from account settings.
    var notificationPreference: NotificationPreference {
        let settings = activeAccount?.notificationSettings
        if settings?.shouldSendEntryNotifications == true {
            return settings?.shouldSendWeightInEntryNotifications == true ? .enableWithWeight : .enable
        }
        return .disable
    }

    // MARK: - Forgot Password Helpers

    func showForgotPasswordAlert() {
        let email = activeAccount?.email ?? ""
        let alert = AlertModel(
            title: alertLang.ForgotPasswordAlert.title,
            message: alertLang.ForgotPasswordAlert.message(email),
            buttons: [
                AlertButtonModel(title: alertLang.ForgotPasswordAlert.send, type: .primary) { _ in
                    self.sendForgotPasswordEmail()
                },
                AlertButtonModel(title: alertLang.ForgotPasswordAlert.cancel, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    func sendForgotPasswordEmail() {
        guard let email = activeAccount?.email else { return }

        let trimmedEmail = removeWhiteSpace(email)
        guard !trimmedEmail.isEmpty else { return }

        Task {
            logger.log(level: .info, tag: tag, message: "Settings forgot password request started")
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
            do {
                try await accountService.requestPasswordReset(email: trimmedEmail)
                logger.log(level: .success, tag: tag, message: "Settings forgot password request succeeded")
                notificationService.showToast(
                    ToastModel(
                        title: toastLang.success,
                        message: toastLang.passwordResetSuccessMessage(trimmedEmail)
                    )
                )
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Settings forgot password request failed. error=\(error.localizedDescription), "
                        + "errorType=\(String(describing: type(of: error)))"
                )
                notificationService.showToast(ToastModel(title: toastLang.somethingWentWrongTitle, message: toastLang.pleaseTryAgain))
            }
            notificationService.dismissLoader()
        }
    }

    // MARK: - Multiple Accounts Educational Modal

    /// Presents the *Add Multiple Accounts* educational modal if the user has only one account and has not seen the modal before.
    /// - Parameters:
    ///   - router: The settings router (used when navigating from Settings screen)
    ///   - tabViewModel: Optional tab bar view model (used when navigating from Dashboard or other tabs)
    func presentAddAccountModalIfNeeded(router: Router<SettingsRoute>, tabViewModel: BottomTabBarViewModel? = nil) {
        guard accountService.allAccounts.count == 1 else { return }

        let flagKey = hasSeenAddMultipleAccountsModalKey
        let hasSeen = (kvStore.getValue(forKey: flagKey) as? Bool) ?? false
        guard !hasSeen else { return }

        guard accountService.activeAccount != nil,
              let firstName = activeAccount?.firstName,
              !firstName.firstAlphabeticCharacter().isEmpty
        else {
            return
        }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            guard self.accountService.allAccounts.count == 1,
                  let activeAccount = self.accountService.activeAccount,
                  let firstName = activeAccount.firstName
            else {
                return
            }

            let initial = firstName.firstAlphabeticCharacter()
            self.kvStore.setValue(true, forKey: flagKey)

            let modalView = AddMultipleAccountsModalView(
                initial: initial,
                onClose: {
                    self.logger.log(level: .info, tag: self.tag, message: "Dismissed add-multiple-accounts educational modal")
                    self.notificationService.dismissModal()
                },
                onAddAccount: {
                    self.logger.log(level: .info, tag: self.tag, message: "Add account selected from educational modal. Navigating to My Accounts")
                    self.notificationService.dismissModal()
                    // Use tabViewModel navigation if available (works from any tab), otherwise use router (Settings screen only)
                    if let tabViewModel = tabViewModel {
                        tabViewModel.navigateToSettings(route: .myAccounts)
                    } else {
                        router.navigate(to: .myAccounts)
                    }
                }
            )

            self.logger.log(level: .info, tag: self.tag, message: "Presenting add-multiple-accounts educational modal")
            self.notificationService.showModal(ModalData(presentedView: AnyView(modalView), backdropDismiss: false))
        }
    }

    private func observeNotificationBadgeChanges() {
        feedService.notificationBadgeUpdated
            .receive(on: DispatchQueue.main)
            .assign(to: \.canShowFeedNotificationBadge, on: self)
            .store(in: &cancellables)
    }

    // MARK: - Entry Check

    private func checkEntries() async {
        do {
            let months = try await entryService.getMonthsAll()
            hasEntries = !months.isEmpty
        } catch {
            hasEntries = false
        }
    }

    // MARK: - Picker Presentation Helpers

    /// Presents the appearance picker (modal on iPad < iOS18, sheet otherwise).
    func presentAppearancePicker() {
        if useModalPicker {
            let picker = PickerView(
                selectedValues: [theme.appearanceMode],
                options: [AppearanceMode.allCases],
                displayValue: { $0.rawValue },
                title: SettingsStrings.appearance,
                showCancel: false,
                updateValues: { vals in // swiftlint:disable:this trailing_closure
                    self.notificationService.dismissModal()
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        if let mode = vals.first { Theme.shared.appearanceMode = mode }
                    }
                }
            )
            notificationService.showModal(
                ModalData(
                    presentedView: AnyView(picker)
                )
            )
        } else {
            showAppearancePicker = true
        }
    }

    /// Presents the notification preference picker (modal on iPad < iOS18, sheet otherwise).
    func presentNotificationPicker() {
        if useModalPicker {
            let picker = PickerView(
                selectedValues: [notificationPreference],
                options: [NotificationPreference.allCases],
                displayValue: { $0.title },
                title: SettingsStrings.notifications,
                showCancel: false,
                updateValues: { vals in // swiftlint:disable:this trailing_closure
                    self.notificationService.dismissModal()
                    if let pref = vals.first { self.updateNotificationPreference(pref) }
                }
            )
            notificationService.showModal(ModalData(presentedView: AnyView(picker)))
        } else {
            showNotificationPicker = true
        }
    }

    /// Presents the gender picker (modal on iPad < iOS18, sheet otherwise).
    func presentGenderPicker() {
        if useModalPicker {
            let picker = PickerView(
                selectedValues: [activeAccount?.gender ?? .male],
                options: [Sex.allCases],
                displayValue: { $0.rawValue.capitalized },
                title: SettingsStrings.biologicalSex,
                showCancel: false,
                updateValues: { vals in // swiftlint:disable:this trailing_closure
                    self.notificationService.dismissModal()
                    if let sex = vals.first { self.updateGenderInForm(sex) }
                }
            )
            notificationService.showModal(ModalData(presentedView: AnyView(picker)))
        } else {
            showGenderPicker = true
        }
    }

    /// Presents the unit picker (modal on iPad < iOS18, sheet otherwise).
    func presentUnitPicker() {
        if useModalPicker {
            let picker = PickerView(
                selectedValues: [activeAccount?.weightSettings?.weightUnit ?? .lb],
                options: [[WeightUnit.lb, WeightUnit.kg]],
                displayValue: { unit in unit == .kg ? CommonStrings.unitKgCm : CommonStrings.pickerLbs },
                title: SettingsStrings.unitType,
                showCancel: false,
                updateValues: { vals in // swiftlint:disable:this trailing_closure
                    self.notificationService.dismissModal()
                    if let unit = vals.first { self.updateWeightUnit(unit) }
                }
            )
            notificationService.showModal(ModalData(presentedView: AnyView(picker)))
        } else {
            showUnitPicker = true
        }
    }

    /// Presents the activity level picker (modal on iPad < iOS18, sheet otherwise).
    func presentActivityPicker() {
        if useModalPicker {
            let picker = PickerView(
                selectedValues: [activeAccount?.weightSettings?.activityLevel ?? .normal],
                options: [[ActivityLevel.normal, ActivityLevel.athlete]],
                displayValue: { $0.rawValue.capitalized },
                title: SettingsStrings.activityLevel,
                showCancel: false,
                updateValues: { vals in // swiftlint:disable:this trailing_closure
                    self.notificationService.dismissModal()
                    if let level = vals.first { self.updateActivityLevel(level) }
                }
            )
            notificationService.showModal(ModalData(presentedView: AnyView(picker)))
        } else {
            showActivityPicker = true
        }
    }

    /// Presents the height picker (modal on iPad < iOS18, sheet otherwise).
    func presentHeightPicker() {
        if useModalPicker {
            if activeAccount?.weightSettings?.weightUnit == .kg {
                let picker = PickerView(
                    selectedValues: selectedHeightCm,
                    options: heightCmOptions,
                    displayValue: { $0 },
                    pickerType: .heightCm,
                    title: SettingsStrings.height,
                    showCancel: false,
                    updateValues: { vals in // swiftlint:disable:this trailing_closure
                        self.notificationService.dismissModal()
                        self.updateHeightInForm(fromMetric: true, values: vals)
                    }
                )
                notificationService.showModal(
                    ModalData(presentedView: AnyView(
                        picker
                    ))
                )
            } else {
                let picker = PickerView(
                    selectedValues: selectedHeightInches,
                    options: heightInchesOptions,
                    displayValue: { $0 },
                    pickerType: .heightInches,
                    title: SettingsStrings.height,
                    showCancel: false,
                    updateValues: { vals in // swiftlint:disable:this trailing_closure
                        self.notificationService.dismissModal()
                        self.updateHeightInForm(fromMetric: false, values: vals)
                    }
                )
                notificationService.showModal(
                    ModalData(presentedView: AnyView(
                        picker
                    ))
                )
            }
        } else {
            showHeightPicker()
        }
    }
}

// swiftlint:enable file_length
