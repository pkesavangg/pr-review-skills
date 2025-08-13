import UIKit

/// Device platform utilities

enum DevicePlatform {
    /// Returns true if the real device model is an iPad.
    /// Uses `UIDevice.current.model` which reports "iPad" on iPad hardware.
    static var isTablet: Bool {
        UIDevice.current.model.range(of: "iPad", options: .caseInsensitive) != nil
    }
} 
