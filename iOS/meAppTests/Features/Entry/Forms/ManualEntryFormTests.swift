import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct ManualEntryFormTests {

    private func makeForm() -> ManualEntryForm { ManualEntryForm() }

    // MARK: - Initial state

    @Test("weight starts empty")
    func initialWeight() {
        let form = makeForm()
        #expect(form.weight.value.isEmpty)
    }

    @Test("date defaults to today")
    func initialDateIsToday() {
        let form = makeForm()
        let cal = Calendar.current
        #expect(cal.isDateInToday(form.date.value))
    }

    @Test("body metrics start empty")
    func initialBodyMetrics() {
        let form = makeForm()
        #expect(form.bmi.value.isEmpty)
        #expect(form.bodyFat.value.isEmpty)
        #expect(form.muscleMass.value.isEmpty)
        #expect(form.bodyWater.value.isEmpty)
        #expect(form.heartRate.value.isEmpty)
        #expect(form.boneMass.value.isEmpty)
        #expect(form.visceralFat.value.isEmpty)
        #expect(form.subcutaneousFat.value.isEmpty)
        #expect(form.protein.value.isEmpty)
        #expect(form.skeletalMuscles.value.isEmpty)
        #expect(form.bmr.value.isEmpty)
        #expect(form.metabolicAge.value.isEmpty)
    }

    // MARK: - weight validators

    @Test("weight invalid when empty and dirty (required)")
    func weightRequiredError() {
        let form = makeForm()
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getError(for: form.weight, weightUnit: .lb)
        #expect(error == FormErrorMessages.required)
    }

    @Test("weight invalid when zero (minValue)")
    func weightMinValueLb() {
        let form = makeForm()
        form.weight.value = "0"
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getError(for: form.weight, weightUnit: .lb)
        #expect(error == FormErrorMessages.minWeightLb)
    }

    @Test("weight minValue error returns kg message when unit is kg")
    func weightMinValueKg() {
        let form = makeForm()
        form.weight.value = "0"
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getError(for: form.weight, weightUnit: .kg)
        #expect(error == FormErrorMessages.minWeightKg)
    }

    @Test("weight invalid when exceeds 999 lbs (maxValue)")
    func weightMaxValueLb() {
        let form = makeForm()
        form.weight.value = "1000"
        form.weight.markAsDirty()
        form.weight.validate()
        let error = form.getError(for: form.weight, weightUnit: .lb)
        #expect(error == FormErrorMessages.maxWeightLb)
    }

    // MOB-1392: the cap is exclusive, so a value equal to the max must surface the message.
    @Test("weight invalid when exactly at 999 lbs boundary (exclusive maxValue)")
    func weightMaxValueLbBoundary() {
        let form = makeForm()
        form.weight.value = "999"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.weight.errors[.maxValue] == true)
        #expect(form.getError(for: form.weight, weightUnit: .lb) == FormErrorMessages.maxWeightLb)
    }

    @Test("weight valid just below 999 lbs boundary")
    func weightJustBelowLbBoundary() {
        let form = makeForm()
        form.weight.value = "998.9"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == nil)
    }

    @Test("weight valid with normal lb value")
    func weightValidLb() {
        let form = makeForm()
        form.weight.value = "150"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == nil)
    }

    @Test("weight valid with normal kg value")
    func weightValidKg() {
        let form = makeForm()
        form.weight.value = "70"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .kg) == nil)
    }

    // MARK: - date validators

    @Test("date invalid with future date")
    func dateFutureDateError() {
        let form = makeForm()
        let future = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
        form.date.value = future
        form.date.markAsDirty()
        form.date.validate()
        let error = form.getError(for: form.date, weightUnit: .lb)
        #expect(error == FormErrorMessages.futureDate)
    }

    @Test("date valid with today")
    func dateTodayValid() {
        let form = makeForm()
        form.date.markAsDirty()
        form.date.validate()
        #expect(form.getError(for: form.date, weightUnit: .lb) == nil)
    }

    @Test("date valid with past date")
    func datePastDateValid() {
        let form = makeForm()
        let past = Calendar.current.date(byAdding: .day, value: -30, to: Date()) ?? Date()
        form.date.value = past
        form.date.markAsDirty()
        form.date.validate()
        #expect(form.getError(for: form.date, weightUnit: .lb) == nil)
    }

    // MARK: - body metric validators (bmi, bodyFat, etc.)

    @Test("bmi invalid when zero (minValue)")
    func bmiMinValue() {
        let form = makeForm()
        form.bmi.value = "0"
        form.bmi.markAsDirty()
        form.bmi.validate()
        let error = form.getError(for: form.bmi, weightUnit: .lb)
        #expect(error == FormErrorMessages.minValue)
    }

    @Test("bmi invalid when exceeds 99 (maxValue)")
    func bmiMaxValue() {
        let form = makeForm()
        form.bmi.value = "100"
        form.bmi.markAsDirty()
        form.bmi.validate()
        let error = form.getError(for: form.bmi, weightUnit: .lb)
        #expect(error == FormErrorMessages.maxValue(99))
    }

    @Test("bmi valid with normal value")
    func bmiValid() {
        let form = makeForm()
        form.bmi.value = "22.5"
        form.bmi.markAsDirty()
        form.bmi.validate()
        #expect(form.getError(for: form.bmi, weightUnit: .lb) == nil)
    }

    @Test("bmi valid when empty (not required)")
    func bmiValidEmpty() {
        let form = makeForm()
        form.bmi.markAsDirty()
        form.bmi.validate()
        #expect(form.getError(for: form.bmi, weightUnit: .lb) == nil)
    }

    @Test("bodyFat invalid when exceeds 99")
    func bodyFatMaxValue() {
        let form = makeForm()
        form.bodyFat.value = "100"
        form.bodyFat.markAsDirty()
        form.bodyFat.validate()
        let error = form.getError(for: form.bodyFat, weightUnit: .lb)
        #expect(error == FormErrorMessages.maxValue(99))
    }

    @Test("heartRate invalid when exceeds 200")
    func heartRateMaxValue() {
        let form = makeForm()
        form.heartRate.value = "201"
        form.heartRate.markAsDirty()
        form.heartRate.validate()
        let error = form.getError(for: form.heartRate, weightUnit: .lb)
        #expect(error == FormErrorMessages.maxValue(200))
    }

    @Test("bmr invalid when exceeds 10000")
    func bmrMaxValue() {
        let form = makeForm()
        form.bmr.value = "10001"
        form.bmr.markAsDirty()
        form.bmr.validate()
        let error = form.getError(for: form.bmr, weightUnit: .lb)
        #expect(error == FormErrorMessages.maxValue(10000))
    }

    @Test("metabolicAge invalid when exceeds 150")
    func metabolicAgeMaxValue() {
        let form = makeForm()
        form.metabolicAge.value = "151"
        form.metabolicAge.markAsDirty()
        form.metabolicAge.validate()
        let error = form.getError(for: form.metabolicAge, weightUnit: .lb)
        #expect(error == FormErrorMessages.maxValue(150))
    }

    // MARK: - getError gating on isDirty

    @Test("getError returns nil when control is pristine")
    func getErrorNilWhenPristine() {
        let form = makeForm()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == nil)
        #expect(form.getError(for: form.bmi, weightUnit: .lb) == nil)
    }

    // MARK: - form-level isValid

    @Test("form valid only when weight is valid and date is not future")
    func formValidWeightAndDate() {
        let form = makeForm()
        form.weight.value = "150"
        form.weight.validate()
        form.date.validate()
        #expect(form.isValid)
    }

    @Test("form invalid when weight is empty")
    func formInvalidEmptyWeight() {
        let form = makeForm()
        form.weight.validate()
        #expect(!form.isValid)
    }

    // MARK: - formDidChange publisher

    @Test("formDidChange emits when weight changes")
    func formDidChangeWeight() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.weight.value = "70"
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }

    @Test("formDidChange emits when bmi changes")
    func formDidChangeBmi() async {
        let form = makeForm()
        var count = 0
        let cancellable = form.formDidChange.sink { count += 1 }
        form.bmi.value = "22"
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(count >= 1)
        _ = cancellable
    }
}
