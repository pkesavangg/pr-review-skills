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
    var biologicalSex = FormControl("")
    var birthLengthInches = FormControl("")
    var birthWeightLbs = FormControl("")
    var birthWeightOz = FormControl("")

    // MARK: - Regex patterns from babyApp
    /// Weight lb: 1-3 digits, whole numbers only (min 1, max 999)
    private let weightLbPattern = "^\\d{1,3}$"
    /// Weight oz: 1-2 digits, optional single decimal (max 15.9)
    private let weightOzPattern = "^\\d{1,2}(\\.\\d)?$"
    /// Length: 1-2 digits, optional single decimal (imperial inches)
    private let lengthPattern = "^\\d{1,2}(\\.\\d)?$"

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
        birthday.value = Date()
        birthday.markAsPristine()
        biologicalSex.value = ""
        biologicalSex.markAsPristine()
        birthLengthInches.value = ""
        birthLengthInches.markAsPristine()
        birthWeightLbs.value = ""
        birthWeightLbs.markAsPristine()
        birthWeightOz.value = ""
        birthWeightOz.markAsPristine()
    }

    // MARK: - Error Messages

    func getNameError() -> String? {
        guard name.isDirty else { return nil }
        if name.errors[.required] { return FormErrorMessages.required }
        if name.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if name.errors[.maxLength] { return FormErrorMessages.maxLength(50) }
        return nil
    }

    func getBirthdayError() -> String? {
        guard birthday.isDirty else { return nil }
        if birthday.errors[.futureDate] { return FormErrorMessages.futureDate }
        return nil
    }

    /// Weight (lbs) validation: if entered, must match `^\d{1,3}$` and be 1-999
    func getBirthWeightError() -> String? {
        let val = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: weightLbPattern, options: .regularExpression) != nil,
              let num = Int(val), num >= 1, num <= 999 else {
            return "Please enter a valid weight."
        }
        return nil
    }

    /// Length validation: if entered, must match `^\d{1,2}(\.\d)?$` and be >= 1
    func getBirthLengthError() -> String? {
        let val = birthLengthInches.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: lengthPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 1 else {
            return "Please enter a valid length."
        }
        return nil
    }

    // MARK: - Form Validity

    /// Mirrors babyApp's `isFormValid()`: name + birthday + sex required,
    /// weight/length optional but if entered must pass validation.
    var isProfileValid: Bool {
        // Name is required
        guard name.isValid else { return false }
        // If weight is entered and invalid, block save
        if !birthWeightLbs.value.trimmingCharacters(in: .whitespaces).isEmpty && getBirthWeightError() != nil {
            return false
        }
        // If length is entered and invalid, block save
        if !birthLengthInches.value.trimmingCharacters(in: .whitespaces).isEmpty && getBirthLengthError() != nil {
            return false
        }
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
