import Foundation

extension DeviceSnapshot {
    /// Creates a mutable `Device` @Model from this snapshot for use in construction-path flows
    /// (e.g. BPM pairing, Bluetooth scale setup). The returned `Device` is NOT inserted into any
    /// SwiftData context — it is an in-memory object suitable for mutation before persistence.
    func toDevice() -> Device {
        Device(
            id: id,
            accountId: accountId,
            peripheralIdentifier: peripheralIdentifier,
            nickname: nickname,
            sku: sku,
            mac: mac,
            password: password,
            isSoftDeleted: isSoftDeleted,
            deviceName: deviceName,
            deviceType: deviceType,
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            userNumber: userNumber,
            protocolType: protocolType,
            createdAt: createdAt,
            lastModified: lastModified,
            isSynced: isSynced,
            hasServerID: hasServerID,
            token: token
        )
    }
}

extension Device {
    func toSnapshot(
        isConnected: Bool = false,
        isWifiConfigured: Bool = false,
        isWeighOnlyModeEnabledByOthers: Bool = false
    ) -> DeviceSnapshot {
        DeviceSnapshot(
            id: id,
            accountId: accountId,
            peripheralIdentifier: peripheralIdentifier,
            nickname: nickname,
            sku: sku,
            mac: mac,
            password: password,
            isSoftDeleted: isSoftDeleted,
            deviceName: deviceName,
            deviceType: deviceType,
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            userNumber: userNumber,
            protocolType: protocolType,
            createdAt: createdAt,
            lastModified: lastModified,
            isSynced: isSynced,
            hasServerID: hasServerID,
            wifiMac: wifiMac,
            token: token,
            isConnected: isConnected,
            isWifiConfigured: isWifiConfigured,
            isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers,
            bathScale: bathScale?.toSnapshot(),
            r4ScalePreference: r4ScalePreference?.toSnapshot(),
            metaData: metaData?.toSnapshot()
        )
    }
}

extension DeviceMetaData {
    func toSnapshot() -> DeviceMetaDataSnapshot {
        DeviceMetaDataSnapshot(
            modelNumber: modelNumber,
            serialNumber: serialNumber,
            firmwareRevision: firmwareRevision,
            hardwareRevision: hardwareRevision,
            softwareRevision: softwareRevision,
            manufacturerName: manufacturerName,
            systemId: systemId,
            latestVersion: latestVersion,
            isSynced: isSynced
        )
    }
}
