//
//  BabyProfileSetupFormTests.swift
//  meAppTests
//

import Combine
import Foundation
@testable import meApp
import Testing

struct BabyProfileSetupFormTests {

    // MARK: - Initial State

    @Test("initial state: required fields empty, form invalid")
    func initialState() {
        let form = BabyProfileSetupForm()

        #expect(form.name.value.isEmpty)
        #expect(form.biologicalSex.value.isEmpty)
        #expect(form.birthLengthInches.value.isEmpty)
        #expect(form.birthWeightLbs.value.isEmpty)
        #expect(form.birthWeightOz.value.isEmpty)
        #expect(form.isProfileValid == false)
    }

    // MARK: - Name Validation

    @Test("getNameError: returns required when name is empty and dirty")
    func nameRequired() {
        let form = BabyProfileSetupForm()

        form.name.markAsDirty()
        form.name.validate()

        #expect(form.getNameError() == ProfileFormTestText.required)
    }

    @Test("getNameError: returns max length error when name exceeds 50 chars")
    func nameMaxLength() {
        let form = BabyProfileSetupForm()

        form.name.value = String(repeating: "a", count: 51)

        #expect(form.getNameError() == ProfileFormTestText.maxLength50)
    }

    @Test("getNameError: returns nil when name is valid")
    func nameValid() {
        let form = BabyProfileSetupForm()

        form.name.value = "Baby Name"

        #expect(form.getNameError() == nil)
    }

    // MARK: - Birthday Validation

    @Test("getBirthdayError: returns future date error for tomorrow")
    func birthdayFutureDate() throws {
        let form = BabyProfileSetupForm()

        let tomorrow = try #require(Calendar.current.date(byAdding: .day, value: 1, to: Date()))
        form.birthday.value = tomorrow

        #expect(form.getBirthdayError() == ProfileFormTestText.futureDate)
    }

    @Test("getBirthdayError: returns nil for past date")
    func birthdayPastDate() throws {
        let form = BabyProfileSetupForm()

        let yesterday = try #require(Calendar.current.date(byAdding: .day, value: -1, to: Date()))
        form.birthday.value = yesterday

        #expect(form.getBirthdayError() == nil)
    }

    // MARK: - Biological Sex Validation

    @Test("getBiologicalSexError: returns required when empty and dirty")
    func biologicalSexRequired() {
        let form = BabyProfileSetupForm()

        form.biologicalSex.markAsDirty()
        form.biologicalSex.validate()

        #expect(form.getBiologicalSexError() == ProfileFormTestText.required)
    }

    @Test("getBiologicalSexError: returns nil when set")
    func biologicalSexValid() {
        let form = BabyProfileSetupForm()

        form.biologicalSex.value = "Male"

        #expect(form.getBiologicalSexError() == nil)
    }

    // MARK: - Weight Lbs Validation

    @Test("getBirthWeightLbsError: returns nil when empty (optional field)")
    func weightLbsEmptyIsValid() {
        let form = BabyProfileSetupForm()

        #expect(form.getBirthWeightLbsError() == nil)
    }

    @Test("getBirthWeightLbsError: returns nil for valid whole number 1-999")
    func weightLbsValidRange() {
        let form = BabyProfileSetupForm()

        form.birthWeightLbs.value = "7"
        #expect(form.getBirthWeightLbsError() == nil)

        form.birthWeightLbs.value = "999"
        #expect(form.getBirthWeightLbsError() == nil)
    }

    @Test("getBirthWeightLbsError: returns error for decimal values")
    func weightLbsDecimalInvalid() {
        let form = BabyProfileSetupForm()

        form.birthWeightLbs.value = "7.5"

        #expect(form.getBirthWeightLbsError() == ProfileFormTestText.invalidWeight)
    }

    @Test("getBirthWeightLbsError: returns error for value exceeding 999")
    func weightLbsExceedsMax() {
        let form = BabyProfileSetupForm()

        form.birthWeightLbs.value = "1000"

        #expect(form.getBirthWeightLbsError() == ProfileFormTestText.invalidWeight)
    }

    @Test("getBirthWeightLbsError: returns error for zero")
    func weightLbsZeroInvalid() {
        let form = BabyProfileSetupForm()

        form.birthWeightLbs.value = "0"

        #expect(form.getBirthWeightLbsError() == ProfileFormTestText.invalidWeight)
    }

    // MARK: - Weight Oz Validation

    @Test("getBirthWeightOzError: returns nil when empty (optional field)")
    func weightOzEmptyIsValid() {
        let form = BabyProfileSetupForm()

        #expect(form.getBirthWeightOzError() == nil)
    }

