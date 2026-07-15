//
//  BabyServiceTests.swift
//  meAppTests
//
//  MOB-386: BabyService wires the remote Baby Profile API.
//  MOB-1527: BabyService is now offline-first — create/edit/delete write locally first and are
//  reconciled by `syncBabies(for:)` (client-id → server-id remap, lost-reply dedupe, cascade-safe
//  delete, merge guards). These tests drive connectivity deterministically via an injected gate.
//

import Combine
import Foundation
@testable import meApp
import SwiftData
import Testing

@Suite(.serialized)
@MainActor
struct BabyServiceTests {
    private let context = PersistenceController.shared.context

    /// Mutable connectivity flag captured by the injected `isConnected` closure.
    private final class NetworkGate {
        var connected: Bool
        init(_ connected: Bool) { self.connected = connected }
    }

    // MARK: - SUT

    // Test factory return; a labeled tuple is clearer than a one-off SUT struct.
    // swiftlint:disable:next large_tuple
    private struct SUT {
        let service: BabyService
        let repo: MockBabyRepositoryAPI
        let account: MockAccountService
        let kv: MockKvStorageService
        let entry: MockEntryService
        let gate: NetworkGate
        let accountId: String
    }

    private func makeSUT(productTypes: [String] = ["myWeight"], connected: Bool = false) -> SUT {
        let accountId = "test-account-\(UUID().uuidString)"
        let repo = MockBabyRepositoryAPI()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: accountId, email: "baby@example.com", isActiveAccount: true, productTypes: productTypes
        )
        let kv = MockKvStorageService()
        let entry = MockEntryService()
        let gate = NetworkGate(connected)

        TestDependencyContainer.reset()
        DependencyContainer.shared.register(account as AccountServiceProtocol)
        DependencyContainer.shared.register(entry as EntryServiceProtocol)

