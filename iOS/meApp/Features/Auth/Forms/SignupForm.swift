//
//  SignupForm.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//
import Foundation
import Combine

class SignupForm: ObservableForm {
    var firstName = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(100)])
    var lastName = FormControl("", validators: [.noWhiteSpace, .maxLength(100)])
    var birthday = FormControl(Date(), validators: [.futureDate]) // Default date 2000-01-01
    var gender = FormControl(Sex.male.rawValue, validators: [.required])
    var goalType = FormControl("", validators: [.required, .noWhiteSpace])
    var currentWeight = FormControl("", validators: [.required])
    var height = FormControl(700)
    var email = FormControl("", validators: [.required, .email, .maxLength(200)])
    var password = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    var confirmPassword = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    var zipcode = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(20)])
    
    override func validateForm() {
        var errors = ValidationErrors<Any>()
        
        // Check if passwords match when both are filled
        if !password.errors[.required] && !confirmPassword.errors[.required] {
            if password.value != confirmPassword.value {
                errors.update(
                    for: Validator<Any>(type: .passwordMatch) { _ in false },
                    value: false
                )
            }
        }
        updateFormErrors(errors)
    }
    
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }
        
        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.email] { return FormErrorMessages.email }
        if control.errors[.minLength], let minLength = control.errors.value(for: .minLength) as? Int {
            return FormErrorMessages.minLength(minLength)
        }
        if control.errors[.maxLength], let maxLength = control.errors.value(for: .maxLength) as? Int {
            return FormErrorMessages.maxLength(maxLength)
        }
        if control.errors[.min], let minValue = control.errors.value(for: .min) as? Int {
            return FormErrorMessages.min(minValue)
        }
        if control.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if control.errors[.futureDate] { return FormErrorMessages.futureDate }
        if control.errors[.requiredTrue] { return FormErrorMessages.requiredTrue }
        if control === confirmPassword && formErrors[.passwordMatch] {
            return FormErrorMessages.passwordMatch
        }
        
        return nil
    }
}
