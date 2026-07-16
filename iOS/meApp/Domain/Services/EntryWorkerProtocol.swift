//
//  EntryWorkerProtocol.swift
//  meApp
//
//  Seam over SwiftDataWorker's batched entry persistence APIs (MOB-1433).
//  EntryService talks to the worker through this protocol so unit tests can
//  substitute a mock while production uses the @ModelActor implementation.
//

import Foundation

/// Outcome of merging a batch of remote operations into the local store.
/// Sendable so the @ModelActor worker can hand it back to the main actor.
/// The worker performs NO UI or integration side effects — the caller
/// (EntryService) emits publishers and forwards integrations from this result.
struct EntryMergeResult: Sendable, Equatable {
    let insertedCount: Int
    let updatedCount: Int
    let deletedCount: Int
    /// Remote create ops that produced a new local row — forwarded to health
    /// integrations by the caller (MA-3886 semantics preserved).
    let newlyCreatedOps: [BathScaleOperationDTO]
    /// One notification per removed local row (remote deletes + duplicate
    /// cleanup), extracted inside the worker before deletion. The caller emits
    /// `entryDeleted` and forwards integration deletes from these.
    let deletedNotifications: [EntryNotification]

    var hadNewCreates: Bool { !newlyCreatedOps.isEmpty }
    var hadChanges: Bool { insertedCount > 0 || updatedCount > 0 || deletedCount > 0 }

    static let empty = EntryMergeResult(
        insertedCount: 0,
        updatedCount: 0,
        deletedCount: 0,
        newlyCreatedOps: [],
        deletedNotifications: []
    )
}

/// Per-entry result of a remote push, applied to the local store in one batch
/// instead of one fetch+save round trip per entry.
struct EntryPushOutcome: Sendable, Equatable {
    enum Outcome: Sendable, Equatable {
        /// Create accepted by the server: mark synced and store the server-assigned id.
        case created(serverEntryId: String?, attempts: Int)
        /// Delete accepted by the server: remove the local row.
        case deleted
        /// Push failed: bump attempts; after the retry cap the entry is marked
        /// synced+failed so it stops retrying (existing semantics).
        case failed(attempts: Int, markAsFailed: Bool)
    }

    let entryId: UUID
    let outcome: Outcome
}

/// Batched, off-main entry persistence operations. Implemented by
/// `SwiftDataWorker` (@ModelActor); injected into `EntryService`.
protocol EntryWorkerProtocol: Sendable {
    /// Progress/streak source data (pre-existing worker API).
    func fetchProgressData(accountId: String) async throws -> ProgressFetchResult

    /// Merges a full batch of remote operations into the local store using one
    /// up-front identity fetch and chunked saves. Port of the former
    /// `EntryService.mergeRemoteOperations` with identical conflict semantics.
    func applyRemoteOperations(_ remoteOps: [BathScaleOperationDTO], accountId: String) async throws -> EntryMergeResult

    /// Batch-inserts rows (used by the SQLite migration). Returns the inserted count.
    func insertEntries(_ rows: [EntrySyncData]) async throws -> Int

    /// Applies per-entry push bookkeeping (synced/server-id/delete/failure) in one batch.
    func applyPushOutcomes(_ outcomes: [EntryPushOutcome]) async throws

    /// Fetches entries as UI-facing snapshots, newest first, extracted off the main actor.
    func fetchEntrySnapshots(accountId: String, operationType: String?) async throws -> [EntrySnapshot]

    /// Fetches entries as DTOs, newest first (pre-existing worker API).
    func fetchEntriesAsDTO(accountId: String, operationType: String) async throws -> [BathScaleOperationDTO]

    /// Counts all entries for an account (any operation type), off the main actor, so the
    /// COUNT no longer takes the SQLite store lock on the main thread and stall behind a
    /// concurrent cold-login merge on the same store (MOB-516).
    func fetchEntryCount(accountId: String) async throws -> Int

    /// Fetches all entries as lightweight `EntryData`, newest first, extracted off
    /// the main actor. Backs History's off-main month grouping (`getMonthsAll`) so the
    /// full-table read no longer blocks the main thread on large accounts (MOB-1433).
    func fetchAllEntryData(accountId: String, operationType: String) async throws -> [EntryData]
}
