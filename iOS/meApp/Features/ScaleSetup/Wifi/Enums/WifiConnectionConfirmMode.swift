//
//  WifiConnectionConfirmMode.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/07/25.
//

/// Mode defining which content WifiConnectionConfirmView should display.
enum WifiConnectionConfirmMode {
    /// Show both setup-complete and AP-mode options.
    case optionSelection
    /// Show only AP-mode related UI (no setup-complete option).
    case apModeOnly
    /// Show the AP-mode confirmation (scale counting etc.).
    case apModeConfirmation
}
