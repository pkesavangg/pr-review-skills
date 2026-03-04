//
//  WifiScaleSetupScreenView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 22/07/25.
//

import Foundation

public struct WifiScaleSetupStrings {
    public static let seeSomethingElse = "I see something else?"
    public struct ActivatePairingModeViewStrings {
        public static let title = "Activate Pairing Mode"
        public static let description = "On the back of your scale, press and hold the UNIT button. "
            + "When SET UP 1 appears on the scale's screen, tap NEXT."
        public static let boldWords = ["UNIT", "SET UP 1"]
    }
    
    public struct ErrorCodeSelectionViewStrings {
        public static let title = "Tap the screen you see:"
        public static let description = "Select the error message that you see on your scale and then tap NEXT."
        public static let boldWords = ["NEXT"]
        public static let err = "Err"
    }
    struct WifiPasswordViewStrings {
        static let title = "What network should your scale connect to?"
        static let description = "Make sure your phone is connected to a 2.4 GHz Wi-Fi Network. "
            + "The network name you enter should match the one your phone is currently connected to."
        static let simpleDescription = "Make sure your phone is connected to a 2.4 GHz Wi-Fi Network."
        static let networkHasNoPassword = "Network has no password"
        static let note = "Your phone should stay connected to the chosen 2.4 GHz network until setup is complete."
    }
    struct UserConfirmationViewStrings {
        static let apModeConfirmationTitle = "Wait as your scale counts to 4 and shows STEP ON, then tap NEXT"
        static let title: (Bool) -> String = { isApModeAlone in
            isApModeAlone
                ? "Wait for the scale to show it is in AP Mode."
                : "Wait for the screen on your scale to change and tap the image that matches."
        }
        static let subtitle = "Once your scale displays that it is in AP mode, tap NEXT."
        static let boldWords = ["NEXT", "AP"]
        static let note = "It can take up to two minutes for your scale to enter AP mode."
    }
    
    struct CopyMacAddressViewStrings {
        static let title = "The MAC address of your scale is:"
        static let copyMacAddress = "COPY MAC ADDRESS"
        static let note = "Record the address to share with your Wi-Fi network or IT department. "
            + "Once the scale is whitelisted, return to scale setup to pair your scale."
    }
    
    struct ApModeConnectionViewStrings {
        static let title = "Switch your wi-fi network"
        static let changeNetwork = "Change your Network"
        static let gotoSettings = "GO TO WIFI SETTINGS"
        static let boldWords = ["gg_SmartScale_##"]
        
        // Step instructions
        static let step1 = "1. Open your iPhone Settings"
        static let step2 = "2. Tap Wi-Fi"
        static let step3 = "3. Select the network that looks like: gg_SmartScale_##"
        static let step4Number = "4. "
        static let step4Text = "Come back to this app and tap Next."
        static let step3BoldWords = ["gg_SmartScale_##"]
        static let inactiveNote = "(If it's still inactive, double-check that your phone is connected to the \"gg_SmartScale_##\" network.)"
    }

    struct ErrorDetailViewStrings {
        static let troubleshooting = "Troubleshooting"
        
        struct ErrorDetail {
            let note: String
            let messages: [String]
        }

