import Foundation

/// Request body for `POST /v3/paired-device/` — the Me App 2.0 unified device-pairing endpoint
/// that replaces the per-product `POST /v3/paired-scale/` flow. Used for weight scales, baby
/// scales, and blood-pressure monitors alike. The server routes by `deviceType` and auto-adds the
/// corresponding product to the account's `productTypes`.
struct PairedDeviceRequest: Codable, Sendable {
    // MARK: - Required
    /// Physical hardware type: `weight_scale`, `baby_scale`, or `bpm`.
    let deviceType: String
    /// Connection type. Devices: `wifi`, `bluetooth`, `appsync`, `lcbt`, `btWifiR4`.
    /// BPM: `bpmBluetooth`, `bpmLcbt`.
    let type: String
    /// User-friendly device name.
    let nickname: String
    /// Product SKU.
    let sku: String

    // MARK: - Optional
    let mac: String?
    let broadcastId: Int?
    let password: Int?
    let userNumber: Int?
    let name: String?
    let peripheralIdentifier: String?
    /// WiFi scales only.
    let scaleToken: String?

    init(
        deviceType: String,
        type: String,
        nickname: String,
        sku: String,
        mac: String? = nil,
        broadcastId: Int? = nil,
        password: Int? = nil,
        userNumber: Int? = nil,
        name: String? = nil,
        peripheralIdentifier: String? = nil,
        scaleToken: String? = nil
    ) {
        self.deviceType = deviceType
        self.type = type
        self.nickname = nickname
        self.sku = sku
        self.mac = mac
        self.broadcastId = broadcastId
        self.password = password
        self.userNumber = userNumber
        self.name = name
        self.peripheralIdentifier = peripheralIdentifier
        self.scaleToken = scaleToken
    }
}

/// Request body for `PATCH /v3/paired-device/:deviceId`. The unified endpoint accepts a partial
/// update; today only the user-editable `nickname` is mutable from the app.
struct PairedDeviceUpdateRequest: Codable, Sendable {
    let nickname: String?

    init(nickname: String? = nil) {
        self.nickname = nickname
    }
}
