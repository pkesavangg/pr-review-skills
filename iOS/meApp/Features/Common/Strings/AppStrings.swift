//
//  AppStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//

import Foundation

/// Constants for form validation error messages
struct FormErrorMessages {
    static let required = "Must not be left blank"
    static let email = "Must use a valid email"
    static let passwordMaxLength = "Password must not exceed 50 characters."
    static let minLength = { (length: Int) in "Minimum of \(length) characters needed" }
    static let maxLength = { (length: Int) in "Maximum \(length) characters allowed." }
    static let min = { (value: Int) in "Value must be at least \(value)." }
    static let max = { (value: Int) in "Value must not exceed \(value)." }
    static let noWhiteSpace = "Must not be left blank"
    static let futureDate = "Date cannot be in the future."
    static let requiredTrue = "This checkbox must be checked."
    static let passwordMatch = "Passwords do not match."
}

struct AlertStrings {
    struct SignupExitAlert {
        static let title = "Confirm"
        static let message = "Are you sure you want to leave?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
}

struct AppAssets {
    static let eyeOpen = "eyeOpen"
    static let eyeClosed = "eyeSlash"
    static let closeCircle = "closeCircle"
    static let helpCircle = "helpCircle"
}
