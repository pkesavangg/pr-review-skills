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
        static let subtitle = "This is also important in determining body metrics and healthy ranges according to CDC and AHA health guidelines. " +
            "Please choose what most closely reflects your body type and makes you most comfortable."
    }
    
    struct HeightStep {
        static let title = "How tall are you?"
        static let subtitle = "Height is another factor that helps us provide you with the most accurate metrics."
        static let pickerHeader = "Height"
        static let fieldLabel = "Height"
        static let imperialUnit = "Ft / In"
        static let metricUnit = "CM"
    }

    struct GoalStep {
        static let title = "Set a goal!"
        static let subtitle = "This can be helpful feature to utilize on your journey. Goals can always be changed in the app settings."
        static let imperialUnit = "lbs"
        static let metricUnit = "kg"
    }
    
    struct EmailStep {
        static let title = "What's your email?"
        static let subtitle = "Be sure to use a valid email. You'll use this to login and it's where we'll send any reports."
    }
    
    struct PickDeviceStep {
        static let title = "What will you use with meApp?"
        static let babyScaleTitle = "Baby Scale"
        static let bpmTitle = "Blood Pressure Monitor"
        static let weightScaleTitle = "Weight Scale"
        static let babyScaleSubtitle = "Track baby growth"
        static let bpmSubtitle = "BP trends & reminders"
        static let weightScaleSubtitle = "BMI & weight insights"
        static let alreadyAdded = "already added to your profile"
    }

    struct AddBabyStep {
        static let title = "Add a Baby"
        static let subtitle = "Let's add a baby. If you don't know their info, take your best guess. You can always update it later."
        static let birthdayLabel = "baby's Birthday"
    }

    struct BabyListStep {
        static let title = "Your baby has been added!"
        static let addBabyButton = "ADD A BABY"
    }

    struct PasswordStep {
        static let title = "Create a password."
        static let subtitle = "Your password must be at least six characters."
        static let termsAndPrivacyText = "By completing, you are agreeing to our"
        static let termsOfService = "Terms of Service"
        static let privacyPolicy = "Privacy Policy"
        static let andText = "&"
    }

    struct ProfileReadyStep {
        static let weightScaleTitle = "Your weight scale profile is ready!"
        static let bpmTitle = "Your blood pressure monitor profile is ready!"
        static let babyScaleTitle = "Your baby scale profile is ready!"
        static let finishButton = "FINISH"
        static let connectAnotherDevice = "CONNECT ANOTHER DEVICE"
    }

    struct AllProfilesReadyStep {
        static let title = "All your health profiles have been set up successfully!"
        static let doneButton = "DONE"
    }

    struct SignupErrorStep {
        static let title = "Something went wrong."
        static let subtitle = "We couldn't save your profile. Check your connection and try again."
        static let deviceSuccess = "Added to your profile"
        static let deviceFailure = "Profile couldn't be saved — tap Try Again"
        static let devicePending = "Not yet started"
        static let cancelButton = "CANCEL"
        static let tryAgainButton = "TRY AGAIN"
    }
}
