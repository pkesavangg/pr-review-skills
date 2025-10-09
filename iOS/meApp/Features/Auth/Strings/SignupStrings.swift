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
    
    struct SexStep {
        static let title = "What is your biological sex?"
        static let subtitle = "This is also important in determining body metrics and healthy ranges according to CDC and AHA health guidelines. Please choose what most closely reflects your body type and makes you most comfortable."
    }
    
    struct HeightStep {
        static let title = "How tall are you?"
        static let subtitle = "Height is another factor that helps us provide you with the most accurate metrics."
        static let pickerHeader = "Height"
    }
    
    struct GoalStep {
        static let title = "Set a goal!"
        static let subtitle = "This can be a helpful feature to utilize on your journey. Goals can always be changed in the app settings."
    }
    
    struct EmailStep {
        static let title = "What's your email?"
        static let subtitle = "Be sure to use a valid email. You'll use this to login and it's where we'll send any reports."
    }
    
    struct PasswordStep {
        static let title = "Create a password."
        static let subtitle = "Your password must be at least six characters."
        static let termsAndPrivacyText = "By completing, you are agreeing to our"
        static let termsOfService = "Terms of Service"
        static let privacyPolicy = "Privacy Policy"
        static let andText = "&"
    }    
}
