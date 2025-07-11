package com.greatergoods.meapp.features.ScaleSetup.strings

/**
 * Strings for Scale Setup screens.
 */
object ScaleSetupStrings {
  fun Header(sku: String) = "Scale Setup - $sku"

  const val backButton = "back"
  const val nextButton = "next"
  const val FinishButton = "Finish"
  const val skipButton = "skip"

  object ScaleInfo {
    fun Title(sku: String) = "Model $sku"
    const val WifiScaleButtonText = "Get your scale’s MAC address"
    const val Subtitle =
      "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right."
  }

  object ExitSetupAlert {
    const val Title = "Are you sure you want to exit?"
    fun Message(isConnected: Boolean) = if(isConnected)
      "If you exit early, you may not be able to \naccess some features until set up."
    else "The scale will not be connected."
    const val Back = "Back"
    const val Exit = "Exit"
  }
}
