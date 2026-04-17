import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct EntryServiceExtendedTests {

    // MARK: - clearAllData

    @Test("clearAllData success: deletes all entries from local repo")
    func clearAllDataSuccess() async {
        let repo = MockEntryRepository()
        repo.entries = [EntryTestFixtures.makeEntry(), EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z")]
        let sut = makeSUT(repo: repo)

        await sut.clearAllData()
        #expect(repo.entries.isEmpty)
    }

    @Test("clearAllData failure: logs error but does not throw")
    func clearAllDataFailure() async {
        let repo = MockEntryRepository()
        repo.deleteAllEntriesError = EntryTestError.localFailure
        let logger = MockLoggerService()
        let sut = makeSUT(repo: repo, logger: logger)

        await sut.clearAllData()
        #expect(logger.messages.contains { $0.contains("Failed to clear local entry data") })
    }

    // MARK: - clearLastSyncTimestamp

    @Test("clearLastSyncTimestamp: clears KV for active account")
    func clearLastSyncTimestampSuccess() async throws {
        let syncStore = MockEntrySyncStore()
        syncStore.timestamps["acct-1"] = "2026-03-01T00:00:00Z"
        let sut = makeSUT(syncStore: syncStore)

        try await sut.clearLastSyncTimestamp()
        #expect(syncStore.clearedAccountIds.contains("acct-1"))
        #expect(syncStore.timestamps["acct-1"] == nil)
    }

    @Test("clearLastSyncTimestamp no account: throws")
    func clearLastSyncTimestampNoAccount() async {
        let sut = makeSUT(activeAccount: nil)
        do {
            try await sut.clearLastSyncTimestamp()
            Issue.record("Expected throw")
        } catch {
            let nsError = error as NSError
            #expect(nsError.domain == "EntryService")
        }
    }

    // MARK: - saveNewEntries

    @Test("saveNewEntries success: saves all entries and syncs")
    func saveNewEntriesSuccess() async throws {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let sut = makeSUT(repo: repo, remote: remote)
        let entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z"),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z")
        ]

        try await sut.saveNewEntries(entries)
        #expect(repo.saveEntryCalls == 2)
        #expect(repo.entries.count == 2)
    }

    @Test("saveNewEntries failure: throws on first local failure")
    func saveNewEntriesFailure() async {
        let repo = MockEntryRepository()
        repo.saveEntryError = EntryTestError.localFailure
        let sut = makeSUT(repo: repo)

        do {
            try await sut.saveNewEntries([EntryTestFixtures.makeEntry()])
            Issue.record("Expected throw")
        } catch {
            #expect(error as? EntryTestError == .localFailure)
        }
    }

    // MARK: - deleteEntry failure

    @Test("deleteEntry local failure: throws and logs error")
    func deleteEntryLocalFailure() async {
        let repo = MockEntryRepository()
        repo.updateEntryError = EntryTestError.localFailure
        let logger = MockLoggerService()
        let entry = EntryTestFixtures.makeEntry()
        repo.entries = [entry]
        let sut = makeSUT(repo: repo, logger: logger)

        do {
            try await sut.deleteEntry(entry)
            Issue.record("Expected throw")
        } catch {
            #expect(error as? EntryTestError == .localFailure)
        }
        #expect(logger.messages.contains { $0.contains("Entry delete failed") })
    }

    // MARK: - getEntries(forMonth:)

    @Test("getEntries forMonth: filters to create operations only")
    func getEntriesForMonthFiltersCreates() async throws {
        let repo = MockEntryRepository()
        let create = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .create)
        let delete = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", operationType: .delete)
        repo.entries = [create, delete]
        let sut = makeSUT(repo: repo)

        let results = try await sut.getEntries(forMonth: "2026-03")
        #expect(results.count == 1)
        #expect(results.first?.operationType == OperationType.create.rawValue)
    }

    // MARK: - getEntries(lastNDays:)

    @Test("getEntries lastNDays: returns entries within window")
    func getEntriesLastNDaysFilters() async throws {
        let repo = MockEntryRepository()
        let recent = EntryTestFixtures.makeEntry(timestamp: ISO8601DateFormatter().string(from: Date()))
        repo.entries = [recent]
        let sut = makeSUT(repo: repo)

        let results = try await sut.getEntries(lastNDays: 7)
        #expect(results.count == 1)
    }

    @Test("getEntries lastNDays: filters BPM entries by entry type")
    func getEntriesLastNDaysFiltersBpmByEntryType() async throws {
        let repo = MockEntryRepository()
        let recentWeight = EntryTestFixtures.makeEntry(timestamp: ISO8601DateFormatter().string(from: Date()))
        let recentBpm = EntryTestFixtures.makeBpmEntry(timestamp: ISO8601DateFormatter().string(from: Date()))
        repo.entries = [recentWeight, recentBpm]
        let sut = makeSUT(repo: repo)

        let results = try await sut.getEntries(lastNDays: 7, entryType: .bpm)
        #expect(results.count == 1)
        #expect(results.first?.entryType == EntryType.bpm.rawValue)
    }

    // MARK: - getMonthsAll

    @Test("getMonthsAll: groups by month, sorts descending, computes stats")
    func getMonthsAllGroupsAndSorts() async throws {
        let repo = MockEntryRepository()
        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-15T08:00:00Z", weight: 1820),
            EntryTestFixtures.makeEntry(timestamp: "2026-02-01T08:00:00Z", weight: 1750)
        ]
        let sut = makeSUT(repo: repo)

        let months = try await sut.getMonthsAll()
        #expect(months.count == 2)
        #expect(months.first?.id == "2026-03")
        #expect(months.last?.id == "2026-02")
        #expect(months.first?.count == 2)
        #expect(months.first?.min == 1800)
        #expect(months.first?.max == 1820)
    }

    @Test("getMonthsAll no active account: throws")
    func getMonthsAllNoAccount() async {
        let sut = makeSUT(activeAccount: nil)
        do {
            _ = try await sut.getMonthsAll()
            Issue.record("Expected throw")
        } catch {
            let nsError = error as NSError
            #expect(nsError.domain == "EntryService")
        }
    }

    @Test("getMonthsAll empty entries: returns empty")
    func getMonthsAllEmpty() async throws {
        let sut = makeSUT()
        let months = try await sut.getMonthsAll()
        #expect(months.isEmpty)
    }

    // MARK: - getMonthDetail

    @Test("getMonthDetail: returns only creates for month")
    func getMonthDetailFiltersCreates() async throws {
        let repo = MockEntryRepository()
        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .create),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", operationType: .delete)
        ]
        let sut = makeSUT(repo: repo)

        let entries = try await sut.getMonthDetail(month: "2026-03")
        #expect(entries.count == 1)
    }

    @Test("getMonthDetail: treats empty entryType as weight and excludes it from BPM")
    func getMonthDetailHandlesLegacyEntryTypes() async throws {
        let repo = MockEntryRepository()
        let legacyWeightEntry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")
        legacyWeightEntry.entryType = ""
        let bpmEntry = EntryTestFixtures.makeBpmEntry(timestamp: "2026-03-02T08:00:00Z")
        repo.entries = [legacyWeightEntry, bpmEntry]
        let sut = makeSUT(repo: repo)

        let weightEntries = try await sut.getMonthDetail(month: "2026-03", entryType: .wg)
        let bpmEntries = try await sut.getMonthDetail(month: "2026-03", entryType: .bpm)

        #expect(weightEntries.count == 1)
        #expect(weightEntries.first?.entryType == "")
        #expect(bpmEntries.count == 1)
        #expect(bpmEntries.first?.entryType == EntryType.bpm.rawValue)
    }

    // MARK: - getMonthYear

    @Test("getMonthYear: only returns months within last 365 days")
    func getMonthYearFiltersLastYear() async throws {
        let repo = MockEntryRepository()
        let recent = EntryTestFixtures.makeEntry(timestamp: ISO8601DateFormatter().string(from: Date()), weight: 1800)
        let old = EntryTestFixtures.makeEntry(timestamp: "2020-01-01T08:00:00Z", weight: 1600)
        repo.entries = [recent, old]
        let sut = makeSUT(repo: repo)

        let months = try await sut.getMonthYear()
        #expect(months.count == 1)
        #expect(months.first?.weight == 1800)
    }

    // MARK: - getMonthSummary

    @Test("getMonthSummary: returns summary for month with entries")
    func getMonthSummaryWithEntries() async throws {
        let repo = MockEntryRepository()
        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-10T08:00:00Z", weight: 1850)
        ]
        let sut = makeSUT(repo: repo)

        let summary = try await sut.getMonthSummary(monthKey: "2026-03")
        #expect(summary != nil)
        #expect(summary?.count == 2)
    }

    @Test("getMonthSummary empty month: returns nil")
    func getMonthSummaryEmpty() async throws {
        let sut = makeSUT()
        let summary = try await sut.getMonthSummary(monthKey: "2026-03")
        #expect(summary == nil)
    }

    // MARK: - getStreak

    @Test("getStreak no entries: returns zero")
    func getStreakNoEntries() async throws {
        let sut = makeSUT()
        let streak = try await sut.getStreak()
        #expect(streak.current == 0)
        #expect(streak.max == 0)
    }

    @Test("getStreak with consecutive days including today: counts current streak")
    func getStreakConsecutiveDays() async throws {
        let repo = MockEntryRepository()
        let cal = Calendar.current
        let today = Date()
        guard let yesterday = cal.date(byAdding: .day, value: -1, to: today),
              let twoDaysAgo = cal.date(byAdding: .day, value: -2, to: today) else {
            Issue.record("Failed to create test dates")
            return
        }
        let iso = ISO8601DateFormatter()

        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: today), weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: yesterday), weight: 1810),
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: twoDaysAgo), weight: 1820)
        ]
        let sut = makeSUT(repo: repo)

        let streak = try await sut.getStreak()
        #expect(streak.current == 3)
        #expect(streak.max == 3)
    }

    @Test("getStreak with gap: longest is computed correctly")
    func getStreakWithGap() async throws {
        let repo = MockEntryRepository()
        let cal = Calendar.current
        let today = Date()
        guard let threeDaysAgo = cal.date(byAdding: .day, value: -3, to: today),
              let fourDaysAgo = cal.date(byAdding: .day, value: -4, to: today),
              let fiveDaysAgo = cal.date(byAdding: .day, value: -5, to: today) else {
            Issue.record("Failed to create test dates")
            return
        }
        let iso = ISO8601DateFormatter()

        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: today), weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: threeDaysAgo), weight: 1810),
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: fourDaysAgo), weight: 1820),
            EntryTestFixtures.makeEntry(timestamp: iso.string(from: fiveDaysAgo), weight: 1830)
        ]
        let sut = makeSUT(repo: repo)

        let streak = try await sut.getStreak()
        #expect(streak.current == 1)
        #expect(streak.max == 3)
    }

    // MARK: - exportCSV

    @Test("exportCSV no dashboard type: throws AccountError.noActiveAccount")
    func exportCSVNoDashboardType() async {
        let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "e@e.com", isActiveAccount: true, dashboardType: nil)
        let sut = makeSUT(activeAccount: account)

        do {
            try await sut.exportCSV()
            Issue.record("Expected throw")
        } catch {
            #expect(error is AccountError)
        }
    }

    @Test("exportCSV with dashboard4: uses non-R4 endpoint")
    func exportCSVDashboard4() async throws {
        let remote = MockEntryRepositoryAPI()
        let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "e@e.com", isActiveAccount: true, dashboardType: DashboardType.dashboard4.rawValue)
        let sut = makeSUT(remote: remote, activeAccount: account)

        try await sut.exportCSV()
        #expect(remote.lastExportCsvUseR4Endpoint == false)
    }

    @Test("exportCSV remote failure: throws")
    func exportCSVRemoteFailure() async {
        let remote = MockEntryRepositoryAPI()
        remote.exportCsvError = EntryTestError.remoteFailure
        let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "e@e.com", isActiveAccount: true, dashboardType: DashboardType.dashboard12.rawValue)
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
        #expect(remote.fetchOperationsCalls == 1)
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
        #expect(remote.syncOperationCalls >= 1)
    }

    @Test("syncAllEntriesWithRemote remote fetch failure: logs error")
    func syncAllEntriesRemoteFetchFailure() async {
        let remote = MockEntryRepositoryAPI()
        remote.fetchOperationsError = EntryTestError.remoteFailure
        let logger = MockLoggerService()
        let sut = makeSUT(remote: remote, logger: logger)

        await sut.syncAllEntriesWithRemote()
        #expect(logger.messages.contains { $0.contains("Full entry sync failed") })
    }

    // MARK: - mergeRemoteOperations (via syncAllEntriesWithRemote)


    @Test("sync merges remote delete: removes local entry")
    func syncMergesRemoteDelete() async {
        let repo = MockEntryRepository()
        let existing = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", operationType: .create, serverTimestamp: "a", isSynced: true)
        repo.entries = [existing]
        let remote = MockEntryRepositoryAPI()
        remote.fetchOperationsResult = BathScaleOperationListResponse(
            operations: [
                BathScaleOperationDTO(
                    accountId: "acct-1", bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
                    entryTimestamp: "2026-03-01T08:00:00Z", entryType: nil, impedance: nil, metabolicAge: nil,
                    muscleMass: nil, operationType: "delete", proteinPercent: nil, pulse: nil,
                    serverTimestamp: "b", skeletalMusclePercent: nil,
                    source: nil, subcutaneousFatPercent: nil,
                    systolic: nil, diastolic: nil, meanArterial: nil, unit: nil,
                    visceralFatLevel: nil, water: nil, weight: nil
                )
            ],
            timestamp: "2026-03-01T10:00:00Z"
        )
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllEntriesWithRemote()
        #expect(repo.entries.isEmpty)
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
            BathScaleWeightSummary(accountId: "acct-1", period: "2026-03-01",
                                   entryTimestamp: "2026-03-01T08:00:00Z",
                                   date: DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd"),
                                   count: 1, weight: 1800)
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
        remote.syncOperationError = EntryTestError.remoteFailure
        let syncStore = MockEntrySyncStore()
        let sut = makeSUT(repo: repo, remote: remote, syncStore: syncStore)

        await sut.syncAllEntriesWithRemote()
        #expect(repo.updateEntrySyncStatusCalls >= 1)
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

    // MARK: - Factory

    private func makeSUT(
        repo: MockEntryRepository? = nil,
        remote: MockEntryRepositoryAPI? = nil,
        syncStore: MockEntrySyncStore? = nil,
        integration: MockIntegrationService? = nil,
        goalAlert: MockGoalAlertService? = nil,
        logger: MockLoggerService? = nil,
        activeAccount: AccountSnapshot? = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "entry@example.com", isActiveAccount: true)
    ) -> EntryService {
        let account = MockAccountService()
        account.activeAccount = activeAccount

        let logger = logger ?? MockLoggerService()
        let goalAlert = goalAlert ?? MockGoalAlertService()
        let integration = integration ?? MockIntegrationService()
        let keychain = MockKeychainService()
        let bluetooth = MockBluetoothService()

        TestDependencyContainer.reset()
        TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)
        DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

        let sut = EntryService(
            accountService: account,
            localRepo: repo ?? MockEntryRepository(),
            localKVRepo: syncStore ?? MockEntrySyncStore(),
            remoteRepo: remote ?? MockEntryRepositoryAPI()
        )
        sut.logger = logger
        sut.goalAlertService = goalAlert
        sut.integrationService = integration
        return sut
    }
}
