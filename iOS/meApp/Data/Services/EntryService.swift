import Foundation
import Combine

final class EntryService: EntryServiceProtocol, ObservableObject {
    @Injector var logger: LoggerService
    @Injector var goalAlertService: GoalAlertService
    @Injector var integrationService: IntegrationsService
    private let accountService: AccountServiceProtocol
    private let localRepo: EntryRepositoryProtocol = EntryRepository()
    private let localKVRepo: EntryRepositoryLocal = EntryRepositoryLocal()
    private let remoteRepo: EntryRepositoryAPIProtocol = EntryRepositoryAPI()
    private let migrationService = SQLiteMigrationService()
    @MainActor static let shared = EntryService(accountService: AccountService.shared)
    // MARK: - Publishers ------------------------------------------------
    
    /// Emits each time a new entry is locally stored (create).
    let entrySaved = PassthroughSubject<Entry, Never>()
    /// Emits each time an entry is deleted locally.
    let entryDeleted = PassthroughSubject<Entry, Never>()
    
    let tag = "EntryService"
    
    @Published var isSyncing: Bool = false
    @Published var lastSyncTime: Date?
    @Published var progress: ProgressSummary = .empty
    @Published var streak: Int = 0
    
    // MARK: - Dashboard Data
    @Published var dailySummaries: [BathScaleWeightSummary] = []
    @Published var monthlySummaries: [BathScaleWeightSummary] = []
    
    private var cancellables = Set<AnyCancellable>()
    private var lastAccountId: String? = nil
    
