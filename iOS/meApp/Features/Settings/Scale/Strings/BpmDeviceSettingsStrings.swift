//
//  BpmDeviceSettingsStrings.swift
//  meApp
//

import Foundation

struct BpmDeviceSettingsStrings {
    // Section Headers
    static let settingsSectionHeader = "Settings"
    static let connectionSectionHeader = "Connection"
    static let supportSectionHeader = "Support"

    // Settings Items
    static let deviceName = "Device Name"

    // Connection Items
    static let bluetooth = "Bluetooth"

    // Support Items
    static let deviceType = "Device Type"
    static let sku = "SKU"
    static let datePaired = "Date Paired"
    static let productGuide = "Product Guide"

    // Actions
    static let deleteDevice = "Delete Device"

    // User Number
    static let userNumber = "User Number"
    static let userNumberInfo: (String) -> String = { userNumber in
        return "U\(userNumber)"
    }
}
