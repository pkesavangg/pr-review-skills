import Foundation
@testable import meApp
import SwiftData
import Testing

@MainActor
extension ScaleRepositoryTests {

    // MARK: - Re-run Safety

    @Test("repeated createScale and deleteScale cycle does not corrupt state")
    func createDeleteCycleIsSafe() async throws {
        let sut = makeSUT()

        for _ in 1...3 {
            try await sut.createScale(ScaleTestFixtures.makeDevice())
            try await sut.deleteScale("scale-1")
        }

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.isEmpty)
    }

    @Test("repeated editScale calls preserve the last written value")
    func repeatedEditScalePreservesLastValue() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice())

        try await sut.editScale("scale-1", properties: ["nickname": "First"])
        try await sut.editScale("scale-1", properties: ["nickname": "Second"])
        try await sut.editScale("scale-1", properties: ["nickname": "Third"])

        let device = try await sut.getDevice("scale-1")
        #expect(device?.nickname == "Third")
    }

    @Test("repeated patchScalePreference fromDTO calls preserve the last written preference")
    func repeatedPatchPreferencePreservesLastValue() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice())

        for i in 1...5 {
            let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "Iteration \(i)")
            try await sut.patchScalePreference("scale-1", fromDTO: dto)
        }

        let pref = sut.fetchAttachedPreference(by: "scale-1")
        #expect(pref?.displayName == "Iteration 5")
    }

    @Test("patchScalePreference non-DTO creates preference when none exists")
    func patchScalePreferenceNonDTOCreatesNew() async throws {
        let sut = makeSUT()
        let device = Device(id: "scale-np", accountId: "acct-1")
        sut.context.insert(device)
        try sut.context.save()

        let preference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-np", displayName: "New Pref"),
            scaleId: "scale-np"
        )
        try await sut.patchScalePreference("scale-np", preference)

        let pref = sut.fetchAttachedPreference(by: "scale-np")
        #expect(pref?.displayName == "New Pref")
        #expect(pref?.isSynced == false)
    }

    @Test("patchScalePreference non-DTO updates existing preference")
    func patchScalePreferenceNonDTOUpdatesExisting() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", displayName: "Old Name")
        try await sut.createScale(device)

        let updatedPreference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "Updated Name"),
            scaleId: "scale-1"
        )
        try await sut.patchScalePreference("scale-1", updatedPreference)

        let pref = sut.fetchAttachedPreference(by: "scale-1")
        #expect(pref?.displayName == "Updated Name")
        #expect(pref?.isSynced == false)
    }

    @Test("patchScalePreference non-DTO sets device isSynced to false")
    func patchScalePreferenceNonDTOSetsSyncedFalse() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        device.isSynced = true
        sut.context.insert(device)
        if let pref = device.r4ScalePreference { sut.context.insert(pref) }
        if let bath = device.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let preference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1"),
            scaleId: "scale-1"
        )
        try await sut.patchScalePreference("scale-1", preference)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.isSynced == false)
    }

    @Test("patchScalePreference non-DTO with nonexistent scale throws")
    func patchScalePreferenceNonDTONotFoundThrows() async throws {
        let sut = makeSUT()
        let preference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(),
            scaleId: "nonexistent"
        )

        await #expect(throws: (any Error).self) {
            try await sut.patchScalePreference("nonexistent", preference)
        }
    }

    // MARK: - Replace-All Sync Edge Cases

    @Test("replaceAllDevicesForAccount matches unsynced device by MAC address")
    func replaceAllDevicesForAccountMatchesByMac() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(
            id: "local-id",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66"
        )
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        let serverDevice = ScaleTestFixtures.makeScaleDTO(
            id: "server-id",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "different-bc"
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.count == 1)
        #expect(scales.first?.id == "server-id")
        #expect(scales.first?.mac == "11:22:33:44:55:66")
    }

    @Test("replaceAllDevicesForAccount matches unsynced device by broadcastId")
    func replaceAllDevicesForAccountMatchesByBroadcastId() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(
            id: "local-id",
            accountId: "acct-1",
            broadcastIdString: "BC-123",
            broadcastId: 999999
        )
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        let serverDevice = ScaleTestFixtures.makeScaleDTO(
            id: "server-id",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "BC-123",
            broadcastId: 999999
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.count == 1)
        #expect(scales.first?.id == "server-id")
        #expect(scales.first?.broadcastIdString == "BC-123")
    }

    @Test("replaceAllDevicesForAccount updates device with new ID when matching")
    func replaceAllDevicesForAccountUpdatesWithNewId() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(
            id: "local-id",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66"
        )
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        let serverDevice = ScaleTestFixtures.makeScaleDTO(
            id: "server-id",
            mac: "11:22:33:44:55:66"
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.count == 1)
        #expect(scales.first?.id == "server-id")
        // Old ID should not exist
        let oldDevice = try await sut.getDevice("local-id")
        #expect(oldDevice == nil)
    }

    @Test("replaceAllDevicesForAccount updates bath scale type")
    func replaceAllDevicesForAccountUpdatesBathScaleType() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        let serverDevice = ScaleTestFixtures.makeScaleDTO(
            id: "scale-1",
            type: "A3"
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.bathScale?.scaleType == "A3")
    }

    @Test("replaceAllDevicesForAccount creates bath scale when none exists")
    func replaceAllDevicesForAccountCreatesBathScale() async throws {
        let sut = makeSUT()
        let device = Device(id: "scale-1", accountId: "acct-1")
        sut.context.insert(device)
        try sut.context.save()
        let unsyncedList = [device]

        let serverDevice = ScaleTestFixtures.makeScaleDTO(
            id: "scale-1",
            type: "R4"
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.bathScale != nil)
        #expect(fetched?.bathScale?.scaleType == "R4")
    }

    @Test("replaceAllDevicesForAccount updates latestVersion from root level")
    func replaceAllDevicesForAccountUpdatesLatestVersion() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        var serverDevice = ScaleTestFixtures.makeScaleDTO(id: "scale-1")
        // Add latestVersion at root level
        serverDevice.latestVersion = "2.0.0"
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData?.latestVersion == "2.0.0")
    }

    @Test("replaceAllDevicesForAccount creates metaData when updating latestVersion")
    func replaceAllDevicesForAccountCreatesMetaDataForLatestVersion() async throws {
        let sut = makeSUT()
        let device = Device(id: "scale-1", accountId: "acct-1")
        sut.context.insert(device)
        try sut.context.save()
        let unsyncedList = [device]

        var serverDevice = ScaleTestFixtures.makeScaleDTO(id: "scale-1")
        serverDevice.latestVersion = "2.0.0"
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData != nil)
        #expect(fetched?.metaData?.latestVersion == "2.0.0")
    }

    @Test("replaceAllDevicesForAccount updates R4 preference from server")
    func replaceAllDevicesForAccountUpdatesR4Preference() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        var serverDevice = ScaleTestFixtures.makeScaleDTO(id: "scale-1")
        serverDevice.preference = ScaleTestFixtures.makePreferenceDTO(
            scaleId: "scale-1",
            displayName: "Server Updated Name"
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let pref = sut.fetchAttachedPreference(by: "scale-1")
        #expect(pref?.displayName == "Server Updated Name")
        #expect(pref?.isSynced == true)
    }

    @Test("replaceAllDevicesForAccount updates metaData from server")
    func replaceAllDevicesForAccountUpdatesMetaData() async throws {
        let sut = makeSUT()
        let unsyncedDevice = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        var serverDevice = ScaleTestFixtures.makeScaleDTO(id: "scale-1")
        var metaDataDTO = ScaleMetaDataDTO()
        metaDataDTO.modelNumber = "R4-Pro"
        metaDataDTO.serialNumber = "SN-999"
        metaDataDTO.firmwareRevision = "FW-2.0"
        metaDataDTO.hardwareRevision = "HW-2.0"
        metaDataDTO.softwareRevision = "SW-2.0"
        metaDataDTO.manufacturerName = "Manufacturer"
        metaDataDTO.systemId = "SYS-123"
        metaDataDTO.latestFirmwareVersion = "3.0.0"
        serverDevice.metaData = metaDataDTO
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData?.modelNumber == "R4-Pro")
        #expect(fetched?.metaData?.serialNumber == "SN-999")
        #expect(fetched?.metaData?.latestVersion == "3.0.0")
        #expect(fetched?.metaData?.isSynced == true)
    }

    @Test("replaceAllDevicesForAccount only matches devices from same account")
    func replaceAllDevicesForAccountOnlyMatchesSameAccount() async throws {
        let sut = makeSUT()
        // Create unsynced device for acct-1
        let unsynced1 = ScaleTestFixtures.makeDevice(
            id: "local-1",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66"
        )
        try await sut.createScale(unsynced1)
        
        // Create unsynced device for acct-2 with same MAC
        let unsynced2 = ScaleTestFixtures.makeDevice(
            id: "local-2",
            accountId: "acct-2",
            mac: "11:22:33:44:55:66"
        )
        try await sut.createScale(unsynced2)
        
        let unsyncedList = try await sut.getUnsyncedDevices()

        // Server device for acct-1 with same MAC
        let serverDevice = ScaleTestFixtures.makeScaleDTO(
            id: "server-1",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66"
        )
        try await sut.replaceAllDevicesForAccount("acct-1", with: [serverDevice], preserveUnsynced: unsyncedList)

        // Only acct-1 device should be matched and updated
        let acct1Devices = try await sut.listScales(forAccountId: "acct-1")
        let acct2Devices = try await sut.listScales(forAccountId: "acct-2")
        #expect(acct1Devices.count == 1)
        #expect(acct1Devices.first?.id == "server-1")
        #expect(acct2Devices.count == 1)
        #expect(acct2Devices.first?.id == "local-2") // Should remain unchanged
    }

    // MARK: - Update Device Relationships

    @Test("updateDevice updates metaData relationship")
    func updateDeviceUpdatesMetaData() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "d-1")
        try await sut.createScale(device)

        let newMeta = ScaleTestFixtures.makeMetaData(modelNumber: "R4-Pro", serialNumber: "SN-2")
        device.metaData = newMeta
        try await sut.updateDevice(device)

        let fetched = try await sut.getDevice("d-1")
        #expect(fetched?.metaData?.modelNumber == "R4-Pro")
        #expect(fetched?.metaData?.serialNumber == "SN-2")
    }

    @Test("updateDevice updates r4ScalePreference relationship")
    func updateDeviceUpdatesR4Preference() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "d-1")
        try await sut.createScale(device)

        let newPreference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "d-1", displayName: "Updated Pref"),
            scaleId: "d-1"
        )
        device.r4ScalePreference = newPreference
        try await sut.updateDevice(device)

        let pref = sut.fetchAttachedPreference(by: "d-1")
        #expect(pref?.displayName == "Updated Pref")
    }

    @Test("updateDevice updates bathScale relationship")
    func updateDeviceUpdatesBathScale() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "d-1")
        try await sut.createScale(device)

        let newBathScale = BathScale(scaleType: "A3", bodyComp: false)
        device.bathScale = newBathScale
        try await sut.updateDevice(device)

        let fetched = try await sut.getDevice("d-1")
        #expect(fetched?.bathScale?.scaleType == "A3")
        #expect(fetched?.bathScale?.bodyComp == false)
    }

    @Test("updateDeviceWithNewId updates relationships")
    func updateDeviceWithNewIdUpdatesRelationships() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "old-id")
        try await sut.createScale(device)

        let updatedDevice = Device(id: "new-id", accountId: "acct-1", nickname: "Renamed")
        let newMeta = ScaleTestFixtures.makeMetaData(modelNumber: "R4-Pro")
        updatedDevice.metaData = newMeta
        let newPreference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "new-id", displayName: "New Pref"),
            scaleId: "new-id"
        )
        updatedDevice.r4ScalePreference = newPreference
        try await sut.updateDeviceWithNewId(oldId: "old-id", updatedDevice: updatedDevice)

        let fetched = try await sut.getDevice("new-id")
        #expect(fetched?.id == "new-id")
        #expect(fetched?.metaData?.modelNumber == "R4-Pro")
        let pref = sut.fetchAttachedPreference(by: "new-id")
        #expect(pref?.displayName == "New Pref")
    }

    // MARK: - Edge Cases

    @Test("isDevicePurelyLocal returns false when synced but no server ID")
    func isDevicePurelyLocalFalseWhenSyncedNoServerId() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "s1", isSynced: true, hasServerID: false)
        device.isSynced = true
        device.hasServerID = false
        sut.context.insert(device)
        if let pref = device.r4ScalePreference { sut.context.insert(pref) }
        if let bath = device.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let result = try await sut.isDevicePurelyLocal("s1")

        #expect(result == false)
    }

    @Test("isDevicePurelyLocal returns false when unsynced but has server ID")
    func isDevicePurelyLocalFalseWhenUnsyncedHasServerId() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "s1", isSynced: false, hasServerID: true)
        device.isSynced = false
        device.hasServerID = true
        sut.context.insert(device)
        if let pref = device.r4ScalePreference { sut.context.insert(pref) }
        if let bath = device.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let result = try await sut.isDevicePurelyLocal("s1")

        #expect(result == false)
    }

    @Test("clearAllData handles empty database")
    func clearAllDataHandlesEmpty() async throws {
        let sut = makeSUT()

        try await sut.clearAllData()
        let scales = try await sut.listScales()

        #expect(scales.isEmpty)
    }

    @Test("getUnsyncedDevices handles nil isSynced values")
    func getUnsyncedDevicesHandlesNilSynced() async throws {
        let sut = makeSUT()
        // Create device with nil isSynced
        let device = Device(id: "nil-synced", accountId: "acct-1")
        device.isSynced = nil
        sut.context.insert(device)
        try sut.context.save()

        let unsynced = try await sut.getUnsyncedDevices()

        #expect(unsynced.count == 1)
        #expect(unsynced.first?.id == "nil-synced")
    }

    @Test("getDevicesMarkedForDeletion handles nil values")
    func getDevicesMarkedForDeletionHandlesNilValues() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "s1")
        device.isSoftDeleted = nil
        sut.context.insert(device)
        if let pref = device.r4ScalePreference { sut.context.insert(pref) }
        if let bath = device.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let forDeletion = try await sut.getDevicesMarkedForDeletion()

        #expect(forDeletion.isEmpty)
    }

    @Test("patchScaleMeta updates all metaData fields")
    func patchScaleMetaUpdatesAllFields() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        try await sut.createScale(device)

        let meta = DeviceMetaData(
            modelNumber: "R4-Pro",
            serialNumber: "SN-999",
            firmwareRevision: "FW-2.0",
            hardwareRevision: "HW-2.0",
            softwareRevision: "SW-2.0",
            manufacturerName: "Manufacturer",
            systemId: "SYS-123",
            latestVersion: "3.0.0"
        )
        try await sut.patchScaleMeta("scale-1", metaData: meta)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData?.modelNumber == "R4-Pro")
        #expect(fetched?.metaData?.serialNumber == "SN-999")
        #expect(fetched?.metaData?.firmwareRevision == "FW-2.0")
        #expect(fetched?.metaData?.hardwareRevision == "HW-2.0")
        #expect(fetched?.metaData?.softwareRevision == "SW-2.0")
        #expect(fetched?.metaData?.manufacturerName == "Manufacturer")
        #expect(fetched?.metaData?.systemId == "SYS-123")
        #expect(fetched?.metaData?.latestVersion == "3.0.0")
    }

    @Test("patchScalePreference fromDTO with isSynced true preserves synced state")
    func patchScalePreferenceFromDTOPreservesSyncedState() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        try await sut.createScale(device)

        let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", isSynced: true)
        try await sut.patchScalePreference("scale-1", fromDTO: dto)

        let pref = sut.fetchAttachedPreference(by: "scale-1")
        #expect(pref?.isSynced == true)
    }

    @Test("replaceAllDevicesForAccount handles multiple server devices")
    func replaceAllDevicesForAccountHandlesMultipleDevices() async throws {
        let sut = makeSUT()
        let syncedDevice = ScaleTestFixtures.makeDevice(id: "synced-1", accountId: "acct-1", isSynced: true)
        syncedDevice.isSynced = true
        sut.context.insert(syncedDevice)
        if let pref = syncedDevice.r4ScalePreference { sut.context.insert(pref) }
        if let bath = syncedDevice.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let serverDevices = [
            ScaleTestFixtures.makeScaleDTO(id: "server-1"),
            ScaleTestFixtures.makeScaleDTO(id: "server-2"),
            ScaleTestFixtures.makeScaleDTO(id: "server-3")
        ]
        try await sut.replaceAllDevicesForAccount("acct-1", with: serverDevices, preserveUnsynced: [])

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.count == 3)
        let ids = scales.map { $0.id }.sorted()
        #expect(ids == ["server-1", "server-2", "server-3"])
    }
}
