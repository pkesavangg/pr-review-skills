import Foundation
@testable import meApp

enum ScaleTestError: Error, Equatable {
    case localFailure
    case remoteFailure
}

enum ScaleTestFixtures {
    static func makePreferenceDTO(
        scaleId: String = "scale-1",
        displayName: String = "Bathroom Scale",
        displayMetrics: [String] = ["weight", "bodyFat"],
        shouldFactoryReset: Bool = false,
        shouldMeasureImpedance: Bool = true,
        shouldMeasurePulse: Bool = false,
        timeFormat: String = "12",
        tzOffset: Int = 330,
        wifiFotaScheduleTime: Int? = 0,
        updatedAt: String? = "2026-03-03T00:00:00Z",
        isSynced: Bool? = false
    ) -> R4ScalePreferenceDTO {
        R4ScalePreferenceDTO(
            scaleId: scaleId,
            displayName: displayName,
            displayMetrics: displayMetrics,
            shouldFactoryReset: shouldFactoryReset,
            shouldMeasureImpedance: shouldMeasureImpedance,
            shouldMeasurePulse: shouldMeasurePulse,
            timeFormat: timeFormat,
            tzOffset: tzOffset,
            wifiFotaScheduleTime: wifiFotaScheduleTime,
            updatedAt: updatedAt,
            isTemporary: nil,
            isSynced: isSynced
        )
    }

    static func makeDevice(
        id: String = "scale-1",
        accountId: String = "acct-1",
        displayName: String = "Bathroom Scale",
        token: String = "token-1",
        mac: String? = "AA:BB:CC:DD:EE:FF",
        broadcastIdString: String? = "A1B2C3",
        broadcastId: Int64? = 123456,
        sku: String = "R4-001",
        deviceName: String = "AccuCheck Verve Smart Scale",
        isSynced: Bool = false,
        hasServerID: Bool = false,
        isSoftDeleted: Bool? = nil
    ) -> Device {
        let device = Device(
            id: id,
            accountId: accountId,
            nickname: "AccuCheck Verve Smart Scale",
            sku: sku,
            mac: mac,
            isSoftDeleted: isSoftDeleted,
            deviceName: deviceName,
            deviceType: DeviceType.scale.rawValue,
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            userNumber: "0",
            createdAt: "2026-03-03T00:00:00Z",
            isSynced: isSynced,
            hasServerID: hasServerID,
            isConnected: false,
            wifiMac: mac,
            isWifiConfigured: false,
            token: token
        )
        device.bathScale = BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        device.r4ScalePreference = R4ScalePreference(
            from: makePreferenceDTO(scaleId: id, displayName: displayName),
            scaleId: id
        )
        return device
    }

    static func makeScaleDTO(
        id: String? = "scale-1",
        accountId: String = "acct-1",
        displayName: String = "Bathroom Scale",
        mac: String = "AA:BB:CC:DD:EE:FF",
        broadcastIdString: String = "A1B2C3",
        broadcastId: Int = 123456,
        sku: String = "R4-001",
        deviceName: String = "AccuCheck Verve Smart Scale",
        token: String = "token-1"
    ) -> ScaleDTO {
        ScaleDTO(
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            createdAt: "2026-03-03T00:00:00Z",
            id: id,
            isConnected: false,
            isDeleted: false,
            isTemporary: false,
            isWeighOnlyModeEnabledByOthers: false,
            isWifiConfigured: false,
            latestVersion: nil,
            mac: mac,
            metaData: nil,
            name: deviceName,
            nickname: "AccuCheck Verve Smart Scale",
            password: nil,
            peripheralIdentifier: mac.replacingOccurrences(of: ":", with: ""),
            preference: makePreferenceDTO(scaleId: id ?? "temp-scale", displayName: displayName),
            scaleToken: token,
            sku: sku,
            type: ScaleSourceType.btWifiR4.rawValue,
            userId: accountId,
            userNumber: 0
        )
    }

    static func makeMetaData(
        modelNumber: String = "R4",
        serialNumber: String = "serial-1",
        latestVersion: String = "1.0.0",
        isSynced: Bool = false
    ) -> DeviceMetaData {
        let metaData = DeviceMetaData(
            modelNumber: modelNumber,
            serialNumber: serialNumber,
            latestVersion: latestVersion
        )
        metaData.isSynced = isSynced
        return metaData
    }

    static func makeScaleMetaDataDTO(
        modelNumber: String? = "R4",
        serialNumber: String? = "serial-1",
        latestFirmwareVersion: String? = "1.0.0",
        firmwareRevision: String? = nil,
        hardwareRevision: String? = nil,
        softwareRevision: String? = nil,
        manufacturerName: String? = nil,
        systemId: String? = nil,
        wifiMac: String? = "AA:BB:CC:DD:EE:FF"
    ) -> ScaleMetaDataDTO {
        ScaleMetaDataDTO(
            firmwareRevision: firmwareRevision,
            hardwareRevision: hardwareRevision,
            latestFirmwareVersion: latestFirmwareVersion,
            manufacturerName: manufacturerName,
            modelNumber: modelNumber,
            serialNumber: serialNumber,
            softwareRevision: softwareRevision,
            systemId: systemId,
            wifiMac: wifiMac
        )
    }
}
