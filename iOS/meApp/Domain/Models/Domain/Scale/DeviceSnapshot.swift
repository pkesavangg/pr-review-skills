import Foundation

/// A value-type copy of Device and its child relationships.
/// Published by DeviceService instead of the SwiftData @Model directly.
/// Safe to use across async boundaries and as Combine publisher payloads.
///
/// ## Ephemeral vs. Persisted State
///
/// Three properties are **ephemeral** — they come from the in-memory
/// `ephemeralState` dictionary in DeviceService, NOT from SwiftData:
/// - `isConnected` (set by BLE DEVICE_CONNECTED events)
/// - `isWifiConfigured` (set by BLE DEVICE_INFO_UPDATE events)
/// - `isWeighOnlyModeEnabledByOthers` (set by BLE scan pipeline)
///
/// These are merged into the snapshot during `refreshScalesFromLocal()`.
/// Updating them never touches SwiftData.
struct DeviceSnapshot: Equatable, Sendable, Identifiable {

    // MARK: - Core Device Fields (persisted in SwiftData)
    let id: String
    let accountId: String
    let peripheralIdentifier: String?
    let nickname: String?
    let sku: String?
    let mac: String?
    let password: Int64?
    let isSoftDeleted: Bool?
    let deviceName: String?
    let deviceType: String?
    let broadcastId: Int64?
    let broadcastIdString: String?
    let userNumber: String?
    let protocolType: String?
    let createdAt: String?
    let lastModified: Int?
    let isSynced: Bool?
    let hasServerID: Bool
    let wifiMac: String?
    let token: String?

    // MARK: - Ephemeral Runtime State (NOT in SwiftData — from in-memory dict)
    let isConnected: Bool
    let isWifiConfigured: Bool
    let isWeighOnlyModeEnabledByOthers: Bool

    // MARK: - Child Relationship Snapshots
    let bathScale: BathScaleSnapshot?
    let r4ScalePreference: R4ScalePreferenceSnapshot?
    let metaData: DeviceMetaDataSnapshot?

    // MARK: - Computed
    var connectionStatus: DeviceConnectionStatus {
        let type = DeviceTypeHelper.determineDeviceModelType(
            sku: sku,
            scaleType: bathScale?.scaleType,
            deviceType: deviceType
        )
        if type == .appsync || type == .wifi { return .noStatus }
        guard isConnected else { return .notConnected }
        if type == .bluetoothR4 {
            let wifiOk = isWifiConfigured
            let weightOnly = !(r4ScalePreference?.shouldMeasureImpedance ?? true)
            if !wifiOk && !weightOnly { return .setupIncomplete }
        }
        return .connected
    }
}
