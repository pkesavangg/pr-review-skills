import Combine
import Foundation
@testable import meApp
import Testing

@MainActor
extension EntryServiceExtendedTests {

    // MARK: - exportCSV

    @Test("exportCSV no active account: throws AccountError.noActiveAccount")
    func exportCSVNoActiveAccount() async {
        let sut = makeSUT(activeAccount: nil)

        do {
            try await sut.exportCSV()
            Issue.record("Expected throw")
        } catch {
            #expect(error is AccountError)
        }
    }

    @Test("exportCSV: forwards the device UTC offset to the export request")
    func exportCSVForwardsUtcOffset() async throws {
        let remote = MockEntryRepositoryAPI()
        let account = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1",
            email: "e@e.com",
            isActiveAccount: true,
            dashboardType: DashboardType.dashboard4.rawValue
        )
        let sut = makeSUT(remote: remote, activeAccount: account)

        try await sut.exportCSV(category: EntryCategory.bp.rawValue)
        #expect(remote.exportCsvCalls == 1)
        #expect(remote.lastExportCsvRequest?.category == EntryCategory.bp.rawValue)
        #expect(remote.lastExportCsvRequest?.utcOffset == DateTimeTools.getUTCOffset())
    }

    @Test("exportCSV remote failure: throws")
    func exportCSVRemoteFailure() async {
        let remote = MockEntryRepositoryAPI()
        remote.exportCsvError = EntryTestError.remoteFailure
        let account = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1",
            email: "e@e.com",
            isActiveAccount: true,
            dashboardType: DashboardType.dashboard12.rawValue
        )
        let sut = makeSUT(remote: remote, activeAccount: account)

        do {
            try await sut.exportCSV()
            Issue.record("Expected throw")
        } catch {
            #expect(error as? EntryTestError == .remoteFailure)
        }
    }

    // MARK: - syncAllEntriesWithRemote

    @Test("syncAllEntriesWithRemote: fetches remote ops and updates last sync time")
    func syncAllEntriesWithRemoteSuccess() async {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let syncStore = MockEntrySyncStore()
        let sut = makeSUT(repo: repo, remote: remote, syncStore: syncStore)

        await sut.syncAllEntriesWithRemote()
        #expect(remote.fetchEntriesCalls == 1)
        #expect(syncStore.setCalls >= 1)
        #expect(sut.lastSyncTime != nil)
        #expect(sut.isSyncing == false)
    }

    @Test("syncAllEntriesWithRemote no account: logs error and returns gracefully")
    func syncAllEntriesNoAccount() async {
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger, activeAccount: nil)
        sut.logger = logger

        await sut.syncAllEntriesWithRemote()
        #expect(logger.messages.contains { $0.contains("No account ID available") })
    }

    @Test("syncAllEntriesWithRemote pushes unsynced creates to remote")
    func syncAllEntriesPushesUnsynced() async {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", isSynced: false)
        repo.entries = [entry]
        let remote = MockEntryRepositoryAPI()
        let syncStore = MockEntrySyncStore()
        let sut = makeSUT(repo: repo, remote: remote, syncStore: syncStore)

        await sut.syncAllEntriesWithRemote()
        #expect(remote.submitEntriesCalls >= 1)
    }

    @Test("syncAllEntriesWithRemote remote fetch failure: logs error")
    func syncAllEntriesRemoteFetchFailure() async {
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesError = EntryTestError.remoteFailure
        let logger = MockLoggerService()
        let sut = makeSUT(remote: remote, logger: logger)

        await sut.syncAllEntriesWithRemote()
        #expect(logger.messages.contains { $0.contains("Full entry sync failed") })
    }

    // MARK: - remote merge (via syncAllEntriesWithRemote → worker)

    // Merge semantics (what a remote delete does to local rows) are covered by
    // BatchedMergeTests against the real SwiftDataWorker. Here we verify the
    // service-side orchestration: the fetched ops reach the worker, and the
    // worker's delete result drives the entryDeleted publisher + integration delete.
    @Test("sync routes remote ops to the worker and applies delete side effects")
    func syncMergesRemoteDelete() async {
        let deleteOp = BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "2026-03-01T08:00:00Z",
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "delete",
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: "b",
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: nil
        )
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesResult = BathScaleOperationListResponse(
            operations: [deleteOp],
            timestamp: "2026-03-01T10:00:00Z"
        )
        let worker = MockEntryWorker()
        worker.applyRemoteOperationsResult = EntryMergeResult(
            insertedCount: 0,
            updatedCount: 0,
            deletedCount: 1,
            newlyCreatedOps: [],
            deletedNotifications: [EntryNotification(from: deleteOp)]
        )
        let integration = MockIntegrationService()
        let sut = makeSUT(remote: remote, integration: integration, worker: worker)
        var deleted: [EntryNotification] = []
        let cancellable = sut.entryDeleted.sink { deleted.append($0) }

        await sut.syncAllEntriesWithRemote()

        #expect(worker.applyRemoteOperationsCalls == 1)
        #expect(worker.lastAppliedOperations.count == 1)
        #expect(worker.lastAppliedAccountId == "acct-1")
        #expect(deleted.count == 1)
        #expect(integration.deletedNotifications.count == 1)
        cancellable.cancel()
    }

    // MARK: - handleEntryDeleted integration delete failure

    @Test("handleEntryDeleted integration failure: logs but does not throw")
    func handleEntryDeletedIntegrationFailure() async throws {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        repo.entries = [entry]
        let integration = MockIntegrationService()
        integration.deleteEntryError = EntryTestError.remoteFailure
        let logger = MockLoggerService()
        let sut = makeSUT(repo: repo, integration: integration, logger: logger)

        try await sut.handleEntryDeleted(entry)
        #expect(logger.messages.contains { $0.contains("Failed to delete entry from integrations") })
    }

    // MARK: - handleEntryAdded edge cases

    @Test("handleEntryAdded: updates daily and monthly summaries and sends notification")
    func handleEntryAddedUpdatesSummaries() async throws {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        repo.entries = [entry]
        let sut = makeSUT(repo: repo)
        var notifications: [EntryNotification] = []
        let cancellable = sut.entrySaved.sink { notifications.append($0) }

        try await sut.handleEntryAdded(entry)
        #expect(sut.dailySummaries.count == 1)
        #expect(sut.monthlySummaries.count == 1)
        #expect(notifications.count == 1)
        cancellable.cancel()
    }

    // MARK: - handleEntryUpdated

    @Test("handleEntryUpdated: removes old and adds new summary")
    func handleEntryUpdatedRemovesAndAdds() async throws {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1900)
        repo.entries = [entry]
        let sut = makeSUT(repo: repo)
        sut.dailySummaries = [
            BathScaleWeightSummary(
                accountId: "acct-1",
                period: "2026-03-01",
                entryTimestamp: "2026-03-01T08:00:00Z",
                date: DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd"),
                count: 1,
                weight: 1800
            )
        ]

        try await sut.handleEntryUpdated(entry)
        #expect(sut.dailySummaries.first?.weight == 1900)
    }

    // MARK: - aggregateByDay edge cases

    @Test("aggregateByDay empty entries: returns empty")
    func aggregateByDayEmpty() {
        let sut = makeSUT()
        let result = sut.aggregateByDay(entries: [], accountId: "acct-1")
        #expect(result.isEmpty)
    }

    @Test("aggregateByDay all zero weight: returns empty")
    func aggregateByDayAllZeroWeight() {
        let sut = makeSUT()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 0)
        let result = sut.aggregateByDay(entries: [entry], accountId: "acct-1").compactMap { $0 }
        #expect(result.isEmpty)
    }

    @Test("aggregateByDay multiple days: sorted by period ascending")
    func aggregateByDayMultipleDays() {
        let sut = makeSUT()
        let day1 = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1800)
        let day2 = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1750)
        let result = sut.aggregateByDay(entries: [day1, day2], accountId: "acct-1").compactMap { $0 }
        #expect(result.count == 2)
        #expect(result.first?.period == "2026-03-01")
        #expect(result.last?.period == "2026-03-02")
    }

    // MARK: - aggregateByMonth edge cases

    @Test("aggregateByMonth empty: returns empty")
    func aggregateByMonthEmpty() {
        let sut = makeSUT()
        let result = sut.aggregateByMonth(entries: [], accountId: "acct-1")
        #expect(result.isEmpty)
    }

    @Test("aggregateByMonth with metrics: body metrics averaged excluding zeros")
    func aggregateByMonthMetricsAverage() {
        let sut = makeSUT()
        let e1 = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800, bodyFat: 200)
        let e2 = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1820, bodyFat: 0)
        let result = sut.aggregateByMonth(entries: [e1, e2], accountId: "acct-1").compactMap { $0 }
        #expect(result.count == 1)
        #expect(result.first?.bodyFat == 200)
    }

    // MARK: - loadDashboardData no account

    @Test("loadDashboardData no account: logs error")
    func loadDashboardDataNoAccount() async {
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger, activeAccount: nil)

        await sut.loadDashboardData()
        #expect(logger.messages.contains { $0.contains("loadDashboardData failed") })
    }

    @Test("loadDashboardData concurrent retry: piggybacked caller retries after failed load")
    func loadDashboardDataConcurrentRetryAfterFailure() async {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        repo.entries = [entry]
        repo.fetchEntriesAsDTODelayNanoseconds = 50_000_000
        repo.fetchEntriesAsDTOResults = [
            .failure(EntryTestError.localFailure),
            .success([entry.toOperationDTO()])
        ]
        let sut = makeSUT(repo: repo)

        await withTaskGroup(of: Void.self) { group in
            group.addTask {
                await sut.loadDashboardData()
            }
            group.addTask {
                await Task.yield()
                await sut.loadDashboardData()
            }
        }

        #expect(repo.fetchEntriesAsDTOCalls == 2)
        #expect(sut.dailySummaries.count == 1)
        #expect(sut.dailySummaries.first?.period == "2026-03-01")
    }

    // MARK: - sync push failure marks entry as failed after retries

    @Test("sync push remote failure: increments attempts on entry")
    func syncPushRemoteFailureIncrementsAttempts() async {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", isSynced: false)
        repo.entries = [entry]
        let remote = MockEntryRepositoryAPI()
        remote.submitEntriesError = EntryTestError.remoteFailure
        let syncStore = MockEntrySyncStore()
        let worker = MockEntryWorker()
        let sut = makeSUT(repo: repo, remote: remote, syncStore: syncStore, worker: worker)

        await sut.syncAllEntriesWithRemote()

        // A failed chunk records one more attempt for every entry in it.
        let failedOutcome = worker.appliedPushOutcomes.first
        #expect(failedOutcome?.entryId == entry.id)
        if case .failed(let attempts, let markAsFailed)? = failedOutcome?.outcome {
            #expect(attempts == 1)
            #expect(markAsFailed == false)
        } else {
            Issue.record("Expected a .failed push outcome, got \(String(describing: failedOutcome))")
        }
    }

    @Test("push batches all unsynced entries into one POST and maps server ids per entry")
    func pushBatchesEntriesIntoOnePost() async {
        let repo = MockEntryRepository()
        let first = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", isSynced: false)
        let second = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", isSynced: false)
        repo.entries = [first, second]
        let remote = MockEntryRepositoryAPI()
        remote.submitEntriesResult = UnifiedEntryResponse(
            entries: [
                EntryTestFixtures.makeUnifiedResult(entryId: "srv-1", timestamp: "2026-03-01T08:00:00Z"),
                EntryTestFixtures.makeUnifiedResult(entryId: "srv-2", timestamp: "2026-03-02T08:00:00Z")
            ],
            timestamp: "2026-03-02T09:00:00Z"
        )
        let worker = MockEntryWorker()
        let sut = makeSUT(repo: repo, remote: remote, worker: worker)

        await sut.syncAllEntriesWithRemote()

        // ONE POST for both entries (previously one round trip per entry).
        #expect(remote.submitEntriesCalls == 1)
        #expect((remote.lastSubmittedEntries ?? []).count == 2)
        // Server ids map back positionally onto the entries' outcomes.
        let serverIds: [String?] = worker.appliedPushOutcomes.compactMap {
            if case .created(let serverEntryId, _) = $0.outcome { return serverEntryId }
            return nil
        }
        #expect(Set(serverIds.compactMap { $0 }) == ["srv-1", "srv-2"])
    }

    @Test("push delete outcome removes the row via the worker and updates summaries")
    func pushDeleteRoutesThroughWorker() async {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .delete, isSynced: false)
        entry.serverEntryId = "srv-9"
        repo.entries = [entry]
        let remote = MockEntryRepositoryAPI()
        let worker = MockEntryWorker()
        let sut = makeSUT(repo: repo, remote: remote, worker: worker)
        var deleted: [EntryNotification] = []
        let cancellable = sut.entryDeleted.sink { deleted.append($0) }

        await sut.syncAllEntriesWithRemote()

        #expect(worker.appliedPushOutcomes.contains { $0.entryId == entry.id && $0.outcome == .deleted })
        #expect(deleted.count == 1)
        cancellable.cancel()
    }

    // MARK: - updateDailySummary / updateMonthlySummary

    @Test("daily summary insertion and removal")
    func dailySummaryInsertionAndRemoval() async throws {
        let repo = MockEntryRepository()
        let e1 = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        let e2 = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1850)
        repo.entries = [e1, e2]
        let sut = makeSUT(repo: repo)

        try await sut.handleEntryAdded(e1)
        #expect(sut.dailySummaries.count == 1)
        try await sut.handleEntryAdded(e2)
        #expect(sut.dailySummaries.count == 2)

        repo.entries = [e2]
        try await sut.handleEntryDeleted(e1)
        #expect(sut.dailySummaries.count == 1)
        #expect(sut.dailySummaries.first?.period == "2026-03-02")
    }

    @Test("monthly summary removal when last entry deleted")
    func monthlySummaryRemovalOnLastDelete() async throws {
        let repo = MockEntryRepository()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        repo.entries = [entry]
        let sut = makeSUT(repo: repo)
        try await sut.handleEntryAdded(entry)
        #expect(sut.monthlySummaries.count == 1)

        repo.entries = []
        try await sut.handleEntryDeleted(entry)
        #expect(sut.monthlySummaries.isEmpty)
    }

    // MARK: - getEntries(forDay:)

    @Test("getEntries forDay: filters create operations for given day")
    func getEntriesForDayFilters() async throws {
        let repo = MockEntryRepository()
        let create = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .create)
        let del = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T12:00:00Z", operationType: .delete)
        repo.entries = [create, del]
        let sut = makeSUT(repo: repo)

        let entries = try await sut.getEntries(forDay: "2026-03-01")
        #expect(entries.count == 1)
    }
}
