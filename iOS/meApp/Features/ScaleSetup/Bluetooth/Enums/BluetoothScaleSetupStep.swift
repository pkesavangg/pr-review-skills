///
///  BluetoothScaleSetupStep.swift
///  meApp
///
///  Created by Cursor AI on 18/07/25.
///

import Foundation

/// Represents each step in the Bluetooth scale setup flow.
enum BluetoothScaleSetupStep: Int, CaseIterable {
    /// Introductory information about the scale (SKU, features, etc.).
    case intro = 0
    /// Application permissions required for setup (Bluetooth).
    case permissions
    /// Collect user-profile details (biological sex, height, optional goal).
    /// Always shown in the Bluetooth flow; existing account values are pre-filled so
    /// the user can confirm or adjust them (MOB-1388).
    case completeProfile
    /// Allows the user to select the user number on the scale before pairing.
    case selectUser
    /// Device discovery & pairing in progress.
    case connectingBluetooth
    /// Educate the user to set the user number on the scale.
    case setUser
    /// Displays *step-on* instructions while the scale syncs the first measurement.
    case stepOn
    /// Final success / completion screen.
    case setupFinished

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
} 
