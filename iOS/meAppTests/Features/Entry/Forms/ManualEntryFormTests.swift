//
//  ManualEntryFormTests.swift
//  meAppTests
//

import Testing
import Foundation
@testable import meApp

@Suite("ManualEntryForm", .serialized)
@MainActor
struct ManualEntryFormTests {

    // MARK: - Helpers

    private func makeSUT() -> ManualEntryForm {
        ManualEntryForm()
    }

    // MARK: - Initial State

    @Test("weight is initially empty and valid (not dirty)")
    func weightInitialState() {
        let form = makeSUT()
        #expect(form.weight.value == "")
        #expect(!form.weight.isDirty)
    }

    @Test("date defaults to today and is not dirty")
    func dateInitialState() {
        let form = makeSUT()
        let today = Calendar.current.startOfDay(for: Date())
        let formDay = Calendar.current.startOfDay(for: form.date.value)
        #expect(formDay == today)
        #expect(!form.date.isDirty)
    }

    @Test("getError returns nil for untouched weight")
    func getErrorNilWhenNotDirty() {
        let form = makeSUT()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == nil)
    }

    @Test("getError returns nil for untouched date")
    func getErrorNilDateWhenNotDirty() {
        let form = makeSUT()
        #expect(form.getError(for: form.date, weightUnit: .lb) == nil)
    }

    // MARK: - Weight Required

    @Test("weight required error when dirty and empty")
    func weightRequired() {
        let form = makeSUT()
        form.weight.value = ""
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == FormErrorMessages.required)
    }

    @Test("weight no error when valid lb value")
    func weightValidLb() {
        let form = makeSUT()
        form.weight.value = "150"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == nil)
    }

    @Test("weight no error when valid kg value")
    func weightValidKg() {
        let form = makeSUT()
        form.weight.value = "70"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .kg) == nil)
    }

    // MARK: - Weight minValue

    @Test("weight minValue error in lb returns lb message")
    func weightMinValueLb() {
        let form = makeSUT()
        form.weight.value = "0"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == FormErrorMessages.minWeightLb)
    }

    @Test("weight minValue error in kg returns kg message")
    func weightMinValueKg() {
        let form = makeSUT()
        form.weight.value = "0"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .kg) == FormErrorMessages.minWeightKg)
    }

    @Test("weight negative value triggers minValue error in lb")
    func weightNegativeLb() {
        let form = makeSUT()
        form.weight.value = "-5"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == FormErrorMessages.minWeightLb)
    }

    // MARK: - Weight maxValue

    @Test("weight maxValue error in lb for value over 999")
    func weightMaxValueLb() {
        let form = makeSUT()
        form.weight.value = "1000"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == FormErrorMessages.maxWeightLb)
    }

    @Test("weight exactly 999 lb is valid")
    func weightExactly999Lb() {
        let form = makeSUT()
        form.weight.value = "999"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.getError(for: form.weight, weightUnit: .lb) == nil)
    }

    @Test("weight isValid false with empty weight")
    func formIsInvalidWithEmptyWeight() {
        let form = makeSUT()
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(!form.isValid)
    }

    @Test("weight isValid true with valid weight")
    func formIsValidWithValidWeight() {
        let form = makeSUT()
        form.weight.value = "150"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.isValid)
    }

    // MARK: - Date futureDate validator

    @Test("date future error for tomorrow")
    func dateFutureDateError() {
        let form = makeSUT()
        form.date.value = Calendar.current.date(byAdding: .day, value: 1, to: Date())!
        form.date.markAsDirty()
        form.date.validate()
        #expect(form.getError(for: form.date, weightUnit: .lb) == FormErrorMessages.futureDate)
    }

    @Test("date today has no error")
    func dateTodayNoError() {
        let form = makeSUT()
        form.date.value = Date()
        form.date.markAsDirty()
        form.date.validate()
        #expect(form.getError(for: form.date, weightUnit: .lb) == nil)
    }

    @Test("date yesterday has no error")
    func dateYesterdayNoError() {
        let form = makeSUT()
        form.date.value = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        form.date.markAsDirty()
        form.date.validate()
        #expect(form.getError(for: form.date, weightUnit: .lb) == nil)
    }

    // MARK: - Body metric maxValue validators

    @Test("bmi maxValue 99 triggers error for 100")
    func bmiMaxValue() {
        let form = makeSUT()
        form.bmi.value = "100"
        form.bmi.markAsDirty()
        form.bmi.validate()
        #expect(form.getError(for: form.bmi, weightUnit: .lb) != nil)
    }

    @Test("bodyFat maxValue 99 triggers error for 100")
    func bodyFatMaxValue() {
        let form = makeSUT()
        form.bodyFat.value = "100"
        form.bodyFat.markAsDirty()
        form.bodyFat.validate()
        #expect(form.getError(for: form.bodyFat, weightUnit: .lb) != nil)
    }

    @Test("heartRate maxValue 200 triggers error for 201")
    func heartRateMaxValue() {
        let form = makeSUT()
        form.heartRate.value = "201"
        form.heartRate.markAsDirty()
        form.heartRate.validate()
        #expect(form.getError(for: form.heartRate, weightUnit: .lb) != nil)
    }

    @Test("heartRate exactly 200 is valid")
    func heartRateExactly200() {
        let form = makeSUT()
        form.heartRate.value = "200"
        form.heartRate.markAsDirty()
        form.heartRate.validate()
        #expect(form.getError(for: form.heartRate, weightUnit: .lb) == nil)
    }

    @Test("visceralFat maxValue 60 triggers error for 61")
    func visceralFatMaxValue() {
        let form = makeSUT()
        form.visceralFat.value = "61"
        form.visceralFat.markAsDirty()
        form.visceralFat.validate()
        #expect(form.getError(for: form.visceralFat, weightUnit: .lb) != nil)
    }

    @Test("bmr maxValue 10000 triggers error for 10001")
    func bmrMaxValue() {
        let form = makeSUT()
        form.bmr.value = "10001"
        form.bmr.markAsDirty()
        form.bmr.validate()
        #expect(form.getError(for: form.bmr, weightUnit: .lb) != nil)
    }

    @Test("metabolicAge maxValue 150 triggers error for 151")
    func metabolicAgeMaxValue() {
        let form = makeSUT()
        form.metabolicAge.value = "151"
        form.metabolicAge.markAsDirty()
        form.metabolicAge.validate()
        #expect(form.getError(for: form.metabolicAge, weightUnit: .lb) != nil)
    }

    @Test("muscleMass maxValue 99 triggers error for 100")
    func muscleMassMaxValue() {
        let form = makeSUT()
        form.muscleMass.value = "100"
        form.muscleMass.markAsDirty()
        form.muscleMass.validate()
        #expect(form.getError(for: form.muscleMass, weightUnit: .lb) != nil)
    }

    @Test("bodyWater maxValue 99 triggers error for 100")
    func bodyWaterMaxValue() {
        let form = makeSUT()
        form.bodyWater.value = "100"
        form.bodyWater.markAsDirty()
        form.bodyWater.validate()
        #expect(form.getError(for: form.bodyWater, weightUnit: .lb) != nil)
    }

    // MARK: - Optional fields are valid when empty

    @Test("bmi empty field is valid (optional)")
    func bmiEmptyIsValid() {
        let form = makeSUT()
        form.bmi.value = ""
        form.bmi.markAsDirty()
        form.bmi.validate()
        #expect(form.getError(for: form.bmi, weightUnit: .lb) == nil)
    }

    // MARK: - Form-level isValid

    @Test("form isValid requires only weight to be valid")
    func formIsValidWeightOnly() {
        let form = makeSUT()
        form.weight.value = "80"
        form.weight.markAsDirty()
        form.weight.validate()
        #expect(form.isValid)
    }

    @Test("form isInvalid when weight valid but bodyFat exceeds max")
    func formInvalidWithBodyFatOver99() {
        let form = makeSUT()
        form.weight.value = "80"
        form.weight.markAsDirty()
        form.weight.validate()
        form.bodyFat.value = "100"
        form.bodyFat.markAsDirty()
        form.bodyFat.validate()
        #expect(!form.isValid)
    }
}