        static let errorDetails: [WifiErrorCode: ErrorDetail] = [
            .t204: ErrorDetail(
                note: "Error entering AP set-up mode. Please try again.",
                messages: [
                    "It seems like your scale is having trouble communicating with your Wi-Fi network. "
                    + "The best way to fix it is to reset the Wi-Fi chip in your scale.",
                    "To do this, start the scale setup process over and connect to another 2.4 GHz network when the app asks "
                    + "for your wi-fi network name and password. You can try using a phone as a hotspot or someone else's Wi-Fi network.",
                    "If the scale connects fully OR simply doesn't get the t204, we should be back in business "
                    + "and can try to connect on your original network again.",
                    defaultLang
                ]
            ),
            .t205: ErrorDetail(
                note: "Your scale timed out before receiving the connection info it needs to connect to the internet. Please try again.",
                messages: [
                    "This is an issue with some phones and operating systems. First, try turning off your phone's data "
                    + "by putting it in Airplane Mode with Wi-Fi enabled and retry the setup.",
                    "If the problem continues, download the app on another device and try to pair with that. " +
                    "Even if you connect the scale to your Wi-Fi through another device you'll still be able to track your weight on your own phone.",
                    defaultLang
                ]
            ),
            .t206: ErrorDetail(
                note: "Error waiting for a connection to form. Make sure credentials are correct and that you are within "
                + "range of your Wi-Fi router and try again.",
                messages: [
                    "Make sure you connect to a 2.4 GHz Wi-Fi network — typically the one WITHOUT \"_5G\" at the end. " +
                    "You may need to forget the 5G network during setup if your phone continues to choose it by default.",
                    "Check your router — the scale cannot pair with some Xfinity XFI, Cox, and Arris routers.",
                    "The router and scale may be too far apart. Move the scale closer to the router and try connecting again.",
                    "If you still can't connect or have one of the routers listed, tap the \"?\" in the top right corner "
                    + "to contact our customer service team, and we'll be happy to help."
                ]
            ),
            .t163: ErrorDetail(
                note: "Error waiting for a connection to form. Make sure credentials are correct and that you are within "
                + "range of your Wi-Fi router and try again.",
                messages: [
                    "Make sure you connect to a 2.4 GHz Wi-Fi network — typically the one WITHOUT \"_5G\" at the end. " +
                    "You may need to forget the 5G network during setup if your phone continues to choose it by default.",
                    "Check your router — the scale cannot pair with some Xfinity XFI, Cox, and Arris routers.",
                    "The router and scale may be too far apart. Move the scale closer to the router and try connecting again.",
                    "If you still can't connect or have one of the routers listed, tap the \"?\" in the top right corner "
                    + "to contact our customer service team, and we'll be happy to help."
                ]
            ),
            .t164: ErrorDetail(
                note: "The server not responding. Make sure you have an internet connection and try again.",
                messages: [
                    "Are you on a shared/community Wi-Fi network? If yes, and you happen to know who is in control of your network, " +
                    "ask them to whitelist, or give access to, your scale's MAC address.",
                    "To find your scales MAC address tap the button below and go through the MAC address search process.",
                    defaultLang
                ]
            ),
            .t165: ErrorDetail(
                note: "The server not responding. Please try again later.",
                messages: [
                    "Check your personal information in the App Settings to make sure it is correct. "
                    + "Keep in mind the birthday must show you are over the age of 13 to comply with COPPA.",
                    defaultLang
                ]
            ),
            .t315: ErrorDetail(
                note: "The server not responding. Make sure you have an internet connection and try again.",
                messages: [
                    "Are you on a shared/community Wi-Fi network? If yes, and you happen to know who is in control of your network, " +
                    "ask them to whitelist, or give access to, your scale's MAC address.",
                    "To find your scales MAC address tap the button below and go through the MAC address search process.",
                    defaultLang
                ]
            ),
            .t323: ErrorDetail(
                note: "Error forming a connection. Make sure you have an internet connection and try again.",
                messages: [
                    "Make sure you connect to a 2.4 GHz Wi-Fi network — typically the one WITHOUT \"_5G\" at the end. " +
                    "You may need to forget the 5G network during setup if your phone continues to choose it by default.",
                    "Check your router — the scale cannot pair with some Xfinity XFI, Cox, and Arris routers.",
                    "The router and scale may be too far apart. Move the scale closer to the router and try connecting again.",
                    "If you still can't connect or have one of the routers listed, tap the \"?\" in the top right corner "
                    + "to contact our customer service team, and we'll be happy to help."
                ]
            ),
            .t325: ErrorDetail(
                note: "The server not responding. Make sure you have an internet connection and try again.",
                messages: [
                    "Are you on a shared/community Wi-Fi network? If yes, and you happen to know who is in control of your network, " +
                    "ask them to whitelist, or give access to, your scale's MAC address.",
                    "To find your scales MAC address tap the button below and go through the MAC address search process.",
                    defaultLang
                ]
            )
        ]
        
        static let defaultLang = "If this doesn't work out for you, give us a call and we'll work through it together."
        static let other = "Exit the setup and try again from the start. "
            + "If this doesn't work out for you, give us a call and we'll work through it together."
        static let copy = defaultLang
    }
}
