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
    static let ok = "OK"
    static let retry = "Retry"
    static let cancel = "Cancel"
    static let submit = "Submit"
    static let next = "Next"
    static let back = "Back"
    static let save = "Save"
    static let skip = "Skip"
    static let logIn = "Log in"
    static let signUp = "Sign up"
    static let complete = "Complete"
    static let edit = "Edit"
    static let dash = "dash"
    static let entry = "entry"
    static let history = "history"
    static let settings = "settings"
    static let appSync = "appsync"
    static let optional = "optional"
    static let connectScale = "Connect Scale"
    static let unitKgCm = "kg & cm"
    static let unitLbsFeet = "lbs & ft"
    static let pickerLbs = "lbs & feet"
    static let on = "On"
    static let off = "Off"
    static let dark = "Dark"
    static let light = "Light"
    static let system = "System Settings"
    static let byGreaterGoods = "By Greater Goods"
    static let date = "Date"
    static let weight = "Weight"
    static let resources = "Resources"
    static let heartRateLabel = "Heart Rate: "
    static let update = "Update"
    static let appVersion = "App Version"
    static let yes = "Yes"
    static let no = "No"
    static let iOS = "iOS"
    static let finish = "Finish"
}

/// Constants for entry strings used in the app
struct EntryStrings {
    static let noEntries = "No Entries"
    static let toStart = "To start, connect a scale or add a manual entry."
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
    static let forgotPassword = { (email: String) in
        "Password reset link sent to \(email)."
    }
    static let success = "Success!"
    static let entryAdded = "Entry added."
    static let errorSavingEntry = "Error saving new entry!"
    static let pleaseTryAgain = "Please try again."
    static let errorUpdatingProfile = "Error updating profile"
    static let profileSaved = "Profile saved successfully."
    static let goalSaved = "Goal Saved."
    static let errorSettingGoal = "Error setting new goal!"
    static let csvExported = ".CSV file sent. Please check your email."
    static let passwordUpdated = "Password updated."
    static let errorUpdatingPassword = "Error updating password."
    static let errorUpdatingWeightless = "Error updating Weightless Settings."
    static let restartAndTryAgain = "Restart the app and try again."
    static let csvExportError = "Error sending .CSV file. Please try again."
    static let unitSettingUpdated = "Unit settings updated."
    static let notificationSettingUpdated = "Notification settings updated."
    static let streakSettingUpdated = "Streak settings updated."
    static let activitySettingUpdated = "Activity level updated."
    static let genderUpdated = "Gender updated."
    static let weightlessUpdated = "Weightless updated."
    static let somethingWentWrongTitle = "Something went wrong!"
    static let unableToUpdateAccountSettings = "Unable to update your account settings at this time. Please try again later."
    static let heightUpdated = "Your height has been updated."
    static let errorUpdatingHeight = "Error updating height."
    static let switchingAccount = { (name: String) in
        "Switched to \(name)."
    }
    static let weightHistorySynced = "Weight history successfully synced."
    static let hkIntegrationRemoved = "Apple Health integration removed."
    static let hkIntegrationSynced = "Apple Health is synced!"
    static let saveScaleError = "Error saving scale. Please restart the app and try again."
    static let restartApp = "Please restart your app and try again."
}

/// Constants for help modal strings used in the app
struct HelpStrings {
    static let question = "Have a question?"
    static let generalHelp = "We're here for you. Contact us and we'll be happy to help."
    static let viewGuide = "View Getting Started Guide"
}

struct HKIntegrationStrings {
    static let healthAccess = "Health Access"
    
}

/// Constants for Apple Health integration strings used in the app
struct HKIntegrationHealthAccessStrings {
    static let notConnected = HKIntegrationHealthAccessContent(
        imageName: AppAssets.hkPermissionsNotAllowedSS,
        title: "Integrate Apple Health",
        description: "Personalize your experience and control which information is shared between Weight Gurus and Apple Health. Your privacy and data security are top priorities.",
        buttonTitle: "CONNECT"
    )
    
    static let permissionsAllowed = HKIntegrationHealthAccessContent(
        imageName: AppAssets.hkPermissionsAllowedSS,
        title: "Integrate Apple Health",
        description: "Personalize your experience and control which information is shared between Weight Gurus and Apple Health. Your privacy and data security are top priorities.",
        buttonTitle: "CONNECT"
    )
    
