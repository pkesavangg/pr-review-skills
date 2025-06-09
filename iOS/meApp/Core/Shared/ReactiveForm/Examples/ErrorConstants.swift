import Foundation

/// Constants for form validation error messages
enum FormErrorMessages {
    static let required = "This field is required."
    static let email = "Please enter a valid email address."
    static let minLength = { (length: Int) in "Minimum \(length) characters required." }
    static let maxLength = { (length: Int) in "Maximum \(length) characters allowed." }
    static let min = { (value: Int) in "Value must be at least \(value)." }
    static let max = { (value: Int) in "Value must not exceed \(value)." }
    static let noWhiteSpace = "Field cannot contain only whitespace."
    static let futureDate = "Date cannot be in the future."
    static let requiredTrue = "This checkbox must be checked."
    static let passwordMatch = "Passwords do not match."
    static let url = "Please enter a valid URL."
} 