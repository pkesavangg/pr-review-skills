//
//  BabyProfileSetupForm.swift
//  meApp
//

import Combine
import Foundation

/// Form for baby profile creation during scale setup.
/// Validation rules ported from babyApp:
/// - name: required
/// - birthday: required, not future
/// - sex: required
/// - weight (lb): optional, if entered must be 1-999 whole number
/// - weight (oz): optional, if entered must be 0-15.9 with max 1 decimal
/// - length: optional, if entered must be 1-99.9 with max 1 decimal (imperial inches)
class BabyProfileSetupForm: ObservableForm {
    var name = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(50)])
    var birthday = FormControl(Date(), validators: [.futureDate])
    var biologicalSex = FormControl("", validators: [.required])
    var birthLengthInches = FormControl("", validators: [.required])
    var birthWeightLbs = FormControl("", validators: [.required])
    var birthWeightOz = FormControl("")

    // MARK: - Regex patterns from babyApp
    /// Weight lb: 1-3 digits, whole numbers only (min 1, max 999)
    private let weightLbPattern = "^\\d{1,3}$"
    /// Weight oz: 1-2 digits, optional single decimal (max 15.9)
    private let weightOzPattern = "^\\d{1,2}(\\.\\d)?$"
    /// Length: 1-2 digits, optional single decimal (imperial inches)
    private let lengthPattern = "^\\d{1,3}(\\.\\d)?$"

    /// Publisher that merges all value changes in the form.
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            name.$value.map { _ in () }.eraseToAnyPublisher(),
            birthday.$value.map { _ in () }.eraseToAnyPublisher(),
            biologicalSex.$value.map { _ in () }.eraseToAnyPublisher(),
            birthLengthInches.$value.map { _ in () }.eraseToAnyPublisher(),
            birthWeightLbs.$value.map { _ in () }.eraseToAnyPublisher(),
            birthWeightOz.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }

    /// Reset the form to initial state.
    func reset() {
        name.value = ""
        name.markAsPristine()
        name.markAsUntouched()
        birthday.value = Date()
        birthday.markAsPristine()
        birthday.markAsUntouched()
        biologicalSex.value = ""
        biologicalSex.markAsPristine()
        biologicalSex.markAsUntouched()
        birthLengthInches.value = ""
        birthLengthInches.markAsPristine()
        birthLengthInches.markAsUntouched()
        birthWeightLbs.value = ""
        birthWeightLbs.markAsPristine()
        birthWeightLbs.markAsUntouched()
        birthWeightOz.value = ""
        birthWeightOz.markAsPristine()
        birthWeightOz.markAsUntouched()
    }

    // MARK: - Error Messages

    func getNameError() -> String? {
        guard name.isDirty || name.isTouched else { return nil }
        if name.errors[.required] { return BabyScaleSetupStrings.BabyProfile.required }
        if name.errors[.noWhiteSpace] { return BabyScaleSetupStrings.BabyProfile.required }
        if name.errors[.maxLength] { return FormErrorMessages.maxLength(50) }
        return nil
    }

    func getBirthdayError() -> String? {
        guard birthday.isDirty else { return nil }
        if birthday.errors[.futureDate] { return FormErrorMessages.futureDate }
        return nil
    }

    func getBiologicalSexError() -> String? {
        guard biologicalSex.isDirty || biologicalSex.isTouched else { return nil }
        if biologicalSex.errors[.required] { return BabyScaleSetupStrings.BabyProfile.required }
        return nil
    }

    /// Weight (lbs) validation: if entered, must match `^\d{1,3}$` and be 1-999
    func getBirthWeightLbsError() -> String? {
        let val = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: weightLbPattern, options: .regularExpression) != nil,
              let num = Int(val), num >= 1, num <= 999 else {
            return BabyScaleSetupStrings.BabyProfile.invalidWeight
        }
        return nil
    }

    /// Weight (oz) validation: if entered, must match `^\d{1,2}(\.\d)?$` and be 0-15.9
    func getBirthWeightOzError() -> String? {
        let val = birthWeightOz.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: weightOzPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 0, num <= 15.9 else {
            return BabyScaleSetupStrings.BabyProfile.invalidWeight
        }
        return nil
    }

    /// Combined birth weight error (lbs or oz).
    func getBirthWeightError() -> String? {
        let lbsVal = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
        let ozVal = birthWeightOz.value.trimmingCharacters(in: .whitespaces)
        if lbsVal.isEmpty && ozVal.isEmpty {
            let interacted = birthWeightLbs.isDirty || birthWeightLbs.isTouched
                || birthWeightOz.isDirty || birthWeightOz.isTouched
            guard interacted else { return nil }
            return BabyScaleSetupStrings.BabyProfile.required
        }
        if let lbsError = getBirthWeightLbsError() { return lbsError }
        if let ozError = getBirthWeightOzError() { return ozError }
        return nil
    }

    /// Length validation: required; if entered, must match pattern and be >= 1
    func getBirthLengthError() -> String? {
        let val = birthLengthInches.value.trimmingCharacters(in: .whitespaces)
        if val.isEmpty {
            guard birthLengthInches.isDirty || birthLengthInches.isTouched else { return nil }
            return BabyScaleSetupStrings.BabyProfile.required
        }
        guard val.range(of: lengthPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 1 else {
            return BabyScaleSetupStrings.BabyProfile.invalidLength
        }
        return nil
    }

    // MARK: - Form Validity

    /// All fields required: name, sex, length, weight (at least lbs or oz).
    var isProfileValid: Bool {
        guard name.isValid else { return false }
        guard !biologicalSex.value.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        guard !birthLengthInches.value.trimmingCharacters(in: .whitespaces).isEmpty
              && getBirthLengthError() == nil else { return false }
        let lbsVal = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
        let ozVal = birthWeightOz.value.trimmingCharacters(in: .whitespaces)
        guard !lbsVal.isEmpty || !ozVal.isEmpty else { return false }
        if !lbsVal.isEmpty && getBirthWeightLbsError() != nil { return false }
        if !ozVal.isEmpty && getBirthWeightOzError() != nil { return false }
        return true
    }

    // MARK: - Parsed Values

    var parsedBirthLengthInches: Double? {
        Double(birthLengthInches.value)
    }

    var parsedBirthWeightLbs: Double? {
        Double(birthWeightLbs.value)
    }

    var parsedBirthWeightOz: Double? {
        Double(birthWeightOz.value)
    }
}