    static let integrationComplete = HKIntegrationHealthAccessContent(
        imageName: AppAssets.hkIntegrationCompleteSS,
        title: "Integration Complete",
        description: nil,
        buttonTitle: "FINISH",
        attributedParts: (
            prefix: "Manage settings anytime by opening Apple Health and going to ",
            highlight: "Profile → Privacy → Apps → Weight Gurus."
        )
    )
    
    static let integrationFailed = HKIntegrationHealthAccessContent(
        imageName: AppAssets.hkPermissionsNotAllowedSS,
        title: "Integration Failed",
        description: "To troubleshoot, open Apple Health and turn on Weight Gurus permissions. Then, come back to Weight Gurus and finish connecting.",
        buttonTitle: "OPEN APPLE HEALTH"
    )
    
    static let userConflict = HKIntegrationHealthAccessContent(
        imageName: AppAssets.hkPermissionsAllowedSS,
        title: "User Conflict",
        description: "Another user has already connected to Apple Health on this device. Please ask them to log in to their account and disconnect the integration.",
        buttonTitle: "EXIT"
    )
}

struct HKIntegrationModalStrings {
    
    static let outOfSync = HKIntegrationModalContent(
        imageName: AppAssets.hkLogoLarge,
        title: "Apple Health is Out of Sync",
        message: nil,
        primaryButtonTitle: "OPEN APPLE HEALTH",
        secondaryButtonTitle: "REMOVE INTEGRATION",
        attributedParts: (
            prefix: "Enable settings in Apple Health by navigating to ",
            highlight: "Profile → Privacy → Apps → Weight Gurus",
            suffix: ". Or remove the integration in Weight Gurus."
        )
    )
    
    
    static let finishAdding = HKIntegrationModalContent(
        imageName: AppAssets.hkLogoLarge,
        title: "Finish Adding Apple Health",
        message: "Weight Gurus permissions have been turned on in Apple Health. Connect to complete set up.",
        primaryButtonTitle: "CONNECT",
        secondaryButtonTitle: nil
    )
    
    static let addIntegration = HKIntegrationModalContent(
        imageName: AppAssets.hkLogoLarge,
        title: "Add Apple Health Integration",
        message: "It looks like you're using Weight Gurus on a new device. To continue syncing with Apple Health, please reconnect.",
        primaryButtonTitle: "CONNECT",
        secondaryButtonTitle: nil
    )
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
    static let valueShouldNotBeEqual = "value should not be equal to current weight"
    static let minWeightKg = "value should be greater than 0 kg"
    static let minWeightLb = "value should be greater than 0 lbs"
    static let maxWeightKg = "value should be less than 450 kg"
    static let maxWeightLb = "value should be less than 999 lbs"
    static let minValue = "value should be greater than 0"
    static let maxValue99 = "value should be less than 99"
    static let maxValue = {(value: Int) in "value should be less than \(value)"}
    static let passwordResetFailed = "Failed to send password reset email."
    static let newPasswordDifferent = "New password must be different from old password"
    static let modelNumberInvalid = "Model Number Invalid"
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
    static let birthday = "birthday"
    static let weightLabel: (Bool) -> String = { isKg in
        return isKg ? "weight (kg)" : "weight (lbs)"
    }
    static let weightLessLabel: (Bool) -> String = { isKg in
        return isKg ? "weightless weight (kg)" : "weightless weight (lbs)"
    }
    static let date = "Date"
    static let bmi = "bmi"
    static let bodyFat = "body fat (%)"
    static let muscleMass = "muscle mass (%)"
    static let bodyWater = "body water (%)"
    static let heartRate = "heart rate (bpm)"
    static let boneMass = "bone mass (%)"
    static let visceralFat = "visceral fat (Lv.)"
    static let subcutaneousFat = "subcutaneous fat (%)"
    static let protein = "protein (%)"
    static let skeletalMuscles = "skeletal muscles (%)"
    static let basalMetabolicRate = "basal metabolic rate (kcal)"
    static let metabolicAge = "metabolic age (yrs)"
}

/// Constants for Alert strings used in the app
struct AlertStrings {
    struct SignupExitAlert {
        static let title = "Confirm"
        static let message = "Are you sure you want to leave?"
        static let goBackButton = "Go back"
        static let exitButton = "Yes, exit"
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
    
    struct LogoutAllAccountAlert {
        static let title = "Log Out All Accounts"
        static let message = "Are you sure you want to log out of all accounts?"
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
    
    struct WeightLessExitAlert {
        static let title = "Confirm"
        static let message = "You have unsaved changes. Are you sure you want to exit?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    
    struct DeleteScaleAlert {
        static let title = "Delete Your Scale"
        static let message = "Are you sure you want to delete this scale? This action cannot be undone."
        static let deleteButton = "Delete"
        static let cancelButton = "Cancel"
    }
    
