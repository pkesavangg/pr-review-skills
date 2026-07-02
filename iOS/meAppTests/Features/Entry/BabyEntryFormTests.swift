//
//  BabyEntryFormTests.swift
//  meAppTests
//

import Combine
import Foundation
@testable import meApp
import Testing

struct BabyEntryFormTests {
    // MARK: - Initial State

    @Test("initial state: fields are empty and form is pristine")
    func initialState() {
        let form = BabyEntryForm()

        #expect(form.pounds.value.isEmpty)
        #expect(form.ounces.value.isEmpty)
        #expect(form.kg.value.isEmpty)
        #expect(form.lb.value.isEmpty)
        #expect(form.inches.value.isEmpty)
        #expect(form.cm.value.isEmpty)
        #expect(form.notes.value.isEmpty)
        #expect(form.isPristine == true)
    }

    // MARK: - Weight Validation (lbs/oz)

    @Test("weightError: returns error when pounds exceeds max limit 999")
    func poundsMaxLimit() {
        let form = BabyEntryForm()

        form.pounds.value = "1000"

        #expect(form.pounds.isInvalid == true)
        #expect(form.pounds.errors[.maxLimit] == true)
        #expect(form.weightError == BabyFormTestText.invalidWeight)
    }

    @Test("weightError: returns error when ounces exceeds max limit 15.9")
    func ouncesMaxLimit() {
        let form = BabyEntryForm()

        form.ounces.value = "16"

        #expect(form.ounces.isInvalid == true)
        #expect(form.ounces.errors[.maxLimit] == true)
        #expect(form.weightError == BabyFormTestText.invalidWeight)
    }

    // MARK: - Weight Validation (metric)

    @Test("weightErrorMetric: returns error when kg is below min or above max")
    func kgValidation() {
        let form = BabyEntryForm()

        form.kg.value = "0.5"
        #expect(form.kg.isInvalid == true)
        #expect(form.weightErrorMetric == BabyFormTestText.invalidWeight)

        form.kg.value = "451"
        #expect(form.kg.isInvalid == true)
        #expect(form.weightErrorMetric == BabyFormTestText.invalidWeight)
    }

    @Test("weightErrorLb: returns error when lb is below min or above max")
    func lbValidation() {
        let form = BabyEntryForm()

        form.lb.value = "0.5"
        #expect(form.lb.isInvalid == true)
        #expect(form.weightErrorLb == BabyFormTestText.invalidWeight)

        form.lb.value = "1000"
        #expect(form.lb.isInvalid == true)
        #expect(form.weightErrorLb == BabyFormTestText.invalidWeight)
    }

    // MARK: - Length Validation

    @Test("lengthError: returns error when inches exceeds max limit 99.9")
    func inchesMaxLimit() {
        let form = BabyEntryForm()

        form.inches.value = "100"

        #expect(form.inches.isInvalid == true)
        #expect(form.inches.errors[.maxLimit] == true)
        #expect(form.lengthError == BabyFormTestText.invalidLength)
    }

    @Test("lengthErrorCm: returns error when cm exceeds max limit 254")
    func cmMaxLimit() {
        let form = BabyEntryForm()

        form.cm.value = "255"

        #expect(form.cm.isInvalid == true)
        #expect(form.cm.errors[.maxLimit] == true)
        #expect(form.lengthErrorCm == BabyFormTestText.invalidLength)
    }

    // MARK: - Date Validation

    @Test("date: returns future date error when set to tomorrow")
    func dateFutureDateError() throws {
        let form = BabyEntryForm()

        let tomorrow = try #require(Calendar.current.date(byAdding: .day, value: 1, to: Date()))
        form.date.value = tomorrow

        #expect(form.date.errors[.futureDate] == true)
        #expect(form.getError(for: form.date) == BabyFormTestText.futureDate)

        form.date.value = Date()
        #expect(form.date.errors[.futureDate] == false)
        #expect(form.getError(for: form.date) == nil)
    }

    // MARK: - Form Change Publisher

    @Test("formDidChange: emits when any field value changes")
    func formDidChangeEmits() {
        let form = BabyEntryForm()
        var emissions = 0
        var cancellables = Set<AnyCancellable>()

        form.formDidChange
            .sink { emissions += 1 }
            .store(in: &cancellables)

        form.pounds.value = "7"
        form.ounces.value = "8"
        form.inches.value = "20"

        #expect(emissions >= 3)
    }
}

// MARK: - Test String Constants

private enum BabyFormTestText {
    static let invalidWeight = "Please enter a valid weight."
    static let invalidLength = "Please enter a valid length."
    static let futureDate = "future dates not accepted"
}
