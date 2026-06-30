package com.dmdbrands.gurus.weight.features.login.strings

/**
 * Strings for the Login screen.
 */
object LoginStrings {
    const val WelcomeBack = "Welcome back!"
    const val EmailLabel = "email"
    const val PasswordLabel = "password"
    const val LoginButton = "LOG IN"
    const val ForgotPassword = "FORGOT PASSWORD?"
    const val TermsAgreement = "by logging in, you are agreeing to our"
    const val TermsOfService = "TERMS OF SERVICE"
    const val PrivacyPolicy = "PRIVACY POLICY"
    const val And = "&"
    const val TermsOfServiceUrl = "https://greatergoods.com/legal/weight-gurus-tos"
    const val PrivacyPolicyUrl = "https://greatergoods.com/legal/privacy-policy"
    const val LoaderMessage = "Logging in..."

    // region Accessibility (TalkBack)
    /** Label for the close (X) navigation icon button. */
    const val accCloseLabel = "Close"

    /** Label for the help (?) action icon button. */
    const val accHelpLabel = "Help"
    // endregion

    object ForgotPasswordDialogStrings {
        const val Title = "Password Reset"
        const val Subtitle = "Enter your email below."
        const val EmailLabel = "email"
        const val SubmitButton = "SUBMIT"
        const val CancelButton = "CANCEL"
        const val LoaderMessage = "Sending email..."
    }

    object Errors {
        const val emailBlank = "this field is required"
        const val invalidemail = "must use a valid email"
        const val maxLengthEmail = "email should not exceed 100 characters"
        const val passwordlen = "password must be 6 characters long"
        const val maxLengthPassword = "password should not exceed 50 characters"
    }
}
