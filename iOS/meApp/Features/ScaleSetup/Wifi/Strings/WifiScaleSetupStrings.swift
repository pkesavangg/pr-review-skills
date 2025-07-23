//
//  WifiScaleSetupScreenView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 22/07/25.
//

import Foundation

struct WifiScaleSetupStrings {
    static let seeSomethingElse = "I see something else?"
    struct ActivatePairingModeViewStrings {
        static let title = "Activate Pairing Mode"
        static let description = "On the back of your scale, press and hold the UNIT button. When SET UP 1 appears on the scale’s screen, tap NEXT."
        static let boldWords = ["UNIT","SET UP 1"]
    }
    
    struct ErrorCodeSelectionViewStrings {
        static let title = "Tap the screen you see:"
        static let description = "Select the error message that you see on your scale and then tap NEXT."
        static let boldWords = ["NEXT"]
        static let err = "Err"
    }
    struct WifiPasswordViewStrings {
        static let title = "What network should your scale connect to?"
        static let description = "If you have multiple Wi-Fi networks, use the 2GHZ network closest to your scale."
        static let networkHasNoPassword = "Network has no password"
        static let note = "Your phone should stay connected to the chosen 2GHZ network until setup is complete."
    }
    struct UserConfirmationViewStrings {
        static let title: (Bool) -> String = { isApModeAlone in
            isApModeAlone ? "Wait for the scale to show it is in AP Mode." : "Wait for the screen on your scale to change and tap the image that matches."
        }
        static let subtitle = "Once your scale displays that it is in AP mode, tap NEXT."
        static let boldWords = ["NEXT"]
        static let note = "It can take up to two minutes for your scale to enter AP mode."
    }
    
    struct CopyMacAddressViewStrings {
        static let title = "The MAC address of your scale is:"
        static let copyMacAddress = "COPY MAC ADDRESS"
        static let note = "Record the address to share with your Wi-Fi network or IT department. Once the scale is whitelisted, return to scale setup to pair your scale."
    }
    
    struct ApModeConnectionViewStrings {
        static let title = "Switch your wi-fi network"
        static let description = "In your phone's wi-fi settings change the connected network to: gg_SmartScale_33."
        static let changeNetwork = "Change your Network"
    }
}
