// swiftlint:disable file_length
import Combine
import Foundation
import SwiftData

/*
 SwiftLint exception:
 This service intentionally aggregates all entry-related operations to keep the entry management flow discoverable and auditable in a single place. Splitting across multiple types would add indirection and risk during critical data operations.
 */
@MainActor
// swiftlint:disable:next type_body_length
final class EntryService: EntryServiceProtocol, ObservableObject {
    @Injector var logger: LoggerServiceProtocol
    @Injector var goalAlertService: GoalAlertServiceProtocol
    @Injector var integrationService: IntegrationServiceProtocol
    private let accountService: AccountServiceProtocol
    private let localRepo: EntryRepositoryProtocol
    private let localKVRepo: EntrySyncStoreProtocol
    private let remoteRepo: EntryRepositoryAPIProtocol
    private let migrationService: SQLiteMigrationService
    /// Batched off-main persistence (merge/insert/push bookkeeping/hot reads).
    /// `SwiftDataWorker` in production; injectable for tests (MOB-1433).
    private let worker: any EntryWorkerProtocol
    private let kvStorage: KvStorageServiceProtocol
    @MainActor static let shared = EntryService(accountService: AccountService.shared)

    // MARK: - Publishers ------------------------------------------------

    /// Emits each time a new entry is locally stored (create).
    /// Uses EntryNotification (Sendable) to safely pass data across actor boundaries.
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    /// Emits each time an entry is deleted locally.
    /// Uses EntryNotification (Sendable) to safely pass data across actor boundaries.
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    let tag = "EntryService"

    /// `start` value used for a full sync (no prior sync timestamp). The unified
    /// `GET /v3/entries/` only returns the complete history in sync mode when a `start`
    /// is supplied; sending no `start` triggers a 20-row cursor page instead. Using the
    /// epoch pulls every entry so History/Dashboard reflect the full server dataset.
    private static let fullSyncStart = "1970-01-01T00:00:00Z"

    @Published var isSyncing: Bool = false
    /// MOB-1726: `true` once the first full remote sync of this session has finished (success or failure).
    /// Snapshot cards use it to distinguish "no data yet because the initial sync is still running" (show a
    /// skeleton) from "genuinely no entries" (show the empty card) — so a fresh login shows skeletons instead
    /// of flashing the empty state before entries land.
    @Published private(set) var hasCompletedInitialSync: Bool = false
    @Published var lastSyncTime: Date?
    @Published var progress: ProgressSummary = .empty
    @Published var streak: Int = 0

    // MARK: - Dashboard Data (Weight)

    @Published var dailySummaries: [BathScaleWeightSummary] = []
    @Published var monthlySummaries: [BathScaleWeightSummary] = []

    // MARK: - Dashboard Data (BPM)

    @Published var bpmDailySummaries: [BathScaleWeightSummary] = []
    @Published var bpmMonthlySummaries: [BathScaleWeightSummary] = []

    // MARK: - Dashboard Data (Baby)
    /// Baby summaries keyed by babyId. Each baby profile has its own entry set.
    @Published var babyDailySummariesByProfile: [String: [BathScaleWeightSummary]] = [:]
    @Published var babyMonthlySummariesByProfile: [String: [BathScaleWeightSummary]] = [:]

    private var cancellables = Set<AnyCancellable>()
    private var lastAccountId: String?
    private var lastLoggedEntryCountByAccount: [String: Int] = [:]
    /// Tracks the active sync task so concurrent callers can await it instead of skipping.
    private var activeSyncTask: Task<Bool, Never>?
    /// Tracks an in-flight eager push (fired after a local mutation) so concurrent
    /// eager pushes coalesce onto one task and a full sync can await it before its
    /// own push, preventing the same unsynced rows from being submitted twice.
    private var pendingPushTask: Task<Void, Never>?
    /// Coalesces the behind-the-UI post-add work (`updateProgressAndStreakInternal` then
    /// `checkGoalAlerts`) so it runs BEHIND the UI after a local add. Awaiting this inline made
    /// the save's loader wait seconds on 5k-10k-entry accounts, since the recompute re-reads the
    /// whole dataset and the goal-alert reads then contend with it (MOB-1433 §5c).
    /// Successive adds cancel the pending refresh so a bulk save triggers one run, not N.
    private var progressRefreshTask: Task<Void, Never>?
    /// Tracks in-flight dashboard loads so repeated callers can piggyback on the same work.
    /// The Bool result indicates whether the load succeeded; piggybacked callers retry on failure.
    private var activeDashboardLoadTasks: [EntryType: Task<Bool, Never>] = [:]
    private var activeBabyDashboardLoadTasks: [String: Task<Bool, Never>] = [:]
    /// MOB-516: single-flight for the full-table History read. Concurrent callers (History
    /// tab-switch, entry-saved/deleted reloads, Settings, sync-completion) share ONE worker
    /// read instead of stacking 2–3 reads on the serial worker — that stacking was the ~6.6 s
    /// "stuck" History loader after adding an entry.
    private var activeMonthsLoadTasks: [EntryType: Task<[HistoryMonth], Error>] = [:]
    /// MOB-516: result cache for the grouped History months. Redundant callers that fire when the
    /// data hasn't changed — Settings' `hasEntries` check, History `onAppear`/tab re-entry, a
    /// no-op sync completing — used to each pay a fresh full-table 10k read + grouping (the
    /// sequential 2–8 s reads that hung the UI on touch right after login). A warm cache serves
    /// them instantly. Weight (`.scale`) months embed RAW weight values (the view formats per-unit),
    /// so unit changes do NOT invalidate this — only genuine entry-data changes do. Invalidation
    /// bumps `entryDataRevision` and clears the cache; see `invalidateMonthsCache()`.
    private var monthsCacheByType: [EntryType: [HistoryMonth]] = [:]
    /// Monotonic marker bumped on every entry-data change. `performGetMonthsAll` snapshots it before
    /// its off-main read and only writes the result to the cache if it is unchanged afterward — so a
    /// mutation that lands mid-read never poisons the cache with pre-mutation data.
    private var entryDataRevision = 0
    private var summaryCacheByEntryType: [EntryType: EntrySummaryCacheEntry] = [:]
    private var babySummaryCacheByProfile: [String: EntryBabySummaryCacheEntry] = [:]
    /// Resolved lazily and non-fatally (unlike `@Injector`) so unit tests that construct
    /// `EntryService` directly without registering a `BabyService` still sync entries — baby
    /// profile sync is simply skipped when the service isn't registered.
    private var babyService: BabyServiceProtocol? {
        DependencyContainer.shared.resolve(BabyServiceProtocol.self)
    }
    private var streakCacheByEntryType: [EntryType: EntryStreakCacheEntry] = [:]
    private var progressCacheByEntryType: [EntryType: EntryProgressCacheEntry] = [:]

    @MainActor
    init(
        accountService: AccountServiceProtocol,
        localRepo: EntryRepositoryProtocol? = nil,
        localKVRepo: EntrySyncStoreProtocol? = nil,
        remoteRepo: EntryRepositoryAPIProtocol? = nil,
        migrationService: SQLiteMigrationService? = nil,
        kvStorage: KvStorageServiceProtocol? = nil,
        worker: (any EntryWorkerProtocol)? = nil
    ) {
        self.accountService = accountService
        self.localRepo = localRepo ?? EntryRepository()
        self.localKVRepo = localKVRepo ?? EntryRepositoryLocal()
        self.remoteRepo = remoteRepo ?? EntryRepositoryAPI()
        let resolvedWorker = worker ?? SwiftDataWorker(modelContainer: PersistenceController.shared.container)
        self.worker = resolvedWorker
        // The migration shares the worker so its batch inserts run off main too.
        self.migrationService = migrationService ?? SQLiteMigrationService(entryWorker: resolvedWorker)
        self.kvStorage = kvStorage ?? KvStorageService.shared

        startObserving()
    }

    /// Wires up the reactive subscriptions this service relies on: active-account
    /// changes (reload on switch), months-cache invalidation on any entry mutation,
    /// and the debounced eager push after a local mutation. Extracted from `init`
    /// so the initializer body stays within the lint length limit.
    private func startObserving() {
        Task { @MainActor in
            if let concreteAccountService = self.accountService as? AccountService {
                concreteAccountService.$activeAccount
                    .map { $0?.accountId }
                    .removeDuplicates()
                    .dropFirst()
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] accountId in
                        guard let self = self else { return }
                        Task { @MainActor in
                            let accountChanged = self.lastAccountId != nil && self.lastAccountId != accountId
                            let previousAccountId = self.lastAccountId
                            self.lastAccountId = accountId

                            self.logger.log(
                                level: .info,
                                tag: "AcctFlowDebug",
                                message: "[EntrySvc] activeAccount emit. prev=\(previousAccountId ?? "nil"), "
                                    + "new=\(accountId ?? "nil"), willReload=\(accountChanged && accountId != nil)"
                            )
                            if accountChanged, accountId != nil {
                                self.invalidateSummaryCaches()
                                self.babySummaryCacheByProfile.removeAll()
                                try? await self.clearLastSyncTimestamp()
                                await self.syncAllEntriesWithRemote()
                                await self.loadDashboardData(entryType: .scale)
                                self.logger.log(
                                    level: .info,
                                    tag: "AcctFlowDebug",
                                    message: "[EntrySvc] reloaded entries for accountId=\(accountId ?? "nil")"
                                )
                            }
                        }
                    }
                    .store(in: &self.cancellables)
            }

            // MOB-516: invalidate the grouped-months cache the instant any entry changes.
            // entrySaved/entryDeleted cover every local mutation AND the remote-merge fan-out;
            // delivery is synchronous on the main actor (no `receive(on:)`), so the cache is
            // already stale-cleared before any reactive reload's `getMonthsAll` runs. (Updates-only
            // remote merges emit nothing — `performSync` invalidates directly on `hadChanges`.)
            Publishers.Merge(self.entrySaved, self.entryDeleted)
                .sink { [weak self] _ in
                    self?.invalidateMonthsCache()
                }
                .store(in: &self.cancellables)

