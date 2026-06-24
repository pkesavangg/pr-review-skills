//
//  AddDeviceForm.swift
//  meApp
//
//  Created by Lakshmi Priya on 24/06/25.
//

import Combine
import Foundation

/// Reactive form for adding a scale via SKU entry.
/// Uses the shared `ObservableForm` infrastructure and a custom `skuMatch` validator.
class AddDeviceForm: ObservableForm {
    // MARK: Controls
    /// Four-digit model/SKU number.
    var modelNumber = FormControl("", validators: [
        .required,
        .minLength(4),
        .maxLength(4),
        .skuMatch
    ])

    // MARK: Binding Helpers – mirrors the old imperative API so calling code needs minimal change.
    /// Sets a new model number after filtering non-numeric characters and truncating to 4 digits.
    func setModelNumber(_ newValue: String) {
        let filtered = newValue.filter { $0.isNumber }
        let truncated = String(filtered.prefix(4))
        if truncated != modelNumber.value {
            modelNumber.value = truncated
        }
        modelNumber.markAsDirty()
        validate()
    }

    /// Convenience for outside views that still expect a string value.
    var modelNumberValue: String { modelNumber.value }

    /// Reset the form to pristine state.
    func reset() {
        modelNumber.value = ""
        modelNumber.markAsPristine()
        validate()
    }

    // MARK: Error helpers – used by UI.
    func getError(for field: FocusField) -> String? {
        switch field {
        case .modelNumber:
            return modelNumberError
        default:
            return nil
        }
    }

    private var modelNumberError: String? {
        guard modelNumber.isDirty else { return nil }
        guard !modelNumber.value.isEmpty else { return nil }
        if modelNumber.errors[.minLength] ||
            modelNumber.errors[.maxLength] ||
            modelNumber.errors[.skuMatch] {
            return FormErrorMessages.modelNumberInvalid
        }
        return nil
    }
}
