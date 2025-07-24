//
//  WifiConnectionStatus.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/07/25.
//
import GGBluetoothSwiftPackage

// MARK: - Wifi Connection Models
/// Enum describing the current Wi-Fi connection state.
 enum WifiConnectionStatus: String {
    case unknown
    case enabled       // Wi-Fi switch ON but not connected to an AP
    case connected     // Connected to an AP
    case disabled      // Wi-Fi switch OFF
}

/// Container returned by `getConnectedWifiInfo()` mirroring the TS implementation.
struct WifiStatus {
    let status: WifiConnectionStatus
    let locationStatus: GGPermissionState
    let ssid: String
    let bssid: String
}
