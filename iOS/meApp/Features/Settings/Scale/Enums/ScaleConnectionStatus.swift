//
//  ScaleConnectionStatus.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Foundation

enum ScaleConnectionStatus {
    case connected
    case notConnected
    case setupIncomplete
    case noStatus // For AppSync scales that don't show connection status

    var displayText: String {
        switch self {
        case .connected: return MyScaleStrings.connected
        case .notConnected: return MyScaleStrings.notConnected
        case .setupIncomplete: return MyScaleStrings.setupIncomplete
        case .noStatus: return ""
        }
    }
}
