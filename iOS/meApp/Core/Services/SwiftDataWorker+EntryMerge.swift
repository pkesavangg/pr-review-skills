//
//  SwiftDataWorker+EntryMerge.swift
//  meApp
//
//  Batched entry merge/write APIs on the @ModelActor (MOB-1433).
//  Ports EntryService.mergeRemoteOperations off the main actor with identical
//  conflict semantics but new mechanics: ONE up-front identity fetch feeding
//  in-memory maps replaces the per-row repository fetches, and saves commit in
//  chunks instead of once per row. The worker performs no UI or integration
//  side effects — those are applied by EntryService from the returned result.
//

import Foundation
import SwiftData

extension SwiftDataWorker: EntryWorkerProtocol {

    /// Mutations applied between context saves. One save per chunk keeps
    /// memory and disk-commit overhead bounded on 5k–10k row histories.
    private static let saveChunkSize = 500

    /// Mutable working state threaded through the merge helpers. Holds live
    /// `Entry` models — never leaves the actor.
    private struct MergeContext {
        /// Local rows keyed by server-assigned entry id (unique per entry).
        var byServerEntryId: [String: Entry]
        /// Local rows grouped by stored `entryTimestamp` (legacy identity).
        var byTimestamp: [String: [Entry]]
        var insertedCount = 0
        var updatedCount = 0
        var deletedCount = 0
        var newlyCreatedOps: [BathScaleOperationDTO] = []
        var deletedNotifications: [EntryNotification] = []
        var mutationsSinceSave = 0
    }

    // MARK: - EntryWorkerProtocol: merge

    /// Merge remote operations into the local store, resolving conflicts
    /// (latest wins by `serverTimestamp`). Same grouping and conflict rules as
    /// the former per-row merge: ops group by `serverEntryId` when present,
    /// falling back to `entryTimestamp` for legacy data.
    func applyRemoteOperations(_ remoteOps: [BathScaleOperationDTO], accountId: String) async throws -> EntryMergeResult {
        var context = try makeMergeContext(accountId: accountId)

        let groupedOps = Dictionary(grouping: remoteOps) { $0.serverEntryId ?? $0.entryTimestamp ?? "" }
        for (groupKey, ops) in groupedOps {
            guard !groupKey.isEmpty else { continue }

            // Latest operation per entry wins, in serverTimestamp order.
            let sortedOps = ops.sorted { ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "") }
            guard let finalOp = sortedOps.last else { continue }

            if finalOp.serverEntryId != nil {
                mergeServerIdentifiedGroup(finalOp: finalOp, accountId: accountId, context: &context)
            } else {
                mergeLegacyTimestampGroup(groupKey: groupKey, finalOp: finalOp, accountId: accountId, context: &context)
            }
            try saveChunkIfNeeded(&context)
        }
        try savePendingChanges(&context)

