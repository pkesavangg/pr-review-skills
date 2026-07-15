//
//  BabyEntryForm.swift
//  meApp
//

import Combine
import Foundation

class BabyEntryForm: ObservableForm {
    // Weight fields (only one set active at a time based on selected unit).
    // Rules ported 1:1 from the Baby app's weight-input validators:
    //  • lb/oz: pounds are a whole number 1–999 (`^\d{1,3}$`); ounces are optional,
    //    ≤ 15.9 with at most one decimal (`^\d{1,2}(\.\d)?$`) so 0 is allowed and 16+ is not.
    //  • kg / decimal-lb: 1–999.999 with up to three decimals (`^\d{1,3}(\.\d{1,3})?$`).
    // (Required-ness is enforced by EntryStore.isBabyFormValid's non-empty check, matching
    //  the Baby app's `Validators.required` on the same fields.)
    var pounds = FormControl("", validators: [.minValue(1.0), .maxLimit(999.0), .pattern("^\\d{1,3}$")])
    var ounces = FormControl("", validators: [.maxLimit(15.9), .pattern("^\\d{1,2}(\\.\\d)?$")])
    var kg = FormControl("", validators: [.minValue(1.0), .maxLimit(999.999), .pattern("^\\d{1,3}(\\.\\d{1,3})?$")])
    var lb = FormControl("", validators: [.minValue(1.0), .maxLimit(999.999), .pattern("^\\d{1,3}(\\.\\d{1,3})?$")])

    // Length fields (only one active at a time based on selected unit)
    var inches = FormControl("", validators: [.minValue(), .maxLimit(99.9)])
    var cm = FormControl("", validators: [.minValue(), .maxLimit(254.0)])

    var notes = FormControl("")

    var date = FormControl(Date(), validators: [.futureDate])
    var time = FormControl(Date())

    private let babyLang = ManualEntryStrings.self

    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            pounds.$value.map { _ in () }.eraseToAnyPublisher(),
            ounces.$value.map { _ in () }.eraseToAnyPublisher(),
            kg.$value.map { _ in () }.eraseToAnyPublisher(),
            lb.$value.map { _ in () }.eraseToAnyPublisher(),
            inches.$value.map { _ in () }.eraseToAnyPublisher(),
            cm.$value.map { _ in () }.eraseToAnyPublisher(),
            notes.$value.map { _ in () }.eraseToAnyPublisher(),
            date.$value.map { _ in () }.eraseToAnyPublisher(),
            time.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }

    // MARK: - Weight Validation Errors

    /// Validation error for the pounds field (lbs/oz mode). Reported separately from ounces
    /// so each field surfaces its own error under itself.
    var poundsError: String? {
        guard pounds.isDirty else { return nil }
        return pounds.isInvalid ? babyLang.invalidWeight : nil
    }

    /// Validation error for the ounces field (lbs/oz mode). Reported separately from pounds.
    var ouncesError: String? {
        guard ounces.isDirty else { return nil }
        return ounces.isInvalid ? babyLang.invalidOunces : nil
    }

    /// Validation error for kg field (metric mode).
    var weightErrorMetric: String? {
        guard kg.isDirty else { return nil }
        if kg.isInvalid { return babyLang.invalidWeight }
        return nil
    }

    /// Validation error for decimal lb field.
    var weightErrorLb: String? {
        guard lb.isDirty else { return nil }
        if lb.isInvalid { return babyLang.invalidWeight }
        return nil
    }

    // MARK: - Length Validation Errors

    /// Validation error for inches field.
    var lengthError: String? {
        guard inches.isDirty else { return nil }
        if inches.isInvalid { return babyLang.invalidLength }
        return nil
    }

    /// Validation error for cm field.
    var lengthErrorCm: String? {
        guard cm.isDirty else { return nil }
        if cm.isInvalid { return babyLang.invalidLength }
        return nil
    }

    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }

        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.minValue] { return FormErrorMessages.minValue }
        if control.errors[.maxValue], let maxVal = control.errors.value(for: .maxValue) as? Double {
            return FormErrorMessages.maxValue(Int(maxVal))
        }
        if control.errors[.maxLimit], let maxVal = control.errors.value(for: .maxLimit) as? Double {
            return FormErrorMessages.maxValue(Int(maxVal))
        }
        if control.errors[.futureDate] { return FormErrorMessages.futureDate }
        return nil
    }
}
