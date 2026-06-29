import Foundation
@testable import meApp
import SwiftData
import Testing

@Suite(.serialized)
@MainActor
struct ScaleRepositoryTests {

    // MARK: - Factory

    func makeSUT() -> ScaleRepository {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        do {
            let container = try ModelContainer(
                for: Device.self,
                BathScale.self,
                R4ScalePreference.self,
                DeviceMetaData.self,
                configurations: config
            )
            return ScaleRepository(context: ModelContext(container))
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }

    // MARK: - Insert

    @Test("createScale persists device and listScales retrieves it")
    func createAndListScales() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice()

        try await sut.createScale(device)
        let scales = try await sut.listScales(forAccountId: "acct-1")

        #expect(scales.count == 1)
        #expect(scales.first?.id == "scale-1")
    }

    @Test("createScale sets isSynced to false regardless of input")
    func createScaleSetsSyncedFalse() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(isSynced: true)

        let created = try await sut.createScale(device)

        #expect(created.isSynced == false)
    }

    @Test("createScale returns the created device with correct fields")
    func createScaleReturnsDevice() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "d-1", accountId: "acct-2")

        let created = try await sut.createScale(device)

        #expect(created.id == "d-1")
        #expect(created.accountId == "acct-2")
    }

    @Test("createScale preserves R4 preference relationship")
    func createScalePreservesPreference() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", displayName: "My Scale")

        try await sut.createScale(device)
        let preference = sut.fetchAttachedPreference(by: "scale-1")

        #expect(preference != nil)
        #expect(preference?.displayName == "My Scale")
    }

    @Test("createScale with duplicate ID throws on second insert")
    func createScaleDuplicateIdThrows() async throws {
        let sut = makeSUT()
        let device1 = ScaleTestFixtures.makeDevice(id: "dup-1")
        let device2 = ScaleTestFixtures.makeDevice(id: "dup-1")

        try await sut.createScale(device1)

        await #expect(throws: (any Error).self) {
            try await sut.createScale(device2)
        }
    }

    // MARK: - Update

    @Test("editScale updates nickname")
    func editScaleUpdatesNickname() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice()
        try await sut.createScale(device)

        let updated = try await sut.editScale("scale-1", properties: ["nickname": "My Bathroom Scale"])

        #expect(updated.nickname == "My Bathroom Scale")
    }

    @Test("editScale sets isSynced to false")
    func editScaleSetsSyncedFalse() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice()
        try await sut.createScale(device)

        let updated = try await sut.editScale("scale-1", properties: ["nickname": "Updated"])

        #expect(updated.isSynced == false)
    }

    @Test("editScale with nonexistent ID throws 404")
    func editScaleNotFoundThrows() async throws {
        let sut = makeSUT()

        await #expect(throws: (any Error).self) {
            try await sut.editScale("nonexistent", properties: ["nickname": "X"])
        }
    }

    @Test("updateDevice persists field changes")
    func updateDevicePersistsFields() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "d-1")
        try await sut.createScale(device)

        // Modifying the live SwiftData object and persisting via updateDevice
        device.nickname = "Updated Nick"
        device.isSynced = true
        try await sut.updateDevice(device)

        let fetched = try await sut.getDevice("d-1")
        #expect(fetched?.nickname == "Updated Nick")
        #expect(fetched?.isSynced == true)
    }

    @Test("updateDevice with nonexistent ID throws 404")
    func updateDeviceNotFoundThrows() async throws {
        let sut = makeSUT()
        // Device not in context — use a fresh Device without inserting it
        let ghost = Device(id: "ghost-id", accountId: "acct-1")

        await #expect(throws: (any Error).self) {
            try await sut.updateDevice(ghost)
        }
    }

    @Test("updateDeviceWithNewId replaces the device ID in storage")
    func updateDeviceWithNewIdReplacesId() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "old-id")
        try await sut.createScale(device)

        // Pass a plain Device (no relationships) to avoid preference ID conflicts
        let updatedDevice = Device(id: "new-id", accountId: "acct-1", nickname: "Renamed")
        try await sut.updateDeviceWithNewId(oldId: "old-id", updatedDevice: updatedDevice)

        let fetched = try await sut.getDevice("new-id")
        #expect(fetched != nil)
        #expect(fetched?.id == "new-id")
    }

    @Test("updateDeviceWithNewId with nonexistent oldId throws 404")
    func updateDeviceWithNewIdNotFoundThrows() async throws {
        let sut = makeSUT()
        let updatedDevice = Device(id: "new-id", accountId: "acct-1")

        await #expect(throws: (any Error).self) {
            try await sut.updateDeviceWithNewId(oldId: "nonexistent", updatedDevice: updatedDevice)
        }
    }

    // MARK: - Delete

    @Test("deleteScale removes device from storage")
    func deleteScaleRemovesDevice() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice()
        try await sut.createScale(device)

        try await sut.deleteScale("scale-1")
        let scales = try await sut.listScales(forAccountId: "acct-1")

        #expect(scales.isEmpty)
    }

    @Test("deleteScale with nonexistent ID does not throw")
    func deleteScaleNonexistentSilent() async throws {
        let sut = makeSUT()

        try await sut.deleteScale("nonexistent")
        // No throw — success
    }

    @Test("clearAllData removes all devices")
    func clearAllDataRemovesAll() async throws {
        let sut = makeSUT()
        for i in 1...3 {
            try await sut.createScale(ScaleTestFixtures.makeDevice(id: "scale-\(i)"))
        }

        try await sut.clearAllData()
        let scales = try await sut.listScales()

        #expect(scales.isEmpty)
    }

    // MARK: - Preference Linkage

    @Test("patchScalePreference fromDTO creates preference when none exists")
    func patchScalePreferenceCreatesNew() async throws {
        let sut = makeSUT()
        // Insert a device without a preference
        let device = Device(id: "scale-np", accountId: "acct-1")
        sut.context.insert(device)
        try sut.context.save()

        let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-np", displayName: "New Pref")
        try await sut.patchScalePreference("scale-np", fromDTO: dto)

        let pref = sut.fetchAttachedPreference(by: "scale-np")
        #expect(pref?.displayName == "New Pref")
    }

    @Test("patchScalePreference fromDTO updates existing preference")
    func patchScalePreferenceUpdatesExisting() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", displayName: "Old Name")
        try await sut.createScale(device)

        let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "Updated Name")
        try await sut.patchScalePreference("scale-1", fromDTO: dto)

        let pref = sut.fetchAttachedPreference(by: "scale-1")
        #expect(pref?.displayName == "Updated Name")
    }

    @Test("repeated patchScalePreference calls do not create duplicate preferences")
    func patchScalePreferenceNoDuplicates() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        try await sut.createScale(device)

        let dto1 = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "First")
        let dto2 = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "Second")
        try await sut.patchScalePreference("scale-1", fromDTO: dto1)
        try await sut.patchScalePreference("scale-1", fromDTO: dto2)

        // Only one preference with this ID should exist; it should reflect the last update
        let pref = sut.fetchAttachedPreference(by: "scale-1")
        #expect(pref?.displayName == "Second")
    }

    @Test("patchScalePreference fromDTO with nonexistent scale throws")
    func patchScalePreferenceNotFoundThrows() async throws {
        let sut = makeSUT()
        let dto = ScaleTestFixtures.makePreferenceDTO()

        await #expect(throws: (any Error).self) {
            try await sut.patchScalePreference("nonexistent", fromDTO: dto)
        }
    }

    @Test("fetchAttachedPreference returns nil for nonexistent scale ID")
    func fetchAttachedPreferenceNil() {
        let sut = makeSUT()

        let pref = sut.fetchAttachedPreference(by: "nonexistent")

        #expect(pref == nil)
    }

    @Test("fetchAttachedPreferenceSync returns same result as fetchAttachedPreference")
    func fetchAttachedPreferenceSyncConsistent() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", displayName: "Test Scale")
        try await sut.createScale(device)

        let asyncResult = sut.fetchAttachedPreference(by: "scale-1")
        let syncResult = sut.fetchAttachedPreferenceSync(by: "scale-1")

        #expect(asyncResult?.displayName == syncResult?.displayName)
    }

    // MARK: - Fetch Consistency

    @Test("listScales forAccountId filters by account")
    func listScalesFiltersByAccount() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s1", accountId: "acct-A"))
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s2", accountId: "acct-B"))
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s3", accountId: "acct-A"))

        let accountA = try await sut.listScales(forAccountId: "acct-A")
        let accountB = try await sut.listScales(forAccountId: "acct-B")

        #expect(accountA.count == 2)
        #expect(accountB.count == 1)
    }

    @Test("listScales returns all devices regardless of account")
    func listScalesReturnsAll() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s1", accountId: "acct-A"))
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s2", accountId: "acct-B"))

        let all = try await sut.listScales()

        #expect(all.count == 2)
    }

    @Test("listScales forAccountId returns empty for unknown account")
    func listScalesForUnknownAccountEmpty() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s1", accountId: "acct-A"))

        let results = try await sut.listScales(forAccountId: "acct-unknown")

        #expect(results.isEmpty)
    }

    @Test("getDevice returns device by ID")
    func getDeviceById() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "target-id"))

        let device = try await sut.getDevice("target-id")

        #expect(device != nil)
        #expect(device?.id == "target-id")
    }

    @Test("getDevice returns nil for nonexistent ID")
    func getDeviceNilForUnknown() async throws {
        let sut = makeSUT()

        let device = try await sut.getDevice("does-not-exist")

        #expect(device == nil)
    }

    @Test("getUnsyncedDevices returns only devices where isSynced is false")
    func getUnsyncedDevicesFiltersCorrectly() async throws {
        let sut = makeSUT()

        // createScale always sets isSynced = false
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s1"))

        // Insert a synced device directly to bypass createScale's forced isSynced=false
        let syncedDevice = ScaleTestFixtures.makeDevice(id: "s2", isSynced: true)
        syncedDevice.isSynced = true
        sut.context.insert(syncedDevice)
        if let pref = syncedDevice.r4ScalePreference { sut.context.insert(pref) }
        if let bath = syncedDevice.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let unsynced = try await sut.getUnsyncedDevices()

        #expect(unsynced.count == 1)
        #expect(unsynced.first?.id == "s1")
    }

    // MARK: - Meta Data

    @Test("patchScaleMeta creates meta data when device has none")
    func patchScaleMetaCreatesNew() async throws {
        let sut = makeSUT()
        let device = Device(id: "scale-nm", accountId: "acct-1")
        sut.context.insert(device)
        try sut.context.save()

        let meta = ScaleTestFixtures.makeMetaData(modelNumber: "R4", serialNumber: "SN-1")
        try await sut.patchScaleMeta("scale-nm", metaData: meta)

        let fetched = try await sut.getDevice("scale-nm")
        #expect(fetched?.metaData?.modelNumber == "R4")
        #expect(fetched?.metaData?.serialNumber == "SN-1")
    }

    @Test("patchScaleMeta updates existing meta data fields")
    func patchScaleMetaUpdatesExisting() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        device.metaData = ScaleTestFixtures.makeMetaData(modelNumber: "R4", serialNumber: "SN-1")
        try await sut.createScale(device)

        let newMeta = ScaleTestFixtures.makeMetaData(modelNumber: "R4-Pro", serialNumber: "SN-2", latestVersion: "2.0.0")
        try await sut.patchScaleMeta("scale-1", metaData: newMeta)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData?.modelNumber == "R4-Pro")
        #expect(fetched?.metaData?.serialNumber == "SN-2")
        #expect(fetched?.metaData?.latestVersion == "2.0.0")
    }

    @Test("patchScaleMeta with nonexistent scale throws")
    func patchScaleMetaNotFoundThrows() async throws {
        let sut = makeSUT()
        let meta = ScaleTestFixtures.makeMetaData()

        await #expect(throws: (any Error).self) {
            try await sut.patchScaleMeta("nonexistent", metaData: meta)
        }
    }

    // MARK: - Sync State

    @Test("markDeviceAsDeleted sets isSoftDeleted true and isSynced false")
    func markDeviceAsDeleted() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice())

        try await sut.markDeviceAsDeleted("scale-1")
        let device = try await sut.getDevice("scale-1")

        #expect(device?.isSoftDeleted == true)
        #expect(device?.isSynced == false)
    }

    @Test("getDevicesMarkedForDeletion returns soft-deleted unsynced devices only")
    func getDevicesMarkedForDeletion() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s1"))
        try await sut.createScale(ScaleTestFixtures.makeDevice(id: "s2"))

        try await sut.markDeviceAsDeleted("s1")

        let forDeletion = try await sut.getDevicesMarkedForDeletion()
        #expect(forDeletion.count == 1)
        #expect(forDeletion.first?.id == "s1")
    }

    @Test("permanentlyRemoveDevice deletes device from storage")
    func permanentlyRemoveDevice() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice())

        try await sut.permanentlyRemoveDevice("scale-1")
        let device = try await sut.getDevice("scale-1")

        #expect(device == nil)
    }

    @Test("isDevicePurelyLocal returns true for unsynced device with no server ID")
    func isDevicePurelyLocalTrue() async throws {
        let sut = makeSUT()
        try await sut.createScale(ScaleTestFixtures.makeDevice(isSynced: false, hasServerID: false))

        let result = try await sut.isDevicePurelyLocal("scale-1")

        #expect(result == true)
    }

    @Test("isDevicePurelyLocal returns false for device with server ID")
    func isDevicePurelyLocalFalseWhenHasServerId() async throws {
        let sut = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "s1", isSynced: true, hasServerID: true)
        device.isSynced = true
        device.hasServerID = true
        sut.context.insert(device)
        if let pref = device.r4ScalePreference { sut.context.insert(pref) }
        if let bath = device.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let result = try await sut.isDevicePurelyLocal("s1")

        #expect(result == false)
    }

    @Test("isDevicePurelyLocal returns false for nonexistent device")
    func isDevicePurelyLocalFalseForNonexistent() async throws {
        let sut = makeSUT()

        let result = try await sut.isDevicePurelyLocal("nonexistent")

        #expect(result == false)
    }

    // MARK: - Replace-All Sync

    @Test("replaceAllDevicesForAccount replaces synced devices with server devices")
    func replaceAllDevicesForAccountReplacesSynced() async throws {
        let sut = makeSUT()

        // Insert a synced device directly (bypassing createScale to keep isSynced=true)
        let syncedDevice = ScaleTestFixtures.makeDevice(id: "old-synced", accountId: "acct-1", isSynced: true)
        syncedDevice.isSynced = true
        sut.context.insert(syncedDevice)
        if let pref = syncedDevice.r4ScalePreference { sut.context.insert(pref) }
        if let bath = syncedDevice.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        let serverDevices = [ScaleTestFixtures.makeScaleDTO(id: "server-1")]
        try await sut.replaceAllDevicesForAccount("acct-1", with: serverDevices, preserveUnsynced: [])

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.count == 1)
        #expect(scales.first?.id == "server-1")
    }

    @Test("replaceAllDevicesForAccount preserves unsynced devices")
    func replaceAllDevicesForAccountPreservesUnsynced() async throws {
        let sut = makeSUT()

        // Create unsynced device with unique identifiers to avoid matching the server device
        let unsyncedDevice = ScaleTestFixtures.makeDevice(
            id: "local-unsynced",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "local-bc",
            broadcastId: 111111
        )
        try await sut.createScale(unsyncedDevice)
        let unsyncedList = try await sut.getUnsyncedDevices()

        // Server device uses different identifiers — no match with local-unsynced
        let serverDevices = [ScaleTestFixtures.makeScaleDTO(
            id: "server-1",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "server-bc",
            broadcastId: 999999
        )]
        try await sut.replaceAllDevicesForAccount("acct-1", with: serverDevices, preserveUnsynced: unsyncedList)

        let scales = try await sut.listScales(forAccountId: "acct-1")
        // Both the preserved unsynced device and the new server device should exist
        #expect(scales.count == 2)
        let ids = scales.map { $0.id }
        #expect(ids.contains("local-unsynced"))
        #expect(ids.contains("server-1"))
    }

    @Test("replaceAllDevicesForAccount with empty server devices removes all synced")
    func replaceAllDevicesForAccountEmptyServerDevices() async throws {
        let sut = makeSUT()

        let syncedDevice = ScaleTestFixtures.makeDevice(id: "synced-1", accountId: "acct-1", isSynced: true)
        syncedDevice.isSynced = true
        sut.context.insert(syncedDevice)
        if let pref = syncedDevice.r4ScalePreference { sut.context.insert(pref) }
        if let bath = syncedDevice.bathScale { sut.context.insert(bath) }
        try sut.context.save()

        try await sut.replaceAllDevicesForAccount("acct-1", with: [], preserveUnsynced: [])

        let scales = try await sut.listScales(forAccountId: "acct-1")
        #expect(scales.isEmpty)
    }

    @Test("replaceAllDevicesForAccount does not affect devices of other accounts")
    func replaceAllDevicesForAccountIsolatesAccounts() async throws {
        let sut = makeSUT()

        // Synced device for acct-1
        let syncedA = ScaleTestFixtures.makeDevice(id: "acct-a-synced", accountId: "acct-1", isSynced: true)
        syncedA.isSynced = true
        sut.context.insert(syncedA)
        if let pref = syncedA.r4ScalePreference { sut.context.insert(pref) }
        if let bath = syncedA.bathScale { sut.context.insert(bath) }

        // Unsynced device for acct-2
        let deviceB = ScaleTestFixtures.makeDevice(id: "acct-b-device", accountId: "acct-2")
        try await sut.createScale(deviceB)

        try sut.context.save()

        try await sut.replaceAllDevicesForAccount("acct-1", with: [], preserveUnsynced: [])

        let scalesB = try await sut.listScales(forAccountId: "acct-2")
        #expect(scalesB.count == 1)
        #expect(scalesB.first?.id == "acct-b-device")
    }

}
