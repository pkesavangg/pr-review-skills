import Foundation

/// Enum representing supported protocol types used for bluetooth scales.
/// Case names match Bluetooth protocol specifications and SDK naming conventions.
public enum ProtocolType: String, Sendable, CaseIterable {
    case A3
    case A6
    case R4
}
