/// Table: device
///
/// Stores user device details for connected devices.
///
/// | Column Name           | Type    | Description                         |
/// | --------------------- | ------- | ----------------------------------- |
/// | id                    | string  | Unique device ID (PK, FK)           |
/// | account_id            | string  | User identifier                     |
/// | peripheral_identifier | string  | Bluetooth peripheral ID             |
/// | nickname              | string  | User's nickname for the device      |
/// | sku                   | string  | SKU identifier                      |
/// | mac                   | string  | MAC address                         |
/// | password              | string  | Device password                     |
/// | is_deleted            | boolean | If the device is deleted            |
/// | device_name           | string  | Device name                         |
/// | device_type           | string  | Device type (e.g., 'scale', 'bgm')  |
/// | broadcast_id          | string  | Broadcast ID                        |
/// | broadcast_id_string   | string  | Broadcast ID as string              |
/// | user_number           | string  | User number                         |
/// | protocol_type         | string  | Protocol type (e.g., 'r4', 'a3')    |
/// | created_at            | string  | Date added                          |
/// | last_modified         | integer | Last modified timestamp             |
/// | is_synced             | boolean | Whether device is synced online     |
/// | has_server_id         | boolean | Whether device has server-assigned ID |
/// | is_connected          | boolean | If the scale is currently connected |
/// | wifi_mac              | string  | Wifi MAC (R4 scales)                |
/// | is_wifi_configured    | boolean | If WiFi is configured               |
/// | token                 | string  | Token for scale authentication      |

import Foundation
import SwiftData

@Model
final class Device {
    @Attribute(.unique) var id: String
    var accountId: String // User identifier
    var peripheralIdentifier: String? // Bluetooth peripheral ID
    var nickname: String? // User's nickname for the device
    var sku: String? // SKU identifier
    var mac: String? // MAC address
    var password: Int64? // Device password
    var isDeleted: Bool? // If the device is deleted
    var deviceName: String? // Device name
    var deviceType: String? // Device type (e.g., 'scale', 'bgm')
    var broadcastId: Int64? // Broadcast ID
    var broadcastIdString: String? // Broadcast ID as string
    var userNumber: String? // User number
    var protocolType: String? // Protocol type (e.g., 'r4', 'a3')
    var createdAt: String? // Date added
    var lastModified: Int? // Last modified timestamp
    var isSynced: Bool? // Whether device is synced online
    var hasServerID: Bool // Whether device has been assigned a server ID
    var isConnected: Bool? // If the scale is currently connected
    var wifiMac: String? // Wifi MAC (R4 scales)
    var isWifiConfigured: Bool? // If WiFi is configured
    var token: String? // Token for scale authentication

    // Relationships
    @Relationship(deleteRule: .cascade, inverse: \BathScale.device) var bathScale: BathScale?
    @Relationship(deleteRule: .cascade, inverse: \R4ScalePreference.device) var r4ScalePreference: R4ScalePreference?
    @Relationship(deleteRule: .cascade, inverse: \DeviceMetaData.device) var metaData: DeviceMetaData?

