/// Enum representing supported protocol types used for bluetooth Scales.
// swiftlint:disable identifier_name
import Foundation

/*
 SwiftLint exception:
 Protocol type enum cases intentionally use uppercase names (A3, A6, R4) to match the Bluetooth protocol specifications and SDK naming conventions. Changing these to lowercase would break compatibility with external SDKs and create confusion. We therefore disable `identifier_name` for this file.
 */
public enum ProtocolType: String, Sendable, CaseIterable {
    case A3 = "A3"
    case A6 = "A6"
    case R4 = "R4"
}
// swiftlint:enable identifier_name
