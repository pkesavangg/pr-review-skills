package com.greatergoods.meapp.features.common.strings

object ToastStrings {
    object Success {

        object ResetPasswordSuccess {
            const val Header = "Success!"
            fun Message(email: String = "") =
                "An email with a link to reset your password has been sent to $email." +
                    " The link will be valid for the next 10 minutes."
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
    }
}
