/// Table: device_meta_data
///
/// | Column Name       | Type   | Description                       |
/// | ----------------- | ------ | --------------------------------- |
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
    var modelNumber: String? // Model number
    var serialNumber: String? // Serial number
    var firmwareRevision: String? // Firmware revision
    var hardwareRevision: String? // Hardware revision
    var softwareRevision: String? // Software revision
    var manufacturerName: String? // Manufacturer name
    var systemId: String? // Device MAC (A3 scales)
    var latestVersion: String? // Latest firmware version
    var isSynced: Bool = false // Flag to check if the meta data is synced with the server
    // Inverse relationship to Device
    var device: Device?

    init(from dto: ScaleMetaDataDTO, device: Device? = nil) {
        self.modelNumber = dto.modelNumber
        self.serialNumber = dto.serialNumber
        self.firmwareRevision = dto.firmwareRevision
        self.hardwareRevision = dto.hardwareRevision
        self.softwareRevision = dto.softwareRevision
        self.manufacturerName = dto.manufacturerName
        self.systemId = dto.systemId
        self.latestVersion = dto.latestFirmwareVersion
        self.device = device
    }

    init(modelNumber: String? = nil, serialNumber: String? = nil, firmwareRevision: String? = nil, hardwareRevision: String? = nil, softwareRevision: String? = nil, manufacturerName: String? = nil, systemId: String? = nil, latestVersion: String? = nil, device: Device? = nil) {
        self.modelNumber = modelNumber
        self.serialNumber = serialNumber
        self.firmwareRevision = firmwareRevision
        self.hardwareRevision = hardwareRevision
        self.softwareRevision = softwareRevision
        self.manufacturerName = manufacturerName
        self.systemId = systemId
        self.latestVersion = latestVersion
        self.device = device
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

/// Marked @unchecked Sendable due to SwiftData’s built-in thread safety, allowing async/concurrent use.
extension DeviceMetaData: @unchecked Sendable {}
