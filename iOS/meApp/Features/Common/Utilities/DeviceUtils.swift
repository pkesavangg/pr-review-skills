import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Utility properties for device-specific behaviors.
enum DeviceUtils {
    /// True for iPad on iOS versions below 18, indicating centered modal presentations.
    static var useModalPicker: Bool {
        #if canImport(UIKit)
        return UIDevice.current.userInterfaceIdiom == .pad &&
               !ProcessInfo.processInfo.isOperatingSystemAtLeast(
                    OperatingSystemVersion(majorVersion: 18, minorVersion: 0, patchVersion: 0)
               )
        #else
        return false
        #endif
    }
}
