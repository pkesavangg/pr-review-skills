import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BpmEntryServiceTests {

    // MARK: - createBpmEntry

    @Test("createBpmEntry: saves BPM entry locally and syncs to remote")
    func createBpmEntrySuccess() async throws {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let syncStore = MockEntrySyncStore()
        let logger = MockLoggerService()
        let sut = makeSUT(repo: repo, remote: remote, syncStore: syncStore, logger: logger)

        let dto = EntryTestFixtures.makeBpmDTO(
            systolic: 130.0,
            diastolic: 85.0,
            pulse: 68.0,
            entryTimestamp: "2026-03-01T08:00:00Z"
        )

        var savedNotifications: [EntryNotification] = []
        let cancellable = sut.entrySaved.sink { savedNotifications.append($0) }

        try await sut.createBpmEntry(dto)

        #expect(repo.saveEntryCalls == 1)
        #expect(repo.entries.count == 1)
        let savedEntry = repo.entries.first
        #expect(savedEntry?.entryType == EntryType.bpm.rawValue)
        #expect(savedEntry?.scaleEntry?.systolic == 130)
        #expect(savedEntry?.scaleEntry?.diastolic == 85)
        #expect(savedEntry?.scaleEntryMetric?.pulse == 68)
        #expect(savedEntry?.operationType == OperationType.create.rawValue)
        #expect(savedNotifications.count >= 1)
        cancellable.cancel()
    }

    @Test("createBpmEntry: local failure throws and skips sync")
    func createBpmEntryLocalFailure() async {
        let repo = MockEntryRepository()
        repo.saveEntryError = EntryTestError.localFailure
        let remote = MockEntryRepositoryAPI()
        let sut = makeSUT(repo: repo, remote: remote)

        do {
            try await sut.createBpmEntry(EntryTestFixtures.makeBpmDTO())
            Issue.record("Expected createBpmEntry to throw")
        } catch {
            #expect(error as? EntryTestError == .localFailure)
        }
    }

    @Test("createBpmEntry: sets accountId from active account")
    func createBpmEntrySetsAccountId() async throws {
        let repo = MockEntryRepository()
        let sut = makeSUT(repo: repo)

        let dto = EntryTestFixtures.makeBpmDTO(accountId: nil)
        try await sut.createBpmEntry(dto)

        #expect(repo.entries.first?.accountId == "acct-1")
    }

    @Test("createBpmEntry: refreshes BPM dashboard summaries with the new reading")
    func createBpmEntryRefreshesDashboardSummaries() async throws {
        let repo = MockEntryRepository()
        let sut = makeSUT(repo: repo)

        try await sut.createBpmEntry(
            EntryTestFixtures.makeBpmDTO(
                systolic: 130.0,
                diastolic: 85.0,
                pulse: 68.0,
                entryTimestamp: "2026-03-01T08:00:00Z"
            )
        )

        let matchingSummary = sut.bpmDailySummaries.first { $0.period == "2026-03-01" }
        #expect(matchingSummary != nil)
        #expect(matchingSummary?.systolic == 130.0)
        #expect(matchingSummary?.diastolic == 85.0)
    }

    @Test("createBpmEntry: no active account throws")
    func createBpmEntryNoAccount() async {
        let sut = makeSUT(activeAccount: nil)

        do {
            try await sut.createBpmEntry(EntryTestFixtures.makeBpmDTO())
            Issue.record("Expected createBpmEntry to throw")
        } catch {
            let nsError = error as NSError
            #expect(nsError.domain == "EntryService")
            #expect(nsError.code == 401)
        }
    }

    // MARK: - fetchBpmEntries

    @Test("fetchBpmEntries: returns only BPM entries for active account")
    func fetchBpmEntriesFiltersCorrectly() async throws {
        let repo = MockEntryRepository()
        let bpmEntry = EntryTestFixtures.makeBpmEntry(timestamp: "2026-03-01T08:00:00Z")
        let scaleEntry = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z")
        let foreignBpm = EntryTestFixtures.makeBpmEntry(accountId: "acct-2", timestamp: "2026-03-03T08:00:00Z")
        repo.entries = [bpmEntry, scaleEntry, foreignBpm]
        let sut = makeSUT(repo: repo)

        let results = try await sut.fetchBpmEntries()

        #expect(results.count == 1)
        #expect(results.first?.systolic == 120.0)
        #expect(results.first?.entryTimestamp == "2026-03-01T08:00:00Z")
    }

    @Test("fetchBpmEntries: returns empty when no BPM entries exist")
    func fetchBpmEntriesEmpty() async throws {
        let repo = MockEntryRepository()
        repo.entries = [EntryTestFixtures.makeEntry()]
        let sut = makeSUT(repo: repo)

        let results = try await sut.fetchBpmEntries()

        #expect(results.isEmpty)
    }

    // MARK: - deleteBpmEntry

    @Test("deleteBpmEntry: marks entry as delete and syncs")
    func deleteBpmEntrySuccess() async throws {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let bpmEntry = EntryTestFixtures.makeBpmEntry(
            timestamp: "2026-03-01T08:00:00Z",
            isSynced: true
        )
        repo.entries = [bpmEntry]
        let sut = makeSUT(repo: repo, remote: remote)

        var deletedNotifications: [EntryNotification] = []
        let cancellable = sut.entryDeleted.sink { deletedNotifications.append($0) }

        try await sut.deleteBpmEntry(entryTimestamp: "2026-03-01T08:00:00Z")

        #expect(repo.updateEntryCalls == 1)
        let updatedEntry = repo.lastUpdatedEntry
        #expect(updatedEntry?.operationType == OperationType.delete.rawValue)
        #expect(updatedEntry?.isSynced == false)
        #expect(deletedNotifications.count >= 1)
        cancellable.cancel()
    }

    @Test("deleteBpmEntry: throws when BPM entry not found")
    func deleteBpmEntryNotFound() async {
        let repo = MockEntryRepository()
        // Add a scale entry with the same timestamp
        repo.entries = [EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")]
        let sut = makeSUT(repo: repo)

        do {
            try await sut.deleteBpmEntry(entryTimestamp: "2026-03-01T08:00:00Z")
            Issue.record("Expected deleteBpmEntry to throw")
        } catch {
            let nsError = error as NSError
            #expect(nsError.code == 404)
        }
    }

    @Test("deleteBpmEntry: refreshes BPM dashboard summaries after deletion")
    func deleteBpmEntryRefreshesDashboardSummaries() async throws {
        let repo = MockEntryRepository()
        repo.entries = [EntryTestFixtures.makeBpmEntry(timestamp: "2026-03-01T08:00:00Z")]
        let sut = makeSUT(repo: repo)

        await sut.loadDashboardData(entryType: .bpm)
        #expect(sut.bpmDailySummaries.contains { $0.period == "2026-03-01" })

        try await sut.deleteBpmEntry(entryTimestamp: "2026-03-01T08:00:00Z")

        #expect(!sut.bpmDailySummaries.contains { $0.period == "2026-03-01" })
    }

    // MARK: - Helpers

    private func makeSUT(
        repo: MockEntryRepository? = nil,
        remote: MockEntryRepositoryAPI? = nil,
        syncStore: MockEntrySyncStore? = nil,
        integration: MockIntegrationService? = nil,
        goalAlert: MockGoalAlertService? = nil,
        logger: MockLoggerService? = nil,
        activeAccount: AccountSnapshot? = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "bpm@example.com", isActiveAccount: true)
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