    @Test("getBirthWeightOzError: returns nil for valid range 0-15.9")
    func weightOzValidRange() {
        let form = BabyProfileSetupForm()

        form.birthWeightOz.value = "0"
        #expect(form.getBirthWeightOzError() == nil)

        form.birthWeightOz.value = "15.9"
        #expect(form.getBirthWeightOzError() == nil)

        form.birthWeightOz.value = "8"
        #expect(form.getBirthWeightOzError() == nil)
    }

    @Test("getBirthWeightOzError: returns error when exceeds 15.9")
    func weightOzExceedsMax() {
        let form = BabyProfileSetupForm()

        form.birthWeightOz.value = "16"

        #expect(form.getBirthWeightOzError() == ProfileFormTestText.invalidWeight)
    }

    @Test("getBirthWeightOzError: returns error for more than one decimal place")
    func weightOzTooManyDecimals() {
        let form = BabyProfileSetupForm()

        form.birthWeightOz.value = "8.25"

        #expect(form.getBirthWeightOzError() == ProfileFormTestText.invalidWeight)
    }

    // MARK: - Combined Weight Error

    @Test("getBirthWeightError: returns nil when both fields empty")
    func weightCombinedBothEmpty() {
        let form = BabyProfileSetupForm()

        #expect(form.getBirthWeightError() == nil)
    }

    @Test("getBirthWeightError: returns lbs error when lbs invalid")
    func weightCombinedLbsInvalid() {
        let form = BabyProfileSetupForm()

        form.birthWeightLbs.value = "1000"

        #expect(form.getBirthWeightError() == ProfileFormTestText.invalidWeight)
    }

    @Test("getBirthWeightError: returns oz error when oz invalid")
    func weightCombinedOzInvalid() {
        let form = BabyProfileSetupForm()

        form.birthWeightOz.value = "16"

        #expect(form.getBirthWeightError() == ProfileFormTestText.invalidWeight)
    }

    // MARK: - Birth Length Validation

    @Test("getBirthLengthError: returns nil when empty (optional field)")
    func lengthEmptyIsValid() {
        let form = BabyProfileSetupForm()

        #expect(form.getBirthLengthError() == nil)
    }

    @Test("getBirthLengthError: returns nil for valid range when touched")
    func lengthValidRange() {
        let form = BabyProfileSetupForm()

        form.birthLengthInches.value = "20"
        form.birthLengthInches.markAsTouched()

        #expect(form.getBirthLengthError() == nil)
    }

    @Test("getBirthLengthError: returns error for value below 1 when touched")
    func lengthBelowMin() {
        let form = BabyProfileSetupForm()

        form.birthLengthInches.value = "0"
        form.birthLengthInches.markAsTouched()

        #expect(form.getBirthLengthError() == ProfileFormTestText.invalidLength)
    }

    @Test("getBirthLengthError: returns error for three-digit value when touched")
    func lengthThreeDigitsInvalid() {
        let form = BabyProfileSetupForm()

        form.birthLengthInches.value = "100"
        form.birthLengthInches.markAsTouched()

        #expect(form.getBirthLengthError() == ProfileFormTestText.invalidLength)
    }

    // MARK: - isProfileValid

    @Test("isProfileValid: true when required fields filled, optional empty")
    func profileValidWithRequiredOnly() {
        let form = BabyProfileSetupForm()

        form.name.value = "Test Baby"
        form.biologicalSex.value = "Female"

        #expect(form.isProfileValid == true)
    }

    @Test("isProfileValid: true when all fields filled with valid values")
    func profileValidWithAllFields() {
        let form = BabyProfileSetupForm()

        form.name.value = "Test Baby"
        form.biologicalSex.value = "Male"
        form.birthWeightLbs.value = "7"
        form.birthWeightOz.value = "8"
        form.birthLengthInches.value = "20"

        #expect(form.isProfileValid == true)
    }

    @Test("isProfileValid: false when name empty")
    func profileInvalidEmptyName() {
        let form = BabyProfileSetupForm()

        form.biologicalSex.value = "Male"

        #expect(form.isProfileValid == false)
    }

    @Test("isProfileValid: false when sex empty")
    func profileInvalidEmptySex() {
        let form = BabyProfileSetupForm()

        form.name.value = "Test Baby"

        #expect(form.isProfileValid == false)
    }

    @Test("isProfileValid: false when optional weight is entered but invalid")
    func profileInvalidBadWeight() {
        let form = BabyProfileSetupForm()

        form.name.value = "Test Baby"
        form.biologicalSex.value = "Male"
        form.birthWeightLbs.value = "1000"

        #expect(form.isProfileValid == false)
    }

    @Test("isProfileValid: false when optional length is entered but invalid")
    func profileInvalidBadLength() {
        let form = BabyProfileSetupForm()

        form.name.value = "Test Baby"
        form.biologicalSex.value = "Male"
        form.birthLengthInches.value = "100"
        form.birthLengthInches.markAsTouched()

        #expect(form.isProfileValid == false)
    }

    // MARK: - Parsed Values

