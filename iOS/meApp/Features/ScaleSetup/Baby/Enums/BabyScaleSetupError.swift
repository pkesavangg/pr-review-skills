//
//  BabyScaleSetupError.swift
//  meApp
//

import Foundation

/// Error states specific to the Baby scale setup flow.
enum BabyScaleSetupError {
    case none
    case bluetoothOff
    case connectionFailed
    case pairingFailed
    case profileSaveFailed
}
