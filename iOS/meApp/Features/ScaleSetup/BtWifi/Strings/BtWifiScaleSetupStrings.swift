//
//  BtWifiScaleSetupStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 12/07/25.
//

import Foundation

struct BtWifiScaleSetupStrings {
    struct DuplicateUserViewStrings {
        static let title: (Bool) -> String = { isCustomizationSettings in
            isCustomizationSettings ? "Confirm User Name" : "A user with this name already exists on the scale."
        }
        static let subtitle: (Bool) -> String = { isCustomizationSettings in
            isCustomizationSettings ? "Change how your name appears on the scale." : "Choose a new user name to proceed. Or, if this is you, restore the existing account."
        }
        static let restoreAccountButton = "Restore account"
        static let lastActive = "last active"
    }
    
    struct MaxUserListViewStrings {
        static let title = "User Limit Exceeded!"
        static let subtitle = "Delete an inactive user to add yourself to the scale."
    }
    
    struct WifiScreenStrings {
        static let title = "Wi-Fi Setup"
        static let gatheringNetworks = "Gathering Networks"
        static let refresh = "Refresh"
        static let multipleNetworksInfo = "If you have multiple Wi-Fi networks, pick the 2.4 GHz network closest to your scale."
        static let continueNetworkPrompt = "Continue or choose a different 2.4 GHz Wi-Fi network."
        static let alreadyConnected = "The scale has already been connected to Wi-Fi"
        static let continueOrChooseDiff = "Continue or choose a different 2.4 GHz Wi-Fi network."
        static let selectNetwork = "Select a 2.4 GHz Network"
        static let pickClosestNetwork = "If you have multiple Wi-Fi networks, pick the 2.4 GHz network closest to your scale."
        static let connectedNetwork = "Connected Network"
        static let availableNetworks = "Available Networks"
        static let passwordPlaceholder = "password"
        static let connectingToWifi = "Connecting to WiFi"
        static let enterPasswordTitle = "Enter Wi-Fi Password"
        static let enterPasswordSubtitlePrefix = "Add the password for "
        static let enterPasswordSubtitleSuffix = "."
        static let noPasswordToggle = "Network has no password"
        static let connectButtonTitle = "Connect"
    }
    
    struct CustomizeSettingsStrings {
        static let title = "Customize your Settings"
        static let subtitle = "You can update settings at any time."
        static let dashboardMetricsTitle = "Dashboard Metrics"
        static let dashboardMetricsSubtitle = "Customize which metrics you'll see on your app's dashboard."
        static let scaleMetricsTitle = "Scale Metrics"
        static let scaleMetricsSubtitle = "Customize the metrics you'll see when weighing-in."
        static let scaleModesTitle = "Scale Modes"
        static let scaleModesSubtitle = "Those with specific medical conditions may want to change modes."
        static let userNameTitle = "User Name"
        static let userNameSubtitle = "Change how your name appears on the scale."
        static let updatingSettings = "Updating Settings"
        static let collectingMeasurement = "Collecting Measurement"
    }
    
    struct StepOnStrings {
        static let title = "One Last Step"
        static let subtitle = "Step on the scale and take a measurement."
    }
    
    struct ScaleSetupFinishStrings {
        static let title = "Measurement Recorded"
        static let subtitle = "Your measurement was verified and added to your account."
        static let whatThis = "What’s this?"
    }
    
    struct BtWifiSetupErrorStateViewStrings {
        static let updateFailed = "Update Failed"
        static let errorCollectingMeasurement = "Error Collecting Measurement"
    }
    
    struct AccuCheckInfoModalViewStrings {
        static let title = "What is AccuCheck?"
        static let description1 = "AccuCheck is Greater Goods’ proprietary algorithm, designed to provide the most accurate results possible every time you weigh in—down to .01 lb."
        static let description2 = "Shortly after getting on the scale, you'll see an orange light. " +
            "This means that you've been weighed. When you get off the scale, you'll see a green light. " +
            "This means that AccuCheck has double checked its work, and your weight has been verified. " +
            "Essentially, AccuCheck is a second set of eyes, every time."
    }
    
    struct ScaleMetricsCustomizationViewStrings {
        static let title = "Customize Your Scale"
        static let subtitle = "Rearrange tiles and/or hide unwanted metrics from your scale screen."
    }
}
