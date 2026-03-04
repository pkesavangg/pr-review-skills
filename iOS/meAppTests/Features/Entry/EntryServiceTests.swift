import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct EntryServiceTests {
    @Test("saveNewEntry success: saves locally, syncs API, updates summaries and notifications")
    func saveNewEntrySuccess() async throws {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let syncStore = MockEntrySyncStore()
        let integration = MockIntegrationService()
        let goalAlert = MockGoalAlertService()
        let logger = MockLoggerService()
        let sut = makeSUT(
            repo: repo,
            remote: remote,
            syncStore: syncStore,
            integration: integration,
            goalAlert: goalAlert,
            logger: logger
        )

        let entry = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-01T08:00:00Z",
            weight: 1800,
            source: "scale"
        )
        var savedNotifications: [EntryNotification] = []
        let cancellable = sut.entrySaved.sink { savedNotifications.append($0) }

        try await sut.saveNewEntry(entry)

        #expect(repo.saveEntryCalls == 1)
        #expect(remote.syncOperationCalls == 1)
        #expect(remote.lastSyncedOperation?.accountId == "acct-1")
        #expect(remote.lastSyncedOperation?.weight == 1800)
        #expect(remote.lastSyncedOperation?.source == "scale")
        #expect(repo.updateEntrySyncStatusCalls == 1)
        #expect(repo.entries.count == 1)
        #expect(repo.entries.first?.isSynced == true)
        #expect(savedNotifications.count == 1)
        #expect(savedNotifications.first?.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(integration.syncNewEntryCalls == 1)
        #expect(sut.dailySummaries.count == 1)
        #expect(sut.dailySummaries.first?.weight == 1800)
        #expect(sut.monthlySummaries.count == 1)
        cancellable.cancel()
    }

    @Test("saveNewEntry local failure: throws and skips sync")
    func saveNewEntryLocalFailure() async {
        let repo = MockEntryRepository()
        repo.saveEntryError = EntryTestError.localFailure
        let remote = MockEntryRepositoryAPI()
        let sut = makeSUT(repo: repo, remote: remote)

        do {
            try await sut.saveNewEntry(EntryTestFixtures.makeEntry())
            Issue.record("Expected saveNewEntry to throw")
        } catch {
            #expect(error as? EntryTestError == .localFailure)
        }

        #expect(remote.syncOperationCalls == 0)
        #expect(repo.entries.isEmpty)
    }

    @Test("entry readers: return active account data and DTOs")
    func entryReadersReturnActiveAccountData() async throws {
        let repo = MockEntryRepository()
        let first = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        let second = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1810)
        let foreign = EntryTestFixtures.makeEntry(accountId: "acct-2", timestamp: "2026-03-03T08:00:00Z", weight: 1900)
        repo.entries = [first, second, foreign]
        let sut = makeSUT(repo: repo)

        let entries = try await sut.getAllEntries()
        let dtos = try await sut.getAllEntriesAsDTO()
        let count = try await sut.getEntryCount()
        let oldest = try await sut.getOldestEntry()
        let latest = try await sut.getLatestEntry()
        let exists = try await sut.checkEntryTimestampExists("2026-03-02T08:00:00Z")

        #expect(entries.count == 2)
        #expect(dtos.count == 2)
        #expect(dtos.allSatisfy { $0.accountId == "acct-1" })
        #expect(count == 2)
        #expect(oldest?.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(latest?.entryTimestamp == "2026-03-02T08:00:00Z")
        #expect(exists == true)
    }

    @Test("entry readers no active account: throw")
    func entryReadersNoActiveAccount() async {
        let sut = makeSUT(activeAccount: nil)

        do {
            _ = try await sut.getAllEntries()
            Issue.record("Expected getAllEntries to throw")
        } catch {
            let nsError = error as NSError
            #expect(nsError.domain == "EntryService")
            #expect(nsError.code == 401)
        }
    }

    @Test("deleteEntry success: syncs delete, removes local data, and clears summaries")
    func deleteEntrySuccess() async throws {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let integration = MockIntegrationService()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800, isSynced: true)
        repo.entries = [entry]
        let sut = makeSUT(repo: repo, remote: remote, integration: integration)
        await sut.loadDashboardData()
        var deletedNotifications: [EntryNotification] = []
        let cancellable = sut.entryDeleted.sink { deletedNotifications.append($0) }

        try await sut.deleteEntry(entry)

        #expect(remote.syncOperationCalls == 1)
        #expect(remote.lastSyncedOperation?.operationType == OperationType.delete.rawValue)
        #expect(repo.entries.isEmpty)
        #expect(sut.dailySummaries.isEmpty)
        #expect(sut.monthlySummaries.isEmpty)
        #expect(deletedNotifications.count >= 1)
        cancellable.cancel()
    }

    @Test("handleEntryUpdated: recomputes daily and monthly summaries for edited data")
    func handleEntryUpdatedRecomputesSummaries() async throws {
        let repo = MockEntryRepository()
        let updated = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1950)
        repo.entries = [updated]
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
        sut.monthlySummaries = [
            BathScaleWeightSummary(
                accountId: "acct-1",
                period: "2026-03",
                entryTimestamp: "2026-03-01T08:00:00Z",
                date: DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd"),
                count: 1,
                weight: 1800
            )
        ]

        try await sut.handleEntryUpdated(updated)

        #expect(sut.dailySummaries.first?.weight == 1950)
        #expect(sut.monthlySummaries.first?.weight == 1950)
    }

    @Test("aggregateByDay: averages weights and ignores zero-value metrics")
    func aggregateByDayAveragesWeights() {
        let sut = makeSUT()
        let first = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800, bodyFat: 250)
        let second = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T12:00:00Z", weight: 1820, bodyFat: 0)
        let invalid = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T15:00:00Z", weight: 0, bodyFat: 300)

        let summaries = sut.aggregateByDay(entries: [first, second, invalid], accountId: "acct-1").compactMap { $0 }

        #expect(summaries.count == 1)
        #expect(summaries.first?.period == "2026-03-01")
        #expect(summaries.first?.count == 2)
        #expect(summaries.first?.weight == 1810)
        #expect(summaries.first?.bodyFat == 250)
    }

    @Test("aggregateByMonth: groups entries by month and filters zero-weight rows")
    func aggregateByMonthGroupsEntries() {
        let sut = makeSUT()
        let march = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        let april = EntryTestFixtures.makeEntry(timestamp: "2026-04-02T08:00:00Z", weight: 1900)
        let zero = EntryTestFixtures.makeEntry(timestamp: "2026-04-03T08:00:00Z", weight: 0)

        let summaries = sut.aggregateByMonth(entries: [march, april, zero], accountId: "acct-1").compactMap { $0 }

        #expect(summaries.count == 2)
        #expect(summaries.first?.period == "2026-03")
        #expect(summaries.last?.period == "2026-04")
        #expect(summaries.last?.count == 1)
        #expect(summaries.last?.weight == 1900)
    }

    @Test("loadDashboardData: builds daily and monthly summaries from DTOs")
    func loadDashboardDataBuildsSummaries() async {
        let repo = MockEntryRepository()
        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T12:00:00Z", weight: 1820),
            EntryTestFixtures.makeEntry(timestamp: "2026-04-02T08:00:00Z", weight: 1900)
        ]
        let sut = makeSUT(repo: repo)

        await sut.loadDashboardData()

        #expect(sut.dailySummaries.count == 2)
        #expect(sut.dailySummaries.first?.weight == 1810)
        #expect(sut.monthlySummaries.count == 2)
        #expect(sut.monthlySummaries.first?.period == "2026-03")
    }

    @Test("loadDashboardData failure: logs error and leaves summaries untouched")
    func loadDashboardDataFailure() async {
        let repo = MockEntryRepository()
        repo.fetchEntriesAsDTOError = EntryTestError.localFailure
        let logger = MockLoggerService()
        let sut = makeSUT(repo: repo, logger: logger)
        sut.dailySummaries = [
            BathScaleWeightSummary(
                accountId: "acct-1",
                period: "2026-02-01",
                entryTimestamp: "2026-02-01T08:00:00Z",
                date: DateTimeTools.getDateFromDateString("2026-02-01", format: "yyyy-MM-dd"),
                count: 1,
                weight: 1700
            )
        ]

        await sut.loadDashboardData()

        #expect(sut.dailySummaries.count == 1)
        #expect(logger.messages.contains(where: { $0.contains("loadDashboardData failed") }))
    }

    @Test("exportCSV: uses R4 endpoint for dashboard 12 accounts")
    func exportCSVUsesDashboardType() async throws {
        let remote = MockEntryRepositoryAPI()
        let account = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "entry@example.com", isActive: true)
        account.dashboardSettings?.dashboardType = DashboardType.dashboard12.rawValue
        let sut = makeSUT(remote: remote, activeAccount: account)

        try await sut.exportCSV()

        #expect(remote.exportCsvCalls == 1)
        #expect(remote.lastExportCsvUseR4Endpoint == true)
    }

    @Test("saveNewEntry integration failure: logs but still succeeds")
    func saveNewEntryIntegrationFailure() async throws {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let integration = MockIntegrationService()
        integration.syncNewEntryError = EntryTestError.remoteFailure
        let logger = MockLoggerService()
        let sut = makeSUT(repo: repo, remote: remote, integration: integration, logger: logger)

        try await sut.saveNewEntry(EntryTestFixtures.makeEntry())

        #expect(repo.entries.count == 1)
        #expect(remote.syncOperationCalls == 1)
        #expect(logger.messages.contains(where: { $0.contains("Failed to sync new entry to integrations") }))
    }

    private func makeSUT(
            repo: MockEntryRepository? = nil,
            remote: MockEntryRepositoryAPI? = nil,
            syncStore: MockEntrySyncStore? = nil,
            integration: MockIntegrationService? = nil,
            goalAlert: MockGoalAlertService? = nil,
            logger: MockLoggerService? = nil,
            activeAccount: Account? = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "entry@example.com", isActive: true)
        ) -> EntryService {
            let account = MockAccountService()
            account.activeAccount = activeAccount

            let logger = logger ?? MockLoggerService()
            let goalAlert = goalAlert ?? MockGoalAlertService()
            let integration = integration ?? MockIntegrationService()
            let keychain = MockKeychainService()
            let bluetooth = MockBluetoothService()

            TestDependencyContainer.reset()
            TestDependencyContainer.registerBase(
                logger: logger,
                keychain: keychain,
                bluetooth: bluetooth
            )
            DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)
            DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

            return EntryService(
                accountService: account,
                localRepo: repo ?? MockEntryRepository(),
                localKVRepo: syncStore ?? MockEntrySyncStore(),
                remoteRepo: remote ?? MockEntryRepositoryAPI()
            )
        }
}
