//
//  ChangePasswordForm.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 20/06/25.
//

import Foundation
import Combine

/// Form model used in Change-Password screen for validating the password change inputs.
/// Validation rules:
///  - All fields required
///  - Min length 6 / Max length 50
///  - New password must differ from current password
///  - New password & confirmation must match
class ChangePasswordForm: ObservableForm {
    // MARK: - Controls
    var currentPassword = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    var newPassword     = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    var confirmNewPassword  = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])

    // MARK: - Change publisher
    /// Emits whenever any of the underlying controls changes – convenient for Combine bindings.
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            currentPassword.$value.map { _ in () }.eraseToAnyPublisher(),
            newPassword.$value.map { _ in () }.eraseToAnyPublisher(),
            confirmNewPassword.$value.map { _ in () }.eraseToAnyPublisher(),
        ])
        .eraseToAnyPublisher()
    }

    // MARK: - Validation
    override func validateForm() {
        var errors = ValidationErrors<Any>()

        // New password must be different from current password
        if !currentPassword.errors[.required] && !newPassword.errors[.required] {
            if currentPassword.value == newPassword.value {
                errors.update(
                    for: Validator<Any>(type: .passwordDifferent) { _ in false },
                    value: false
                )
            }
        }

        // Repeat password must match new password
        if !newPassword.errors[.required] && !confirmNewPassword.errors[.required] {
            if newPassword.value != confirmNewPassword.value {
                errors.update(
                    for: Validator<Any>(type: .passwordMatch) { _ in false },
                    value: false
                )
            }
        }

        updateFormErrors(errors)
    }

    // MARK: - Error helpers
    /// Convenience helper used by screens/stores to map Validator errors into human-readable strings.
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isTouched || control.isDirty else { return nil }

        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.minLength], let min = control.errors.value(for: .minLength) as? Int {
            return FormErrorMessages.minLength(min)
        }
        if control.errors[.maxLength] {
            return FormErrorMessages.passwordMaxLength
        }

        if control === newPassword,
           formErrors[.passwordDifferent] {
            return FormErrorMessages.newPasswordDifferent
        }
        if control === confirmNewPassword,
           formErrors[.passwordMatch] {
            return FormErrorMessages.passwordMatch
        }

        return nil
    }
} 
