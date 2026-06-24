import Foundation

struct DeviceDTO: Codable, Sendable {
    var broadcastId: Int?
    var broadcastIdString: String?
    var createdAt: String?
    /// Physical hardware type returned by the server (`weight_scale`/`baby_scale`/`bpm`).
    /// Added in Me App 2.0 — present on legacy `/paired-scale/` responses and the unified
    /// `/paired-device/` endpoints. Defaults to `nil` so existing call sites keep compiling.
    var deviceType: String? = nil
    var id: String?
    var isConnected: Bool?
    var isDeleted: Bool?
    var isTemporary: Bool?
    var isWeighOnlyModeEnabledByOthers: Bool?
    var isWifiConfigured: Bool?
    var latestVersion: String?
    var mac: String?
    var metaData: DeviceMetaDataDTO?
    var name: String?
    var nickname: String?
    var password: Int?
    var peripheralIdentifier: String?
    var preference: R4ScalePreferenceDTO?
    var scaleToken: String?
    var sku: String?
    var type: String?
    var userId: String?
    var userNumber: Int?
}
