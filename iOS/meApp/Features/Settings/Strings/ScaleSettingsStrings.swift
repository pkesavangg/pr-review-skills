//
//  ScaleSettingsStrings.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import Foundation

struct ScaleSettingsStrings {
    static let enableBodyMetrics = "Enable Body Metrics"
    static let setupWiFi = "Setup Wi-Fi"
    static let weightOnlyOn = "Weight Only: On"
    static let setupIncomplete = "Setup Incomplete"
    static let deleteScale = "Delete Scale"
    static let scalenName = "Scale Name"
    // Section Headers
    static let settingsSectionHeader = "Settings"
    static let connectionSectionHeader = "Connection"
    static let supportSectionHeader = "Support"

    // List Item Titles
    static let mode = "Mode"
    static let displayMetrics = "Display Metrics"
    static let users = "Users"
    static let scaleName = "Scale Name"
    static let bluetooth = "Bluetooth"
    static let wifi = "Wi-Fi"
    static let wifiMacAddress = "Wi-Fi Mac Address"
    static let scaleType = "Scale Type"
    static let sku = "SKU"
    static let datePaired = "Date Paired"
    static let productGuide = "Product Guide"
    static let userNumber  = "User Number"
    static let userNumberInfo: (String) -> String = { userNumber in
        return "U\(userNumber)"
    }

    // Additional Settings
    static let othersSectionHeader = "Others"
    static let otherSettings = "Other Settings"
    static let softwareUpdate = "Software Update"
    static let scaleMac = "Scale MAC"
    static let scaleFeatures = "Scale Features"
    static let startAnimation = "Start Animation"
    static let endAnimation = "End Animation"
    static let clearData = "Clear Data"
    static let clearWifi = "Clear Wi‑Fi"
    static let clearSettings = "Clear Settings"
    static let clearHistory = "Clear History"
    static let clearAccount = "Clear Account"
    static let timeFormat = "Time Format"
    static let resetFirmware = "Reset Firmware"
    static let restoreFactorySettings = "Restore Factory Settings"
    static let sessionImpedance = "Session Impedance"
}
