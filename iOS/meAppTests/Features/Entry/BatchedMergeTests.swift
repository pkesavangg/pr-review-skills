import Foundation
@testable import meApp
import SwiftData
import Testing

/// Merge-parity suite for `SwiftDataWorker.applyRemoteOperations` (MOB-1433).
///
/// The batched merge replaced the per-row `EntryService.mergeRemoteOperations`;
/// these tests run the same scenarios the old merge handled against a real
/// in-memory container so the conflict semantics are verified end to end:
/// create / update / delete, serverEntryId vs legacy timestamp grouping,
/// ".000Z" normalization, duplicate cleanup, and chunked saves.
@Suite(.serialized)
@MainActor
struct BatchedMergeTests {

    private let accountId = "acct-1"

    // MARK: - Factory

    private func makeSUT() -> (worker: SwiftDataWorker, container: ModelContainer) {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        do {
            let container = try ModelContainer(
                for: Entry.self,
                BathScaleEntry.self,
                BathScaleMetric.self,
                configurations: config
            )
            return (SwiftDataWorker(modelContainer: container), container)
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }

    private func seed(_ container: ModelContainer, entries: [Entry]) throws {
        let context = ModelContext(container)
        for entry in entries {
            context.insert(entry)
        }
        try context.save()
    }

    private func fetchAll(_ container: ModelContainer) throws -> [Entry] {
        let context = ModelContext(container)
        return try context.fetch(FetchDescriptor<Entry>())
    }

    private func makeOp(
        serverEntryId: String? = nil,
        timestamp: String? = "2026-03-01T08:00:00Z",
        serverTimestamp: String? = "2026-03-01T09:00:00Z",
        operationType: String = "create",
        weight: Double? = 1800
    ) -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: accountId,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: timestamp,
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: operationType,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: serverTimestamp,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: weight,
            serverEntryId: serverEntryId
        )
    }

    // MARK: - Inserts

    @Test("remote create with no local row inserts a synced entry")
    func remoteCreateInserts() async throws {
        let (worker, container) = makeSUT()

        let result = try await worker.applyRemoteOperations(
            [makeOp(serverEntryId: "srv-1")], accountId: accountId
        )

        #expect(result.insertedCount == 1)
        #expect(result.hadNewCreates)
        #expect(result.newlyCreatedOps.count == 1)
        let all = try fetchAll(container)
        #expect(all.count == 1)
        #expect(all.first?.isSynced == true)
        #expect(all.first?.serverEntryId == "srv-1")
        #expect(all.first?.scaleEntry?.weight == 1800)
    }

    @Test("create then delete in one batch (same serverEntryId): nothing materializes")
    func createThenDeleteSameBatch() async throws {
        let (worker, container) = makeSUT()

        let result = try await worker.applyRemoteOperations(
            [
                makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-01T09:00:00Z"),
                makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-01T10:00:00Z", operationType: "delete")
            ],
            accountId: accountId
        )

        #expect(result.insertedCount == 0)
        #expect(!result.hadNewCreates)
        #expect(try fetchAll(container).isEmpty)
    }

    @Test("ops without serverEntryId and without timestamp are skipped")
    func emptyGroupKeySkipped() async throws {
        let (worker, container) = makeSUT()

        let result = try await worker.applyRemoteOperations(
            [makeOp(serverEntryId: nil, timestamp: nil)], accountId: accountId
        )

        #expect(result == EntryMergeResult.empty)
        #expect(try fetchAll(container).isEmpty)
    }

    // MARK: - Updates (serverEntryId identity)

    @Test("newer remote op updates scalar fields but preserves relationship data")
    func newerRemoteUpdatesScalars() async throws {
        let (worker, container) = makeSUT()
        let local = EntryTestFixtures.makeEntry(weight: 1800, serverTimestamp: "2026-03-01T09:00:00Z", isSynced: true)
        local.serverEntryId = "srv-1"
        try seed(container, entries: [local])

        let result = try await worker.applyRemoteOperations(
            [makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-02T09:00:00Z", weight: 9999)],
            accountId: accountId
        )

        #expect(result.updatedCount == 1)
        #expect(result.insertedCount == 0)
        let all = try fetchAll(container)
        #expect(all.count == 1)
        #expect(all.first?.serverTimestamp == "2026-03-02T09:00:00Z")
        #expect(all.first?.isSynced == true)
        // Parity with the old merge: EntryRepository.updateEntry copied scalar
        // fields only — the stored weight must NOT change.
        #expect(all.first?.scaleEntry?.weight == 1800)
    }

    @Test("older remote op does not overwrite, but marks an unsynced local row synced")
    func olderRemoteMarksSynced() async throws {
        let (worker, container) = makeSUT()
        let local = EntryTestFixtures.makeEntry(serverTimestamp: "2026-03-05T09:00:00Z", isSynced: false)
        local.serverEntryId = "srv-1"
        try seed(container, entries: [local])

        let result = try await worker.applyRemoteOperations(
            [makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-01T09:00:00Z")],
            accountId: accountId
        )

        #expect(result.updatedCount == 1)
        let all = try fetchAll(container)
        #expect(all.first?.isSynced == true)
        #expect(all.first?.serverTimestamp == "2026-03-05T09:00:00Z")
    }

    @Test("remote delete removes the row and reports a notification with extracted data")
    func remoteDeleteRemovesRow() async throws {
        let (worker, container) = makeSUT()
        let local = EntryTestFixtures.makeEntry(weight: 1750, serverTimestamp: "2026-03-01T09:00:00Z", isSynced: true)
        local.serverEntryId = "srv-1"
        try seed(container, entries: [local])

        let result = try await worker.applyRemoteOperations(
            [makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-02T09:00:00Z", operationType: "delete")],
            accountId: accountId
        )

        #expect(result.deletedCount == 1)
        #expect(result.deletedNotifications.count == 1)
        #expect(result.deletedNotifications.first?.weight == 1750)
        #expect(try fetchAll(container).isEmpty)
    }

    @Test("multiple ops in one group: last by serverTimestamp wins")
    func lastOpByServerTimestampWins() async throws {
        let (worker, container) = makeSUT()

        // delete is older than the create — the create must win.
        let result = try await worker.applyRemoteOperations(
            [
                makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-01T10:00:00Z"),
                makeOp(serverEntryId: "srv-1", serverTimestamp: "2026-03-01T09:00:00Z", operationType: "delete")
            ],
            accountId: accountId
        )

        #expect(result.insertedCount == 1)
        #expect(try fetchAll(container).count == 1)
    }

    // MARK: - Legacy timestamp identity

    @Test("legacy op matches local row by timestamp and updates it")
    func legacyTimestampMatchUpdates() async throws {
        let (worker, container) = makeSUT()
        let local = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", isSynced: false)
        try seed(container, entries: [local])

        let result = try await worker.applyRemoteOperations(
            [makeOp(timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "2026-03-01T09:00:00Z")],
            accountId: accountId
        )

        #expect(result.updatedCount == 1)
        #expect(result.insertedCount == 0)
        let all = try fetchAll(container)
        #expect(all.count == 1)
        #expect(all.first?.isSynced == true)
        #expect(all.first?.serverTimestamp == "2026-03-01T09:00:00Z")
    }

    @Test("legacy op with .000Z millisecond precision matches the normalized local timestamp")
    func legacyMillisecondNormalizationMatches() async throws {
        let (worker, container) = makeSUT()
        let local = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", isSynced: false)
        try seed(container, entries: [local])

        let result = try await worker.applyRemoteOperations(
            [makeOp(timestamp: "2026-03-01T08:00:00.000Z", serverTimestamp: "2026-03-01T09:00:00Z")],
            accountId: accountId
        )

        // Matched the normalized timestamp — updated, NOT duplicated.
        #expect(result.updatedCount == 1)
        #expect(result.insertedCount == 0)
        #expect(try fetchAll(container).count == 1)
    }

    @Test("legacy delete removes every local row sharing the timestamp, one notification")
    func legacyDeleteRemovesAllRowsForTimestamp() async throws {
        let (worker, container) = makeSUT()
        let first = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "a", isSynced: true)
        let second = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "a", isSynced: true)
        try seed(container, entries: [first, second])

        let result = try await worker.applyRemoteOperations(
            [makeOp(timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "b", operationType: "delete")],
            accountId: accountId
        )

        #expect(result.deletedCount == 2)
        #expect(result.deletedNotifications.count == 1)
        #expect(try fetchAll(container).isEmpty)
    }

    @Test("legacy update cleans up duplicate rows, keeping the create row")
    func legacyUpdateCleansUpDuplicates() async throws {
        let (worker, container) = makeSUT()
        let keep = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .create, isSynced: false)
        let duplicate = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .create, isSynced: false)
        try seed(container, entries: [keep, duplicate])

        let result = try await worker.applyRemoteOperations(
            [makeOp(timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "2026-03-01T09:00:00Z")],
            accountId: accountId
        )

        // One row updated from the remote op, the duplicate removed (with a
        // notification — previous cleanupDuplicates semantics).
        #expect(result.updatedCount == 1)
        #expect(result.deletedCount == 1)
        #expect(result.deletedNotifications.count == 1)
        let all = try fetchAll(container)
        #expect(all.count == 1)
        #expect(all.first?.isSynced == true)
    }

    @Test("legacy create with no local match inserts a new row")
    func legacyCreateInserts() async throws {
        let (worker, container) = makeSUT()

        let result = try await worker.applyRemoteOperations(
            [makeOp(timestamp: "2026-03-01T08:00:00Z")], accountId: accountId
        )

        #expect(result.insertedCount == 1)
        #expect(try fetchAll(container).count == 1)
    }

    // MARK: - Account isolation

    @Test("merge only touches the given account's rows")
    func mergeIsAccountScoped() async throws {
        let (worker, container) = makeSUT()
        let otherAccount = EntryTestFixtures.makeEntry(
            accountId: "acct-2", timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "a", isSynced: true
        )
        try seed(container, entries: [otherAccount])

        let result = try await worker.applyRemoteOperations(
            [makeOp(timestamp: "2026-03-01T08:00:00Z", serverTimestamp: "b", operationType: "delete")],
            accountId: accountId
        )

        // The other account's row with the same timestamp must be invisible.
        #expect(result.deletedCount == 0)
        #expect(try fetchAll(container).count == 1)
    }

    // MARK: - Chunked saves

    @Test("large batch spanning several save chunks persists every row")
    func largeBatchPersistsAllChunks() async throws {
        let (worker, container) = makeSUT()
        let ops = (0 ..< 1200).map { index in
            makeOp(
                serverEntryId: "srv-\(index)",
                timestamp: "2026-01-01T00:\(String(format: "%02d", index % 60)):00Z",
                serverTimestamp: "2026-03-01T09:00:00Z"
            )
        }

        let result = try await worker.applyRemoteOperations(ops, accountId: accountId)

        #expect(result.insertedCount == 1200)
        #expect(try fetchAll(container).count == 1200)
    }

    // MARK: - insertEntries (SQLite migration path)

    @Test("insertEntries persists rows with relationship data in chunks")
    func insertEntriesPersistsRows() async throws {
        let (worker, container) = makeSUT()
        let rows = (0 ..< 600).map { index in
            EntrySyncData(
                id: UUID(),
                accountId: accountId,
                entryTimestamp: "2026-01-01T00:00:\(String(format: "%02d", index % 60))Z",
                serverTimestamp: nil,
                operationType: "create",
                entryType: EntryType.scale.rawValue,
                isSynced: false,
                isFailedToSync: false,
                attempts: 0,
                scaleEntry: DeviceEntryData(weight: 1800, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, source: "migration"),
                scaleEntryMetric: nil,
                bpmSystolic: nil,
                bpmDiastolic: nil,
                bpmMeanArterial: nil,
                bpmPulse: nil,
                note: nil,
                babyEntryBabyId: nil,
                babyEntryLength: nil,
                babyEntryWeight: nil
            )
        }

        let inserted = try await worker.insertEntries(rows)

        #expect(inserted == 600)
        let all = try fetchAll(container)
        #expect(all.count == 600)
        #expect(all.first?.scaleEntry?.weight == 1800)
        #expect(all.first?.isSynced == false)
    }

    // MARK: - applyPushOutcomes

    @Test("applyPushOutcomes marks creates synced, stores server id, deletes, and records failures")
    func applyPushOutcomesAppliesBookkeeping() async throws {
        let (worker, container) = makeSUT()
        let created = EntryTestFixtures.makeEntry(isSynced: false)
        let deleted = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", operationType: .delete, isSynced: false)
        let failed = EntryTestFixtures.makeEntry(timestamp: "2026-03-03T08:00:00Z", isSynced: false)
        try seed(container, entries: [created, deleted, failed])

        try await worker.applyPushOutcomes([
            EntryPushOutcome(entryId: created.id, outcome: .created(serverEntryId: "srv-9", attempts: 0)),
            EntryPushOutcome(entryId: deleted.id, outcome: .deleted),
            EntryPushOutcome(entryId: failed.id, outcome: .failed(attempts: 3, markAsFailed: false))
        ])

        let all = try fetchAll(container)
        #expect(all.count == 2)
        let createdRow = all.first { $0.id == created.id }
        #expect(createdRow?.isSynced == true)
        #expect(createdRow?.serverEntryId == "srv-9")
        let failedRow = all.first { $0.id == failed.id }
        #expect(failedRow?.isSynced == false)
        #expect(failedRow?.attempts == 3)
    }

    // MARK: - fetchEntrySnapshots

    @Test("fetchEntrySnapshots returns newest-first snapshots for the account")
    func fetchEntrySnapshotsNewestFirst() async throws {
        let (worker, container) = makeSUT()
        let older = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1700)
        let newer = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1800)
        let otherAccount = EntryTestFixtures.makeEntry(accountId: "acct-2", timestamp: "2026-03-03T08:00:00Z")
        try seed(container, entries: [older, newer, otherAccount])

        let snapshots = try await worker.fetchEntrySnapshots(accountId: accountId, operationType: "create")

        #expect(snapshots.count == 2)
        #expect(snapshots.first?.entryTimestamp == "2026-03-02T08:00:00Z")
        #expect(snapshots.first?.scaleEntry?.weight == 1800)
    }
}
