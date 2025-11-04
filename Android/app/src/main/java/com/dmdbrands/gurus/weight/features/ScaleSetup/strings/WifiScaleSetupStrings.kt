package com.dmdbrands.gurus.weight.features.ScaleSetup.strings

object WifiScaleSetupStrings {

  object NetworkFormSlide {
    const val Title = "What network should your scale connect to?"
    const val Subtitle =
      "If you have multiple Wi-Fi networks, use the 2.4 GHZ network closest to your scale."
    const val NetworkName = "network name"
    const val Password = "password"
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
    const val goToWifiSettings = "Go to Wi-Fi settings"
  }

  object MacAddress {
    const val Title = "The MAC address of your scale is:"
    const val Message = ""
  }

  object Note {
    const val label = "network name"
    const val NetworkMessage =
      "Your phone should stay connected to the chosen 2.4 GHZ network until setup is complete."
    const val NavigateToErrorSlide = "I see something else?"
  }

  object ChooseUser {
    const val Title = "Choose your user number."
    const val Message = "Pick one that no one else is using for this scale."
  }

  object WifiMode {
    const val Title =
      "Wait for the screen on your scale to change and tap the image that matches."
    const val ApNote = "It can take up to two minutes for your scale to enter AP mode."
    const val CommonNote = "This can take up to two minutes."
  }

  object Error {
    const val Title = "Tap the screen you see:"
    const val Message = "Select the error message that you see on your scale and then tap NEXT."
  }

  object ErrorDetail {
    const val Troubleshooting = "Troubleshooting"

    // T204 error messages
    const val T204a = "It seems like your scale is having trouble communicating with your Wi-Fi network. " +
      "The best way to fix it is to reset the Wi- Fi chip in your scale."
    const val T204b = "To do this, start the scale setup process over and connect to another 2.4 GHz network  " +
      "when the app asks for your wi- fi network name and password.You can try using a phone as a  " +
      "hotspot or someone else’s Wi- Fi network (friend, family, neighbor, etc)."
    const val T204c = "If the scale connects fully OR simply doesn’t get the t204, we should be back in business  " +
      "and can try to connect on your original network again."

    // T205 error messages
    const val T205a =
      "This is an issue with some phones and operating systems. First, try turning off your phone's  " +
        "data by putting it in Airplane Mode with Wi - Fi enabled and retry the setup."
    const val T205b = "If the problem continues, download the app on another device and try to pair with that. ' " +
      "Even if you connect the scale to your Wi- Fi through another device you’ll still be able to  " +
      "track your weight on your own phone."

    // T163, T206, T323 error messages (numbered list)
    const val T163a =
      "Make sure you connect to a 2.4 GHz Wi-Fi network — typically the one WITHOUT “_5G” at the end." +
        "You may need to forget the 5G network during setup if your phone continues to choose it by default."
    const val T163b = "Check your router — the scale cannot pair with some Xfinity XFI, Cox, and Arris routers."
    const val T163c =
      "The router and scale may be too far apart. Move the scale closer to the router and try connecting again."
    const val T163d = "If you still can't connect or have one of the routers listed ," +
      "tap the \"?\" in the top right corner to contact our customer service team, and we'll be happy to help."

    // T164, T315, T325 error messages
    const val T164a =
      "Are you on a shared/community Wi-Fi network? If yes, and you happen to know who is in control of your  " +
        "network, ask them to whitelist, or give access to, your scale’s MAC address."
    const val T164b =
      "To find your scales MAC address tap the button below and go through the MAC address search process."

    // T165 error message
    const val T165 =
      "Check your personal information in the App Settings to make sure it is correct. Keep in mind the  " +
        "birthday must show you are over the age of 13 to comply with COPPA."

    // Other error message
    const val Other = "Exit the setup and try again from the start."

    // Default copy message
    const val Copy = "If this doesn’t work out for you, give us a call and we’ll work through it together."
  }

  object SetupFinished {
    const val Title = "Your scale is paired and ready to go!"
    const val Message = "Next time you weigh in, the results will automatically be sent to Weight Gurus."
    const val MacTitle = "The MAC address of your scale is:"
  }

  object StepOn {
    const val Title = "Let’s weigh in!"
    const val Message = "Set the scale on a hard flat surface. Step on and wait for your measurement. "
  }

  object ScaleCount {
    const val Title = "Wait as your scale counts to 4 and shows STEP ON, then tap NEXT"
    const val Message = "Set the scale on a hard flat surface. Step on and wait for your measurement. "
  }

  object WifiErrors {
    val ERROR_MESSAGES = mapOf(
      "t163" to "Error waiting for a connection to form. Make sure credentials are correct and that you are within range of your Wi-Fi router and try again.",
      "t164" to "The server not responding. Make sure you have an internet connection and try again.",
      "t165" to "The server not responding. Please try again later.",
      "t204" to "Error entering AP set-up mode. Please try again.",
      "t205" to "Your scale timed out before receiving the connection info it needs to connect to the internet. Please try again.",
      "t206" to "Error waiting for a connection to form. Make sure credentials are correct and that you are within range of your Wi-Fi router and try again.",
      "t315" to "The server not responding. Make sure you have an internet connection and try again.",
      "t323" to "Error forming a connection. Make sure you have an internet connection and try again.",
      "t325" to "The server not responding. Make sure you have an internet connection and try again.",
    )

    fun getErrorMessage(errorCode: String?): String? {
      return errorCode?.let { code ->
        ERROR_MESSAGES[code.lowercase()]
      }
    }
  }
}
