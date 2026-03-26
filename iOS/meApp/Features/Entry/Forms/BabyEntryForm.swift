//
//  BabyEntryForm.swift
//  meApp
//

import Combine
import Foundation

class BabyEntryForm: ObservableForm {
    var pounds = FormControl("", validators: [.required, .minValue(), .maxLimit(999.0)])
    var ounces = FormControl("", validators: [.minValue(), .maxLimit(15.9)])
    var inches = FormControl("", validators: [.minValue(), .maxLimit(99.9)])
    var notes = FormControl("")

    var date = FormControl(Date(), validators: [.futureDate])
    var time = FormControl(Date())

    private let babyLang = ManualEntryStrings.self

    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            pounds.$value.map { _ in () }.eraseToAnyPublisher(),
            ounces.$value.map { _ in () }.eraseToAnyPublisher(),
            inches.$value.map { _ in () }.eraseToAnyPublisher(),
            notes.$value.map { _ in () }.eraseToAnyPublisher(),
            date.$value.map { _ in () }.eraseToAnyPublisher(),
            time.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }

    /// Combined validation error for pounds and ounces fields.
    var weightError: String? {
        guard pounds.isDirty || ounces.isDirty else { return nil }

        // Required: pounds was touched then cleared
        if pounds.isDirty && pounds.value.isEmpty {
            return babyLang.required
        }

        // Value validity (e.g. ounces > 15.9)
        if pounds.isInvalid || ounces.isInvalid {
            return babyLang.invalidWeight
        }
        return nil
    }

    /// Validation error for inches field.
    var lengthError: String? {
        guard inches.isDirty else { return nil }

        // Required: inches was touched then cleared
        if inches.value.isEmpty {
            return babyLang.required
        }

        // Value validity (e.g. inches > 99.9)
        if inches.isInvalid {
            return babyLang.invalidLength
        }
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
