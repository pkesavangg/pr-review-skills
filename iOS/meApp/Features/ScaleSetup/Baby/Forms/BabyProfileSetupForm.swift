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
/// - weight (kg): optional, if entered must be 0.1-999.9 with max 1 decimal
/// - length (inches): optional, if entered must be 1-99.9 with max 1 decimal
/// - length (cm): optional, if entered must be 1-999.9 with max 1 decimal
class BabyProfileSetupForm: ObservableForm {
    var name = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(50)])
    var birthday = FormControl(Date(), validators: [.futureDate])
    var biologicalSex = FormControl("", validators: [.required])

    // Imperial fields (stored as source of truth in the Baby model)
    var birthLengthInches = FormControl("")
    var birthWeightLbs = FormControl("")
    var birthWeightOz = FormControl("")

    // Metric fields
    var birthLengthCm = FormControl("")
    var birthWeightKg = FormControl("")

    // Unit selection — drives which fields are shown and which length unit is derived
    @Published var selectedWeightUnit: BabyWeightUnit = .kg

    /// Derived length unit: kg → cm, lb/lb-oz → in
    var derivedLengthUnit: BabyLengthUnit {
        selectedWeightUnit == .kg ? .cm : .inches
    }

    // MARK: - Regex patterns from babyApp
    /// Weight lb component for lb/oz mode: 1-3 digits, whole numbers only (min 1, max 999)
    private let weightLbComponentPattern = "^\\d{1,3}$"
    /// Weight lb decimal mode: 1-3 digits, optional single decimal (0.1-999.9)
    private let weightLbDecimalPattern = "^\\d{1,3}(\\.\\d)?$"
    /// Weight oz: 1-2 digits, optional single decimal (max 15.9)
    private let weightOzPattern = "^\\d{1,2}(\\.\\d)?$"
    /// Weight kg: 1-3 digits, optional single decimal (0.1-999.9)
    private let weightKgPattern = "^\\d{1,3}(\\.\\d)?$"
    /// Length inches: 1-2 digits, optional single decimal
    private let lengthInchesPattern = "^\\d{1,2}(\\.\\d)?$"
    /// Length cm: 1-3 digits, optional single decimal
    private let lengthCmPattern = "^\\d{1,3}(\\.\\d)?$"

    /// Publisher that merges all value changes in the form.
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            name.$value.map { _ in () }.eraseToAnyPublisher(),
            birthday.$value.map { _ in () }.eraseToAnyPublisher(),
            biologicalSex.$value.map { _ in () }.eraseToAnyPublisher(),
            birthLengthInches.$value.map { _ in () }.eraseToAnyPublisher(),
            birthWeightLbs.$value.map { _ in () }.eraseToAnyPublisher(),
            birthWeightOz.$value.map { _ in () }.eraseToAnyPublisher(),
            birthLengthCm.$value.map { _ in () }.eraseToAnyPublisher(),
            birthWeightKg.$value.map { _ in () }.eraseToAnyPublisher(),
            $selectedWeightUnit.map { _ in () }.eraseToAnyPublisher()
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
        birthLengthCm.value = ""
        birthLengthCm.markAsPristine()
        birthLengthCm.markAsUntouched()
        birthWeightKg.value = ""
        birthWeightKg.markAsPristine()
        birthWeightKg.markAsUntouched()
        selectedWeightUnit = .kg
    }

    // MARK: - Error Messages

    /// Set externally to surface a duplicate-name validation error.
    /// Cleared automatically when the name field value changes.
    var duplicateNameError: String?

    func getNameError() -> String? {
        guard name.isDirty || name.isTouched else { return nil }
        if name.errors[.required] { return BabyScaleSetupStrings.BabyProfile.required }
        if name.errors[.noWhiteSpace] { return BabyScaleSetupStrings.BabyProfile.required }
        if name.errors[.maxLength] { return FormErrorMessages.maxLength(50) }
        if let dupError = duplicateNameError { return dupError }
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

    /// Weight (lb component in lb/oz mode): must be a whole number 1-999.
    func getBirthWeightLbsComponentError() -> String? {
        let val = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: weightLbComponentPattern, options: .regularExpression) != nil,
              let num = Int(val), num >= 1, num <= 999 else {
            return BabyScaleSetupStrings.BabyProfile.invalidWeight
        }
        return nil
    }

    /// Weight (single lb mode): must be 0.1-999.9 with max 1 decimal place.
    func getBirthWeightLbDecimalError() -> String? {
        let val = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: weightLbDecimalPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 0.1, num <= 999.9 else {
            return BabyScaleSetupStrings.BabyProfile.invalidWeight
        }
        return nil
    }

    /// Backward-compatible lbs validation entry point used by existing tests and callers.
    func getBirthWeightLbsError() -> String? {
        selectedWeightUnit == .lb ? getBirthWeightLbDecimalError() : getBirthWeightLbsComponentError()
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

    /// Weight (kg): must match pattern and be 0.1-999.9.
    func getBirthWeightKgError() -> String? {
        let val = birthWeightKg.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: weightKgPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 0.1, num <= 999.9 else {
            return BabyScaleSetupStrings.BabyProfile.invalidWeight
        }
        return nil
    }

    /// Combined birth weight error based on selected unit.
    func getBirthWeightError() -> String? {
        switch selectedWeightUnit {
        case .kg:
            return getBirthWeightKgError()
        case .lb:
            return getBirthWeightLbDecimalError()
        case .lbsOz:
            let lbsVal = birthWeightLbs.value.trimmingCharacters(in: .whitespaces)
            let ozVal = birthWeightOz.value.trimmingCharacters(in: .whitespaces)
            if lbsVal.isEmpty && ozVal.isEmpty { return nil }
            if let lbsError = getBirthWeightLbsComponentError() { return lbsError }
            if let ozError = getBirthWeightOzError() { return ozError }
            return nil
        }
    }

    /// Length (inches): must match pattern and be >= 1.
    func getBirthLengthInchesError() -> String? {
        let val = birthLengthInches.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: lengthInchesPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 1 else {
            return BabyScaleSetupStrings.BabyProfile.invalidLength
        }
        return nil
    }

    /// Length (cm): must match pattern and be >= 1.
    func getBirthLengthCmError() -> String? {
        let val = birthLengthCm.value.trimmingCharacters(in: .whitespaces)
        guard !val.isEmpty else { return nil }
        guard val.range(of: lengthCmPattern, options: .regularExpression) != nil,
              let num = Double(val), num >= 1, num <= 999.9 else {
            return BabyScaleSetupStrings.BabyProfile.invalidLength
        }
        return nil
    }

    /// Length error based on derived unit.
    func getBirthLengthError() -> String? {
        switch derivedLengthUnit {
        case .cm:
            return getBirthLengthCmError()
        case .inches:
            return getBirthLengthInchesError()
        }
    }

    // MARK: - Form Validity

    /// Required: name, birthday, sex. Birth length and weight are optional.
    var isProfileValid: Bool {
        guard name.isValid else { return false }
        guard !biologicalSex.value.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        if getBirthLengthError() != nil { return false }
        if getBirthWeightError() != nil { return false }
        return true
    }

    // MARK: - Parsed Values (always convert to imperial for storage)

    private let inchesPerCm = 0.393701
    private let lbsPerKg = 2.20462
    private let cmPerInch = 2.54
    private let ozPerLb = 16.0

    /// Birth length in inches — converts from cm if metric unit is selected.
    var parsedBirthLengthInches: Double? {
        switch derivedLengthUnit {
        case .inches:
            return Double(birthLengthInches.value)
        case .cm:
            guard let cm = Double(birthLengthCm.value) else { return nil }
            return cm * inchesPerCm
        }
    }

    /// Birth weight in lbs — converts from kg if metric unit is selected.
    /// For lb unit, returns the whole lbs value; for lbs/oz returns lbs component.
    var parsedBirthWeightLbs: Double? {
        switch selectedWeightUnit {
        case .kg:
            guard let kg = Double(birthWeightKg.value) else { return nil }
            let totalLbs = kg * lbsPerKg
            return totalLbs.rounded(.down)
        case .lb:
            guard let totalLbs = Double(birthWeightLbs.value) else { return nil }
            return totalLbs.rounded(.down)
        case .lbsOz:
            return Double(birthWeightLbs.value)
        }
    }

    /// Birth weight oz component. For kg, extracts the fractional lbs as oz.
    /// For lb unit, returns nil (no oz component). For lbs/oz, returns oz value.
    var parsedBirthWeightOz: Double? {
        switch selectedWeightUnit {
        case .kg:
            guard let kg = Double(birthWeightKg.value) else { return nil }
            let totalLbs = kg * lbsPerKg
            let wholeLbs = totalLbs.rounded(.down)
            let oz = (totalLbs - wholeLbs) * ozPerLb
            return (oz * 10).rounded() / 10 // round to 1 decimal
        case .lb:
            guard let totalLbs = Double(birthWeightLbs.value) else { return nil }
            let wholeLbs = totalLbs.rounded(.down)
            let oz = (totalLbs - wholeLbs) * ozPerLb
            return oz == 0 ? nil : (oz * 10).rounded() / 10
        case .lbsOz:
            return Double(birthWeightOz.value)
        }
    }

    func populateStoredMeasurements(
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?,
        preferredWeightUnit: BabyWeightUnit? = nil
    ) {
        if let preferredWeightUnit {
            selectedWeightUnit = preferredWeightUnit
        }

        clearMeasurementFields()

        if let birthLengthInches {
            switch derivedLengthUnit {
            case .cm:
                birthLengthCm.value = Self.formattedDecimal(birthLengthInches * cmPerInch)
            case .inches:
                self.birthLengthInches.value = Self.formattedDecimal(birthLengthInches)
            }
        }

        guard birthWeightLbs != nil || birthWeightOz != nil else { return }
        let poundsComponent = birthWeightLbs ?? 0
        let ouncesComponent = birthWeightOz ?? 0
        let totalLbs = poundsComponent + (ouncesComponent / ozPerLb)

        switch selectedWeightUnit {
        case .kg:
            birthWeightKg.value = Self.formattedDecimal(totalLbs / lbsPerKg)
        case .lb:
            self.birthWeightLbs.value = Self.formattedDecimal(totalLbs)
        case .lbsOz:
            if let birthWeightLbs {
                self.birthWeightLbs.value = String(Int(birthWeightLbs.rounded(.down)))
            }
            if let birthWeightOz {
                self.birthWeightOz.value = Self.formattedDecimal(birthWeightOz)
            }
        }
    }

    private func clearMeasurementFields() {
        birthLengthInches.value = ""
        birthWeightLbs.value = ""
        birthWeightOz.value = ""
        birthLengthCm.value = ""
        birthWeightKg.value = ""
    }

    private static func formattedDecimal(_ value: Double) -> String {
        String(format: "%.1f", value)
    }
}
