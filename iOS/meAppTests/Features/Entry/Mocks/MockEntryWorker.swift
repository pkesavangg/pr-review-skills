import Foundation
@testable import meApp

enum MockEntryWorkerError: Error, Equatable {
    case insertFailed
}

/// Records `EntryWorkerProtocol` calls and returns canned results so
/// EntryService orchestration tests never touch a real SwiftData container.
/// Merge semantics themselves are covered by `BatchedMergeTests`, which runs
/// the real `SwiftDataWorker` against an in-memory container.
///
/// For the read paths (`fetchEntriesAsDTO` / `fetchEntrySnapshots` /
/// `fetchProgressData`), set `backingRepo` so reads stay consistent with the
/// same `MockEntryRepository` the SUT writes to — in production both hit the
/// one SwiftData container, so tests that seed `repo.entries` and then read
/// back through the worker keep working. `@MainActor` keeps all `@Model`
/// access on the main actor (the mock repo is main-actor-bound).
@MainActor
final class MockEntryWorker: EntryWorkerProtocol {

    /// When set, read methods project this repo's entries instead of returning
    /// the canned results below.
    var backingRepo: MockEntryRepository?

    // MARK: - applyRemoteOperations

    var applyRemoteOperationsResult = EntryMergeResult.empty
    var applyRemoteOperationsError: Error?
    private(set) var applyRemoteOperationsCalls = 0
    private(set) var lastAppliedOperations: [BathScaleOperationDTO] = []
    private(set) var lastAppliedAccountId: String?

    func applyRemoteOperations(_ remoteOps: [BathScaleOperationDTO], accountId: String) async throws -> EntryMergeResult {
        applyRemoteOperationsCalls += 1
        lastAppliedOperations = remoteOps
        lastAppliedAccountId = accountId
        if let applyRemoteOperationsError { throw applyRemoteOperationsError }
        return applyRemoteOperationsResult
    }

    // MARK: - insertEntries

    var insertEntriesError: Error?
    /// Account ids whose batch should fail — lets tests exercise the
    /// "one user's migration fails, others continue" path.
    var insertEntriesFailAccountIds: Set<String> = []
    private(set) var insertedRows: [EntrySyncData] = []

    func insertEntries(_ rows: [EntrySyncData]) async throws -> Int {
        if let insertEntriesError { throw insertEntriesError }
        if let accountId = rows.first?.accountId, insertEntriesFailAccountIds.contains(accountId) {
            throw MockEntryWorkerError.insertFailed
        }
        insertedRows.append(contentsOf: rows)
        return rows.count
    }

    // MARK: - applyPushOutcomes

    var applyPushOutcomesError: Error?
    private(set) var appliedPushOutcomes: [EntryPushOutcome] = []

    func applyPushOutcomes(_ outcomes: [EntryPushOutcome]) async throws {
        if let applyPushOutcomesError { throw applyPushOutcomesError }
        appliedPushOutcomes.append(contentsOf: outcomes)
    }

    // MARK: - fetchProgressData

    var fetchProgressDataResult = ProgressFetchResult(
        latestEntry: nil,
        weekEntries: [],
        monthEntries: [],
        allEntries: [],
        totalCount: 0
    )
    var fetchProgressDataError: Error?
    private(set) var fetchProgressDataCalls = 0

    func fetchProgressData(accountId: String) async throws -> ProgressFetchResult {
        fetchProgressDataCalls += 1
        if let fetchProgressDataError { throw fetchProgressDataError }
        return fetchProgressDataResult
    }

    // MARK: - reads

    var entrySnapshotsResult: [EntrySnapshot] = []
    var entrySnapshotsError: Error?
    private(set) var fetchEntrySnapshotsCalls = 0

    func fetchEntrySnapshots(accountId: String, operationType: String?) async throws -> [EntrySnapshot] {
        fetchEntrySnapshotsCalls += 1
        if let entrySnapshotsError { throw entrySnapshotsError }
        if let backingRepo {
            let entries = (try? await backingRepo.fetchEntries(forUserId: accountId, operationType: operationType)) ?? []
            return entries.map { $0.toSnapshot() }
        }
        return entrySnapshotsResult
    }

    var entriesAsDTOResult: [BathScaleOperationDTO] = []
    var entriesAsDTOError: Error?
    private(set) var fetchEntriesAsDTOCalls = 0

    func fetchEntriesAsDTO(accountId: String, operationType: String) async throws -> [BathScaleOperationDTO] {
        fetchEntriesAsDTOCalls += 1
        if let entriesAsDTOError { throw entriesAsDTOError }
        if let backingRepo {
            return (try? await backingRepo.fetchEntriesAsDTO(forUserId: accountId, operationType: operationType)) ?? []
        }
        return entriesAsDTOResult
    }
}
