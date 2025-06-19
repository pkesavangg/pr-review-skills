package com.greatergoods.meapp.features.login.strings

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

    object Error {
        const val Header = "Login Error"
        const val MessageGeneric =
            "Something went wrong. Please try again. If the problem continues, contact customer service."
        const val MessageNotAuth = "Your email or password is incorrect. Please try again."
        const val MessageNoConn = "No connection detected. Please make sure you have internet access and try again."
        const val MessageServError =
            "Unable to reach the Greater Goods servers. The issue is probably on our end. Try again later, but if the problem continues, contact customer service."
    }
}
