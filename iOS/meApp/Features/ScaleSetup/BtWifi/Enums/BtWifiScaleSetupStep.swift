//
//  BtWifiScaleSetupStep.swift
//  meApp
//
//  Created by Cursor AI on 12/01/25.
//

import Foundation

/// Represents each step in the BtWifi scale setup flow.
enum BtWifiScaleSetupStep: Int, CaseIterable {
    /// Introductory information about the scale (SKU, features, etc.).
    case intro = 0
    /// Application permissions required for setup (Bluetooth, Location).
    case permissions
    /// Wake up the scale via Bluetooth.
    case wakeup
    /// Connecting to the scale via Bluetooth.
    case connectingBluetooth
    /// Gathering available Wi-Fi networks with error handling.
    case gatheringNetwork
    /// Show available Wi-Fi networks list.
    case availableWifiList
    /// Enter Wi-Fi password.
    case wifiPassword
    /// Connecting to Wi-Fi.
    case connectingWifi
    /// Customize scale settings.
    case customizeSettings
    /// View current settings.
    case viewSettings
    /// Update scale settings.
    case updateSettings
    /// Step on scale for measurement.
    case stepOn
    /// Taking measurement.
    case measurement
    /// Scale successfully connected.
    case scaleConnected

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
}
