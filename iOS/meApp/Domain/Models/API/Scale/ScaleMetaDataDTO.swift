import Foundation

struct ScaleMetaDataDTO: Codable {
    var firmwareRevision: String?
    var hardwareRevision: String?
    var latestFirmwareVersion: String?
    var manufacturerName: String?
    var modelNumber: String?
    var serialNumber: String?
    var softwareRevision: String?
    var systemId: String? // A3 scales only
    var wifiMac: String?  // R4 scales only
}
