//
//  A6ScaleSetupStep.swift
//  meApp
//
//  Created by Cursor AI on 08/07/25.
//

import Foundation

/// Represents each step in the A6 (LCBT) scale setup flow.
enum A6ScaleSetupStep: Int, CaseIterable {
    /// Introductory information about the scale (SKU, features, etc.).
    case intro = 0
    /// Application permissions required for setup (Bluetooth).
    case permissions
    ///  Searching for the scale via Bluetooth.
    case wakeUp
    /// Connecting and saving scale information.
    case connectingBluetooth
    /// Final success / completion screen.
    case setupFinished

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
} 
