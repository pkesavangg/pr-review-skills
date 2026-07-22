import Combine
import Foundation
@testable import meApp
import Testing

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

        // MA-3820: local save is decoupled from remote sync. saveNewEntry only persists locally,
        // updates summaries, fires the entrySaved notification, and triggers integration sync.
        // Pushing to the remote (submitEntries) now happens separately via syncAllEntriesWithRemote,
        // so the entry stays unsynced here and no submit/sync-status calls are made.
        #expect(repo.saveEntryCalls == 1)
        #expect(remote.submitEntriesCalls == 0)
        #expect(repo.updateEntrySyncStatusCalls == 0)
        #expect(repo.entries.count == 1)
        #expect(repo.entries.first?.isSynced == false)
        #expect(savedNotifications.count == 1)
        #expect(savedNotifications.first?.entryTimestamp == "2026-03-01T08:00:00Z")
        // MOB-1433 §5c: the integration (HealthKit) forward now runs behind the save as a
        // fire-and-forget Task so it can't block the save, so await it before asserting.
        let synced = await waitUntil { integration.syncNewEntryCalls == 1 }
        #expect(synced)
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

        #expect(remote.submitEntriesCalls == 0)
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

    @Test("getEntryCount routes through the @ModelActor worker, not the main-actor repo (MOB-516)")
    func getEntryCountRoutesThroughWorker() async throws {
        let repo = MockEntryRepository()
        repo.entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1810)
        ]
        let worker = MockEntryWorker()
        let sut = makeSUT(repo: repo, worker: worker)

        let count = try await sut.getEntryCount()

        #expect(count == 2)
        // The MOB-516 fix: the COUNT runs on the worker (off the main actor), not directly on the
        // @MainActor EntryRepository. A revert to `localRepo.fetchEntryCount` would leave this at 0.
        #expect(worker.fetchEntryCountCalls == 1)
    }

    @Test("getEntryCount propagates a worker fetch error (MOB-516)")
    func getEntryCountPropagatesWorkerError() async {
        let worker = MockEntryWorker()
        worker.entryCountError = MockEntryWorkerError.insertFailed
        let sut = makeSUT(worker: worker)

        await #expect(throws: MockEntryWorkerError.self) {
            _ = try await sut.getEntryCount()
        }
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

    @Test("deleteEntry success: queues delete locally, notifies integrations, and clears summaries")
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

        // MA-3820: delete is decoupled from remote sync. deleteEntry marks the entry as a pending
        // delete locally (operationType == "delete", unsynced) and clears the affected summaries.
        // The actual remote submitEntries happens later via syncAllEntriesWithRemote, so the
        // delete-op row remains in the local store here and no submit call is made.
        #expect(remote.submitEntriesCalls == 0)
        #expect(repo.entries.count == 1)
        #expect(repo.entries.first?.operationType == OperationType.delete.rawValue)
        #expect(repo.entries.first?.isSynced == false)
        #expect(integration.deleteEntryCalls == 1)
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

    @Test("aggregateByDay: surfaces latest weigh-in for the most recent day and ignores zero-value metrics")
    func aggregateByDayAveragesWeights() {
        let sut = makeSUT()
        let first = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800, bodyFat: 250)
        let second = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T12:00:00Z", weight: 1820, bodyFat: 0)
        let invalid = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T15:00:00Z", weight: 0, bodyFat: 300)

        let summaries = sut.aggregateByDay(entries: [first, second, invalid], accountId: "acct-1").compactMap { $0 }

        // MA-3937 hybrid rule: the most recent day with valid data surfaces its latest weigh-in
        // (the 12:00 entry, weight 1820) rather than the daily average. The 15:00 entry is ignored
        // because its weight is 0, and bodyFat falls back to the latest non-zero reading (250).
        #expect(summaries.count == 1)
        #expect(summaries.first?.period == "2026-03-01")
        #expect(summaries.first?.count == 2)
        #expect(summaries.first?.weight == 1820)
        #expect(summaries.first?.bodyFat == 250)
    }

    @Test("aggregateByDay: keeps fractional stored average until kg display rounding")
    func aggregateByDayPreservesFractionalStoredAverageForKgDisplay() {
        let sut = makeSUT()
        let kgValues = [20.0, 18.7, 9.3, 17.0, 21.3, 24.9]
        var entries = kgValues.enumerated().map { index, kg in
            EntryTestFixtures.makeEntry(
                timestamp: String(format: "2026-02-17T%02d:00:00Z", index),
                weight: ConversionTools.convertKgToStored(kg)
            )
        }
        // MA-3937 hybrid rule: the *most recent* valid day surfaces its latest weigh-in instead of
        // an average. Add a later day so 2026-02-17 stays on the daily-average branch, which is the
        // branch that must preserve the fractional stored average until kg display rounding.
        entries.append(EntryTestFixtures.makeEntry(timestamp: "2026-02-18T08:00:00Z", weight: 1800))

        let summaries = sut.aggregateByDay(entries: entries, accountId: "acct-1").compactMap { $0 }
        let summary = summaries.first { $0.period == "2026-02-17" }

        #expect(summaries.count == 2)
        #expect(abs((summary?.weight ?? 0) - (2452.0 / 6.0)) < 0.001)
        #expect(ConversionTools.convertStoredToKg(summary?.weight ?? 0) == 18.5)
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

    @Test("loadDashboardData: DTO summaries keep fractional stored averages")
    func loadDashboardDataKeepsFractionalStoredAverages() async {
        let repo = MockEntryRepository()
        let kgValues = [20.0, 18.7, 9.3, 17.0, 21.3, 24.9]
        repo.entries = kgValues.enumerated().map { index, kg in
            EntryTestFixtures.makeEntry(
                timestamp: String(format: "2026-02-17T%02d:00:00Z", index),
                weight: ConversionTools.convertKgToStored(kg)
            )
        }
        // MA-3937 hybrid rule: the most recent valid day surfaces its latest weigh-in, not an average.
        // Add a later day so 2026-02-17 stays on the daily-average branch, which must preserve the
        // fractional stored average until kg display rounding.
        repo.entries.append(EntryTestFixtures.makeEntry(timestamp: "2026-02-18T08:00:00Z", weight: 1800))
        let sut = makeSUT(repo: repo)

        await sut.loadDashboardData()

        let summary = sut.dailySummaries.first { $0.period == "2026-02-17" }
        #expect(sut.dailySummaries.count == 2)
        #expect(abs((summary?.weight ?? 0) - (2452.0 / 6.0)) < 0.001)
        #expect(ConversionTools.convertStoredToKg(summary?.weight ?? 0) == 18.5)
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
        #expect(logger.messages.contains { $0.contains("loadDashboardData failed") })
    }

    @Test("exportCSV: calls unified CSV endpoint in email mode for the given category")
    func exportCSVUsesUnifiedEndpoint() async throws {
        let remote = MockEntryRepositoryAPI()
        let account = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1",
            email: "entry@example.com",
            isActiveAccount: true,
            dashboardType: DashboardType.dashboard12.rawValue
        )
        let sut = makeSUT(remote: remote, activeAccount: account)

        try await sut.exportCSV(category: EntryCategory.weight.rawValue)

        #expect(remote.exportCsvCalls == 1)
        #expect(remote.lastExportCsvRequest?.category == EntryCategory.weight.rawValue)
        #expect(remote.lastExportCsvRequest?.download == false)
    }

    // MARK: - loadBabyDashboardData / decigramsToStoredWeight

    @Test("loadBabyDashboardData: decigramsToStoredWeight converts baby decigrams to stored weight correctly")
    func loadBabyDashboardDataDecigramsConversion() async {
        let repo = MockEntryRepository()
        let entry = Entry(
            entryTimestamp: "2026-05-06T08:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: true
        )
        // 45200 dg → 4.52 kg → Int(round(4.52 × 22.0462)) = Int(round(99.649)) = 100
        entry.babyEntry = BabyEntry(babyId: "baby-1", length: 510, weight: 45200)
        repo.entries = [entry]
        let sut = makeSUT(repo: repo)

        await sut.loadBabyDashboardData(babyId: "baby-1")

        let summaries = sut.babyDailySummariesByProfile["baby-1"] ?? []
        #expect(summaries.count == 1)
        #expect(summaries.first?.weight == 100.0)
    }

    @Test("loadBabyDashboardData: no entries produces empty summaries")
    func loadBabyDashboardDataEmptyEntriesProducesNoSummaries() async {
        let sut = makeSUT()

        await sut.loadBabyDashboardData(babyId: "baby-none")

        #expect((sut.babyDailySummariesByProfile["baby-none"] ?? []).isEmpty)
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

        // MA-3820: saveNewEntry persists locally and triggers integration sync but does not push to
        // the remote inline. An integration failure is logged and swallowed so the save still succeeds.
        #expect(repo.entries.count == 1)
        #expect(remote.submitEntriesCalls == 0)
        // MOB-1433 §5c: the integration forward is fire-and-forget; wait for it to run,
        // fail, and log before asserting the swallowed error was recorded.
        let logged = await waitUntil {
            logger.messages.contains { $0.contains("Failed to sync new entry to integrations") }
        }
        #expect(logged)
    }

    // MARK: - Baby-before-entry ordering (MOB-1527)

    @Test("pushPendingEntries reconciles a pending offline baby before pushing entries")
    func eagerPushReconcilesPendingOfflineBabyFirst() async throws {
        guard NetworkMonitor.shared.isConnected else { return } // eager push is online-only
        let sut = makeSUT()
        let baby = MockBabyService()
        // An offline-created baby awaiting its server id (never pushed): isServerCreated == false.
        baby.babies = [Baby(accountId: "acct-1", name: "Lily", isServerCreated: false)]
        DependencyContainer.shared.register(baby)
        DependencyContainer.shared.register(baby as BabyServiceProtocol)

        await sut.pushPendingEntries()

        #expect(baby.syncBabiesCalls == 1)
    }

    @Test("pushPendingEntries does NOT reconcile babies when none are pending offline")
    func eagerPushSkipsBabyReconcileWhenAllSynced() async throws {
        guard NetworkMonitor.shared.isConnected else { return }
        let sut = makeSUT()
        let baby = MockBabyService()
        // Already on the server — nothing to remap, so no baby reconcile should be triggered.
        baby.babies = [Baby(accountId: "acct-1", name: "Emma", isSynced: true, isServerCreated: true)]
        DependencyContainer.shared.register(baby)
        DependencyContainer.shared.register(baby as BabyServiceProtocol)

        await sut.pushPendingEntries()

        #expect(baby.syncBabiesCalls == 0)
    }

    // MOB-1726: a sync fired before the active account resolves (a dashboard/refresh trigger racing
    // login) must NOT mark the initial sync complete. It used to flip `hasCompletedInitialSync` even
    // though `performSync` bailed at `getAccountId`, which dropped the snapshot cards' skeletons and
    // flashed the empty "no entries" state on cold login until the real post-account sync landed.
    @Test("syncAllEntriesWithRemote with no active account leaves initial-sync flag false (MOB-1726)")
    func syncWithoutActiveAccountDoesNotCompleteInitialSync() async {
        let sut = makeSUT(activeAccount: nil)
        #expect(sut.hasCompletedInitialSync == false)

        await sut.syncAllEntriesWithRemote()

        #expect(sut.hasCompletedInitialSync == false)
        #expect(sut.isSyncing == false)
    }

    // MOB-1726: the guard above must gate ONLY the no-account no-op — a sync WITH an active account must
    // still complete the initial sync. (An over-eager fix that skipped the real sync left an account
    // with entries stuck on an infinite skeleton.)
    @Test("syncAllEntriesWithRemote with an active account completes the initial sync (MOB-1726)")
    func syncWithActiveAccountCompletesInitialSync() async {
        let account = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1",
            email: "entry@example.com",
            isActiveAccount: true
        )
        let sut = makeSUT(remote: MockEntryRepositoryAPI(), activeAccount: account)
        #expect(sut.hasCompletedInitialSync == false)

        await sut.syncAllEntriesWithRemote()

        #expect(sut.hasCompletedInitialSync == true)
        #expect(sut.isSyncing == false)
    }

    private func makeSUT(
        repo: MockEntryRepository? = nil,
        remote: MockEntryRepositoryAPI? = nil,
        syncStore: MockEntrySyncStore? = nil,
        integration: MockIntegrationService? = nil,
        goalAlert: MockGoalAlertService? = nil,
        logger: MockLoggerService? = nil,
        worker: MockEntryWorker? = nil,
        activeAccount: AccountSnapshot? = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1",
            email: "entry@example.com",
            isActiveAccount: true
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
            TestDependencyContainer.registerBase(
                logger: logger,
                keychain: keychain,
                bluetooth: bluetooth
            )
            // Register the test doubles LAST and under both the concrete type and the protocol so
            // they deterministically win over the throwaway mocks registered by reset() and over the
            // real services ServiceRegistry registers (e.g. IntegrationsService.shared) if it was
            // ever initialised earlier in the serialized run. EntryService injects these via @Injector
            // (IntegrationServiceProtocol / LoggerServiceProtocol), so without this the SUT can resolve
            // a different instance than the one the test asserts on.
            DependencyContainer.shared.register(logger)
            DependencyContainer.shared.register(logger as LoggerServiceProtocol)
            DependencyContainer.shared.register(goalAlert)
            DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)
            DependencyContainer.shared.register(integration)
            DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

            let entryLocalRepo = repo ?? MockEntryRepository()
            let entryWorker = worker ?? MockEntryWorker()
            entryWorker.backingRepo = entryLocalRepo
            let service = EntryService(
                accountService: account,
                localRepo: entryLocalRepo,
                localKVRepo: syncStore ?? MockEntrySyncStore(),
                remoteRepo: remote ?? MockEntryRepositoryAPI(),
                worker: entryWorker
            )
            // Lock the @Injector-resolved collaborators to the test doubles directly, bypassing the
            // global DependencyContainer. In the full serialized suite, leaked async work from the
            // real services built in registerDashboardConcreteDependencies (and other suites) can
            // change which instance @Injector resolves; direct assignment makes these tests
            // order-independent (they already pass in isolation).
            service.logger = logger
            service.goalAlertService = goalAlert
            service.integrationService = integration
            return service
        }
}

/// Polls `condition` on the main actor, yielding between checks, until it holds or the
/// iteration budget is exhausted. Used to await fire-and-forget background work (e.g. the
/// MOB-1433 §5c integration forward that no longer blocks `saveNewEntry`).
@MainActor
private func waitUntil(timeoutIterations: Int = 200, condition: () -> Bool) async -> Bool {
    for _ in 0..<timeoutIterations {
        if condition() { return true }
        await Task.yield()
    }
    return false
}
