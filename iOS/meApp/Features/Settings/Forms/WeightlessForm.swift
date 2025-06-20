// iOS/meApp/Features/Settings/Forms/WeightlessForm.swift
// Form object used by the Weightless settings screen
// Follows pattern of ManualEntryForm / EditProfileForm.

import Foundation
import Combine

/// Form that controls the Weightless settings page.
/// - isOn: toggle state indicating whether Weightless mode is enabled.
/// - weight: user-entered anchor weight in display units (kg or lbs depending on account).
final class WeightlessForm: ObservableForm {
    // MARK: - Controls
    /// Whether weightless mode is enabled.
    var isOn = FormControl(false)
    /// Anchor weight in **display units** (String so we can validate numeric input easily).
    var weight = FormControl("", validators: [.required, .minValue()])

    // MARK: - Change publisher
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.Merge(
            isOn.$value.map { _ in () }.eraseToAnyPublisher(),
            weight.$value.map { _ in () }.eraseToAnyPublisher()
        ).eraseToAnyPublisher()
    }

    // MARK: - Validation
    override func validateForm() {
        // weight is only required when toggle is On; clear errors when Off
//        if !isOn.value {
//           // weight.clearErrors()
//        } else {
//            weight.validate()
//        }
        updateFormErrors(ValidationErrors<Any>())
    }

    /// Returns a human-readable error for the weight field (if any).
    func getWeightError(unit: WeightUnit) -> String? {
        guard weight.isDirty else { return nil }

        if weight.errors[.required] { return FormErrorMessages.required }
        if weight.errors[.minValue] {
            return unit == .kg ? FormErrorMessages.minWeightKg : FormErrorMessages.minWeightLb
        }
        if weight.errors[.maxValue] {
            return unit == .kg ? FormErrorMessages.maxWeightKg : FormErrorMessages.maxWeightLb
        }
        return nil
    }
} 
