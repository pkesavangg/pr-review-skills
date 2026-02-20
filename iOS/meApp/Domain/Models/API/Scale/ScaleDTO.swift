import Foundation

struct ScaleDTO: Codable, Sendable {
    var broadcastId: Int?
    var broadcastIdString: String?
    var createdAt: String?
    var id: String?
    var isConnected: Bool?
    var isDeleted: Bool?
    var isTemporary: Bool?
    var isWeighOnlyModeEnabledByOthers: Bool?
    var isWifiConfigured: Bool?
    var latestVersion: String?
    var mac: String?
    var metaData: ScaleMetaDataDTO?
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
