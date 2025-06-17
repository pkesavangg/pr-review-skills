//
//  LoginStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import Foundation
import SwiftUI
import Combine

// MARK: LoginStore
/// This store is responsible for managing the Login process.
@MainActor
final class LoginStore: ObservableObject {
    @Published var showPassword: Bool = false
    @Published var isFormSubmitting: Bool = false
    @Published var errorMessage: String? = nil
    @Published var showResetPrompt: Bool = false
    @Published var resetEmail: String = ""
    @Published var resetError: String? = nil
    @Published var showSuccessToast: Bool = false
    @Published var successToastMessage: String = ""
    @Published var showErrorToast: Bool = false
    @Published var errorToastMessage: String = ""
    @Published var isLoading: Bool = false
    @Published var alertData: AlertModel? = nil
    @Published var loaderOverride: LoaderModel? = nil

    // MARK: - In-App Browser State
    @Published var showPrivacyBrowser: Bool = false
    @Published var showTermsBrowser: Bool = false
    @Published var showHelpBrowser: Bool = false
    @Published var browserURL: URL? = nil

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
        browserURL ?? URL(string: urlStrings.baseUrl)!
    }

    /// Loader binding for presentLoader
    var loaderData: Binding<LoaderModel?> {
        Binding(
            get: { self.loaderOverride ?? (self.isLoading ? LoaderModel(text: self.lang.loggingAccount) : nil) },
            set: { _ in }
        )
    }

    // Navigation
    var onLoginSuccess: (() -> Void)?
    var onNavigateBack: (() -> Void)?
    var onOpenPrivacy: (() -> Void)?
    var onOpenTerms: (() -> Void)?
    var onOpenHelp: (() -> Void)?

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
        loginForm.email.markAsDirty()
        objectWillChange.send()
    }

    func setPasswordTouched() {
        loginForm.password.markAsDirty()
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

    // MARK: - Login Logic
    func logIn() async {
        loginForm.email.markAsDirty()
        loginForm.password.markAsDirty()
        isFormSubmitting = true
        isLoading = true
        errorMessage = nil
        do {
            try await accountService.logIn(email: loginForm.email.value, password: loginForm.password.value)
            onLoginSuccess?()
        } catch {
            logger.log(level: .error, tag: logTag, message: "Error logging in: \(error)")
        }
        isFormSubmitting = false
        isLoading = false
    }

    // MARK: - Password Reset
    func showPasswordResetPrompt() {
        let emailValue = loginForm.email.value.trimmingCharacters(in: .whitespacesAndNewlines)
        resetEmail = emailValue
        showResetPrompt = true
        resetError = nil
        alertData = AlertModel(
            title: alertLang.ResetPasswordAlert.passwordResetTitle,
            message: alertLang.ResetPasswordAlert.enterEmailMessage,
            buttons: [
                AlertButtonModel(title: commonLang.cancel, type: .secondary) { _ in },
                AlertButtonModel(title: commonLang.submit, type: .primary) { [weak self] input in
                    guard let self = self else { return }
                    let email = input ?? self.resetEmail
                    Task { await self.handlePasswordReset(email: email) }
                }
            ],
            inputField: AlertInputField(placeholder: inputFieldLabels.email, value: emailValue.isEmpty ? "" : emailValue, type: .email)
        )
    }

    private func handlePasswordReset(email: String) async {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let tempEmailControl = FormControl(trimmedEmail, validators: [.required, .email, .maxLength(100)])
        tempEmailControl.markAsDirty()
        tempEmailControl.validate()
        guard tempEmailControl.isValid else {
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
        loaderOverride = LoaderModel(text: lang.sendingEmail)
        do {
            try await accountService.requestPasswordReset(email: trimmedEmail)
            showResetPrompt = false
            notificationService.showToast(
                ToastModel(
                    title: toastLang.passwordResetSuccessTitle,
                    message: toastLang.passwordResetSuccessMessage(trimmedEmail)
                )
            )
        } catch {
            logger.log(level: .error, tag: logTag, message: "Error:\(error)")
            resetError = errorLang.passwordResetFailed
        }
        loaderOverride = nil
        isFormSubmitting = false
        isLoading = false
    }

    // MARK: - Show/Hide Password
    func toggleShowPassword() {
        showPassword.toggle()
    }
    
    // MARK: - Navigation
    func openPrivacy() {
        browserURL = URLHelper.getURL(for: .privacyPolicy)
        showPrivacyBrowser = true
    }

    func openTerms() {
        browserURL = URLHelper.getURL(for: .termsOfService)
        showTermsBrowser = true
    }
    
    func openHelp() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(){
                self.notificationService.dismissModal()
            })
        ))
    }
}
