//
//  SettingsConstants.swift
//  meApp
//
//  Created by Lakshmi Priya on 27/06/25.
//

import Foundation

/// Constants used throughout the Settings feature to avoid magic numbers and hardcoded values
struct SettingsConstants {
    
    // MARK: - Scale SKU Constants
    /// Default SKU for R4 scales (Bluetooth/WiFi)
    static let defaultR4Sku = "0412"
    
    // MARK: - Timer Intervals
    /// Connection refresh interval in seconds
    static let connectionRefreshInterval: TimeInterval = 3.0
    
    /// WiFi status refresh delay in seconds
    static let wifiStatusRefreshDelay: TimeInterval = 5.0
    
    // MARK: - Periodic Check Constants
    /// Maximum number of iterations for periodic device info checks
    static let maxCheckIterations = 6
    
    /// Check interval in nanoseconds (10 seconds)
    static let checkIntervalNanoseconds: UInt64 = 10_000_000_000
    
    // MARK: - Step Index Constants
    /// Index for the gathering network step in BtWifi scale setup
    static let gatheringNetworkStepIndex = 4
    
    // MARK: - Helper Methods
    /// Converts seconds to nanoseconds for Task.sleep
    /// - Parameter seconds: The number of seconds to convert
    /// - Returns: The equivalent number of nanoseconds
    static func secondsToNanoseconds(_ seconds: TimeInterval) -> UInt64 {
        return UInt64(seconds * 1_000_000_000)
    }
} 
