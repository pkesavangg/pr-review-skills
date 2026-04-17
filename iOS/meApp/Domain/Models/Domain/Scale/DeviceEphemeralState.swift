import Foundation

/// In-memory ephemeral state for a Device, keyed by broadcastIdString or device ID.
/// Managed by ScaleService. Never persisted to SwiftData.
///
/// These three properties are reset on every app launch — they have no persistence
/// value and were previously stored in SwiftData unnecessarily.
struct DeviceEphemeralState {
    var isConnected: Bool = false
    var isWifiConfigured: Bool = false
    var isWeighOnlyModeEnabledByOthers: Bool = false
}
