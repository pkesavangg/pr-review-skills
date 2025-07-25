//
//  WifiConnectionStatus.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/07/25.
//
import GGBluetoothSwiftPackage

// MARK: - Wifi Connection Models
/// Enum describing the current Wi-Fi connection state.
public enum WifiConnectionStatus: String, Codable {
    case unknown
    case enabled       // Wi-Fi switch ON but not connected to an AP
    case connected     // Connected to an AP
    case disabled      // Wi-Fi switch OFF
}

/// Container returned by `getConnectedWifiInfo()` mirroring the TS implementation.
public struct WifiStatus: Codable {
    let status: WifiConnectionStatus
    let locationStatus: GGPermissionState
    let ssid: String?
    let bssid: String?

    public enum CodingKeys: String, CodingKey {
        case status
        case locationStatus
        case ssid
        case bssid
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(status, forKey: .status)
        // Encode GGPermissionState using its raw value to avoid requiring it to conform to Codable
        try container.encode(locationStatus.rawValue, forKey: .locationStatus)
        try container.encodeIfPresent(ssid, forKey: .ssid)
        try container.encodeIfPresent(bssid, forKey: .bssid)
    }
}
