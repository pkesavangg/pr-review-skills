package com.dmdbrands.gurus.weight.features.ScaleSetup.strings

import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState

object BabyScaleSetupStrings {
  object WakeupScale {
    fun Title(connectionState: ConnectionState) =
      if (connectionState is ConnectionState.Failed) "Unable to connect to your device"
      else "Turn on your Scale"

    fun Subtitle(connectionState: ConnectionState) =
      if (connectionState is ConnectionState.Failed)
        "This may be caused by interference from another Bluetooth device. Tap PAIR AGAIN to try again or contact customer service."
      else "Searching..."
  }

  object ConnectingBluetooth {
    fun Title(connectionState: ConnectionState) =
      if (connectionState is ConnectionState.Failed) "Unable to connect to your device"
      else "Connecting to Bluetooth"
  }

  object ScaleName {
    const val Title = "Give your scale a name."
    const val Hint = "nickname"
  }

  object PairedSuccess {
    const val Title = "You're Paired!"
    const val Subtitle = "Add a baby profile to personalize weight tracking. You can do this later from Settings."
  }

  object BabyProfileForm {
    const val Title = "Complete Baby Profile"
    const val Subtitle = "Let's add a baby. This helps personalize your baby's scale readings."
    const val NameHint = "name"
    const val BirthdayHint = "baby's birthday"
    const val SexHint = "Biological Sex"
    const val BirthLengthHint = "birth length"
    const val BirthWeightHint = "birth weight"
  }

  object BabyList {
    const val Title = "Your Baby Has Been Added!"
    const val AddBabyButton = "ADD A BABY"
  }

  object SkipDialog {
    const val Title = "Skip Baby Profile?"
    const val Message = "Setup is complete. You can add a baby profile later from Settings."
    const val Cancel = "CANCEL"
    const val FinishSetup = "FINISH SETUP"
  }

  object SetupButtons {
    const val PairAgain = "PAIR AGAIN"
    const val Continue = "CONTINUE"
    const val Finish = "FINISH"
    const val Save = "SAVE"
  }
}
