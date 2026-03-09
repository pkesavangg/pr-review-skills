import Combine
import Foundation
import Testing
@testable import meApp

struct SignupFormTests {
    @Test("initial state")
    func initialState() {
        let form = SignupForm()

        #expect(form.firstName.value == "")
        #expect(form.lastName.value == "")
        #expect(form.email.value == "")
        #expect(form.password.value == "")
        #expect(form.confirmPassword.value == "")
        #expect(form.zipcode.value == "")
        #expect(form.goalType.value == GoalTypeSegment.losegainValue)
        #expect(form.isValid == false)
    }

    @Test("password match validation")
    func passwordMatchValidation() {
        let form = SignupForm()
        form.password.markAsTouched()
        form.confirmPassword.markAsTouched()

        form.password.value = "password123"
        form.confirmPassword.value = "password456"
        form.validate()

        #expect(form.formErrors[.passwordMatch] == true)
        #expect(form.getError(for: form.confirmPassword) == SignupFormTestText.passwordMatch)

        form.confirmPassword.value = "password123"
        form.validate()

        #expect(form.formErrors[.passwordMatch] == false)
        #expect(form.getError(for: form.confirmPassword) == nil)
    }

    @Test("email invalid format message")
    func emailInvalidFormatMessage() {
        let form = SignupForm()
        form.email.markAsTouched()
        form.email.value = "invalid-email"

        #expect(form.email.errors[.email] == true)
        #expect(form.getError(for: form.email) == SignupFormTestText.email)
    }

    @Test("password and confirm password min and max messages")
    func passwordAndConfirmPasswordLengthMessages() {
        let form = SignupForm()
        form.password.markAsTouched()
        form.confirmPassword.markAsTouched()

        form.password.value = "12345"
        #expect(form.getError(for: form.password) == SignupFormTestText.signupPasswordMinLength)

        form.confirmPassword.value = "12345"
        #expect(form.getError(for: form.confirmPassword) == SignupFormTestText.signupPasswordMinLength)

        form.password.value = String(repeating: "a", count: 51)
        #expect(form.getError(for: form.password) == SignupFormTestText.passwordMaxLength)

        form.confirmPassword.value = String(repeating: "a", count: 51)
        #expect(form.getError(for: form.confirmPassword) == SignupFormTestText.passwordMaxLength)
    }

    @Test("equal weights produce goal error in lose or gain mode")
    func equalWeightsValidation() {
        let form = SignupForm()
        form.goalType.value = GoalTypeSegment.losegainValue
        form.currentWeight.markAsTouched()
        form.goalWeight.markAsTouched()
        form.currentWeight.value = "180"
        form.goalWeight.value = "180"
        form.validate()

        #expect(form.hasEqualWeights == true)
        #expect(form.formErrors[.weightEqual] == true)
        #expect(form.getError(for: form.goalWeight) == SignupFormTestText.valueShouldNotBeEqual)
    }

    @Test("current weight error hidden in maintain mode")
    func currentWeightErrorHiddenInMaintainMode() {
        let form = SignupForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.currentWeight.markAsTouched()
        form.currentWeight.value = "0"

        #expect(form.currentWeight.errors[.minValue] == true)
        #expect(form.getError(for: form.currentWeight) == nil)
    }

    @Test("goal validity for maintain mode")
    func goalValidityMaintainMode() {
        let form = SignupForm()
        form.goalType.value = GoalType.maintain.rawValue

        #expect(form.isGoalValidForSave == false)

        form.goalWeight.value = "150"
        form.goalWeight.markAsTouched()

        #expect(form.isGoalValidForSave == true)
    }

    @Test("goal validity fails when weights are equal in lose or gain")
    func goalValidityLoseGainEqualWeights() {
        let form = SignupForm()
        form.goalType.value = GoalTypeSegment.losegainValue
        form.currentWeight.value = "150"
        form.goalWeight.value = "150"
        form.currentWeight.markAsTouched()
        form.goalWeight.markAsTouched()

        #expect(form.isGoalValidForSave == false)
    }

    @Test("weight unit specific min error")
    func weightUnitSpecificMinError() {
        let form = SignupForm()
        form.currentWeight.markAsTouched()

        form.useMetric.value = false
        form.currentWeight.value = "0"
        #expect(form.getError(for: form.currentWeight) == SignupFormTestText.minWeightLb)

        form.useMetric.value = true
        form.currentWeight.value = "0"
        #expect(form.getError(for: form.currentWeight) == SignupFormTestText.minWeightKg)
    }

    @Test("weight unit specific max error")
    func weightUnitSpecificMaxError() {
        let form = SignupForm()
        form.currentWeight.addValidator(.maxValue(999.0))
        form.currentWeight.markAsTouched()

        form.useMetric.value = false
        form.currentWeight.value = "1000"
        #expect(form.getError(for: form.currentWeight) == SignupFormTestText.maxWeightLb)

        form.currentWeight.removeValidator(ofType: .maxValue)
        form.currentWeight.addValidator(.maxValue(450.0))
        form.useMetric.value = true
        form.currentWeight.value = "451"
        #expect(form.getError(for: form.currentWeight) == SignupFormTestText.maxWeightKg)
    }

