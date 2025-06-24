package com.greatergoods.meapp.features.profile.strings

/**
 * Strings for the Profile screen.
 */
object ProfileStrings {
    const val ScreenTitle = "User Profile"
    const val FirstNameLabel = "FIRST NAME"
    const val LastNameLabel = "LAST NAME"
    const val EmailLabel = "EMAIL"
    const val ZipcodeLabel = "ZIPCODE"
    const val BirthdayLabel = "BIRTHDAY"
    const val SaveButton = "SAVE"
    const val LoaderMessage = "Saving..."

    object Error {
        const val Header = "Profile Update Error"
        const val MessageGeneric = "Something went wrong. Please try again."
        const val MessageNoConn = "No connection detected. Please make sure you have internet access and try again."
        const val MessageValidation = "Please check your information and try again."
    }

    object Success {
        const val Header = "Profile Updated"
        const val Message = "Your profile has been updated successfully."
    }

    object ExitDialog {
        const val Title = "Confirm"
        const val Message = "You have unsaved changes. Are you sure you want to exit?"
        const val ConfirmText = "EXIT"
        const val CancelText = "RETURN"
    }
}
