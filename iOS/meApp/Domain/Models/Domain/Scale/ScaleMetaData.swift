import Foundation
import SwiftData

@Model
final class ScaleMetaData {
    @Attribute(.unique) var id: String
    var modelNumber: String?
    var serialNumber: String?
    var firmwareRevision: String?
    var hardwareRevision: String?
    var softwareRevision: String?
    var manufacturerName: String?
    var systemId: String?
    var wifiMac: String?

    init(from dto: ScaleMetaDataDTO, id: String? = nil) {
        self.id =  id ?? UUID().uuidString
        self.modelNumber = dto.modelNumber
        self.serialNumber = dto.serialNumber
        self.firmwareRevision = dto.firmwareRevision
        self.hardwareRevision = dto.hardwareRevision
        self.softwareRevision = dto.softwareRevision
        self.manufacturerName = dto.manufacturerName
        self.systemId = dto.systemId
        self.wifiMac = dto.wifiMac
    }

    func toDTO() -> ScaleMetaDataDTO {
        return ScaleMetaDataDTO(
            firmwareRevision: self.firmwareRevision,
            hardwareRevision: self.hardwareRevision,
            manufacturerName: self.manufacturerName,
            modelNumber: self.modelNumber,
            serialNumber: self.serialNumber,
            softwareRevision: self.softwareRevision,
            systemId: self.systemId,
            wifiMac: self.wifiMac
        )
    }
}