            // Eager push after a local mutation. Every mutation (weight/BP/baby
            // create or delete) emits on entrySaved/entryDeleted, so subscribing
            // here covers all of them — and any future mutation — in one place.
            // Debounce a short window so an edit (delete + add → two emissions)
            // collapses into a single push, then flush unsynced rows to the server
            // right away instead of waiting for the next Dashboard/History/foreground
            // sync. This shrinks the window where a just-made add/edit/delete would
            // be lost on app kill/uninstall (MOB-1433). The push is push-only,
            // coalesced, and offline-guarded — see `pushPendingEntries()`.
            Publishers.Merge(self.entrySaved, self.entryDeleted)
                .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
                .sink { [weak self] _ in
                    guard let self else { return }
                    Task { @MainActor in
                        await self.pushPendingEntries()
                    }
                }
                .store(in: &self.cancellables)
        }
    }

    // MARK: - Helper

    private func getAccountId() throws -> String {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw NSError(domain: "EntryService", code: 401, userInfo: [NSLocalizedDescriptionKey: "No active account"])
        }
        return accountId
    }

    /// Reads goal initial weight on MainActor to avoid crossing SwiftData model objects between executors.
    private func getGoalInitialWeight() async -> Int? {
        await MainActor.run {
            accountService.activeAccount?.initialWeight.map(Int.init)
        }
    }

    // MARK: - CRUD

    func clearAllData() async {
        do {
            try await localRepo.deleteAllEntries()
            logger.log(level: .info, tag: tag, message: "Cleared all local entry data")
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to clear local entry data: \(error.localizedDescription)"
            )
        }
    }

    /// Clears the last sync timestamp for the current user.
    func clearLastSyncTimestamp() async throws {
        let accountId = try getAccountId()
        try await localKVRepo.clearLastSyncTimestamp(accountId: accountId)
    }

    func saveNewEntry(_ entry: Entry) async throws {
        entry.isSynced = false
        entry.operationType = OperationType.create.rawValue
        entry.attempts = 0

        let entrySource = entry.scaleEntry?.source ?? "manual"
        do {
            try await localRepo.saveEntry(entry)
            logger.log(
                level: .info,
                tag: tag,
                message: "New entry saved locally: entryId=\(entry.id.uuidString), accountId=\(entry.accountId), source=\(entrySource)",
                data: entry.toOperationDTO()
            )

            try await handleEntryAdded(entry)
            // Goal alerts are evaluated BEHIND the UI via scheduleProgressAndStreakRefresh()
            // (invoked from handleEntryAdded), not awaited here. Awaiting them inline put their
            // main-context reads (getLatestEntry/getEntryCount) on the save's critical path,
            // where they were starved for ~1.8s by the concurrent full-dataset progress/streak
            // worker read on the shared store — delaying the save's success toast by seconds on
            // 5k-10k-entry accounts once the HealthKit forward was moved off the path (MOB-1433 §5c).
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to save new entry: entryId=\(entry.id.uuidString), accountId=\(entry.accountId), "
                    + "source=\(entrySource), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    func saveNewEntries(_ entries: [Entry]) async throws {
        logger.log(level: .info, tag: tag, message: "Bulk entry save requested: count=\(entries.count)")
        do {
            for entry in entries {
                entry.isSynced = false
                try await localRepo.saveEntry(entry)
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Bulk entry item saved locally: entryId=\(entry.id.uuidString), accountId=\(entry.accountId)",
                    data: entry.toOperationDTO()
                )
                try await handleEntryAdded(entry)
            }

            logger.log(level: .info, tag: tag, message: "Bulk entry save completed: count=\(entries.count)")
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Bulk entry save failed: count=\(entries.count), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    func deleteEntry(_ entry: Entry) async throws {
        let deletedEntry = entry
        deletedEntry.operationType = OperationType.delete.rawValue
        deletedEntry.isSynced = false

        logger.log(level: .info, tag: tag, message: "Entry delete requested: entryId=\(entry.id.uuidString), accountId=\(entry.accountId)")
        do {
            try await localRepo.updateEntry(deletedEntry)
            try await handleEntryDeleted(deletedEntry)
            logger.log(
                level: .info,
                tag: tag,
                message: "Entry delete queued for sync: entryId=\(entry.id.uuidString), accountId=\(entry.accountId)"
            )
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Entry delete failed: entryId=\(entry.id.uuidString), accountId=\(entry.accountId), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    func deleteEntry(entryId: UUID) async throws {
        guard let entry = try await localRepo.fetchEntry(byId: entryId.uuidString) else {
            logger.log(
                level: .error,
                tag: tag,
                message: "Entry delete failed — not found: entryId=\(entryId.uuidString)"
            )
            return
        }
        try await deleteEntry(entry)
    }

    func assignBabyEntry(entryId: UUID, babyId: String) async throws {
        guard let entry = try await localRepo.fetchEntry(byId: entryId.uuidString) else {
            logger.log(
                level: .error,
                tag: tag,
                message: "Baby entry assign failed — not found: entryId=\(entryId.uuidString)"
            )
            return
        }
        entry.babyEntry?.babyId = babyId
        try await localRepo.updateEntry(entry)
        logger.log(
            level: .info,
            tag: tag,
            message: "Baby entry assigned: entryId=\(entryId.uuidString), babyId=\(babyId)"
        )
        let notification = EntryNotification(from: entry)
        entrySaved.send(notification)
    }

    func remapBabyId(from oldId: String, to newId: String) async {
        guard oldId != newId else { return }
        guard let accountId = try? getAccountId() else { return }
        do {
            // Baby entries are few per account; a full fetch + in-memory filter is cheaper than
            // maintaining a dedicated predicate path and keeps the FK rewrite on the main actor.
            let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: nil)
            var remapped = 0
            for entry in entries where entry.babyEntry?.babyId == oldId {
                entry.babyEntry?.babyId = newId
                try await localRepo.updateEntry(entry)
                remapped += 1
            }
            if remapped > 0 {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Remapped babyId on \(remapped) entr\(remapped == 1 ? "y" : "ies"): \(oldId) -> \(newId)"
                )
            }
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to remap babyId \(oldId) -> \(newId): \(error.localizedDescription)"
            )
        }
    }

    // MARK: - Snapshot Queries

    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? {
        guard let entry = try await localRepo.fetchEntry(byId: id.uuidString) else {
            return nil
        }
        return entry.toSnapshot()
    }

    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] {
        // MOB-1433: read + snapshot-map off the main actor via the worker.
        // History/Content open this on a large account; doing the full-table
        // fetch and per-row snapshot build on main was a re-open stutter source.
        let accountId = try getAccountId()
        return try await worker.fetchEntrySnapshots(accountId: accountId, operationType: OperationType.create.rawValue)
    }

    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType = .scale) async throws -> [EntrySnapshot] {
        let entries = try await getEntries(forMonth: month, entryType: entryType)
        return entries.map { $0.toSnapshot() }
    }

    // MARK: - Query

    func getEntry(byId id: UUID) async throws -> Entry? {
        return try await localRepo.fetchEntry(byId: id.uuidString)
    }

    func getAllEntries() async throws -> [Entry] {
        let accountId = try getAccountId()
        return try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
    }

    func getAllEntriesAsSnapshots() async throws -> [EntrySnapshot] {
        let entries = try await getAllEntries()
        return entries.map { $0.toSnapshot() }
    }

    /// Returns all entries as DTOs with DTO conversion done on background thread.
    /// Use this instead of getAllEntries() when you only need to read entry data
    /// to avoid blocking the main thread with toOperationDTO() calls.
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] {
        // MOB-1433: fetch + DTO-map off the main actor via the worker.
        // MOB-516: the worker is a stock `@ModelActor`; its `DefaultSerialModelExecutor`
        // runs the job on the CALLING thread, so awaiting it directly from this `@MainActor`
        // method ran the full-table read on the MAIN thread (cold-login hang). SwiftData
        // rejects a custom background executor at runtime ("Unexpected executor"), so we
        // enqueue the worker call from a DETACHED (background) task instead — the stock
        // executor then runs it off-main. Verify via trace: `fetchEntriesAsDTO` off Main.
        let accountId = try getAccountId()
        let worker = self.worker
        return try await Task.detached(priority: .userInitiated) {
            try await worker.fetchEntriesAsDTO(accountId: accountId, operationType: OperationType.create.rawValue)
        }.value
    }

    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool {
        let accountId = try getAccountId()
        return try await localRepo.checkEntryTimestampExists(forUserId: accountId, entryTimestamp: entryTimestamp)
    }

    func getEntryCount() async throws -> Int {
        // MOB-516: route the COUNT through the `@ModelActor` worker via a DETACHED task so it
        // runs off-main. The `@MainActor` `EntryRepository` ran `fetchCount` on the main thread,
        // where it took the SQLite store lock and stalled behind the concurrent cold-login
        // merge/read on the same store — a blocked-on-lock main-thread stall (severe hang, ~0% CPU).
        // Every caller (progress refresh, goal-card gating, sync bookkeeping) benefits at once.
        let accountId = try getAccountId()
        let worker = self.worker
        return try await Task.detached(priority: .userInitiated) {
            try await worker.fetchEntryCount(accountId: accountId)
        }.value
    }

    /// Local entry count for sync bookkeeping — logged, and nil on failure (MOB-516). A silent
    /// count error must not masquerade as an empty DB, so it's logged; returning nil preserves the
    /// caller's conservative default (`nil == 0` is false → the empty-DB full-resync reset is skipped).
    private func localEntryCountOrNil() async -> Int? {
        do {
            return try await getEntryCount()
        } catch {
            logger.log(level: .error, tag: tag, message: "Sync: local entry count failed; keeping incremental: \(error.localizedDescription)")
            return nil
        }
    }

    /// Resolves the sync-window start for `accountId` (MOB-516). An empty local DB clears any stale
    /// last-sync timestamp so the server replays full history in sync mode; otherwise it resumes
    /// from the stored timestamp (epoch on first sync). A nil count leaves the timestamp untouched.
    private func resolveSyncStart(accountId: String) async -> String {
        let localEntryCount = await localEntryCountOrNil()
        var lastSyncTimestamp = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)
        if localEntryCount == 0 {
            try? await localKVRepo.clearLastSyncTimestamp(accountId: accountId)
            lastSyncTimestamp = nil
        }
        return lastSyncTimestamp ?? Self.fullSyncStart
    }

    func getOldestEntry() async throws -> Entry? {
        let accountId = try getAccountId()
        return try await localRepo.fetchOldestEntry(forUserId: accountId)
    }

    func getLatestEntry() async throws -> Entry? {
        let accountId = try getAccountId()
        return try await localRepo.fetchLatestEntry(forUserId: accountId)
    }

    func getEntries(lastNDays: Int, entryType: EntryType = .scale) async throws -> [Entry] {
        let accountId = try getAccountId()
        let entries = try await localRepo.fetchEntries(lastNDays: lastNDays, userId: accountId)
        return entries.filter { matchesEntryType($0, entryType: entryType) }
    }

    func getEntries(forMonth month: String, entryType: EntryType = .scale) async throws -> [Entry] {
        let accountId = try getAccountId()
        let entries = try await localRepo.fetchEntries(forMonth: month, userId: accountId)
        return entries.filter { $0.operationType == OperationType.create.rawValue && matchesEntryType($0, entryType: entryType) }
    }

    func getEntries(forDay day: String) async throws -> [Entry] {
        let accountId = try getAccountId()
        let entries = try await localRepo.fetchEntries(forDay: day, userId: accountId)
        return entries.filter { $0.operationType == OperationType.create.rawValue }
    }

    // MARK: - Month/History

    /// MOB-516: clears the grouped-months result cache. Called on every entry-data change (the
    /// entrySaved/entryDeleted sink in `init`, and directly from `performSync` for updates-only
    /// merges that emit nothing). Bumping `entryDataRevision` also cancels any in-flight read's
    /// pending cache write (`performGetMonthsAll`), so a mutation mid-read can't cache stale data.
    private func invalidateMonthsCache() {
        entryDataRevision &+= 1
        monthsCacheByType.removeAll()
    }

    func getMonthsAll(entryType: EntryType = .scale) async throws -> [HistoryMonth] {
        // MOB-516: serve a warm cache instantly. Redundant callers (Settings `hasEntries`, History
        // re-entry, a no-op sync completing) then cost nothing instead of a fresh full-table read.
        // The cache is cleared the moment any entry changes (see `invalidateMonthsCache()`).
        if let cached = monthsCacheByType[entryType] {
            return cached
        }
        // MOB-516: single-flight so concurrent callers share ONE read instead of stacking
        // full-table reads on the serial worker. Freshness holds because History triggers its
        // reads AFTER a mutation commits (and its reload driver reruns once post-mutation), so
        // a piggybacked in-flight read reflects the current state.
        if let existing = activeMonthsLoadTasks[entryType] {
            return try await existing.value
        }
        let task = Task<[HistoryMonth], Error> { [weak self] in
            guard let self else { return [] }
            return try await self.performGetMonthsAll(entryType: entryType)
        }
        activeMonthsLoadTasks[entryType] = task
        defer {
            if activeMonthsLoadTasks[entryType] == task {
                activeMonthsLoadTasks[entryType] = nil
            }
        }
        return try await task.value
    }

    private func performGetMonthsAll(entryType: EntryType) async throws -> [HistoryMonth] {
        // MOB-1433 read-path follow-up: fetch + group off the main actor.
        // MOB-516: the worker fetch was awaited DIRECTLY from this `@MainActor` method, so
        // the stock `@ModelActor` `DefaultSerialModelExecutor` ran the full-table read on
        // the MAIN thread (the single biggest cold-login hang cost). SwiftData rejects a
        // custom background executor at runtime, so we enqueue the worker call from a
        // DETACHED (background) task — the stock executor then runs the fetch off-main —
        // and group in the same task. Verify via trace: `fetchAllEntryData` off Main.
        let accountId = try getAccountId()
        let worker = self.worker
        // Snapshot the data revision BEFORE the off-main read; only cache the result if it is still
        // current afterward (no mutation landed mid-read) — otherwise this caller gets its result
        // but the cache stays empty so the next call re-reads fresh (MOB-516).
        let revisionAtStart = entryDataRevision
        let result = try await Task.detached(priority: .userInitiated) {
            let allEntries = try await worker.fetchAllEntryData(
                accountId: accountId,
                operationType: OperationType.create.rawValue
            )
            return Self.buildMonthsAll(from: allEntries, entryType: entryType)
        }.value
        if entryDataRevision == revisionAtStart {
            monthsCacheByType[entryType] = result
        }
        return result
    }

    /// Groups pre-fetched entry data into monthly summaries. `nonisolated` so it runs
    /// off the main actor from `getMonthsAll`'s detached task (see there).
    private nonisolated static func buildMonthsAll(from allEntries: [EntryData], entryType: EntryType) -> [HistoryMonth] {
        let entries = allEntries.filter { entry in
            // Mirrors the instance `matchesEntryType(_:entryType:)`: an empty entryType
            // is legacy weight data and counts as `.scale`.
            entry.entryType.isEmpty ? (entryType == .scale) : (entry.entryType == entryType.rawValue)
        }
        // Group by YYYY-MM prefix, converting UTC timestamps to local timezone
        let grouped = Dictionary(grouping: entries) { DateTimeTools.getLocalMonthStringFromUTCDate($0.entryTimestamp) }

        guard let validMonthRegex = try? NSRegularExpression(pattern: "^\\d{4}-\\d{2}$") else {
            return []
        }

        var result: [HistoryMonth] = []
        for (monthKey, monthEntries) in grouped {
            // Skip keys that are not in YYYY-MM format (e.g., malformed keys)
            guard validMonthRegex.firstMatch(in: monthKey, range: NSRange(location: 0, length: monthKey.count)) != nil else { continue }

            result.append(Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries))
        }

        // Sort descending by month key
        return result.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    func getMonthDetail(month: String, entryType: EntryType = .scale) async throws -> [Entry] {
        return try await getEntries(forMonth: month, entryType: entryType)
    }

    func getMonthYear() async throws -> [HistoryMonth] {
        let accountId = try getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        // Get entries from last 365 days (matching TypeScript: entry."entryTimestamp" >= getIntervalDatetimeIsoString(365))
        let calendar = Calendar.current
        let now = Date()
        let oneYearAgo = calendar.date(byAdding: .day, value: -365, to: now) ?? now

        // Filter entries from last 365 days
        let filteredEntries = entries.filter { entry in
            guard let entryDate = DateTimeTools.parse(entry.entryTimestamp) else { return false }
            return entryDate >= oneYearAgo && entryDate <= now
        }

        // Group by month (YYYY-MM)
        let grouped = Dictionary(grouping: filteredEntries) { entry -> String in
            DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
        }

        // Build HistoryMonth for each month that has entries, sorted by timestamp DESC (newest first)
        var result: [HistoryMonth] = []
        for (monthKey, monthEntries) in grouped {
            guard !monthEntries.isEmpty else { continue }
            result.append(Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries))
        }

        // Sort descending by entryTimestamp (newest first, matching TypeScript ORDER BY DESC)
        return result.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    // MARK: - Progress/Stats

    // swiftlint:disable:next function_body_length
    func getProgress(entryType: EntryType = .scale) async throws -> Progress {
        let accountId = try getAccountId()

        // Reuse the worker so progress refreshes do not keep creating fresh model contexts.
        // MOB-516: enqueue from a detached (background) task so the stock @ModelActor executor
        // runs this full-table streak read off-main — awaited directly from @MainActor it ran on
        // the MAIN thread (~18% of the cold-login hang). Verify via trace: `fetchProgressData` off Main.
        let progressWorker = self.worker
        let fetchResult = try await Task.detached(priority: .userInitiated) {
            try await progressWorker.fetchProgressData(accountId: accountId)
        }.value
        let goalInitial = await getGoalInitialWeight()
        let datasetSignature = Self.makeEntryDataSignature(fetchResult.allEntries)

        if let cached = progressCacheByEntryType[entryType],
           cached.datasetSignature == datasetSignature,
           cached.goalInitialWeight == goalInitial {
            return cached.progress
        }

        guard let latestData = fetchResult.latestEntry else {
            throw NSError(domain: "EntryService", code: 404, userInfo: [NSLocalizedDescriptionKey: "No entries found"])
        }

        // All data is already extracted - safe to use across actors
        let latestWeight = latestData.weight ?? 0
        let weekStartWeight = fetchResult.weekStartEntry?.weight
        let monthStartWeight = fetchResult.monthStartEntry?.weight
        let firstEntryWeight = fetchResult.firstEntry?.weight

        // DTOs already extracted by worker
        let latestDTO = latestData.toDTO()
        let weekDTO = fetchResult.weekStartEntry?.toDTO()
        let monthDTO = fetchResult.monthStartEntry?.toDTO()

        let weekDelta = latestWeight - (weekStartWeight ?? latestWeight)
        let monthDelta = latestWeight - (monthStartWeight ?? latestWeight)

        // Compute the derived history/streak data off the main actor so dashboard refreshes
        // don't block while rebuilding the same graph-ready structures.
        let monthSeriesTask = Task.detached(priority: .userInitiated) {
            Self.getMonthYear(from: fetchResult.allEntries)
        }
        let streakTask = Task.detached(priority: .userInitiated) {
            Self.computeStreak(entryType: entryType, from: fetchResult.allEntries)
        }

        let monthSeries = await monthSeriesTask.value
        let yearDeltaResult = try await calculateYearDelta(latestWeight: latestWeight, monthStartWeight: monthStartWeight, monthSeries: monthSeries)
        let yearDelta = yearDeltaResult.yearDelta
        let yearStartDTO = yearDeltaResult.yearStartDTO
        let yearKey = yearDeltaResult.yearKey

        let initialWeight: Int?
        if let goalInitial, goalInitial > 0 {
            initialWeight = goalInitial
        } else {
            initialWeight = firstEntryWeight
        }
        let totalDelta = latestWeight - (initialWeight ?? latestWeight)

        let streak = await streakTask.value

        logger.log(
            level: .debug,
            tag: tag,
            message: "Progress(year): latest=\(latestWeight), yearKey=\(yearKey), yearDelta=\(yearDelta)"
        )

        let progress = Progress(
            count: fetchResult.totalCount,
            currentStreak: streak.current,
            initYear: yearStartDTO,
            initMonth: monthDTO,
            initWeek: weekDTO,
            initWt: Double(firstEntryWeight ?? 0),
            latest: latestDTO,
            longestStreak: streak.max,
            month: monthDelta,
            percent: nil,
            total: Double(totalDelta),
            week: weekDelta,
            year: yearDelta
        )

        progressCacheByEntryType[entryType] = EntryProgressCacheEntry(
            datasetSignature: datasetSignature,
            goalInitialWeight: goalInitial,
            progress: progress
        )

        return progress
    }

    // MARK: - Modular Year Delta Calculation

    private struct YearDeltaResult {
        let yearDelta: Int
        let yearStartDTO: BathScaleOperationDTO?
        let yearKey: String
    }

    private func calculateYearDelta(
        latestWeight: Int, monthStartWeight: Int?, monthSeries: [HistoryMonth]
    ) async throws -> YearDeltaResult {
        guard let initYearMonth = monthSeries.last, let initYearWeight = initYearMonth.weight else {
            return YearDeltaResult(yearDelta: 0, yearStartDTO: nil, yearKey: "")
        }

        guard let thirtyDaysAgo = Calendar.current.date(byAdding: .day, value: -30, to: Date()) else {
            logger.log(level: .error, tag: tag, message: "Failed to calculate date 30 days ago for year delta calculation.")
            return YearDeltaResult(yearDelta: 0, yearStartDTO: nil, yearKey: "")
        }
        let (initYearDate, yearKey) = parseYearKeyAndDate(from: initYearMonth.entryTimestamp, id: initYearMonth.id)
        let isWithin30Days = isoString(from: initYearDate) >= isoString(from: thirtyDaysAgo)
        let initYearWeightStored = Int(round(initYearWeight))

        let delta = isWithin30Days
            ? latestWeight - (monthStartWeight ?? latestWeight)
            : latestWeight - initYearWeightStored

        let yearAvgWeight = Int(round(initYearWeight))
        let yearStartDTO = makeYearDTO(
            key: yearKey, avgWeight: yearAvgWeight, accountId: try getAccountId()
        )

        return YearDeltaResult(yearDelta: delta, yearStartDTO: yearStartDTO, yearKey: yearKey)
    }

    private func parseYearKeyAndDate(from timestamp: String, id: String) -> (Date, String) {
        let fmt = DateTimeTools.formatter("yyyy-MM")
        if let date = fmt.date(from: timestamp) {
            return (date, id)
        }
        // Fallback, parse manually
        let comps = timestamp.split(separator: "-")
        if comps.count == 2, let year = Int(comps[0]), let month = Int(comps[1]) {
            var dc = DateComponents(); dc.year = year; dc.month = month; dc.day = 1
            if let dt = Calendar.current.date(from: dc) { return (dt, timestamp) }
        }
        return (Date(), timestamp)
    }

    private static let isoDayFormatter: DateFormatter = {
        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        return df
    }()

    private nonisolated static let monthSummaryDateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        return df
    }()

    private func isoString(from date: Date) -> String {
        return Self.isoDayFormatter.string(from: date)
    }

    private nonisolated static func makeDTOSignature(_ dtos: [BathScaleOperationDTO]) -> Int {
        var hasher = Hasher()
        hasher.combine(dtos.count)
        for dto in dtos {
            hasher.combine(dto.accountId)
            hasher.combine(dto.entryTimestamp)
            hasher.combine(dto.serverTimestamp)
            hasher.combine(dto.entryType)
            hasher.combine(dto.operationType)
            hasher.combine(dto.weight)
            hasher.combine(dto.bodyFat)
            hasher.combine(dto.muscleMass)
            hasher.combine(dto.water)
            hasher.combine(dto.bmi)
            hasher.combine(dto.bmr)
            hasher.combine(dto.metabolicAge)
            hasher.combine(dto.proteinPercent)
            hasher.combine(dto.pulse)
            hasher.combine(dto.skeletalMusclePercent)
            hasher.combine(dto.subcutaneousFatPercent)
            hasher.combine(dto.visceralFatLevel)
            hasher.combine(dto.boneMass)
            hasher.combine(dto.impedance)
            hasher.combine(dto.systolic)
            hasher.combine(dto.diastolic)
            hasher.combine(dto.meanArterial)
        }
        return hasher.finalize()
    }

    private nonisolated static func makeBabySnapshotSignature(_ snapshots: [BabyEntrySnapshot]) -> Int {
        var hasher = Hasher()
        hasher.combine(snapshots.count)
        for snapshot in snapshots {
            hasher.combine(snapshot.entryTimestamp)
            hasher.combine(snapshot.babyId)
            hasher.combine(snapshot.weightDecigrams)
            hasher.combine(snapshot.lengthMm)
        }
        return hasher.finalize()
    }

    private nonisolated static func makeEntryDataSignature(_ entries: [EntryData]) -> Int {
        var hasher = Hasher()
        hasher.combine(entries.count)
        for entry in entries {
            hasher.combine(entry.accountId)
            hasher.combine(entry.entryTimestamp)
            hasher.combine(entry.serverTimestamp)
            hasher.combine(entry.operationType)
            hasher.combine(entry.entryType)
            hasher.combine(entry.isSynced)
            hasher.combine(entry.weight)
            hasher.combine(entry.bodyFat)
            hasher.combine(entry.muscleMass)
            hasher.combine(entry.water)
            hasher.combine(entry.bmi)
            hasher.combine(entry.bmr)
            hasher.combine(entry.metabolicAge)
            hasher.combine(entry.proteinPercent)
            hasher.combine(entry.pulse)
            hasher.combine(entry.skeletalMusclePercent)
            hasher.combine(entry.subcutaneousFatPercent)
            hasher.combine(entry.visceralFatLevel)
            hasher.combine(entry.boneMass)
            hasher.combine(entry.impedance)
        }
        return hasher.finalize()
    }

    private func makeYearDTO(key: String, avgWeight: Int, accountId: String) -> BathScaleOperationDTO? {
        guard !key.isEmpty else { return nil }
        let date = DateTimeTools.formatter("yyyy-MM").date(from: key) ?? Date()
        return BathScaleOperationDTO(
            accountId: accountId,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: DateTimeTools.isoFormatter().string(from: date),
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: OperationType.create.rawValue,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: "monthly",
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: Double(avgWeight)
        )
    }

    func getStreak(entryType: EntryType = .scale) async throws -> Streak {
        let dtos = try await getAllEntriesAsDTO()
        let cachedSignature = streakCacheByEntryType[entryType]?.datasetSignature
        let result = await Task.detached(priority: .userInitiated) {
            let filtered = dtos.filter { dto in
                let type = dto.entryType ?? ""
                if type.isEmpty { return entryType == .scale }
                return type == entryType.rawValue
            }
            let signature = Self.makeDTOSignature(filtered)
            if signature == cachedSignature {
                return (signature, Optional<Streak>.none)
            }
            let streak = Self.computeStreak(from: filtered)
            return (signature, Optional(streak))
        }.value

        if let streak = result.1 {
            streakCacheByEntryType[entryType] = EntryStreakCacheEntry(datasetSignature: result.0, streak: streak)
            return streak
        }

        if let cached = streakCacheByEntryType[entryType], cached.datasetSignature == result.0 {
            return cached.streak
        }

        let streak = Self.computeStreak(from: dtos.filter { dto in
            let type = dto.entryType ?? ""
            if type.isEmpty { return entryType == .scale }
            return type == entryType.rawValue
        })
        streakCacheByEntryType[entryType] = EntryStreakCacheEntry(datasetSignature: result.0, streak: streak)
        return streak
    }

    // PERFORMANCE: Overloads that accept pre-fetched EntryData to avoid redundant full-table fetches.
    // Called from getProgress which already has allEntries from fetchProgressData.

    private nonisolated static func getMonthYear(from allEntries: [EntryData]) -> [HistoryMonth] {
        let calendar = Calendar.current
        let now = Date()
        let oneYearAgo = calendar.date(byAdding: .day, value: -365, to: now) ?? now

        let filteredEntries = allEntries.filter { entry in
            guard let entryDate = DateTimeTools.parse(entry.entryTimestamp) else { return false }
            return entryDate >= oneYearAgo && entryDate <= now
        }

        let grouped = Dictionary(grouping: filteredEntries) { entry -> String in
            DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
        }

        var result: [HistoryMonth] = []
        for (monthKey, monthEntries) in grouped {
            guard !monthEntries.isEmpty else { continue }
            result.append(Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries))
        }

        return result.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    private nonisolated static func buildHistoryMonth(monthKey: String, monthEntries: [EntryData]) -> HistoryMonth {
        let weightPairs = monthEntries.compactMap { entry -> String? in
            guard let weight = entry.weight else { return nil }
            return "\(weight)|\(entry.entryTimestamp)"
        }
        let weightsConcat = weightPairs.joined(separator: ",")

        let weightValues = monthEntries.compactMap { $0.weight }.filter { $0 > 0 }.map(Double.init)
        let avgWeight: Double? = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count)
        let minWeight = weightValues.min()
        let maxWeight = weightValues.max()

        let sortedByTime = monthEntries.sorted { $0.entryTimestamp < $1.entryTimestamp }
        let firstWeight = sortedByTime.first?.weight
        let lastWeight = sortedByTime.last?.weight
        let change: String? = {
            guard let first = firstWeight, let last = lastWeight else { return nil }
            return String(format: "%.1f", Double(last - first))
        }()

        return HistoryMonth(
            id: monthKey,
            weight: avgWeight,
            entryTimestamp: monthKey,
            count: monthEntries.count,
            weights: weightsConcat,
            change: change,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            date: nil,
            time: nil,
            month: String(monthKey.suffix(2)),
            year: String(monthKey.prefix(4)),
            min: minWeight,
            max: maxWeight
        )
    }

    private nonisolated static func computeStreak(entryType: EntryType = .scale, from allEntries: [EntryData]) -> Streak {
        let entries = allEntries.filter { entry in
            let type = entry.entryType
            if type.isEmpty { return entryType == .scale }
            return type == entryType.rawValue
        }
        let calendar = Calendar.current

        let uniqueDayStrings = Set(entries.map { DateTimeTools.getLocalDateStringFromUTCDate($0.entryTimestamp) })
            .filter { $0 != DateTimeTools.invalidString && !$0.isEmpty }
        let uniqueDaysDescending: [Date] = uniqueDayStrings
            .compactMap { DateTimeTools.formatter("yyyy-MM-dd").date(from: $0) }
            .map { calendar.startOfDay(for: $0) }
            .sorted(by: >)

        guard !uniqueDaysDescending.isEmpty else { return Streak(current: 0, max: 0) }

        let uniqueDaysAscending = uniqueDaysDescending.sorted()

        let todayStart = calendar.startOfDay(for: Date())
        guard let yesterdayStart = calendar.date(byAdding: .day, value: -1, to: todayStart) else {
            return Streak(current: 0, max: 0)
        }

        func isSameDay(_ firstDate: Date, _ secondDate: Date) -> Bool {
            calendar.isDate(firstDate, inSameDayAs: secondDate)
        }

        var currentStreak = 0
        var dateToCheck: Date

        if let first = uniqueDaysDescending.first, isSameDay(first, todayStart) {
            currentStreak = 1
            dateToCheck = yesterdayStart
        } else if let first = uniqueDaysDescending.first, isSameDay(first, yesterdayStart) {
            currentStreak = 1
            guard let twoDaysAgo = calendar.date(byAdding: .day, value: -1, to: yesterdayStart) else {
                return Streak(current: 1, max: Self.computeLongestStreak(from: uniqueDaysAscending, calendar: calendar))
            }
            dateToCheck = twoDaysAgo
        } else {
            return Streak(current: 0, max: Self.computeLongestStreak(from: uniqueDaysAscending, calendar: calendar))
        }

        for day in uniqueDaysDescending.dropFirst() {
            if isSameDay(day, dateToCheck) {
                guard let previousDate = calendar.date(byAdding: .day, value: -1, to: dateToCheck) else { break }
                currentStreak += 1
                dateToCheck = previousDate
            } else {
                break
            }
        }

        let longestStreak = Self.computeLongestStreak(from: uniqueDaysAscending, calendar: calendar)
        return Streak(current: currentStreak, max: longestStreak)
    }

    private nonisolated static func computeStreak(from dtos: [BathScaleOperationDTO]) -> Streak {
        let calendar = Calendar.current
        let uniqueDayStrings = Set(
            dtos.compactMap { dto -> String? in
                guard let timestamp = dto.entryTimestamp else { return nil }
                return DateTimeTools.getLocalDateStringFromUTCDate(timestamp)
            }
        )
        .filter { $0 != DateTimeTools.invalidString && !$0.isEmpty }

        let uniqueDaysDescending: [Date] = uniqueDayStrings
            .compactMap { DateTimeTools.formatter("yyyy-MM-dd").date(from: $0) }
            .map { calendar.startOfDay(for: $0) }
            .sorted(by: >)

        guard !uniqueDaysDescending.isEmpty else { return Streak(current: 0, max: 0) }

        let uniqueDaysAscending = uniqueDaysDescending.sorted()
        let todayStart = calendar.startOfDay(for: Date())
        let yesterdayStart = calendar.date(byAdding: .day, value: -1, to: todayStart) ?? todayStart

        func isSameDay(_ firstDate: Date, _ secondDate: Date) -> Bool {
            calendar.isDate(firstDate, inSameDayAs: secondDate)
        }

        var currentStreak = 0
        var dateToCheck: Date

        if let first = uniqueDaysDescending.first, isSameDay(first, todayStart) {
            currentStreak = 1
            dateToCheck = yesterdayStart
        } else if let first = uniqueDaysDescending.first, isSameDay(first, yesterdayStart) {
            currentStreak = 1
            dateToCheck = calendar.date(byAdding: .day, value: -1, to: yesterdayStart) ?? yesterdayStart
        } else {
            return Streak(current: 0, max: computeLongestStreak(from: uniqueDaysAscending, calendar: calendar))
        }

        for day in uniqueDaysDescending.dropFirst() {
            if isSameDay(day, dateToCheck) {
                guard let previousDate = calendar.date(byAdding: .day, value: -1, to: dateToCheck) else { break }
                currentStreak += 1
                dateToCheck = previousDate
            } else {
                break
            }
        }

        let longestStreak = computeLongestStreak(from: uniqueDaysAscending, calendar: calendar)
        return Streak(current: currentStreak, max: longestStreak)
    }

    private nonisolated static func computeLongestStreak(from days: [Date], calendar: Calendar = .current) -> Int {
        guard !days.isEmpty else { return 0 }
        var longest = 1
        var current = 1

        for i in 1 ..< days.count {
            let prevDay = days[i - 1]
            let currentDay = days[i]
            let diff = calendar.dateComponents([.day], from: prevDay, to: currentDay).day ?? 0

            if diff == 1 {
                current += 1
            } else if diff > 1 {
                longest = max(longest, current)
                current = 1
            }
        }

        return max(longest, current)
    }

    // MARK: - Migration Logic

    /// Migrates data from Ionic app's SQLite database to SwiftData if needed
    /// Should be called once on app startup before other operations
    /// This method migrates data for ALL users found in the opStack tables
    func migrateFromSQLiteIfNeeded() async {
        guard migrationService.isMigrationNeeded() else {
            logger.log(level: .info, tag: tag, message: "No SQLite migration needed")
            return
        }

        do {
            logger.log(level: .info, tag: tag, message: "Starting SQLite migration for all users in opStack")
            // Migrate data for all users found in the opStack tables
            let migratedData = try await migrationService.migrateAllUsersEntryData()

            // Update dashboard data after migration (only for current active user if available)
            do {
                let accountId = try getAccountId()
                if migratedData[accountId] != nil {
                    await loadDashboardData()
                    await updateProgressAndStreakInternal()
                    logger.log(level: .info, tag: tag, message: "Dashboard data updated for current user: \(accountId)")
                }
            } catch {
                logger.log(level: .info, tag: tag, message: "No active account found, skipping dashboard update")
            }

            // Clean up SQLite database after successful migration
            try migrationService.cleanupAfterMigration()

        } catch {
            logger.log(level: .error, tag: tag, message: "SQLite migration failed: \(error.localizedDescription)")
        }
    }

    /// Migrates existing baby entry weight from ounces to decigrams and length from inches to millimeters.
    /// Runs once per account on app startup; skips if already completed for the active account.
    func migrateBabyEntriesToDecigrams() async {
        do {
            let accountId = try getAccountId()
            let key = KvStorageKeys.babyEntryDecigramsMigratedKey(for: accountId)
            guard (kvStorage.getValue(forKey: key) as? Bool) != true else { return }

            let allEntries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
            let babyEntries = allEntries.filter { $0.entryType == EntryType.baby.rawValue && $0.babyEntry != nil }

            // Idempotency: the heuristic (weight < 2835) skips already-converted entries,
            // so a crash mid-loop is safely re-runnable on next launch.
            for entry in babyEntries {
                guard entry.babyEntry != nil else { continue }
                try await localRepo.updateEntry(entry)
            }

            logger.log(level: .info, tag: tag, message: "Baby entry decigram migration completed: \(babyEntries.count) entries")
            kvStorage.setValue(true, forKey: key)
        } catch {
            logger.log(level: .error, tag: tag, message: "Baby entry decigram migration failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Sync Logic

    /// Sync all unsynced entries with the remote backend. Call this on app start or after network recovery.
    /// If a sync is already in progress, callers await its completion (zero CPU overhead) instead of skipping.
    func syncAllEntriesWithRemote() async {
        // If a sync is already running, piggyback on it — await the same task.
        if let existingTask = activeSyncTask {
            await existingTask.value
            return
        }

        // Let a just-fired eager push finish first so its rows are marked synced
        // before this sync's own push runs — otherwise both could submit the same
        // unsynced entries. (`pushPendingEntries()` piggybacks the other way while a
        // full sync is active, so the two never push concurrently.)
        if let existingPush = pendingPushTask {
            await existingPush.value
        }

        let task = Task { [weak self] () -> Bool in
            guard let self else { return false }
            return await self.performSync()
        }
        activeSyncTask = task
        isSyncing = true

        let didReachRemote = await task.value

        activeSyncTask = nil
        isSyncing = false
        // MOB-1726: mark the initial sync done ONLY when this pass actually reached the remote (had an
        // active account and attempted the fetch — success OR network failure). A sync fired before the
        // account resolves (a dashboard/refresh trigger racing login) bails inside `performSync` at
        // `getAccountId` and returns false; flipping the flag for THAT no-op marked the initial sync
        // "complete" against an empty DB, so the snapshot cards dropped their skeletons and flashed the
        // empty "no entries" state until the real post-account sync landed (cold-login empty flash).
        // Gating on `didReachRemote` keeps the flag false through the pre-account no-op, so the skeleton
        // stays up until a real sync completes — while NOT blocking any sync (the account propagates on
        // the main-actor hop into the task, so `getAccountId` still succeeds for the legitimate sync).
        if didReachRemote {
            hasCompletedInitialSync = true
        }
    }

    /// Pushes locally-saved-but-unsynced changes to the server immediately after a
    /// mutation (add/edit/delete), so they survive an app kill/uninstall instead of
    /// waiting for the next Dashboard/History/foreground sync (MOB-1433). Fired
    /// automatically off `entrySaved`/`entryDeleted` — see the subscription in `init`.
    ///
    /// Push-only (no remote pull), so it stays cheap enough to run per mutation.
    /// Safety:
    /// - If a full sync is running it piggybacks on that task (whose first step is a
    ///   push) instead of pushing concurrently — no double-submit.
    /// - Concurrent eager pushes coalesce onto one in-flight task.
    /// - Skips entirely when offline: a failed push increments each entry's attempt
    ///   count and abandons it after 8 tries (isSynced/isFailedToSync flipped true),
    ///   so firing doomed pushes offline would burn the retry budget. Offline entries
    ///   stay queued at their current attempt count for the next online trigger.
    func pushPendingEntries() async {
        // A full sync pushes unsynced rows as its first step — ride it, don't race it.
        if let existingSync = activeSyncTask {
            await existingSync.value
            return
        }
        // Coalesce bursts (an edit is delete + add → two mutations → one push).
        if let existingPush = pendingPushTask {
            await existingPush.value
            return
        }
        guard NetworkMonitor.shared.isConnected else {
            logger.log(level: .info, tag: tag, message: "Eager push skipped — offline; entries remain queued for next sync")
            return
        }
        guard let accountId = try? getAccountId() else {
            logger.log(level: .error, tag: tag, message: "Eager push skipped — no active account")
            return
        }

        let task = Task { [weak self] in
            guard let self else { return }
            // Baby-before-entry ordering (MOB-1527): if an offline-created baby is still awaiting
            // its server id, reconcile babies first so any baby entries get remapped onto the
            // server id before they're pushed — otherwise this eager push would POST a client-UUID
            // babyId the server rejects, burning the entry's retry budget. Mirrors `performSync`;
            // the in-memory guard keeps the common weight/BP push (no pending baby) free of extra work.
            if self.babyService?.currentBabies.contains(where: { !$0.isServerCreated && !$0.isDeleted }) == true {
                await self.babyService?.syncBabies(for: accountId)
            }
            _ = await self.pushUnsyncedEntriesToRemote(accountId: accountId)
        }
        pendingPushTask = task
        await task.value
        pendingPushTask = nil
    }

    /// The actual sync work — only one instance runs at a time.
    /// Returns `true` when the sync reached the remote (had an active account and attempted the fetch —
    /// success OR network failure), `false` when it bailed before that because no account had resolved
    /// yet. The caller uses this to decide whether to mark the initial sync complete (MOB-1726): a
    /// pre-account no-op must NOT complete it, or the snapshot cards flash the empty state on cold login.
    private func performSync() async -> Bool {
        let accountId: String
        do {
            accountId = try getAccountId()
        } catch {
            logger.log(level: .error, tag: tag, message: "Sync failed: No account ID available")
            return false
        }
        logger.log(level: .info, tag: tag, message: "Full entry sync started: accountId=\(accountId)")

        // Reconcile baby profiles BEFORE pushing entries (MOB-1527): an offline-created baby
        // must swap its client id for the server id — and have baby entries remapped onto it —
        // before those entries are pushed, otherwise they'd reference a non-existent baby id.
        await babyService?.syncBabies(for: accountId)

        do {
            let hadPushedCreates = await pushUnsyncedEntriesToRemote(accountId: accountId)

            // Sync mode of the unified GET /v3/entries/ — all entries since the last sync (epoch
            // on a full sync so the server replays entire history rather than a 20-row cursor page).
            let syncStart = await resolveSyncStart(accountId: accountId)
            let remoteOps = try await remoteRepo.fetchEntries(
                start: syncStart, cursor: nil, limit: nil, category: nil
            )
            // `.operations` recomputes its DTO remap on every access — hold it once.
            let operations = remoteOps.operations

            // Batched merge runs on the @ModelActor. MOB-516: awaited directly from @MainActor,
            // the stock executor ran the chunked merge/save on the MAIN thread (~27% + the 8.9 s
            // cold-login hang). Enqueue from a detached (background) task so it runs off-main.
            // Verify via trace: `applyRemoteOperations` off Main.
            let mergeWorker = self.worker
            let mergeResult = try await Task.detached(priority: .userInitiated) {
                try await mergeWorker.applyRemoteOperations(operations, accountId: accountId)
            }.value
            await applyMergeSideEffects(mergeResult)

            // Remote deletes/updates refresh the dashboard too: the old per-row
            // merge patched summaries inline as it deleted; the batched merge
            // defers to one full reload instead.
            let dashboardDataChanged = hadPushedCreates || mergeResult.hadChanges
            if dashboardDataChanged {
                await reloadAllDashboardData()
            }

            let syncTimestamp = remoteOps.timestamp ?? ISO8601DateFormatter().string(from: Date())
            try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: syncTimestamp)
            lastSyncTime = Date()

            if dashboardDataChanged {
                await updateProgressAndStreakInternal()
            }
            // Goal alerts fire only when new entries appeared (previous semantics).
            if hadPushedCreates || mergeResult.hadNewCreates {
                await checkGoalAlerts()
            }
            logger.log(
                level: .info,
                tag: tag,
                message: """
                Full entry sync completed successfully: accountId=\(accountId), hadPushedCreates=\(hadPushedCreates), \
                merged(inserted=\(mergeResult.insertedCount), updated=\(mergeResult.updatedCount), \
                deleted=\(mergeResult.deletedCount)), remoteOperationCount=\(operations.count)
                """
            )

        } catch {
            logger.log(level: .error, tag: tag, message: "Full entry sync failed: accountId=\(accountId), error=\(error.localizedDescription)")
        }
        // Reached the remote (fetch succeeded or threw) — a real attempt, so the caller may complete the
        // initial sync. Only the no-account bail above returns false.
        return true
    }

    /// MOB-1726: refresh EVERY product's published dashboard summaries after a merge — not just weight.
    /// The snapshot overview's BPM/baby cards mirror `bpmDailySummaries` / `babyDailySummariesByProfile`,
    /// which only these calls republish; reloading `.scale` alone left them stuck on their pre-sync values
    /// (the "all three empty on first login" bug — the first snapshot load ran against an empty DB and
    /// nothing re-aggregated BPM/baby after sync).
    private func reloadAllDashboardData() async {
        await loadDashboardData(entryType: .scale)
        await loadDashboardData(entryType: .bpm)
        for baby in babyService?.currentBabies ?? [] where baby.isServerCreated && !baby.isDeleted {
            await loadBabyDashboardData(babyId: baby.id)
        }
    }

    /// Maximum unified requests per batched `POST /v3/entries/` call.
    private static let pushChunkMaxRequests = 100

    /// One unsynced local entry prepared for pushing: its identity/bookkeeping
    /// fields plus the 1–2 unified requests it expands to (baby: weight + length).
    private struct PendingPushEntry {
        let entryId: UUID
        let operationType: String
        let entryTimestamp: String
        let attempts: Int
        let requests: [UnifiedEntryRequest]
    }

    /// Running counters for a push pass, shared across chunks.
    private struct PushTally {
        var hadSuccessfulCreate = false
        var successfulCreateCount = 0
        var successfulDeleteCount = 0
        var failedSyncCount = 0
        var firstFailureReason: String?
    }

    /// Pushes unsynced local entries to the remote API in chunked batch POSTs
    /// (MOB-1433) — N unsynced entries no longer cost N HTTP round trips and
    /// N per-row saves.
    ///
    /// - Returns: `true` if at least one create operation was successfully synced.
    ///   The caller uses this to decide whether to show the goal met card: we only show it when
    ///   the user actually added new entries in this sync (not on every login or pull-to-refresh).
    private func pushUnsyncedEntriesToRemote(accountId: String) async -> Bool {
        // 1. All unsynced entries (creates and deletes) as Sendable snapshot+DTO
        // pairs, extracted inside the repository context (MA-3898).
        let unsynced = (try? await localRepo.fetchUnsyncedEntriesAsSnapshots(forUserId: accountId)) ?? []
        guard !unsynced.isEmpty else { return false }

        // 2. Map every entry to its unified request(s) up front. Weight/BP produce
        // one request; a baby entry can expand into a `weight` and a
        // `measureLength` request (MOB-386).
        let pending: [PendingPushEntry] = unsynced.compactMap { snapshot, dto in
            let requests: [UnifiedEntryRequest]
            if dto.entryType == EntryType.baby.rawValue {
                requests = BabyEntryRequest.makeRequests(from: dto, entryId: snapshot.id.uuidString, note: snapshot.note)
            } else if let request = UnifiedEntryRequest(from: dto, serverEntryId: snapshot.serverEntryId, note: snapshot.note) {
                requests = [request]
            } else {
                requests = []
            }
            guard !requests.isEmpty else { return nil }
            return PendingPushEntry(
                entryId: snapshot.id,
                operationType: snapshot.operationType,
                entryTimestamp: snapshot.entryTimestamp,
                attempts: snapshot.attempts,
                requests: requests
            )
        }
        guard !pending.isEmpty else { return false }

        // 3. POST in chunks of ≤100 requests; an entry's requests never split
        // across chunks so response rows can be mapped back per entry.
        var tally = PushTally()
        for chunk in Self.chunkForPush(pending, maxRequests: Self.pushChunkMaxRequests) {
            await pushChunk(chunk, accountId: accountId, tally: &tally)
        }

        logger.log(
            level: .info,
            tag: tag,
            message: "Unsynced entry push completed for accountId=\(accountId): "
                + "createsSynced=\(tally.successfulCreateCount), deletesSynced=\(tally.successfulDeleteCount), failures=\(tally.failedSyncCount)"
        )
        if tally.failedSyncCount > 0 {
            logger.log(
                level: .error,
                tag: tag,
                message: "Unsynced entry push had failures: accountId=\(accountId), failures=\(tally.failedSyncCount), "
                    + "firstFailure=\(tally.firstFailureReason ?? "unknown")"
            )
        }
        return tally.hadSuccessfulCreate
    }

    /// Splits pending entries into chunks whose combined request count stays
    /// within `maxRequests`, keeping each entry's requests contiguous.
    private static func chunkForPush(_ entries: [PendingPushEntry], maxRequests: Int) -> [[PendingPushEntry]] {
        var chunks: [[PendingPushEntry]] = []
        var current: [PendingPushEntry] = []
        var requestCount = 0
        for entry in entries {
            if !current.isEmpty, requestCount + entry.requests.count > maxRequests {
                chunks.append(current)
                current = []
                requestCount = 0
            }
            current.append(entry)
            requestCount += entry.requests.count
        }
        if !current.isEmpty {
            chunks.append(current)
        }
        return chunks
    }

    /// POSTs one chunk and applies its bookkeeping in a single batched worker
    /// call (mark-synced + server ids + deletes, or failure attempts).
    private func pushChunk(_ chunk: [PendingPushEntry], accountId: String, tally: inout PushTally) async {
        let requests = chunk.flatMap { $0.requests }
        do {
            // POST /v3/entries/ — atomic batch; baby entries submit their sub-type rows together.
            let submitResponse = try await remoteRepo.submitEntries(requests)
            logger.log(
                level: .info,
                tag: tag,
                message: "API submitEntries batch response: requests=\(requests.count), "
                    + "responseEntries.count=\(submitResponse.entries.count)"
            )

            // Map response rows back positionally: one row per request in order,
            // and each entry owns the row of its FIRST request (the same row the
            // per-entry push read via `entries.first`).
            let (outcomes, deletedEntries) = buildPushOutcomes(
                for: chunk, submitResponse: submitResponse, tally: &tally
            )

            do {
                try await worker.applyPushOutcomes(outcomes)
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to persist push outcomes for accountId=\(accountId): \(error.localizedDescription)"
                )
            }
            for deleted in deletedEntries {
                try? await handleEntryDeleted(entryId: deleted.entryId, entryTimestamp: deleted.entryTimestamp)
            }
        } catch {
            // The batch is atomic, so a failed chunk marks every entry in it
            // with one more attempt (the per-entry semantics, applied per chunk).
            let outcomes = chunk.map { entry -> EntryPushOutcome in
                let newAttempts = entry.attempts + 1
                return EntryPushOutcome(
                    entryId: entry.entryId,
                    outcome: .failed(attempts: newAttempts, markAsFailed: newAttempts > 8)
                )
            }
            try? await worker.applyPushOutcomes(outcomes)
            tally.failedSyncCount += chunk.count
            if tally.firstFailureReason == nil {
                tally.firstFailureReason = error.localizedDescription
            }
        }
    }

    /// Builds per-entry push outcomes from a batch response, tallying created/deleted
    /// counts. Returns the outcomes plus the entries whose delete op succeeded, so the
    /// caller can fire their delete side effects. Extracted to keep `pushChunk` within
    /// the function-length limit.
    private func buildPushOutcomes(
        for chunk: [PendingPushEntry],
        submitResponse: UnifiedEntryResponse,
        tally: inout PushTally
    ) -> (outcomes: [EntryPushOutcome], deleted: [(entryId: UUID, entryTimestamp: String)]) {
        var outcomes: [EntryPushOutcome] = []
        var deletedEntries: [(entryId: UUID, entryTimestamp: String)] = []
        var responseCursor = 0
        for entry in chunk {
            if entry.operationType == OperationType.create.rawValue {
                let serverEntryId = responseCursor < submitResponse.entries.count
                    ? submitResponse.entries[responseCursor].entryId
                    : nil
                outcomes.append(EntryPushOutcome(
                    entryId: entry.entryId,
                    outcome: .created(serverEntryId: serverEntryId, attempts: entry.attempts)
                ))
                tally.hadSuccessfulCreate = true
                tally.successfulCreateCount += 1
            } else {
                outcomes.append(EntryPushOutcome(entryId: entry.entryId, outcome: .deleted))
                deletedEntries.append((entry.entryId, entry.entryTimestamp))
                tally.successfulDeleteCount += 1
            }
            responseCursor += entry.requests.count
        }
        return (outcomes, deletedEntries)
    }

    /// Lightweight summary for a single month. Avoids computing all months when only one changes.
    func getMonthSummary(monthKey: String) async throws -> HistoryMonth? {
        let monthEntries = try await getEntries(forMonth: monthKey)
        guard !monthEntries.isEmpty else { return nil }
        return Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries)
    }

    /// Applies the UI/integration side effects the merge used to perform per
    /// row, now driven by the worker's Sendable result (MOB-1433):
    /// `entryDeleted` emissions + integration deletes for removed rows, ONE
    /// `entrySaved` emission for the whole batch of new creates, and
    /// integration forwarding of each newly-created entry (MA-3886).
    private func applyMergeSideEffects(_ result: EntryMergeResult) async {
        // MOB-516: an updates-only remote merge changes local data but emits neither entrySaved
        // nor entryDeleted, so the sink-based cache invalidation wouldn't fire — clear directly
        // on any merge change so History never serves pre-merge months.
        if result.hadChanges {
            invalidateMonthsCache()
        }
        for notification in result.deletedNotifications {
            entryDeleted.send(notification)
            do {
                try await integrationService.deleteEntry(notification: notification)
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to delete merged entry from integrations: \(error.localizedDescription)"
                )
            }
        }

        // MA-3851: only notify UI listeners when the remote merge actually
        // inserted new entries locally — one emission for the whole batch.
        if result.hadNewCreates {
            do {
                if let entry = try await getLatestEntry() {
                    let notification = EntryNotification(from: entry)
                    entrySaved.send(notification)
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to get latest entry: \(error.localizedDescription)")
            }
        }

        // MA-3886: forward newly-created entries to active health integrations
        // (e.g., Apple Health). Without this, Wi-Fi/R4 entries arriving via push-triggered
        // remote sync never reach HealthKit because the scale uploads to the server, not the phone.
        // MOB-1433: ONE batched call — settings read once, and a per-account marker
        // skips the historical backfill on the first full sync instead of flooding
        // HealthKit with thousands of writes.
        guard !result.newlyCreatedOps.isEmpty else { return }
        let notifications = result.newlyCreatedOps.map { EntryNotification(from: $0) }
        do {
            try await integrationService.syncNewEntries(notifications: notifications)
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to forward remote-merged entries to integrations: \(error.localizedDescription)"
            )
        }
    }

    // MARK: - Export

    /// Exports entries as CSV via the unified `GET /v3/entries/csv` endpoint.
    ///
    /// Emails the report (no `download` flag) for the given product `category`. The device's
    /// current UTC offset is applied to the exported `Date/Time` column.
    /// - Parameters:
    ///   - category: The product to export (`weight`/`bp`/`baby`); `nil` exports all.
    ///   - babyId: Required when `category == "baby"` — scopes the export to one baby.
    func exportCSV(category: String?, babyId: String?) async throws {
        // An active account is still required so the request is authorized.
        guard accountService.activeAccount != nil else {
            throw AccountError.noActiveAccount
        }
        if category == EntryCategory.baby.rawValue && babyId == nil {
            throw NSError(
                domain: "EntryService",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "babyId is required for baby CSV export"]
            )
        }
        let request = EntriesCSVRequest(
            category: category,
            babyId: babyId,
            download: false,
            utcOffset: DateTimeTools.getUTCOffset()
        )
        _ = try await remoteRepo.exportEntriesCSV(request)
    }

    // MARK: - Cursor Pagination (Remote Read)

    /// Reads a single page of entries from the unified `GET /v3/entries/` cursor mode.
    ///
    /// Pass `cursor = nil` for the first page; thereafter forward the returned
    /// `EntriesPage.nextCursor`. Paging stops once `hasMore` is `false`. The `limit` is
    /// clamped to the server-accepted range.
    /// - Parameters:
    ///   - cursor: The `entryTimestamp` cursor from the previous page, or `nil` for page 1.
    ///   - limit: Requested page size (clamped to `1...100`).
    ///   - category: Optional product filter (`weight`/`bp`/`baby`); `nil` returns all products.
    ///   - babyId: Optional baby filter (only meaningful with `category == "baby"`).
    /// - Returns: The page of entries plus pagination metadata.
    func fetchEntriesPage(cursor: String?, limit: Int, category: String?, babyId: String?) async throws -> EntriesPage {
        let clamped = EntriesPagination.clamp(limit: limit)
        let response = try await remoteRepo.fetchEntries(
            start: nil, cursor: cursor, limit: clamped, category: category, babyId: babyId
        )
        return EntriesPage(
            entries: response.operations,
            nextCursor: response.nextCursor,
            // Fall back to inferring from page fullness when the server omits `hasMore`.
            hasMore: response.hasMore ?? (response.entries.count >= clamped)
        )
    }

    // MARK: - Entry Type Filtering

    /// Checks if an Entry matches the given entryType.
    /// Legacy entries without an entryType default to `.scale`.
    private func matchesEntryType(_ entry: Entry, entryType: EntryType) -> Bool {
        guard let type = entry.entryType, !type.isEmpty else { return entryType == .scale }
        return type == entryType.rawValue
    }

    /// DTO-level entry type matching for background-thread aggregation.
    private nonisolated func matchesDTOEntryType(_ dto: BathScaleOperationDTO, entryType: EntryType) -> Bool {
        guard let type = dto.entryType, !type.isEmpty else { return entryType == .scale }
        return type == entryType.rawValue
    }

    // MARK: - Aggregation Helpers

    /// Helper function for all metrics (excludes zero values)
    private nonisolated func avgNonZero(_ values: [Double?]) -> Double? {
        let vals = values.compactMap { $0 }.filter { $0 > 0 }
        return vals.isEmpty ? nil : vals.reduce(0, +) / Double(vals.count)
    }

    /// MA-3937 helper: walks `entries` (already sorted latest-first) and returns the first
    /// non-nil, > 0 value picked from each entry. Preserves the prior "treat 0 as missing" rule.
    private nonisolated func latestPositive<T: BinaryInteger>(
        _ entries: [Entry],
        pick: (Entry) -> T?
    ) -> Double? {
        for entry in entries {
            if let value = pick(entry), value > 0 {
                return Double(value)
            }
        }
        return nil
    }

    /// MA-3937 helper: DTO variant of `latestPositive` used by background aggregation.
    private nonisolated func latestPositiveDTO(
        _ dtos: [BathScaleOperationDTO],
        pick: (BathScaleOperationDTO) -> Double?
    ) -> Double? {
        for dto in dtos {
            if let value = pick(dto), value > 0 {
                return value
            }
        }
        return nil
    }

    /// Helper function for weight: average stored values (tenths of lbs) without rounding early.
    private nonisolated func avgWeight(_ values: [Int]) -> Double {
        guard !values.isEmpty else { return 0 }
        let filtered = values.filter { $0 > 0 }
        guard !filtered.isEmpty else { return 0 }
        return Double(filtered.reduce(0, +)) / Double(filtered.count)
    }

    // Aggregate entries by day, returning BathScaleWeightSummary for each day.
    //
    // MA-3937: hybrid rule — the *most recent day with valid entries* surfaces its latest
    // non-null-positive values per metric (so the dashboard headline reflects the actual most
    // recent weigh-in). Every other day reverts to the prior daily-average behaviour.
    // swiftlint:disable:next function_body_length
    func aggregateByDay(entries: [Entry], accountId: String) -> [BathScaleWeightSummary?] {
        // Group entries by day (YYYY-MM-DD), converting UTC to local timezone
        let grouped = Dictionary(grouping: entries) { entry -> String in
            return DateTimeTools.getLocalDateStringFromUTCDate(entry.entryTimestamp)
        }

        // Identify the most recent day that actually has a valid weight entry. Lexicographic max
        // of YYYY-MM-DD strings == chronological max. Skipping days with no valid weight ensures a
        // day made entirely of weight=0 entries doesn't "steal" the latest-day slot from the
        // genuinely most recent day with real data.
        let latestValidDayKey: String = grouped
            .filter { _, dayEntries in
                dayEntries.contains { ($0.scaleEntry?.weight ?? 0) > 0 }
            }
            .keys
            .max() ?? ""

        return grouped.compactMap { day, dayEntries -> BathScaleWeightSummary? in
            // Filter entries that have valid weight data from scaleEntry
            let validEntries = dayEntries.filter { entry in
                guard let scaleEntry = entry.scaleEntry,
                      let weight = scaleEntry.weight,
                      weight > 0 else { return false }
                return true
            }
            guard !validEntries.isEmpty else { return nil }

            // Ensure we have a valid date string before parsing
            guard !day.isEmpty else { return nil }
            let date = DateTimeTools.getDateFromDateString(day, format: "yyyy-MM-dd")
            let count = validEntries.count

            if day == latestValidDayKey {
                // MA-3937 latest-day branch: surface the latest entry's values, with per-metric
                // fallback to the most recent non-null-positive reading across the day.
                let sortedDesc = validEntries.sorted { $0.entryTimestamp > $1.entryTimestamp }
                return BathScaleWeightSummary(
                    accountId: accountId,
                    period: day,
                    entryTimestamp: sortedDesc.first?.entryTimestamp ?? "",
                    date: date,
                    count: count,
                    weight: Double(sortedDesc.first?.scaleEntry?.weight ?? 0),
                    bodyFat: latestPositive(sortedDesc) { $0.scaleEntry?.bodyFat },
                    muscleMass: latestPositive(sortedDesc) { $0.scaleEntry?.muscleMass },
                    water: latestPositive(sortedDesc) { $0.scaleEntry?.water },
                    bmi: latestPositive(sortedDesc) { $0.scaleEntry?.bmi },
                    bmr: latestPositive(sortedDesc) { $0.scaleEntryMetric?.bmr },
                    metabolicAge: latestPositive(sortedDesc) { $0.scaleEntryMetric?.metabolicAge },
                    proteinPercent: latestPositive(sortedDesc) { $0.scaleEntryMetric?.proteinPercent },
                    pulse: latestPositive(sortedDesc) { $0.scaleEntryMetric?.pulse },
                    skeletalMusclePercent: latestPositive(sortedDesc) { $0.scaleEntryMetric?.skeletalMusclePercent },
                    subcutaneousFatPercent: latestPositive(sortedDesc) { $0.scaleEntryMetric?.subcutaneousFatPercent },
                    visceralFatLevel: latestPositive(sortedDesc) { $0.scaleEntryMetric?.visceralFatLevel },
                    boneMass: latestPositive(sortedDesc) { $0.scaleEntryMetric?.boneMass },
                    impedance: latestPositive(sortedDesc) { $0.scaleEntryMetric?.impedance }
                )
            }

            // Daily-average branch — used for every day except the most recent.
            let latestTimestamp = validEntries.map { $0.entryTimestamp }.max() ?? ""
            return BathScaleWeightSummary(
                accountId: accountId,
                period: day,
                entryTimestamp: latestTimestamp,
                date: date,
                count: count,
                weight: avgWeight(validEntries.compactMap { $0.scaleEntry?.weight }),
                bodyFat: avgNonZero(validEntries.compactMap { $0.scaleEntry?.bodyFat.map(Double.init) }),
                muscleMass: avgNonZero(validEntries.compactMap { $0.scaleEntry?.muscleMass.map(Double.init) }),
                water: avgNonZero(validEntries.compactMap { $0.scaleEntry?.water.map(Double.init) }),
                bmi: avgNonZero(validEntries.compactMap { $0.scaleEntry?.bmi.map(Double.init) }),
                bmr: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.bmr.map(Double.init) }),
                metabolicAge: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.metabolicAge.map(Double.init) }),
                proteinPercent: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.proteinPercent.map(Double.init) }),
                pulse: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.pulse.map(Double.init) }),
                skeletalMusclePercent: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.skeletalMusclePercent.map(Double.init) }),
                subcutaneousFatPercent: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.subcutaneousFatPercent.map(Double.init) }),
                visceralFatLevel: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.visceralFatLevel.map(Double.init) }),
                boneMass: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.boneMass.map(Double.init) }),
                impedance: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.impedance.map(Double.init) })
            )
        }.sorted { $0.period < $1.period }
    }

    /// Aggregate entries by month, returning BathScaleWeightSummary for each month
    func aggregateByMonth(entries: [Entry], accountId: String) -> [BathScaleWeightSummary?] {
        // Group entries by month (YYYY-MM), converting UTC to local timezone
        let grouped = Dictionary(grouping: entries) { entry -> String in
            return DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
        }

        return grouped.compactMap { month, monthEntries -> BathScaleWeightSummary? in
            guard !monthEntries.isEmpty else { return nil }
            // Filter entries that have valid weight data from scaleEntry
            let validEntries = monthEntries.filter { entry in
                guard let scaleEntry = entry.scaleEntry,
                      let weight = scaleEntry.weight,
                      weight > 0 else { return false }
                return true
            }
            guard !validEntries.isEmpty else { return nil }

            // Create date from month string (YYYY-MM) by adding "-01" to get first day of month
            guard !month.isEmpty else { return nil }
            let dateString = "\(month)-01"
            let date = DateTimeTools.formatter("yyyy-MM-dd").date(from: dateString) ?? Date()

            let latestTimestamp = validEntries.map { $0.entryTimestamp }.max() ?? ""
            let count = validEntries.count

            return BathScaleWeightSummary(
                accountId: accountId,
                period: month,
                entryTimestamp: latestTimestamp,
                date: date,
                count: count,
                weight: avgWeight(validEntries.compactMap { $0.scaleEntry?.weight }),
                bodyFat: avgNonZero(validEntries.compactMap { $0.scaleEntry?.bodyFat.map(Double.init) }),
                muscleMass: avgNonZero(validEntries.compactMap { $0.scaleEntry?.muscleMass.map(Double.init) }),
                water: avgNonZero(validEntries.compactMap { $0.scaleEntry?.water.map(Double.init) }),
                bmi: avgNonZero(validEntries.compactMap { $0.scaleEntry?.bmi.map(Double.init) }),
                bmr: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.bmr.map(Double.init) }),
                metabolicAge: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.metabolicAge.map(Double.init) }),
                proteinPercent: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.proteinPercent.map(Double.init) }),
                pulse: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.pulse.map(Double.init) }),
                skeletalMusclePercent: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.skeletalMusclePercent.map(Double.init) }),
                subcutaneousFatPercent: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.subcutaneousFatPercent.map(Double.init) }),
                visceralFatLevel: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.visceralFatLevel.map(Double.init) }),
                boneMass: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.boneMass.map(Double.init) }),
                impedance: avgNonZero(validEntries.compactMap { $0.scaleEntryMetric?.impedance.map(Double.init) })
            )
        }.sorted { $0.period < $1.period }
    }

    // MARK: - DTO-based Aggregation (Background Thread Safe)

    // Aggregate DTOs by day on background thread - avoids SwiftData relationship access.
    //
    // MA-3937 hybrid rule: the most recent day with valid weight surfaces its latest-non-null-positive
    // metrics (latest weigh-in), while every other day keeps the daily-average behaviour via the
    // `EntrySummaryBucket` accumulator.
    // swiftlint:disable:next function_body_length
    private nonisolated func aggregateByDayFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        var groupedDTOs: [String: [BathScaleOperationDTO]] = [:]
        groupedDTOs.reserveCapacity(dtos.count)

        for dto in dtos {
            guard let ts = dto.entryTimestamp else { continue }
            let day = DateTimeTools.getLocalDateStringFromUTCDate(ts)
            guard !day.isEmpty else { continue }
            guard (dto.weight ?? 0) > 0 else { continue }
            groupedDTOs[day, default: []].append(dto)
        }

        let latestValidDayKey: String = groupedDTOs.keys.max() ?? ""

        return groupedDTOs.compactMap { day, dayDTOs -> BathScaleWeightSummary? in
            let date = DateTimeTools.getDateFromDateString(day, format: "yyyy-MM-dd")

            if day == latestValidDayKey {
                let sortedDesc = dayDTOs.sorted { ($0.entryTimestamp ?? "") > ($1.entryTimestamp ?? "") }
                return BathScaleWeightSummary(
                    accountId: accountId,
                    period: day,
                    entryTimestamp: sortedDesc.first?.entryTimestamp ?? "",
                    date: date,
                    count: sortedDesc.count,
                    weight: sortedDesc.first?.weight ?? 0,
                    bodyFat: latestPositiveDTO(sortedDesc) { $0.bodyFat },
                    muscleMass: latestPositiveDTO(sortedDesc) { $0.muscleMass },
                    water: latestPositiveDTO(sortedDesc) { $0.water },
                    bmi: latestPositiveDTO(sortedDesc) { $0.bmi },
                    bmr: latestPositiveDTO(sortedDesc) { $0.bmr },
                    metabolicAge: latestPositiveDTO(sortedDesc) { $0.metabolicAge },
                    proteinPercent: latestPositiveDTO(sortedDesc) { $0.proteinPercent },
                    pulse: latestPositiveDTO(sortedDesc) { $0.pulse },
                    skeletalMusclePercent: latestPositiveDTO(sortedDesc) { $0.skeletalMusclePercent },
                    subcutaneousFatPercent: latestPositiveDTO(sortedDesc) { $0.subcutaneousFatPercent },
                    visceralFatLevel: latestPositiveDTO(sortedDesc) { $0.visceralFatLevel },
                    boneMass: latestPositiveDTO(sortedDesc) { $0.boneMass },
                    impedance: latestPositiveDTO(sortedDesc) { $0.impedance }
                )
            }

            var bucket = EntrySummaryBucket(accountId: accountId, period: day)
            for dto in dayDTOs { bucket.add(dto: dto) }
            return BathScaleWeightSummary(
                accountId: accountId,
                period: bucket.period,
                entryTimestamp: bucket.latestTimestamp,
                date: date,
                count: bucket.count,
                weight: bucket.averagedWeight,
                bodyFat: bucket.bodyFat.average,
                muscleMass: bucket.muscleMass.average,
                water: bucket.water.average,
                bmi: bucket.bmi.average,
                bmr: bucket.bmr.average,
                metabolicAge: bucket.metabolicAge.average,
                proteinPercent: bucket.proteinPercent.average,
                pulse: bucket.pulse.average,
                skeletalMusclePercent: bucket.skeletalMusclePercent.average,
                subcutaneousFatPercent: bucket.subcutaneousFatPercent.average,
                visceralFatLevel: bucket.visceralFatLevel.average,
                boneMass: bucket.boneMass.average,
                impedance: bucket.impedance.average
            )
        }.sorted { $0.period < $1.period }
    }

    /// Aggregate DTOs by month on background thread - avoids SwiftData relationship access
    private nonisolated func aggregateByMonthFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        var grouped: [String: EntrySummaryBucket] = [:]
        grouped.reserveCapacity(dtos.count)

        for dto in dtos {
            guard let ts = dto.entryTimestamp else { continue }
            let month = DateTimeTools.getLocalMonthStringFromUTCDate(ts)
            guard !month.isEmpty else { continue }
            guard (dto.weight ?? 0) > 0 else { continue }

            var bucket = grouped[month] ?? EntrySummaryBucket(accountId: accountId, period: month)
            bucket.add(dto: dto)
            grouped[month] = bucket
        }

        return grouped.values.compactMap { bucket in
            let date = Self.monthSummaryDateFormatter.date(from: "\(bucket.period)-01") ?? Date()
            return BathScaleWeightSummary(
                accountId: accountId,
                period: bucket.period,
                entryTimestamp: bucket.latestTimestamp,
                date: date,
                count: bucket.count,
                weight: bucket.averagedWeight,
                bodyFat: bucket.bodyFat.average,
                muscleMass: bucket.muscleMass.average,
                water: bucket.water.average,
                bmi: bucket.bmi.average,
                bmr: bucket.bmr.average,
                metabolicAge: bucket.metabolicAge.average,
                proteinPercent: bucket.proteinPercent.average,
                pulse: bucket.pulse.average,
                skeletalMusclePercent: bucket.skeletalMusclePercent.average,
                subcutaneousFatPercent: bucket.subcutaneousFatPercent.average,
                visceralFatLevel: bucket.visceralFatLevel.average,
                boneMass: bucket.boneMass.average,
                impedance: bucket.impedance.average
            )
        }.sorted { $0.period < $1.period }
    }

    // MARK: - BPM DTO Aggregation (Background Thread Safe)

    /// Aggregate BPM DTOs by day — filters on systolic > 0 instead of weight > 0.
    private nonisolated func aggregateBpmByDayFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        var grouped: [String: EntrySummaryBucket] = [:]
        grouped.reserveCapacity(dtos.count)

        for dto in dtos {
            guard let ts = dto.entryTimestamp else { continue }
            let day = DateTimeTools.getLocalDateStringFromUTCDate(ts)
            guard !day.isEmpty else { continue }
            guard (dto.systolic ?? 0) > 0 else { continue }

            var bucket = grouped[day] ?? EntrySummaryBucket(accountId: accountId, period: day)
            bucket.add(dto: dto)
            grouped[day] = bucket
        }

        return grouped.values.compactMap { bucket in
            let date = DateTimeTools.getDateFromDateString(bucket.period, format: "yyyy-MM-dd")
            return BathScaleWeightSummary(
                accountId: accountId,
                period: bucket.period,
                entryTimestamp: bucket.latestTimestamp,
                date: date,
                count: bucket.count,
                weight: 0,
                pulse: bucket.pulse.average,
                systolic: bucket.systolic.average,
                diastolic: bucket.diastolic.average,
                meanArterial: bucket.meanArterial.average,
                entryType: EntryType.bpm.rawValue
            )
        }.sorted { $0.period < $1.period }
    }

    /// Aggregate BPM DTOs by month.
    private nonisolated func aggregateBpmByMonthFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        var grouped: [String: EntrySummaryBucket] = [:]
        grouped.reserveCapacity(dtos.count)

        for dto in dtos {
            guard let ts = dto.entryTimestamp else { continue }
            let month = DateTimeTools.getLocalMonthStringFromUTCDate(ts)
            guard !month.isEmpty else { continue }
            guard (dto.systolic ?? 0) > 0 else { continue }

            var bucket = grouped[month] ?? EntrySummaryBucket(accountId: accountId, period: month)
            bucket.add(dto: dto)
            grouped[month] = bucket
        }

        return grouped.values.compactMap { bucket in
            let date = Self.monthSummaryDateFormatter.date(from: "\(bucket.period)-01") ?? Date()
            return BathScaleWeightSummary(
                accountId: accountId,
                period: bucket.period,
                entryTimestamp: bucket.latestTimestamp,
                date: date,
                count: bucket.count,
                weight: 0,
                pulse: bucket.pulse.average,
                systolic: bucket.systolic.average,
                diastolic: bucket.diastolic.average,
                meanArterial: bucket.meanArterial.average,
                entryType: EntryType.bpm.rawValue
            )
        }.sorted { $0.period < $1.period }
    }

    // MARK: - Baby Entry Aggregation

    /// Represents baby entry data extracted from Entry + BabyEntry relationships.
    /// Used for background-thread aggregation (not `@Model`, fully `Sendable`).
    private struct BabyEntrySnapshot: Sendable {
        let entryTimestamp: String
        let babyId: String
        let weightDecigrams: Int
        /// Recorded length in millimeters (the unit `BabyEntry.length` is persisted in).
        let lengthMm: Int
    }

    /// Fetches baby entries for a given babyId and returns snapshots safe for background aggregation.
    private func fetchBabyEntrySnapshots(accountId: String, babyId: String) async throws -> [BabyEntrySnapshot] {
        let allEntries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        let babyEntryType = EntryType.baby.rawValue
        return allEntries.compactMap { entry -> BabyEntrySnapshot? in
            guard entry.entryType == babyEntryType,
                  let babyEntry = entry.babyEntry,
                  babyEntry.babyId == babyId,
                  babyEntry.weight > 0
            else { return nil }
            return BabyEntrySnapshot(
                entryTimestamp: entry.entryTimestamp,
                babyId: babyEntry.babyId,
                weightDecigrams: babyEntry.weight,
                lengthMm: babyEntry.length
            )
        }
    }

    /// Converts baby weight in decigrams to the stored weight format (tenths of lbs)
    /// used by BathScaleWeightSummary and the chart rendering pipeline.
    private nonisolated func decigramsToStoredWeight(_ decigrams: Int) -> Int {
        let kg = Double(decigrams) / BabyPercentileGrowthReference.decigramsToKgFactor
        return ConversionTools.convertKgToStored(kg)
    }

    /// Average of the recorded lengths (millimeters) for a period, expressed in inches for the
    /// baby height chart. Only positive (actually recorded) lengths count; returns nil when the
    /// period has no recorded length, so the height series simply omits that point rather than
    /// plotting a zero. Averages in mm first to match History's per-day/week length rendering.
    private nonisolated func avgLengthInches(_ lengthsMm: [Int]) -> Double? {
        let recorded = lengthsMm.filter { $0 > 0 }
        guard !recorded.isEmpty else { return nil }
        // Average in Double and round to the nearest mm rather than integer-dividing, so the
        // fractional part isn't silently truncated (e.g. [201, 202] → 202mm, not 201mm).
        let avgMm = Int((Double(recorded.reduce(0, +)) / Double(recorded.count)).rounded())
        return ConversionTools.convertBabyMmToInches(avgMm)
    }

    /// Aggregates baby entry snapshots into daily summaries.
    private nonisolated func aggregateBabyByDay(
        _ snapshots: [BabyEntrySnapshot],
        accountId: String,
        babyId: String
    ) -> [BathScaleWeightSummary] {
        let grouped = Dictionary(grouping: snapshots) { snapshot -> String in
            DateTimeTools.getLocalDateStringFromUTCDate(snapshot.entryTimestamp)
        }

        return grouped.compactMap { day, daySnapshots -> BathScaleWeightSummary? in
            guard !day.isEmpty, !daySnapshots.isEmpty else { return nil }

            let date = DateTimeTools.getDateFromDateString(day, format: "yyyy-MM-dd")
            let latestTimestamp = daySnapshots.compactMap(\.entryTimestamp).max() ?? ""
            let storedWeights = daySnapshots.map { decigramsToStoredWeight($0.weightDecigrams) }

            return BathScaleWeightSummary(
                accountId: "baby_\(babyId)",
                period: day,
                entryTimestamp: latestTimestamp,
                date: date,
                count: daySnapshots.count,
                weight: avgWeight(storedWeights),
                babyLengthInches: avgLengthInches(daySnapshots.map(\.lengthMm))
            )
        }.sorted { $0.period < $1.period }
    }

    /// Aggregates baby entry snapshots into monthly summaries.
    private nonisolated func aggregateBabyByMonth(
        _ snapshots: [BabyEntrySnapshot],
        accountId: String,
        babyId: String
    ) -> [BathScaleWeightSummary] {
        let grouped = Dictionary(grouping: snapshots) { snapshot -> String in
            DateTimeTools.getLocalMonthStringFromUTCDate(snapshot.entryTimestamp)
        }

        return grouped.compactMap { month, monthSnapshots -> BathScaleWeightSummary? in
            guard !month.isEmpty, !monthSnapshots.isEmpty else { return nil }

            let dateString = "\(month)-01"
            let date = DateTimeTools.formatter("yyyy-MM-dd").date(from: dateString) ?? Date()
            let latestTimestamp = monthSnapshots.compactMap(\.entryTimestamp).max() ?? ""
            let storedWeights = monthSnapshots.map { decigramsToStoredWeight($0.weightDecigrams) }

            return BathScaleWeightSummary(
                accountId: "baby_\(babyId)",
                period: month,
                entryTimestamp: latestTimestamp,
                date: date,
                count: monthSnapshots.count,
                weight: avgWeight(storedWeights),
                babyLengthInches: avgLengthInches(monthSnapshots.map(\.lengthMm))
            )
        }.sorted { $0.period < $1.period }
    }

    /// Loads baby dashboard data for a specific baby profile.
    /// Fetches real baby entries, aggregates them into daily/monthly summaries,
    /// and publishes to `babyDailySummariesByProfile` / `babyMonthlySummariesByProfile`.
    func loadBabyDashboardData(babyId: String) async {
        if let existingTask = activeBabyDashboardLoadTasks[babyId] {
            let didSucceed = await existingTask.value
            if didSucceed { return }
        }

        let task = Task<Bool, Never> { [weak self] in
            guard let self else { return false }
            return await self.performBabyDashboardDataLoad(babyId: babyId)
        }
        activeBabyDashboardLoadTasks[babyId] = task

        _ = await task.value

        if activeBabyDashboardLoadTasks[babyId] == task {
            activeBabyDashboardLoadTasks[babyId] = nil
        }
    }

    private func performBabyDashboardDataLoad(babyId: String) async -> Bool {
        do {
            let accountId = try getAccountId()
            let snapshots = try await fetchBabyEntrySnapshots(accountId: accountId, babyId: babyId)
            let cachedSignature = babySummaryCacheByProfile[babyId]?.datasetSignature
            let result = await Task.detached(priority: .utility) { [weak self] in
                guard let self else { return EntrySummaryLoadResult.computed(signature: 0, daily: [], monthly: []) }
                let signature = Self.makeBabySnapshotSignature(snapshots)
                if signature == cachedSignature {
                    return EntrySummaryLoadResult.cached(signature: signature)
                }
                let daily = self.aggregateBabyByDay(snapshots, accountId: accountId, babyId: babyId)
                let monthly = self.aggregateBabyByMonth(snapshots, accountId: accountId, babyId: babyId)
                return EntrySummaryLoadResult.computed(signature: signature, daily: daily, monthly: monthly)
            }.value

            switch result {
            case .cached(let signature):
                if let cached = babySummaryCacheByProfile[babyId], cached.datasetSignature == signature {
                    if babyDailySummariesByProfile[babyId] != cached.daily {
                        babyDailySummariesByProfile[babyId] = cached.daily
                    }
                    if babyMonthlySummariesByProfile[babyId] != cached.monthly {
                        babyMonthlySummariesByProfile[babyId] = cached.monthly
                    }
                }
            case .computed(let signature, let dailyData, let monthlyData):
                babySummaryCacheByProfile[babyId] = EntryBabySummaryCacheEntry(
                    datasetSignature: signature,
                    daily: dailyData,
                    monthly: monthlyData
                )
                if babyDailySummariesByProfile[babyId] != dailyData {
                    babyDailySummariesByProfile[babyId] = dailyData
                }
                if babyMonthlySummariesByProfile[babyId] != monthlyData {
                    babyMonthlySummariesByProfile[babyId] = monthlyData
                }
            }

            logger.log(
                level: .info,
                tag: tag,
                message: "Baby dashboard data loaded: babyId=\(babyId), " +
                    "daily=\(babyDailySummariesByProfile[babyId]?.count ?? 0), " +
                    "monthly=\(babyMonthlySummariesByProfile[babyId]?.count ?? 0)"
            )
            return true
        } catch {
            logger.log(level: .error, tag: tag, message: "loadBabyDashboardData failed (babyId=\(babyId)): \(error.localizedDescription)")
            return false
        }
    }

    // MARK: - Helpers ---------------------------------------------------

    /// Update progress and streak based on current entries
    private func updateProgressAndStreakInternal() async {
        do {
            // Run the count and streak refresh together so sync/dashboard updates spend less
            // wall time waiting on serial data work. Both read OFF the main actor now (MOB-516):
            // getEntryCount() routes through the worker via a detached task; getStreak() already did.
            async let totalEntriesTask = getEntryCount()
            async let streakTask = getStreak()
            let totalEntries = try await totalEntriesTask
            let streakValue = try await streakTask
            let nextProgress = ProgressSummary(totalEntries: totalEntries, streak: streakValue.current)
            if progress.totalEntries != nextProgress.totalEntries || progress.streak != nextProgress.streak {
                progress = nextProgress
            }
            if streak != streakValue.current {
                streak = streakValue.current
            }

            logger.log(level: .debug, tag: tag, message: "Progress and streak updated: total=\(totalEntries), streak=\(streakValue.current)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update progress/streak: \(error.localizedDescription)")
        }
    }

    /// Check for goal achievements and trigger alerts if needed
    private func checkGoalAlerts() async {
        do {
            guard let latestEntry = try await getLatestEntry(),
                  let weight = latestEntry.scaleEntry?.weight else { return }
            // Weight is stored as tenths of lbs – cast to Double for compatibility
            await goalAlertService.showGoalMetMessage(currentWeight: Double(weight))

            // Also check if "Set a Goal" card should be shown (when 3+ entries and no goal)
            // Get entry count to pass as parameter (avoiding circular dependency)
            let entryCount = try await getEntryCount()
            await goalAlertService.checkSetGoalCard(entryCount: entryCount)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to evaluate goal alerts: \(error.localizedDescription)")
        }
    }

    private static func buildHistoryMonth(monthKey: String, monthEntries: [Entry]) -> HistoryMonth {
        // Build the `weights` concatenated string  "<w>|<ts>,<w>|<ts>"  like the SQL query
        let weightPairs = monthEntries.compactMap { entry -> String? in
            guard let weight = entry.scaleEntry?.weight else { return nil }
            return "\(weight)|\(entry.entryTimestamp)"
        }
        let weightsConcat = weightPairs.joined(separator: ",")

        // Numeric helpers - filter out zero values for average calculation
        let weightValues = monthEntries.compactMap { $0.scaleEntry?.weight }.filter { $0 > 0 }.map(Double.init)
        let avgWeight: Double? = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count)
        let minWeight = weightValues.min()
        let maxWeight = weightValues.max()

        // Change = last - first by timestamp order
        let sortedByTime = monthEntries.sorted { $0.entryTimestamp < $1.entryTimestamp }
        let firstWeight = sortedByTime.first?.scaleEntry?.weight
        let lastWeight = sortedByTime.last?.scaleEntry?.weight
        let change: String? = {
            guard let first = firstWeight, let last = lastWeight else { return nil }
            return String(format: "%.1f", Double(last - first))
        }()

        return HistoryMonth(
            id: monthKey,
            weight: avgWeight,
            entryTimestamp: monthKey,
            count: monthEntries.count,
            weights: weightsConcat,
            change: change,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            date: nil,
            time: nil,
            month: String(monthKey.suffix(2)),
            year: String(monthKey.prefix(4)),
            min: minWeight,
            max: maxWeight
        )
    }

    // MARK: - Entry Management (Moved from DashboardDataManager)

    /// Loads and aggregates all entry data for dashboard display
    /// Uses DTOs and background aggregation to avoid blocking main thread
    func loadDashboardData(entryType: EntryType = .scale) async {
        if let existingTask = activeDashboardLoadTasks[entryType] {
            let didSucceed = await existingTask.value
            if didSucceed { return }
        }

        let task = Task<Bool, Never> { [weak self] in
            guard let self else { return false }
            return await self.performDashboardDataLoad(entryType: entryType)
        }
        activeDashboardLoadTasks[entryType] = task

        _ = await task.value

        if activeDashboardLoadTasks[entryType] == task {
            activeDashboardLoadTasks[entryType] = nil
        }
    }

    private func performDashboardDataLoad(entryType: EntryType) async -> Bool {
        do {
            let accountId = try getAccountId()

            let allDTOs = try await getAllEntriesAsDTO()
            let totalEntries = allDTOs.count
            if lastLoggedEntryCountByAccount[accountId] != totalEntries {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Account total create type entries updated: accountId=\(accountId), totalEntries=\(totalEntries)"
                )
                lastLoggedEntryCountByAccount[accountId] = totalEntries
            }

            let cachedSignature = summaryCacheByEntryType[entryType]?.datasetSignature

            let result = await Task.detached(priority: .userInitiated) { [weak self] in
                guard let self else { return EntrySummaryLoadResult.computed(signature: 0, daily: [], monthly: []) }
                let dtos = allDTOs.filter { self.matchesDTOEntryType($0, entryType: entryType) }
                let signature = Self.makeDTOSignature(dtos)
                if signature == cachedSignature {
                    return EntrySummaryLoadResult.cached(signature: signature)
                }
                switch entryType {
                case .scale, .baby:
                    let daily = self.aggregateByDayFromDTOs(dtos, accountId: accountId)
                    let monthly = self.aggregateByMonthFromDTOs(dtos, accountId: accountId)
                    return EntrySummaryLoadResult.computed(signature: signature, daily: daily, monthly: monthly)
                case .bpm:
                    let daily = self.aggregateBpmByDayFromDTOs(dtos, accountId: accountId)
                    let monthly = self.aggregateBpmByMonthFromDTOs(dtos, accountId: accountId)
                    return EntrySummaryLoadResult.computed(signature: signature, daily: daily, monthly: monthly)
                }
            }.value

            switch result {
            case .cached(let signature):
                if let cached = summaryCacheByEntryType[entryType], cached.datasetSignature == signature {
                    applySummaryPayload(daily: cached.daily, monthly: cached.monthly, entryType: entryType, accountId: accountId)
                }
            case .computed(let signature, let dailyData, let monthlyData):
                summaryCacheByEntryType[entryType] = EntrySummaryCacheEntry(
                    datasetSignature: signature,
                    daily: dailyData,
                    monthly: monthlyData
                )
                applySummaryPayload(daily: dailyData, monthly: monthlyData, entryType: entryType, accountId: accountId)
            }
            return true
        } catch {
            logger.log(level: .error, tag: tag, message: "loadDashboardData failed (\(entryType)): \(error.localizedDescription)")
            return false
        }
    }

    /// Handles entry addition by updating affected day and month summaries
    func handleEntryAdded(_ entry: Entry) async throws {
        let accountId = try getAccountId()

        logger.log(level: .debug, tag: tag, message: "Handling entry addition: \(entry.id)")

        let dayKey = DateTimeTools.getLocalDateStringFromUTCDate(entry.entryTimestamp)
        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)

        // Fetch all entries for the affected day and month
        let dayEntries = try await getEntries(forDay: dayKey)
        let monthEntries = try await getEntries(forMonth: monthKey)

        // Update summaries with aggregated data
        let dailySummary = aggregateByDay(entries: dayEntries, accountId: accountId).first
        let monthlySummary = aggregateByMonth(entries: monthEntries, accountId: accountId).first

        if let dailySummary = dailySummary {
            updateDailySummary(dayKey: dayKey, summary: dailySummary)
        }
        if let monthlySummary = monthlySummary {
            updateMonthlySummary(monthKey: monthKey, summary: monthlySummary)
        }

        // Notify the UI immediately — the row is written and the affected day/month
        // summaries are updated, so History/Dashboard can refresh and the save's loader
        // can dismiss now. The full-dataset progress/streak recompute below re-reads all
        // entries, so it MUST run behind the UI, not block the save (MOB-1433).
        let notification = EntryNotification(from: entry)
        entrySaved.send(notification)

        scheduleProgressAndStreakRefresh()

        // Forward to health integrations (e.g. HealthKit) BEHIND the UI — never block the
        // save on it. On this account HealthKit is off, yet syncNewEntry took ~1.8s: its
        // single `getStoredIntegrationData()` main-context read was stalled behind a
        // concurrent worker fetch on the shared store (MOB-1433 §5c). The row is already
        // persisted and queued for push, so the forward is best-effort.
        let hkNotification = EntryNotification(from: entry)
        Task { [weak self] in
            guard let self else { return }
            do {
                try await self.integrationService.syncNewEntry(notification: hkNotification)
            } catch {
                self.logger.log(level: .error, tag: self.tag, message: "Failed to sync new entry to integrations: \(error.localizedDescription)")
            }
        }
    }

    /// Schedules the full-dataset progress/streak recompute — and the goal-alert check —
    /// to run behind the UI, coalescing rapid successive adds onto a single trailing run
    /// (see `progressRefreshTask`). Use this on the local-add path; `performSync` still
    /// awaits `updateProgressAndStreakInternal()` directly since it runs off the UI's
    /// critical path already.
    private func scheduleProgressAndStreakRefresh() {
        progressRefreshTask?.cancel()
        progressRefreshTask = Task { [weak self] in
            await self?.updateProgressAndStreakInternal()
            // Check goal alerts AFTER the progress/streak recompute so their cheap
            // main-context reads (getLatestEntry/getEntryCount) don't contend with that
            // full-dataset worker read on the shared store. That contention was pushing the
            // save's success toast out by seconds once the HealthKit forward was moved off
            // the critical path (MOB-1433 §5c).
            await self?.checkGoalAlerts()
        }
    }

    /// Handles entry update by treating as delete + add
    func handleEntryUpdated(_ entry: Entry) async throws {
        logger.log(level: .debug, tag: tag, message: "Handling entry update: \(entry.id)")

        // For updates, we can treat as delete + add for simplicity
        try await handleEntryDeleted(entry)
        try await handleEntryAdded(entry)
    }

    /// Lightweight variant for sync loop: handles entry deletion using extracted primitives only.
    /// Used when @Model Entry is not safely accessible after async boundary (R7).
    /// Skips integration cleanup since that happens at original user-delete time.
    private func handleEntryDeleted(entryId: UUID, entryTimestamp: String) async throws {
        let accountId = try getAccountId()

        logger.log(level: .debug, tag: tag, message: "Handling entry deletion (sync): \(entryId)")

        let dayKey = DateTimeTools.getLocalDateStringFromUTCDate(entryTimestamp)
        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entryTimestamp)

        let dayEntries = try await getEntries(forDay: dayKey)
        let monthEntries = try await getEntries(forMonth: monthKey)

        let dailySummary = aggregateByDay(entries: dayEntries, accountId: accountId).first
        let monthlySummary = aggregateByMonth(entries: monthEntries, accountId: accountId).first

        updateDailySummary(dayKey: dayKey, summary: dailySummary.flatMap { $0 })
        updateMonthlySummary(monthKey: monthKey, summary: monthlySummary.flatMap { $0 })

        // Minimal notification — entry already deleted locally, no relationship data needed
        let dto = BathScaleOperationDTO(
            accountId: accountId,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: entryTimestamp,
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "delete",
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
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
        entryDeleted.send(EntryNotification(from: dto, id: entryId))
    }

    /// Handles entry deletion by updating affected day and month summaries
    func handleEntryDeleted(_ entry: Entry) async throws {
        let accountId = try getAccountId()

        logger.log(level: .debug, tag: tag, message: "Handling entry deletion: \(entry.id)")

        let dayKey = DateTimeTools.getLocalDateStringFromUTCDate(entry.entryTimestamp)
        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)

        // Fetch remaining entries for the affected day and month
        let dayEntries = try await getEntries(forDay: dayKey)
        let monthEntries = try await getEntries(forMonth: monthKey)

        // Update summaries with aggregated data
        let dailySummary = aggregateByDay(entries: dayEntries, accountId: accountId).first
        let monthlySummary = aggregateByMonth(entries: monthEntries, accountId: accountId).first

        if let dailySummary = dailySummary {
            updateDailySummary(dayKey: dayKey, summary: dailySummary)
        } else {
            // If no entries left for the day, remove summary
            updateDailySummary(dayKey: dayKey, summary: nil)
        }
        if let monthlySummary = monthlySummary {
            updateMonthlySummary(monthKey: monthKey, summary: monthlySummary)
        } else {
            // If no entries left for the month, remove summary
            updateMonthlySummary(monthKey: monthKey, summary: nil)
        }

        // Create notification on MainActor to safely extract relationship data
        let notification = EntryNotification(from: entry)
        entryDeleted.send(notification)

        // Trigger integration delete for the removed entry (e.g., HealthKit)
        do {
            try await integrationService.deleteEntry(entry)
        } catch {
            // Log error but don't fail the entry deletion process
            logger.log(level: .error, tag: tag, message: "Failed to delete entry from integrations: \(error.localizedDescription)")
        }
    }

    // MARK: - Private Helper Methods

    private func applySummaryPayload(
        daily: [BathScaleWeightSummary],
        monthly: [BathScaleWeightSummary],
        entryType: EntryType,
        accountId: String
    ) {
        switch entryType {
        case .scale, .baby:
            if dailySummaries != daily {
                dailySummaries = daily
            }
            if monthlySummaries != monthly {
                monthlySummaries = monthly
            }
        case .bpm:
            if bpmDailySummaries != daily {
                bpmDailySummaries = daily
            }
            if bpmMonthlySummaries != monthly {
                bpmMonthlySummaries = monthly
            }
        }
    }

    private func invalidateSummaryCaches(for entryType: EntryType? = nil) {
        if let entryType {
            summaryCacheByEntryType[entryType] = nil
            streakCacheByEntryType[entryType] = nil
            progressCacheByEntryType[entryType] = nil
        } else {
            summaryCacheByEntryType.removeAll()
            streakCacheByEntryType.removeAll()
            progressCacheByEntryType.removeAll()
        }
    }

    /// Updates the daily summary for a specific day key
    private func updateDailySummary(dayKey: String, summary: BathScaleWeightSummary?) {
        if let summary = summary {
            // Update existing or add new
            if let index = dailySummaries.firstIndex(where: { $0.period == dayKey }) {
                dailySummaries[index] = summary
            } else {
                dailySummaries.append(summary)
                dailySummaries.sort { $0.period < $1.period }
            }
        } else {
            // Remove if no summary (empty day)
            dailySummaries.removeAll { $0.period == dayKey }
        }
        invalidateSummaryCaches(for: .scale)
    }

    /// Updates the monthly summary for a specific month key
    private func updateMonthlySummary(monthKey: String, summary: BathScaleWeightSummary?) {
        if let summary = summary {
            // Update existing or add new
            if let index = monthlySummaries.firstIndex(where: { $0.period == monthKey }) {
                monthlySummaries[index] = summary
            } else {
                monthlySummaries.append(summary)
                monthlySummaries.sort { $0.period < $1.period }
            }
        } else {
            // Remove if no summary (empty month)
            monthlySummaries.removeAll { $0.period == monthKey }
        }
        invalidateSummaryCaches(for: .scale)
    }

    // MARK: - BPM Entry CRUD

    func createBpmEntry(_ dto: BpmOperationDTO) async throws {
        let accountId = try getAccountId()
        let bpmDTO = BpmOperationDTO(
            accountId: accountId,
            systolic: dto.systolic,
            diastolic: dto.diastolic,
            pulse: dto.pulse,
            meanArterial: dto.meanArterial,
            note: dto.note,
            source: dto.source,
            unit: dto.unit,
            entryTimestamp: dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date()),
            operationType: OperationType.create.rawValue,
            serverTimestamp: dto.serverTimestamp
        )

        let entry = Entry(from: bpmDTO, accountId: accountId, isSynced: false)
        entry.attempts = 0

        do {
            try await localRepo.saveEntry(entry)
            await refreshBpmDashboardSummaries()
            logger.log(
                level: .info,
                tag: tag,
                message: "BPM entry saved locally: entryId=\(entry.id.uuidString), accountId=\(accountId)"
            )

            let notification = EntryNotification(from: entry)
            entrySaved.send(notification)
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to save BPM entry: accountId=\(accountId), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    func fetchBpmEntries() async throws -> [BpmOperationDTO] {
        let accountId = try getAccountId()
        return try await localRepo.fetchEntriesAsBpmDTO(forUserId: accountId, operationType: OperationType.create.rawValue)
    }

    func deleteBpmEntry(entryTimestamp: String) async throws {
        let accountId = try getAccountId()

        let entries = try await localRepo.fetchEntriesOfTimestamp(forUserId: accountId, timestamp: entryTimestamp)
        guard let entry = entries.first(where: { $0.entryType == EntryType.bpm.rawValue }) else {
            throw NSError(domain: "EntryService", code: 404, userInfo: [NSLocalizedDescriptionKey: "BPM entry not found"])
        }

        entry.operationType = OperationType.delete.rawValue
        entry.isSynced = false

        do {
            try await localRepo.updateEntry(entry)
            await refreshBpmDashboardSummaries()
            let notification = EntryNotification(from: entry)
            entryDeleted.send(notification)
            logger.log(
                level: .info,
                tag: tag,
                message: "BPM entry delete queued: entryTimestamp=\(entryTimestamp), accountId=\(accountId)"
            )
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "BPM entry delete failed: entryTimestamp=\(entryTimestamp), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    // MARK: - Baby Entry CRUD

    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String? = nil) async throws {
        let accountId = try getAccountId()

        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: false
        )
        entry.attempts = 0
        entry.note = note
        entry.babyEntry = BabyEntry(babyId: babyId, length: length, weight: weight, source: source)

        do {
            try await localRepo.saveEntry(entry)
            logger.log(
                level: .info,
                tag: tag,
                message: "Baby entry saved locally: entryId=\(entry.id.uuidString), accountId=\(accountId), babyId=\(babyId)"
            )

            let notification = EntryNotification(from: entry)
            entrySaved.send(notification)
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to save baby entry: accountId=\(accountId), babyId=\(babyId), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    private func refreshBpmDashboardSummaries() async {
        await loadDashboardData(entryType: .bpm)
    }

    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
