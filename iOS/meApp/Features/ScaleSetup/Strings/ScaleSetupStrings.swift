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
    
    /// Finish view strings
    struct FinishViewStrings {
        static let title = "Your scale is paired and ready to go!"
        static let description = "Next time you weigh in, the results will automatically be sent to Weight Gurus."
        static let appSyncDescription = "To sync new entries, tap the icon at the bottom right of the app when you see the result code display on your scale's screen."
    }
}
