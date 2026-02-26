//
//  LoginStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import Combine
import Foundation
import SwiftUI

// MARK: LoginStore

/// This store is responsible for managing the Login process.
@MainActor
final class LoginStore: ObservableObject {
    @Published var showPassword: Bool = false
    @Published var isFormSubmitting: Bool = false
    @Published var errorMessage: String?
    @Published var showResetPrompt: Bool = false
    @Published var resetEmail: String = ""
    @Published var resetError: String?
    @Published var showSuccessToast: Bool = false
    @Published var successToastMessage: String = ""
    @Published var showErrorToast: Bool = false
    @Published var errorToastMessage: String = ""
    @Published var isLoading: Bool = false
    @Published var alertData: AlertModel?
    @Published var loaderOverride: LoaderModel?
    @Published var isPasswordResetAlertVisible: Bool = false

    // MARK: - In-App Browser State

    @Published var showPrivacyBrowser: Bool = false
    @Published var showTermsBrowser: Bool = false
    @Published var showHelpBrowser: Bool = false
    @Published var browserURL: URL?

    // MARK: - Account Management State

    @Published var isFromAccountSwitching: Bool = false

    // MARK: - Common Strings/Labels as variables

    let lang = LoaderStrings.self
    let alertLang = AlertStrings.self
    let errorLang = FormErrorMessages.self
    let toastLang = ToastStrings.self
    let commonLang = CommonStrings.self
    let inputFieldLabels = InputFieldLabels.self
    let urlStrings = URLStrings.self
    let logTag = "LoginStore"

    /// Main browser presentation binding for the view
    var isBrowserPresented: Binding<Bool> {
        Binding(
            get: { self.showPrivacyBrowser || self.showTermsBrowser || self.showHelpBrowser },
            set: { newValue in
                if !newValue {
                    self.showPrivacyBrowser = false
                    self.showTermsBrowser = false
                    self.showHelpBrowser = false
                    self.browserURL = nil
                }
            }
        )
    }

    /// Main browser URL for the view
    var presentingBrowserURL: URL {
        browserURL
            ?? URL(string: urlStrings.baseUrl)
            ?? URL(fileURLWithPath: "/")
    }

    // Navigation
    var onLoginSuccess: (() -> Void)?
    var dismissAction: DismissAction?
    var onNavigateBack: (() -> Void)?
    var onOpenPrivacy: (() -> Void)?
    var onOpenTerms: (() -> Void)?
    var onOpenHelp: (() -> Void)?
    var onAccountSwitchingExit: (() -> Void)?
    /// Callback to clear focus when password reset alert is dismissed
    var onPasswordResetAlertDismissed: (() -> Void)?

    // Services (inject as needed)
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService
    @Injector var notificationService: NotificationHelperService

    // MARK: - Login Form

    @Published var loginForm = LoginForm()
    private var cancellables = Set<AnyCancellable>()

    init() {
        setupFormObservers()
    }

    // MARK: - Derived Properties from LoginForm

    var isFormValid: Bool { loginForm.isValid }
    var emailError: String? { loginForm.getError(for: loginForm.email) }
    var passwordError: String? { loginForm.getError(for: loginForm.password) }

    func setEmailTouched() {
        loginForm.email.markAsTouched()
        objectWillChange.send()
    }

    func setPasswordTouched() {
        loginForm.password.markAsTouched()
        objectWillChange.send()
    }

