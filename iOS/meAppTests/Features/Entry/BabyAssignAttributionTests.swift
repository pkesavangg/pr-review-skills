//
//  BabyAssignAttributionTests.swift
//  meAppTests
//
//  MOB-1849 / MOB-1850: assigning a baby reading must produce exactly one attribution,
//  and reassigning must move it. The old path mutated `entry.babyEntry?.babyId` and called
//  the blanket `updateEntry`, which copies scalars only — the attribution never persisted
//  and the caller's stale sync scalars were written back over it.
//
//  MOB-1850 also has a server half: once a reading has synced it is never re-pushed, so an
//  in-place babyId rewrite left the server holding the old attribution and the reading showed
//  under BOTH children. A synced reassign therefore re-creates the reading under the new baby
//  and queues a delete for the synced row.
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

    private func makeBabyEntry(id: UUID = UUID(), babyId: String?, isSynced: Bool = true, source: String? = nil) -> Entry {
        let entry = Entry(
            entryTimestamp: "2026-07-27T09:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: isSynced
        )
        entry.id = id
        entry.serverEntryId = "server-1"
        entry.babyEntry = BabyEntry(babyId: babyId ?? "", length: 500, weight: 30000, source: source)
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

        // Hoisted out of #expect: the macro can't expand optional chaining inside a closure.
        let emmaRows = repo.entries.filter { $0.babyEntry?.babyId == "emma" }
        #expect(repo.entries.count == 1)
        #expect(emmaRows.count == 1)
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

    @Test("reassign before the reading has synced moves it in place — one row, new baby")
    func reassignUnsyncedMovesInPlace() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        repo.entries = [makeBabyEntry(id: entryId, babyId: "emma", isSynced: false)]
        let sut = makeSUT(repo: repo)

        let assignedId = try await sut.assignBabyEntry(entryId: entryId, babyId: "princy")

        // Nothing reached the server yet, so re-attributing the row is enough.
        #expect(assignedId == entryId)
        #expect(repo.entries.count == 1)
        #expect(repo.entries.first?.babyEntry?.babyId == "princy")
        #expect(repo.updateEntryBabyIdCalls == 1)
    }

    @Test("reassign after the reading has synced re-creates it and queues a delete for the old baby")
    func reassignSyncedMovesViaCreateAndDelete() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        repo.entries = [makeBabyEntry(id: entryId, babyId: "emma", isSynced: true, source: "0220")]
        let sut = makeSUT(repo: repo)

        let assignedId = try await sut.assignBabyEntry(entryId: entryId, babyId: "princy")

        // The reading lives on a fresh unsynced row so the push carries a create for Princy.
        #expect(assignedId != entryId)
        let live = repo.entries.filter { $0.operationType == OperationType.create.rawValue }
        #expect(live.count == 1)
        #expect(live.first?.id == assignedId)
        #expect(live.first?.babyEntry?.babyId == "princy")
        #expect(live.first?.isSynced == false)
        // The scale SKU survives the move — a device reading must not become a manual one.
        #expect(live.first?.babyEntry?.source == "0220")

        // Emma's synced row is queued as a delete so the server drops its copy. Without it the
        // server kept the old attribution and the reading showed under BOTH children (MOB-1850).
        let original = repo.entries.first { $0.id == entryId }
        #expect(original?.operationType == OperationType.delete.rawValue)
        #expect(original?.isSynced == false)
        #expect(original?.babyEntry?.babyId == "emma")

        // Exactly one baby holds a live copy of the reading.
        let liveEmmaRows = live.filter { $0.babyEntry?.babyId == "emma" }
        #expect(liveEmmaRows.isEmpty)
    }

    @Test("confirming the baby a reading already carries re-attributes in place, never re-creates")
    func repeatAssignOfSameBabyStaysInPlace() async throws {
        let repo = MockEntryRepository()
        let entryId = UUID()
        // A scale linked to a profile pre-sets babyId, so SAVE re-assigns the same baby.
        repo.entries = [makeBabyEntry(id: entryId, babyId: "emma", isSynced: true)]
        let sut = makeSUT(repo: repo)

        let assignedId = try await sut.assignBabyEntry(entryId: entryId, babyId: "emma")

        // No re-create and no delete — the reading must not be duplicated or dropped.
        #expect(assignedId == entryId)
        #expect(repo.entries.count == 1)
        #expect(repo.entries.first?.operationType == OperationType.create.rawValue)
        // Still goes through the in-place updater so entrySaved fires and History refreshes
        // (MOB-1842); the sync scalars stay put so an already-pushed row isn't re-created.
        #expect(repo.updateEntryBabyIdCalls == 1)
        #expect(repo.updateEntryCalls == 0)
        #expect(repo.entries.first?.isSynced == true)
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
