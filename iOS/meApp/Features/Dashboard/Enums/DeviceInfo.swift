import UIKit

/// Device platform utilities

enum DevicePlatform {
    /// iPad check
    static var isTablet: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }
    
    /// iPhone mini check (12 mini & 13 mini)
    /// Native resolution: 2340 × 1080
    static var isMiniPhone: Bool {
        guard !isTablet else { return false }
        let size = max(UIScreen.main.nativeBounds.width, UIScreen.main.nativeBounds.height)
        return size == 2340   // precise mini models
    }
    
    /// Small iPhones but NOT mini
    static var isSmallPhone: Bool {
        guard !isTablet else { return false }
        guard !isMiniPhone else { return false }   // exclude mini
        let minSide = min(UIScreen.main.nativeBounds.width, UIScreen.main.nativeBounds.height)
        return minSide < 1200
    }
    
    /// Large iPhones (anything not small, not mini, not iPad)
    static var isLargePhone: Bool {
        guard !isTablet else { return false }
        return !isMiniPhone && !isSmallPhone
    }
}