    private func setupFormObservers() {
        loginForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    // MARK: - Helper for email trimming

    private func trimmedEmail(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Prefill Logic

    /// Prefills the e-mail field when a value is supplied from navigation.
    /// - Parameter email: The e-mail address to prefill.
    /// If `nil` or empty, the call is ignored.
    func prefillEmailIfNeeded(_ email: String?) {
        guard let email, !email.isEmpty else { return }

        // Update without marking the control as dirty, then reset to pristine.
        loginForm.email.silentlyUpdateValue(email)
        loginForm.email.markAsPristine()

        // Notify observers of the change so UI updates immediately.
        objectWillChange.send()
    }

    // MARK: - Login Logic

    func logIn() async {
        loginForm.email.markAsDirty()
        loginForm.password.markAsDirty()

        guard loginForm.isValid else { return }
        let normalizedEmail = removeWhiteSpace(loginForm.email.value)
        logger.log(level: .info, tag: logTag, message: "Login flow started. accountSwitching=\(isFromAccountSwitching)")

        isFormSubmitting = true
        notificationService.showLoader(LoaderModel(text: lang.loggingAccount))
        errorMessage = nil

        defer {
            isFormSubmitting = false
            isLoading = false
        }

        do {
            _ = try await accountService.logIn(
                email: normalizedEmail,
                password: loginForm.password.value
            )
            logger.log(level: .success, tag: logTag, message: "Login flow succeeded. accountSwitching=\(isFromAccountSwitching)")
            // If the login is from account switching, dismiss the login screen
            if isFromAccountSwitching {
                dismissAction?()
            } else {
                onLoginSuccess?()
            }
        } catch {
            logger.log(
                level: .error,
                tag: logTag,
                message: "Login flow failed. error=\(error.localizedDescription), errorType=\(String(describing: type(of: error)))"
            )
            if case AccountError.maxAccountsReached = error {
                showMaxUserAccountsAlert()
                return
            }
            handleLoginError(error)
        }
        notificationService.dismissLoader()
    }

    private func handleLoginError(_ error: Error) {
        let httpError = error as? HTTPError
        switch httpError {
        case .unauthorized:
            showToast(title: toastLang.loginError, message: toastLang.invalidCredentials)
        case .timeout, .noInternet, .statusCode(0):
            showToast(title: toastLang.networkError, message: toastLang.unableToConnect)
        default:
            showToast(title: toastLang.loginError, message: toastLang.somethingWentWrong)
        }
    }

    private func showToast(title: String, message: String) {
        notificationService.showToast(ToastModel(title: title, message: message))
    }

    // MARK: - Password Reset

    func showPasswordResetPrompt() {
        let emailValue = trimmedEmail(loginForm.email.value)
        resetEmail = emailValue
        showResetPrompt = true
        resetError = nil
        isPasswordResetAlertVisible = true
        let alertData = AlertModel(
            title: alertLang.ResetPasswordAlert.passwordResetTitle,
            message: alertLang.ResetPasswordAlert.enterEmailMessage,
            buttons: [
                AlertButtonModel(title: commonLang.cancel, type: .secondary) { [weak self] _ in
                    guard let self = self else { return }
                    self.isPasswordResetAlertVisible = false
                    // Clear focus when alert is dismissed via cancel
                    self.onPasswordResetAlertDismissed?()
                },
                AlertButtonModel(title: commonLang.submit, type: .primary) { [weak self] input in
                    guard let self = self else { return }
                    let email = input ?? self.resetEmail
                    self.isPasswordResetAlertVisible = false
                    Task { await self.handlePasswordReset(email: email) }
                    // Clear focus after submit
                    self.onPasswordResetAlertDismissed?()
                }
            ],
            inputField: AlertInputField(placeholder: inputFieldLabels.email, value: emailValue.isEmpty ? "" : emailValue, type: .email)
        )
        notificationService.showAlert(alertData)
    }

    private func handlePasswordReset(email: String) async {
        let trimmedEmail = trimmedEmail(email)
        let tempEmailControl = FormControl(trimmedEmail, validators: [.required, .email, .maxLength(100)])
        tempEmailControl.markAsDirty()
        tempEmailControl.validate()
        guard tempEmailControl.isValid else {
            logger.log(level: .error, tag: logTag, message: "Password reset blocked by invalid email input")
            resetError = LoginForm().getError(for: tempEmailControl) ?? errorLang.email
            notificationService.showToast(
                ToastModel(
                    title: toastLang.invalidEmailTitle,
                    message: toastLang.invalidEmailMessage
                )
            )
            return
        }
        isFormSubmitting = true
        isLoading = true
        notificationService.showLoader(LoaderModel(text: lang.sendingEmail))
        logger.log(level: .info, tag: logTag, message: "Password reset requested")
        do {
            try await accountService.requestPasswordReset(email: trimmedEmail)
            showResetPrompt = false
            logger.log(level: .success, tag: logTag, message: "Password reset request succeeded")
            notificationService.showToast(
                ToastModel(
                    title: toastLang.success,
                    message: toastLang.passwordResetSuccessMessage(trimmedEmail)
                )
            )
        } catch {
            logger.log(
                level: .error,
                tag: logTag,
                message: "Password reset request failed. error=\(error.localizedDescription), errorType=\(String(describing: type(of: error)))"
            )
            resetError = errorLang.passwordResetFailed
        }
        loaderOverride = nil
        isFormSubmitting = false
        isLoading = false
        notificationService.dismissLoader()
    }

    // MARK: - Show/Hide Password

    func toggleShowPassword() {
        showPassword.toggle()
    }

    // MARK: - Navigation

    func openPrivacy() {
        browserURL = URLHelper.getURL(for: .privacyPolicy)
        showPrivacyBrowser = true
        logger.log(level: .info, tag: logTag, message: "Opening privacy policy browser modal. url=\(browserURL?.absoluteString ?? "nil")")
    }

    func openTerms() {
        browserURL = URLHelper.getURL(for: .termsOfService)
        showTermsBrowser = true
        logger.log(level: .info, tag: logTag, message: "Opening terms of service browser modal. url=\(browserURL?.absoluteString ?? "nil")")
    }

    func openHelp() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView {
                self.notificationService.dismissModal()
            })
        ))
    }

    func handleExit(router: Router<AuthRoute>? = nil) {
        if !loginForm.isDirty {
            if isFromAccountSwitching {
                if let exitHandler = onAccountSwitchingExit {
                    exitHandler()
                } else {
                    dismissAction?()
                }
            } else {
                router?.navigateBack()
            }
            return
        }
        let loginExitAlert = alertLang.LoginExitAlert
        let alert = AlertModel(
            title: loginExitAlert.title,
            message: loginExitAlert.message,
            buttons: [
                AlertButtonModel(title: loginExitAlert.exitButton, type: .primary) { _ in
                    if self.isFromAccountSwitching {
                        if let exitHandler = self.onAccountSwitchingExit {
                            exitHandler()
                        } else {
                            self.dismissAction?()
                        }
                    } else {
                        router?.navigateBack()
                    }
                },
                AlertButtonModel(title: loginExitAlert.returnButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Presents an alert informing the user that the maximum number of accounts
    /// has been reached.
    private func showMaxUserAccountsAlert() {
        let alertLang = alertLang.MaxUsersAlert
        let alert = AlertModel(
            title: alertLang.title,
            message: isFromAccountSwitching ? alertLang.message : alertLang.logInAndRemoveMessage,
            buttons: [
                AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
}
