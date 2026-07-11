import Foundation
@testable import meApp
import SwiftData
import Testing

@Suite(.serialized)
@MainActor
struct DeviceRepositoryTests {

    // MARK: - Helpers

    private func makeContainer() throws -> ModelContainer {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(
            for: Device.self,
            DeviceMetaData.self,
            R4ScalePreference.self,
            BathScale.self,
            configurations: config
        )
    }

    private func makeSUT() throws -> DeviceRepository {
        let container = try makeContainer()
        return DeviceRepository(context: ModelContext(container))
    }

    private func makeDevice(
        id: String = "scale-1",
        accountId: String = "acct-1",
        isSynced: Bool = false,
        hasServerID: Bool = false,
        isSoftDeleted: Bool? = nil
    ) -> Device {
        ScaleTestFixtures.makeDevice(
            id: id,
            accountId: accountId,
            isSynced: isSynced,
            hasServerID: hasServerID,
            isSoftDeleted: isSoftDeleted
        )
    }

    // MARK: - createScale

    @Test("createScale persists a new device retrievable by ID")
    func createScale_success_persisted() async throws {
        let sut = try makeSUT()

        let created = try await sut.createScale(makeDevice(id: "scale-1"))

        #expect(created.id == "scale-1")
        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.id == "scale-1")
        #expect(fetched?.isSynced == false)
    }

    @Test("createScale duplicate ID throws")
    func createScale_duplicateId_throws() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        await #expect(throws: (any Error).self) {
            _ = try await sut.createScale(self.makeDevice(id: "scale-1"))
        }
    }

    // MARK: - listScales

    @Test("listScales returns all devices across accounts")
    func listScales_returnsAll() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1", accountId: "acct-1"))
        _ = try await sut.createScale(makeDevice(id: "scale-2", accountId: "acct-2"))

        let all = try await sut.listScales()

        #expect(all.count == 2)
    }

    @Test("listScales empty store returns empty array")
    func listScales_empty_returnsEmpty() async throws {
        let sut = try makeSUT()

        let all = try await sut.listScales()

        #expect(all.isEmpty)
    }

    @Test("listScales(forAccountId:) filters by account")
    func listScales_forAccountId_filters() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1", accountId: "acct-1"))
        _ = try await sut.createScale(makeDevice(id: "scale-2", accountId: "acct-1"))
        _ = try await sut.createScale(makeDevice(id: "scale-3", accountId: "acct-2"))

        let forAccount = try await sut.listScales(forAccountId: "acct-1")

        #expect(forAccount.count == 2)
        #expect(Set(forAccount.map(\.id)) == Set(["scale-1", "scale-2"]))
    }

    // MARK: - getDevice

    @Test("getDevice returns matching device")
    func getDevice_found_returnsDevice() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        let device = try await sut.getDevice("scale-1")

        #expect(device?.id == "scale-1")
    }

    @Test("getDevice unknown ID returns nil")
    func getDevice_notFound_returnsNil() async throws {
        let sut = try makeSUT()

        let device = try await sut.getDevice("missing")

        #expect(device == nil)
    }

    // MARK: - updateDevice

    @Test("updateDevice persists changed fields")
    func updateDevice_persistsChanges() async throws {
        let sut = try makeSUT()
        let created = try await sut.createScale(makeDevice(id: "scale-1"))

        created.nickname = "Updated Nickname"
        created.deviceName = "Renamed Device"
        try await sut.updateDevice(created)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.nickname == "Updated Nickname")
        #expect(fetched?.deviceName == "Renamed Device")
    }

    @Test("updateDevice missing device throws")
    func updateDevice_notFound_throws() async throws {
        let sut = try makeSUT()

        await #expect(throws: (any Error).self) {
            try await sut.updateDevice(self.makeDevice(id: "missing"))
        }
    }

    // MARK: - updateDeviceWithNewId

    @Test("updateDeviceWithNewId replaces old ID with new server ID")
    func updateDeviceWithNewId_replacesId() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "local-1", accountId: "acct-1"))

        let updated = makeDevice(id: "server-1", accountId: "acct-1")
        try await sut.updateDeviceWithNewId(oldId: "local-1", updatedDevice: updated)

        let oldDevice = try await sut.getDevice("local-1")
        let newDevice = try await sut.getDevice("server-1")
        #expect(oldDevice == nil)
        #expect(newDevice?.id == "server-1")
    }

    @Test("updateDeviceWithNewId missing old device throws")
    func updateDeviceWithNewId_missing_throws() async throws {
        let sut = try makeSUT()

        await #expect(throws: (any Error).self) {
            try await sut.updateDeviceWithNewId(oldId: "missing", updatedDevice: self.makeDevice(id: "server-1"))
        }
    }

    // MARK: - getUnsyncedDevices

    @Test("getUnsyncedDevices returns devices with isSynced false")
    func getUnsyncedDevices_returnsUnsynced() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        let unsynced = try await sut.getUnsyncedDevices()

        #expect(unsynced.count == 1)
        #expect(unsynced.first?.id == "scale-1")
    }

    // MARK: - editScale

    @Test("editScale updates nickname and marks unsynced")
    func editScale_updatesNickname() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        let updated = try await sut.editScale("scale-1", properties: ["nickname": "New Name"])

        #expect(updated.nickname == "New Name")
        #expect(updated.isSynced == false)
        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.nickname == "New Name")
    }

    @Test("editScale missing device throws")
    func editScale_notFound_throws() async throws {
        let sut = try makeSUT()

        await #expect(throws: (any Error).self) {
            _ = try await sut.editScale("missing", properties: ["nickname": "Nope"])
        }
    }

    // MARK: - deleteScale

    @Test("deleteScale removes the device")
    func deleteScale_removesDevice() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        try await sut.deleteScale("scale-1")

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched == nil)
    }

    @Test("deleteScale unknown ID is a no-op")
    func deleteScale_notFound_noThrow() async throws {
        let sut = try makeSUT()

        try await sut.deleteScale("missing")

        let all = try await sut.listScales()
        #expect(all.isEmpty)
    }

    // MARK: - patchScaleMeta

    @Test("patchScaleMeta creates metadata when none exists")
    func patchScaleMeta_createsMetaData() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        try await sut.patchScaleMeta("scale-1", metaData: ScaleTestFixtures.makeMetaData(serialNumber: "serial-A"))

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData?.serialNumber == "serial-A")
        #expect(fetched?.isSynced == false)
    }

    @Test("patchScaleMeta updates existing metadata fields")
    func patchScaleMeta_updatesExisting() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))
        try await sut.patchScaleMeta("scale-1", metaData: ScaleTestFixtures.makeMetaData(serialNumber: "serial-A"))

        try await sut.patchScaleMeta("scale-1", metaData: ScaleTestFixtures.makeMetaData(serialNumber: "serial-B", latestVersion: "2.0.0"))

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.metaData?.serialNumber == "serial-B")
        #expect(fetched?.metaData?.latestVersion == "2.0.0")
    }

    @Test("patchScaleMeta missing device throws")
    func patchScaleMeta_notFound_throws() async throws {
        let sut = try makeSUT()

        await #expect(throws: (any Error).self) {
            try await sut.patchScaleMeta("missing", metaData: ScaleTestFixtures.makeMetaData())
        }
    }

    // MARK: - patchScalePreference (model)

    @Test("patchScalePreference updates existing preference")
    func patchScalePreference_model_updatesExisting() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        let preference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "Renamed Pref"),
            scaleId: "scale-1"
        )
        try await sut.patchScalePreference("scale-1", preference)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.r4ScalePreference?.displayName == "Renamed Pref")
    }

    @Test("patchScalePreference creates preference when none exists")
    func patchScalePreference_model_createsNew() async throws {
        let sut = try makeSUT()
        let bare = Device(id: "scale-nopref", accountId: "acct-1")
        _ = try await sut.createScale(bare)

        let preference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-nopref", displayName: "Fresh Pref"),
            scaleId: "scale-nopref"
        )
        try await sut.patchScalePreference("scale-nopref", preference)

        let fetched = try await sut.getDevice("scale-nopref")
        #expect(fetched?.r4ScalePreference?.displayName == "Fresh Pref")
        #expect(fetched?.r4ScalePreference?.id == "scale-nopref")
    }

    // MARK: - patchScalePreference (DTO)

    @Test("patchScalePreference(fromDTO:) updates existing preference")
    func patchScalePreference_dto_updatesExisting() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "DTO Updated")
        try await sut.patchScalePreference("scale-1", fromDTO: dto)

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.r4ScalePreference?.displayName == "DTO Updated")
    }

    @Test("patchScalePreference(fromDTO:) creates preference when none exists")
    func patchScalePreference_dto_createsNew() async throws {
        let sut = try makeSUT()
        let bare = Device(id: "scale-nopref", accountId: "acct-1")
        _ = try await sut.createScale(bare)

        let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-nopref", displayName: "DTO Fresh")
        try await sut.patchScalePreference("scale-nopref", fromDTO: dto)

        let fetched = try await sut.getDevice("scale-nopref")
        #expect(fetched?.r4ScalePreference?.displayName == "DTO Fresh")
    }

    // MARK: - markDeviceAsDeleted / getDevicesMarkedForDeletion

    @Test("markDeviceAsDeleted sets soft-delete and unsynced flags")
    func markDeviceAsDeleted_setsFlags() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        try await sut.markDeviceAsDeleted("scale-1")

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched?.isSoftDeleted == true)
        #expect(fetched?.isSynced == false)
    }

    @Test("markDeviceAsDeleted missing device throws")
    func markDeviceAsDeleted_notFound_throws() async throws {
        let sut = try makeSUT()

        await #expect(throws: (any Error).self) {
            try await sut.markDeviceAsDeleted("missing")
        }
    }

    @Test("getDevicesMarkedForDeletion returns soft-deleted unsynced devices")
    func getDevicesMarkedForDeletion_returnsMatches() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))
        _ = try await sut.createScale(makeDevice(id: "scale-2"))
        try await sut.markDeviceAsDeleted("scale-1")

        let marked = try await sut.getDevicesMarkedForDeletion()

        #expect(marked.count == 1)
        #expect(marked.first?.id == "scale-1")
    }

    // MARK: - permanentlyRemoveDevice

    @Test("permanentlyRemoveDevice removes the device")
    func permanentlyRemoveDevice_removes() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        try await sut.permanentlyRemoveDevice("scale-1")

        let fetched = try await sut.getDevice("scale-1")
        #expect(fetched == nil)
    }

    @Test("permanentlyRemoveDevice unknown ID is a no-op")
    func permanentlyRemoveDevice_notFound_noThrow() async throws {
        let sut = try makeSUT()

        try await sut.permanentlyRemoveDevice("missing")

        let all = try await sut.listScales()
        #expect(all.isEmpty)
    }

    // MARK: - isDevicePurelyLocal

    @Test("isDevicePurelyLocal true for unsynced device without server ID")
    func isDevicePurelyLocal_localDevice_true() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1", hasServerID: false))

        let result = try await sut.isDevicePurelyLocal("scale-1")

        #expect(result == true)
    }

    @Test("isDevicePurelyLocal false when device has server ID")
    func isDevicePurelyLocal_hasServerId_false() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1", hasServerID: true))

        let result = try await sut.isDevicePurelyLocal("scale-1")

        #expect(result == false)
    }

    @Test("isDevicePurelyLocal false for unknown device")
    func isDevicePurelyLocal_notFound_false() async throws {
        let sut = try makeSUT()

        let result = try await sut.isDevicePurelyLocal("missing")

        #expect(result == false)
    }

    // MARK: - fetchAttachedPreference

    @Test("fetchAttachedPreference returns attached preference by ID")
    func fetchAttachedPreference_found() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1", accountId: "acct-1"))

        let preference = sut.fetchAttachedPreference(by: "scale-1")

        #expect(preference?.id == "scale-1")
    }

    @Test("fetchAttachedPreference returns nil for unknown ID")
    func fetchAttachedPreference_notFound_nil() async throws {
        let sut = try makeSUT()

        let preference = sut.fetchAttachedPreference(by: "missing")

        #expect(preference == nil)
    }

    @Test("fetchAttachedPreferenceSync returns attached preference by ID")
    func fetchAttachedPreferenceSync_found() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1"))

        let preference = sut.fetchAttachedPreferenceSync(by: "scale-1")

        #expect(preference?.id == "scale-1")
    }

    // MARK: - clearAllData

    @Test("clearAllData removes every device")
    func clearAllData_removesAll() async throws {
        let sut = try makeSUT()
        _ = try await sut.createScale(makeDevice(id: "scale-1", accountId: "acct-1"))
        _ = try await sut.createScale(makeDevice(id: "scale-2", accountId: "acct-2"))

        try await sut.clearAllData()

        let all = try await sut.listScales()
        #expect(all.isEmpty)
    }

    @Test("clearAllData empty store does not throw")
    func clearAllData_empty_noThrow() async throws {
        let sut = try makeSUT()

        try await sut.clearAllData()

        let all = try await sut.listScales()
        #expect(all.isEmpty)
    }

    // MARK: - replaceAllDevicesForAccount

    @Test("replaceAllDevicesForAccount inserts server devices into empty store")
    func replaceAllDevicesForAccount_insertsServerDevices() async throws {
        let sut = try makeSUT()

        try await sut.replaceAllDevicesForAccount(
            "acct-1",
            with: [ScaleTestFixtures.makeScaleDTO(id: "server-1", accountId: "acct-1")],
            preserveUnsynced: []
        )

        let forAccount = try await sut.listScales(forAccountId: "acct-1")
        #expect(forAccount.count == 1)
        #expect(forAccount.first?.id == "server-1")
        #expect(forAccount.first?.hasServerID == true)
        #expect(forAccount.first?.isSynced == true)
    }

    @Test("replaceAllDevicesForAccount deletes stale synced devices for the account")
    func replaceAllDevicesForAccount_deletesStaleSynced() async throws {
        let sut = try makeSUT()
        try await sut.replaceAllDevicesForAccount(
            "acct-1",
            with: [ScaleTestFixtures.makeScaleDTO(id: "server-old", accountId: "acct-1")],
            preserveUnsynced: []
        )

        try await sut.replaceAllDevicesForAccount(
            "acct-1",
            with: [ScaleTestFixtures.makeScaleDTO(id: "server-new", accountId: "acct-1")],
            preserveUnsynced: []
        )

        let forAccount = try await sut.listScales(forAccountId: "acct-1")
        #expect(forAccount.count == 1)
        #expect(forAccount.first?.id == "server-new")
    }
}