    init(accountService: AccountServiceProtocol) {
        self.accountService = accountService
        
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
                            
                            if accountChanged, let accountId = accountId {
                                try? await self.clearLastSyncTimestamp()
                                await self.syncAllEntriesWithRemote()
                                await self.loadDashboardData()
                            }
                        }
                    }
                    .store(in: &self.cancellables)
            }
        }
    }
    
    // MARK: - Helper
    private func getAccountId() async throws -> String {
        guard let account = try await accountService.getActiveAccount() else {
            throw NSError(domain: "EntryService", code: 401, userInfo: [NSLocalizedDescriptionKey: "No active account"])
        }
        return account.accountId
    }
    
    // MARK: - CRUD
    func clearAllData() async {
        try? await localRepo.deleteAllEntries()
    }
    
    /// Clears the last sync timestamp for the current user.
    func clearLastSyncTimestamp() async throws {
        let accountId = try await getAccountId()
        try await localKVRepo.clearLastSyncTimestamp(accountId: accountId)
    }
    
    func saveNewEntry(_ entry: Entry) async throws {
        entry.isSynced = false
        entry.operationType = OperationType.create.rawValue
        entry.attempts = 0
        
        try await localRepo.saveEntry(entry)
        try await handleEntryAdded(entry)
        
        // Broadcast change
        await syncUnsyncedEntries()
        await checkGoalAlerts()
    }
    
    func saveNewEntries(_ entries: [Entry]) async throws {
        for entry in entries {
            entry.isSynced = false
            try await localRepo.saveEntry(entry)
            try await handleEntryAdded(entry)
        }
        
        await syncUnsyncedEntries()
    }
    
    func deleteEntry(_ entry: Entry) async throws {
        let deletedEntry = entry
        deletedEntry.operationType = OperationType.delete.rawValue
        deletedEntry.isSynced = false
        
        try await localRepo.updateEntry(deletedEntry)
        try await handleEntryDeleted(deletedEntry)
        await syncUnsyncedEntries()
    }
    
    // MARK: - Query
    func getAllEntries() async throws -> [Entry] {
        let accountId = try await getAccountId()
        return try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
    }
    
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool {
        let accountId = try await getAccountId()
        return try await localRepo.checkEntryTimestampExists(forUserId: accountId, entryTimestamp: entryTimestamp)
    }
    
    func getEntryCount() async throws -> Int {
        let accountId = try await getAccountId()
        return try await localRepo.fetchEntryCount(forUserId: accountId)
    }
    
    func getOldestEntry() async throws -> Entry? {
        let accountId = try await getAccountId()
        return try await localRepo.fetchOldestEntry(forUserId: accountId)
    }
    
    func getLatestEntry() async throws -> Entry? {
        let accountId = try await getAccountId()
        return try await localRepo.fetchLatestEntry(forUserId: accountId)
    }
    
    func getEntries(lastNDays: Int) async throws -> [Entry] {
        let accountId = try await getAccountId()
        return try await localRepo.fetchEntries(lastNDays: lastNDays, userId: accountId)
    }
    
    func getEntries(forMonth month: String) async throws -> [Entry] {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forMonth: month, userId: accountId)
        return entries.filter { $0.operationType == OperationType.create.rawValue }
    }
    
    func getEntries(forDay day: String) async throws -> [Entry] {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forDay: day, userId: accountId)
        return entries.filter { $0.operationType == OperationType.create.rawValue }
    }
    
    // MARK: - Month/History
    func getMonthsAll() async throws -> [HistoryMonth] {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        // Group by YYYY-MM prefix, converting UTC timestamps to local timezone
        let grouped = Dictionary(grouping: entries) { DateTimeTools.getLocalMonthStringFromUTCDate($0.entryTimestamp) }
        
        var result: [HistoryMonth] = []
        
        let validMonthRegex = try! NSRegularExpression(pattern: "^\\d{4}-\\d{2}$")
        
        for (monthKey, monthEntries) in grouped {
            // Skip keys that are not in YYYY-MM format (e.g., malformed keys)
            guard validMonthRegex.firstMatch(in: monthKey, range: NSRange(location: 0, length: monthKey.count)) != nil else { continue }
            
            result.append(Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries))
        }
        
        // Sort descending by month key
        return result.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }
    
    func getMonthDetail(month: String) async throws -> [Entry] {
        return try await getEntries(forMonth: month)
    }
    
    func getMonthYear() async throws -> [HistoryMonth] {
        let accountId = try await getAccountId()
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
        let grouped = Dictionary(grouping: filteredEntries) { (entry) -> String in
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
    func getProgress() async throws -> Progress {
        let accountId = try await getAccountId()
        let allEntries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        let sortedEntries = allEntries.sorted { $0.entryTimestamp < $1.entryTimestamp }
        
        guard let latestEntry = sortedEntries.last else {
            throw NSError(domain: "EntryService", code: 404, userInfo: [NSLocalizedDescriptionKey: "No entries found"])
        }
        
        // Helper: Gets and sorts entries in the last N days
        func sortedRecentEntries(_ days: Int) async throws -> [Entry] {
            try await localRepo.fetchEntries(lastNDays: days, userId: accountId).sorted { $0.entryTimestamp < $1.entryTimestamp }
        }
        
        let weekEntries = try await sortedRecentEntries(7)
        let monthEntries = try await sortedRecentEntries(30)
        
        // Prepare entry IDs to refetch in main actor context for SwiftData safety
        let idSet = [
            latestEntry.id,
            weekEntries.first?.id,
            monthEntries.first?.id,
            sortedEntries.first?.id
        ].compactMap { $0 }
        
        guard let entryRepo = localRepo as? EntryRepository else {
            throw NSError(domain: "EntryService", code: 500, userInfo: [NSLocalizedDescriptionKey: "localRepo is not of type EntryRepository"])
        }
        let refetched = try await entryRepo.refetchEntriesOnMainActor(entryIds: idSet)
        
        // Store IDs before accessing SwiftData properties
        let latestEntryId = latestEntry.id
        let weekStartId = weekEntries.first?.id
        let monthStartId = monthEntries.first?.id
        let firstEntryId = sortedEntries.first?.id
        
        // Helper: Safe extraction utilities
        func safe<T>(_ id: UUID?) -> T? { id.flatMap { refetched[$0] as? T } }
        func sync<T>(_ block: @Sendable () throws -> T) async rethrows -> T { try await MainActor.run(body: block) }
        
        // Extract relevant weights and DTOs in a MainActor context
        // All SwiftData property access must happen on MainActor to avoid crashes
        let ext: (
            latestWeight: Int,
            weekStartWeight: Int?,
            monthStartWeight: Int?,
            firstEntryWeight: Int?,
            weekDTO: BathScaleOperationDTO?,
            monthDTO: BathScaleOperationDTO?,
            firstDTO: BathScaleOperationDTO?,
            latestDTO: BathScaleOperationDTO
        ) = try await sync {
            guard let latestEntry = refetched[latestEntryId] else {
                throw NSError(domain: "EntryService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Latest entry not found in refetched entries"])
            }
            let weekStart = safe(weekStartId) as Entry?
            let monthStart = safe(monthStartId) as Entry?
            let firstEntry = safe(firstEntryId) as Entry?
            
            return (
                latestWeight: latestEntry.scaleEntry?.weight ?? 0,
                weekStartWeight: weekStart?.scaleEntry?.weight,
                monthStartWeight: monthStart?.scaleEntry?.weight,
                firstEntryWeight: firstEntry?.scaleEntry?.weight,
                weekDTO: weekStart?.toOperationDTO(),
                monthDTO: monthStart?.toOperationDTO(),
                firstDTO: firstEntry?.toOperationDTO(),
                latestDTO: latestEntry.toOperationDTO()
            )
        }
        
        let latestWeight = ext.latestWeight
        let weekStartWeight = ext.weekStartWeight
        let monthStartWeight = ext.monthStartWeight
        let firstEntryWeight = ext.firstEntryWeight
        let weekDTO = ext.weekDTO
        let monthDTO = ext.monthDTO
        let firstDTO = ext.firstDTO
        let latestDTO = ext.latestDTO
        
        let weekDelta = latestWeight - (weekStartWeight ?? latestWeight)
        let monthDelta = latestWeight - (monthStartWeight ?? latestWeight)
        
        // -- Year delta logic, extracted to a helper for clarity --
        let monthSeries = try await getMonthYear()
        let yearDeltaResult = try await calculateYearDelta(latestWeight: latestWeight, monthStartWeight: monthStartWeight, monthSeries: monthSeries)
        let yearDelta = yearDeltaResult.yearDelta
        let yearStartDTO = yearDeltaResult.yearStartDTO
        let yearKey = yearDeltaResult.yearKey
        
        let account = try await accountService.getActiveAccount()
        let initialWeight = account?.goalSettings?.initialWeight.map(Int.init) ?? firstEntryWeight
        let totalDelta = latestWeight - (initialWeight ?? latestWeight)
        
        let streak = try await getStreak()
        
        await logger.log(
            level: .debug, tag: tag,
            message: "Progress(year): latest=\(latestWeight), yearKey=\(yearKey), yearDelta=\(yearDelta)"
        )
        
        return Progress(
            count: sortedEntries.count,
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
    private func calculateYearDelta(
        latestWeight: Int, monthStartWeight: Int?, monthSeries: [HistoryMonth]
    ) async throws -> (yearDelta: Int, yearStartDTO: BathScaleOperationDTO?, yearKey: String) {
        guard let initYearMonth = monthSeries.last, let initYearWeight = initYearMonth.weight else {
            return (0, nil, "")
        }
        
        guard let thirtyDaysAgo = Calendar.current.date(byAdding: .day, value: -30, to: Date()) else {
            await logger.log(level: .error, tag: tag, message: "Failed to calculate date 30 days ago for year delta calculation.")
            return (0, nil, "")
        }
        let (initYearDate, yearKey) = parseYearKeyAndDate(from: initYearMonth.entryTimestamp, id: initYearMonth.id)
        let isWithin30Days = isoString(from: initYearDate) >= isoString(from: thirtyDaysAgo)
        let initYearWeightStored = Int(round(initYearWeight))
        
        let delta = isWithin30Days
        ? latestWeight - (monthStartWeight ?? latestWeight)
        : latestWeight - initYearWeightStored
        
        let yearAvgWeight = Int(round(initYearWeight))
        let yearStartDTO = makeYearDTO(
            key: yearKey, avgWeight: yearAvgWeight, accountId: try await getAccountId()
        )
        
        return (delta, yearStartDTO, yearKey)
    }
    
    private func parseYearKeyAndDate(from timestamp: String, id: String) -> (Date, String) {
        let fmt = DateTimeTools.formatter("yyyy-MM")
        if let date = fmt.date(from: timestamp) {
            return (date, id)
        }
        // Fallback, parse manually
        let comps = timestamp.split(separator: "-")
        if comps.count == 2, let y = Int(comps[0]), let m = Int(comps[1]) {
            var dc = DateComponents(); dc.year = y; dc.month = m; dc.day = 1
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
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: Double(avgWeight)
        )
    }

    func getStreak() async throws -> Streak {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        let calendar = Calendar.current

        // Extract unique days
        let uniqueDays: [Date] = Array(
            Set(entries.map { DateTimeTools.getLocalDateStringFromUTCDate($0.entryTimestamp) })
        )
        .compactMap { DateTimeTools.getDateFromDateString($0, format: "yyyy-MM-dd") }
        .sorted(by: >)

        guard !uniqueDays.isEmpty else { return Streak(current: 0, max: 0) }

        func isSameDay(_ a: Date, _ b: Date) -> Bool { calendar.isDate(a, inSameDayAs: b) }

        var currentStreak = 0
        var dateToCheck = Date()

        if let first = uniqueDays.first, isSameDay(first, dateToCheck) {
            currentStreak = 1
            dateToCheck = calendar.date(byAdding: .day, value: -1, to: dateToCheck)!
        } else if let first = uniqueDays.first,
                  isSameDay(first, calendar.date(byAdding: .day, value: -1, to: dateToCheck)!) {
            currentStreak = 1
            dateToCheck = calendar.date(byAdding: .day, value: -1, to: dateToCheck)!
        } else {
            return Streak(current: 0, max: Self.computeLongestStreak(from: uniqueDays.sorted()))
        }

        for day in uniqueDays.dropFirst() {
            if isSameDay(day, dateToCheck) {
                currentStreak += 1
                dateToCheck = calendar.date(byAdding: .day, value: -1, to: dateToCheck)!
            } else {
                break
            }
        }

        let longestStreak = Self.computeLongestStreak(from: uniqueDays.sorted())
        return Streak(current: currentStreak, max: longestStreak)
    }

    private static func computeLongestStreak(from days: [Date]) -> Int {
        guard !days.isEmpty else { return 0 }
        let calendar = Calendar.current
        var longest = 1
        var current = 1

        for i in 1..<days.count {
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
    public func migrateFromSQLiteIfNeeded() async {
        guard migrationService.isMigrationNeeded() else {
            await logger.log(level: .info, tag: tag, message: "No SQLite migration needed")
            return
        }
        
        do {
            await logger.log(level: .info, tag: tag, message: "Starting SQLite migration for all users in opStack")
            
            // Migrate data for all users found in the opStack tables
            let migratedData = try await migrationService.migrateAllUsersEntryData()
            
            let totalMigrated = migratedData.values.reduce(0, +)
            await logger.log(level: .info, tag: tag, message: "SQLite migration completed: \(totalMigrated) entries migrated for \(migratedData.count) users")
            
            // Log migration details per user
            for (userId, count) in migratedData {
                await logger.log(level: .info, tag: tag, message: "User \(userId): \(count) entries migrated")
            }
            
            // Update dashboard data after migration (only for current active user if available)
            do {
                let accountId = try await getAccountId()
                if migratedData[accountId] != nil {
                    await loadDashboardData()
                    await updateProgressAndStreakInternal()
                    await logger.log(level: .info, tag: tag, message: "Dashboard data updated for current user: \(accountId)")
                }
            } catch {
                await logger.log(level: .info, tag: tag, message: "No active account found, skipping dashboard update")
            }
            
            // Clean up SQLite database after successful migration
            try migrationService.cleanupAfterMigration()
            await logger.log(level: .info, tag: tag, message: "✅ SQLite database cleaned up successfully")
            await logger.log(level: .info, tag: tag, message: "🎉 Migration process completed!")
            
        } catch {
            await logger.log(level: .error, tag: tag, message: "SQLite migration failed: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Sync Logic
    /// Sync all unsynced entries with the remote backend. Call this on app start or after network recovery.
    public func syncAllEntriesWithRemote() async {
        guard !isSyncing else {
            return
        }
        
        isSyncing = true
        defer { 
            isSyncing = false
        }
        
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            await logger.log(level: .error, tag: tag, message: "Sync failed: No account ID available")
            return
        }
        
        do {
            // 1. Push unsynced entries to remote
            await pushUnsyncedEntriesToRemote(accountId: accountId)
            
            // 2. Fetch latest from remote and merge, using last sync timestamp
            let localEntries = try? await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
            var lastSyncTimestamp = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)
            
            if localEntries?.isEmpty == true {
                try? await localKVRepo.clearLastSyncTimestamp(accountId: accountId)
                lastSyncTimestamp = nil
            }
            
            let remoteOps = try await remoteRepo.fetchOperations(startTimestamp: lastSyncTimestamp)
            await mergeRemoteOperations(remoteOps.operations, accountId: accountId)
            await loadDashboardData()

            // 5. Update sync timestamp and local state
            try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: remoteOps.timestamp)
            lastSyncTime = Date()
            
            // 6. Update progress, streak, and check for goal alerts
            await updateProgressAndStreakInternal()
            await checkGoalAlerts()
            
            await logger.log(level: .debug, tag: tag, message: "Full sync completed successfully")

        } catch {
            await logger.log(level: .error, tag: tag, message: "Sync failed: \(error.localizedDescription)")
        }
    }
    
    private func pushUnsyncedEntriesToRemote(accountId: String) async {
        // 1. Get all unsynced entries (both new and delete operations)
        let unsynced = try? await localRepo.fetchUnsyncedEntries(forUserId: accountId)
        
        var successfulOps: [Entry] = []
        var failedOps: [Entry] = []
        
        // 2. Try to sync with backend
        if let unsyncedEntries = unsynced, !unsyncedEntries.isEmpty {
            for operation in unsyncedEntries {
                do {
                    let dto = operation.toOperationDTO()
                    try await remoteRepo.syncOperation(operation: dto)
                    
                    successfulOps.append(operation)
                    
                    if operation.operationType == "create" {
                        operation.isSynced = true
                        try await localRepo.updateEntry(operation)
                        await logger.log(level: .debug, tag: tag, message: "Entry create/update synced: \(operation.id)")
                    } else {
                        try await localRepo.deleteEntry(byId: operation.id.uuidString)
                        try await handleEntryDeleted(operation)
                        await logger.log(level: .debug, tag: tag, message: "Entry deleted: \(operation.id)")
                    }
                } catch {
                    //check if error is due to unauthorized access
                    //                  if error is HTTPError.unauthorized {
                    //                    return
                    //                  }
                    // If sync fails, mark synced as false and update local state
                    operation.isSynced = false
                    operation.attempts = operation.attempts + 1
                    
                    //if attempts is more than 8, mark as failed and update local state
                    if operation.attempts > 8 {
                        operation.isSynced = true
                        operation.isFailedToSync = true
                    }
                    try? await localRepo.updateEntry(operation)
                    failedOps.append(operation)
                    await logger.log(level: .error, tag: tag, message: "Sync failed: \(error)")
                }
            }
        }
    }
    /// Lightweight summary for a single month. Avoids computing all months when only one changes.
    func getMonthSummary(monthKey: String) async throws -> HistoryMonth? {
        let monthEntries = try await getEntries(forMonth: monthKey)
        guard !monthEntries.isEmpty else { return nil }
        return Self.buildHistoryMonth(monthKey: monthKey, monthEntries: monthEntries)
    }
    
    /// Internal: Sync only unsynced entries (used after local changes)
    private func syncUnsyncedEntries() async {
        guard !isSyncing else {
            return
        }
        
        isSyncing = true
        defer { 
            isSyncing = false
        }
        
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            await logger.log(level: .error, tag: tag, message: "Unsynced entries sync failed: No account ID available")
            return
        }
        
        do {
            // 1. Push unsynced entries to remote
            await pushUnsyncedEntriesToRemote(accountId: accountId)
            
            // After syncing, update last sync timestamp and local state
            let now = ISO8601DateFormatter().string(from: Date())
            try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: now)
            lastSyncTime = Date()
            
            // Update progress, streak, and check for goal alerts
            await updateProgressAndStreakInternal()
        }  catch {
            await logger.log(level: .error, tag: tag, message: "Unsynced entries sync failed: \(error.localizedDescription)")
        }
    }
    
    /// Merge remote operations into local DB, resolving conflicts (latest wins by timestamp)
    private func mergeRemoteOperations(_ remoteOps: [BathScaleOperationDTO], accountId: String) async {
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
            
            // Attempt to find local entries by trying both timestamp variants
            var localEntry: Entry? = nil
            var localEntries: [Entry]? = nil
            for ts in tsCandidates {
                if let fetched = try? await localRepo.fetchEntriesOfTimestamp(forUserId: accountId, timestamp: ts), !fetched.isEmpty {
                    localEntries = fetched
                    localEntry = fetched.first
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
            let entryToProcess = localEntry ?? (allEntriesForUser.filter { entry in
                if let entryWeight = entry.scaleEntry?.weight, let opWeight = finalOp.weight {
                    return entryWeight == Int(opWeight) && entry.entryTimestamp == normalizedTimestamp
                }
                return false
            }.first)
            
            if let localEntry = entryToProcess {
                // Local entry exists - compare server timestamps
                let localServerTS = localEntry.serverTimestamp ?? ""
                let remoteServerTS = finalOp.serverTimestamp ?? ""
                
                if remoteServerTS > localServerTS {
                    // Remote is newer - apply the final operation
                    if finalOp.operationType == OperationType.delete.rawValue {
                        // Final state is deleted - remove from local storage
                        try? await localRepo.deleteEntry(byId: localEntry.id.uuidString)
                        try? await handleEntryDeleted(localEntry)
                    } else {
                        // Final state is create - update local with remote
                        let updated = Entry(from: finalOp, accountId: accountId, isSynced: true)
                        try? await localRepo.updateEntry(updated)
                        // Notify downstream listeners/UI about the updated/added entry so lists refresh
                    }
                }
            } else if !potentialDuplicates.isEmpty {
                // Found potential duplicate by weight - update the existing one instead of creating new
                let duplicateEntry = potentialDuplicates.first!
                let updated: Entry = {
                    var entry = Entry(from: finalOp, accountId: accountId, isSynced: true)
                    entry.id = duplicateEntry.id
                    return entry
                }()
                try? await localRepo.updateEntry(updated)
            } else {
                // No local entry - only create if final operation is create
                if finalOp.operationType == OperationType.create.rawValue {
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
                entrySaved.send(entry)
                try await self.handleEntryAdded(entry)
            }
        } catch {
            await logger.log(level: .error, tag: tag, message: "Failed to get latest entry: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Export
    /// Exports entries as CSV based on current dashboard type (4 or 12 metrics)
    func exportCSV() async throws {
        // Determine account and dashboard setting
        guard let account = try await accountService.getActiveAccount() else {
            throw AccountError.noActiveAccount
        }
        let useR4Endpoint = account.dashboardSettings?.dashboardType == DashboardType.dashboard12.rawValue
        let _ = try await remoteRepo.exportCsv(useR4Endpoint: useR4Endpoint)
    }
    
    // MARK: - Aggregation Helpers
    
    /// Helper function for all metrics (excludes zero values)
    private func avgNonZero(_ values: [Double?]) -> Double? {
        let vals = values.compactMap { $0 }.filter { $0 > 0 }
        return vals.isEmpty ? nil : vals.reduce(0, +) / Double(vals.count)
    }
    
    /// Helper function for weight: average stored values (tenths of lbs) and round to whole tenths
    /// This matches the logic in buildHistoryMonth to ensure consistent rounding
    private func avgWeight(_ values: [Int]) -> Double {
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
        
        return grouped.compactMap { (day, dayEntries) -> BathScaleWeightSummary? in
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
        
        return grouped.compactMap { (month, monthEntries) -> BathScaleWeightSummary? in
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
    
    
    // MARK: - Helpers ---------------------------------------------------
    
    
    /// Update progress and streak based on current entries
    private func updateProgressAndStreakInternal() async {
        do {
            let accountId = try await getAccountId()
            let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
            
            // Compute progress
            let totalEntries = entries.count
            let streakValue = try await getStreak()
            
            self.progress = ProgressSummary(totalEntries: totalEntries, streak: streakValue.current)
            self.streak = streakValue.current
            
            await logger.log(level: .debug, tag: tag, message: "Progress and streak updated: total=\(totalEntries), streak=\(streakValue.current)")
        } catch {
            await logger.log(level: .error, tag: tag, message: "Failed to update progress/streak: \(error.localizedDescription)")
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
            await logger.log(level: .error, tag: tag, message: "Failed to evaluate goal alerts: \(error.localizedDescription)")
        }
    }
    
    private static func buildHistoryMonth(monthKey: String, monthEntries: [Entry]) -> HistoryMonth {
        // Build the `weights` concatenated string  "<w>|<ts>,<w>|<ts>"  like the SQL query
        let weightPairs = monthEntries.compactMap { e -> String? in
            guard let w = e.scaleEntry?.weight else { return nil }
            return "\(w)|\(e.entryTimestamp)"
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
        let lastWeight  = sortedByTime.last?.scaleEntry?.weight
        let change: String? = {
            guard let f = firstWeight, let l = lastWeight else { return nil }
            return String(format: "%.1f", Double(l - f))
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
    func loadDashboardData() async {
        do {
            let accountId = try await getAccountId()
            await logger.log(level: .debug, tag: tag, message: "Loading dashboard data")
            // Get all entries for the account
            let entries = try await getAllEntries()
            // Aggregate data by day and month
            let dailyData = aggregateByDay(entries: entries, accountId: accountId)
            let monthlyData = aggregateByMonth(entries: entries, accountId: accountId)
            
            // Update published arrays
            dailySummaries = dailyData.compactMap { $0 }.sorted { $0.period < $1.period }
            monthlySummaries = monthlyData.compactMap { $0 }.sorted { $0.period < $1.period }
            
            // Log entry count with synced/unsynced breakdown (reuse entries already fetched)
            let totalCount = entries.count
            let unsyncedCount = entries.filter { $0.isSynced == false }.count
            let syncedCount = totalCount - unsyncedCount
            await logger.log(level: .info, tag: tag, message: "Dashboard data loaded - Entries count=\(totalCount) (synced=\(syncedCount), unsynced=\(unsyncedCount)), Daily: \(dailySummaries.count), Monthly: \(monthlySummaries.count)")
        } catch {
            await logger.log(level: .error, tag: tag, message: "Failed to load entries: \(error.localizedDescription)")
        }
    }
    
    /// Handles entry addition by updating affected day and month summaries
    func handleEntryAdded(_ entry: Entry) async throws {
        let accountId = try await getAccountId()
        
        await logger.log(level: .debug, tag: tag, message: "Handling entry addition: \(entry.id)")
        
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
        
        entrySaved.send(entry)
        
        // Trigger integration sync for the new entry (e.g., HealthKit)
        do {
            try await integrationService.syncNewEntry(entry)
        } catch {
            // Log error but don't fail the entry creation process
            await logger.log(level: .error, tag: tag, message: "Failed to sync new entry to integrations: \(error.localizedDescription)")
        }
    }
    
    /// Handles entry update by treating as delete + add
    func handleEntryUpdated(_ entry: Entry) async throws {
        await logger.log(level: .debug, tag: tag, message: "Handling entry update: \(entry.id)")
        
        // For updates, we can treat as delete + add for simplicity
        try await handleEntryDeleted(entry)
        try await handleEntryAdded(entry)
    }
    
    /// Handles entry deletion by updating affected day and month summaries
    func handleEntryDeleted(_ entry: Entry) async throws {
        let accountId = try await getAccountId()
        
        await logger.log(level: .debug, tag: tag, message: "Handling entry deletion: \(entry.id)")
        
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
        
        entryDeleted.send(entry)
        
        // Trigger integration delete for the removed entry (e.g., HealthKit)
        do {
            try await integrationService.deleteEntry(entry)
        } catch {
            // Log error but don't fail the entry deletion process
            await logger.log(level: .error, tag: tag, message: "Failed to delete entry from integrations: \(error.localizedDescription)")
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
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
