package com.greatergoods.meapp.features.ScaleSetup.strings

object BtScaleSetupStrings {
  object ChooseUser {
    const val Title = "Choose Your User Number"
    const val Message = "Select a user number (1-8) for your scale."
  }

  object PairingMode {
    const val Title = "Press and hold the UNIT on the back of your scale"
    const val Subtitle = "Release the button when the animation on your scales's screen begins. It will then show brackets, and the scale will fall asleep."
    const val LoaderText = "Pairing"
  }

  object SetDeviceUser {
    fun Title(userString: String?) = "Set your user number on the scale"
    fun Subtitle(userString: String?) = "Press the SET button on the front of the scale and then use the arrow buttons to find your user number(${userString ?: ""})"
  }

  object StepOn {
    const val Title = "Time to weigh in!"
    const val Subtitle = "Set your scale on a hard, flat surface, step on, and wait for your results."
    const val LoaderText = "Syncing"
  }

  object SetupFinished {
    const val Title = "Setup Complete!"
    const val Subtitle = "Your scale is now connected and ready to use."
  }
}
