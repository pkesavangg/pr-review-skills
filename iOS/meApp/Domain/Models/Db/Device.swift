/// Table: device
///
/// | Column Name          | Type    | Description                         |
/// | -------------------- | ------- | ----------------------------------- |
/// | id                   | string  | Unique device ID (PK, FK)           |
/// | userId               | string  | User identifier                     |
/// | peripheralIdentifier | string  | Bluetooth peripheral ID             |
/// | nickname             | string  | User's nickname for the device      |
/// | sku                  | string  | SKU identifier                      |
/// | mac                  | string  | MAC address                         |
/// | password             | string  | Device password                     |
/// | isDeleted            | boolean | If the device is deleted            |
/// | deviceName           | string  | Device name                         |
/// | deviceType           | string  | Device type (e.g., 'scale', 'bgm')  |
/// | broadcastId          | string  | Broadcast ID                        |
/// | broadcastIdString    | string  | Broadcast ID as string              |
/// | userNumber           | string  | User number                         |
/// | protocolType         | string  | Protocol type (e.g., 'r4', 'a3')    |
/// | createdAt            | string  | Date added                          |
/// | lastModified         | integer | Last modified timestamp             |
/// | isSynced             | boolean | Whether device is synced online     |
/// | isConnected          | boolean | If the scale is currently connected |
/// | wifiMac              | string  | Wifi MAC (R4 scales)                |
/// | isWifiConfigured     | boolean | If WiFi is configured               |
/// | token                | string  | Token for scale authentication      |

import Foundation
import SwiftData

@Model
final class Device {
    @Attribute(.unique) var id: String
    var userId: String
    var peripheralIdentifier: String?
    var nickname: String?
    var sku: String?
    var mac: String?
    var password: String?
    var isDeleted: Bool?
    var deviceName: String?
    var deviceType: String?
    var broadcastId: String?
    var broadcastIdString: String?
    var userNumber: String?
    var protocolType: String?
    var createdAt: String?
    var lastModified: Int?
    var isSynced: Bool?
    var isConnected: Bool?
    var wifiMac: String?
    var isWifiConfigured: Bool?
    var token: String?

    // Relationships
    @Relationship(deleteRule: .cascade) var bathScale: BathScaleEntry?
    @Relationship(deleteRule: .cascade) var r4ScalePreference: R4ScalePreference?
    @Relationship(deleteRule: .cascade) var metaData: DeviceMetaData?

    init(id: String,
         userId: String,
         peripheralIdentifier: String? = nil,
         nickname: String? = nil,
         sku: String? = nil,
         mac: String? = nil,
         password: String? = nil,
         isDeleted: Bool? = nil,
         deviceName: String? = nil,
         deviceType: String? = nil,
         broadcastId: String? = nil,
         broadcastIdString: String? = nil,
         userNumber: String? = nil,
         protocolType: String? = nil,
         createdAt: String? = nil,
         lastModified: Int? = nil,
         isSynced: Bool? = nil,
         isConnected: Bool? = nil,
         wifiMac: String? = nil,
         isWifiConfigured: Bool? = nil,
         token: String? = nil,
         bathScale: BathScaleEntry? = nil,
         r4ScalePreference: R4ScalePreference? = nil,
         metaData: DeviceMetaData? = nil) {
        self.id = id
        self.userId = userId
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
        self.isConnected = isConnected
        self.wifiMac = wifiMac
        self.isWifiConfigured = isWifiConfigured
        self.token = token
        self.bathScale = bathScale
        self.r4ScalePreference = r4ScalePreference
        self.metaData = metaData
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
            type: self.deviceType,
            userId: self.userId,
            userNumber: self.userNumber != nil ? Int(self.userNumber!) : nil
        )
    }
}