    @Test("future date validation")
    func futureDateValidation() {
        let form = SignupForm()
        form.birthday.markAsTouched()
        form.birthday.value = Date().addingTimeInterval(60 * 60 * 24)

        #expect(form.birthday.errors[.futureDate] == true)
        #expect(form.getError(for: form.birthday) == SignupFormTestText.futureDate)
    }

    @Test("name no whitespace validation")
    func noWhiteSpaceValidation() {
        let form = SignupForm()
        form.firstName.markAsTouched()
        form.firstName.value = "   "

        #expect(form.firstName.errors[.noWhiteSpace] == true)
        #expect(form.getError(for: form.firstName) == SignupFormTestText.noWhiteSpace)
    }

    @Test("required error uses leaveBlank for input controls")
    func requiredLeaveBlankMessage() {
        let form = SignupForm()
        form.firstName.markAsTouched()
        form.firstName.value = ""
        form.firstName.validate()

        #expect(form.firstName.errors[.required] == true)
        #expect(form.getError(for: form.firstName) == SignupFormTestText.leaveBlank)
    }

    @Test("required error uses generic required for non-special control")
    func requiredGenericMessage() {
        let form = SignupForm()
        form.gender.markAsTouched()
        form.gender.value = ""
        form.gender.validate()

        #expect(form.gender.errors[.required] == true)
        #expect(form.getError(for: form.gender) == SignupFormTestText.required)
    }

    @Test("field max length messages for email and zipcode")
    func maxLengthMessages() {
        let form = SignupForm()
        form.email.markAsTouched()
        form.zipcode.markAsTouched()

        form.email.value = String(repeating: "a", count: 90) + "@example.com"
        #expect(form.getError(for: form.email) == SignupFormTestText.emailMaxLength)

        form.zipcode.value = String(repeating: "1", count: 21)
        #expect(form.getError(for: form.zipcode) == SignupFormTestText.maxLength(20))
    }

    @Test("generic max length fallback message")
    func genericMaxLengthFallback() {
        let form = SignupForm()
        let control = FormControl("", validators: [.maxLength(3)])
        control.markAsTouched()
        control.value = "abcd"

        #expect(form.getError(for: control) == SignupFormTestText.maxLength(3))
    }

    @Test("goal touched state aggregate")
    func goalTouchedAggregate() {
        let form = SignupForm()
        #expect(form.isTouched == false)

        form.goalWeight.markAsTouched()
        #expect(form.isTouched == true)
    }

    @Test("goal validity succeeds for lose or gain with valid unequal weights")
    func goalValidityLoseGainSuccess() {
        let form = SignupForm()
        form.goalType.value = GoalTypeSegment.losegainValue
        form.currentWeight.value = "180"
        form.goalWeight.value = "170"
        form.currentWeight.markAsTouched()
        form.goalWeight.markAsTouched()

        #expect(form.hasEqualWeights == false)
        #expect(form.isGoalValidForSave == true)
    }

    @Test("resetGoal clears and resets interaction state")
    func resetGoal() {
        let form = SignupForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.currentWeight.value = "180"
        form.goalWeight.value = "150"
        form.goalType.markAsTouched()
        form.currentWeight.markAsTouched()
        form.goalWeight.markAsTouched()

        form.resetGoal()

        #expect(form.goalType.value == GoalTypeSegment.losegainValue)
        #expect(form.currentWeight.value == "")
        #expect(form.goalWeight.value == "")
        #expect(form.goalType.isTouched == false)
        #expect(form.currentWeight.isTouched == false)
        #expect(form.goalWeight.isTouched == false)
        #expect(form.currentWeight.isDirty == false)
        #expect(form.goalWeight.isDirty == false)
    }

    @Test("formDidChange emits for field updates")
    func formDidChangePublishes() {
        let form = SignupForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.formDidChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.firstName.value = "Test"
        form.email.value = "test@example.com"
        form.password.value = "password123"

        #expect(emissions >= 3)
    }
}

private enum SignupFormTestText {
    static let required = "This field is required"
    static let leaveBlank = "This field is required"
    static let email = "must use a valid email"
    static let emailMaxLength = "email should not exceed 100 characters"
    static let passwordMaxLength = "password should not exceed 50 characters"
    static let signupPasswordMinLength = "minimum of 6 characters needed"
    static let noWhiteSpace = "This field is required"
    static let futureDate = "future dates not accepted"
    static let passwordMatch = "both passwords must match"
    static let valueShouldNotBeEqual = "value should not be equal to starting weight"
    static let minWeightKg = "value should be greater than 0 kg"
    static let minWeightLb = "value should be greater than 0 lbs"
    static let maxWeightKg = "value should be less than 450 kg"
    static let maxWeightLb = "value should be less than 999 lbs"
    static func maxLength(_ length: Int) -> String { "maximum value should be \(length)" }
}
