/// Table: device_meta_data
///
/// | Column Name       | Type   | Description                       |
/// | ----------------- | ------ | --------------------------------- |
/// | id                | string | Unique scale ID (PK, FK to scale) |
/// | model_number      | string | Model number                      |
/// | serial_number     | string | Serial number                     |
/// | firmware_revision | string | Firmware revision                 |
/// | hardware_revision | string | Hardware revision                 |
/// | software_revision | string | Software revision                 |
/// | manufacturer_name | string | Manufacturer name                 |
/// | system_id         | string | Device MAC (A3 scales)            |
/// | latest_version    | string | Latest firmware version           |
///

import Foundation
import SwiftData

@Model
final class DeviceMetaData {
    @Attribute(.unique) var id: String // Unique scale ID (PK, FK to scale)
    var modelNumber: String? // Model number
    var serialNumber: String? // Serial number
    var firmwareRevision: String? // Firmware revision
    var hardwareRevision: String? // Hardware revision
    var softwareRevision: String? // Software revision
    var manufacturerName: String? // Manufacturer name
    var systemId: String? // Device MAC (A3 scales)
    var latestVersion: String? // Latest firmware version

    init(from dto: ScaleMetaDataDTO, id: String? = nil) {
        self.id =  id ?? UUID().uuidString
        self.modelNumber = dto.modelNumber
        self.serialNumber = dto.serialNumber
        self.firmwareRevision = dto.firmwareRevision
        self.hardwareRevision = dto.hardwareRevision
        self.softwareRevision = dto.softwareRevision
        self.manufacturerName = dto.manufacturerName
        self.systemId = dto.systemId
        self.latestVersion = dto.latestFirmwareVersion
    }

    func toDTO() -> ScaleMetaDataDTO {
        return ScaleMetaDataDTO(
            firmwareRevision: self.firmwareRevision,
            hardwareRevision: self.hardwareRevision,
            latestFirmwareVersion: self.latestVersion,
            manufacturerName: self.manufacturerName,
            modelNumber: self.modelNumber,
            serialNumber: self.serialNumber,
            softwareRevision: self.softwareRevision,
            systemId: self.systemId
        )
    }
}
