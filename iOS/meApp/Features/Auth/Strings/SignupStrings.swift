//
//  SignupStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import Foundation


/// SignupStrings provides a centralized collection of string constants used throughout the signup process in the application.
struct SignupStrings {
    struct NameStep {
        static let title = "What's your name?"
        static let subtitle = "We just need a first name or even a nickname. But rest assured we protect whatever info you give us."
    }
    
    struct DateOfBirthStep {
        static let title = "When were you born?"
        static let subtitle = "Your age helps us accurately calculate body metrics and healthy ranges. Note: You must be 13+ to make an account."
        static let birthdayLabel = "BIRTHDAY"
    }
}