        return EntryMergeResult(
            insertedCount: context.insertedCount,
            updatedCount: context.updatedCount,
            deletedCount: context.deletedCount,
            newlyCreatedOps: context.newlyCreatedOps,
            deletedNotifications: context.deletedNotifications
        )
    }

    // MARK: - EntryWorkerProtocol: batch insert

    /// Batch-inserts rows in chunks with one save per chunk. Used by the
    /// SQLite migration instead of one main-actor save per row.
    func insertEntries(_ rows: [EntrySyncData]) async throws -> Int {
        var pending = 0
        for row in rows {
            modelContext.insert(Entry(from: row))
            pending += 1
            if pending >= Self.saveChunkSize {
                try saveOrRollback()
                pending = 0
            }
        }
        if pending > 0 {
            try saveOrRollback()
        }
        return rows.count
    }

    // MARK: - EntryWorkerProtocol: push bookkeeping

    /// Applies per-entry push results (mark-synced / server id / delete /
    /// failure bookkeeping) in one context with chunked saves.
    func applyPushOutcomes(_ outcomes: [EntryPushOutcome]) async throws {
        var pending = 0
        for pushOutcome in outcomes {
            let entryId = pushOutcome.entryId
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == entryId })
            guard let entry = try modelContext.fetch(descriptor).first else { continue }

            switch pushOutcome.outcome {
            case .created(let serverEntryId, let attempts):
                if let serverEntryId {
                    entry.serverEntryId = serverEntryId
                }
                entry.isSynced = true
                entry.isFailedToSync = false
                entry.attempts = attempts
            case .deleted:
                modelContext.delete(entry)
            case .failed(let attempts, let markAsFailed):
                entry.isSynced = markAsFailed
                entry.isFailedToSync = markAsFailed
                entry.attempts = attempts
            }

            pending += 1
            if pending >= Self.saveChunkSize {
                try saveOrRollback()
                pending = 0
            }
        }
        if pending > 0 {
            try saveOrRollback()
        }
    }

    // MARK: - EntryWorkerProtocol: reads

    /// Fetches entries as UI-facing snapshots, newest first. Snapshot
    /// extraction (including relationship faults) runs inside the actor.
    func fetchEntrySnapshots(accountId: String, operationType: String?) async throws -> [EntrySnapshot] {
        let descriptor: FetchDescriptor<Entry>
        if let operationType {
            descriptor = FetchDescriptor<Entry>(
                predicate: #Predicate { $0.accountId == accountId && $0.operationType == operationType },
                sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
            )
        } else {
            descriptor = FetchDescriptor<Entry>(
                predicate: #Predicate { $0.accountId == accountId },
                sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
            )
        }
        return try modelContext.fetch(descriptor).map { $0.toSnapshot() }
    }

    // MARK: - Merge helpers

    /// ONE identity fetch for the whole merge: loads the account's rows and
    /// indexes them by serverEntryId and by entryTimestamp.
    private func makeMergeContext(accountId: String) throws -> MergeContext {
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == accountId })
        let localEntries = try modelContext.fetch(descriptor)

        var byServerEntryId: [String: Entry] = [:]
        byServerEntryId.reserveCapacity(localEntries.count)
        var byTimestamp: [String: [Entry]] = [:]
        byTimestamp.reserveCapacity(localEntries.count)
        for entry in localEntries {
            if let serverEntryId = entry.serverEntryId {
                byServerEntryId[serverEntryId] = entry
            }
            byTimestamp[entry.entryTimestamp, default: []].append(entry)
        }
        return MergeContext(byServerEntryId: byServerEntryId, byTimestamp: byTimestamp)
    }

    /// Server-identified entries: match the local row by serverEntryId so that
    /// distinct entries sharing an entryTimestamp remain independent.
    private func mergeServerIdentifiedGroup(finalOp: BathScaleOperationDTO, accountId: String, context: inout MergeContext) {
        guard let serverEntryId = finalOp.serverEntryId else { return }
        guard let localEntry = context.byServerEntryId[serverEntryId] else {
            // No local entry for this server id — insert if the final op is a
            // create; a delete with no local row is nothing to do.
            if finalOp.operationType == OperationType.create.rawValue {
                insertRemoteCreate(finalOp, accountId: accountId, context: &context)
            }
            return
        }

        let localServerTS = localEntry.serverTimestamp ?? ""
        let remoteServerTS = finalOp.serverTimestamp ?? ""
        if localServerTS.isEmpty || remoteServerTS > localServerTS {
            if finalOp.operationType == OperationType.delete.rawValue {
                deleteRow(localEntry, notify: true, context: &context)
            } else {
                applyRemoteFields(finalOp, to: localEntry, accountId: accountId, context: &context)
            }
        } else if !localServerTS.isEmpty, !localEntry.isSynced {
            localEntry.isSynced = true
            context.updatedCount += 1
            context.mutationsSinceSave += 1
        }
    }

    /// Legacy entries (no serverEntryId): key on entryTimestamp, normalizing
    /// millisecond-precision variants (".000Z") exactly like the old merge.
    private func mergeLegacyTimestampGroup(
        groupKey timestamp: String,
        finalOp: BathScaleOperationDTO,
        accountId: String,
        context: inout MergeContext
    ) {
        let normalizedTimestamp = timestamp.replacingOccurrences(of: ".000Z", with: "Z")
        let tsCandidates = timestamp == normalizedTimestamp ? [timestamp] : [timestamp, normalizedTimestamp]

        var localGroup: [Entry]?
        for candidate in tsCandidates {
            if let rows = context.byTimestamp[candidate], !rows.isEmpty {
                localGroup = rows
                break
            }
        }

        guard let rows = localGroup,
              let localEntry = rows.first(where: { $0.operationType == OperationType.create.rawValue }) ?? rows.first else {
            // No local entry — only a final create materializes a new row.
            if finalOp.operationType == OperationType.create.rawValue {
                insertRemoteCreate(finalOp, accountId: accountId, context: &context)
            }
            return
        }

        let localServerTS = localEntry.serverTimestamp ?? ""
        let remoteServerTS = finalOp.serverTimestamp ?? ""
        if localServerTS.isEmpty || remoteServerTS > localServerTS {
            if finalOp.operationType == OperationType.delete.rawValue {
                // A remote delete removes every local row sharing the
                // timestamp; one notification for the primary row (as before).
                deleteRow(localEntry, notify: true, context: &context)
                for row in rows where row.id != localEntry.id {
                    deleteRow(row, notify: false, context: &context)
                }
            } else {
                removeDuplicates(rows, keeping: localEntry, context: &context)
                applyRemoteFields(finalOp, to: localEntry, accountId: accountId, context: &context)
            }
        } else if !localServerTS.isEmpty {
            if !localEntry.isSynced {
                localEntry.isSynced = true
                context.updatedCount += 1
                context.mutationsSinceSave += 1
            }
            removeDuplicates(rows, keeping: localEntry, context: &context)
        }
    }

    private func insertRemoteCreate(_ op: BathScaleOperationDTO, accountId: String, context: inout MergeContext) {
        let newEntry = Entry(from: op, accountId: accountId, isSynced: true)
        modelContext.insert(newEntry)
        if let serverEntryId = newEntry.serverEntryId {
            context.byServerEntryId[serverEntryId] = newEntry
        }
        context.byTimestamp[newEntry.entryTimestamp, default: []].append(newEntry)
        context.newlyCreatedOps.append(op)
        context.insertedCount += 1
        context.mutationsSinceSave += 1
    }

    /// Applies the remote operation's scalar fields to an existing row — the
    /// same field set the per-row merge updated via `EntryRepository.updateEntry`.
    /// Relationship values (weight/metrics) are intentionally left untouched,
    /// matching the previous behavior.
    private func applyRemoteFields(_ op: BathScaleOperationDTO, to entry: Entry, accountId: String, context: inout MergeContext) {
        let newTimestamp = op.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
        if newTimestamp != entry.entryTimestamp {
            context.byTimestamp[entry.entryTimestamp]?.removeAll { $0.id == entry.id }
            context.byTimestamp[newTimestamp, default: []].append(entry)
        }
        entry.accountId = accountId
        entry.entryTimestamp = newTimestamp
        entry.serverTimestamp = op.serverTimestamp
        entry.serverEntryId = op.serverEntryId ?? entry.serverEntryId
        entry.operationType = op.operationType ?? ""
        entry.entryType = op.entryType ?? EntryType.scale.rawValue
        entry.isSynced = true
        entry.isFailedToSync = false
        entry.attempts = 0
        context.updatedCount += 1
        context.mutationsSinceSave += 1
    }

    /// Duplicate cleanup: keeps the primary row, removes the rest, one
    /// notification per removed duplicate (previous `cleanupDuplicates` semantics).
    private func removeDuplicates(_ rows: [Entry], keeping keep: Entry, context: inout MergeContext) {
        guard rows.count > 1 else { return }
        for row in rows where row.id != keep.id {
            deleteRow(row, notify: true, context: &context)
        }
    }

    /// Deletes a row, keeping the in-memory indexes consistent so later groups
    /// observe earlier deletions (the old per-row merge re-fetched each time).
    private func deleteRow(_ entry: Entry, notify: Bool, context: inout MergeContext) {
        if notify {
            context.deletedNotifications.append(EntryNotification(extracting: entry))
        }
        if let serverEntryId = entry.serverEntryId {
            context.byServerEntryId.removeValue(forKey: serverEntryId)
        }
        context.byTimestamp[entry.entryTimestamp]?.removeAll { $0.id == entry.id }
        modelContext.delete(entry)
        context.deletedCount += 1
        context.mutationsSinceSave += 1
    }

    // MARK: - Save helpers

    private func saveChunkIfNeeded(_ context: inout MergeContext) throws {
        guard context.mutationsSinceSave >= Self.saveChunkSize else { return }
        try saveOrRollback()
        context.mutationsSinceSave = 0
    }

    private func savePendingChanges(_ context: inout MergeContext) throws {
        guard context.mutationsSinceSave > 0 else { return }
        try saveOrRollback()
        context.mutationsSinceSave = 0
    }

    private func saveOrRollback() throws {
        do {
            try modelContext.save()
        } catch {
            modelContext.rollback()
            throw error
        }
    }
}
