import Foundation
import SwiftData

@Model
final class Scale {
    @Attribute(.unique) var id: String
    var sku: String?
    var nickname: String?
    var name: String?
    var type: String?
    var userId: String?
    var peripheralIdentifier: String?
    @Relationship(deleteRule: .cascade) var preference: R4ScalePreference?
    @Relationship(deleteRule: .cascade) var metaData: ScaleMetaData?
    var userNumber: Int?
    var scaleToken: String?
    var broadcastId: Int?
    var broadcastIdString: String?
    var latestVersion: String?
    var mac: String?
    var password: Int?
    var createdAt: String?
    var isTemporary: Bool?
    var isDeleted: Bool?
    var isWifiConfigured: Bool?
    var isConnected: Bool?
    var isWeighOnlyModeEnabledByOthers: Bool?
    
    init(from dto: ScaleDTO) {
        self.id = dto.id ?? UUID().uuidString
        self.sku = dto.sku
        self.nickname = dto.nickname
        self.name = dto.name
        self.type = dto.type
        self.userId = dto.userId
        self.peripheralIdentifier = dto.peripheralIdentifier
        self.preference = dto.preference.map { R4ScalePreference(from: $0) }
        self.metaData = dto.metaData.map { ScaleMetaData(from: $0) }
        self.userNumber = dto.userNumber
        self.scaleToken = dto.scaleToken
        self.broadcastId = dto.broadcastId
        self.broadcastIdString = dto.broadcastIdString
        self.latestVersion = dto.latestVersion
        self.mac = dto.mac
        self.password = dto.password
        self.createdAt = dto.createdAt
        self.isTemporary = dto.isTemporary
        self.isDeleted = dto.isDeleted
        self.isWifiConfigured = dto.isWifiConfigured
        self.isConnected = dto.isConnected
        self.isWeighOnlyModeEnabledByOthers = dto.isWeighOnlyModeEnabledByOthers
    }
    
    func toScaleDTO() -> ScaleDTO {
        return ScaleDTO(
            broadcastId: self.broadcastId,
            broadcastIdString: self.broadcastIdString,
            createdAt: self.createdAt,
            id: self.id,
            isConnected: self.isConnected,
            isDeleted: self.isDeleted,
            isTemporary: self.isTemporary,
            isWeighOnlyModeEnabledByOthers: self.isWeighOnlyModeEnabledByOthers,
            isWifiConfigured: self.isWifiConfigured,
            latestVersion: self.latestVersion,
            mac: self.mac,
            metaData: self.metaData?.toDTO(),
            name: self.name,
            nickname: self.nickname,
            password: self.password,
            peripheralIdentifier: self.peripheralIdentifier,
            preference: self.preference?.toDTO(),
            scaleToken: self.scaleToken,
            sku: self.sku,
            type: self.type,
            userId: self.userId,
            userNumber: self.userNumber
        )
    }
}
