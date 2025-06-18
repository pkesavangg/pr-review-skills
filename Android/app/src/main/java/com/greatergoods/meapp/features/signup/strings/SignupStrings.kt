package com.greatergoods.meapp.features.signup.strings

/**
 * String constants for the signup feature
 */
class SignupStrings {
    companion object {
        // NameStep strings
        const val nameStepTitle = "What's your name?"
        const val nameStepSubtitle = "We just need a first name or even a nickname. But rest assured we protect whatever info you give us."
        const val firstNameLabel = "first Name"
        const val lastNameLabel = "last Name"

        // BirthdayStep strings
        const val birthdayStepTitle = "When were you born?"
        const val birthdayStepSubtitle = "your age helps us accurately calculate body metrics and healthy ranges. Note: You must be 13+ to make an account."

        // GenderStep strings
        const val genderStepTitle = "What is your biological sex?"
        const val genderStepSubtitle = "This is also important in determining body metrics and healthy ranges according to CDC and AHA health guidelines." +
            " Please choose what most closely reflects your body type and makes you most comfortable."
        const val genderMale = "MALE"
        const val genderFemale = "FEMALE"

        // Goal step strings
        const val goalStepTitle = "Set a goal!"
        const val goalStepSubtitle = "This can be a helpful feature to utilize on your journey. Goals can always be changed in the app settings."
        const val goalStepMaintain = "Maintain"
        const val goalStepLoseGain = "Lose/Gain"
        const val goalStepCurrentWeight = "current weight (lbs)"
        const val goalStepGoalWeight = "goal weight (lbs)"

        // HeightStep strings
        const val heightStepTitle = "How tall are you?"
        const val heightStepSubtitle = "Height is another factor that helps us provide you with the most accurate metrics."
        const val heightLabel = "Height"

        // EmailStep strings
        const val emailStepTitle = "What's your email?"
        const val emailStepSubtitle = "Be sure to use a valid email. You'll use this to login and it's where we'll send any reports."
        const val emailLabel = "email"

        // PasswordStep strings
        const val passwordStepTitle = "Create a password."
        const val passwordStepSubtitle = "Your password must be at least six characters."
        const val passwordLabel = "Password"
        const val confirmPasswordLabel = "confirm password"
        const val zipcodeLabel = "zipcode"

        // SignupScreen buttons
        const val backButton = "BACK"
        const val skipButton = "SKIP"
        const val createAccountButton = "CREATE ACCOUNT"
        const val nextButton = "NEXT"
        const val completeButton = "COMPLETE"

    }
}