    init(id: String,
         accountId: String,
         peripheralIdentifier: String? = nil,
         nickname: String? = nil,
         sku: String? = nil,
         mac: String? = nil,
         password: Int64? = nil,
         isDeleted: Bool? = nil,
         deviceName: String? = nil,
         deviceType: String? = nil,
         broadcastId: Int64? = nil,
         broadcastIdString: String? = nil,
         userNumber: String? = nil,
         protocolType: String? = nil,
         createdAt: String? = nil,
         lastModified: Int? = nil,
         isSynced: Bool? = nil,
         hasServerID: Bool = false,
         isConnected: Bool? = nil,
         wifiMac: String? = nil,
         isWifiConfigured: Bool? = nil,
         token: String? = nil,
         bathScale: BathScale? = nil,
         r4ScalePreference: R4ScalePreference? = nil,
         metaData: DeviceMetaData? = nil) {
        self.id = id
        self.accountId = accountId
        self.peripheralIdentifier = peripheralIdentifier
        self.nickname = nickname
        self.sku = sku
        self.mac = mac
        self.password = password
        self.isDeleted = isDeleted
        self.deviceName = deviceName
        self.deviceType = deviceType
        self.broadcastId = broadcastId
        self.broadcastIdString = broadcastIdString
        self.userNumber = userNumber
        self.protocolType = protocolType
        self.createdAt = createdAt
        self.lastModified = lastModified
        self.isSynced = isSynced
        self.hasServerID = hasServerID
        self.isConnected = isConnected
        self.wifiMac = wifiMac
        self.isWifiConfigured = isWifiConfigured
        self.token = token
        self.bathScale = bathScale
        self.r4ScalePreference = r4ScalePreference
        self.metaData = metaData

        if let broadcastId = broadcastId {
            let scaleSource = ScaleSourceType(rawValue: bathScale?.scaleType ?? "") ?? .bluetoothScale
            let protocolType = ProtocolConversionTools.getProtocolTypeFromScaleType(scaleType: scaleSource)
            self.broadcastIdString = ProtocolConversionTools.convertIntToHex(Int(broadcastId), protocolType: protocolType)
        }

    }
    convenience init(from dto: ScaleDTO,
                     protocolType: String? = nil,
                     isSynced: Bool? = nil,
                     lastModified: Int? = nil,
                     isConnected: Bool? = nil,
                     isWifiConfigured: Bool? = nil,
                     scaleType: String? = nil,
                     bodyComp: Bool? = nil) {
        let id = dto.id ?? UUID().uuidString

        // Create R4ScalePreference first if needed
        var r4Preference: R4ScalePreference? = nil
        if let preference = dto.preference {
            r4Preference = R4ScalePreference(from: preference, scaleId: id)
        }

        var metaData: DeviceMetaData? = nil
        if let metaDataDto = dto.metaData {
            metaData = DeviceMetaData(from: metaDataDto)
        }

        var bathScale: BathScale? = nil
        if let scaleType = scaleType {
            bathScale = BathScale(scaleType: scaleType, bodyComp: bodyComp)
        }

        self.init(
            id: id,
            accountId: dto.userId ?? "",
            peripheralIdentifier: dto.peripheralIdentifier,
            nickname: dto.nickname,
            sku: dto.sku,
            mac: dto.mac,
            password: dto.password.map { Int64($0) },
            isDeleted: dto.isDeleted,
            deviceName: dto.name,
            deviceType: "scale",
            broadcastId: dto.broadcastId.map { Int64($0) },
            broadcastIdString: dto.broadcastIdString,
            userNumber: dto.userNumber.map { String($0) },
            protocolType: protocolType,
            createdAt: dto.createdAt,
            lastModified: lastModified,
            isSynced: isSynced,
            hasServerID: dto.id != nil, // True if DTO has server ID, false for local devices
            isConnected: dto.isConnected,
            wifiMac: dto.metaData?.wifiMac,
            isWifiConfigured: dto.isWifiConfigured,
            token: dto.scaleToken,
            bathScale: bathScale,
            r4ScalePreference: r4Preference,
            metaData: metaData
        )

        // Set the device reference after initialization
        if let preference = self.r4ScalePreference {
            preference.device = self
        }
        if let scale = self.bathScale {
            scale.device = self
        }
        if let metaData = self.metaData {
            metaData.device = self
        }

        if let broadcastId = self.broadcastId {
            let scaleSource = ScaleSourceType(rawValue: self.bathScale?.scaleType ?? "") ?? .bluetoothScale
            let protocolType = ProtocolConversionTools.getProtocolTypeFromScaleType(scaleType: scaleSource)
            self.broadcastIdString = ProtocolConversionTools.convertIntToHex(Int(broadcastId), protocolType: protocolType)
        }
    }

    func toDTO() -> ScaleDTO {
        return ScaleDTO(
            broadcastId: self.broadcastId != nil ? Int(self.broadcastId!) : nil,
            broadcastIdString: self.broadcastIdString,
            createdAt: self.createdAt,
            id: self.id,
            isConnected: self.isConnected,
            isDeleted: self.isDeleted,
            isTemporary: nil,
            isWeighOnlyModeEnabledByOthers: nil,
            isWifiConfigured: self.isWifiConfigured,
            latestVersion: self.metaData?.latestVersion,
            mac: self.mac,
            metaData: self.metaData?.toDTO(),
            name: self.deviceName,
            nickname: self.nickname,
            password: self.password != nil ? Int(self.password!) : nil,
            peripheralIdentifier: self.peripheralIdentifier,
            preference: self.r4ScalePreference?.toDTO(),
            scaleToken: self.token,
            sku: self.sku,
            type: self.bathScale?.scaleType,
            userId: self.accountId,
            userNumber: self.userNumber != nil ? Int(self.userNumber!) : nil
        )
    }
}

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension Device: @unchecked Sendable {}
// Extend `Device` to conform to `Identifiable` so it can be used with SwiftUI's `.sheet(item:)` API.
extension Device: Identifiable {}
