import Foundation
@testable import meApp
import Testing

/// Unit tests for `DeviceService` (Data/Services) — the paired-scale/device management service
/// with the offline-first "replace-all" sync policy and the unified `/v3/paired-device/` wiring.
///
/// Uses the in-memory `MockScaleRepository` (real `ModelContext`) + `MockScaleRepositoryAPI`
/// via the constructor-injection initializer, so no production SwiftData or network is touched.
@MainActor
@Suite(.serialized)
struct DeviceServiceTests {

    // MARK: - SUT

    // swiftlint:disable:next large_tuple
    typealias SUTBundle = (DeviceService, MockScaleRepository, MockScaleRepositoryAPI, MockAccountService)

    func makeSUT(
        repo: MockScaleRepository? = nil,
        remote: MockScaleRepositoryAPI? = nil,
        account: MockAccountService? = nil,
        activeAccountId: String? = "acct-1"
    ) -> SUTBundle {
        let repo = repo ?? MockScaleRepository()
        let remote = remote ?? MockScaleRepositoryAPI()
        let account = account ?? MockAccountService()
        TestDependencyContainer.reset()
        DependencyContainer.shared.register(MockLoggerService() as LoggerServiceProtocol)
        if let activeAccountId {
            let snapshot = AccountTestFixtures.makeAccountSnapshot(id: activeAccountId, isActiveAccount: true)
            account.activeAccount = snapshot
            account.seedAccounts([snapshot], active: snapshot)
        }
        let sut = DeviceService(accountService: account, apiRepository: remote, localRepository: repo)
        return (sut, repo, remote, account)
    }

    // MARK: - clearAllData

    @Test("clearAllData delegates to the repository")
    func clearAllDataSuccess() async {
        let (sut, repo, _, _) = makeSUT()

        await sut.clearAllData()

        #expect(repo.clearAllDataCalls == 1)
    }

    @Test("clearAllData swallows repository errors")
    func clearAllDataFailureDoesNotThrow() async {
        let repo = MockScaleRepository()
        repo.clearAllDataError = ScaleTestError.localFailure
        let (sut, _, _, _) = makeSUT(repo: repo)

        await sut.clearAllData()

        #expect(repo.clearAllDataCalls == 1)
    }

    // MARK: - getDevices / getDevice

    @Test("getDevices excludes soft-deleted devices")
    func getDevicesExcludesSoftDeleted() async throws {
        let repo = MockScaleRepository()
        repo.devices = [
            ScaleTestFixtures.makeDevice(id: "active"),
            ScaleTestFixtures.makeDevice(id: "deleted", isSoftDeleted: true)
        ]
        let (sut, _, _, _) = makeSUT(repo: repo)
        try await sut.updateAllScalesStatus()

        let devices = try await sut.getDevices()

        #expect(devices.map(\.id) == ["active"])
    }

    @Test("getDevice returns the matching published snapshot or nil")
    func getDeviceByIdReturnsSnapshot() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let (sut, _, _, _) = makeSUT(repo: repo)
        try await sut.updateAllScalesStatus()

