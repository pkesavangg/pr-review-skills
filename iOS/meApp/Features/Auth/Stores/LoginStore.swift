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
    // Input fields
    @Published var email: String = ""
    @Published var password: String = ""
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

    // MARK: - In-App Browser State
    @Published var showPrivacyBrowser: Bool = false
    @Published var showTermsBrowser: Bool = false
    @Published var showHelpBrowser: Bool = false
    @Published var browserURL: URL? = nil

    // Navigation
    var onLoginSuccess: (() -> Void)?
    var onNavigateBack: (() -> Void)?
    var onOpenPrivacy: (() -> Void)?
    var onOpenTerms: (() -> Void)?
    var onOpenHelp: (() -> Void)?

    // Services (inject as needed)
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService

    // MARK: - Form Validation
    var isEmailValid: Bool {
        // Simple regex for email validation
        let emailRegEx = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        let emailPred = NSPredicate(format:"SELF MATCHES %@", emailRegEx)
        return emailPred.evaluate(with: email) && email.count <= 100
    }
    var isPasswordValid: Bool {
        password.count >= 6 && password.count <= 50
    }
    var isFormValid: Bool {
        isEmailValid && isPasswordValid
    }

    // MARK: - Login Logic
    func logIn() async {
        guard isFormValid else {
            errorMessage = "Please enter a valid email and password."
            showErrorToast(with: errorMessage ?? "Invalid form.")
            return
        }
        isFormSubmitting = true
        isLoading = true
        errorMessage = nil
        do {
            try await accountService.logIn(email: email, password: password)
            onLoginSuccess?()
        } catch {
            logger.log(level: .error, tag: "LoginStore", message: "Error logging in: \(error)")
            errorMessage = parseLoginError(error)
            showErrorToast(with: errorMessage ?? "Login failed.")
        }
        isFormSubmitting = false
        isLoading = false
    }

    // MARK: - Password Reset
    func showPasswordResetPrompt() {
        resetEmail = email
        showResetPrompt = true
        resetError = nil
    }
    func requestPasswordReset() async {
        let trimmedEmail = resetEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidEmail(trimmedEmail) else {
            resetError = "Please enter a valid email."
            showErrorToast(with: resetError ?? "Invalid email.")
            return
        }
        isFormSubmitting = true
        isLoading = true
        do {
            try await accountService.requestPasswordReset(email: trimmedEmail)
            showSuccessToast(with: "Password reset email sent to \(trimmedEmail)")
            showResetPrompt = false
        } catch {
            logger.log(level: .error, tag: "LoginStore", message: "Error requesting password reset: \(error)")
            resetError = "Failed to send password reset email."
            showErrorToast(with: resetError ?? "Failed to send reset email.")
        }
        isFormSubmitting = false
        isLoading = false
    }

    // MARK: - Show/Hide Password
    func toggleShowPassword() {
        showPassword.toggle()
    }

    // MARK: - Navigation
    func openPrivacy() {
        browserURL = URL(string: "https://greatergoods.com/legal/privacy-policy")
        showPrivacyBrowser = true
    }
    func openTerms() {
        browserURL = URL(string: "https://greatergoods.com/legal/weight-gurus-tos")
        showTermsBrowser = true
    }
    
    func openHelp() {
        // TODO:  Implement functionlaity
    }

    // MARK: - Helpers
    private func isValidEmail(_ email: String) -> Bool {
        let emailRegEx = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        let emailPred = NSPredicate(format:"SELF MATCHES %@", emailRegEx)
        return emailPred.evaluate(with: email) && email.count <= 100
    }
    private func parseLoginError(_ error: Error) -> String {
        // Map error to user-friendly message
        let nsError = error as NSError
        switch nsError.code {
        case 401: return "Incorrect email or password."
        case NSURLErrorNotConnectedToInternet: return "No internet connection."
        case 500: return "Server error. Please try again later."
        default: return nsError.localizedDescription
        }
    }
    private func showErrorToast(with message: String) {
        errorToastMessage = message
        showErrorToast = true
    }
    private func showSuccessToast(with message: String) {
        successToastMessage = message
        showSuccessToast = true
    }
}
