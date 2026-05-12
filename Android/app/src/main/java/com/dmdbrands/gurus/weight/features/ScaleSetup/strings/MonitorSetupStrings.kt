package com.dmdbrands.gurus.weight.features.ScaleSetup.strings

/**
 * Unified strings for BPM monitor setup (A3 and A6 protocols).
 */
object MonitorSetupStrings {
  const val HeaderTitle = "Blood Pressure Monitor Setup"
  const val DefaultMonitorNickname = "Blood Pressure Monitor"

  fun MonitorNameBySku(sku: String): String = when (sku) {
    "0603" -> "Smart Wrist Blood Pressure Monitor"
    "0604" -> "Smart Blood Pressure Monitor"
    "0634" -> "Smart Pro-Series Blood Pressure Monitor"
    "0636" -> "All-In-One Bluetooth Blood Pressure Monitor"
    "0661" -> "Smart Blood Pressure Monitor"
    "0663" -> "Smart Blood Pressure Monitor"
    else -> DefaultMonitorNickname
  }

  fun DefaultScaleNickname(sku: String) = "Smart Scale $sku"

  fun ModelNumber(sku: String) = "Model #$sku"

  object MonitorDetail {
    const val Description =
      "If you have any trouble setting up your monitor, you can connect with our team via the help button in the top right."
  }

  object UserSelection {
    const val Title = "Which user do you want to be?"
    const val Subtitle = "Make sure to pick a user no one else is using."
  }

  object UserConfirmation {
    const val Title = "Set the monitor to"

    fun Subtitle(sku: String): String = when (sku) {
      "0603" -> "Change the user by tapping the USER button."
      "0604" -> "With the monitor off, toggle the user switch to change users."
      "0634" -> "Change the user by tapping the A/B button."
      "0636" -> "Press the A/B button to switch between users, confirm your selection by pressing SET, and wait for the monitor to enter sleep mode."
      else -> "Change the user by tapping the A/B button."
    }
  }

  object PowerSwitch {
    const val Title = "Set the monitor's power switch to ON."
    const val Subtitle =
      "Keep the switch set to ON unless you need to reset the monitor. To enter sleep mode, press SET or let the monitor enter it automatically after one minute of non-use."
    const val Note =
      "Please note: The switch on some monitors may read ON/OFF, but the settings remain the same."
  }

  object MonitorOff {
    const val Title = "Press the START/STOP button to confirm user selection."

    fun Subtitle(sku: String): String = when (sku) {
      "0603" -> "The monitor will confirm and turn off."
      "0634" -> "The monitor will say done and turn off."
      else -> "The monitor will confirm and turn off."
    }
  }

  object MemorySelection {
    fun Title(sku: String): String = when (sku) {
      "0636" -> "With the monitor screen blank, press and hold the MEM button."
      "0604" -> "With the monitor off, press and hold the UP arrow button."
      "0661" -> "With the monitor off, press and hold the UP arrow button."
      else -> "Press and hold the MEM button."
    }

    fun Subtitle(sku: String): String = when (sku) {
      "0604", "0661" -> "Hold until \"SYNC\" starts flashing on the screen of the monitor."
      else -> "Hold until \"PAIR\" starts flashing on the screen of the monitor."
    }
  }

  object Connectivity {
    const val SearchingTitle = "Searching for monitor..."
    const val SuccessSubtitle = "We're in sync! Now tap NEXT to finish up and take a measurement."
    const val FailedTitle = "Unable to Connect"
    const val FailedSubtitle =
      "This may be caused by interference from another Bluetooth device. Tap RETRY to try again or contact customer service."
  }

  object MonitorNickname {
    const val Title = "What should this monitor be called?"
    const val Label = "NICKNAME:"
  }

  // ── A6 companion scale steps ─────────────────────────────────────────────

  object ScaleIntro {
    const val Title = "Now let's pair your companion scale."

    fun Subtitle(sku: String): String = when (sku) {
      "0661" -> "Your monitor works best with the 0661/0667 scale paired to the same user. Tap NEXT to pair it now."
      "0663" -> "Your monitor works best with the 0663/0665 scale paired to the same user. Tap NEXT to pair it now."
      else -> "Tap NEXT to pair your companion scale now."
    }
  }