        #expect(try await sut.getDevice(by: "scale-1")?.id == "scale-1")
        #expect(try await sut.getDevice(by: "missing") == nil)
    }

    // MARK: - createDevice

    @Test("createDevice creates a new device locally")
    func createDeviceCreatesNew() async throws {
        let repo = MockScaleRepository()
        let (sut, _, _, _) = makeSUT(repo: repo)
        let device = ScaleTestFixtures.makeDevice(id: "new-scale")

        let created = try await sut.createDevice(device)

        #expect(created.id == "new-scale")
        #expect(repo.createScaleCalls == 1)
    }

    @Test("createDevice returns the existing device when one matches by id")
    func createDeviceReturnsExisting() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "dup")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        let created = try await sut.createDevice(ScaleTestFixtures.makeDevice(id: "dup"))

        #expect(created.id == "dup")
        #expect(repo.createScaleCalls == 0)
    }

    @Test("createDevice with skipDuplicateCheck always creates")
    func createDeviceSkipDuplicateCheck() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "dup")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        _ = try await sut.createDevice(ScaleTestFixtures.makeDevice(id: "dup"), true)

        #expect(repo.createScaleCalls == 1)
    }

    @Test("createScaleInLocal delegates to the repository")
    func createScaleInLocalDelegates() async throws {
        let repo = MockScaleRepository()
        let (sut, _, _, _) = makeSUT(repo: repo)

        _ = try await sut.createScaleInLocal(ScaleTestFixtures.makeDevice(id: "x"))

        #expect(repo.createScaleCalls == 1)
    }

    // MARK: - editDevice

    @Test("editDevice throws when the device does not exist")
    func editDeviceNotFoundThrows() async {
        let (sut, _, _, _) = makeSUT()

        await #expect(throws: DeviceError.self) {
            _ = try await sut.editDevice("missing", properties: ["nickname": "New"])
        }
    }

    @Test("editDevice updates an existing device")
    func editDeviceSuccess() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        let updated = try await sut.editDevice("scale-1", properties: ["nickname": "Renamed"])

        #expect(updated.nickname == "Renamed")
        #expect(repo.editScaleCalls == 1)
    }

    // MARK: - deleteDevice

    @Test("deleteDevice throws when device not found")
    func deleteDeviceNotFoundThrows() async {
        let (sut, _, _, _) = makeSUT()

        await #expect(throws: DeviceError.self) {
            try await sut.deleteDevice("missing", showToast: false)
        }
    }

    @Test("deleteDevice removes a purely-local device immediately")
    func deleteDevicePurelyLocal() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "local", hasServerID: false)]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteDevice("local", showToast: false)

        #expect(repo.deleteScaleCalls >= 1)
        #expect(repo.markDeviceAsDeletedCalls == 0)
    }

    @Test("deleteDevice marks a server device for deletion")
    func deleteDeviceServerDeviceMarksDeleted() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "server", isSynced: true, hasServerID: true)]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteDevice("server", showToast: false)

        #expect(repo.markDeviceAsDeletedCalls >= 1)
    }

    // MARK: - deleteSingleDeviceEntry

    @Test("deleteSingleDeviceEntry deletes purely-local devices")
    func deleteSingleDeviceEntryPurelyLocal() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "local", hasServerID: false)]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteSingleDeviceEntry("local")

        #expect(repo.deleteScaleCalls >= 1)
        #expect(repo.markDeviceAsDeletedCalls == 0)
    }

    @Test("deleteSingleDeviceEntry marks server devices as deleted")
    func deleteSingleDeviceEntryServerDevice() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "server", isSynced: true, hasServerID: true)]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteSingleDeviceEntry("server")

        #expect(repo.markDeviceAsDeletedCalls >= 1)
    }

    // MARK: - updateScaleMeta

    @Test("updateScaleMeta throws when device not found")
    func updateScaleMetaNotFoundThrows() async {
        let (sut, _, _, _) = makeSUT()

        await #expect(throws: DeviceError.self) {
            try await sut.updateScaleMeta("missing", metaData: DeviceMetaData(latestVersion: "1.0"))
        }
    }

    @Test("updateScaleMeta marks meta synced when the server accepts it")
    func updateScaleMetaServerSuccess() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let (sut, _, remote, _) = makeSUT(repo: repo)
        let meta = DeviceMetaData(latestVersion: "2.0")

        try await sut.updateScaleMeta("scale-1", metaData: meta)

        #expect(remote.patchScaleMetaCalls == 1)
        #expect(meta.isSynced == true)
        #expect(repo.patchScaleMetaCalls >= 1)
    }

    @Test("updateScaleMeta marks meta unsynced when the server rejects it")
    func updateScaleMetaServerFailure() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let remote = MockScaleRepositoryAPI()
        remote.patchScaleMetaError = ScaleTestError.remoteFailure
        let (sut, _, _, _) = makeSUT(repo: repo, remote: remote)
        let meta = DeviceMetaData(latestVersion: "2.0")

        try await sut.updateScaleMeta("scale-1", metaData: meta)

        #expect(meta.isSynced == false)
        #expect(repo.patchScaleMetaCalls >= 1)
    }

    // MARK: - updateScalePreference

    @Test("updateScalePreference throws when device not found")
    func updateScalePreferenceNotFoundThrows() async {
        let (sut, _, _, _) = makeSUT()
        let pref = R4ScalePreference(from: ScaleTestFixtures.makePreferenceDTO(), scaleId: "missing")

        await #expect(throws: DeviceError.self) {
            try await sut.updateScalePreference("missing", pref)
        }
    }

    @Test("updateScalePreference patches server and local on success")
    func updateScalePreferenceSuccess() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let (sut, _, remote, _) = makeSUT(repo: repo)
        let pref = R4ScalePreference(from: ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1"), scaleId: "scale-1")

        try await sut.updateScalePreference("scale-1", pref)

        #expect(remote.patchScalePreferenceCalls == 1)
        #expect(repo.patchScalePreferenceCalls >= 1)
    }

    // MARK: - fetchAttachedPreference

    @Test("fetchAttachedPreference returns the device's stored preference")
    func fetchAttachedPreferenceReturnsStored() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        let pref = await sut.fetchAttachedPreference(by: "scale-1")
        let prefSync = sut.fetchAttachedPreferenceSync(by: "scale-1")

        #expect(pref != nil)
        #expect(prefSync != nil)
    }

    // MARK: - Ephemeral connection state

    @Test("updateConnectedDevices reflects connection state in getConnectedDevices")
    func updateConnectedDevicesMarksConnected() async {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        let bid = device.broadcastIdString ?? ""
        repo.devices = [device]
        let (sut, _, _, _) = makeSUT(repo: repo)

        await sut.updateConnectedDevices(
            device: ["broadcastId": bid, "isWifiConfigured": true],
            isConnected: true
        )

        let connected = await sut.getConnectedDevices()
        #expect(connected[bid] != nil)
    }

    @Test("updateConnectedDevices without an active account is a no-op")
    func updateConnectedDevicesNoAccount() async {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1", broadcastIdString: "BID1")]
        let (sut, _, _, _) = makeSUT(repo: repo, activeAccountId: nil)

        await sut.updateConnectedDevices(
            device: ["broadcastId": "BID1", "isWifiConfigured": true],
            isConnected: true
        )

        let connected = await sut.getConnectedDevices()
        #expect(connected.isEmpty)
    }

    @Test("updateConnectedDevices ignores payloads without a broadcast id")
    func updateConnectedDevicesNoBroadcastId() async {
        let (sut, _, _, _) = makeSUT()

        await sut.updateConnectedDevices(device: ["isWifiConfigured": true], isConnected: true)

        let connected = await sut.getConnectedDevices()
        #expect(connected.isEmpty)
    }

    @Test("updateConnectedDeviceWeightOnlyMode updates the stored device")
    func updateConnectedDeviceWeightOnlyMode() async throws {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        let bid = device.broadcastIdString ?? ""
        repo.devices = [device]
        let (sut, _, _, _) = makeSUT(repo: repo)

        await sut.updateConnectedDeviceWeightOnlyMode(broadcastId: bid, isWeightOnlyModeEnabledByOthers: true)

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isWeighOnlyModeEnabledByOthers == true)
    }

    // MARK: - Sync

    @Test("syncAllScalesWithRemote pulls server state for the active account")
    func syncAllScalesPullsServerState() async {
        let (sut, repo, remote, _) = makeSUT()

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls >= 1)
        #expect(repo.replaceAllDevicesForAccountCalls >= 1)
    }

    @Test("syncAllScalesWithRemote skips when there is no active account")
    func syncAllScalesNoAccountSkips() async {
        let (sut, _, remote, _) = makeSUT(activeAccountId: nil)

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls == 0)
    }

    @Test("syncDevices with a new temp device creates it before syncing")
    func syncDevicesCreatesTempDevice() async throws {
        let repo = MockScaleRepository()
        let (sut, _, remote, _) = makeSUT(repo: repo)

        try await sut.syncDevices(tempDevice: ScaleTestFixtures.makeDevice(id: "temp"))

        #expect(repo.createScaleCalls == 1)
        #expect(remote.listScalesCalls >= 1)
    }

    @Test("syncDevices with nil temp device just syncs")
    func syncDevicesNilTempDevice() async throws {
        let (sut, _, remote, _) = makeSUT()

        try await sut.syncDevices(tempDevice: nil)

        #expect(remote.listScalesCalls >= 1)
    }

    @Test("updateAllScalesStatus refreshes without error")
    func updateAllScalesStatusRefreshes() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.updateAllScalesStatus()

        #expect(sut.scales.map(\.id) == ["scale-1"])
    }

    // MARK: - pushLocalChangesToServer

    @Test("pushLocalChangesToServer creates unsynced purely-local devices on the server")
    func pushLocalChangesCreatesNewDevice() async {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "local", isSynced: false, hasServerID: false)]
        let (sut, _, remote, _) = makeSUT(repo: repo)

        await sut.pushLocalChangesToServer()

        #expect(remote.createScaleCalls >= 1)
    }

    @Test("pushLocalChangesToServer deletes devices marked for deletion on the server")
    func pushLocalChangesDeletesMarkedDevices() async throws {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "server", isSynced: true, hasServerID: true)
        repo.devices = [device]
        let (sut, _, remote, _) = makeSUT(repo: repo)
        try await repo.markDeviceAsDeleted("server")

        await sut.pushLocalChangesToServer()

        #expect(remote.deleteScaleCalls >= 1)
    }

    // MARK: - Unified /v3/paired-device/ API

    @Test("listPairedDevices forwards the deviceType filter to the remote repository")
    func listPairedDevicesForwardsFilter() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.listPairedDevicesResult = [ScaleTestFixtures.makePairedDeviceResponse(id: "d1")]
        let (sut, _, _, _) = makeSUT(remote: remote)

        let result = try await sut.listPairedDevices(deviceType: .bpm)

        #expect(result.count == 1)
        #expect(remote.listPairedDevicesCalls == 1)
        #expect(remote.lastListedDeviceTypeFilter == DeviceType.bpm.serverValue)
    }

    @Test("createPairedDevice forwards to the remote repository")
    func createPairedDeviceForwards() async throws {
        let remote = MockScaleRepositoryAPI()
        let (sut, _, _, _) = makeSUT(remote: remote)
        let request = PairedDeviceRequest(
            deviceType: DeviceType.bpm.serverValue,
            type: DeviceType.bpm.serverValue,
            nickname: "Monitor",
            sku: "BP-1",
            mac: "AA:BB"
        )

        _ = try await sut.createPairedDevice(request)

        #expect(remote.createPairedDeviceCalls == 1)
    }

    @Test("updatePairedDevice forwards the new nickname")
    func updatePairedDeviceForwards() async throws {
        let remote = MockScaleRepositoryAPI()
        let (sut, _, _, _) = makeSUT(remote: remote)

        _ = try await sut.updatePairedDevice("d1", nickname: "Renamed")

        #expect(remote.updatePairedDeviceCalls == 1)
        #expect(remote.lastUpdatedPairedDeviceId == "d1")
    }

    @Test("deletePairedDevice forwards to the remote repository")
    func deletePairedDeviceForwards() async throws {
        let remote = MockScaleRepositoryAPI()
        let (sut, _, _, _) = makeSUT(remote: remote)

        try await sut.deletePairedDevice("d1")

        #expect(remote.deletePairedDeviceCalls == 1)
        #expect(remote.lastDeletedPairedDeviceId == "d1")
    }

    // MARK: - Construction helpers (R4 / A6 / Bluetooth scales)

    @Test("createR4Scale builds a device with bath scale and R4 preference")
    func createR4ScaleBuildsRelationships() async throws {
        let repo = MockScaleRepository()
        let (sut, _, _, _) = makeSUT(repo: repo)

        let device = try await sut.createR4Scale(
            scaleId: "r4-1",
            accountId: "acct-1",
            displayName: "R4 Scale",
            token: "tok",
            mac: "AA:BB:CC",
            broadcastIdString: "BID",
            broadcastId: 42,
            sku: "0412",
            deviceName: "AccuCheck"
        )

        #expect(device.id == "r4-1")
        #expect(device.bathScale != nil)
        #expect(device.r4ScalePreference != nil)
        #expect(repo.createScaleCalls == 1)
    }

    @Test("createA6Scale sets an LCBT bath scale and syncs")
    func createA6ScaleSetsLcbt() async throws {
        let repo = MockScaleRepository()
        let (sut, _, remote, _) = makeSUT(repo: repo)
        let device = Device(id: "a6-1", accountId: "acct-1", userNumber: "0")

        let created = try await sut.createA6Scale(device: device, sku: "A6-1", accountId: "acct-1")

        #expect(created.bathScale?.scaleType == DeviceSourceType.lcbt.rawValue)
        #expect(repo.createScaleCalls == 1)
        #expect(remote.listScalesCalls >= 1)
    }

    @Test("createBluetoothScale sets a bath scale by protocol and syncs")
    func createBluetoothScaleSetsScaleType() async throws {
        let repo = MockScaleRepository()
        let (sut, _, remote, _) = makeSUT(repo: repo)
        let device = Device(id: "bt-1", accountId: "acct-1", userNumber: "0", protocolType: "A3")

        let created = try await sut.createBluetoothScale(
            device: device,
            sku: "BT-1",
            userNumber: "0",
            accountId: "acct-1"
        )

        #expect(created.bathScale != nil)
        #expect(repo.createScaleCalls == 1)
        #expect(remote.listScalesCalls >= 1)
    }

    @Test("createBluetoothScale for a BPM device names it as a blood pressure monitor")
    func createBluetoothScaleBpmNickname() async throws {
        let repo = MockScaleRepository()
        let (sut, _, _, _) = makeSUT(repo: repo)
        let device = Device(id: "bpm-1", accountId: "acct-1", userNumber: "0", protocolType: "A6")

        let created = try await sut.createBluetoothScale(
            device: device,
            sku: "BPM-1",
            userNumber: "0",
            accountId: "acct-1",
            deviceType: .bpm
        )

        #expect(created.nickname == "Blood Pressure Monitor")
        #expect(created.deviceType == DeviceType.bpm.rawValue)
    }

    @Test("createA6Scale reuses an existing bath scale relationship")
    func createA6ScaleReusesExistingBathScale() async throws {
        let repo = MockScaleRepository()
        let (sut, _, _, _) = makeSUT(repo: repo)
        let device = Device(id: "a6-2", accountId: "acct-1", userNumber: "0")
        device.bathScale = BathScale(scaleType: DeviceSourceType.bluetooth.rawValue, bodyComp: true)

        let created = try await sut.createA6Scale(device: device, sku: "A6-2", accountId: "acct-1")

        // Existing bath scale is kept but its scale type is corrected to LCBT.
        #expect(created.bathScale?.scaleType == DeviceSourceType.lcbt.rawValue)
        #expect(created.bathScale?.bodyComp == true)
    }

    // MARK: - deleteDevice de-duplication by MAC / userNumber

    @Test("deleteDevice removes sibling devices that share a MAC and user number")
    func deleteDeviceRemovesMacSiblings() async throws {
        let repo = MockScaleRepository()
        let deviceA = ScaleTestFixtures.makeDevice(id: "dev-a", hasServerID: false)
        let deviceB = ScaleTestFixtures.makeDevice(id: "dev-b", hasServerID: false)
        // deviceA and deviceB share MAC + userNumber (both "0") → both are deletion candidates.
        repo.devices = [deviceA, deviceB]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteDevice("dev-a", showToast: false)

        // Both purely-local siblings are removed.
        #expect(repo.deleteScaleCalls >= 2)
    }

    @Test("deleteDevice keeps sibling devices with a different user number")
    func deleteDeviceKeepsDifferentUserNumber() async throws {
        let repo = MockScaleRepository()
        let deviceA = ScaleTestFixtures.makeDevice(id: "dev-a", hasServerID: false)
        let deviceB = ScaleTestFixtures.makeDevice(id: "dev-b", hasServerID: false)
        deviceB.userNumber = "1" // Different user slot on the same physical device.
        repo.devices = [deviceA, deviceB]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteDevice("dev-a", showToast: false)

        // Only the target (user 0) is deleted; the user-1 sibling survives.
        #expect(try await repo.getDevice("dev-b") != nil)
    }

    // MARK: - updateConnectedDeviceWifiStatus

    @Test("updateConnectedDeviceWifiStatus records the wifi flag in ephemeral state")
    func updateConnectedDeviceWifiStatusUpdatesEphemeral() async {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        let bid = device.broadcastIdString ?? ""
        repo.devices = [device]
        let (sut, _, _, _) = makeSUT(repo: repo)
        // Establish a connected snapshot first so the published scale is present.
        await sut.updateConnectedDevices(device: ["broadcastId": bid], isConnected: true)

        await sut.updateConnectedDeviceWifiStatus(broadcastId: bid, isConfigured: true)

        let connected = await sut.getConnectedDevices()
        let entry = connected[bid] as? [String: Any]
        #expect(entry?["isWifiConfigured"] as? Bool == true)
    }

    // MARK: - pushLocalChangesToServer edit path

    @Test("pushLocalChangesToServer edits an unsynced device that already has a server id")
    func pushLocalChangesEditsServerDevice() async {
        let repo = MockScaleRepository()
        // Server-backed device with local edits pending (isSynced = false, hasServerID = true).
        repo.devices = [ScaleTestFixtures.makeDevice(id: "server-1", isSynced: false, hasServerID: true)]
        let (sut, _, remote, _) = makeSUT(repo: repo)

        await sut.pushLocalChangesToServer()

        #expect(remote.editScaleCalls >= 1)
        #expect(remote.createScaleCalls == 0)
    }

    @Test("pushLocalChangesToServer removes a device the server reports as not found during edit")
    func pushLocalChangesRemovesNotFoundOnEdit() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "ghost", isSynced: false, hasServerID: true)]
        let remote = MockScaleRepositoryAPI()
        remote.editScaleError = HTTPError.notFound
        let (sut, _, _, _) = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        // A 404 on edit means the device was deleted server-side → pruned locally.
        #expect(try await repo.getDevice("ghost") == nil)
    }
}
