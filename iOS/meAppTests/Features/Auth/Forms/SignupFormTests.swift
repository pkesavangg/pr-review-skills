//
//  SignupFormTests.swift
//  meAppTests
//
//  Comprehensive unit tests for SignupForm validation logic.
//

import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct SignupFormTests {

    // MARK: - Helpers

    private func makeForm() -> SignupForm {
        SignupForm()
    }

    // MARK: - firstName validation

    @Test("firstName is invalid when empty")
    func firstNameInvalidWhenEmpty() {
        let form = makeForm()
        form.firstName.value = ""
        form.firstName.validate()
        #expect(!form.firstName.isValid)
    }

    @Test("firstName is valid with normal text")
    func firstNameValid() {
        let form = makeForm()
        form.firstName.value = "Alice"
        form.firstName.validate()
        #expect(form.firstName.isValid)
    }

    @Test("firstName is invalid when whitespace only")
    func firstNameInvalidWhitespaceOnly() {
        let form = makeForm()
        form.firstName.value = "   "
        form.firstName.validate()
        #expect(!form.firstName.isValid)
    }

    @Test("firstName is invalid when exceeds max length")
    func firstNameInvalidMaxLength() {
        let form = makeForm()
        form.firstName.value = String(repeating: "a", count: 101)
        form.firstName.validate()
        #expect(!form.firstName.isValid)
    }

    // MARK: - lastName validation

    @Test("lastName is invalid when empty")
    func lastNameInvalidWhenEmpty() {
        let form = makeForm()
        form.lastName.value = ""
        form.lastName.validate()
        #expect(!form.lastName.isValid)
    }

    @Test("lastName is valid with normal text")
    func lastNameValid() {
        let form = makeForm()
        form.lastName.value = "Smith"
        form.lastName.validate()
        #expect(form.lastName.isValid)
    }

    // MARK: - email validation

    @Test("email is invalid when empty")
    func emailInvalidWhenEmpty() {
        let form = makeForm()
        form.email.value = ""
        form.email.validate()
        #expect(!form.email.isValid)
    }

    @Test("email is invalid with bad format")
    func emailInvalidBadFormat() {
        let form = makeForm()
        form.email.value = "not-an-email"
        form.email.validate()
        #expect(!form.email.isValid)
    }

    @Test("email is valid with proper address")
    func emailValid() {
        let form = makeForm()
        form.email.value = "user@example.com"
        form.email.validate()
        #expect(form.email.isValid)
    }

    @Test("email is invalid when exceeds 100 chars")
    func emailInvalidMaxLength() {
        let form = makeForm()
        form.email.value = String(repeating: "a", count: 95) + "@b.com"
        form.email.validate()
        #expect(!form.email.isValid)
    }

    // MARK: - password validation

    @Test("password is invalid when empty")
    func passwordInvalidWhenEmpty() {
        let form = makeForm()
        form.password.value = ""
        form.password.validate()
        #expect(!form.password.isValid)
    }

    @Test("password is invalid when shorter than 6 chars")
    func passwordInvalidTooShort() {
        let form = makeForm()
        form.password.value = "ab123"
        form.password.validate()
        #expect(!form.password.isValid)
    }

    @Test("password is valid with 6+ chars")
    func passwordValid() {
        let form = makeForm()
        form.password.value = "secure123"
        form.password.validate()
        #expect(form.password.isValid)
    }

    @Test("password is invalid when exceeds 50 chars")
    func passwordInvalidTooLong() {
        let form = makeForm()
        form.password.value = String(repeating: "a", count: 51)
        form.password.validate()
        #expect(!form.password.isValid)
    }

    // MARK: - confirmPassword validation

    @Test("confirmPassword is invalid when empty")
    func confirmPasswordInvalidWhenEmpty() {
        let form = makeForm()
        form.confirmPassword.value = ""
        form.confirmPassword.validate()
        #expect(!form.confirmPassword.isValid)
    }

    @Test("confirmPassword is valid when same as password")
    func confirmPasswordValid() {
        let form = makeForm()
        form.password.value = "secure123"
        form.confirmPassword.value = "secure123"
        form.confirmPassword.validate()
        form.validate()
        #expect(form.confirmPassword.isValid)
        #expect(!form.formErrors[.passwordMatch])
    }

    @Test("form has passwordMatch error when passwords differ")
    func formHasPasswordMatchError() {
        let form = makeForm()
        form.password.value = "secure123"
        form.confirmPassword.value = "different"
        form.password.validate()
        form.confirmPassword.validate()
        form.validate()
        #expect(form.formErrors[.passwordMatch])
    }

    @Test("form has no passwordMatch error when passwords match")
    func formNoPasswordMatchError() {
        let form = makeForm()
        form.password.value = "abcdef"
        form.confirmPassword.value = "abcdef"
        form.password.validate()
        form.confirmPassword.validate()
        form.validate()
        #expect(!form.formErrors[.passwordMatch])
    }

    // MARK: - zipcode validation

    @Test("zipcode is invalid when empty")
    func zipcodeInvalidWhenEmpty() {
        let form = makeForm()
        form.zipcode.value = ""
        form.zipcode.validate()
        #expect(!form.zipcode.isValid)
    }

    @Test("zipcode is valid with 5 digit zip")
    func zipcodeValid() {
        let form = makeForm()
        form.zipcode.value = "90210"
        form.zipcode.validate()
        #expect(form.zipcode.isValid)
    }

    @Test("zipcode is invalid when exceeds 20 chars")
    func zipcodeInvalidMaxLength() {
        let form = makeForm()
        form.zipcode.value = String(repeating: "1", count: 21)
        form.zipcode.validate()
        #expect(!form.zipcode.isValid)
    }

    // MARK: - gender validation

    @Test("gender is invalid when empty")
    func genderInvalidWhenEmpty() {
        let form = makeForm()
        form.gender.value = ""
        form.gender.validate()
        #expect(!form.gender.isValid)
    }

    @Test("gender is valid when set to male")
    func genderValidMale() {
        let form = makeForm()
        form.gender.value = Sex.male.rawValue
        form.gender.validate()
        #expect(form.gender.isValid)
    }

    // MARK: - birthday validation

    @Test("birthday is invalid when set to a future date")
    func birthdayInvalidFutureDate() {
        let form = makeForm()
        let futureDate = Calendar.current.date(byAdding: .year, value: 1, to: Date()) ?? Date()
        form.birthday.value = futureDate
        form.birthday.validate()
        #expect(!form.birthday.isValid)
    }

    @Test("birthday is valid when set to a past date")
    func birthdayValidPastDate() {
        let form = makeForm()
        let pastDate = Calendar.current.date(from: DateComponents(year: 1990, month: 6, day: 15)) ?? Date()
        form.birthday.value = pastDate
        form.birthday.validate()
        #expect(form.birthday.isValid)
    }

    // MARK: - goal weight validation

    @Test("isGoalValidForSave is false when no goal fields are dirty")
    func goalValidForSaveFalseWhenNotDirty() {
        let form = makeForm()
        #expect(!form.isGoalValidForSave)
    }

    @Test("isGoalValidForSave is false when weights are equal in lose/gain mode")
    func goalValidForSaveFalseEqualWeights() {
        let form = makeForm()
        form.goalType.value = GoalTypeSegment.losegainValue
        form.currentWeight.value = "150"
        form.goalWeight.value = "150"
        form.currentWeight.markAsTouched()
        form.goalWeight.markAsTouched()
        form.currentWeight.validate()
        form.goalWeight.validate()
        form.validate()
        #expect(!form.isGoalValidForSave)
    }

    @Test("isGoalValidForSave is true for maintain mode with valid goal weight")
    func goalValidForSaveTrueMaintain() {
        let form = makeForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.goalWeight.value = "150"
        form.goalWeight.markAsTouched()
        form.goalWeight.validate()
        #expect(form.isGoalValidForSave)
    }

    @Test("hasEqualWeights is true when both weights are same positive number")
    func hasEqualWeightsTrue() {
        let form = makeForm()
        form.currentWeight.value = "200"
        form.goalWeight.value = "200"
        #expect(form.hasEqualWeights)
    }

    @Test("hasEqualWeights is false when weights differ")
    func hasEqualWeightsFalse() {
        let form = makeForm()
        form.currentWeight.value = "200"
        form.goalWeight.value = "180"
        #expect(!form.hasEqualWeights)
    }

    @Test("hasEqualWeights is false when a weight is empty")
    func hasEqualWeightsFalseWhenEmpty() {
        let form = makeForm()
        form.currentWeight.value = ""
        form.goalWeight.value = "200"
        #expect(!form.hasEqualWeights)
    }

    // MARK: - isDirty

    @Test("form isDirty after modifying firstName")
    func formIsDirtyAfterChange() {
        let form = makeForm()
        #expect(!form.isDirty)
        form.firstName.value = "Changed"
        #expect(form.isDirty)
    }

    // MARK: - getError

    @Test("getError returns nil when field is untouched and not dirty")
    func getErrorNilWhenUntouched() {
        let form = makeForm()
        let error = form.getError(for: form.email)
        #expect(error == nil)
    }

    @Test("getError returns leaveBlank for required text field when touched and empty")
    func getErrorLeaveBlankForEmail() {
        let form = makeForm()
        form.email.markAsTouched()
        form.email.value = ""
        form.email.validate()
        let error = form.getError(for: form.email)
        #expect(error != nil)
    }

    @Test("getError returns email format error")
    func getErrorEmailFormat() {
        let form = makeForm()
        form.email.markAsTouched()
        form.email.value = "badformat"
        form.email.validate()
        let error = form.getError(for: form.email)
        #expect(error == FormErrorMessages.email)
    }

    @Test("getError returns passwordMatch for confirmPassword when passwords differ")
    func getErrorPasswordMatch() {
        let form = makeForm()
        form.password.value = "abcdef"
        form.confirmPassword.value = "zzz999"
        form.password.validate()
        form.confirmPassword.markAsTouched()
        form.confirmPassword.validate()
        form.validate()
        let error = form.getError(for: form.confirmPassword)
        #expect(error == FormErrorMessages.passwordMatch)
    }

    // MARK: - resetGoal

    @Test("resetGoal clears goal fields back to defaults")
    func resetGoalClearsFields() {
        let form = makeForm()
        form.goalType.value = GoalType.gain.rawValue
        form.currentWeight.value = "180"
        form.goalWeight.value = "200"
        form.resetGoal()
        #expect(form.goalType.value == GoalTypeSegment.losegainValue)
        #expect(form.currentWeight.value == "")
        #expect(form.goalWeight.value == "")
    }
}
