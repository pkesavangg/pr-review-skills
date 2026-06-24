//
//  DeviceConnectionStatus.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Foundation

enum DeviceConnectionStatus {
    case connected
    case notConnected
    case setupIncomplete
    case noStatus // For AppSync scales that don't show connection status

    var displayText: String {
        switch self {
        case .connected: return MyDeviceStrings.connected
        case .notConnected: return MyDeviceStrings.notConnected
        case .setupIncomplete: return MyDeviceStrings.setupIncomplete
        case .noStatus: return ""
        }
    }
}
