// swiftlint:disable file_length
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
// swiftlint:disable:next type_body_length
struct ScaleServiceTests {
    @Test("clearAllData clears local scale storage")
    func clearAllDataSuccess() async {
        let repo = MockScaleRepository()
        repo.devices = [
            ScaleTestFixtures.makeDevice(id: "scale-1"),
            ScaleTestFixtures.makeDevice(id: "scale-2")
        ]
        let sut = makeSUT(repo: repo)

        await sut.clearAllData()

        #expect(repo.clearAllDataCalls == 1)
        #expect(repo.devices.isEmpty)
    }

    @Test("clearAllData failure does not mutate local scales")
    func clearAllDataFailureLeavesDataUntouched() async {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        repo.clearAllDataError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo)

        await sut.clearAllData()

        #expect(repo.devices.count == 1)
        #expect(repo.clearAllDataCalls == 1)
        _ = sut
    }

    @Test("createR4Scale success: stores paired scale with default R4 preference")
    func createR4ScaleSuccess() async throws {
        let repo = MockScaleRepository()
        let sut = makeSUT(repo: repo)

        let device = try await sut.createR4Scale(
            scaleId: "scale-1",
            accountId: "acct-1",
            displayName: "Primary Scale",
            token: "token-1",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "A1B2C3",
            broadcastId: 123456,
            sku: "R4-001",
            deviceName: "Verve"
        )

        #expect(repo.createScaleCalls == 1)
        #expect(device.id == "scale-1")
        #expect(device.accountId == "acct-1")
        #expect(device.token == "token-1")
        #expect(device.bathScale?.scaleType == ScaleSourceType.btWifiR4.rawValue)
        #expect(device.r4ScalePreference?.displayName == "Primary Scale")
        #expect(device.r4ScalePreference?.shouldMeasureImpedance == true)
        #expect(device.r4ScalePreference?.isSynced == false)
        #expect(sut.scales.count == 1)
    }

    @Test("createR4Scale failure: propagates pairing persistence error")
    func createR4ScaleFailure() async {
        let repo = MockScaleRepository()
        repo.createScaleError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo)

        do {
            _ = try await sut.createR4Scale(
                scaleId: "scale-1",
                accountId: "acct-1",
                displayName: "Primary Scale",
                token: "token-1",
                mac: "AA:BB:CC:DD:EE:FF",
                broadcastIdString: "A1B2C3",
                broadcastId: 123456,
                sku: "R4-001",
                deviceName: "Verve"
            )
            Issue.record("Expected createR4Scale to throw")
        } catch {
            #expect(error as? ScaleTestError == .localFailure)
        }
    }

    @Test("updateScalePreference success: patches remote and stores synced preference locally")
    func updateScalePreferenceSuccess() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)
        let dto = ScaleTestFixtures.makePreferenceDTO(
            scaleId: "scale-1",
            displayName: "Hallway Scale",
            displayMetrics: ["weight", "bmi", "muscleMass"],
            shouldMeasurePulse: true
        )

        try await sut.updateScalePreference("scale-1", fromDTO: dto)

        let preference = await sut.fetchAttachedPreference(by: "scale-1")
        #expect(remote.patchScalePreferenceCalls == 1)
        #expect(remote.lastPatchedPreference?.displayName == "Hallway Scale")
        #expect(repo.patchScalePreferenceCalls == 1)
        #expect(repo.lastPatchedPreferenceDTO?.isSynced == true)
        #expect(preference?.displayName == "Hallway Scale")
        #expect(preference?.shouldMeasurePulse == true)
        #expect(preference?.isSynced == true)
    }

    @Test("updateScalePreference model overload converts and stores synced preference")
    func updateScalePreferenceModelOverloadSuccess() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)
        let preference = R4ScalePreference(
            scaleId: "scale-1",
            displayName: "Primary Scale",
            displayMetrics: ["weight", "bmi"],
            shouldFactoryReset: false,
            shouldMeasureImpedance: true,
            shouldMeasurePulse: true,
            timeFormat: "12",
            tzOffset: 330,
            wifiFotaScheduleTime: 0,
            updatedAt: "2026-03-03T00:00:00Z"
        )

        try await sut.updateScalePreference("scale-1", preference)

        #expect(remote.patchScalePreferenceCalls == 1)
        #expect(remote.lastPatchedPreference?.displayName == "Primary Scale")
        #expect(try await repo.getDevice("scale-1")?.r4ScalePreference?.shouldMeasurePulse == true)
        #expect(try await repo.getDevice("scale-1")?.r4ScalePreference?.isSynced == true)
    }

    @Test("updateScalePreference remote failure: keeps local preference unsynced and retries during sync")
    func updateScalePreferenceRemoteFailure() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.patchScalePreferenceError = ScaleTestError.remoteFailure
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)
        let dto = ScaleTestFixtures.makePreferenceDTO(scaleId: "scale-1", displayName: "Offline Scale")

        try await sut.updateScalePreference("scale-1", fromDTO: dto)

        let preference = await sut.fetchAttachedPreference(by: "scale-1")
        #expect(remote.patchScalePreferenceCalls >= 1)
        #expect(repo.lastPatchedPreferenceDTO?.isSynced == false)
        #expect(preference?.displayName == "Offline Scale")
        #expect(preference?.isSynced == false)
    }

    @Test("updateScalePreference missing scale: throws deviceNotFound")
    func updateScalePreferenceMissingScale() async {
        let sut = makeSUT()

        do {
            try await sut.updateScalePreference("missing-scale", fromDTO: ScaleTestFixtures.makePreferenceDTO(scaleId: "missing-scale"))
            Issue.record("Expected updateScalePreference to throw")
        } catch {
            guard case let ScaleError.deviceNotFound(id) = error else {
                Issue.record("Expected deviceNotFound, got \(error)")
                return
            }
            #expect(id == "missing-scale")
        }
    }

    @Test("updateScaleMeta missing scale throws deviceNotFound")
    func updateScaleMetaMissingScaleThrows() async {
        let sut = makeSUT()

        do {
            try await sut.updateScaleMeta("missing-scale", metaData: ScaleTestFixtures.makeMetaData())
            Issue.record("Expected updateScaleMeta to throw")
        } catch {
            guard case let ScaleError.deviceNotFound(id) = error else {
                Issue.record("Expected deviceNotFound, got \(error)")
                return
            }
            #expect(id == "missing-scale")
        }
    }

    @Test("fetchAttachedPreference loads saved preference")
    func fetchAttachedPreferenceLoadsSavedPreference() async {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", displayName: "Bedroom Scale")
        repo.devices = [device]
        let sut = makeSUT(repo: repo)

        let preference = await sut.fetchAttachedPreference(by: "scale-1")
        let syncPreference = sut.fetchAttachedPreferenceSync(by: "scale-1")

        #expect(preference?.displayName == "Bedroom Scale")
        #expect(syncPreference?.displayName == "Bedroom Scale")
    }

    @Test("getDevices filters deleted scales and scopes to the active account")
    func getDevicesFiltersDeletedAndOtherAccounts() async throws {
        let repo = MockScaleRepository()
        repo.devices = [
            ScaleTestFixtures.makeDevice(id: "active-scale", accountId: "acct-1"),
            ScaleTestFixtures.makeDevice(id: "deleted-scale", accountId: "acct-1", isSoftDeleted: true),
            ScaleTestFixtures.makeDevice(id: "other-account-scale", accountId: "acct-2")
        ]
        let sut = makeSUT(repo: repo)

        let devices = try await sut.getDevices()

        #expect(devices.map(\.id) == ["active-scale"])
    }

    @Test("getDevices without active account throws")
    func getDevicesNoActiveAccountThrows() async {
        let account = MockAccountService()
        let sut = ScaleService(accountService: account, apiRepository: MockScaleRepositoryAPI(), localRepository: MockScaleRepository())

        do {
            _ = try await sut.getDevices()
            Issue.record("Expected getDevices to throw without an active account")
        } catch {
            guard case AccountError.noActiveAccount = error else {
                Issue.record("Expected noActiveAccount, got \(error)")
                return
            }
        }
    }

    @Test("getDevice returns the stored scale by id")
    func getDeviceReturnsStoredScale() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let sut = makeSUT(repo: repo)

        let device = try await sut.getDevice(by: "scale-1")

        #expect(device?.id == "scale-1")
    }

    @Test("getDevice returns nil for an unknown scale id")
    func getDeviceReturnsNilForMissingScale() async throws {
        let sut = makeSUT()

        let device = try await sut.getDevice(by: "missing-scale")

        #expect(device == nil)
    }

    @Test("getConnectedDevices returns only connected scales for the active account")
    func getConnectedDevicesScopesToActiveAccount() async {
        let repo = MockScaleRepository()
        let connected = ScaleTestFixtures.makeDevice(id: "connected-scale", accountId: "acct-1")
        connected.isConnected = true
        connected.isWifiConfigured = true
        connected.deviceName = "Kitchen Scale"
        let otherAccount = ScaleTestFixtures.makeDevice(id: "other-account-scale", accountId: "acct-2")
        otherAccount.isConnected = true
        repo.devices = [connected, otherAccount]
        let sut = makeSUT(repo: repo)

        let connectedDevices = await sut.getConnectedDevices()

        #expect(connectedDevices.count == 1)
        #expect(connectedDevices["40E20100"] != nil)
    }

    @Test("getConnectedDevices without active account returns empty")
    func getConnectedDevicesNoActiveAccountReturnsEmpty() async {
        let account = MockAccountService()
        let sut = ScaleService(accountService: account, apiRepository: MockScaleRepositoryAPI(), localRepository: MockScaleRepository())

        let connectedDevices = await sut.getConnectedDevices()

        #expect(connectedDevices.isEmpty)
    }

    @Test("getConnectedDevices repository failure returns empty")
    func getConnectedDevicesRepositoryFailureReturnsEmpty() async {
        let repo = MockScaleRepository()
        repo.listScalesError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo)

        let connectedDevices = await sut.getConnectedDevices()

        #expect(connectedDevices.isEmpty)
    }

    @Test("getConnectedDevices skips connected scales without a broadcast id")
    func getConnectedDevicesSkipsMissingBroadcastId() async {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(
            id: "connected-scale",
            accountId: "acct-1",
            broadcastIdString: nil,
            broadcastId: nil
        )
        device.isConnected = true
        repo.devices = [device]
        let sut = makeSUT(repo: repo)

        let connectedDevices = await sut.getConnectedDevices()

        #expect(connectedDevices.isEmpty)
    }

    @Test("updateConnectedDevices updates matching device connection and wifi status")
    func updateConnectedDevicesUpdatesStoredScale() async throws {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        repo.devices = [device]
        let sut = makeSUT(repo: repo)

        await sut.updateConnectedDevices(
            device: ["id": "scale-1", "broadcastId": "40E20100", "isWifiConfigured": true],
            isConnected: true
        )

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isConnected == true)
        #expect(stored?.isWifiConfigured == true)
    }

    @Test("updateConnectedDevices without active account leaves scale unchanged")
    func updateConnectedDevicesNoActiveAccountLeavesScaleUnchanged() async throws {
        let account = MockAccountService()
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let sut = ScaleService(accountService: account, apiRepository: MockScaleRepositoryAPI(), localRepository: repo)

        await sut.updateConnectedDevices(
            device: ["id": "scale-1", "broadcastId": "40E20100", "isWifiConfigured": true],
            isConnected: true
        )

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isConnected == false)
        #expect(stored?.isWifiConfigured == false)
    }

    @Test("updateConnectedDevices with unknown device leaves stored scales unchanged")
    func updateConnectedDevicesUnknownDeviceLeavesScalesUnchanged() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let sut = makeSUT(repo: repo)

        await sut.updateConnectedDevices(
            device: ["id": "missing-scale", "broadcastId": "UNKNOWN", "isWifiConfigured": true],
            isConnected: true
        )

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isConnected == false)
        #expect(stored?.isWifiConfigured == false)
    }

    @Test("updateConnectedDeviceWifiStatus updates only the active account scale")
    func updateConnectedDeviceWifiStatusUpdatesStoredScale() async throws {
        let repo = MockScaleRepository()
        let active = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")
        let other = ScaleTestFixtures.makeDevice(id: "scale-2", accountId: "acct-2")
        repo.devices = [active, other]
        let sut = makeSUT(repo: repo)

        await sut.updateConnectedDeviceWifiStatus(broadcastId: "40E20100", isConfigured: true)

        let activeStored = try await repo.getDevice("scale-1")
        let otherStored = try await repo.getDevice("scale-2")
        #expect(activeStored?.isWifiConfigured == true)
        #expect(otherStored?.isWifiConfigured == false)
    }

    @Test("updateConnectedDeviceWifiStatus missing device leaves stored scales unchanged")
    func updateConnectedDeviceWifiStatusMissingDeviceLeavesStateUnchanged() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")]
        let sut = makeSUT(repo: repo)

        await sut.updateConnectedDeviceWifiStatus(broadcastId: "UNKNOWN", isConfigured: true)

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isWifiConfigured == false)
    }

    @Test("updateConnectedDeviceWeightOnlyMode marks the active scale unsynced")
    func updateConnectedDeviceWeightOnlyModeMarksUnsynced() async throws {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1", isSynced: true, hasServerID: true)
        repo.devices = [device]
        let sut = makeSUT(repo: repo)

        await sut.updateConnectedDeviceWeightOnlyMode(broadcastId: "40E20100", isWeightOnlyModeEnabledByOthers: true)

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isWeighOnlyModeEnabledByOthers == true)
        #expect(stored?.isSynced == false)
    }

    @Test("updateConnectedDeviceWeightOnlyMode missing device leaves stored scales unchanged")
    func updateConnectedDeviceWeightOnlyModeMissingDeviceLeavesStateUnchanged() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1", isSynced: true, hasServerID: true)]
        let sut = makeSUT(repo: repo)

        await sut.updateConnectedDeviceWeightOnlyMode(broadcastId: "UNKNOWN", isWeightOnlyModeEnabledByOthers: true)

        let stored = try await repo.getDevice("scale-1")
        #expect(stored?.isWeighOnlyModeEnabledByOthers != true)
        #expect(stored?.isSynced == true)
    }

    @Test("createDevice returns existing duplicate by MAC without saving twice")
    func createDeviceDuplicateByMacReturnsExisting() async throws {
        let repo = MockScaleRepository()
        let existing = ScaleTestFixtures.makeDevice(id: "existing-scale", mac: "AA:BB:CC:DD:EE:FF", broadcastIdString: "ABC123")
        repo.devices = [existing]
        let sut = makeSUT(repo: repo)
        let duplicate = ScaleTestFixtures.makeDevice(id: "new-scale", mac: "AA:BB:CC:DD:EE:FF", broadcastIdString: "DIFFERENT")

        let result = try await sut.createDevice(duplicate)

        #expect(result.id == "existing-scale")
        #expect(repo.createScaleCalls == 0)
        #expect(repo.devices.count == 1)
    }

    @Test("createDevice returns existing duplicate by id without saving twice")
    func createDeviceDuplicateByIdReturnsExisting() async throws {
        let repo = MockScaleRepository()
        let existing = ScaleTestFixtures.makeDevice(id: "existing-scale", mac: "AA:BB:CC:DD:EE:FF", broadcastIdString: "ABC123")
        repo.devices = [existing]
        let sut = makeSUT(repo: repo)
        let duplicate = ScaleTestFixtures.makeDevice(id: "existing-scale", mac: "11:22:33:44:55:66", broadcastIdString: "DIFFERENT")

        let result = try await sut.createDevice(duplicate)

        #expect(result.id == "existing-scale")
        #expect(repo.createScaleCalls == 0)
        #expect(repo.devices.count == 1)
    }

    @Test("createDevice returns existing duplicate by broadcast id without saving twice")
    func createDeviceDuplicateByBroadcastIdReturnsExisting() async throws {
        let repo = MockScaleRepository()
        let existing = ScaleTestFixtures.makeDevice(id: "existing-scale", mac: "AA:BB:CC:DD:EE:FF", broadcastIdString: "ABC123")
        repo.devices = [existing]
        let sut = makeSUT(repo: repo)
        let duplicate = ScaleTestFixtures.makeDevice(id: "new-scale", mac: "11:22:33:44:55:66", broadcastIdString: "ABC123")

        let result = try await sut.createDevice(duplicate)

        #expect(result.id == "existing-scale")
        #expect(repo.createScaleCalls == 0)
        #expect(repo.devices.count == 1)
    }

    @Test("createScaleInLocal persists the provided device")
    func createScaleInLocalPersistsDevice() async throws {
        let repo = MockScaleRepository()
        let sut = makeSUT(repo: repo)
        let device = ScaleTestFixtures.makeDevice(id: "local-scale")

        let created = try await sut.createScaleInLocal(device)

        #expect(created.id == "local-scale")
        #expect(repo.createScaleCalls == 1)
        #expect(try await repo.getDevice("local-scale")?.id == "local-scale")
    }

    @Test("createScaleInLocal propagates repository failure")
    func createScaleInLocalPropagatesFailure() async {
        let repo = MockScaleRepository()
        repo.createScaleError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo)

        do {
            _ = try await sut.createScaleInLocal(ScaleTestFixtures.makeDevice(id: "local-scale"))
            Issue.record("Expected createScaleInLocal to throw")
        } catch {
            #expect(error as? ScaleTestError == .localFailure)
        }
    }

    @Test("editDevice success updates local device and returns edited copy")
    func editDeviceSuccess() async throws {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        let sut = makeSUT(repo: repo)

        let edited = try await sut.editDevice("scale-1", properties: ["nickname": "Kitchen Scale"])

        #expect(repo.editScaleCalls == 1)
        #expect(repo.lastEditedScaleId == "scale-1")
        #expect(edited.nickname == "Kitchen Scale")
    }

    @Test("editDevice missing scale throws deviceNotFound")
    func editDeviceMissingScaleThrows() async {
        let sut = makeSUT()

        do {
            _ = try await sut.editDevice("missing-scale", properties: ["nickname": "Kitchen Scale"])
            Issue.record("Expected editDevice to throw")
        } catch {
            guard case let ScaleError.deviceNotFound(id) = error else {
                Issue.record("Expected deviceNotFound, got \(error)")
                return
            }
            #expect(id == "missing-scale")
        }
    }

    @Test("editDevice propagates repository edit failure")
    func editDevicePropagatesRepositoryFailure() async {
        let repo = MockScaleRepository()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "scale-1")]
        repo.editScaleError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo)

        do {
            _ = try await sut.editDevice("scale-1", properties: ["nickname": "Kitchen Scale"])
            Issue.record("Expected editDevice to throw")
        } catch {
            #expect(error as? ScaleTestError == .localFailure)
        }
    }

    @Test("deleteDevice purely local scale removes it locally without remote delete")
    func deleteDevicePurelyLocalRemovesImmediately() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "local-scale", isSynced: false, hasServerID: false)]
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.deleteDevice("local-scale", showToast: false)

        #expect(repo.deleteScaleCalls == 1)
        #expect(repo.markDeviceAsDeletedCalls == 0)
        #expect(remote.deleteScaleCalls == 0)
        #expect(repo.devices.isEmpty)
        #expect(sut.scales.isEmpty)
    }

    @Test("deleteDevice server-backed scale marks deleted, syncs remote delete, and removes local copy")
    func deleteDeviceServerBackedRemovesAfterSync() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "server-scale", isSynced: true, hasServerID: true)]
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.deleteDevice("server-scale", showToast: false)

        #expect(repo.markDeviceAsDeletedCalls == 1)
        #expect(remote.deleteScaleCalls == 1)
        #expect(remote.lastDeletedScaleId == "server-scale")
        #expect(repo.permanentlyRemoveDeviceCalls == 1)
        #expect(repo.devices.isEmpty)
    }

    @Test("deleteDevice removes duplicate purely local scales matched by mac and broadcast id")
    func deleteDeviceRemovesDuplicatePurelyLocalCandidates() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let target = ScaleTestFixtures.makeDevice(
            id: "target-scale",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "40E20100",
            broadcastId: 123456,
            isSynced: false,
            hasServerID: false
        )
        let duplicateByMac = ScaleTestFixtures.makeDevice(
            id: "dup-mac",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "99999999",
            broadcastId: 999999,
            isSynced: false,
            hasServerID: false
        )
        let duplicateByBid = ScaleTestFixtures.makeDevice(
            id: "dup-bid",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "40E20100",
            broadcastId: 123456,
            isSynced: false,
            hasServerID: false
        )
        repo.devices = [target, duplicateByMac, duplicateByBid]
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.deleteDevice("target-scale", showToast: false)

        #expect(repo.deleteScaleCalls == 3)
        #expect(repo.markDeviceAsDeletedCalls == 0)
        #expect(repo.devices.isEmpty)
    }

    @Test("deleteDevice removes duplicate server-backed scales matched by mac only")
    func deleteDeviceMarksDuplicateServerBackedCandidatesByMac() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let target = ScaleTestFixtures.makeDevice(
            id: "target-scale",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: nil,
            broadcastId: nil,
            isSynced: true,
            hasServerID: true
        )
        let duplicate = ScaleTestFixtures.makeDevice(
            id: "dup-mac",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: nil,
            broadcastId: nil,
            isSynced: true,
            hasServerID: true
        )
        repo.devices = [target, duplicate]
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.deleteDevice("target-scale", showToast: false)

        #expect(repo.markDeviceAsDeletedCalls == 2)
        #expect(remote.deleteScaleCalls == 2)
        #expect(repo.devices.isEmpty)
    }

    @Test("deleteDevice removes duplicate server-backed scales matched by broadcast id only")
    func deleteDeviceMarksDuplicateServerBackedCandidatesByBroadcastId() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let target = ScaleTestFixtures.makeDevice(
            id: "target-scale",
            mac: nil,
            broadcastIdString: "40E20100",
            broadcastId: 123456,
            isSynced: true,
            hasServerID: true
        )
        let duplicate = ScaleTestFixtures.makeDevice(
            id: "dup-bid",
            mac: nil,
            broadcastIdString: "40E20100",
            broadcastId: 123456,
            isSynced: true,
            hasServerID: true
        )
        repo.devices = [target, duplicate]
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.deleteDevice("target-scale", showToast: false)

        #expect(repo.markDeviceAsDeletedCalls == 2)
        #expect(remote.deleteScaleCalls == 2)
        #expect(repo.devices.isEmpty)
    }

    @Test("deleteDevice missing scale throws deviceNotFound")
    func deleteDeviceMissingScaleThrows() async {
        let sut = makeSUT()

        do {
            try await sut.deleteDevice("missing-scale", showToast: false)
            Issue.record("Expected deleteDevice to throw")
        } catch {
            guard case let ScaleError.deviceNotFound(id) = error else {
                Issue.record("Expected deviceNotFound, got \(error)")
                return
            }
            #expect(id == "missing-scale")
        }
    }

    @Test("updateScaleMeta success patches remote and stores synced metadata locally")
    func updateScaleMetaSuccess() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", isSynced: true, hasServerID: true)
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)
        let metaData = ScaleTestFixtures.makeMetaData(modelNumber: "R4-Pro", latestVersion: "2.0.0")

        try await sut.updateScaleMeta("scale-1", metaData: metaData)

        #expect(remote.patchScaleMetaCalls == 1)
        #expect(remote.lastPatchedMetaScaleId == "scale-1")
        #expect(repo.patchScaleMetaCalls == 1)
        #expect(repo.lastPatchedMetaData?.latestVersion == "2.0.0")
        #expect(try await repo.getDevice("scale-1")?.metaData?.isSynced == true)
    }

    @Test("updateScaleMeta remote failure keeps local metadata unsynced")
    func updateScaleMetaRemoteFailure() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.patchScaleMetaError = ScaleTestError.remoteFailure
        let device = ScaleTestFixtures.makeDevice(id: "scale-1", isSynced: true, hasServerID: true)
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)
        let metaData = ScaleTestFixtures.makeMetaData(modelNumber: "R4-Pro", latestVersion: "2.0.0")

        try await sut.updateScaleMeta("scale-1", metaData: metaData)

        #expect(remote.patchScaleMetaCalls == 1)
        #expect(repo.patchScaleMetaCalls == 1)
        #expect(try await repo.getDevice("scale-1")?.metaData?.latestVersion == "2.0.0")
        #expect(try await repo.getDevice("scale-1")?.metaData?.isSynced == false)
    }

    @Test("pushLocalChangesToServer creates purely local scale, patches preference, and promotes server ID")
    func pushLocalChangesCreatesPurelyLocalDevice() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let localDevice = ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false)
        repo.devices = [localDevice]
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(id: "server-scale", displayName: "Server Scale")
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.createScaleCalls == 1)
        #expect(remote.patchScalePreferenceCalls == 1)
        #expect(repo.devices.count == 1)
        #expect(repo.devices.first?.id == "server-scale")
        #expect(repo.devices.first?.hasServerID == true)
        #expect(repo.devices.first?.isSynced == true)
        #expect(repo.devices.first?.r4ScalePreference?.isSynced == true)
    }

    @Test("pushLocalChangesToServer promotes local-only scale to remote-backed record")
    func pushLocalChangesPromotesLocalOnlyScaleToRemote() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let localOnly = ScaleTestFixtures.makeDevice(
            id: "local-only-scale",
            accountId: "acct-1",
            isSynced: false,
            hasServerID: false
        )
        repo.devices = [localOnly]
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(
            id: "server-promoted-scale",
            accountId: "acct-1",
            displayName: "Promoted Scale"
        )
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.createScaleCalls == 1)
        #expect(repo.devices.count == 1)
        #expect(repo.devices.first?.id == "server-promoted-scale")
        #expect(repo.devices.first?.hasServerID == true)
        #expect(repo.devices.first?.isSynced == true)
    }

    @Test("pushLocalChangesToServer updates existing server-backed device and syncs metadata and preference")
    func pushLocalChangesUpdatesServerBackedDevice() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let device = ScaleTestFixtures.makeDevice(id: "server-scale", isSynced: false, hasServerID: true)
        let metaData = ScaleTestFixtures.makeMetaData(isSynced: false)
        device.metaData = metaData
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.editScaleCalls == 1)
        #expect(remote.lastEditedScaleId == "server-scale")
        #expect(remote.patchScaleMetaCalls == 1)
        #expect(remote.patchScalePreferenceCalls == 1)
        #expect(repo.updateDeviceCalls == 1)
        #expect(repo.devices.first?.isSynced == true)
        #expect(repo.devices.first?.metaData?.isSynced == true)
        #expect(repo.devices.first?.r4ScalePreference?.isSynced == true)
    }

    @Test("pushLocalChangesToServer delete not found still removes local device")
    func pushLocalChangesDeleteNotFoundStillRemovesLocalDevice() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.deleteScaleError = NSError(domain: "test", code: 404, userInfo: [NSLocalizedDescriptionKey: "Not found"])
        let deleted = ScaleTestFixtures.makeDevice(id: "deleted-scale", isSynced: true, hasServerID: true, isSoftDeleted: true)
        repo.devices = [deleted]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.deleteScaleCalls == 1)
        #expect(repo.permanentlyRemoveDeviceCalls == 1)
        #expect(repo.devices.isEmpty)
    }

    @Test("pushLocalChangesToServer delete failure keeps soft-deleted device for retry")
    func pushLocalChangesDeleteFailureKeepsDeviceForRetry() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.deleteScaleError = ScaleTestError.remoteFailure
        let deleted = ScaleTestFixtures.makeDevice(id: "deleted-scale", isSynced: true, hasServerID: true, isSoftDeleted: true)
        repo.devices = [deleted]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.deleteScaleCalls == 1)
        #expect(repo.permanentlyRemoveDeviceCalls == 0)
        #expect(repo.devices.count == 1)
        #expect(repo.devices.first?.id == "deleted-scale")
        #expect(repo.devices.first?.isSoftDeleted == true)
    }

    @Test("pushLocalChangesToServer update failure leaves device unsynced")
    func pushLocalChangesUpdateFailureLeavesDeviceUnsynced() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.editScaleError = ScaleTestError.remoteFailure
        let device = ScaleTestFixtures.makeDevice(id: "server-scale", isSynced: false, hasServerID: true)
        repo.devices = [device]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.editScaleCalls == 1)
        let stored = try? await repo.getDevice("server-scale")
        #expect(stored?.isSynced == false)
    }

    @Test("pushLocalChangesToServer create failure keeps local device pending sync")
    func pushLocalChangesCreateFailureKeepsLocalDevicePending() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.createScaleError = ScaleTestError.remoteFailure
        let localDevice = ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false)
        repo.devices = [localDevice]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.createScaleCalls == 1)
        #expect(repo.devices.first?.id == "temp-scale")
        #expect(repo.devices.first?.hasServerID == false)
        #expect(repo.devices.first?.isSynced == false)
    }

    @Test("pushLocalChangesToServer without active account still processes local deletion queue")
    func pushLocalChangesWithoutActiveAccountProcessesDeletionQueue() async {
        let account = MockAccountService()
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let deleted = ScaleTestFixtures.makeDevice(id: "deleted-scale", isSynced: true, hasServerID: true, isSoftDeleted: true)
        repo.devices = [deleted]
        let sut = ScaleService(accountService: account, apiRepository: remote, localRepository: repo)

        await sut.pushLocalChangesToServer()

        #expect(remote.deleteScaleCalls == 1)
        #expect(repo.devices.isEmpty)
    }

    @Test("pushLocalChangesToServer deletion fetch failure leaves local state untouched")
    func pushLocalChangesDeletionFetchFailureLeavesStateUntouched() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "deleted-scale", isSynced: true, hasServerID: true, isSoftDeleted: true)]
        repo.getDevicesMarkedForDeletionError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.deleteScaleCalls == 0)
        #expect(repo.permanentlyRemoveDeviceCalls == 0)
        #expect(repo.devices.count == 1)
    }

    @Test("pushLocalChangesToServer unsynced fetch failure leaves local state untouched")
    func pushLocalChangesUnsyncedFetchFailureLeavesStateUntouched() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false)]
        repo.getUnsyncedDevicesError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.pushLocalChangesToServer()

        #expect(remote.createScaleCalls == 0)
        #expect(repo.devices.first?.id == "temp-scale")
        #expect(repo.devices.first?.isSynced == false)
    }

    @Test("syncAllScalesWithRemote no active account skips local and remote work")
    func syncAllScalesWithRemoteNoActiveAccountSkips() async {
        let account = MockAccountService()
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let sut = ScaleService(accountService: account, apiRepository: remote, localRepository: repo)

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls == 0)
        #expect(remote.createScaleCalls == 0)
        #expect(repo.replaceAllDevicesForAccountCalls == 0)
    }

    @Test("syncAllScalesWithRemote replaces local state with server scales for the active account")
    func syncAllScalesWithRemoteLoadsServerScales() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.listScalesResult = [
            ScaleTestFixtures.makeScaleDTO(id: "server-scale", displayName: "Server Scale")
        ]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls == 1)
        #expect(repo.replaceAllDevicesForAccountCalls == 1)
        #expect(repo.lastReplacedAccountId == "acct-1")
        #expect(sut.scales.count == 1)
        #expect(sut.scales.first?.id == "server-scale")
    }

    @Test("syncAllScalesWithRemote adds remote-only scale to local state")
    func syncAllScalesWithRemoteAddsRemoteOnlyScaleLocally() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = []
        remote.listScalesResult = [
            ScaleTestFixtures.makeScaleDTO(id: "remote-only-scale", displayName: "Remote Only Scale")
        ]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(repo.replaceAllDevicesForAccountCalls == 1)
        #expect(sut.scales.map(\.id) == ["remote-only-scale"])
        #expect(sut.scales.first?.hasServerID == true)
        #expect(sut.scales.first?.r4ScalePreference?.displayName == "Remote Only Scale")
    }

    @Test("syncAllScalesWithRemote skips reconciliation when server id already exists for the active account")
    func syncAllScalesWithRemoteSkipsExistingServerIdMatch() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let existing = ScaleTestFixtures.makeDevice(id: "server-scale", accountId: "acct-1", isSynced: true, hasServerID: true)
        repo.devices = [existing]
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(id: "server-scale", accountId: "acct-1")]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(repo.updateDeviceCalls == 0)
        #expect(sut.scales.map(\.id) == ["server-scale"])
    }

    @Test("syncAllScalesWithRemote does not reconcile a server id that belongs to another account locally")
    func syncAllScalesWithRemoteSkipsCrossAccountServerIdMatch() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let otherAccount = ScaleTestFixtures.makeDevice(id: "shared-server-id", accountId: "acct-2", isSynced: true, hasServerID: true)
        repo.devices = [otherAccount]
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(
            id: "shared-server-id",
            accountId: "acct-1",
            mac: "77:88:99:AA:BB:CC",
            broadcastIdString: "BBBBBB",
            broadcastId: 222222
        )]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(repo.updateDeviceCalls == 0)
        #expect(sut.scales.map(\.id) == ["shared-server-id"])
    }

    @Test("syncAllScalesWithRemote remote fetch failure leaves published scales unchanged")
    func syncAllScalesWithRemoteRemoteFetchFailureLeavesStateUntouched() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "existing-scale", isSynced: true, hasServerID: true)]
        remote.listScalesError = ScaleTestError.remoteFailure
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls == 1)
        #expect(sut.scales.map(\.id) == ["existing-scale"])
    }

    @Test("syncAllScalesWithRemote pushes unsynced local scales before applying pulled server state")
    func syncAllScalesWithRemotePushesUnsyncedLocalScalesBeforePull() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let localUnsynced = ScaleTestFixtures.makeDevice(
            id: "local-unsynced",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: false,
            hasServerID: false
        )
        repo.devices = [localUnsynced]
        remote.listScalesResult = [
            ScaleTestFixtures.makeScaleDTO(
                id: "server-scale",
                accountId: "acct-1",
                displayName: "Server Scale",
                mac: "77:88:99:AA:BB:CC",
                broadcastIdString: "BBBBBB",
                broadcastId: 222222
            )
        ]
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(
            id: "created-from-local",
            accountId: "acct-1",
            displayName: "Created From Local",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111
        )
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(remote.createScaleCalls == 1)
        #expect(repo.replaceAllDevicesForAccountCalls == 1)
        #expect(sut.scales.map(\.id) == ["server-scale"])
    }

    @Test("syncAllScalesWithRemote reconciles pulled scales by matching local device on mac address")
    func syncAllScalesWithRemoteReconcilesByMac() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let local = ScaleTestFixtures.makeDevice(
            id: "local-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: false,
            hasServerID: false
        )
        repo.devices = [local]
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(
            id: "server-created",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111
        )
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(
            id: "server-created",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "ZZZZZZ",
            broadcastId: 222222
        )]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(repo.devices.contains { $0.id == "server-created" })
        #expect(sut.scales.map(\.id) == ["server-created"])
    }

    @Test("syncAllScalesWithRemote reconciles pulled scales by matching local device on broadcast id")
    func syncAllScalesWithRemoteReconcilesByBroadcastId() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let local = ScaleTestFixtures.makeDevice(
            id: "local-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: false,
            hasServerID: false
        )
        local.mac = nil
        repo.devices = [local]
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(
            id: "server-created",
            accountId: "acct-1",
            mac: "",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111
        )
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(
            id: "server-created",
            accountId: "acct-1",
            mac: "",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111
        )]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(repo.devices.contains { $0.id == "server-created" })
        #expect(sut.scales.map(\.id) == ["server-created"])
    }

    @Test("syncAllScalesWithRemote removes synced orphan local scales when server returns empty state")
    func syncAllScalesWithRemoteRemovesSyncedOrphanScale() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let orphan = ScaleTestFixtures.makeDevice(
            id: "orphan-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: true,
            hasServerID: true
        )
        repo.devices = [orphan]
        remote.listScalesResult = []
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(repo.replaceAllDevicesForAccountCalls == 1)
        #expect(sut.scales.isEmpty)
    }

    @Test("syncAllScalesWithRemote preserves unsynced local scale when server returns empty state")
    func syncAllScalesWithRemotePreservesUnsyncedLocalScale() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let unsynced = ScaleTestFixtures.makeDevice(
            id: "pending-local-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: false,
            hasServerID: false
        )
        repo.devices = [unsynced]
        remote.listScalesResult = []
        remote.createScaleError = ScaleTestError.remoteFailure
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(remote.createScaleCalls == 1)
        #expect(repo.replaceAllDevicesForAccountCalls == 1)
        #expect(repo.lastPreservedUnsyncedDevices.count == 1)
        #expect(sut.scales.map(\.id) == ["pending-local-scale"])
        #expect(sut.scales.first?.isSynced == false)
        #expect(sut.scales.first?.hasServerID == false)
    }

    @Test("syncAllScalesWithRemote keeps synced local scale when server matches by id")
    func syncAllScalesWithRemoteKeepsSyncedScaleMatchedById() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let local = ScaleTestFixtures.makeDevice(id: "server-scale", accountId: "acct-1", isSynced: true, hasServerID: true)
        repo.devices = [local]
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(id: "server-scale", accountId: "acct-1")]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(sut.scales.map(\.id) == ["server-scale"])
    }

    @Test("syncAllScalesWithRemote keeps synced local scale when server matches by mac")
    func syncAllScalesWithRemoteKeepsSyncedScaleMatchedByMac() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let local = ScaleTestFixtures.makeDevice(
            id: "server-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: true,
            hasServerID: true
        )
        repo.devices = [local]
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(
            id: "different-server-id",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "BBBBBB",
            broadcastId: 222222
        )]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(sut.scales.map(\.id) == ["different-server-id"])
    }

    @Test("syncAllScalesWithRemote keeps synced local scale when server matches by broadcast id")
    func syncAllScalesWithRemoteKeepsSyncedScaleMatchedByBroadcastId() async {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let local = ScaleTestFixtures.makeDevice(
            id: "server-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111,
            isSynced: true,
            hasServerID: true
        )
        local.mac = nil
        repo.devices = [local]
        remote.listScalesResult = [ScaleTestFixtures.makeScaleDTO(
            id: "different-server-id",
            accountId: "acct-1",
            mac: "",
            broadcastIdString: "AAAAAA",
            broadcastId: 111111
        )]
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(sut.scales.map(\.id) == ["different-server-id"])
    }

    @Test("updateAllScalesStatus restores connected flags from the connected-device map")
    func updateAllScalesStatusUsesConnectedDeviceMap() async throws {
        let repo = MockScaleRepository()
        let connected = ScaleTestFixtures.makeDevice(id: "connected-scale", accountId: "acct-1")
        connected.isConnected = true
        connected.isWifiConfigured = true
        let disconnected = ScaleTestFixtures.makeDevice(
            id: "disconnected-scale",
            accountId: "acct-1",
            mac: "11:22:33:44:55:66",
            broadcastIdString: "0F0F0F",
            broadcastId: 654321
        )
        disconnected.isConnected = false
        disconnected.isWifiConfigured = false
        repo.devices = [connected, disconnected]
        let sut = makeSUT(repo: repo)

        try await sut.updateAllScalesStatus()

        let connectedStored = try await repo.getDevice("connected-scale")
        let disconnectedStored = try await repo.getDevice("disconnected-scale")
        #expect(connectedStored?.isConnected == true)
        #expect(connectedStored?.isWifiConfigured == true)
        #expect(disconnectedStored?.isConnected == false)
    }

    @Test("updateAllScalesStatus uses provided scales and fills missing broadcast id string")
    func updateAllScalesStatusProvidedScalesResolvesBroadcastId() async throws {
        let repo = MockScaleRepository()
        let device = ScaleTestFixtures.makeDevice(
            id: "provided-scale",
            accountId: "acct-1",
            broadcastIdString: "",
            broadcastId: 123456
        )
        device.isConnected = false
        repo.devices = [device]
        let sut = makeSUT(repo: repo)

        try await sut.updateAllScalesStatus([device])

        let stored = try await repo.getDevice("provided-scale")
        #expect(stored?.broadcastIdString?.isEmpty == false)
    }

    @Test("updateAllScalesStatus list failure throws")
    func updateAllScalesStatusListFailureThrows() async {
        let repo = MockScaleRepository()
        repo.listScalesError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo)

        do {
            try await sut.updateAllScalesStatus()
            Issue.record("Expected updateAllScalesStatus to throw")
        } catch {
            #expect(error as? ScaleTestError == .localFailure)
        }
    }

    @Test("syncDevices stores a temp device locally before syncing")
    func syncDevicesWithTempDeviceCreatesLocalScale() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let tempDevice = ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false)
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(id: "server-scale", displayName: "Server Scale")
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.syncDevices(tempDevice: tempDevice)

        #expect(repo.createScaleCalls == 1)
        #expect(remote.listScalesCalls == 1)
    }

    @Test("syncDevices temp device local creation failure throws apiSyncFailed")
    func syncDevicesTempDeviceCreateFailureThrowsApiSyncFailed() async {
        let repo = MockScaleRepository()
        repo.createScaleError = ScaleTestError.localFailure
        let sut = makeSUT(repo: repo, remote: MockScaleRepositoryAPI())

        do {
            try await sut.syncDevices(tempDevice: ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false))
            Issue.record("Expected syncDevices to throw")
        } catch {
            guard case let ScaleError.apiSyncFailed(error) = error else {
                Issue.record("Expected apiSyncFailed, got \(error)")
                return
            }
            #expect(error as? ScaleTestError == .localFailure)
        }
    }

    @Test("syncDevices skips duplicate temp device creation and still syncs")
    func syncDevicesDuplicateTempDeviceSkipsCreate() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false)]
        let duplicate = ScaleTestFixtures.makeDevice(id: "temp-scale", isSynced: false, hasServerID: false)
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.syncDevices(tempDevice: duplicate)

        #expect(repo.createScaleCalls == 0)
        #expect(remote.listScalesCalls == 1)
    }

    @Test("syncDevices skips temp device creation when a duplicate exists by broadcast id")
    func syncDevicesDuplicateTempDeviceByBroadcastIdSkipsCreate() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(
            id: "existing-scale",
            broadcastIdString: "ABC123",
            broadcastId: 111111,
            isSynced: false,
            hasServerID: false
        )]
        let duplicate = ScaleTestFixtures.makeDevice(
            id: "temp-scale",
            broadcastIdString: "ABC123",
            broadcastId: 111111,
            isSynced: false,
            hasServerID: false
        )
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.syncDevices(tempDevice: duplicate)

        #expect(repo.createScaleCalls == 0)
        #expect(remote.listScalesCalls == 1)
    }

    @Test("syncDevices skips temp device creation when a duplicate exists by mac")
    func syncDevicesDuplicateTempDeviceByMacSkipsCreate() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        repo.devices = [ScaleTestFixtures.makeDevice(
            id: "existing-scale",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "ABC123",
            isSynced: false,
            hasServerID: false
        )]
        let duplicate = ScaleTestFixtures.makeDevice(
            id: "temp-scale",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastIdString: "DIFFERENT",
            isSynced: false,
            hasServerID: false
        )
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.syncDevices(tempDevice: duplicate)

        #expect(repo.createScaleCalls == 0)
        #expect(remote.listScalesCalls == 1)
    }

    @Test("syncDevices without temp device only performs sync")
    func syncDevicesWithoutTempDeviceOnlyPerformsSync() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(repo: repo, remote: remote)

        try await sut.syncDevices(tempDevice: nil)

        #expect(repo.createScaleCalls == 0)
        #expect(remote.listScalesCalls == 1)
    }

    @Test("createBluetoothScale stores device with bluetooth bath scale and syncs it")
    func createBluetoothScaleSuccess() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(id: "server-scale")
        let sut = makeSUT(repo: repo, remote: remote)
        let device = Device(id: "bt-scale", accountId: "acct-1", mac: "AA:BB:CC:DD:EE:FF")

        let saved = try await sut.createBluetoothScale(
            device: device,
            sku: "BT-001",
            userNumber: "7",
            accountId: "acct-1",
            deviceMetadata: ScaleTestFixtures.makeMetaData()
        )

        #expect(saved.bathScale?.scaleType == ScaleSourceType.bluetooth.rawValue)
        #expect(saved.userNumber == "7")
        #expect(saved.nickname == "Bluetooth Smart Scale")
        #expect(remote.createScaleCalls == 1)
    }

    @Test("createBluetoothScale updates an existing bath scale and fills missing id")
    func createBluetoothScaleUpdatesExistingBathScale() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(id: "server-scale")
        let sut = makeSUT(repo: repo, remote: remote)
        let device = Device(id: "", accountId: "acct-1", mac: "AA:BB:CC:DD:EE:FF")
        device.nickname = "Named Scale"
        device.bathScale = BathScale(scaleType: ScaleSourceType.lcbt.rawValue, bodyComp: true)

        let saved = try await sut.createBluetoothScale(
            device: device,
            sku: "BT-001",
            userNumber: "7",
            accountId: "acct-1"
        )

        #expect(saved.id.isEmpty == false)
        #expect(saved.bathScale?.scaleType == ScaleSourceType.bluetooth.rawValue)
        #expect(saved.nickname == "Named Scale")
        #expect(remote.createScaleCalls == 1)
    }

    @Test("createA6Scale stores device with lcbt bath scale and syncs it")
    func createA6ScaleSuccess() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(id: "server-scale")
        let sut = makeSUT(repo: repo, remote: remote)
        let device = Device(id: "a6-scale", accountId: "acct-1", mac: "AA:BB:CC:DD:EE:FF")

        let saved = try await sut.createA6Scale(
            device: device,
            sku: "A6-001",
            accountId: "acct-1",
            deviceMetadata: ScaleTestFixtures.makeMetaData()
        )

        #expect(saved.bathScale?.scaleType == ScaleSourceType.lcbt.rawValue)
        #expect(saved.nickname == "Bluetooth Smart Scale")
        #expect(remote.createScaleCalls == 1)
    }

    @Test("createA6Scale updates an existing bath scale without resetting nickname")
    func createA6ScaleUpdatesExistingBathScale() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.createScaleResult = ScaleTestFixtures.makeScaleDTO(id: "server-scale")
        let sut = makeSUT(repo: repo, remote: remote)
        let device = Device(id: "a6-scale", accountId: "acct-1", mac: "AA:BB:CC:DD:EE:FF")
        device.nickname = "Custom Name"
        device.bathScale = BathScale(scaleType: ScaleSourceType.bluetooth.rawValue, bodyComp: true)

        let saved = try await sut.createA6Scale(
            device: device,
            sku: "A6-001",
            accountId: "acct-1"
        )

        #expect(saved.bathScale?.scaleType == ScaleSourceType.lcbt.rawValue)
        #expect(saved.nickname == "Custom Name")
        #expect(remote.createScaleCalls == 1)
    }

    // MARK: - Unified Device API (Me App 2.0)

    @Test("createPairedDevice success: delegates to remoteRepo with the provided request and returns the response")
    func createPairedDeviceSuccess() async throws {
        let remote = MockScaleRepositoryAPI()
        let expected = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-new")
        remote.createPairedDeviceResult = expected
        let sut = makeSUT(remote: remote)

        let req = ScaleTestFixtures.makePairedDeviceRequest(deviceType: "weight_scale", nickname: "Kitchen Scale")
        let result = try await sut.createPairedDevice(req)

        #expect(remote.createPairedDeviceCalls == 1)
        #expect(remote.lastCreatedPairedDevice?.deviceType == "weight_scale")
        #expect(remote.lastCreatedPairedDevice?.nickname == "Kitchen Scale")
        #expect(result.id == "pd-new")
    }

    @Test("createPairedDevice failure: propagates remote error")
    func createPairedDeviceFailure() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.createPairedDeviceError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: (any Error).self) {
            try await sut.createPairedDevice(ScaleTestFixtures.makePairedDeviceRequest())
        }
        #expect(remote.createPairedDeviceCalls == 1)
    }

    @Test("updatePairedDevice success: wraps nickname in PairedDeviceUpdateRequest and delegates to remoteRepo")
    func updatePairedDeviceSuccess() async throws {
        let remote = MockScaleRepositoryAPI()
        let expected = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-1", nickname: "New Name")
        remote.updatePairedDeviceResult = expected
        let sut = makeSUT(remote: remote)

        let result = try await sut.updatePairedDevice("pd-1", nickname: "New Name")

        #expect(remote.updatePairedDeviceCalls == 1)
        #expect(remote.lastUpdatedPairedDeviceId == "pd-1")
        #expect(remote.lastUpdatedPairedDevice?.nickname == "New Name")
        #expect(result.id == "pd-1")
    }

    @Test("updatePairedDevice failure: propagates remote error")
    func updatePairedDeviceFailure() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.updatePairedDeviceError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: (any Error).self) {
            try await sut.updatePairedDevice("pd-1", nickname: "X")
        }
    }

    @Test("deletePairedDevice success: delegates to remoteRepo with the provided deviceId")
    func deletePairedDeviceSuccess() async throws {
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(remote: remote)

        try await sut.deletePairedDevice("pd-99")

        #expect(remote.deletePairedDeviceCalls == 1)
        #expect(remote.lastDeletedPairedDeviceId == "pd-99")
    }

    @Test("deletePairedDevice failure: propagates remote error")
    func deletePairedDeviceFailure() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.deletePairedDeviceError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: (any Error).self) {
            try await sut.deletePairedDevice("pd-99")
        }
    }

    // MARK: - pairDevice (default protocol impl)

    @Test("pairDevice uses bathScale.scaleType as connectionType when available")
    func pairDeviceUsesBathScaleType() async throws {
        let remote = MockScaleRepositoryAPI()
        let expected = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-bath")
        remote.createPairedDeviceResult = expected
        let device = ScaleTestFixtures.makeDevice()
        // makeDevice sets bathScale.scaleType = btWifiR4
        let sut = makeSUT(remote: remote)

        let result = try await sut.pairDevice(device, deviceType: .scale)

        #expect(remote.lastCreatedPairedDevice?.type == ScaleSourceType.btWifiR4.rawValue)
        #expect(remote.lastCreatedPairedDevice?.deviceType == DeviceType.scale.serverValue)
        #expect(result.id == "pd-bath")
    }

    @Test("pairDevice falls back to protocolType when bathScale is nil")
    func pairDeviceFallsBackToProtocolType() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.createPairedDeviceResult = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-proto")
        let device = ScaleTestFixtures.makeDevice()
        device.bathScale = nil
        device.protocolType = ScaleSourceType.bluetooth.rawValue
        let sut = makeSUT(remote: remote)

        _ = try await sut.pairDevice(device, deviceType: .scale)

        #expect(remote.lastCreatedPairedDevice?.type == ScaleSourceType.bluetooth.rawValue)
    }

    @Test("pairDevice falls back to bluetooth when bathScale and protocolType are both nil")
    func pairDeviceFallsBackToBluetooth() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.createPairedDeviceResult = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-bt")
        let device = ScaleTestFixtures.makeDevice()
        device.bathScale = nil
        device.protocolType = nil
        let sut = makeSUT(remote: remote)

        _ = try await sut.pairDevice(device, deviceType: .bpm)

        #expect(remote.lastCreatedPairedDevice?.type == ScaleSourceType.bluetooth.rawValue)
        #expect(remote.lastCreatedPairedDevice?.deviceType == DeviceType.bpm.serverValue)
    }

    @Test("pairDevice propagates createPairedDevice error")
    func pairDevicePropagatesError() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.createPairedDeviceError = ScaleTestError.remoteFailure
        let device = ScaleTestFixtures.makeDevice()
        let sut = makeSUT(remote: remote)

        await #expect(throws: (any Error).self) {
            _ = try await sut.pairDevice(device, deviceType: .scale)
        }
    }

    private func makeSUT(
        account: MockAccountService? = nil,
        repo: MockScaleRepository? = nil,
        remote: (any ScaleRepositoryAPIProtocol)? = nil
    ) -> ScaleService {
        let account = account ?? MockAccountService()
        if account.activeAccount == nil {
            account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "scale@example.com", isActiveAccount: true)
        }

        return ScaleService(
            accountService: account,
            apiRepository: remote ?? MockScaleRepositoryAPI(),
            localRepository: repo ?? MockScaleRepository()
        )
    }
}
