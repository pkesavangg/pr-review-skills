//
//  AppStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//

import Foundation

/// Constants for common strings used throughout the app
struct CommonStrings {
    static let done = "Done"
    static let cancel = "Cancel"
    static let next = "Next"
    static let back = "Back"
    static let save = "Save"
    static let skip = "Skip"
}

/// Constants for form validation error messages
struct FormErrorMessages {
    static let required = "must not be left blank"
    static let email = "must use a valid email"
    static let passwordMaxLength = "password must not exceed 50 characters"
    static let minLength = { (length: Int) in "minimum of \(length) characters needed" }
    static let maxLength = { (length: Int) in "maximum \(length) characters allowed" }
    static let min = { (value: Int) in "value must be at least \(value)" }
    static let max = { (value: Int) in "value must not exceed \(value)" }
    static let noWhiteSpace = "must not be left blank"
    static let futureDate = "future dates not accepted"
    static let passwordMatch = "passwords do not match"
    static let valueShouldBeEqual = "value should not be equal to current weight"
}

/// Constants for input field labels used in the app
struct InputFieldLabels {
    static let firstName = "first name"
    static let lastName = "last name"
    static let email = "email"
    static let password = "password"
    static let confirmPassword = "confirm password"
    static let currentWeight = "current weight"
    static let targetWeight = "target weight"
}

/// Constants for Alert strings used in the app
struct AlertStrings {
    struct SignupExitAlert {
        static let title = "Confirm"
        static let message = "Are you sure you want to leave?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
}

/// Constants for App Assets used in the app
struct AppAssets {
    static let eyeOpen = "eyeOpen"
    static let eyeClosed = "eyeSlash"
    static let closeCircle = "closeCircle"
    static let helpCircle = "helpCircle"
    static let xmark = "xmark"
}
