package com.greatergoods.meapp.features.ScaleSetup.strings

object WifiScaleSetupStrings {

    object NetworkFormSlide {
        const val Title = "What network should your scale connect to?"
        const val Subtitle =
            "If you have multiple Wi-Fi networks, use the 2GHZ network closest to your scale."
    }

    object ActivateScaleSlide {
        const val Title = "Activate pairing mode."
        const val Message =
            "On the back of your scale, press and hold the UNIT button. When SET UP 1 appears on the scale’s screen, tap NEXT."
    }

    object TroubleshootingSlide {
        const val Title = "Troubleshooting - other"
        const val Message =
            "Exit the setup and try again from the start. If this doesn't work out for you, give us a call and we'll work through it together."
    }

    object SwitchWifi {
        const val Title = "Switch your wi-fi network"
        const val Message =
            "In your phone's wi-fi settings change the connected network to: gg_SmartScale_33."
        const val ChangeNetwork = "Change your network"

    }

    object MacAddress {
        const val Title = "The MAC address of your scale is:"
        const val Message = ""
    }

    object Note {
        const val label = "network name"
        const val NetworkMessage =
            "Your phone should stay connected to the chosen 2GHZ network until setup is complete."
        const val NavigateToErrorSlide = "I see something else?"
    }

    object ChooseUser {
        const val Title = "Choose your user number."
        const val Message = "Pick one that no one else is using for this scale."
    }

    object WifiMode {
        const val Title =
            "Wait for the screen on your scale to change and tap the image that matches."
        const val Note = "It can take up to two minutes for your scale to enter AP mode."
    }

    object Error {
        const val Title = "Tap the screen you see:"
        const val Message = "Select the error message that you see on your scale and then tap NEXT."
    }

    object setupFinished {
    const val Title = "Your scale is paired and ready to go!"
    const val Message = "Next time you weigh in, the results will automatically be sent to Weight Gurus."
    const val MacTitle = "The MAC address of your scale is:"
  }
}
