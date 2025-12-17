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
    static let save = "Save"
    static let skip = "Skip"
    static let logIn = "Log in"
    static let signUp = "Sign up"
    static let complete = "Complete"
    static let edit = "Edit"
    static let exit = "Exit"
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
    static let heartRateLabel = "Heart Rate:"
    static let update = "Update"
    static let appVersion = "App Version"
    static let yes = "Yes"
    static let no = "No"
    static let iOS = "iOS"
    static let finish = "Finish"
    static let delete = "Delete"
    static let permissions = "Permissions"
    static let allow = "Allow"
    static let ignore = "Ignore"
    static let enable = "Enable"
    static let tryAgain = "Try Again"
    static let support = "Support"
    static let connect = "Connect"
    static let dismiss = "Dismiss"
    static let emailAlreadyInUse = "Email already in use"
    static let why = "Why?"
    static let back = "Back"
}

/// Constants for entry strings used in the app
struct EntryStrings {
    static let noEntries = "No Entries"
    static let toStart = "To start, connect a scale or add a manual entry."
}

/// Constants for scale discovered sheet strings used in the app
struct ScaleDiscoveredSheetStrings {
    static let title = "New Scale Discovered"
}

struct WeightOnlyModeAlertStrings {
    static let title = "A User has Weight Only Mode on"
    static let enableAllBodyMetrics = "You can enable All Body Metrics for one session. This will temporarily disable Weight Only mode, and all body metrics will be collected."
}


/// Constants for toast messages used in the app
struct ToastStrings {
    static let error = "Error"
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
        "An email with a link to reset your password has been sent to \(email). The link will be valid for the next 30 minutes."
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
    static let displayMetricsSaved = "Display metrics saved successfully."
    static let errorSavingDisplayMetrics = "Failed to save display metrics."
    static let userNameUpdated = "User name updated successfully."
    static let errorUpdatingUserName = "Failed to update user name."
    static let userDeleted = "User deleted successfully."
    static let errorDeletingUser = "Failed to delete user."
    static let scaleDeleted = "Scale deleted."
    static let errorDeletingScale = "Error deleting scale."
    static let errorEditingScale = "Error editing scale."
    static let nicknameUpdated = "Nickname updated."
    static let deleted = "Deleted"
    static let saved = "Saved"
    static let scaleNameUpdated = "Scale name updated."
    static let copiedToClipboard = "Copied to clipboard"
    static let logsSent = "Logs sent to Greater Goods"
    static let synced = "Entries successfully resynced."
    static let resyncError = "Unable to resync entries. Please check your Wi-Fi connection and try again."
    static let resyncErrorTitle = "Not Connected to Wi-Fi"
    static let internetRequiredTitle = "Internet Required"
    static let internetRequiredMessage = "Internet required to connect Wi-Fi-scales"
    static let bluetoothRequiredTitle = "Bluetooth Required"
    static let bluetoothRequiredMessage = "Please turn on Bluetooth to delete users from the scale."
    static let genericError = "Sorry, something went wrong. Please try again later."
}

/// Constants for help modal strings used in the app
struct HelpStrings {
    static let question = "Have a question?"
    static let generalHelp = "We're here for you. Contact us and we'll be happy to help."
    static let gettingStartedGuide = "Getting Started Guide"
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
    static let leaveBlank = "must not leave blank"
    static let email = "must use a valid email"
    static let emailMaxLength = "email should not exceed 100 characters"
    static let passwordMaxLength = "password should not exceed 50 characters"
    static let passwordMinLength = "password must be 6 characters long"
    static let minLength = { (length: Int) in "minimum value should be \(length)" }
    static let maxLength = { (length: Int) in "maximum value should be \(length)" }
    static let min = { (value: Int) in "value must be at least \(value)" }
    static let max = { (value: Int) in "value must not exceed \(value)" }
    static let noWhiteSpace = "must not be left blank"
    static let futureDate = "future dates not accepted"
    static let passwordMatch = "both passwords must match"
    static let valueShouldNotBeEqual = "value should not be equal to starting weight"
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
    static let duplicate = "the scale can’t have duplicate user names"
    static let namePattern = "please enter a valid name"
    static let userNameUnavailable = "user name is unavailable"
}

