///
///  BpmSetupStep.swift
///  meApp
///

import Foundation

/// Each screen in the BPM setup flow.
///
/// Artwork: A3 uses ``BpmA3MonitorSetupAssets``; A6 uses ``BpmA6MonitorSetupAssets``.
/// Phases: model selection → pre-pairing instructions → BT permission → scanning → pairing/naming → first reading → complete.
enum BpmSetupStep: Int, CaseIterable {
    // Phase 1 – Model selection
    case selectModel = 0

    // Phase 2 – BT permission (placed early so it can be skipped if already granted)
    case btPermission

    // Phase 3 – Pre-pairing instructions
    case selectUser
    case setUser
    case confirmUser
    case prePairing

    // Phase 4 – BLE scanning (auto-pairs on discovery)
    case scanning

    // Phase 5 – Naming the device + paired confirmation
    case nickname
    case paired

    // Phase 6 – First measurement tutorial
    case measureSetup
    case measureStart

    // Phase 7 – Complete
    case complete

    // Intro screen (replaces selectModel when SKU is pre-selected via model number input)
    case intro

    /// Convenience property for page-based controls.
    var index: Int { rawValue }

    /// Default steps used when no SKU is pre-selected (model selection flow).
    static var defaultSteps: [BpmSetupStep] {
        allCases.filter { $0 != .intro }
    }

    /// Steps used when a SKU is pre-selected (intro flow, same as scale setup).
    static var preSelectedSteps: [BpmSetupStep] {
        [.intro, .btPermission, .selectUser, .setUser,
         .confirmUser, .prePairing, .scanning, .nickname,
         .paired, .measureSetup, .measureStart, .complete]
    }
}
