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
    case powerSwitch   // 0636 only: set the power switch to ON
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
        [.selectModel, .btPermission, .selectUser, .setUser,
         .confirmUser, .prePairing, .scanning, .nickname,
         .paired, .measureSetup, .measureStart, .complete]
    }

    /// Steps used when a SKU is pre-selected (intro flow, same as scale setup).
    static var preSelectedSteps: [BpmSetupStep] {
        [.intro, .btPermission, .selectUser, .setUser,
         .confirmUser, .prePairing, .scanning, .nickname,
         .paired, .measureSetup, .measureStart, .complete]
    }

    /// Returns the setup step flow for the given SKU.
    /// Different monitors have different step sequences matching the Ionic app.
    static func steps(for sku: String, preSelected: Bool) -> [BpmSetupStep] {
        let first: BpmSetupStep = preSelected ? .intro : .selectModel

        switch sku {
        case "0604", "0661":
            // Toggle-switch monitors skip the confirmUser (MONITOR_OFF) step
            return [first, .btPermission, .selectUser, .setUser,
                    .prePairing, .scanning, .nickname, .paired,
                    .measureSetup, .measureStart, .complete]

        case "0636":
            // Has powerSwitch step; keeps confirmUser
            return [first, .btPermission, .selectUser, .powerSwitch, .setUser,
                    .confirmUser, .prePairing, .scanning, .nickname, .paired,
                    .measureSetup, .measureStart, .complete]

        default:
            // Default flow (0603, 0634, 0663): includes confirmUser
            return [first, .btPermission, .selectUser, .setUser,
                    .confirmUser, .prePairing, .scanning, .nickname,
                    .paired, .measureSetup, .measureStart, .complete]
        }
    }
}