/// Constants for input field labels used in the app
struct InputFieldLabels {
    static let firstName = "first name"
    static let lastName = "last name"
    static let userName = "user name"
    static let email = "email"
    static let password = "password"
    static let createNewPassword = "create new password"
    static let networkName = "network name"
    static let confirmPassword = "confirm password"
    static let confirmNewPassword = "confirm new password"
    static let currentPassword = "current password"
    static let startingWeight = "starting weight"
    static let startingWeightLabel: (Bool) -> String = { isKg in
        return isKg ? "starting weight (kg)" : "starting weight (lbs)"
    }
    static let goalWeightLabel: (Bool) -> String = { isKg in
        return isKg ? "goal weight (kg)" : "goal weight (lbs)"
    }
    static let goalWeight = "goal weight"
    static let useMetric = "Use Metric Units"
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
        static let returnButton = "Go back"
        static let exitButton = "Yes, exit"
    }

    struct ResetPasswordAlert {
        static let passwordResetTitle = "Password Reset"
        static let enterEmailMessage = "Enter your email"
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
        static let returnButton = "Go back"
        static let exitButton = "Yes, exit"
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

    struct ExitBtWifiSetupAlert {
        static let title = "Are you sure you want to exit?"
        static let preConnectionExitMessage = "The scale will not be connected."
        static let postConnectionExitMessage = "If you exit early, you may not be able to access some features until set up."
        static let wifiExitMessage = "Wi-Fi settings will not be updated."
        static let goBackButton = "Go Back"
        static let exitButton = "Exit"
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

    struct DeleteEntryAlert {
        static let title = "Delete Entry?"
        static let message = "Are you sure you want to delete your entry?"
        static let cancelButton = "Cancel"
        static let deleteButton = "Delete"
    }

    // MARK: - Goal Alerts
    struct GoalMetAlert {
        static let header = "Congratulations! You've hit your goal weight!"
        static let message = "Would you like to set a new goal or maintain this goal weight?"
        static let newGoal = "NEW GOAL"
        static let maintain = "MAINTAIN"
    }

    struct GoalLeaveAlert {
        static let message = "It looks like you’re moving away from your target weight. Do you want to set a new goal to get back on track?"
        static let no = "NO"
        static let yes = "YES"
    }

    struct knownScaleDiscoveredAlert {
        static let title = "Known Scale Discovered"
        static let message = "Weight Gurus sees a scale that is already set up. If you are trying to set up a second scale, make sure only one is turned on at a time."
        static let exitButton = "Exit"
    }

    struct PermissionAlerts {
        // MARK: - Bluetooth
        static let bluetoothDisabledTitle = "To turn on Bluetooth"
        static let bluetoothDisabledMessage = "1. Open Settings on your phone\n2. Tap Bluetooth\n3. Toggle Bluetooth on\n4. Tap Allow New Connections\n5. Return to this app"
        static let bluetoothAuthDisabledTitle = "Bluetooth Access is Disabled"
        static let bluetoothAuthDisabledMessage = "To sync with your device, please enable Bluetooth access."

        // MARK: - Location
        static let locationDisabledTitle = "To turn on Location Services"
        static let locationDisabledMessage = "1. Open your iPhone Settings\n2. Tap Privacy & Security\n3. Tap Location Settings\n4. Toggle Location Services on\n5. Return to this app"
        static let locationAuthTitle = "Weight Gurus needs location permissions."
        static let locationAuthMessage = "Apple requires this for Wi-Fi connections. Weight Gurus doesn't store this data. On the next screen, select 'Allow while using app.' Choosing 'Allow once' will block future connections with the scale."
        static let locationWhyTitle = "Why are we asking your location permission?"
        static let locationWhyMessage = "iOS requires location permission to connect with nearby Bluetooth devices. We don't track or store your location—it's just an Apple requirement for device setup."

        // MARK: - WiFi
        static let wifiDisabledTitle = "To turn on Wi-Fi"
        static let wifiDisabledMessage = "1. Open Settings on your phone\n2. Tap Wi-Fi\n3. Toggle Wi-Fi on\n4. Return to this app"

        // MARK: - Camera
        static let cameraDisabledTitle = "Camera Access is Disabled"
        static let cameraDisabledMessage = "You will not be able to pair or sync with your AppSync scale. Please enable Camera access in Settings."

        // MARK: - Notification
        static let notificationDisabledTitle = "Notifications are disabled!"
        static let notificationDisabledMessage = "Notification permissions have been turned off. Enable notifications to receive updates from your Wi-Fi scale."
    }

    struct ConfirmRestoreAlert {
        static let title = "Confirm Account Restore"
        static let message = "Restoring this account will reconnect me.health and the scale. Scale settings may be reset."
        static let restoreButton = "Restore"
        static let backButton = "Back"
    }

    struct EnableBodyMetricsAlert {
        static let title = "Enable Body Metrics"
        static let message = "This will disable Weight Only Mode for one session, and all body metrics will be collected."
        static let enableButton = "Enable"
        static let cancelButton = "Cancel"
    }

    struct DisableWeightOnlyModeAlert {
        static let title = "Are you sure?"
        static let message = "The alert will be dismissed for this session. Visit scale settings to enable and/or review users."
        static let dismissButton = "Dismiss"
        static let cancelButton = "Cancel"
    }

    struct ConfirmDeleteUserAlert {
        static let title = "Are you sure you want to delete?"
        static let message: (String) -> String = { userName in
            "Deleting \(userName) will remove them as a user of the scale and they’ll need to reconnect."
        }
        static let deleteButton = "Delete"
        static let goBackButton = "Go Back"
    }

    struct SkipWifiStepAlert {
        static let title = "Are you sure you want to skip Wi-Fi?"
        static let message = "After setup, find additional WiFi settings or the MAC Address via scale settings."
        static let skipButton = "Skip"
        static let goBackButton = "BACK"
    }

    struct PermissionDisabledAlert {
        static let title = "Unable to scan devices!"
        static let message = "One or more required permissions or device services may be disabled. Visit the App Permissions screen in the Settings tab to check and enable the app’s permissions access."
        static let dismissButton = "DISMISS"
        static let appPermissionButton = "APP PERMISSION"
    }

    struct DataClearingAlert {
        static let successHeader = "Your data has been cleared."
        static let successMessage = "To complete this, you will need to close your app."
        static let errorHeader = "Something may have gone wrong."
        static let errorMessage = "Please restart your app and try again. If the problem continues, you can clear your data by deleting the app."
        static let okButton = "OK"
    }

    struct SkipPermissionsAlert {
        static let title = "Are you sure you want to skip?"
        static let message = "Doing so makes necessary a more in-depth, time consuming setup process."
        static let skipButton = "YES, SKIP"
        static let goBackButton = "GO BACK"
    }

    struct UpdateAccountFailedAlert {
        static let title = "Update Can't Be Saved"
        static let message = "The scale is currently busy. Wait a few moments and try again."
        static let tryAgainButton = "Try Again"
        static let cancelButton = "Cancel"
    }
    struct WeightOnlyModeAlert {
        static let title = "Weight Only Mode"
        static let message = "This will disable Weight Only Mode for one session, and all body metrics will be collected."
        static let enableButton = "Enable"
    }
    struct ReconnectDeviceAlert {
        static let header = "Scale is at Its User Limit"
        static let message = "Your connection was deactivated by another user. Reconnect now or delete the scale from your account by visiting scale settings."
        static let reconnectButton = "Reconnect"
        static let cancelButton = "Cancel"
    }
    
    struct DuplicateUserAlert {
        static let header = "Duplicate Scale User Name"
        static let message = "Reconnect the scale with a new user name."
        static let reconnectButton = "Reconnect"
        static let cancelButton = "Cancel"
    }
    
    struct UpdatesPendingAlert {
        static let title = "Updates Pending..."
        static let message = "Scale settings can't be updated at this time. Weight Gurus will save changes and update the scale next time it connects."
        static let okButton = "OK"
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
    static let deletingScale = "Deleting scale..."
    static let deletingEntry = "Deleting entry..."
    static let savingScale = "Saving scale..."
    static let exiting = "Exiting..."
    static let sendingLogs = "Sending logs..."
    static let resync = "Resyncing Data..."
    static let pleaseWait = "Please wait..."
    /// Loader shown while retrieving the scale's MAC address.
    static let gettingMacAddress = "Getting MAC address..."
    static let updatingMode = "Updating Mode..."
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

struct FirmwareUpdateStrings {
    static let title = "Software Update"
    static let version = "Version"
    static let alreadyUpdated = "You are already on the latest version."
    static let now = "Now"
    static let schedule = "Schedule"
    static let message = "A new firmware version"
    static let message1 = "is available. You can either upgrade now or schedule to upgrade at a later time."
    static let nowDetails = "A new version is available with performance and stability improvement."
    static let message2 = "We will start the update immediately. Keep the scale powered."
    static let upgrade = "Upgrade"
    static let upgradeNow = "Upgrade Now"
    static let updatingFirmware = "Updating firmware..."
    static let updateTriggered = "Firmware update requested"
    static let date = "Date"
}



/// Constants for App Assets used in the app
struct AppAssets {
    static let eyeOpen = "eyeOpen"
    static let eyeClosed = "eyeSlash"
    static let closeCircle = "closeCircle"
    static let helpCircle = "helpCircle"
    static let xmark = "xmark"
    static let xmarkSmall = "xmarkSmall"
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
    static let weightOnlyModeAlertIcon = "weightOnlyMode"
    static let weightOnlyModeAlertIconLarge = "weightOnlyModeLarge"
    static let scaleIcon = "scaleIcon"
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
    static let ggLogoLight = "ggLogoLight"
    static let checkMarkLarge = "checkMarkLarge"
    static let appSyncTab = "appSyncTab"
    static let close = "close"
    static let grid = "grid"
    static let scale = "scale"
    static let metric = "metric"
    static let export = "export"

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

    // MARK: - BtWifi Setup
    static let userInfoScreen = "0412UserInfoScreen"
    static let wgLogo = "wgLogo"
    static let stepOnGif = "stepOnGif"
    static let btStepOnGif = "btStepOnGif"
    static let accuCheck = "accuCheck"
    static let accuCheckTickLarge = "accuCheckTickLarge"
    static let accuCheckTickLargeDark = "accuCheckTickLargeDark"

    
    // MARK: - Error Code Images
    /// Generates error code image asset names based on SKU, error code, filled state, and theme
    /// - Parameters:
    ///   - sku: Scale SKU (e.g., "0384", "0396")
    ///   - errorCode: Error code raw value (e.g., "t163", "t164")
    ///   - isFilled: Whether to use filled or outlined version
    ///   - isDarkMode: Whether to use dark mode variant
    /// - Returns: Asset name string
    static func errorCodeImageName(sku: String, errorCode: String, isFilled: Bool, isDarkMode: Bool) -> String {
        let fillType = isFilled ? "Filled" : "Outlined"
        let themeVariant = isDarkMode ? "_dark" : ""
        return "\(sku)_Err_\(errorCode)_\(fillType)\(themeVariant)"
    }
    
    /// Generates AP mode image asset names based on SKU, filled state, and theme
    /// - Parameters:
    ///   - sku: Scale SKU (e.g., "0384", "0396")
    ///   - isFilled: Whether to use filled or outlined version
    ///   - isDarkMode: Whether to use dark mode variant
    /// - Returns: Asset name string
    static func apModeImageName(sku: String, isFilled: Bool, isDarkMode: Bool) -> String {
        let fillType = isFilled ? "Filled" : "Outlined"
        let themeVariant = isDarkMode ? "_dark" : ""
        return "\(sku)_AP_\(fillType)\(themeVariant)"
    }
    
    /// Generates complete setup image asset names based on SKU, filled state, and theme
    /// - Parameters:
    ///   - isFilled: Whether to use filled or outlined version
    ///   - isDarkMode: Whether to use dark mode variant
    /// - Returns: Asset name string
    static func completeImageName(isFilled: Bool, isDarkMode: Bool) -> String {
        let fillType = isFilled ? "Filled" : "Outlined"
        let themeVariant = isDarkMode ? "_dark" : ""
        return "0396_Complete_\(fillType)\(themeVariant)"
    }
    
    /// Generates step on image asset names based on SKU, filled state, and theme
    /// - Parameters:
    ///   - sku: Scale SKU (e.g., "0396")
    ///   - isFilled: Whether to use filled or outlined version
    ///   - isDarkMode: Whether to use dark mode variant
    /// - Returns: Asset name string
    static func stepOnImageName(sku: String, isFilled: Bool, isDarkMode: Bool) -> String {
        let fillType = isFilled ? "Filled" : "Outlined"
        let themeVariant = isDarkMode ? "_dark" : ""
        return "\(sku)_StepOn_\(fillType)\(themeVariant)"
    }
    
    // MARK: - Bluetooth Setup
    static let setupPressUnitButtonGifName: (String) -> String = { sku in
        "\(sku)-Setup-PressUnitButton"
    }
    static let setupSetUserNumberGifName: (String) -> String = { sku in
        "\(sku)-Setup-SetUserNumber"
    }

    // MARK: - WiFi Setup
    static let wifiStepOnGif: (String) -> String = { sku in
        sku == "0384" ? "0384-Sync" : "0396-Sync"
    }
    static let wifiApMode: (String) -> String = { sku in
        sku == "0384" ? "0384-Setup-Ap" : "0396-Setup-Ap"
    }
    static let wifiSetupComplete = "0396-Setup-Complete"
    /// Generates WiFi setup complete GIF asset names based on user number, filled state, and theme
    /// - Parameters:
    ///   - user: User number (1-8)
    ///   - isFilled: Whether to use filled or outlined version
    ///   - isDarkMode: Whether to use dark mode variant
    /// - Returns: Asset name string for the GIF
    static func wifiSetupCompleteGifName(user: Int, isFilled: Bool, isDarkMode: Bool) -> String {
        let fillType = isFilled ? "Filled" : "Outlined"
        let themeVariant = isDarkMode ? "_dark" : ""
        return "0384_U\(user)_\(fillType)\(themeVariant)"
    }
    
    /// Legacy function for backward compatibility - generates step on GIF name
    /// Parameters:
    ///   - isFilled: Whether to use filled or outlined version
    ///   - isDarkMode: Whether to use dark mode variant
    /// - Returns: Asset name string for the GIF
    static func wifiSetupStepOnGifName( isFilled: Bool, isDarkMode: Bool) -> String {
        let fillType = isFilled ? "Filled" : "Outlined"
        let themeVariant = isDarkMode ? "_dark" : ""
        return "0384_StepOn_\(fillType)\(themeVariant)"
    }

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

/// Constants used in the Set A Goal card
struct SetGoalCardStrings {
    static let title = "Set a Goal"
    static let description = "A great tool for tracking your journey that can always be changed in the app settings."
    static let buttonTitle = "LET'S DO IT"
}

