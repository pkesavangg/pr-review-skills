//
//  BabyScaleSetupStep.swift
//  meApp
//

import Foundation

/// Represents each step in the Baby scale setup flow.
enum BabyScaleSetupStep: Int, CaseIterable {
    /// Model info screen (image + "Smart Baby Scale").
    case intro = 0
    /// Bluetooth permissions checklist.
    case permissions
    /// "Turn on your Scale" — scanning for device.
    case wakeup
    /// Connecting to the scale via Bluetooth / error state.
    case connectingBluetooth
    /// "Give your scale a name" — nickname input.
    case scaleName
    /// "You're Paired!" — success with baby profile prompt.
    case paired
    /// "Complete Baby Profile" — name, birthday, sex, birth length/weight.
    case babyProfile
    /// "Your Baby Has Been Added!" — list of babies + add more.
    case babyAdded

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
}
