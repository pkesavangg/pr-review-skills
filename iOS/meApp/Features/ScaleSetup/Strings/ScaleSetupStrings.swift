//
//  ScaleSetupStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//
import Foundation

// MARK: - Strings lookup
struct ScaleSetupStrings {
    static let troubleSettingUp = "If you're having trouble setting up your scale, press the help button in the top right to connect with our team."
    static let getScaleMacAddress = "Get your scale's MAC address"
    static let setupHeader: (String) -> String = { sku in
        // Map SKU for display (e.g., 0022 -> 0383) for UI
        let displaySku = DeviceHelper.mapSkuForDisplay(sku)
        return "Scale Setup - \(displaySku)"
    }
    static let modelTitle: (String) -> String = { sku in
        // Map SKU for display (e.g., 0022 -> 0383) for UI
        let displaySku = DeviceHelper.mapSkuForDisplay(sku)
        return "Model \(displaySku)"
    }
    static let wakeYourScaleTitle = "Wake Your Scale"
    static let wakeYourScaleSubtitle = "Give it a little tap, so your phone can find it."
    /// Subtitle for LCBT (A6) scales - user should step on the scale
    static let wakeYourScaleSubtitleLCBT = "Step on the scale, so your phone can find it."
    static let gatheringNetworksTitle = "Gathering Networks"
    static let connectingToBluetooth = "Connecting to Bluetooth"
    static let connectedToBluetooth = "Connected to Bluetooth"
    static let connectingToWifi = "Connecting to WiFi"
    static let connectedToWifi = "Connected to WiFi"
    static let connectionError = "Connection Error"
    static let noNetworksFound = "No Networks Found"
    static let errorCode: (String) -> String = { errorCode in
        "Error Code: \(errorCode)"
    }
    static let setupWifiLater = "Setup WiFi Later"
    
    struct UserNumberSelectionViewStrings {
        static let title = "Choose your user number."
        static let description = "Pick one that no one else is using for this scale."
    }

    /// Complete Profile Setup step. Shared across the Bluetooth scale-setup flows
    /// (A6/LCBT, Bluetooth/A3, and BtWifi/R4) — see `CompleteProfileSetupStore`.
    struct CompleteProfileStrings {
        static let title = "Complete Profile Set Up"
        static let subtitle = "Add a few details to personalize your profile."
        static let biologicalSex = "Biological Sex"
        static let height = "Height"
        static let setAGoal = "Set a Goal"
        static let optional = "(optional)"
        static let startingWeight = "Starting Weight"
        static let goalWeight = "Goal Weight"
    }
    
    /// Finish view strings
    struct FinishViewStrings {
        static let title = "Your scale is paired and ready to go!"
        static let description = "Next time you weigh in, the results will automatically be sent to Weight Gurus."
        static let appSyncDescription = "To sync new entries, tap the icon at the bottom right of the app when you see "
            + "the result code display on your scale's screen."
    }

    struct A11y {
        static let closeButtonLabel = "Close"
        static let closeButtonHint = "Dismiss this sheet"
        static let connectButtonHint = "Double tap to connect to this scale"
        static let userNumberLabel: (Int) -> String = { "User number \($0)" }
        static let userNumberHint = "Double tap to select"
        static let networkRowLabel: (String) -> String = { "Wi-Fi network: \($0)" }
        static let networkRowHint = "Double tap to select this network"
        static let refreshNetworksHint = "Double tap to refresh available networks"
        static let successIconLabel = "Setup complete"
        static let tryAgainHint = "Double tap to retry the connection"
        static let supportHint = "Double tap to contact support"
        static let setupWifiLaterHint = "Double tap to skip Wi-Fi setup"
    }
}
