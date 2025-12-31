//
//  EditProfileForm.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 20/06/25.
//

import Foundation
import Combine

/// Form model used in Edit-Profile screen for validating and capturing user profile changes.
/// Mirrors the validation rules used during signup (name / email / zipcode / birthday) but omits
/// password-specific logic.
class EditProfileForm: ObservableForm {
    // MARK: - Controls
    var firstName = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(100)])
    var lastName  = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(100)])
    var birthday: FormControl<Date> = {
        let defaultDate = Calendar.current.date(from: DateComponents(year: 2000, month: 1, day: 1)) ?? Date()
        return FormControl(defaultDate, validators: [.futureDate])
    }()
    var email     = FormControl("", validators: [.required, .email, .maxLength(100)])
    var zipcode   = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(20)])

    // MARK: - Change publisher
    /// Emits whenever any of the underlying controls changes – convenient for wiring to Combine in `SettingsStore`.
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            firstName.$value.map { _ in () }.eraseToAnyPublisher(),
            lastName.$value.map  { _ in () }.eraseToAnyPublisher(),
            birthday.$value.map { _ in () }.eraseToAnyPublisher(),
            email.$value.map     { _ in () }.eraseToAnyPublisher(),
            zipcode.$value.map   { _ in () }.eraseToAnyPublisher(),
        ])
        .eraseToAnyPublisher()
    }

    // MARK: - Validation
    /// Aggregate (cross-field) validation – minimal for now but we keep the override for future rules.
    override func validateForm() {
        // Nothing beyond per-control validation at the moment.
        updateFormErrors(ValidationErrors<Any>())
    }

    /// Convenience helper used by screens/stores to map Validator errors into human-readable strings.
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }

        if control === email && control.errors[.required] {
            return FormErrorMessages.leaveBlank
        }
        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.email] { return FormErrorMessages.email }
        if control.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if control.errors[.futureDate] { return FormErrorMessages.futureDate }

        if control.errors[.maxLength], let max = control.errors.value(for: .maxLength) as? Int {
            if control === email && max == 100 {
                return FormErrorMessages.emailMaxLength
            }
            return FormErrorMessages.maxLength(max)
        }

        return nil
    }
} 
