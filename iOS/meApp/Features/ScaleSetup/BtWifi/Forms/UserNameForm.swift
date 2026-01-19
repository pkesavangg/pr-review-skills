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
    var displayName = FormControl("", validators: [.required, .noWhiteSpace, .alphanumeric, .userNameUnavailable, .maxLength(20)])
    @Published var userList: [ScaleUser] = []
    /// The current user's initial name
    var currentUserName: String? = nil
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
        if control.errors[.duplicate] { return errorMessages.duplicate }
        
        guard control.isDirty else { return nil }
        
        if control.errors[.required] { return errorMessages.required }
        if control.errors[.noWhiteSpace] { return errorMessages.noWhiteSpace }
        if control.errors[.alphanumeric] { return errorMessages.validInput }
        if control.errors[.maxLength] { return errorMessages.maxLength(20) }
        if control.errors[.userNameUnavailable] { return errorMessages.userNameUnavailable }
        
        return nil
    }
    
    /// Setup duplicate validation based on current user list
    private func setupDuplicateValidation() {
        // Remove existing duplicate validator
        displayName.removeValidator(ofType: .duplicate)
        // Add new duplicate validator with current user list
        let duplicateValidator = Validator<String>.duplicateUser {
            // Filter out the current user's name from the duplicate check
            let currentNameLower = self.currentUserName?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            return self.userList
                .map { $0.name }
                .filter { name in
                    // Exclude current user's name from duplicate check
                    guard let currentName = currentNameLower else { return true }
                    return name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() != currentName
                }
        }
        displayName.addValidator(duplicateValidator)
    }
    
    /// Set the current user's name to exclude from duplicate check
    func setCurrentUserName(_ name: String?) {
        currentUserName = name
        setupDuplicateValidation()
    }
} 
