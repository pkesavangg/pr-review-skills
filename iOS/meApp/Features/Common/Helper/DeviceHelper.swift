//
//  DeviceHelper.swift
//  meApp
//
//

import Foundation

struct DeviceHelper {
    static let sku0022 = "0022"
    static let sku0383 = "0383"
    
    /// Maps a SKU to its display SKU for UI purposes only.
    static func mapSkuForDisplay(_ sku: String) -> String {
        if sku == sku0022 {
            return sku0383
        }
        return sku
    }
}
