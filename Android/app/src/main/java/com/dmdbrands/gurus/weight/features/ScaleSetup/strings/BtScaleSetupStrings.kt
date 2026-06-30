package com.dmdbrands.gurus.weight.features.ScaleSetup.strings

import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState

object BtScaleSetupStrings {
  object ChooseUser {
    const val Title = "Choose your user number."
    const val Message = "Pick one that no one else is using for this scale."
  }

  object PairingMode {
    const val Title = "Press and hold the UNIT on the back of your scale"
    const val Subtitle =
      "Release the button when the animation on your scale's screen begins. It will then show brackets, and the scale will fall asleep."

    fun PairModeText(state: ConnectionState) = when (state) {
      ConnectionState.Loading -> "Pairing"
      ConnectionState.Success -> "Paired!"
      ConnectionState.Failed.Error -> "Pair Again"
      is ConnectionState.Failed.ErrorWithMessage -> "Pair Again"
    }

    object RetryToast {
      const val Title = "Unable to connect to your device"
      const val Message = "This may be caused by interference from another Bluetooth device. " +
        "Tap PAIR AGAIN to try again or contact customer service."
    }
  }

  object SetDeviceUser {
    const val Title = "Set your user number on the scale"
    fun Subtitle(sku: String, userString: String?) = when (sku) {
      "0375" -> "Press the SEL button on the front of the scale and then use the arrow buttons to find your user number(${userString ?: ""})"
      else -> "Press the SET button on the front of the scale and then use the arrow buttons to find your user number(${userString ?: ""})"
    }
  }

  object StepOn {
    const val Title = "Time to weigh in!"
    const val Subtitle = "Set your scale on a hard, flat surface, step on, and wait for your results."

    fun StepOnText(state: ConnectionState) = when (state) {
      ConnectionState.Success -> "Synced!"
      else -> "Syncing"
    }
  }

  object SetupFinished {
    const val Title = "Your scale is paired and ready to go!"
    const val Subtitle = "Next time you weigh in, the results will automatically be sent to me.App."
  }

  object ConfirmPairDialog {
    const val Title = "Device already paired"
    fun Message(sku: String) = "The device with sku: $sku is already paired. Do you want to pair it again?"
    const val ConfirmButton = "PAIR"
    const val CancelButton = "RETURN"
  }


  object Loader {
    const val Exiting = "Exiting..."
  }

  /** Default nickname for a Bluetooth scale when product name is unavailable. */
  const val DefaultScaleNickname = "Bluetooth Smart Scale"

  /** Default nickname when replacing an existing paired scale and nickname is unknown. */
  const val DefaultScaleNicknameAlternate = "Smart Bluetooth Scale"
}
