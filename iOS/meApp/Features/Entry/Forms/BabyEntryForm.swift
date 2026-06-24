//
//  BabyEntryForm.swift
//  meApp
//

import Combine
import Foundation

class BabyEntryForm: ObservableForm {
    // Weight fields (only one set active at a time based on selected unit)
    var pounds = FormControl("", validators: [.minValue(), .maxLimit(999.0)])
    var ounces = FormControl("", validators: [.minValue(), .maxLimit(15.9)])
    var kg = FormControl("", validators: [.minValue(1.0), .maxLimit(450.0)])
    var lb = FormControl("", validators: [.minValue(1.0), .maxLimit(999.0)])

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

    /// Combined validation error for pounds and ounces fields (lbs/oz mode).
    var weightError: String? {
        guard pounds.isDirty || ounces.isDirty else { return nil }
        if pounds.isInvalid || ounces.isInvalid { return babyLang.invalidWeight }
        return nil
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
