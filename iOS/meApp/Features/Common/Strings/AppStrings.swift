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
    static let submit = "Submit"
    static let next = "Next"
    static let back = "Back"
    static let save = "Save"
    static let skip = "Skip"
    static let logIn = "Log in"
    static let complete = "Complete"
    static let edit = "Edit"
    
    static let dash = "dash"
    static let entry = "entry"
    static let history = "history"
    static let settings = "settings"
    static let appSync = "appsync"
    static let optional = "optional"
    static let unitKgCm = "kg & cm"
    static let unitLbsFeet = "lbs & feet"
    static let on = "On"
    static let off = "Off"
    static let dark = "Dark"
    static let light = "Light"
    static let system = "System Settings"
}

/// Constants for toast messages used in the app
struct ToastStrings {
    static let loginError = "Login Error"
    static let networkError = "Network Error"
    static let unableToConnect = "Unable to find a network connection at this time. Please try again later."
    static let somethingWentWrong = "Something went wrong. Please try again. If the problem continues, contact customer service."
    static let serverError = "Unable to reach the Greater Goods servers. The issue is probably on our end. Try again later, but if the problem continues, contact customer service."
    static let emailInUse = "Email address is already in use"
    static let errorCreatingAccount = "Error creating account."
    static let invalidEmailTitle = "Invalid Email ID!"
    static let invalidEmailMessage = "Enter a valid email address."
    static let invalidCredentials = "Your Email or password is incorrect. Please try again."
    static let passwordResetSuccessMessage = { (email: String) in
        "An email with a link to reset your password has been sent to \(email). The link will be valid for the next 10 minutes."
    }
    static let success = "Success!"
    static let entryAdded = "Entry added."
    static let errorSavingEntry = "Error saving new entry!"
    static let pleaseTryAgain = "Please try again."
    static let errorUpdatingProfile = "Error updating profile"
    static let profileSaved = "Profile saved successfully."
    static let csvExported = ".CSV file sent. Please check your email."
    static let passwordUpdated = "Password updated."
    static let errorUpdatingPassword = "Error updating password."
    static let restartAndTryAgain = "Restart the app and try again."
}

/// Constants for help modal strings used in the app
struct HelpStrings {
    static let question = "Have a question?"
    static let generalHelp = "We're here for you. Contact us and we'll be happy to help."
    static let viewGuide = "View Getting Started Guide"
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
    static let bothPasswordsMatch = "both passwords must match"
    static let valueShouldBeEqual = "value should not be equal to current weight"
    static let minWeightKg = "value should be greater than 0 kg"
    static let minWeightLb = "value should be greater than 0 lbs"
    static let maxWeightKg = "value should be less than 450 kg"
    static let maxWeightLb = "value should be less than 999 lbs"
    static let minValue = "value should be greater than 0"
    static let maxValue99 = "value should be less than 99"
    static let maxValue = {(value: Int) in "value should be less than \(value)"}
    static let passwordResetFailed = "Failed to send password reset email."
    static let newPasswordDifferent = "New password must be different from old password"
}

/// Constants for input field labels used in the app
struct InputFieldLabels {
    static let firstName = "first name"
    static let lastName = "last name"
    static let email = "email"
    static let password = "password"
    static let confirmPassword = "confirm password"
    static let currentPassword = "current password"
    static let currentWeight = "current weight"
    static let goalWeight = "goal weight"
    static let useMetric = "Use Metric"
    static let zipCode = "zipcode"
    static let weightLabel: (Bool) -> String = { isKg in
        return isKg ? "weight (kg)" : "weight (lbs)"
    }
    static let date = "Date"
    static let bmi = "bmi"
    static let bodyFat = "body fat %"
    static let muscleMass = "muscle mass %"
    static let bodyWater = "body water %"
    static let heartRate = "heart rate (bpm)"
    static let boneMass = "bone mass %"
    static let visceralFat = "visceral fat (Lv.)"
    static let subcutaneousFat = "subcutaneous fat %"
    static let protein = "protein %"
    static let skeletalMuscles = "skeletal muscles %"
    static let basalMetabolicRate = "basal metabolic rate (kcal)"
    static let metabolicAge = "metabolic age (yrs)"
}

/// Constants for Alert strings used in the app
struct AlertStrings {
    struct SignupExitAlert {
        static let title = "Confirm"
        static let message = "Are you sure you want to leave?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    
    struct ResetPasswordAlert {
        static let passwordResetTitle = "Password Reset"
        static let enterEmailMessage = "Enter your email below."
    }
    
    struct ManualEntryExitAlert {
        static let title = "Your entry has not been saved!"
        static let message = "Are you sure you want to exit?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    
    struct LogoutAlert {
        static let title = "Log Out"
        static let message = "Are you sure you want to log out?"
        static let logoutButton = "Log Out"
        static let cancelButton = "Cancel"
    }
    
    struct DeleteAccountAlert {
        static let title = "Delete Your Account"
        static let message = "Are you sure you want to delete your account? This action cannot be undone."
        static let deleteButton = "Delete"
        static let cancelButton = "Cancel"
    }
    struct EditProfileExitAlert {
        static let title = "Confirm"
        static let message = "You have unsaved changes. Are you sure you want to exit?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    struct ChangePasswordExitAlert {
        static let title = "Confirm"
        static let message = "You have unsaved changes. Are you sure you want to exit?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    struct CsvExportAlert {
        static let title = "Download Weight History"
        static let message = "An email with your measurement history will be sent to the email address associated with this account."
        static let sendButton = "Send"
        static let cancelButton = "Cancel"
    }
}

struct LoaderStrings {
    static let creatingAccount = "Creating account..."
    static let savingEntry = "Saving entry..."
    static let saving = "Saving..."
    static let loggingAccount = "Logging in..."
    static let loggingOut = "Logging out..."
    static let deletingAccount = "Deleting account..."
    static let sendingEmail = "Sending email..."
    static let sendingCsv = "Sending .CSV File..."
}

struct URLStrings {
    static let baseUrl =  "https://greatergoods.com/"
}

/// Constants for legal text used in the app
struct LegalStrings {
    static let termsAndPrivacyText = "By clicking \"COMPLETE\", you are agreeing to our"
    static let termsOfService = "Terms of Service"
    static let privacyPolicy = "Privacy Policy"
    static let andText = "&"
}

/// Constants for App Assets used in the app
struct AppAssets {
    static let eyeOpen = "eyeOpen"
    static let eyeClosed = "eyeSlash"
    static let closeCircle = "closeCircle"
    static let helpCircle = "helpCircle"
    static let xmark = "xmark"
    static let meLogoDark = "meLogoDark"
    static let meLogoLight = "meLogoLight"
    static let stamp = "stamp"
    static let stampDark = "stampDark"
    static let phone = "phone"
    static let email = "email"
    static let loader = "loader"
    static let dash = "dash"
    static let dashFill = "dashFill"
    static let addEntry = "addEntry"
    static let addEntryFill = "addEntryFill"
    static let settings = "settings"
    static let settingsFill = "settingsFill"
    static let history = "history"
    static let historyFill = "historyFill"
    static let appSync = "appSync"
    static let chevronUp = "chevronUp"
    static let chevronDown = "chevronDown"
    static let chevronRight = "chevronRight"
}

