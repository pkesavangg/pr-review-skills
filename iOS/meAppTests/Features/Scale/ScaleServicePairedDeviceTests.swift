import Foundation
import Testing
@testable import meApp

/// Tests for the unified `/paired-device/` service-layer wiring on `ScaleService` (MOB-383):
/// list/create/update/delete delegation and the `pairDevice(_:deviceType:)` Device→request mapping.
@Suite(.serialized)
@MainActor
struct ScaleServicePairedDeviceTests {

    private func makeSUT(remote: MockScaleRepositoryAPI) -> ScaleService {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1", email: "scale@example.com", isActiveAccount: true
        )
        return ScaleService(
            accountService: account,
            apiRepository: remote,
            localRepository: MockScaleRepository()
        )
    }

    // MARK: - listPairedDevices

    @Test("listPairedDevices: delegates to repo and forwards server deviceType filter")
    func listPairedDevicesForwardsFilter() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.listPairedDevicesResult = [ScaleTestFixtures.makePairedDeviceResponse(id: "d1")]
        let sut = makeSUT(remote: remote)

        let result = try await sut.listPairedDevices(deviceType: .bpm)

        #expect(remote.listPairedDevicesCalls == 1)
        #expect(remote.lastListedDeviceTypeFilter == "bpm")
        #expect(result.count == 1)
        #expect(result[0].id == "d1")
    }

    @Test("listPairedDevices: nil filter passes nil to repo")
    func listPairedDevicesNilFilter() async throws {
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(remote: remote)

        _ = try await sut.listPairedDevices(deviceType: nil)

        #expect(remote.lastListedDeviceTypeFilter == nil)
    }

    @Test("listPairedDevices: propagates repo error")
    func listPairedDevicesError() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.listPairedDevicesError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: ScaleTestError.remoteFailure) {
            try await sut.listPairedDevices(deviceType: nil)
        }
    }

    // MARK: - createPairedDevice

    @Test("createPairedDevice: delegates request to repo and returns response")
    func createPairedDeviceDelegates() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.createPairedDeviceResult = ScaleTestFixtures.makePairedDeviceResponse(id: "created-1")
        let sut = makeSUT(remote: remote)
        let request = ScaleTestFixtures.makePairedDeviceRequest(deviceType: "bpm")

        let result = try await sut.createPairedDevice(request)

        #expect(remote.createPairedDeviceCalls == 1)
        #expect(remote.lastCreatedPairedDevice?.deviceType == "bpm")
        #expect(result.id == "created-1")
    }

    @Test("createPairedDevice: propagates repo error")
    func createPairedDeviceError() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.createPairedDeviceError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: ScaleTestError.remoteFailure) {
            try await sut.createPairedDevice(ScaleTestFixtures.makePairedDeviceRequest())
        }
    }

    // MARK: - updatePairedDevice

    @Test("updatePairedDevice: delegates id + nickname to repo")
    func updatePairedDeviceDelegates() async throws {
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(remote: remote)

        _ = try await sut.updatePairedDevice("u1", nickname: "Bedroom")

        #expect(remote.updatePairedDeviceCalls == 1)
        #expect(remote.lastUpdatedPairedDeviceId == "u1")
        #expect(remote.lastUpdatedPairedDevice?.nickname == "Bedroom")
    }

    @Test("updatePairedDevice: propagates repo error")
    func updatePairedDeviceError() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.updatePairedDeviceError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: ScaleTestError.remoteFailure) {
            try await sut.updatePairedDevice("u1", nickname: "x")
        }
    }

    // MARK: - deletePairedDevice

    @Test("deletePairedDevice: delegates id to repo")
    func deletePairedDeviceDelegates() async throws {
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(remote: remote)

        try await sut.deletePairedDevice("del-1")

        #expect(remote.deletePairedDeviceCalls == 1)
        #expect(remote.lastDeletedPairedDeviceId == "del-1")
    }

    @Test("deletePairedDevice: propagates repo error")
    func deletePairedDeviceError() async throws {
        let remote = MockScaleRepositoryAPI()
        remote.deletePairedDeviceError = ScaleTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: ScaleTestError.remoteFailure) {
            try await sut.deletePairedDevice("del-1")
        }
    }

    // MARK: - pairDevice (Device → PairedDeviceRequest mapping)

    @Test("pairDevice: maps Device fields into the unified request and submits via createPairedDevice")
    func pairDeviceMapsDevice() async throws {
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(remote: remote)
        let device = ScaleTestFixtures.makeDevice(id: "s1", mac: "AA:BB:CC:DD:EE:FF", sku: "0375")
        device.nickname = "Bathroom Scale"
        device.deviceName = "Smart Scale"

        _ = try await sut.pairDevice(device, deviceType: .scale)

        #expect(remote.createPairedDeviceCalls == 1)
        let request = remote.lastCreatedPairedDevice
        #expect(request?.deviceType == "weight_scale")
        // bathScale source type (btWifiR4) is used as the connection type
        #expect(request?.type == ScaleSourceType.btWifiR4.rawValue)
        #expect(request?.nickname == "Bathroom Scale")
        #expect(request?.sku == "0375")
        #expect(request?.mac == "AA:BB:CC:DD:EE:FF")
        #expect(request?.name == "Smart Scale")
    }

    @Test("pairDevice: bpm device maps to bpm server value")
    func pairDeviceBpm() async throws {
        let remote = MockScaleRepositoryAPI()
        let sut = makeSUT(remote: remote)
        let device = ScaleTestFixtures.makeDevice(id: "bpm-1")

        _ = try await sut.pairDevice(device, deviceType: .bpm)

        #expect(remote.lastCreatedPairedDevice?.deviceType == "bpm")
    }
}
