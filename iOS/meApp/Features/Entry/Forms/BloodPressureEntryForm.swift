//
//  BloodPressureEntryForm.swift
//  meApp
//

import Combine
import Foundation

class BloodPressureEntryForm: ObservableForm {
    var systolic = FormControl("", validators: [.required, .maxLimit(500.0)])
    var diastolic = FormControl("", validators: [.required, .maxLimit(500.0)])
    var pulse = FormControl("", validators: [.required, .maxLimit(500.0)])
    var notes = FormControl("")

    var date = FormControl(Date(), validators: [.futureDate])
    var time = FormControl(Date())

    private let bpLang = ManualEntryStrings.self

    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            systolic.$value.map { _ in () }.eraseToAnyPublisher(),
            diastolic.$value.map { _ in () }.eraseToAnyPublisher(),
            pulse.$value.map { _ in () }.eraseToAnyPublisher(),
            notes.$value.map { _ in () }.eraseToAnyPublisher(),
            date.$value.map { _ in () }.eraseToAnyPublisher(),
            time.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }

    // MARK: - Error Messages (blocking - prevent save)

    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }

        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.maxLimit] { return bpLang.maxLimit }
        if control.errors[.futureDate] { return FormErrorMessages.futureDate }
        return nil
    }

    // MARK: - Warning Messages (non-blocking - don't prevent save)

    func getWarning<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }
        // No warning if field has a blocking error
        if control.errors.hasError { return nil }

        if control === systolic {
            return systolicWarning
        } else if control === diastolic {
            return diastolicWarning
        } else if control === pulse {
            return pulseWarning
        }
        return nil
    }

    private var systolicWarning: String? {
        guard let sys = Double(systolic.value) else { return nil }
        let dia = Double(diastolic.value)

        // Reversed values check (only when systolic is in typical range)
        if sys >= 60, sys <= 250, let dia, dia > sys {
            return bpLang.systolicReversed
        }
        // Typical range warning
        if sys < 60 || sys > 250 {
            return bpLang.typicalRange(60, 250)
        }
        return nil
    }

    private var diastolicWarning: String? {
        guard let dia = Double(diastolic.value) else { return nil }
        let sys = Double(systolic.value)

        // Reversed values check (only when diastolic is in typical range)
        if dia >= 30, dia <= 150 {
            if let sys, sys < dia {
                return bpLang.diastolicReversed
            }
            if systolic.value.isEmpty {
                return bpLang.diastolicReversed
            }
        }
        // Typical range warning
        if dia < 30 || dia > 150 {
            return bpLang.typicalRange(30, 150)
        }
        return nil
    }

    private var pulseWarning: String? {
        guard let pul = Double(pulse.value) else { return nil }
        if pul < 20 || pul > 200 {
            return bpLang.typicalRange(20, 200)
        }
        return nil
    }
}
