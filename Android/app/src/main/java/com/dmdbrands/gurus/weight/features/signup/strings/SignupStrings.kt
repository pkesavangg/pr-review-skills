package com.dmdbrands.gurus.weight.features.signup.strings

/**
 * String constants for the signup feature
 */
object SignupStrings {
  // NameStep strings
  const val nameStepTitle = "What's your name?"
  const val nameStepSubtitle =
    "We just need a first name or even a nickname. But rest assured we protect whatever info you give us."
  const val firstNameLabel = "first Name"
  const val lastNameLabel = "last Name"

  // BirthdayStep strings
  const val birthdayStepTitle = "When were you born?"
  const val birthdayStepSubtitle =
    "Your age helps us accurately calculate body metrics and healthy ranges. Note: You must be 13+ to make an account."
  const val birthdayLabel = "Date of Birth"

  // GenderStep strings
  const val genderStepTitle = "What is your biological sex?"
  const val genderStepSubtitle =
    "This is also important in determining body metrics and healthy ranges according to CDC and AHA health guidelines." +
      " Please choose what mostly closely reflects your body type and makes you most comfortable."
  const val genderMale = "Male"
  const val genderFemale = "Female"

  // Goal step strings
  const val goalStepTitle = "Set a goal!"
  const val goalStepSubtitle =
    "This can be helpful feature to utilize on your journey. Goals can always be changed in the app settings."
  const val goalStepMaintain = "Maintain"
  const val goalStepLoseGain = "Lose/Gain"
  const val goalStepUseMetric = "Use Metric Units"
  const val weightUnitLbs = "lbs"
  const val weightUnitKg = "kg"
  const val heightUnitFtIn = "Ft / In"
  const val heightUnitCm = "CM"

  // Goal step dynamic weight labels
  const val goalStepCurrentWeightDynamic = "starting weight (%s)"
  const val goalStepGoalWeightDynamic = "Goal weight (%s)"

  // HeightStep strings
  const val heightStepTitle = "How tall are you?"
  const val heightStepSubtitle = "Height is another factor that helps us provide you with the most accurate metrics."
  const val heightLabel = "Height"

  // EmailStep strings
  const val emailStepTitle = "What's your email?"
  const val emailStepSubtitle =
    "Be sure to use a valid email. You'll use this to login and it's where we'll send any reports."
  const val emailLabel = "email"

  // PasswordStep strings
  const val passwordStepTitle = "Create a password."
  const val passwordStepSubtitle = "Your password must be at least six characters."
  const val passwordLabel = "Password"
  const val confirmPasswordLabel = "confirm password"
  const val zipcodeLabel = "zipcode"
  const val passwordStepFooter = "by completing, you are agreeing to our"
  const val And = "&"
  const val TermsOfService = "TERMS OF SERVICE"
  const val PrivacyPolicy = "PRIVACY POLICY"
  const val TermsOfServiceUrl = "https://greatergoods.com/legal/weight-gurus-tos"
  const val PrivacyPolicyUrl = "https://greatergoods.com/legal/privacy-policy"

  // SignupScreen buttons
  const val backButton = "BACK"
  const val skipButton = "SKIP"
  const val nextButton = "NEXT"
  const val completeButton = "Complete"

  // Loader message
  const val LoaderMessage = "Creating your account..."

  // Exit confirmation dialog
  const val ExitConfirmTitle = "Confirm"
  const val ExitConfirmMessage = "Are you sure you want to leave?"
  const val ExitConfirmButton = "EXIT"
  const val ExitCancelButton = "RETURN"

  object Error {
    const val Header = "Signup Error"
    const val accountExistHeader = "Error creating account"
    const val MessageNotAuth = "Unable to create account. Please check your information and try again."
    const val MessageGeneric =
      "Something went wrong. Please try again. If the problem continues, contact customer service."
    const val MessageNoConn = "Unable to find a network connection at this time. Please try again later."
    const val accountExist = "Email address is already in use"
      const val blank = "this field is required"
      const val maxName = "maximum value should be 100"
      const val maxZipcode = "maximum value should be 20"
      const val required = "this field is required"
  }
}
