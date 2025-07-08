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
    /// Application permissions required for setup (Bluetooth / Location, etc.).
    case permissions
    /// Searching / pairing progress UI.
    case searching
    /// Persisting the newly paired scale.
    case saving
    /// Final success / completion screen.
    case finish

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
} 