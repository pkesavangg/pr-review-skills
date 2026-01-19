import Foundation
import Combine
import SwiftUI

/// Form for handling WiFi network configuration with password validation
class NetworkForm: ObservableForm {
    var ssid = FormControl("", validators: [.required])
    var password = FormControl("", validators: [.required])
    private let apModeSSIDPrefix = "gg_SmartScaleSetup"
    @Published var networkHasNoPassword: Bool = false {
        didSet {
            updatePasswordValidation()
        }
    }
    
    private let errorMessages = FormErrorMessages.self
    
    /// Publisher that merges all value changes in the form
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            ssid.$value.map { _ in () }.eraseToAnyPublisher(),
            password.$value.map { _ in () }.eraseToAnyPublisher(),
            $networkHasNoPassword.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }
    
    /// Set the SSID value
    func setSSID(_ ssid: String) {
        self.ssid.value = ssid
    }
    
    /// Clear SSID and mark as pristine (used when permissions are skipped to avoid validation errors)
    func clearSSIDAndMarkPristine() {
        self.ssid.value = ""
        self.ssid.markAsPristine()
    }
    
    func isValidApModeSSID() -> Bool {
        return !ssid.value.isEmpty && (ssid.value.contains(apModeSSIDPrefix))
    }
    
    /// Set the password value
    func setPassword(_ password: String) {
        self.password.value = password
    }
    
    /// Reset the form to initial state
    func reset() {
        ssid.value = ""
        password.value = ""
        networkHasNoPassword = false
        updatePasswordValidation()
    }
    
    /// Get the raw values as WifiConfig
    func getRawValue() -> WifiConfig {
        return WifiConfig(
            ssid: ssid.value,
            password: networkHasNoPassword ? nil : password.value
        )
    }
    
    /// Get error message for any form control
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty || control.isTouched else { return nil }
        
        if let passwordControl = control as? FormControl<String>,
           passwordControl === password,
           networkHasNoPassword {
            return nil
        }
        
        if control.errors[.required] { return errorMessages.required }
        
        return nil
    }
    
    func touchAndValidatePassword() {
        password.markAsTouched()
        password.validate()
    }
    
    func touchAndValidateSSID() {
        ssid.markAsTouched()
        ssid.validate()
    }
    
    /// Update password validation based on networkHasNoPassword toggle
    private func updatePasswordValidation() {
        if networkHasNoPassword {
            // Remove required validator and clear password
            password.removeValidator(ofType: .required)
            password.value = ""
            password.resetInteractionState()
        } else {
            // Add required validator if not present
            if !password.validators.contains(where: { $0.type == .required }) {
                password.addValidator(.required)
            }
            password.resetInteractionState()
        }
    }
} 
