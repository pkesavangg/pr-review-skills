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
    var goalType = FormControl(GoalType.lose.rawValue, validators: [.required])
    /// Current weight as string (display units).
    var currentWeight = FormControl("", validators: [.required, .minValue()])
    /// Goal weight as string (display units).
    var goalWeight = FormControl("", validators: [.required, .minValue()])
    
    // MARK: - Cancellables
    private var cancellables: Set<AnyCancellable> = []
    
    // MARK: - Initialization
    override init() {
        super.init()
        setupWeightValidationTriggers()
    }
    
    // MARK: - Change Publisher
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            goalType.$value.map { _ in () }.eraseToAnyPublisher(),
            currentWeight.$value.map { _ in () }.eraseToAnyPublisher(),
            goalWeight.$value.map { _ in () }.eraseToAnyPublisher(),
        ])
        .eraseToAnyPublisher()
    }
    
    // MARK: - Setup
    /// Sets up validation triggers to ensure form-level validation runs immediately when weight values change
    private func setupWeightValidationTriggers() {
        // Trigger form validation when currentWeight changes
        currentWeight.$value
            .sink { [weak self] _ in
                self?.validateForm()
            }
            .store(in: &cancellables)
        
        // Trigger form validation when goalWeight changes
        goalWeight.$value
            .sink { [weak self] _ in
                self?.validateForm()
            }
            .store(in: &cancellables)
        
        // Trigger form validation when goalType changes
        goalType.$value
            .sink { [weak self] _ in
                self?.validateForm()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Validation
    
    override func validateForm() {
        var errors = ValidationErrors<Any>()
        
        if isLoseGainMode {
            validateWeightEquality(into: &errors)
        }
        
        updateFormErrors(errors)
    }
    
    private func validateWeightEquality(into errors: inout ValidationErrors<Any>) {
        let current = parseWeight(currentWeight.value)
        let goal = parseWeight(goalWeight.value)
        
        guard areWeightsEqual(current: current, goal: goal) else { return }
        guard hasUserInteractedWithWeights() else { return }
        
        errors.update(
            for: Validator<Any>(type: .weightEqual) { _ in false },
            value: false
        )
    }
    
    private func parseWeight(_ value: String) -> Double {
        Double(value) ?? 0.0
    }
    
    private func areWeightsEqual(current: Double, goal: Double) -> Bool {
        current > 0 && goal > 0 && current == goal
    }
    
    private func hasUserInteractedWithWeights() -> Bool {
        currentWeight.isDirty || goalWeight.isDirty || isDirty
    }
    
    private var isLoseGainMode: Bool {
        goalType.value != GoalType.maintain.rawValue
    }
    
    // MARK: - Form State Helpers
    
    var isTouched: Bool {
        goalType.isTouched || currentWeight.isTouched || goalWeight.isTouched
    }
    
    /// Returns true if the form is valid for saving.
    /// - Parameter focusedField: Optional focused field to enable save while typing.
    func isValidForSave(focusedField: FocusField? = nil) -> Bool {
        guard isDirty else { return false }
        
        if let focused = focusedField, isValidWhileFocused(focusedField: focused) {
            return true
        }
        
        guard isTouched || goalType.isDirty else { return false }
        return isValidForCurrentGoalType()
    }
    
    // MARK: - Private Validation Helpers
    
    private func isValidWhileFocused(focusedField: FocusField) -> Bool {
        switch focusedField {
        case .currentWeight:
            return isValidForCurrentWeightFocused()
        case .goalWeight:
            return isValidForGoalWeightFocused()
        default:
            return false
        }
    }
    
    private func isValidForCurrentWeightFocused() -> Bool {
        guard goalType.value == GoalTypeSegment.losegainValue else { return false }
        guard currentWeight.isDirty && currentWeight.isValid else { return false }
        guard !formErrors[.weightEqual] else { return false }
        return !goalWeight.value.isEmpty && goalWeight.isValid
    }
    
    private func isValidForGoalWeightFocused() -> Bool {
        guard goalWeight.isDirty && goalWeight.isValid else { return false }
        
        if goalType.value == GoalTypeSegment.losegainValue {
            guard !formErrors[.weightEqual] else { return false }
            return !currentWeight.value.isEmpty && currentWeight.isValid
        } else {
            return true
        }
    }
    
    private func isValidForCurrentGoalType() -> Bool {
        if goalType.value == GoalTypeSegment.losegainValue {
            guard !formErrors[.weightEqual] else { return false }
            return currentWeight.isValid && goalWeight.isValid
        } else {
            return goalWeight.isValid
        }
    }
    
    
    // MARK: - Error Helpers
    
    /// Returns error message for a control, if any.
    /// - Parameters:
    ///   - control: The form control to check.
    func getError<T>(for control: FormControl<T>, isMetric: Bool) -> String? {
        if control === currentWeight && !isLoseGainMode {
            return nil
        }
        
        if let fieldError = getFieldLevelError(for: control, isMetric: isMetric) {
            return fieldError
        }
        
        if let formError = getFormLevelError(for: control) {
            return formError
        }
        
        return nil
    }
    
    private func getFieldLevelError<T>(for control: FormControl<T>, isMetric: Bool) -> String? {
        guard control.isDirty else { return nil }
        
        if control.errors[.required] {
            return FormErrorMessages.required
        }
        
        if control.errors[.minValue] {
            return isMetric ? FormErrorMessages.minWeightKg : FormErrorMessages.minWeightLb
        }
        
        if control.errors[.maxValue] {
            return isMetric ? FormErrorMessages.maxWeightKg : FormErrorMessages.maxWeightLb
        }
        
        return nil
    }
    
    private func getFormLevelError<T>(for control: FormControl<T>) -> String? {
        guard isLoseGainMode && formErrors[.weightEqual] else { return nil }
        guard control === goalWeight else { return nil }
        
        let current = parseWeight(currentWeight.value)
        let goal = parseWeight(goalWeight.value)
        
        guard areWeightsEqual(current: current, goal: goal) else { return nil }
        guard goalWeight.isDirty || currentWeight.isDirty else { return nil }
        
        return FormErrorMessages.valueShouldNotBeEqual
    }
}
