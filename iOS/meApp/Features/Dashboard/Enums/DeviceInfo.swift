import UIKit

/// Device platform utilities

enum DevicePlatform {
    /// Returns true if the real device model is an iPad.
    /// Uses `UIDevice.current.model` which reports "iPad" on iPad hardware.
    static var isTablet: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }
    static var isSmallPhone: Bool {
        !isTablet && min(UIScreen.main.nativeBounds.width, UIScreen.main.nativeBounds.height) < 1200
    }
    static var isLargePhone: Bool {
        !isTablet && !isSmallPhone
    }
}
