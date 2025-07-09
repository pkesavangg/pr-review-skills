//
//  ScaleSetupStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//
import Foundation

// MARK: - Strings lookup
struct ScaleSetupStrings {
    static let troubleSettingUp = "If you're having trouble setting up your scale, press the help button in the top right to connect with our team."
    static let getScaleMacAddress = "Get your scale's MAC address"
    static let setupHeader: (String) -> String = { sku in
        "Scale Setup - \(sku)"
    }
    static let modelTitle: (String) -> String = { sku in
        "Model \(sku)"
    }
    static let wakeYourScaleTitle = "Wake Your Scale"
    static let wakeYourScaleSubtitle = "Give it a little tap, so your phone can find it."
    static let gatheringNetworksTitle = "Gathering Networks"
}
