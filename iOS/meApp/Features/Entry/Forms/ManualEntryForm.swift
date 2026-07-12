//  ManualEntryForm.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//

import Combine
import Foundation

/// Form that manages manual weight entry.
/// Only weight field is included for now; additional fields will be added later.
class ManualEntryForm: ObservableForm {
    /// Weight input (String) measured in lbs by default.
    /// Validation: required, minValue (>0), maxValue (<= 999 lbs)
    var weight = FormControl("", validators: [.required, .minValue(), .maxValue(999.0)])

    /// Date portion of the entry (default = current day). Future dates not allowed.
    var date = FormControl(Date(), validators: [.futureDate])

    /// Time portion of the entry (default = now). Validation is handled by UI restriction.
    var time = FormControl(Date())

    /// Optional free-text note (max 280 chars, enforced by the notes input field).
    var notes = FormControl("")

    /// Additional fields for body composition metrics. 
    var bmi = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var bodyFat = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var muscleMass = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var bodyWater = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var heartRate = FormControl("", validators: [.minValue(), .maxValue(200.0)])
    var boneMass = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var visceralFat = FormControl("", validators: [.minValue(), .maxValue(60.0)])
    var subcutaneousFat = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var protein = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var skeletalMuscles = FormControl("", validators: [.minValue(), .maxValue(99.0)])
    var bmr = FormControl("", validators: [.minValue(), .maxValue(10000.0)])
    var metabolicAge = FormControl("", validators: [.minValue(), .maxValue(150.0)])
    
    /// Publisher that emits whenever any field in the form changes.
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            weight.$value.map { _ in () }.eraseToAnyPublisher(),
            date.$value.map { _ in () }.eraseToAnyPublisher(),
            time.$value.map { _ in () }.eraseToAnyPublisher(),
            notes.$value.map { _ in () }.eraseToAnyPublisher(),
            bmi.$value.map { _ in () }.eraseToAnyPublisher(),
            bodyFat.$value.map { _ in () }.eraseToAnyPublisher(),
            muscleMass.$value.map { _ in () }.eraseToAnyPublisher(),
            bodyWater.$value.map { _ in () }.eraseToAnyPublisher(),
            heartRate.$value.map { _ in () }.eraseToAnyPublisher(),
            boneMass.$value.map { _ in () }.eraseToAnyPublisher(),
            visceralFat.$value.map { _ in () }.eraseToAnyPublisher(),
            subcutaneousFat.$value.map { _ in () }.eraseToAnyPublisher(),
            protein.$value.map { _ in () }.eraseToAnyPublisher(),
            skeletalMuscles.$value.map { _ in () }.eraseToAnyPublisher(),
            bmr.$value.map { _ in () }.eraseToAnyPublisher(),
            metabolicAge.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }

    /// Returns a localized error message for the given control, if any.
    /// - Parameters:
    ///   - control: The form control to evaluate.
    ///   - weightUnit: Current weight unit setting of the user; drives metric vs imperial error strings.
    func getError<T>(for control: FormControl<T>, weightUnit: WeightUnit) -> String? {
        guard control.isDirty else { return nil }

        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.minValue] {
            if control === weight {
                return weightUnit == .kg ? FormErrorMessages.minWeightKg : FormErrorMessages.minWeightLb
            } else {
                return FormErrorMessages.minValue
            }
        }
        if control.errors[.maxValue], let maxVal = control.errors.value(for: .maxValue) as? Double {
            if control === weight {
                return weightUnit == .kg ? FormErrorMessages.maxWeightKg : FormErrorMessages.maxWeightLb
            } else {
                return FormErrorMessages.maxValue(Int(maxVal))
            }
        }
        if control.errors[.futureDate] {
            return FormErrorMessages.futureDate
        }
        return nil
    }
} 