    struct ConnectWifiNetwork {
        static let title = "Are you sure you want to exit?"
        static let message = "Wi-Fi settings will not be updated."
        static let goBackButton = "Go Back"
        static let exitButton = "Exit"
    }
    
    struct GoalExitAlert {
        static let title = "Are you sure you want to leave?"
        static let message = "You have unsaved changes. Are you sure you want to exit?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    
    struct ForgotPasswordAlert {
        static let title = "Forgot your Password?"
        static let message: (String) -> String = { email in
            return "Send a password reset link to \(email)"
        }
        static let send = "send"
        static let cancel = "Cancel"
    }
    
    struct MaxUsersAlert {
        static let title = "Maximum Users Reached"
        static let message = "Please swipe left to remove any unused accounts before attempting to add a new one."
        static let logInAndRemoveMessage = "Log in to a saved account, then open Settings and tap Switch Accounts to remove users."
        static let okButton = "OK"
    }
    
    struct LoginExitAlert {
        static let title = "Confirm"
        static let message = "Are you sure you want to leave?"
        static let goBackButton = "Go Back"
        static let yesExitButton = "Yes, Exit"
    }
    
    struct DeleteUserAlert {
        static let title: (String) -> String = { name in
            return "Remove \(name)?"
        }
        static let message: (String) -> String = { name in
            return "Are you sure you want to remove \(name) from this device?"
        }
        static let removeButton = "Remove"
        static let cancelButton = "Cancel"
    }
    
    struct ExpiredUserLogOutAlert {
        static let title: (String) -> String = { name in
            return "\(name) was logged out"
        }
        static let message = "Please log back in to continue."
        static let okButton = "OK"
    }
    
    struct SyncWeightHistoryAlert {
        static let title = "Sync Weight History"
        static let message = "Do you want to sync all entries to Apple Health? You cannot do this later without reconnecting."
        static let syncButton = "Sync"
        static let cancelButton = "Cancel"
    }
    
    struct HKOutOfSyncAlert {
        static let title = "Apple Health Out of Sync"
        static let message = "Enable app permissions in Apple Health or remove the integration in Weight Gurus."
        static let closeButton = "CLOSE"
    }
    
    struct HKRemoveAlert {
        static let title = "Are you sure?"
        static let message = "The integration will be removed.  To fully disconnect, ensure Weight Gurus is disabled in the Apple Health App."
        static let removeButton = "Remove"
        static let cancelButton = "Cancel"
    }
    
    // Alert shown when user attempts to disconnect a third-party integration (Fitbit / MyFitnessPal).
    struct RemoveIntegrationAlert {
        static let title = "Are you sure you want to turn off this integration?"
        static let cancelButton = "Cancel"
        static let removeButton = "Remove"
    }
    
    // Alert shown when integration add/remove fails.
    struct IntegrationFailureAlert {
        static let message = "Sorry, something went wrong. Try again?"
    }
    
    // Alert when in-app browser cannot open link.
    struct LinkOpenErrorAlert {
        static let title = "Something went wrong!"
        static let message = "Copy this link and paste it into your web browser."
        static let copyLinkButton = "Copy Link"
        static let dismissButton = "Dismiss"
    }
    
    // Alert when integration is enabled but invalid.
    struct ReIntegrateAlert {
        static let disableButton: (String) -> String = { name in "Disable \(name)" }
        static let disableAllButton = "Disable All"
        static let okButton = "OK"
        static let message: (String, Int) -> String = { name, count in
            "Unable to connect to \(name)! You may need to re-enable \(count > 1 ? "these" : "this") integration by re-authorizing your account."
        }
    }
    
    struct ExitSetupAlert {
        static let title = "Confirm"
        static let message = "Are you sure you want to exit setup?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
    
    struct ResetDashboardAlert {
        static let title = "Are you sure?"
        static let message = "Your dashboard display metrics will reset to default settings"
        static let cancelButton = "Cancel"
        static let resetButton = "Reset"
    }
    
    struct HKExitAlert {
        static let title = "Are you sure you want to exit?"
        static let message = "Apple Health will not sync with Weight Gurus."
        static let cancelButton = "cancel"
        static let exitButton = "exit setup"
    }
    
