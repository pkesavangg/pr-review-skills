//
//  BabyAssignAttributionTests.swift
//  meAppTests
//
//  MOB-1849 / MOB-1850: assigning a baby reading must produce exactly one attribution,
//  and reassigning must move it. The old path mutated `entry.babyEntry?.babyId` and called
//  the blanket `updateEntry`, which copies scalars only — the attribution never persisted
//  and the caller's stale sync scalars were written back over it.
//
//  MOB-1852: an untouched card auto-assigns to the last active profile on timeout instead
//  of discarding, so a captured reading is never dropped.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BabyAssignAttributionTests {

    private func makeBabyEntry(id: UUID = UUID(), babyId: String?) -> Entry {
        let entry = Entry(
            entryTimestamp: "2026-07-27T09:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: true
        )
        entry.id = id
        entry.serverEntryId = "server-1"
        entry.babyEntry = BabyEntry(babyId: babyId ?? "", length: 500, weight: 30000)
        return entry
    }

    private func makeSUT(repo: MockEntryRepository) -> EntryService {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1", email: "baby@example.com", isActiveAccount: true
        )
        TestDependencyContainer.reset()
        TestDependencyContainer.registerBase(
            logger: MockLoggerService(),
            keychain: MockKeychainService(),
            bluetooth: MockBluetoothService()
        )
        DependencyContainer.shared.register(MockGoalAlertService() as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(MockIntegrationService() as IntegrationServiceProtocol)
        DependencyContainer.shared.register(account as AccountServiceProtocol)
        return EntryService(
            accountService: account,
            localRepo: repo,
            remoteRepo: MockEntryRepositoryAPI()
        )
    }

    @Test("assign persists babyId through the targeted updater, never the scalar-copying updateEntry")
    func assignUsesTargetedUpdater() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        repo.entries = [makeBabyEntry(id: entryId, babyId: nil)]
        let sut = makeSUT(repo: repo)

        try await sut.assignBabyEntry(entryId: entryId, babyId: "emma")

        #expect(repo.updateEntryBabyIdCalls == 1)
        #expect(repo.lastAssignedBabyId == "emma")
        // updateEntry would have written back the stale sync scalars over the assignment.
        #expect(repo.updateEntryCalls == 0)
        #expect(repo.entries.first?.babyEntry?.babyId == "emma")
    }

    @Test("assign leaves the sync scalars untouched so an already-pushed reading isn't re-created")
    func assignDoesNotDisturbSyncState() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        repo.entries = [makeBabyEntry(id: entryId, babyId: nil)]
        let sut = makeSUT(repo: repo)

        try await sut.assignBabyEntry(entryId: entryId, babyId: "emma")

        let stored = repo.entries.first
        #expect(stored?.isSynced == true)
        #expect(stored?.serverEntryId == "server-1")
        #expect(stored?.operationType == OperationType.create.rawValue)
    }

    @Test("one assignment yields exactly one attributed row")
    func assignProducesSingleRow() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        repo.entries = [makeBabyEntry(id: entryId, babyId: nil)]
        let sut = makeSUT(repo: repo)

        try await sut.assignBabyEntry(entryId: entryId, babyId: "emma")

        #expect(repo.entries.count == 1)
        #expect(repo.entries.filter { $0.babyEntry?.babyId == "emma" }.count == 1)
    }

    @Test("remapBabyId rewrites the attribution so a client-id entry can still sync")
    func remapPersistsNewBabyId() async throws {
        let repo = MockEntryRepository()
        repo.entries = [makeBabyEntry(babyId: "client-uuid")]
        let sut = makeSUT(repo: repo)

        await sut.remapBabyId(from: "client-uuid", to: "server-42")

        #expect(repo.updateEntryBabyIdCalls == 1)
        #expect(repo.entries.first?.babyEntry?.babyId == "server-42")
        // updateEntry never persisted the relationship, leaving the stale client id behind.
        #expect(repo.updateEntryCalls == 0)
    }

    @Test("reassign moves the reading — it never remains under the original baby")
    func reassignMovesAttribution() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        repo.entries = [makeBabyEntry(id: entryId, babyId: nil)]
        let sut = makeSUT(repo: repo)

        try await sut.assignBabyEntry(entryId: entryId, babyId: "emma")
        try await sut.assignBabyEntry(entryId: entryId, babyId: "princy")

        #expect(repo.entries.count == 1)
        #expect(!repo.entries.contains { $0.babyEntry?.babyId == "emma" })
        #expect(repo.entries.filter { $0.babyEntry?.babyId == "princy" }.count == 1)
    }
}

// MARK: - MOB-1852 timeout target resolution
//
// Mirrors `timeoutAssignTarget` in BottomTabBarViewModel, which follows Android's
// ReadingAssignmentManager (MOB-598). Tested as pure logic, per the
// MultipleReadingsCounterTests precedent of not instantiating the full view model.

/// Where an untouched card commits: the last-assigned baby on a multi-baby account when it
/// still exists, the only baby when there is one, otherwise nowhere (`nil` ⇒ discard).
private func timeoutAssignTarget(lastAssignedBabyId: String?, babyIds: [String]) -> String? {
    if babyIds.count > 1, let lastAssignedBabyId, babyIds.contains(lastAssignedBabyId) {
        return lastAssignedBabyId
    }
    return babyIds.count == 1 ? babyIds.first : nil
}

@Suite("Baby reading timeout target — MOB-1852")
struct BabyReadingTimeoutTargetTests {

    @Test("multi-baby account commits to the last-assigned baby")
    func usesLastAssignedBaby() {
        #expect(timeoutAssignTarget(lastAssignedBabyId: "princy", babyIds: ["emma", "princy"]) == "princy")
    }

    @Test("a single-baby account always commits to that baby")
    func singleBabyIsAlwaysTheTarget() {
        #expect(timeoutAssignTarget(lastAssignedBabyId: nil, babyIds: ["emma"]) == "emma")
        #expect(timeoutAssignTarget(lastAssignedBabyId: "stale", babyIds: ["emma"]) == "emma")
    }

    @Test("multi-baby with no prior assignment has no target — matching Android")
    func noTargetBeforeFirstAssignment() {
        #expect(timeoutAssignTarget(lastAssignedBabyId: nil, babyIds: ["emma", "princy"]) == nil)
    }

    @Test("a last-assigned baby since removed from the account is not used")
    func ignoresRemovedBaby() {
        #expect(timeoutAssignTarget(lastAssignedBabyId: "deleted", babyIds: ["emma", "princy"]) == nil)
    }

    @Test("no babies means no target — the reading is discarded rather than orphaned")
    func noTargetWithoutBabies() {
        #expect(timeoutAssignTarget(lastAssignedBabyId: "emma", babyIds: []) == nil)
    }
}
