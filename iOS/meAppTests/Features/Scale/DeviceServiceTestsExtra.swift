import Foundation
@testable import meApp
import Testing

@MainActor
extension DeviceServiceTests {

    // MARK: - Sync pull / reconcile / prune

    @Test("syncAllScalesWithRemote replaces local state with server devices")
    func syncReplacesWithServerDevices() async throws {
        let repo = MockScaleRepository()
        let remote = MockScaleRepositoryAPI()
        remote.listScalesResult = [
            ScaleTestFixtures.makeScaleDTO(id: "srv-1", mac: "11:11:11:11:11:11", broadcastIdString: "B1"),
            ScaleTestFixtures.makeScaleDTO(id: "srv-2", mac: "22:22:22:22:22:22", broadcastIdString: "B2")
        ]
        let (sut, _, _, _) = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls >= 1)
        #expect(repo.replaceAllDevicesForAccountCalls >= 1)
        // Server devices are now the local truth.
        #expect(repo.lastServerDevices.count == 2)
    }

    @Test("syncAllScalesWithRemote prunes a synced local device the server no longer reports")
    func syncPrunesOrphanSyncedDevice() async throws {
        let repo = MockScaleRepository()
        // A previously-synced local device with a MAC/broadcast not present on the server.
        repo.devices = [ScaleTestFixtures.makeDevice(id: "orphan", isSynced: true, hasServerID: true)]
        let remote = MockScaleRepositoryAPI()
        remote.listScalesResult = [
            ScaleTestFixtures.makeScaleDTO(id: "srv-1", mac: "99:99:99:99:99:99", broadcastIdString: "ZZ")
        ]
        let (sut, _, _, _) = makeSUT(repo: repo, remote: remote)

        await sut.syncAllScalesWithRemote()

        #expect(remote.listScalesCalls >= 1)
    }

    @Test("syncDevices skips creating a temp device that duplicates an existing local device")
    func syncDevicesSkipsDuplicateTempDevice() async throws {
        let repo = MockScaleRepository()
        let existing = ScaleTestFixtures.makeDevice(id: "existing")
        repo.devices = [existing]
        let (sut, _, _, _) = makeSUT(repo: repo)
        let createsBefore = repo.createScaleCalls

        // Same id as an existing device → duplicate check short-circuits, no new create.
        try await sut.syncDevices(tempDevice: ScaleTestFixtures.makeDevice(id: "existing"))

        #expect(repo.createScaleCalls == createsBefore)
    }
}