    struct DeviceAlreadyPairedAlert {
        static let title = "Device Already Paired"
        static let message: (String) -> String = { sku in
            "The device with SKU: \(sku) is already paired. Do you want to pair it again?"
        }
        static let returnButton = "RETURN"
        static let pairButton = "PAIR"
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
    static let loading = "Loading..."
    static let removingIntegration = "Removing integration..."
    static let syncing = "Syncing..."
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
    static let wgLogoDark = "wgLogoDark"
    static let wgLogoLight = "wgLogoLight"
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
    static let bluetooth = "bluetooth"
    static let btWifi = "btWifi"
    static let chevronUp = "chevronUp"
    static let chevronDown = "chevronDown"
    static let chevronRight = "chevronRight"
    static let chevronLeft = "chevronLeft"
    static let chevronUpDown = "chevronUpDown"
    static let bmiIcon = "bmi"
    static let bodyFatIcon = "bodyFat"
    static let muscleIcon = "muscle"
    static let waterIcon = "bodyWater"
    static let heartIcon = "heartRate"
    static let boneIcon = "boneMass"
    static let visceralFatIcon = "visceralFat"
    static let subcutaneousFatIcon = "subcutaneousFat"
    static let proteinIcon = "protein"
    static let skeletalMuscleIcon = "skeletalMuscle"
    static let bmrIcon = "bmr"
    static let ageIcon = "metabolicAge"
    static let emptyStateIcon = "emptyStateIcon"
    static let checkMarkCircle = "checkMarkCircle"
    static let circleOutline = "circleOutline"
    static let circleCheckFilled = "circleCheckFilled"
    static let wifi = "wifi"
    static let trash = "trash"
    static let filledCloseCircle = "filledCloseCircle"
    static let filledTickCircle = "filledTickCircle"
    static let exclamationMark = "exclamationMark"
    static let weightOnlyMode = "weightOnlyMode"
    static let scaleWeightOnlyMode = "scaleWeightOnlyMode"
    static let  skuNumberSticker = "skuNumberSticker"
    static let userProfile = "userProfile"
    // Newly added from image
    static let exclamationDanger = "exclamationDanger"
    static let hkIntegrationCompleteSS = "hkIntegrationCompleteSS"
    static let hkPermissionsAllowedSS = "hkPermissionsAllowedSS"
    static let hkPermissionsNotAllowedSS = "hkPermissionsNotAllowedSS"
    static let fitbitLogoSmall = "fitbitLogoSmall"
    static let fitbitLogoLarge = "fitbitLogoLarge"
    static let hkLogoLarge = "hkLogoLarge"
    static let hkLogoSmall = "hkLogoSmall"
    static let myFitnessLogoSmall = "myFitnessLogoSmall"
    static let myFitnessLogoLarge = "myFitnessLogoLarge"
    static let ggLogoSmall = "ggLogoSmall"
    static let ggLogoLarge = "ggLogoLarge"
    static let checkMarkLarge = "checkMarkLarge"
    static let appSyncTab = "appSyncTab"
    
    // MARK: - Scale images
    // MARK: - AppSync series
    static let scale0341 = "0341"
    static let scale0342 = "0342"
    static let scale0343 = "0343"
    static let scale0345 = "0345"
    static let scale0346 = "0346"
    static let scale0347 = "0347"
    static let scale0358 = "0358"
    static let scale0359 = "0359"
    static let scale0364 = "0364"
    static let scale0369 = "0369"
    static let scale0370 = "0370"
    static let scale0371 = "0371"
    
    // MARK: - Bluetooth series
    static let scale0375 = "0375"
    static let scale0376 = "0376"
    static let scale0378 = "0378"
    static let scale0380 = "0380"
    static let scale0382 = "0382"
    static let scale0383 = "0383"
    
    // MARK: - WiFi series
    static let scale0384 = "0384"
    static let scale0385 = "0385"
    static let scale0396_0397 = "0396_0397" // Wi-Fi Smart Scale (0396 & 0397 share artwork)
    
    // MARK: - Bluetooth wifi series
    static let scale0412 = "0412"
    static let streak = "streak"
    static let longestStreak = "longestStreak"
    static let plusCircle = "plusCircle"
    static let minusCircle = "minusCircle"
    static let plusCircleDark = "plusCircleDark"
    static let minusCircleDark = "minusCircleDark"
    static let minusCircleClear = "minusCircleClear"
}

/// Constants used in the AppSync entry result confirmation card
struct AppSyncEntryCardStrings {
    static let title = "Your AppSync Scan was successful!"
    static let weight = "Weight"
    static let bodyFat = "Body Fat"
    static let muscleMass = "Muscle Mass"
    static let waterWeight = "Water Weight"
    static let bmi = "BMI"
}

