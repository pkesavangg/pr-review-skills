package com.greatergoods.meapp.features.common.strings

object ToastStrings {
    object Success {

        object ResetPasswordSuccess {
            const val Header = "Success!"
            fun Message(email: String = "") =
                "An email with a link to reset your password has been sent to $email." +
                    " The link will be valid for the next 10 minutes."
        }

        object UpdateProfileSuccess {
            const val Header = "Success!"
            const val Message = "Your profile saved successfully"
        }

        object ChangePasswordSuccess {
            const val Header = "Success!"
            const val Message = "Password updated."
        }
        
        object AccountSwitchSuccess {
            fun Message(accountName: String) = "Switched to $accountName"
        }
    }

    object Error {
        object LoginError {
            const val Header = "Login Error"
            const val MessageGeneric =
                "Something went wrong. Please try again. If the problem continues, contact customer service."
            const val MessageNotAuth = "Your email or password is incorrect. Please try again."
            const val MessageNoConn = "No connection detected. Please make sure you have internet access and try again."
            const val MessageServError =
                "Unable to reach the Greater Goods servers. The issue is probably on our end. Try again later, but if the problem continues, contact customer service."
        }

        object ResetPasswordError {
            const val Header = "Password Reset Error"
            const val Message = "Something went wrong. Please try again. " +
                "If the problem continues, contact customer service."
        }

        object UpdateProfileError {
            const val Header = "Profile Update Error"
            const val MessageGeneric = "Something went wrong. Please try again. " +
                "If the problem continues, contact customer service."
            const val MessageNotAuth = "Your session has expired. Please log in again."
            const val MessageNoConn = "No connection detected. Please make sure you have internet access and try again."
            const val MessageServError =
                "Unable to reach the Greater Goods servers. The issue is probably on our end. " +
                    "Try again later, but if the problem continues, contact customer service."
        }

        object ChangePasswordError {
            const val Header = "Password Change Error"
            const val Message = "Error updating password. Please try again. " +
                "If the problem continues, contact customer service."
        }

        object NetworkError {
            const val Message = "Unable to find a network connection at this time. Please try again later."
        }
    }
}
