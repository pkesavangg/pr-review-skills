import Foundation

/// Represents each step in the AppSync scale-setup flow.
enum AppSyncSetupStep: Int, CaseIterable {
    /// Introductory information about the scale (SKU, features, etc.).
    case info = 0
    /// Application permissions required for setup (Bluetooth / Location).
    case permissions
    /// Prompt the user to physically activate / power-on the scale.
    case activateScale
    /// Collect additional profile information if necessary.
    case addInfo
    /// Educate the user on when to weigh-in for best results.
    case weighInTime
    /// The actual AppSync process / progress screen.
    case appSync
    /// Final success / completion screen.
    case finish

    /// Convenience for binding to page-index-based UI controls.
    var index: Int { rawValue }
} 