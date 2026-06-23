import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct GoalFormTests {

    private func makeForm() -> GoalForm { GoalForm() }

    // MARK: - Initial state

    @Test("goalType starts as lose mode")
    func initialGoalType() {
        let form = makeForm()
        #expect(form.goalType.value == GoalType.lose.rawValue)
    }

    @Test("currentWeight and goalWeight start empty")
    func initialWeights() {
        let form = makeForm()
        #expect(form.currentWeight.value == "")
        #expect(form.goalWeight.value == "")
    }

    @Test("form is not dirty initially")
    func initialNotDirty() {
        let form = makeForm()
        #expect(!form.isDirty)
    }

    // MARK: - goalType control

    @Test("goalType valid when set to maintain")
    func goalTypeValidMaintain() {
        let form = makeForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.goalType.validate()
        #expect(form.goalType.isValid)
    }

    @Test("goalType valid when set to gain")
    func goalTypeValidGain() {
        let form = makeForm()
        form.goalType.value = GoalType.gain.rawValue
        form.goalType.validate()
        #expect(form.goalType.isValid)
    }

    // MARK: - required validator on weight fields

    @Test("currentWeight invalid when empty and dirty")
    func currentWeightInvalidEmpty() {
        let form = makeForm()
        form.currentWeight.markAsDirty()
        form.currentWeight.validate()
        #expect(!form.currentWeight.isValid)
        #expect(form.currentWeight.errors[.required])
    }

    @Test("goalWeight invalid when empty and dirty")
    func goalWeightInvalidEmpty() {
        let form = makeForm()
        form.goalWeight.markAsDirty()
        form.goalWeight.validate()
        #expect(!form.goalWeight.isValid)
        #expect(form.goalWeight.errors[.required])
    }

    @Test("currentWeight valid with positive numeric string")
    func currentWeightValid() {
        let form = makeForm()
        form.currentWeight.value = "70.5"
        form.currentWeight.validate()
        #expect(form.currentWeight.isValid)
    }

    @Test("goalWeight valid with positive numeric string")
    func goalWeightValid() {
        let form = makeForm()
        form.goalWeight.value = "65.0"
        form.goalWeight.validate()
        #expect(form.goalWeight.isValid)
    }

    // MARK: - hasEqualWeights

    @Test("hasEqualWeights false when both are zero")
    func hasEqualWeightsBothZero() {
        let form = makeForm()
        #expect(!form.hasEqualWeights)
    }

    @Test("hasEqualWeights false when weights differ")
    func hasEqualWeightsDiffer() {
        let form = makeForm()
        form.currentWeight.value = "70"
        form.goalWeight.value = "65"
        #expect(!form.hasEqualWeights)
    }

    @Test("hasEqualWeights true when both weights are equal and non-zero")
    func hasEqualWeightsEqual() {
        let form = makeForm()
        form.currentWeight.value = "70"
        form.goalWeight.value = "70"
        #expect(form.hasEqualWeights)
    }

    // MARK: - cross-field .weightEqual form error

    @Test("validateForm sets weightEqual error in lose mode with equal weights")
    func weightEqualErrorInLoseMode() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.currentWeight.value = "70"
        form.goalWeight.value = "70"
        form.validate()
        #expect(form.formErrors[.weightEqual])
    }

    @Test("validateForm sets weightEqual error in gain mode with equal weights")
    func weightEqualErrorInGainMode() {
        let form = makeForm()
        form.goalType.value = GoalType.gain.rawValue
        form.currentWeight.value = "65"
        form.goalWeight.value = "65"
        form.validate()
        #expect(form.formErrors[.weightEqual])
    }

    @Test("validateForm no weightEqual error when weights differ")
    func noWeightEqualErrorWhenDiffer() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.currentWeight.value = "70"
        form.goalWeight.value = "65"
        form.validate()
        #expect(!form.formErrors[.weightEqual])
    }

    @Test("validateForm no weightEqual error in maintain mode with equal weights")
    func noWeightEqualInMaintainMode() {
        let form = makeForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.currentWeight.value = "70"
        form.goalWeight.value = "70"
        form.validate()
        #expect(!form.formErrors[.weightEqual])
    }

    // MARK: - isValidForSave

    @Test("isValidForSave false when not dirty")
    func isValidForSaveFalseNotDirty() {
        let form = makeForm()
        #expect(!form.isValidForSave())
    }

    @Test("isValidForSave false in lose mode with equal weights")
    func isValidForSaveFalseEqualWeights() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.goalType.markAsDirty()
        form.currentWeight.value = "70"
        form.currentWeight.markAsDirty()
        form.goalWeight.value = "70"
        form.goalWeight.markAsDirty()
        form.validate()
        #expect(!form.isValidForSave())
    }

    @Test("isValidForSave true in lose mode with different valid weights")
    func isValidForSaveTrueLoseMode() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.goalType.markAsDirty()
        form.currentWeight.value = "70"
        form.currentWeight.markAsDirty()
        form.goalWeight.value = "65"
        form.goalWeight.markAsDirty()
        form.validate()
        #expect(form.isValidForSave())
    }

    @Test("isValidForSave true in maintain mode with valid goalWeight")
    func isValidForSaveTrueMaintainMode() {
        let form = makeForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.goalType.markAsDirty()
        form.goalWeight.value = "70"
        form.goalWeight.markAsDirty()
        form.validate()
        #expect(form.isValidForSave())
    }

    // MARK: - getError

    @Test("getError for currentWeight returns nil when not dirty")
    func getErrorCurrentWeightNilNotDirty() {
        let form = makeForm()
        #expect(form.getError(for: form.currentWeight, isMetric: true) == nil)
    }

    @Test("getError for currentWeight returns nil in maintain mode")
    func getErrorCurrentWeightNilInMaintain() {
        let form = makeForm()
        form.goalType.value = GoalType.maintain.rawValue
        form.currentWeight.value = ""
        form.currentWeight.markAsDirty()
        form.validate()
        #expect(form.getError(for: form.currentWeight, isMetric: true) == nil)
    }

    @Test("getError for currentWeight returns required in lose mode when empty")
    func getErrorCurrentWeightRequired() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.currentWeight.markAsDirty()
        form.currentWeight.validate()
        let error = form.getError(for: form.currentWeight, isMetric: true)
        #expect(error == FormErrorMessages.required)
    }

    @Test("getError for goalWeight returns weightEqual message when equal and dirty")
    func getErrorGoalWeightEqual() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.goalType.markAsDirty()
        form.currentWeight.value = "70"
        form.currentWeight.markAsDirty()
        form.goalWeight.value = "70"
        form.goalWeight.markAsDirty()
        form.validate()
        let error = form.getError(for: form.goalWeight, isMetric: true)
        #expect(error == FormErrorMessages.valueShouldNotBeEqual)
    }

    @Test("getError for currentWeight returns minWeightKg when isMetric true and value zero")
    func getErrorMinWeightKg() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.currentWeight.value = "0"
        form.currentWeight.markAsDirty()
        form.currentWeight.validate()
        let error = form.getError(for: form.currentWeight, isMetric: true)
        #expect(error == FormErrorMessages.minWeightKg)
    }

    @Test("getError for currentWeight returns minWeightLb when isMetric false and value zero")
    func getErrorMinWeightLb() {
        let form = makeForm()
        form.goalType.value = GoalType.lose.rawValue
        form.currentWeight.value = "0"
        form.currentWeight.markAsDirty()
        form.currentWeight.validate()
        let error = form.getError(for: form.currentWeight, isMetric: false)
        #expect(error == FormErrorMessages.minWeightLb)
    }

    // MARK: - formDidChange

    @Test("formDidChange emits when goalType changes")
    func formDidChangeGoalType() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.goalType.value = GoalType.maintain.rawValue
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }
}
