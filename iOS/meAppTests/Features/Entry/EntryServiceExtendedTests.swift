import Combine
import Foundation
@testable import meApp
import Testing

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

        let weightEntries = try await sut.getMonthDetail(month: "2026-03", entryType: .scale)
        let bpmEntries = try await sut.getMonthDetail(month: "2026-03", entryType: .bpm)

        #expect(weightEntries.count == 1)
        // swiftlint:disable:next empty_string
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

    // MARK: - serverEntryId persistence

    @Test("syncAllEntriesWithRemote: stores server-assigned entryId returned in submit response")
    func syncAllEntriesWithRemoteStoresServerEntryId() async {
        let repo = MockEntryRepository()
        repo.entries = [EntryTestFixtures.makeEntry(isSynced: false)]

        let remote = MockEntryRepositoryAPI()
        remote.submitEntriesResult = UnifiedEntryResponse(
            entries: [UnifiedEntryResult(
                category: EntryCategory.weight.rawValue,
                entryId: "server-abc-123",
                operationType: "create",
                entryTimestamp: "2026-03-01T08:00:00Z",
                serverTimestamp: "2026-03-01T08:00:05Z",
                source: "manual",
                weight: 1800,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                boneMass: nil,
                impedance: nil,
                unit: "lb",
                systolic: nil,
                diastolic: nil,
                pulse: nil,
                note: nil
            )],
            timestamp: "2026-03-01T08:00:05Z"
        )

        let worker = MockEntryWorker()
        let sut = makeSUT(repo: repo, remote: remote, worker: worker)
        await sut.syncAllEntriesWithRemote()

        // The batched push routes bookkeeping through the worker: the create
        // outcome must carry the server-assigned id from the response row.
        let createdOutcome = worker.appliedPushOutcomes.first
        #expect(createdOutcome != nil)
        if case .created(let serverEntryId, _)? = createdOutcome?.outcome {
            #expect(serverEntryId == "server-abc-123")
        } else {
            Issue.record("Expected a .created push outcome, got \(String(describing: createdOutcome))")
        }
    }

    @Test("syncAllEntriesWithRemote: logs error and continues when push bookkeeping fails")
    func syncAllEntriesWithRemoteLogsErrorOnServerEntryIdFailure() async {
        let repo = MockEntryRepository()
        repo.entries = [EntryTestFixtures.makeEntry(isSynced: false)]

        let remote = MockEntryRepositoryAPI()
        remote.submitEntriesResult = UnifiedEntryResponse(
            entries: [UnifiedEntryResult(
                category: EntryCategory.weight.rawValue,
                entryId: "server-xyz-456",
                operationType: "create",
                entryTimestamp: "2026-03-01T08:00:00Z",
                serverTimestamp: "2026-03-01T08:00:05Z",
                source: "manual",
                weight: 1800,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                boneMass: nil,
                impedance: nil,
                unit: "lb",
                systolic: nil,
                diastolic: nil,
                pulse: nil,
                note: nil
            )],
            timestamp: "2026-03-01T08:00:05Z"
        )

        let worker = MockEntryWorker()
        worker.applyPushOutcomesError = EntryTestError.localFailure
        let logger = MockLoggerService()
        let sut = makeSUT(repo: repo, remote: remote, logger: logger, worker: worker)
        await sut.syncAllEntriesWithRemote()

        #expect(logger.messages.contains { $0.contains("Failed to persist push outcomes") })
        // The sync must still complete (fetch + merge + timestamp) after the
        // bookkeeping failure.
        #expect(worker.applyRemoteOperationsCalls == 1, "Sync should continue despite push bookkeeping failure")
    }

    // MARK: - Factory

    func makeSUT(
        repo: MockEntryRepository? = nil,
        remote: MockEntryRepositoryAPI? = nil,
        syncStore: MockEntrySyncStore? = nil,
        integration: MockIntegrationService? = nil,
        goalAlert: MockGoalAlertService? = nil,
        logger: MockLoggerService? = nil,
        worker: MockEntryWorker? = nil,
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

        let localRepo = repo ?? MockEntryRepository()
        let entryWorker = worker ?? MockEntryWorker()
        // Keep worker reads consistent with the repo the SUT writes to
        // (production: one shared SwiftData container).
        entryWorker.backingRepo = localRepo
        let sut = EntryService(
            accountService: account,
            localRepo: localRepo,
            localKVRepo: syncStore ?? MockEntrySyncStore(),
            remoteRepo: remote ?? MockEntryRepositoryAPI(),
            worker: entryWorker
        )
        sut.logger = logger
        sut.goalAlertService = goalAlert
        sut.integrationService = integration
        return sut
    }
}
