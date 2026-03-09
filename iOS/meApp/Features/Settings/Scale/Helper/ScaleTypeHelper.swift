//
//  ScaleTypeHelper.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Foundation

/// Helper class for determining scale types based on device properties
struct ScaleTypeHelper {
    
    // Determines the scale type based on the scale's SKU and other properties
    // - Parameter scale: The device to determine the scale type for
    // - Returns: The determined scale type as a ScaleType enum
// swiftlint:disable:next cyclomatic_complexity
    static func determineScaleType(for scale: Device) -> ScaleType {
        guard let sku = scale.sku else { return .bluetoothA6 } // Default fallback
        
        // Map SKU for SCALES lookup (e.g., 0022 -> 0383)
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        // Get scale info from the SCALES constant
        if let scaleInfo = SCALES.first(where: { $0.sku == lookupSku }) {
            switch scaleInfo.setupType {
            case .bluetooth, .lcbt:
                return .bluetoothA6
            case .wifi, .espTouchWifi:
                return .wifi
            case .appSync:
                return .appsync
            case .btWifiR4:
                return .bluetoothR4
            }
        }
        
        // Fallback: determine based on scale source type if available
        if let scaleSourceType = scale.bathScale?.scaleType {
            let sourceType = ScaleSourceType(rawValue: scaleSourceType) ?? .bluetoothScale
            switch sourceType {
            case .bluetooth, .bluetoothScale, .lcbt, .lcbtScale:
                return .bluetoothA6
            case .wifi, .espTouchWifi:
                return .wifi
            case .appsync, .appsyncScale:
                return .appsync
            case .btWifiR4:
                return .bluetoothR4
            }
        }
        
        // Final fallback: determine based on device type
        if let deviceType = scale.deviceType {
            switch deviceType.lowercased() {
            case "bluetooth", "bluetoothscale":
                return .bluetoothA6
            case "wifi", "wifiscale":
                return .wifi
            case "appsync", "appsyncscale":
                return .appsync
            default:
                return .bluetoothA6
            }
        }
        
        return .bluetoothA6 // Default fallback
    }
    
    // Determines the scale type as a string based on the scale's SKU and other properties
    // - Parameter scale: The device to determine the scale type for
    // - Returns: The determined scale type as a string
// swiftlint:disable:next cyclomatic_complexity
    static func determineScaleTypeString(for scale: Device) -> String {
        guard let sku = scale.sku else { return "Unknown" }
        
        // Map SKU for SCALES lookup (e.g., 0022 -> 0383)
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        // Get scale info from the SCALES constant
        if let scaleInfo = SCALES.first(where: { $0.sku == lookupSku }) {
            switch scaleInfo.setupType {
            case .bluetooth, .lcbt:
                return "Bluetooth"
            case .wifi, .espTouchWifi:
                return "WiFi"
            case .appSync:
                return "AppSync"
            case .btWifiR4:
                return "Bluetooth/Wi-Fi"
            }
        }
        
        // Fallback: determine based on scale source type if available
        if let scaleSourceType = scale.bathScale?.scaleType {
            let sourceType = ScaleSourceType(rawValue: scaleSourceType) ?? .bluetoothScale
            switch sourceType {
            case .bluetooth, .bluetoothScale, .lcbt, .lcbtScale:
                return "Bluetooth"
            case .wifi, .espTouchWifi:
                return "WiFi"
            case .appsync, .appsyncScale:
                return "AppSync"
            case .btWifiR4:
                return "Bluetooth/Wi-Fi"
            }
        }
        
        // Final fallback: determine based on device type
        if let deviceType = scale.deviceType {
            switch deviceType.lowercased() {
            case "bluetooth", "bluetoothscale":
                return "Bluetooth"
            case "wifi", "wifiscale":
                return "WiFi"
            case "appsync", "appsyncscale":
                return "AppSync"
            default:
                return "Unknown"
            }
        }
        
        return "Unknown"
    }
} 
