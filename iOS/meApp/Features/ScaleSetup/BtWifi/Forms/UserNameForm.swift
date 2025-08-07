import Foundation
import Combine
import SwiftUI

/// User model for duplicate checking
struct ScaleUser {
    let name: String
    let token: String?
}

/// Form for handling username input with validation
class UserNameForm: ObservableForm {
    var displayName = FormControl("", validators: [.required, .noWhiteSpace, .namePattern, .userNameUnavailable, .maxLength(20)])
    @Published var userList: [ScaleUser] = []
    private let errorMessages = FormErrorMessages.self
    
    /// Publisher that merges all value changes in the form
    var formDidChange: AnyPublisher<Void, Never> {
        Publishers.MergeMany([
            displayName.$value.map { _ in () }.eraseToAnyPublisher()
        ])
        .eraseToAnyPublisher()
    }
    
    /// Update the user list for duplicate checking and refresh validation
    func updateUserList(_ users: [ScaleUser]) {
        userList = users
        setupDuplicateValidation()
    }
    
    /// Set the display name
    func setDisplayName(_ name: String) {
        displayName.value = name
    }
    
    /// Reset the form to initial state
    func reset() {
        displayName.value = ""
        setupDuplicateValidation()
    }
    
    /// Get error message for the display name control
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }
        
        if control.errors[.required] { return errorMessages.required }
        if control.errors[.noWhiteSpace] { return errorMessages.noWhiteSpace }
        if control.errors[.namePattern] { return errorMessages.namePattern }
        if control.errors[.maxLength] { return errorMessages.maxLength(20) }
        if control.errors[.userNameUnavailable] { return errorMessages.userNameUnavailable }
        if control.errors[.duplicate] { return errorMessages.duplicate }
        
        return nil
    }
    
    /// Setup duplicate validation based on current user list
    private func setupDuplicateValidation() {
        // Remove existing duplicate validator
        displayName.removeValidator(ofType: .duplicate)
        
        // Add new duplicate validator with current user list
        let duplicateValidator = Validator<String>.duplicateUser {
            self.userList.map { $0.name }
        }
        displayName.addValidator(duplicateValidator)
    }
} 
