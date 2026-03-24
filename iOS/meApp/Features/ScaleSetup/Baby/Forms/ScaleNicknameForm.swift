//
//  ScaleNicknameForm.swift
//  meApp
//

import Combine
import Foundation

/// Form for baby scale nickname input with validation.
class ScaleNicknameForm: ObservableForm {
    var nickname = FormControl("Smart Baby Scale", validators: [.required, .noWhiteSpace, .maxLength(30)])

    /// Publisher that merges all value changes in the form.
    var formDidChange: AnyPublisher<Void, Never> {
        nickname.$value.map { _ in () }.eraseToAnyPublisher()
    }

    /// Reset the form to default state.
    func reset() {
        nickname.value = "Smart Baby Scale"
        nickname.markAsPristine()
    }

    /// Get error message for the nickname control.
    func getError() -> String? {
        guard nickname.isDirty else { return nil }
        if nickname.errors[.required] { return FormErrorMessages.required }
        if nickname.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if nickname.errors[.maxLength] { return FormErrorMessages.maxLength(30) }
        return nil
    }
}
