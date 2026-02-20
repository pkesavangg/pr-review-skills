import Combine
//
//  SignupForm.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//
import Foundation

// MARK: SignupForm
/// This form is responsible for managing the signup process.
class SignupForm: ObservableForm {
    var firstName = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(100)])
    var lastName = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(100)])
    var birthday: FormControl<Date> = {
        let defaultDate = Calendar.current.date(from: DateComponents(year: 2000, month: 1, day: 1)) ?? Date()
        return FormControl(defaultDate, validators: [.futureDate])
    }()
    var gender = FormControl("", validators: [.required])
    var goalType = FormControl(GoalTypeSegment.losegainValue, validators: [.required])
    var currentWeight = FormControl("", validators: [.required, .minValue()])
    var goalWeight = FormControl("", validators: [.required, .minValue()])
    var useMetric = FormControl(false)
    var height = FormControl(Double(700))
    var email = FormControl("", validators: [.required, .email, .maxLength(100)])
    var password = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    var confirmPassword = FormControl("", validators: [.required, .minLength(6), .maxLength(50)])
    var zipcode = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(20)])
    
    /// Publisher that merges all value changes in the form
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            firstName.$value.map { _ in () }.eraseToAnyPublisher(),
            lastName.$value.map { _ in () }.eraseToAnyPublisher(),
            birthday.$value.map { _ in () }.eraseToAnyPublisher(),
            gender.$value.map { _ in () }.eraseToAnyPublisher(),
            goalType.$value.map { _ in () }.eraseToAnyPublisher(),
            useMetric.$value.map { _ in () }.eraseToAnyPublisher(),
            currentWeight.$value.map { _ in () }.eraseToAnyPublisher(),
            goalWeight.$value.map { _ in () }.eraseToAnyPublisher(),
            height.$value.map { _ in () }.eraseToAnyPublisher(),
            email.$value.map { _ in () }.eraseToAnyPublisher(),
            password.$value.map { _ in () }.eraseToAnyPublisher(),
            confirmPassword.$value.map { _ in () }.eraseToAnyPublisher(),
            zipcode.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }
    
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
        
        // Check if goal weight equals current weight when in lose/gain mode
        if goalType.value != GoalType.maintain.rawValue {
            if hasEqualWeights {
                errors.update(
                    for: Validator<Any>(type: .weightEqual) { _ in false },
                    value: false
                )
            }
        }
        
        updateFormErrors(errors)
    }
    
    // MARK: - Weight Validation Helpers
    
    /// Returns `true` if both weights are positive and equal.
    var hasEqualWeights: Bool {
        let current = Double(currentWeight.value) ?? 0.0
        let goal = Double(goalWeight.value) ?? 0.0
        return current > 0 && goal > 0 && current == goal
    }
    
    // MARK: - Form State Helpers
    
    var isTouched: Bool {
        goalType.isTouched || currentWeight.isTouched || goalWeight.isTouched
    }
    
    var isGoalValidForSave: Bool {
        let isAnyGoalFieldDirty =
        goalType.isDirty || currentWeight.isDirty || goalWeight.isDirty
        guard isAnyGoalFieldDirty else { return false }
        
        let isMaintainMode = goalType.value == GoalType.maintain.rawValue
        
        if !isMaintainMode && hasEqualWeights {
            return false
        }
        
        // For maintain mode, only goal weight needs to be dirty/touched
        // For lose/gain mode, both weights need to be dirty/touched
        if isMaintainMode {
            guard isTouched || goalType.isDirty || goalWeight.isDirty else { return false }
        } else {
            guard isTouched || goalType.isDirty || (currentWeight.isDirty && goalWeight.isDirty) else { return false }
        }
        
        return isMaintainMode
        ? goalWeight.isValid
        : currentWeight.isValid && goalWeight.isValid
    }

    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isTouched || control.isDirty else { return nil }

        if control === currentWeight && goalType.value == GoalType.maintain.rawValue {
            return nil
        }
        if (control === email || control === password || control === confirmPassword || control === zipcode || control === firstName || control === lastName) && control.errors[.required] {
            return FormErrorMessages.leaveBlank
        }
        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.email] { return FormErrorMessages.email }
        if control.errors[.minLength], let minLength = control.errors.value(for: .minLength) as? Int {
            if control === password || control === confirmPassword {
                return FormErrorMessages.signupPasswordMinLength
            } else {
                return FormErrorMessages.minLength(minLength)
            }
        }
        if control.errors[.maxLength], let maxLength = control.errors.value(for: .maxLength) as? Int {
            // Use custom message for password fields
            if control === password || control === confirmPassword {
                return FormErrorMessages.passwordMaxLength
            } else if control === zipcode {
                return FormErrorMessages.maxLength(20)
            } else if control === email {
                return FormErrorMessages.emailMaxLength
            } else {
                return FormErrorMessages.maxLength(maxLength)
            }
        }
        if control.errors[.minValue] {
            if control === currentWeight || control === goalWeight {
                return useMetric.value ? FormErrorMessages.minWeightKg : FormErrorMessages.minWeightLb
            }
        }
        if control.errors[.maxValue], let _ = control.errors.value(for: .maxValue) as? Double {
            if control === currentWeight || control === goalWeight {
                return useMetric.value ? FormErrorMessages.maxWeightKg : FormErrorMessages.maxWeightLb
            }
        }
        if control.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if control.errors[.futureDate] { return FormErrorMessages.futureDate }
        if control === confirmPassword && formErrors[.passwordMatch] {
            return FormErrorMessages.passwordMatch
        }

        if goalType.value != GoalType.maintain.rawValue
            && hasEqualWeights
            && control === goalWeight
            && (goalWeight.isTouched || goalWeight.isDirty || currentWeight.isTouched || currentWeight.isDirty) {
            return FormErrorMessages.valueShouldNotBeEqual
        }

        return nil
    }
    
    /// Resets the goal-related form fields to their default state.
    func resetGoal() {
        goalType.value = GoalTypeSegment.losegainValue
        currentWeight.value = ""
        goalWeight.value = ""
        goalType.resetInteractionState()
        currentWeight.resetInteractionState()
        goalWeight.resetInteractionState()
    }
}
