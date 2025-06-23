// iOS/meApp/Features/Settings/Forms/GoalForm.swift
// Form object used by the Goal settings screen.
// Mirrors the validation logic used during signup (GoalStep) so the user can
// adjust their goal after onboarding.

import Foundation
import Combine

/// Form that controls the Goal settings sheet.
/// - goalType: "maintain" or "losegain" (wrapper around `GoalTypeSegment`).
/// - currentWeight: The users current weight **in display units** (kg/lbs).
/// - goalWeight: Target weight **in display units** (kg/lbs).
/// The validators are identical to those used during sign-up.
final class GoalForm: ObservableForm {
    // MARK: - Controls
    /// Goal type string (see `GoalTypeSegment.goalTypeValue`).
    var goalType = FormControl(GoalTypeSegment.losegainValue, validators: [.required])
    /// Current weight as string (display units).
    var currentWeight = FormControl("", validators: [.required, .minValue()])
    /// Goal weight as string (display units).
    var goalWeight = FormControl("", validators: [.required, .minValue()])

    // MARK: - Change Publisher
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            goalType.$value.map { _ in () }.eraseToAnyPublisher(),
            currentWeight.$value.map { _ in () }.eraseToAnyPublisher(),
            goalWeight.$value.map { _ in () }.eraseToAnyPublisher(),
        ])
        .eraseToAnyPublisher()
    }

    // MARK: - Validation
    override func validateForm() {
        var errors = ValidationErrors<Any>()

        // Weight-equality check (only for lose / gain mode).
        if goalType.value != GoalType.maintain.rawValue {
            if !currentWeight.errors[.required] && !goalWeight.errors[.required] {
                let current = Double(currentWeight.value) ?? 0.0
                let goal    = Double(goalWeight.value) ?? 0.0
                if current > 0 && goal > 0 && current == goal {
                    errors.update(for: Validator<Any>(type: .weightEqual) { _ in false }, value: false)
                }
            }
        }

        updateFormErrors(errors)
    }

    // MARK: - Error helpers
    /// Maps validator errors to human-readable strings (leverages `FormErrorMessages`).
    func getError<T>(for control: FormControl<T>, isMetric: Bool) -> String? {
        guard control.isDirty else { return nil }
        // Skip current-weight error when maintain mode is selected.
        if control === currentWeight && goalType.value == GoalType.maintain.rawValue {
            return nil
        }

        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.minValue] {
            return isMetric ? FormErrorMessages.minWeightKg : FormErrorMessages.minWeightLb
        }
        if control.errors[.maxValue] {
            return isMetric ? FormErrorMessages.maxWeightKg : FormErrorMessages.maxWeightLb
        }
        if (goalType.value == GoalTypeSegment.losegainValue && control === goalWeight) && formErrors[.weightEqual] {
            return FormErrorMessages.valueShouldBeEqual
        }
        
        return nil
    }
} 
