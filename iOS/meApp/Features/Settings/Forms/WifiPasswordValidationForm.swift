//
//  WifiPasswordValidationForm.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import Foundation
import Combine

class WifiPasswordValidationForm: ObservableForm {
    var password = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    let lang = FormErrorMessages.self
    
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            password.$value.map { _ in () }.eraseToAnyPublisher(),
        ]).eraseToAnyPublisher()
    }
    
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }
        if control.errors[.required] { return lang.required }
        if control.errors[.minLength], let minLength = control.errors.value(for: .minLength) as? Int {
            return lang.minLength(minLength)
        }
        if control.errors[.maxLength], let maxLength = control.errors.value(for: .maxLength) as? Int {
            if control === password {
                return lang.passwordMaxLength
            } else {
                return lang.maxLength(maxLength)
            }
        }
        return nil
    }
}

