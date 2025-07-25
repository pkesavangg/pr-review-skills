import Foundation
import SystemConfiguration.CaptiveNetwork
import NetworkExtension

/// Lightweight helper for retrieving the phone's currently connected Wi-Fi SSID.
///
/// Requires the **Access WiFi Information** capability as well as Location permission.
@MainActor
enum WifiInfoService {
    /// Returns the SSID of the network the device is currently connected to, or `nil` if unavailable.
    static func currentSSID() async -> String? {
        // iOS 14+: Use the modern NetworkExtension API when possible.
        if #available(iOS 14.0, *) {
            if let hotspotNetwork = try? await NEHotspotNetwork.fetchCurrent()
                {
                return hotspotNetwork.ssid
            }
        }

        // Fallback for older iOS versions – use CaptiveNetwork.
        guard let interfaces = CNCopySupportedInterfaces() as? [String] else { return nil }
        for interface in interfaces {
            if let info = CNCopyCurrentNetworkInfo(interface as CFString) as NSDictionary?,
               let ssid = info[kCNNetworkInfoKeySSID as String] as? String, !ssid.isEmpty {
                return ssid
            }
        }
        return nil
    }

    /// Returns the BSSID (MAC address) of the network the device is currently connected to, or `nil` if unavailable.
    static func currentBSSID() async -> String? {
        // iOS 14+: Use the modern NetworkExtension API when possible.
        if #available(iOS 14.0, *) {
            if let hotspotNetwork = try? await NEHotspotNetwork.fetchCurrent() {
                return hotspotNetwork.bssid
            }
        }

        // Fallback for older iOS versions – use CaptiveNetwork.
        guard let interfaces = CNCopySupportedInterfaces() as? [String] else { return nil }
        for interface in interfaces {
            if let info = CNCopyCurrentNetworkInfo(interface as CFString) as NSDictionary?,
               let bssid = info[kCNNetworkInfoKeyBSSID as String] as? String, !bssid.isEmpty {
                return bssid
            }
        }
        return nil
    }
} 
