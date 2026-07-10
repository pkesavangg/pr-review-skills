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
        // The synced orphan is absent from the server list, so the replace/prune pass must
        // drop it from local storage and from the published snapshots.
        let remaining = try await repo.listScales()
        #expect(!remaining.contains { $0.id == "orphan" })
        #expect(!sut.scales.contains { $0.id == "orphan" })
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
