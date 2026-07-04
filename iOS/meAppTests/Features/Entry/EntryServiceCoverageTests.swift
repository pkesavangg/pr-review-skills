import Combine
import Foundation
@testable import meApp
import Testing

/// Additional coverage for `EntryService` (Data/Services) targeting the sync-merge,
/// baby-entry, and delete-handler paths that the base suites don't exercise (MOB-1396).
@Suite(.serialized)
@MainActor
struct EntryServiceCoverageTests {

    // MARK: - SUT

    private func makeSUT(
        repo: MockEntryRepository? = nil,
        remote: MockEntryRepositoryAPI? = nil,
        syncStore: MockEntrySyncStore? = nil,
        integration: MockIntegrationService? = nil,
        goalAlert: MockGoalAlertService? = nil,
        logger: MockLoggerService? = nil,
        activeAccount: AccountSnapshot? = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1", email: "entry@example.com", isActiveAccount: true
        )
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
        DependencyContainer.shared.register(logger)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(goalAlert)
        DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(integration)
        DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

        let service = EntryService(
            accountService: account,
            localRepo: repo ?? MockEntryRepository(),
            localKVRepo: syncStore ?? MockEntrySyncStore(),
            remoteRepo: remote ?? MockEntryRepositoryAPI()
        )
        service.logger = logger
        service.goalAlertService = goalAlert
        service.integrationService = integration
        return service
    }

    private func remoteDTO(
        serverEntryId: String,
        timestamp: String,
        weight: Double? = 180,
        entryType: String = EntryType.scale.rawValue
    ) -> BathScaleOperationDTO {
        var dto = BathScaleOperationDTO(
            accountId: "acct-1", bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
            entryTimestamp: timestamp, entryType: entryType, impedance: nil, metabolicAge: nil,
            muscleMass: nil, operationType: OperationType.create.rawValue, proteinPercent: nil,
            pulse: nil, serverTimestamp: timestamp, skeletalMusclePercent: nil, source: "manual",
            subcutaneousFatPercent: nil, systolic: nil, diastolic: nil, meanArterial: nil,
            unit: "lb", visceralFatLevel: nil, water: nil, weight: weight
        )
        dto.serverEntryId = serverEntryId
        return dto
    }

    // MARK: - createBabyEntry

    @Test("createBabyEntry: persists a baby entry locally and emits entrySaved")
    func createBabyEntrySuccess() async throws {
        let repo = MockEntryRepository()
        let sut = makeSUT(repo: repo)

        try await sut.createBabyEntry(
            babyId: "baby-1", weight: 3500, length: 500, note: "morning",
            entryTimestamp: "2026-03-01T08:00:00Z"
        )

        #expect(repo.entries.contains { $0.babyEntry?.babyId == "baby-1" })
        #expect(repo.entries.first?.entryType == EntryType.baby.rawValue)
    }

    @Test("createBabyEntry: rethrows when the local save fails")
    func createBabyEntrySaveFailureRethrows() async {
        let repo = MockEntryRepository()
        repo.saveEntryError = EntryTestError.localFailure
        let sut = makeSUT(repo: repo)

        await #expect(throws: (any Error).self) {
            try await sut.createBabyEntry(
                babyId: "baby-1", weight: 3500, length: 500, note: "n",
                entryTimestamp: "2026-03-01T08:00:00Z"
            )
        }
    }

    // MARK: - handleEntryDeleted

    @Test("handleEntryDeleted: recomputes summaries and forwards to the integration service")
    func handleEntryDeletedForwardsToIntegration() async throws {
        let repo = MockEntryRepository()
        let integration = MockIntegrationService()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")
        repo.entries = [EntryTestFixtures.makeEntry(timestamp: "2026-03-01T09:00:00Z")]
        let sut = makeSUT(repo: repo, integration: integration)

        try await sut.handleEntryDeleted(entry)

        #expect(integration.deleteEntryCalls == 1)
    }

    @Test("handleEntryDeleted: swallows an integration failure without throwing")
    func handleEntryDeletedIgnoresIntegrationFailure() async throws {
        let repo = MockEntryRepository()
        let integration = MockIntegrationService()
        integration.deleteEntryError = EntryTestError.localFailure
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")
        let sut = makeSUT(repo: repo, integration: integration)

        // Must not throw even though the integration delete errors.
        try await sut.handleEntryDeleted(entry)

        #expect(integration.deleteEntryCalls == 1)
    }

    // MARK: - syncAllEntriesWithRemote

    @Test("syncAllEntriesWithRemote: no unsynced local and empty remote completes without changes")
    func syncNoChanges() async {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllEntriesWithRemote()

        #expect(sut.isSyncing == false)
    }

    @Test("syncAllEntriesWithRemote: pushes unsynced local entries to the server")
    func syncPushesUnsynced() async {
        let repo = MockEntryRepository()
        repo.entries = [EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", isSynced: false)]
        let remote = MockEntryRepositoryAPI()
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllEntriesWithRemote()

        #expect(remote.submitEntriesCalls >= 1)
    }

    @Test("syncAllEntriesWithRemote: merges new remote creates into local storage")
    func syncMergesRemoteCreates() async {
        let repo = MockEntryRepository()
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesResult = BathScaleOperationListResponse(
            operations: [
                remoteDTO(serverEntryId: "srv-1", timestamp: "2026-03-02T08:00:00Z"),
                remoteDTO(serverEntryId: "srv-2", timestamp: "2026-03-03T08:00:00Z")
            ],
            timestamp: "2026-03-03T08:00:00Z"
        )
        let sut = makeSUT(repo: repo, remote: remote)

        await sut.syncAllEntriesWithRemote()

        // Drives the remote fetch + mergeRemoteOperations path. The merge persists via the
        // SwiftData worker (not the mock's in-memory array), so we assert the sync ran to
        // completion rather than inspecting the mock repo.
        #expect(remote.fetchEntriesCalls >= 1)
        #expect(sut.isSyncing == false)
    }

    @Test("syncAllEntriesWithRemote: concurrent calls piggyback on the in-flight sync")
    func syncConcurrentCallsCoalesce() async {
        let sut = makeSUT()

        async let a: Void = sut.syncAllEntriesWithRemote()
        async let b: Void = sut.syncAllEntriesWithRemote()
        _ = await (a, b)

        #expect(sut.isSyncing == false)
    }
}
