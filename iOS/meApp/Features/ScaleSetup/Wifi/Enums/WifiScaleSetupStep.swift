import Foundation

/// Represents each step in the WiFi scale setup flow.
enum WifiScaleSetupStep: Int, CaseIterable {
    /// Introductory information about the scale (SKU, features, etc.).
    case intro = 0
    /// Application permissions required for setup (Location).
    case permissions
    /// Enter Wi-Fi password.
    case wifiPassword
    /// Select user number.
    case selectUser
    /// Pairing mode activation.
    case activatePairingMode
    /// Connection confirmation.
    case connectionConfirm
    /// AP mode.
    case apMode
    /// AP mode confirmation.
    case apModeConfirm
    /// Error selection.
    case errorSelect
    /// Error details.
    case errorDetail
    ///  Copy MAC address to clipboard.
    case copyMacAddress
    /// Step on scale.
    case stepOn
    /// Setup finished.
    case setupFinish

    /// Convenience property for page-based controls.
    var index: Int { rawValue }
} 
