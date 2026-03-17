///
///  BpmSetupStep.swift
///  meApp
///

import Foundation

/// Represents each step in the BPM (Blood Pressure Monitor) setup flow.
enum BpmSetupStep: Int, CaseIterable {
    /// Allows the user to select their BPM model.
    case selectModel = 0
    /// Preparing the device for pairing.
    case preparing
    /// Application permissions required for setup (Bluetooth).
    case btPermission
    /// Scanning for nearby BPM devices.
    case scanning
    /// Pairing with the discovered BPM device.
    case pairing
    /// Connecting to the paired BPM device.
    case connecting
    /// Final success / completion screen.
    case success

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
}
