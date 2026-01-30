//
//  DeviceHelper.swift
//  meApp
//
//

import Foundation

/// Helper class for device-related utilities, including SKU mapping for display purposes.
/// Aligned with Android DeviceHelper implementation.
struct DeviceHelper {
    static let sku0022 = "0022"
    static let sku0383 = "0383"
    
    /// Maps a SKU to its display SKU for UI purposes only.
    /// For example, SKU 0022 is displayed as 0383.
    /// This should only be used for display, not for saving.
    /// - Parameter sku: The SKU to map for display
    /// - Returns: The display SKU
    static func mapSkuForDisplay(_ sku: String) -> String {
        if sku == sku0022 {
            return sku0383
        }
        return sku
    }
}
