//
//  DeviceNameForm.swift
//  meApp
//
//  Created by AI on 04/08/25.
//

import Combine
import Foundation

/// Reactive form for editing a scale's display name.
/// Provides basic `.required` and `.noWhiteSpace` validation and helper utilities
/// similar to `UserNameForm` used in the Bt-WiFi setup flow.
class DeviceNameForm: ObservableForm {
    // MARK: - Controls
    /// Text field for the scale name
    var scaleName = FormControl("", validators: [.required, .noWhiteSpace, .maxLength(100)])
    
    // MARK: - Binding helpers
    /// Update the scale name value and trigger validation
    func setDeviceName(_ name: String) {
        if name != scaleName.value {
            scaleName.value = name
        }
        scaleName.markAsDirty()
        validate()
    }
    
    /// Convenience accessor for current value
    var scaleNameValue: String { scaleName.value }
    
    /// Reset the form back to pristine state
    func reset() {
        scaleName.value = ""
        scaleName.markAsPristine()
        validate()
    }
    
    // MARK: - Error helpers (used by UI)
    func getError(for field: FocusField) -> String? {
        switch field {
        case .scaleName:
            return scaleNameError
        default:
            return nil
        }
    }
    
    private var scaleNameError: String? {
        guard scaleName.isDirty else { return nil }
        if scaleName.errors[.required] { return FormErrorMessages.required }
        if scaleName.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if scaleName.errors[.maxLength], let max = scaleName.errors.value(for: .maxLength) as? Int {
            return FormErrorMessages.maxLength(max)
        }
        return nil
    }
}