  /**
   * Strings for the [MonitorSetupStep.SCALE_PAIRING_INSTRUCTION] step.
   *
   * This step is instructional only — companion scale BLE pairing is performed via
   * the standard Add-Scale flow, never inside the BPM wizard. See enum doc.
   */
  object ScalePairingInstruction {
    const val Title = "Pair your companion scale separately."
    const val Subtitle =
      "Your companion scale is set up like any other scale. " +
        "Open Add Device from the home screen and follow the scale setup flow. " +
        "Tap NEXT to continue."
  }

  // ── Success screen ─────────────────────────────────────────────────────

  object SuccessScreen {
    fun Title(isA6: Boolean, hasSkippedScalePairing: Boolean): String = when {
      !isA6 -> "Your monitor is paired!"
      hasSkippedScalePairing -> "Your monitor is paired!"
      else -> "Setup complete!"
    }

    fun Subtitle(
      isA6: Boolean,
      monitorNickname: String,
      scaleNickname: String,
      hasSkippedScalePairing: Boolean,
    ): String = when {
      hasSkippedScalePairing -> "$monitorNickname is now paired. You can pair your companion scale later from the Add Device screen. You can also check out a tutorial for using your monitor."
      isA6 -> "$monitorNickname and $scaleNickname are now paired and ready to use. You can also check out a tutorial for using your monitor."
      else -> "You can wrap things up by tapping FINISH, or check out a tutorial for using your monitor."
    }

    const val TutorialLinkText = "check out a tutorial"
  }

  // ── Instruction screens ────────────────────────────────────────────────

  object InstructionCuff {
    const val Title = "Let's take a measurement."

    fun Subtitle(sku: String): String = when (sku) {
      "0603" -> "Put on the wrist cuff and make sure it's lined up properly. Sit down with your feet flat on the floor and your arm angled so that the monitor is level with your heart. Press the START button to turn on the monitor."
      "0636" -> "Put on the arm cuff, making sure it's lined up properly. Sit down with your feet flat on the floor and your arm resting on the table in front of you. Make sure the switch on top of your monitor is set to ON."
      else -> "Put on the arm cuff, making sure it's lined up properly. Sit down with your feet flat on the floor and your arm resting on the table in front of you. Press the START button to turn on the monitor."
    }
  }

  object InstructionStart {
    const val Title = "Relax and take a deep breath."

    fun Subtitle(sku: String): String = when (sku) {
      "0636" -> "Press SET to confirm the correct user is selected. Then press the SET button again to begin taking your measurement."
      else -> "Then press the START button again to begin taking your measurement."
    }
  }

  object SetupCompleted {
    const val Title = "Your measurement has been recorded!"
    const val Subtitle =
      "That's it! When you want to record your next measurement simply open the app, put the cuff on, and press start on the monitor."
  }

  // ── Dialogs ────────────────────────────────────────────────────────────

  object ConfirmPairDialog {
    const val Title = "Caution: User Already Paired"
    const val Message =
      "This monitor is already paired under the same User. By continuing the connection will be reset."
    const val ConfirmButton = "Continue"
    const val CancelButton = "Cancel"
  }

  object ConfirmDifferentUserPairDialog {
    const val Title = "Caution: Different User Detected"

    fun Message(userLabel: String) =
      "This device is already assigned to User $userLabel. Would you like to replace them?"

    const val ReplaceButton = "Replace User"
    const val CancelButton = "Cancel"
  }

  object DifferentUserDialog {
    const val Title = "Wrong User Detected"
    const val Message =
      "The monitor reported a different user than what was selected. Please make sure the correct user is set on the monitor and try again."
    const val ChangeUserButton = "Change User"
    const val DismissButton = "Dismiss"
  }

  object SkipScaleDialog {
    const val Title = "Skip Companion Scale?"
    const val Message =
      "Your monitor works best with its companion scale. You can pair the scale later from the Add Device screen."
    const val SkipButton = "SKIP"
    const val CancelButton = "Go Back"
  }

  object RetryAlert {
    const val Title = "Unable to Connect"
    const val Message =
      "This may be caused by interference from another Bluetooth device. Try again or contact customer service."
    const val RetryButton = "Retry"
    const val DismissButton = "Dismiss"
  }

  object ExitSetupAlert {
    const val Title = "Confirm"
    const val Message = "Are you sure you want to cancel setup?"
  }
}
