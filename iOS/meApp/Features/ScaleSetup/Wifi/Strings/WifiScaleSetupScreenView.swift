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
}
