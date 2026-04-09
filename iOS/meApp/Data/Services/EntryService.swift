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
    @MainActor static let shared = EntryService(accountService: AccountService.shared)

    // MARK: - Publishers ------------------------------------------------

    /// Emits each time a new entry is locally stored (create).
    /// Uses EntryNotification (Sendable) to safely pass data across actor boundaries.
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    /// Emits each time an entry is deleted locally.
    /// Uses EntryNotification (Sendable) to safely pass data across actor boundaries.
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    let tag = "EntryService"

    @Published var isSyncing: Bool = false
    @Published var lastSyncTime: Date?
    @Published var progress: ProgressSummary = .empty
    @Published var streak: Int = 0

    // MARK: - Dashboard Data (Weight)

    @Published var dailySummaries: [BathScaleWeightSummary] = []
    @Published var monthlySummaries: [BathScaleWeightSummary] = []

    // MARK: - Dashboard Data (BPM)

    @Published var bpmDailySummaries: [BathScaleWeightSummary] = []
    @Published var bpmMonthlySummaries: [BathScaleWeightSummary] = []

    private var cancellables = Set<AnyCancellable>()
    private var lastAccountId: String?
    private var lastLoggedEntryCountByAccount: [String: Int] = [:]
    /// Tracks the active sync task so concurrent callers can await it instead of skipping.
    private var activeSyncTask: Task<Void, Never>?

    @MainActor
    init(
        accountService: AccountServiceProtocol,
        localRepo: EntryRepositoryProtocol? = nil,
        localKVRepo: EntrySyncStoreProtocol? = nil,
        remoteRepo: EntryRepositoryAPIProtocol? = nil,
        migrationService: SQLiteMigrationService? = nil
    ) {
        self.accountService = accountService
        self.localRepo = localRepo ?? EntryRepository()
        self.localKVRepo = localKVRepo ?? EntryRepositoryLocal()
        self.remoteRepo = remoteRepo ?? EntryRepositoryAPI()
        self.migrationService = migrationService ?? SQLiteMigrationService()

        Task { @MainActor in
            if let concreteAccountService = accountService as? AccountService {
                concreteAccountService.$activeAccount
                    .map { $0?.accountId }
                    .removeDuplicates()
                    .dropFirst()
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] accountId in
                        guard let self = self else { return }
                        Task { @MainActor in
                            let accountChanged = self.lastAccountId != nil && self.lastAccountId != accountId
                            self.lastAccountId = accountId

                            if accountChanged, accountId != nil {
                                try? await self.clearLastSyncTimestamp()
                                await self.syncAllEntriesWithRemote()
                                await self.loadDashboardData(entryType: .wg)
                            }
                        }
                    }
                    .store(in: &self.cancellables)
            }
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
            accountService.activeAccount?.goalSettings?.initialWeight.map(Int.init)
        }
    }

    /// Reads dashboard type on MainActor to avoid crossing SwiftData model objects between executors.
    private func getDashboardType() async -> String? {
        await MainActor.run {
            accountService.activeAccount?.dashboardSettings?.dashboardType
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

            // Broadcast change
            await syncUnsyncedEntries()
            await checkGoalAlerts()
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
            await syncUnsyncedEntries()
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
            await syncUnsyncedEntries()
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

    // MARK: - Query

    func getAllEntries() async throws -> [Entry] {
        let accountId = try getAccountId()
        return try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
    }

    /// Returns all entries as DTOs with DTO conversion done on background thread.
    /// Use this instead of getAllEntries() when you only need to read entry data
    /// to avoid blocking the main thread with toOperationDTO() calls.
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] {
        let accountId = try getAccountId()
        return try await localRepo.fetchEntriesAsDTO(forUserId: accountId, operationType: OperationType.create.rawValue)
    }

    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool {
        let accountId = try getAccountId()
        return try await localRepo.checkEntryTimestampExists(forUserId: accountId, entryTimestamp: entryTimestamp)
    }

    func getEntryCount() async throws -> Int {
        let accountId = try getAccountId()
        return try await localRepo.fetchEntryCount(forUserId: accountId)
    }

    func getOldestEntry() async throws -> Entry? {
        let accountId = try getAccountId()
        return try await localRepo.fetchOldestEntry(forUserId: accountId)
    }

    func getLatestEntry() async throws -> Entry? {
        let accountId = try getAccountId()
        return try await localRepo.fetchLatestEntry(forUserId: accountId)
    }

    func getEntries(lastNDays: Int, entryType: EntryType = .wg) async throws -> [Entry] {
        let accountId = try getAccountId()
        let entries = try await localRepo.fetchEntries(lastNDays: lastNDays, userId: accountId)
        return entries.filter { matchesEntryType($0, entryType: entryType) }
    }

    func getEntries(forMonth month: String, entryType: EntryType = .wg) async throws -> [Entry] {
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

    func getMonthsAll(entryType: EntryType = .wg) async throws -> [HistoryMonth] {
        let accountId = try getAccountId()
        let allEntries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        let entries = allEntries.filter { matchesEntryType($0, entryType: entryType) }
        // Group by YYYY-MM prefix, converting UTC timestamps to local timezone
        let grouped = Dictionary(grouping: entries) { DateTimeTools.getLocalMonthStringFromUTCDate($0.entryTimestamp) }

        var result: [HistoryMonth] = []

        guard let validMonthRegex = try? NSRegularExpression(pattern: "^\\d{4}-\\d{2}$") else {
            return []
        }

        for (monthKey, monthEntries) in grouped {
            // Skip keys that are not in YYYY-MM format (e.g., malformed keys)
            guard validMonthRegex.firstMatch(in: monthKey, range: NSRange(location: 0, length: monthKey.count)) != nil else { continue }

            result.append(Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries))
        }

        // Sort descending by month key
        return result.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    func getMonthDetail(month: String, entryType: EntryType = .wg) async throws -> [Entry] {
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

    /// Helper struct to hold extracted entry weights and DTOs
    private struct ExtractedEntryData {
        let latestWeight: Int
        let weekStartWeight: Int?
        let monthStartWeight: Int?
        let firstEntryWeight: Int?
        let weekDTO: BathScaleOperationDTO?
        let monthDTO: BathScaleOperationDTO?
        let firstDTO: BathScaleOperationDTO?
        let latestDTO: BathScaleOperationDTO
    }

    func getProgress(entryType: EntryType = .wg) async throws -> Progress {
        let accountId = try getAccountId()

        // Use SwiftDataWorker for thread-safe access to SwiftData relationships
        // All relationship data is extracted within the worker's isolated context
        let worker = SwiftDataWorker(modelContainer: PersistenceController.shared.container)
        let fetchResult = try await worker.fetchProgressData(accountId: accountId)

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

        // -- Year delta logic, extracted to a helper for clarity --
        let monthSeries = try await getMonthYear()
        let yearDeltaResult = try await calculateYearDelta(latestWeight: latestWeight, monthStartWeight: monthStartWeight, monthSeries: monthSeries)
        let yearDelta = yearDeltaResult.yearDelta
        let yearStartDTO = yearDeltaResult.yearStartDTO
        let yearKey = yearDeltaResult.yearKey

        let goalInitial = await getGoalInitialWeight()
        let initialWeight: Int?
        if let goalInitial, goalInitial > 0 {
            initialWeight = goalInitial
        } else {
            initialWeight = firstEntryWeight
        }
        let totalDelta = latestWeight - (initialWeight ?? latestWeight)

        let streak = try await getStreak(entryType: entryType)

        logger.log(
            level: .debug,
            tag: tag,
            message: "Progress(year): latest=\(latestWeight), yearKey=\(yearKey), yearDelta=\(yearDelta)"
        )

        return Progress(
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
        df.dateFormat = "yyyy-MM-dd"
        df.timeZone = TimeZone(secondsFromGMT: 0)
        return df
    }()

    private func isoString(from date: Date) -> String {
        return Self.isoDayFormatter.string(from: date)
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

    func getStreak(entryType: EntryType = .wg) async throws -> Streak {
        let accountId = try getAccountId()
        let allEntries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        let entries = allEntries.filter { matchesEntryType($0, entryType: entryType) }
        let calendar = Calendar.current

        // Extract unique calendar days (start-of-day in local timezone) so comparisons are consistent.
        // Exclude invalid/placeholder strings so they are not treated as "today" (getDateFromDateString fallback).
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

        // Walk backward: for each consecutive day in uniqueDaysDescending (newest to oldest), count while it matches dateToCheck
        for day in uniqueDaysDescending.dropFirst() {
            if isSameDay(day, dateToCheck) {
                guard let previousDate = calendar.date(byAdding: .day, value: -1, to: dateToCheck) else {
                    break
                }
                currentStreak += 1
                dateToCheck = previousDate
            } else {
                break
            }
        }

        let longestStreak = Self.computeLongestStreak(from: uniqueDaysAscending, calendar: calendar)
        return Streak(current: currentStreak, max: longestStreak)
    }

    private static func computeLongestStreak(from days: [Date], calendar: Calendar = .current) -> Int {
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

    // MARK: - Sync Logic

    /// Sync all unsynced entries with the remote backend. Call this on app start or after network recovery.
    /// If a sync is already in progress, callers await its completion (zero CPU overhead) instead of skipping.
    func syncAllEntriesWithRemote() async {
        // If a sync is already running, piggyback on it — await the same task.
        if let existingTask = activeSyncTask {
            await existingTask.value
            return
        }

        let task = Task { [weak self] in
            guard let self else { return }
            await self.performSync()
        }
        activeSyncTask = task
        isSyncing = true

        await task.value

        activeSyncTask = nil
        isSyncing = false
    }

    /// The actual sync work — only one instance runs at a time.
    private func performSync() async {
        let accountId: String
        do {
            accountId = try getAccountId()
        } catch {
            logger.log(level: .error, tag: tag, message: "Sync failed: No account ID available")
            return
        }
        logger.log(level: .info, tag: tag, message: "Full entry sync started: accountId=\(accountId)")

        do {
            let hadPushedCreates = await pushUnsyncedEntriesToRemote(accountId: accountId)

            let localEntryCount = try? await localRepo.fetchEntryCount(forUserId: accountId)
            var lastSyncTimestamp = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)

            if localEntryCount == 0 {
                try? await localKVRepo.clearLastSyncTimestamp(accountId: accountId)
                lastSyncTimestamp = nil
            }

            let remoteOps = try await remoteRepo.fetchOperations(startTimestamp: lastSyncTimestamp)
            let hadMergedNewCreates = await mergeRemoteOperations(remoteOps.operations, accountId: accountId)
            await loadDashboardData()

            try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: remoteOps.timestamp)
            lastSyncTime = Date()

            await updateProgressAndStreakInternal()
            if hadPushedCreates || hadMergedNewCreates {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                await checkGoalAlerts()
            }
            logger.log(
                level: .info,
                tag: tag,
                message: """
                Full entry sync completed successfully: accountId=\(accountId), hadPushedCreates=\(hadPushedCreates), \
                hadMergedNewCreates=\(hadMergedNewCreates), remoteOperationCount=\(remoteOps.operations.count)
                """
            )

        } catch {
            logger.log(level: .error, tag: tag, message: "Full entry sync failed: accountId=\(accountId), error=\(error.localizedDescription)")
        }
    }

    /// Pushes unsynced local entries to the remote API.
    ///
    /// - Returns: `true` if at least one create operation was successfully synced.
    ///   The caller uses this to decide whether to show the goal met card: we only show it when
    ///   the user actually added new entries in this sync (not on every login or pull-to-refresh).
    private func pushUnsyncedEntriesToRemote(accountId: String) async -> Bool { // swiftlint:disable:this function_body_length
        // 1. Get all unsynced entries (both new and delete operations)
        let unsynced = try? await localRepo.fetchUnsyncedEntries(forUserId: accountId)

        // Tracks whether we successfully synced at least one create to the API; drives goal met card visibility.
        var hadSuccessfulCreate = false
        var successfulCreateCount = 0
        var successfulDeleteCount = 0
        var failedSyncCount = 0
        var firstFailureReason: String?

        // 2. Try to sync with backend
        if let unsyncedEntries = unsynced, !unsyncedEntries.isEmpty {
            for operation in unsyncedEntries {
                // R7/R9: Extract all @Model data BEFORE any await calls
                let entryId = operation.id
                let entryIdString = entryId.uuidString
                let operationType = operation.operationType
                let entryTimestamp = operation.entryTimestamp
                let currentAttempts = operation.attempts
                let dto = operation.toOperationDTO()

                do {
                    try await remoteRepo.syncOperation(operation: dto)

                    if operationType == "create" {
                        hadSuccessfulCreate = true
                        // R9: Use primitive-based update instead of mutating @Model
                        try await localRepo.updateEntrySyncStatus(
                            entryId: entryIdString,
                            isSynced: true,
                            isFailedToSync: false,
                            attempts: currentAttempts
                        )
                        successfulCreateCount += 1
                    } else {
                        try await localRepo.deleteEntry(byId: entryIdString)
                        try await handleEntryDeleted(entryId: entryId, entryTimestamp: entryTimestamp)
                        successfulDeleteCount += 1
                    }
                } catch {
                    // R9: Compute new sync values from extracted primitives (no @Model mutation)
                    let newAttempts = currentAttempts + 1
                    let markAsFailed = newAttempts > 8

                    try? await localRepo.updateEntrySyncStatus(
                        entryId: entryIdString,
                        isSynced: markAsFailed,
                        isFailedToSync: markAsFailed,
                        attempts: newAttempts
                    )
                    failedSyncCount += 1
                    if firstFailureReason == nil {
                        firstFailureReason = error.localizedDescription
                    }
                }
            }
            logger.log(
                level: .info,
                tag: tag,
                message: "Unsynced entry push completed for accountId=\(accountId): "
                    + "createsSynced=\(successfulCreateCount), deletesSynced=\(successfulDeleteCount), failures=\(failedSyncCount)"
            )
            if failedSyncCount > 0 {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Unsynced entry push had failures: accountId=\(accountId), failures=\(failedSyncCount), "
                        + "firstFailure=\(firstFailureReason ?? "unknown")"
                )
            }
        }
        return hadSuccessfulCreate
    }

    /// Lightweight summary for a single month. Avoids computing all months when only one changes.
    func getMonthSummary(monthKey: String) async throws -> HistoryMonth? {
        let monthEntries = try await getEntries(forMonth: monthKey)
        guard !monthEntries.isEmpty else { return nil }
        return Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries)
    }

    /// Internal: Sync only unsynced entries (used after local changes)
    private func syncUnsyncedEntries() async {
        guard !isSyncing else { return }

        isSyncing = true
        defer { isSyncing = false }

        guard let accountId = try? getAccountId() else {
            logger.log(level: .error, tag: tag, message: "Unsynced entries sync failed: No account ID available")
            return
        }

        do {
            // 1. Push unsynced entries to remote (return value unused; goal alerts handled by saveNewEntry)
            _ = await pushUnsyncedEntriesToRemote(accountId: accountId)

            // After syncing, update last sync timestamp and local state
            var lastSyncTimestamp = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)
            if (try? await localRepo.fetchEntryCount(forUserId: accountId)) == 0 {
                try? await localKVRepo.clearLastSyncTimestamp(accountId: accountId)
                lastSyncTimestamp = nil
            }

            let remoteOps = try await remoteRepo.fetchOperations(startTimestamp: lastSyncTimestamp)
            if !remoteOps.operations.isEmpty {
                _ = await mergeRemoteOperations(remoteOps.operations, accountId: accountId)
                try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: remoteOps.timestamp)
            } else {
                try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: ISO8601DateFormatter().string(from: Date()))
            }

            lastSyncTime = Date()
            // Update progress, streak, and check for goal alerts
            await updateProgressAndStreakInternal()
            await loadDashboardData()
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Unsynced entries sync failed: accountId=\(accountId), error=\(error.localizedDescription)"
            )
        }
    }

    /// Merge remote operations into local DB, resolving conflicts (latest wins by timestamp).
    ///
    /// - Returns: `true` if at least one new entry was inserted (create from remote that didn't exist locally).
    ///   The caller uses this to decide whether to show the goal met card: we only show it when
    ///   new entries arrived from the server in this sync (not on every login or pull-to-refresh).
    private func mergeRemoteOperations( // swiftlint:disable:this cyclomatic_complexity function_body_length
        _ remoteOps: [BathScaleOperationDTO],
        accountId: String
    ) async -> Bool {
        var hadNewCreates = false
        // Group operations by timestamp to determine final state for each timestamp
        let groupedOps = Dictionary(grouping: remoteOps) { op in
            op.entryTimestamp ?? ""
        }
        for (timestamp, ops) in groupedOps {
            guard !timestamp.isEmpty else { continue }

            // Sort operations by serverTimestamp to process in chronological order
            let sortedOps = ops.sorted {
                ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "")
            }

            // Find the final operation for this timestamp (the latest one)
            guard let finalOp = sortedOps.last else { continue }

            // Check if local entry exists with this timestamp
            // Normalize timestamp format variants to improve matching reliability
            // Some entries may be stored with millisecond precision (".000Z"); others without.
            let normalizedTimestamp = timestamp.replacingOccurrences(of: ".000Z", with: "Z")
            let tsCandidates = timestamp == normalizedTimestamp ? [timestamp] : [timestamp, normalizedTimestamp]

            // Fetch ALL entries with this timestamp (including both create and delete operations)
            var localEntry: Entry?
            var localEntries: [Entry]?
            for ts in tsCandidates {
                if let fetched = try? await localRepo.fetchEntriesOfTimestamp(forUserId: accountId, timestamp: ts), !fetched.isEmpty {
                    localEntries = fetched
                    localEntry = fetched.first { $0.operationType == OperationType.create.rawValue } ?? fetched.first
                    break
                }
            }

            // Additional check: Look for entries with same timestamp AND weight to prevent race condition duplicates
            let potentialDuplicates = localEntries?.filter { entry in
                if let entryWeight = entry.scaleEntry?.weight, let opWeight = finalOp.weight {
                    return entryWeight == Int(opWeight) && tsCandidates.contains(entry.entryTimestamp)
                }
                return false
            } ?? []

            // If no entries found by timestamp but we have a weight, check all entries for this user to find potential duplicates
            // This handles the case where the entry was just synced but not yet visible in the timestamp query
            var allEntriesForUser: [Entry] = []
            if localEntries?.isEmpty == true && finalOp.weight != nil {
                do {
                    allEntriesForUser = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
                } catch {
                    allEntriesForUser = []
                }
            }

            // Determine the entry to work with - either from timestamp search or weight-based search
            let entryToProcess = localEntry ?? allEntriesForUser.first { entry in
                if let entryWeight = entry.scaleEntry?.weight, let opWeight = finalOp.weight {
                    return entryWeight == Int(opWeight) && entry.entryTimestamp == normalizedTimestamp
                }
                return false
            }

            if let localEntry = entryToProcess {
                // Local entry exists - compare server timestamps
                let localServerTS = localEntry.serverTimestamp ?? ""
                let remoteServerTS = finalOp.serverTimestamp ?? ""

                let shouldApplyRemote = localServerTS.isEmpty || remoteServerTS > localServerTS

                if shouldApplyRemote {
                    if finalOp.operationType == OperationType.delete.rawValue {
                        let entriesToDelete = localEntries ?? [localEntry]
                        for entry in entriesToDelete {
                            try? await localRepo.deleteEntry(byId: entry.id.uuidString)
                        }
                        try? await handleEntryDeleted(localEntry)
                    } else {
                        await cleanupDuplicates(localEntries: localEntries, keepId: localEntry.id)
                        let updated = Entry(from: finalOp, accountId: accountId, isSynced: true)
                        updated.id = localEntry.id
                        try? await localRepo.updateEntry(updated)
                    }
                } else if !localServerTS.isEmpty {
                    if !localEntry.isSynced {
                        localEntry.isSynced = true
                        try? await localRepo.updateEntry(localEntry)
                    }
                    await cleanupDuplicates(localEntries: localEntries, keepId: localEntry.id)
                }
            } else if !potentialDuplicates.isEmpty {
                // Found potential duplicate by weight - update the existing one instead of creating new
                guard let duplicateEntry = potentialDuplicates.first else {
                    continue
                }
                let updated = Entry(from: finalOp, accountId: accountId, isSynced: true)
                updated.id = duplicateEntry.id
                try? await localRepo.updateEntry(updated)
            } else {
                // No local entry - only create if final operation is create
                if finalOp.operationType == OperationType.create.rawValue {
                    hadNewCreates = true
                    // Final state is create - add to local storage
                    let newEntry = Entry(from: finalOp, accountId: accountId, isSynced: true)
                    try? await localRepo.saveEntry(newEntry)
                    // Notify downstream listeners/UI about the new entry so lists refresh
                }
                // If final operation is delete and no local entry exists, nothing to do
                // (entry was already deleted or never existed locally)
            }
        }

        do {
            let latestEntry = try await getLatestEntry()
            if let entry = latestEntry {
                // Create notification on MainActor to safely extract relationship data
                let notification = EntryNotification(from: entry)
                entrySaved.send(notification)
                try await handleEntryAdded(entry)
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to get latest entry: \(error.localizedDescription)")
        }
        return hadNewCreates
    }

    private func cleanupDuplicates(localEntries: [Entry]?, keepId: UUID) async {
        guard let allEntries = localEntries, allEntries.count > 1 else { return }
        for entry in allEntries where entry.id != keepId {
            do {
                try await localRepo.deleteEntry(byId: entry.id.uuidString)
                try await self.handleEntryDeleted(entry)
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to delete duplicate entry \(entry.id): \(error.localizedDescription)"
                )
            }
        }
    }

    // MARK: - Export

    /// Exports entries as CSV based on current dashboard type (4 or 12 metrics)
    func exportCSV() async throws {
        // Determine account and dashboard setting
        guard let dashboardType = await getDashboardType() else {
            throw AccountError.noActiveAccount
        }
        let useR4Endpoint = dashboardType == DashboardType.dashboard12.rawValue
        _ = try await remoteRepo.exportCsv(useR4Endpoint: useR4Endpoint)
    }

    // MARK: - Entry Type Filtering

    /// Checks if an Entry matches the given entryType.
    /// Entries without an entryType (legacy data) default to `.wg`.
    private func matchesEntryType(_ entry: Entry, entryType: EntryType) -> Bool {
        let type = entry.entryType
        if type.isEmpty { return entryType == .wg }
        return type == entryType.rawValue
    }

    /// DTO-level entry type matching for background-thread aggregation.
    private nonisolated func matchesDTOEntryType(_ dto: BathScaleOperationDTO, entryType: EntryType) -> Bool {
        guard let type = dto.entryType, !type.isEmpty else { return entryType == .wg }
        return type == entryType.rawValue
    }

    // MARK: - Aggregation Helpers

    /// Helper function for all metrics (excludes zero values)
    private nonisolated func avgNonZero(_ values: [Double?]) -> Double? {
        let vals = values.compactMap { $0 }.filter { $0 > 0 }
        return vals.isEmpty ? nil : vals.reduce(0, +) / Double(vals.count)
    }

    /// Helper function for weight: average stored values (tenths of lbs) and round to whole tenths
    /// This matches the logic in buildHistoryMonth to ensure consistent rounding
    private nonisolated func avgWeight(_ values: [Int]) -> Double {
        guard !values.isEmpty else { return 0 }
        let filtered = values.filter { $0 > 0 }
        guard !filtered.isEmpty else { return 0 }
        // Round average to whole tenths of lbs, then convert to Double
        return Double(Int(round(Double(filtered.reduce(0, +)) / Double(filtered.count))))
    }

    /// Aggregate entries by day, returning BathScaleWeightSummary for each day
    func aggregateByDay(entries: [Entry], accountId: String) -> [BathScaleWeightSummary?] {
        // Group entries by day (YYYY-MM-DD), converting UTC to local timezone
        let grouped = Dictionary(grouping: entries) { entry -> String in
            return DateTimeTools.getLocalDateStringFromUTCDate(entry.entryTimestamp)
        }

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
            let latestTimestamp = validEntries.map { $0.entryTimestamp }.max() ?? ""
            let count = validEntries.count

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

    /// Aggregate DTOs by day on background thread - avoids SwiftData relationship access
    private nonisolated func aggregateByDayFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        let grouped = Dictionary(grouping: dtos) { dto -> String in
            guard let ts = dto.entryTimestamp else { return "" }
            return DateTimeTools.getLocalDateStringFromUTCDate(ts)
        }

        return grouped.compactMap { day, dayDTOs -> BathScaleWeightSummary? in
            guard !day.isEmpty else { return nil }
            let validDTOs = dayDTOs.filter { ($0.weight ?? 0) > 0 }
            guard !validDTOs.isEmpty else { return nil }

            let date = DateTimeTools.getDateFromDateString(day, format: "yyyy-MM-dd")
            let latestTimestamp = validDTOs.compactMap { $0.entryTimestamp }.max() ?? ""

            return BathScaleWeightSummary(
                accountId: accountId,
                period: day,
                entryTimestamp: latestTimestamp,
                date: date,
                count: validDTOs.count,
                weight: avgWeight(validDTOs.compactMap { $0.weight.map { Int($0) } }),
                bodyFat: avgNonZero(validDTOs.compactMap { $0.bodyFat }),
                muscleMass: avgNonZero(validDTOs.compactMap { $0.muscleMass }),
                water: avgNonZero(validDTOs.compactMap { $0.water }),
                bmi: avgNonZero(validDTOs.compactMap { $0.bmi }),
                bmr: avgNonZero(validDTOs.compactMap { $0.bmr }),
                metabolicAge: avgNonZero(validDTOs.compactMap { $0.metabolicAge }),
                proteinPercent: avgNonZero(validDTOs.compactMap { $0.proteinPercent }),
                pulse: avgNonZero(validDTOs.compactMap { $0.pulse }),
                skeletalMusclePercent: avgNonZero(validDTOs.compactMap { $0.skeletalMusclePercent }),
                subcutaneousFatPercent: avgNonZero(validDTOs.compactMap { $0.subcutaneousFatPercent }),
                visceralFatLevel: avgNonZero(validDTOs.compactMap { $0.visceralFatLevel }),
                boneMass: avgNonZero(validDTOs.compactMap { $0.boneMass }),
                impedance: avgNonZero(validDTOs.compactMap { $0.impedance })
            )
        }.sorted { $0.period < $1.period }
    }

    /// Aggregate DTOs by month on background thread - avoids SwiftData relationship access
    private nonisolated func aggregateByMonthFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        let grouped = Dictionary(grouping: dtos) { dto -> String in
            guard let ts = dto.entryTimestamp else { return "" }
            return DateTimeTools.getLocalMonthStringFromUTCDate(ts)
        }

        return grouped.compactMap { month, monthDTOs -> BathScaleWeightSummary? in
            guard !month.isEmpty else { return nil }
            let validDTOs = monthDTOs.filter { ($0.weight ?? 0) > 0 }
            guard !validDTOs.isEmpty else { return nil }

            let dateString = "\(month)-01"
            let date = DateTimeTools.formatter("yyyy-MM-dd").date(from: dateString) ?? Date()
            let latestTimestamp = validDTOs.compactMap { $0.entryTimestamp }.max() ?? ""

            return BathScaleWeightSummary(
                accountId: accountId,
                period: month,
                entryTimestamp: latestTimestamp,
                date: date,
                count: validDTOs.count,
                weight: avgWeight(validDTOs.compactMap { $0.weight.map { Int($0) } }),
                bodyFat: avgNonZero(validDTOs.compactMap { $0.bodyFat }),
                muscleMass: avgNonZero(validDTOs.compactMap { $0.muscleMass }),
                water: avgNonZero(validDTOs.compactMap { $0.water }),
                bmi: avgNonZero(validDTOs.compactMap { $0.bmi }),
                bmr: avgNonZero(validDTOs.compactMap { $0.bmr }),
                metabolicAge: avgNonZero(validDTOs.compactMap { $0.metabolicAge }),
                proteinPercent: avgNonZero(validDTOs.compactMap { $0.proteinPercent }),
                pulse: avgNonZero(validDTOs.compactMap { $0.pulse }),
                skeletalMusclePercent: avgNonZero(validDTOs.compactMap { $0.skeletalMusclePercent }),
                subcutaneousFatPercent: avgNonZero(validDTOs.compactMap { $0.subcutaneousFatPercent }),
                visceralFatLevel: avgNonZero(validDTOs.compactMap { $0.visceralFatLevel }),
                boneMass: avgNonZero(validDTOs.compactMap { $0.boneMass }),
                impedance: avgNonZero(validDTOs.compactMap { $0.impedance })
            )
        }.sorted { $0.period < $1.period }
    }

    // MARK: - BPM DTO Aggregation (Background Thread Safe)

    /// Aggregate BPM DTOs by day — filters on systolic > 0 instead of weight > 0.
    private nonisolated func aggregateBpmByDayFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        let grouped = Dictionary(grouping: dtos) { dto -> String in
            guard let ts = dto.entryTimestamp else { return "" }
            return DateTimeTools.getLocalDateStringFromUTCDate(ts)
        }

        return grouped.compactMap { day, dayDTOs -> BathScaleWeightSummary? in
            guard !day.isEmpty else { return nil }
            let validDTOs = dayDTOs.filter { ($0.systolic ?? 0) > 0 }
            guard !validDTOs.isEmpty else { return nil }

            let date = DateTimeTools.getDateFromDateString(day, format: "yyyy-MM-dd")
            let latestTimestamp = validDTOs.compactMap { $0.entryTimestamp }.max() ?? ""

            return BathScaleWeightSummary(
                accountId: accountId,
                period: day,
                entryTimestamp: latestTimestamp,
                date: date,
                count: validDTOs.count,
                weight: 0,
                pulse: avgNonZero(validDTOs.compactMap { $0.pulse }),
                systolic: avgNonZero(validDTOs.compactMap { $0.systolic }),
                diastolic: avgNonZero(validDTOs.compactMap { $0.diastolic }),
                meanArterial: avgNonZero(validDTOs.compactMap { $0.meanArterial }),
                entryType: EntryType.bpm.rawValue
            )
        }.sorted { $0.period < $1.period }
    }

    /// Aggregate BPM DTOs by month.
    private nonisolated func aggregateBpmByMonthFromDTOs(_ dtos: [BathScaleOperationDTO], accountId: String) -> [BathScaleWeightSummary] {
        let grouped = Dictionary(grouping: dtos) { dto -> String in
            guard let ts = dto.entryTimestamp else { return "" }
            return DateTimeTools.getLocalMonthStringFromUTCDate(ts)
        }

        return grouped.compactMap { month, monthDTOs -> BathScaleWeightSummary? in
            guard !month.isEmpty else { return nil }
            let validDTOs = monthDTOs.filter { ($0.systolic ?? 0) > 0 }
            guard !validDTOs.isEmpty else { return nil }

            let dateString = "\(month)-01"
            let date = DateTimeTools.formatter("yyyy-MM-dd").date(from: dateString) ?? Date()
            let latestTimestamp = validDTOs.compactMap { $0.entryTimestamp }.max() ?? ""

            return BathScaleWeightSummary(
                accountId: accountId,
                period: month,
                entryTimestamp: latestTimestamp,
                date: date,
                count: validDTOs.count,
                weight: 0,
                pulse: avgNonZero(validDTOs.compactMap { $0.pulse }),
                systolic: avgNonZero(validDTOs.compactMap { $0.systolic }),
                diastolic: avgNonZero(validDTOs.compactMap { $0.diastolic }),
                meanArterial: avgNonZero(validDTOs.compactMap { $0.meanArterial }),
                entryType: EntryType.bpm.rawValue
            )
        }.sorted { $0.period < $1.period }
    }

    // MARK: - Helpers ---------------------------------------------------

    /// Update progress and streak based on current entries
    private func updateProgressAndStreakInternal() async {
        do {
            let accountId = try getAccountId()
            // Use count instead of fetching all entries (avoids loading 3660+ Entry objects)
            let totalEntries = try await localRepo.fetchEntryCount(forUserId: accountId)
            let streakValue = try await getStreak()

            progress = ProgressSummary(totalEntries: totalEntries, streak: streakValue.current)
            streak = streakValue.current

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
        let avgWeight: Double? = weightValues.isEmpty ? nil : Double(Int(round(weightValues.reduce(0, +) / Double(weightValues.count))))
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
    func loadDashboardData(entryType: EntryType = .wg) async {
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

            let dtos = allDTOs.filter { matchesDTOEntryType($0, entryType: entryType) }

            let (dailyData, monthlyData) = await Task.detached(priority: .userInitiated) { [weak self] in
                guard let self = self else { return ([BathScaleWeightSummary](), [BathScaleWeightSummary]()) }
                switch entryType {
                case .wg:
                    let daily = self.aggregateByDayFromDTOs(dtos, accountId: accountId)
                    let monthly = self.aggregateByMonthFromDTOs(dtos, accountId: accountId)
                    return (daily, monthly)
                case .bpm:
                    let daily = self.aggregateBpmByDayFromDTOs(dtos, accountId: accountId)
                    let monthly = self.aggregateBpmByMonthFromDTOs(dtos, accountId: accountId)
                    return (daily, monthly)
                }
            }.value

            switch entryType {
            case .wg:
                dailySummaries = dailyData
                monthlySummaries = monthlyData
            case .bpm:
                // Fallback to dummy data for development/testing when no real BPM entries exist.
                // Once BPM data is available, this condition will be false and real data will be used.
                if dailyData.isEmpty {
                    let dummy = Self.generateDummyBpmSummaries(accountId: accountId)
                    bpmDailySummaries = dummy
                    bpmMonthlySummaries = dummy
                } else {
                    bpmDailySummaries = dailyData
                    bpmMonthlySummaries = monthlyData
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "loadDashboardData failed (\(entryType)): \(error.localizedDescription)")
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

        await updateProgressAndStreakInternal()

        // Create notification on MainActor to safely extract relationship data
        let notification = EntryNotification(from: entry)
        entrySaved.send(notification)

        // Trigger integration sync for the new entry (e.g., HealthKit)
        do {
            try await integrationService.syncNewEntry(entry)
        } catch {
            // Log error but don't fail the entry creation process
            logger.log(level: .error, tag: tag, message: "Failed to sync new entry to integrations: \(error.localizedDescription)")
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
        guard let entry = entries.first(where: { $0.deviceType == DeviceType.bpm.rawValue }) else {
            throw NSError(domain: "EntryService", code: 404, userInfo: [NSLocalizedDescriptionKey: "BPM entry not found"])
        }

        entry.operationType = OperationType.delete.rawValue
        entry.isSynced = false

        do {
            try await localRepo.updateEntry(entry)
            await refreshBpmDashboardSummaries()
            let notification = EntryNotification(from: entry)
            entryDeleted.send(notification)
            await syncUnsyncedBpmEntries()
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

    func exportBpmCSV() async throws {
        _ = try await remoteRepo.exportBpmCsv()
    }

    // MARK: - Baby Entry CRUD

    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String) async throws {
        let accountId = try getAccountId()

        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.babyScale.rawValue,
            isSynced: false,
            babyId: babyId
        )
        entry.attempts = 0
        entry.babyEntry = BabyEntry(babyId: babyId, length: length, weight: weight, note: note)

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

    /// Pushes unsynced BPM entries to the remote API and pulls remote BPM operations.
    private func syncUnsyncedBpmEntries() async {
        guard let accountId = try? getAccountId() else { return }

        do {
            let unsynced = try await localRepo.fetchUnsyncedEntries(forUserId: accountId)
            let bpmUnsynced = unsynced.filter { $0.deviceType == DeviceType.bpm.rawValue }

            for operation in bpmUnsynced {
                let entryIdString = operation.id.uuidString
                let operationType = operation.operationType
                let currentAttempts = operation.attempts
                let dto = operation.toBpmOperationDTO()

                do {
                    try await remoteRepo.syncBpmOperation(operation: dto)

                    if operationType == OperationType.create.rawValue {
                        try await localRepo.updateEntrySyncStatus(
                            entryId: entryIdString,
                            isSynced: true,
                            isFailedToSync: false,
                            attempts: currentAttempts
                        )
                    } else {
                        try await localRepo.deleteEntry(byId: entryIdString)
                    }
                } catch {
                    let newAttempts = currentAttempts + 1
                    let markAsFailed = newAttempts > 8

                    try? await localRepo.updateEntrySyncStatus(
                        entryId: entryIdString,
                        isSynced: markAsFailed,
                        isFailedToSync: markAsFailed,
                        attempts: newAttempts
                    )
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "BPM sync failed: \(error.localizedDescription)")
        }
    }

    private func refreshBpmDashboardSummaries() async {
        await loadDashboardData(entryType: .bpm)
    }

    // MARK: - Dummy BPM Data (Testing Only — Remove Before Release)

    /// Generates 14 days of dummy BP summaries for testing the BPM dashboard.
    /// Readings vary realistically across AHA classifications.
    private static func generateDummyBpmSummaries(accountId: String) -> [BathScaleWeightSummary] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let isoFormatter = ISO8601DateFormatter()

        // Realistic BP readings across different AHA levels
        let readings: [BpmAverage] = [
            BpmAverage(systolic: 118, diastolic: 76, pulse: 68, classification: .normal),
            BpmAverage(systolic: 122, diastolic: 78, pulse: 72, classification: .elevated),
            BpmAverage(systolic: 115, diastolic: 74, pulse: 65, classification: .normal),
            BpmAverage(systolic: 132, diastolic: 84, pulse: 75, classification: .hypertensionStage1),
            BpmAverage(systolic: 119, diastolic: 77, pulse: 70, classification: .normal),
            BpmAverage(systolic: 125, diastolic: 79, pulse: 73, classification: .elevated),
            BpmAverage(systolic: 138, diastolic: 88, pulse: 78, classification: .hypertensionStage1),
            BpmAverage(systolic: 112, diastolic: 72, pulse: 62, classification: .normal),
            BpmAverage(systolic: 142, diastolic: 92, pulse: 82, classification: .hypertensionStage2),
            BpmAverage(systolic: 120, diastolic: 78, pulse: 69, classification: .elevated),
            BpmAverage(systolic: 116, diastolic: 75, pulse: 66, classification: .normal),
            BpmAverage(systolic: 128, diastolic: 82, pulse: 74, classification: .hypertensionStage1),
            BpmAverage(systolic: 110, diastolic: 70, pulse: 60, classification: .normal),
            BpmAverage(systolic: 135, diastolic: 86, pulse: 76, classification: .hypertensionStage1)
        ]

        return readings.enumerated().compactMap { index, bp -> BathScaleWeightSummary? in
            guard let date = calendar.date(byAdding: .day, value: -(readings.count - 1 - index), to: today) else { return nil }
            let period = formatter.string(from: date)
            let timestamp = isoFormatter.string(from: date)

            return BathScaleWeightSummary(
                accountId: accountId,
                period: period,
                entryTimestamp: timestamp,
                date: date,
                count: 1,
                weight: 0,
                pulse: Double(bp.pulse),
                systolic: Double(bp.systolic),
                diastolic: Double(bp.diastolic),
                meanArterial: Double((bp.systolic + 2 * bp.diastolic) / 3),
                entryType: EntryType.bpm.rawValue
            )
        }
    }

    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