        let service = BabyService(
            remoteRepo: repo,
            kvStorage: kv,
            isConnected: { gate.connected },
            reconnectSignal: Empty(completeImmediately: false).eraseToAnyPublisher()
        )
        return SUT(service: service, repo: repo, account: account, kv: kv, entry: entry, gate: gate, accountId: accountId)
    }

    /// Purges every baby row (including tombstones) for the account from the shared context.
    private func cleanup(_ accountId: String) throws {
        let all = try context.fetch(FetchDescriptor<Baby>(predicate: #Predicate { $0.accountId == accountId }))
        all.forEach { context.delete($0) }
        try context.save()
    }

    private func makeBaby(on sut: SUT, name: String = "Lily", birthday: Date? = nil) async throws -> Baby {
        try await sut.service.saveBaby(
            name: name, accountId: sut.accountId, deviceId: nil,
            birthday: birthday, biologicalSex: nil,
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )
    }

    // MARK: - Offline create (AC: appears immediately, no error, productTypes reflects baby)

    @Test("saveBaby offline: persists unsynced, no network call, no throw, appears immediately")
    func saveBabyOfflineLocalFirst() async throws {
        let sut = makeSUT(connected: false)

        let baby = try await makeBaby(on: sut, name: "Offline Lily")

        #expect(sut.repo.createCalls == 0) // no network while offline
        #expect(baby.isSynced == false)
        #expect(baby.isServerCreated == false)
        #expect(sut.service.currentBabies.contains { $0.id == baby.id })
        // "baby" is attempted locally (best-effort) so productTypes reflects it.
        #expect(sut.account.lastUpdatedProductTypes?.contains("baby") == true)

        try cleanup(sut.accountId)
    }

    // MARK: - Sync / reconcile happy path (AC: POST on reconnect, server id replaces client id)

    @Test("syncBabies: POSTs an offline baby and remaps client id -> server id everywhere")
    func syncBabiesCreateHappyPath() async throws {
        let sut = makeSUT(connected: false)
        let baby = try await makeBaby(on: sut, name: "Emma")
        let clientId = baby.id
        sut.repo.createResult = BabyResponse(
            id: "srv-emma", name: "Emma", birthdate: nil, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )

        sut.gate.connected = true
        await sut.service.syncBabies(for: sut.accountId)

        #expect(sut.repo.createCalls == 1)
        let synced = try #require(sut.service.currentBabies.first { $0.id == "srv-emma" })
        #expect(synced.isSynced == true)
        #expect(synced.isServerCreated == true)
        #expect(!sut.service.currentBabies.contains { $0.id == clientId }) // client id gone, no duplicate
        // Baby entries were delegated for remap (keeps offline entries attached).
        #expect(sut.entry.remapBabyIdCalls == 1)
        #expect(sut.entry.lastRemapBabyIds?.old == clientId)
        #expect(sut.entry.lastRemapBabyIds?.new == "srv-emma")

        try cleanup(sut.accountId)
    }

    @Test("syncBabies: remaps the persisted active-baby selection pointer to the server id")
    func syncBabiesRemapsSelectionPointer() async throws {
        let sut = makeSUT(connected: false)
        let baby = try await makeBaby(on: sut, name: "Pointer")
        let clientId = baby.id
        // Simulate the active-baby selection pointing at the offline baby.
        sut.kv.setValue("baby_\(clientId)", forKey: KvStorageKeys.selectedProductTypeKey(for: sut.accountId))
        sut.repo.createResult = BabyResponse(
            id: "srv-ptr", name: "Pointer", birthdate: nil, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )

        sut.gate.connected = true
        await sut.service.syncBabies(for: sut.accountId)

        let stored = sut.kv.getValue(forKey: KvStorageKeys.selectedProductTypeKey(for: sut.accountId)) as? String
        #expect(stored == "baby_srv-ptr")

        try cleanup(sut.accountId)
    }

    // MARK: - Edit offline

    @Test("updateBabyProfile offline: marks unsynced without a network call; syncs on reconnect")
    func editOfflineThenSync() async throws {
        // Start with a server-created baby (as if already synced).
        let sut = makeSUT(connected: true)
        sut.repo.listResult = [BabyResponse(
            id: "srv-edit", name: "Old", birthdate: nil, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )]
        try await sut.service.loadBabies(for: sut.accountId)
        let baby = try #require(sut.service.currentBabies.first { $0.id == "srv-edit" })

        // Go offline and edit.
        sut.gate.connected = false
        try await sut.service.updateBabyProfile(
            baby, name: "New Name", birthday: nil, biologicalSex: "female",
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )
        #expect(sut.repo.updateCalls == 0)
        #expect(baby.isSynced == false)
        #expect(baby.name == "New Name")

        // Reconnect and sync → PUT to the server id.
        sut.gate.connected = true
        sut.repo.listResult = [BabyResponse(
            id: "srv-edit", name: "New Name", birthdate: nil, sex: "female",
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )]
        await sut.service.syncBabies(for: sut.accountId)

        #expect(sut.repo.updateCalls == 1)
        #expect(sut.repo.lastUpdateId == "srv-edit")
        #expect(sut.service.currentBabies.first { $0.id == "srv-edit" }?.isSynced == true)

        try cleanup(sut.accountId)
    }

    // MARK: - Delete offline

    @Test("deleteBaby offline (server baby): tombstones, hides from list, DELETEs on reconnect")
    func deleteOfflineThenSync() async throws {
        let sut = makeSUT(productTypes: ["myWeight", "baby"], connected: true)
        sut.repo.listResult = [BabyResponse(
            id: "srv-del", name: "Bye", birthdate: nil, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )]
        try await sut.service.loadBabies(for: sut.accountId)
        let baby = try #require(sut.service.currentBabies.first { $0.id == "srv-del" })

        sut.gate.connected = false
        try await sut.service.deleteBaby(baby)
        #expect(sut.repo.deleteCalls == 0)
        #expect(!sut.service.currentBabies.contains { $0.id == "srv-del" }) // hidden immediately

        sut.gate.connected = true
        sut.repo.listResult = [] // server now returns none
        await sut.service.syncBabies(for: sut.accountId)

        #expect(sut.repo.deleteCalls == 1)
        #expect(sut.repo.lastDeletedId == "srv-del")
        let remaining = try context.fetch(FetchDescriptor<Baby>(predicate: #Predicate { $0.id == "srv-del" }))
        #expect(remaining.isEmpty) // purged, no lingering tombstone

        try cleanup(sut.accountId)
    }

    @Test("create-then-delete offline: purged locally, never hits the server")
    func createThenDeleteOffline() async throws {
        let sut = makeSUT(connected: false)
        let baby = try await makeBaby(on: sut, name: "Transient")
        let id = baby.id

        try await sut.service.deleteBaby(baby)
        #expect(!sut.service.currentBabies.contains { $0.id == id })

        // Reconnecting must produce no create and no delete for a baby that never reached the server.
        sut.gate.connected = true
        await sut.service.syncBabies(for: sut.accountId)
        #expect(sut.repo.createCalls == 0)
        #expect(sut.repo.deleteCalls == 0)

        try cleanup(sut.accountId)
    }

    // MARK: - Refresh must not delete an unsynced offline baby

    @Test("syncBabies: a server refresh does NOT delete a not-yet-synced offline baby")
    func refreshKeepsUnsyncedOfflineBaby() async throws {
        let sut = makeSUT(connected: false)
        let baby = try await makeBaby(on: sut, name: "Survivor")
        let clientId = baby.id

        // Go online but make the POST fail (non-network) so the baby stays a pending local create,
        // and have the server list return NONE — the merge sweep must not wipe the local-only baby.
        sut.gate.connected = true
        sut.repo.createError = HTTPError.serverError
        sut.repo.listResult = []
        await sut.service.syncBabies(for: sut.accountId)

        #expect(sut.repo.createCalls == 1) // attempted
        #expect(sut.service.currentBabies.contains { $0.id == clientId }) // survived the merge
        #expect(sut.service.currentBabies.first { $0.id == clientId }?.isServerCreated == false)

        try cleanup(sut.accountId)
    }

    // MARK: - No-op when already synced

    @Test("syncBabies: a fully-synced baby triggers no create/update/delete")
    func syncNoOpWhenSynced() async throws {
        let sut = makeSUT(connected: true)
        sut.repo.listResult = [BabyResponse(
            id: "srv-stable", name: "Stable", birthdate: nil, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )]
        try await sut.service.loadBabies(for: sut.accountId)

        // Second sync with the same server state.
        await sut.service.syncBabies(for: sut.accountId)

        #expect(sut.repo.createCalls == 0)
        #expect(sut.repo.updateCalls == 0)
        #expect(sut.repo.deleteCalls == 0)
        #expect(sut.service.currentBabies.contains { $0.id == "srv-stable" })

        try cleanup(sut.accountId)
    }

    // MARK: - Lost-reply dedupe (AC: no duplicate baby)

    @Test("syncBabies: lost-reply adopts an existing server baby (name + birthdate) — no duplicate, no POST")
    func lostReplyDedupe() async throws {
        let sut = makeSUT(connected: false)
        let birthday = Date(timeIntervalSince1970: 1_700_000_000)
        let baby = try await makeBaby(on: sut, name: "Twin", birthday: birthday)
        let clientId = baby.id

        // Server already has this baby from a prior POST whose reply was lost.
        let serverBirthdate = DateTimeTools.formatter("yyyy-MM-dd").string(from: birthday)
        sut.gate.connected = true
        sut.repo.listResult = [BabyResponse(
            id: "srv-twin", name: "Twin", birthdate: serverBirthdate, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )]

        await sut.service.syncBabies(for: sut.accountId)

        #expect(sut.repo.createCalls == 0) // adopted, not re-POSTed
        #expect(sut.service.currentBabies.filter { $0.name == "Twin" }.count == 1) // no duplicate
        #expect(sut.service.currentBabies.contains { $0.id == "srv-twin" })
        #expect(!sut.service.currentBabies.contains { $0.id == clientId })
        #expect(sut.entry.lastRemapBabyIds?.new == "srv-twin")

        try cleanup(sut.accountId)
    }

    // MARK: - loadBabies merge

    @Test("loadBabies: merges the remote list into the local store")
    func loadBabiesMergesRemote() async throws {
        let sut = makeSUT(connected: true)
        sut.repo.listResult = [BabyResponse(
            id: "remote-1", name: "Remote Baby", birthdate: "2026-03-15", sex: "female",
            birthWeightDecigrams: 32500, birthLengthMillimeters: 510
        )]

        try await sut.service.loadBabies(for: sut.accountId)

        let merged = try #require(sut.service.currentBabies.first { $0.id == "remote-1" })
        #expect(merged.name == "Remote Baby")
        #expect(merged.biologicalSex == "female")
        #expect(merged.isSynced == true)
        #expect(merged.isServerCreated == true)

        try cleanup(sut.accountId)
    }

    @Test("loadBabies offline: falls back to the local cache without deleting anything")
    func loadBabiesOfflineFallsBack() async throws {
        let sut = makeSUT(connected: false)
        let baby = try await makeBaby(on: sut, name: "Cached")

        // Offline load must not throw and must keep the cached baby.
        try await sut.service.loadBabies(for: sut.accountId)
        #expect(sut.service.currentBabies.contains { $0.id == baby.id })

        try cleanup(sut.accountId)
    }

    // MARK: - productTypes append

    @Test("saveBaby: appends 'baby' to productTypes when absent")
    func saveBabyAppendsProductType() async throws {
        let sut = makeSUT(productTypes: ["myWeight"], connected: false)

        _ = try await makeBaby(on: sut, name: "Lily")

        #expect(sut.account.updateProductTypesCalls >= 1)
        #expect(sut.account.lastUpdatedProductTypes?.contains("baby") == true)

        try cleanup(sut.accountId)
    }
}
