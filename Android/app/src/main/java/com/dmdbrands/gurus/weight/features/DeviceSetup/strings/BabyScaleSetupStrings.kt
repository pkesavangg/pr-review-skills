package com.dmdbrands.gurus.weight.features.DeviceSetup.strings

import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState

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

  object DeviceName {
    const val Title = "Give your scale a name."
    const val Hint = "nickname"
  }

  /** Shown when the discovered baby scale is already paired to this account (Figma 33013-205573). */
  object AlreadyPaired {
    const val Title = "Caution: Scale Already Connected"
    const val Message = "This baby scale is already paired to your profile. Please use a different scale to continue setup."
    const val Exit = "OK, EXIT"
  }

  object PairedSuccess {
    const val Title = "You're Paired!"
    const val Subtitle = "Add a baby profile to personalize weight tracking. You can do this later from Settings."
  }

  object SetupComplete {
    const val Title = "You're Done!"
    const val Subtitle = "Setup complete. Your scale is ready. Start tracking on the dashboard."
  }

  /** Loader shown while the scale (and any baby profiles) are saved on setup finish. */
  const val savingLoader = "Saving your scale..."

  object BabyProfileForm {
    const val Title = "Complete Baby Profile"
    const val Subtitle = "Let's add a baby. This helps personalize your baby's scale readings."
    const val NameHint = "name"
    const val BirthdayHint = "baby's birthday"
    const val SexHint = "Biological Sex"
    const val SexMale = "Male"
    const val SexFemale = "Female"
    const val SexOther = "Other"
    const val SexSelectContentDescription = "Select biological sex"
    const val BirthLengthHint = "birth length"
    const val BirthWeightHint = "birth weight"
  }

  object BabyList {
    const val Title = "Your Baby Has Been Added!"
    const val AddBabyButton = "ADD A BABY"
    const val EditContentDescription = "Edit baby"
    const val DeleteContentDescription = "Delete baby"
    fun BabyFallbackName(index: Int) = "Baby ${index + 1}"
    const val AvatarInitialFallback = "?"
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
  }

  // region Accessibility (TalkBack)
  /** Spoken description for the green check shown when the baby scale is paired. */
  const val accPairedImage = "Baby scale paired"

  /** Spoken description for the green check shown on the final "You're Done!" screen. */
  const val accDoneImage = "Setup complete"

  /** Spoken description for the animated loader shown while searching for the scale. */
  const val accSearchingLoader = "Searching for scale"
  // endregion
}
