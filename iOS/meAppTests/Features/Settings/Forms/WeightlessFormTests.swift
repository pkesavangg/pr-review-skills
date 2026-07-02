import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct WeightlessFormTests {

    private func makeForm() -> WeightlessForm { WeightlessForm() }

    // MARK: - Initial state

    @Test("isOn defaults to false")
    func initialIsOn() {
        let form = makeForm()
        #expect(form.isOn.value == false)
    }

    @Test("weight starts empty")
    func initialWeight() {
        let form = makeForm()
        #expect(form.weight.value.isEmpty)
    }

    @Test("weight not dirty initially")
    func initialNotDirty() {
        let form = makeForm()
        #expect(!form.weight.isDirty)
    }

    // MARK: - isOn toggle

    @Test("isOn can be set to true")
    func isOnSetTrue() {
        let form = makeForm()
        form.isOn.value = true
        #expect(form.isOn.value == true)
    }

    @Test("isOn can be toggled back to false")
    func isOnToggleBackFalse() {
        let form = makeForm()
        form.isOn.value = true
        form.isOn.value = false
        #expect(form.isOn.value == false)
    }

    // MARK: - weight required validator

    @Test("weight invalid when empty and dirty")
    func weightInvalidEmpty() {
        let form = makeForm()
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(!form.weight.isValid)
        #expect(form.weight.errors[.required])
    }

    // MARK: - weight minValue validator

    @Test("weight invalid when value is zero (minValue)")
    func weightInvalidZero() {
        let form = makeForm()
        form.weight.value = "0"
        form.weight.validate()
        #expect(!form.weight.isValid)
        #expect(form.weight.errors[.minValue])
    }

    @Test("weight valid with positive value")
    func weightValidPositive() {
        let form = makeForm()
        form.weight.value = "70"
        form.weight.validate()
        #expect(form.weight.isValid)
    }

    // MARK: - getWeightError — gate on isDirty

    @Test("getWeightError returns nil when not dirty")
    func getWeightErrorNilNotDirty() {
        let form = makeForm()
        form.isOn.value = true
        let error = form.getWeightError(for: form.weight, unit: .kg)
        #expect(error == nil)
    }

    // MARK: - getWeightError — isOn gating

    @Test("getWeightError returns nil when isOn is false even if weight invalid")
    func getWeightErrorNilWhenIsOff() {
        let form = makeForm()
        form.isOn.value = false
        form.weight.value = ""
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getWeightError(for: form.weight, unit: .kg)
        #expect(error == nil)
    }

    @Test("getWeightError returns required when isOn is true and weight empty")
    func getWeightErrorRequiredWhenIsOn() {
        let form = makeForm()
        form.isOn.value = true
        form.weight.value = ""
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getWeightError(for: form.weight, unit: .kg)
        #expect(error == FormErrorMessages.required)
    }

    // MARK: - getWeightError — unit-specific messages

    @Test("getWeightError returns minWeightKg when unit is kg and value is zero")
    func getWeightErrorMinKg() {
        let form = makeForm()
        form.isOn.value = true
        form.weight.value = "0"
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getWeightError(for: form.weight, unit: .kg)
        #expect(error == FormErrorMessages.minWeightKg)
    }

    @Test("getWeightError returns minWeightLb when unit is lb and value is zero")
    func getWeightErrorMinLb() {
        let form = makeForm()
        form.isOn.value = true
        form.weight.value = "0"
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getWeightError(for: form.weight, unit: .lb)
        #expect(error == FormErrorMessages.minWeightLb)
    }

    @Test("getWeightError returns nil when isOn true and weight valid")
    func getWeightErrorNilWhenValid() {
        let form = makeForm()
        form.isOn.value = true
        form.weight.value = "70"
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getWeightError(for: form.weight, unit: .kg)
        #expect(error == nil)
    }

    // MARK: - formDidChange

    @Test("formDidChange emits when isOn changes")
    func formDidChangeIsOn() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.isOn.value = true
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }

    @Test("formDidChange emits when weight changes")
    func formDidChangeWeight() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.weight.value = "65"
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }

    // MARK: - validateForm

    @Test("validateForm clears form-level errors")
    func validateFormClearsErrors() {
        let form = makeForm()
        form.validate()
        #expect(!form.formErrors[.weightEqual])
    }
}
