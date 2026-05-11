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
    static let user = "User"
    static let userNumber = "User Number"
    static let userALabel = "A"
    static let userBLabel = "B"
    /// Formats user number for display. Only 0603 (hasNumericUsers) shows "User 1"/"User 2";
    /// all other monitors show "User A"/"User B" regardless of protocol type.
    static let userNumberInfo: (String, String?) -> String = { userNumber, sku in
        let hasNumeric = sku.flatMap { bpmCatalogItem(forEnteredCode: $0) }?.hasNumericUsers ?? false
        let userLabel: String

        if hasNumeric {
            userLabel = userNumber
        } else {
            userLabel = userNumber == "1" ? userALabel : userBLabel
        }

        return "\(user) \(userLabel)"
    }
}