    @Test("parsedBirthLengthInches: parses valid input and returns nil for empty")
    func parsedLength() {
        let form = BabyProfileSetupForm()

        #expect(form.parsedBirthLengthInches == nil)

        form.birthLengthInches.value = "20.5"
        #expect(form.parsedBirthLengthInches == 20.5)
    }

    @Test("parsedBirthWeightLbs: parses valid input and returns nil for empty")
    func parsedWeightLbs() {
        let form = BabyProfileSetupForm()

        #expect(form.parsedBirthWeightLbs == nil)

        form.birthWeightLbs.value = "7"
        #expect(form.parsedBirthWeightLbs == 7.0)
    }

    @Test("parsedBirthWeightOz: parses valid input and returns nil for empty")
    func parsedWeightOz() {
        let form = BabyProfileSetupForm()

        #expect(form.parsedBirthWeightOz == nil)

        form.birthWeightOz.value = "8.5"
        #expect(form.parsedBirthWeightOz == 8.5)
    }

    // MARK: - Reset

    @Test("reset: clears all fields and marks as pristine")
    func resetClearsFields() {
        let form = BabyProfileSetupForm()

        form.name.value = "Baby"
        form.biologicalSex.value = "Male"
        form.birthWeightLbs.value = "7"
        form.birthWeightOz.value = "8"
        form.birthLengthInches.value = "20"

        form.reset()

        #expect(form.name.value.isEmpty)
        #expect(form.biologicalSex.value.isEmpty)
        #expect(form.birthWeightLbs.value.isEmpty)
        #expect(form.birthWeightOz.value.isEmpty)
        #expect(form.birthLengthInches.value.isEmpty)
        #expect(form.name.isPristine == true)
        #expect(form.biologicalSex.isPristine == true)
    }

    // MARK: - formDidChange Publisher

    @Test("formDidChange: emits when any field changes")
    func formDidChangeEmits() {
        let form = BabyProfileSetupForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.formDidChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.name.value = "Baby"
        form.biologicalSex.value = "Male"
        form.birthWeightLbs.value = "7"

        #expect(emissions >= 3)
    }

    // MARK: - refreshDuplicateNameError (shared duplicate-name check)

    @Test("refreshDuplicateNameError: exact match sets duplicate error and returns true")
    func refreshDuplicate_exactMatch_setsError() {
        let form = BabyProfileSetupForm()
        form.name.value = "Aria"

        let isDuplicate = form.refreshDuplicateNameError(against: ["Aria"])

        #expect(isDuplicate == true)
        #expect(form.duplicateNameError == BabyScaleSetupStrings.BabyProfile.duplicateNameError)
    }

    @Test("refreshDuplicateNameError: unique name clears error and returns false")
    func refreshDuplicate_uniqueName_clearsError() {
        let form = BabyProfileSetupForm()
        form.duplicateNameError = BabyScaleSetupStrings.BabyProfile.duplicateNameError
        form.name.value = "Bella"

        let isDuplicate = form.refreshDuplicateNameError(against: ["Aria"])

        #expect(isDuplicate == false)
        #expect(form.duplicateNameError == nil)
    }

    @Test("refreshDuplicateNameError: match is case-insensitive")
    func refreshDuplicate_caseInsensitive() {
        let form = BabyProfileSetupForm()
        form.name.value = "aria"

        #expect(form.refreshDuplicateNameError(against: ["ARIA"]) == true)
    }

    @Test("refreshDuplicateNameError: surrounding whitespace and newlines are ignored")
    func refreshDuplicate_trimsWhitespaceAndNewlines() {
        let form = BabyProfileSetupForm()
        form.name.value = "  Aria\n"

        #expect(form.refreshDuplicateNameError(against: ["Aria"]) == true)
    }

    @Test("refreshDuplicateNameError: empty/whitespace-only name is never a duplicate")
    func refreshDuplicate_emptyName_returnsFalse() {
        let form = BabyProfileSetupForm()
        form.duplicateNameError = BabyScaleSetupStrings.BabyProfile.duplicateNameError
        form.name.value = "   "

        let isDuplicate = form.refreshDuplicateNameError(against: ["   ", "Aria"])

        #expect(isDuplicate == false)
        #expect(form.duplicateNameError == nil)
    }

    @Test("refreshDuplicateNameError: no other names means no duplicate")
    func refreshDuplicate_noOtherNames_returnsFalse() {
        let form = BabyProfileSetupForm()
        form.name.value = "Aria"

        #expect(form.refreshDuplicateNameError(against: []) == false)
        #expect(form.duplicateNameError == nil)
    }
}

// MARK: - Test String Constants

private enum ProfileFormTestText {
    static let required = "Required."
    static let maxLength50 = "maximum value should be 50"
    static let futureDate = "future dates not accepted"
    static let invalidWeight = "Please enter a valid weight."
    static let invalidLength = "Please enter a valid length."
}
