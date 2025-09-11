import Foundation
import Combine

@MainActor
final class EntryService: EntryServiceProtocol, ObservableObject {
    @Injector var logger: LoggerService
    @Injector var goalAlertService: GoalAlertService
    @Injector var integrationService: IntegrationsService
    private let accountService: AccountServiceProtocol
    private let localRepo: EntryRepositoryProtocol = EntryRepository()
    private let localKVRepo: EntryRepositoryLocal = EntryRepositoryLocal()
    private let remoteRepo: EntryRepositoryAPIProtocol = EntryRepositoryAPI()
    private let migrationService = SQLiteMigrationService()
    static let shared = EntryService(accountService: AccountService.shared)
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

    init(accountService: AccountServiceProtocol) {
        self.accountService = accountService
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
        // Get last 12 months
        let calendar = Calendar.current
        let now = Date()
        let months = (0..<12).map { offset -> String in
            let date = calendar.date(byAdding: .month, value: -offset, to: now)!
            let comps = calendar.dateComponents([.year, .month], from: date)
            return String(format: "%04d-%02d", comps.year ?? 0, comps.month ?? 0)
        }
        let grouped = Dictionary(grouping: entries) { (entry) -> String in
            DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
        }
        var result: [HistoryMonth] = []
        for month in months {
            guard let monthEntries = grouped[month], !monthEntries.isEmpty else { continue }
            result.append(Self.buildHistoryMonth(monthKey: month, monthEntries: monthEntries))
        }
        return result
    }

    // MARK: - Progress/Stats
    func getProgress() async throws -> Progress {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue).sorted { $0.entryTimestamp < $1.entryTimestamp }
        guard let latest = entries.last else {
            throw NSError(domain: "EntryService", code: 404, userInfo: [NSLocalizedDescriptionKey: "No entries found"])
        }
        let latestWeight = latest.scaleEntry?.weight ?? 0
        let weekEntries = entries.suffix(7)
        let monthEntries = entries.suffix(30)
        let yearEntries = entries.suffix(365)
        let initWeek = weekEntries.first
        let initMonth = monthEntries.first
        let initYear = yearEntries.first
        let week = latestWeight - (initWeek?.scaleEntry?.weight ?? latestWeight)
        let month = latestWeight - (initMonth?.scaleEntry?.weight ?? latestWeight)
        let year = latestWeight - (initYear?.scaleEntry?.weight ?? latestWeight)
        let total = latestWeight - (entries.first?.scaleEntry?.weight ?? latestWeight)
        let count = entries.count
        let streak = try await getStreak()
        let longestStreak = streak.max
        let currentStreak = streak.current
        return Progress(
            count: count,
            currentStreak: currentStreak,
            initYear: initYear?.toOperationDTO(),
            initMonth: initMonth?.toOperationDTO(),
            initWeek: initWeek?.toOperationDTO(),
            initWt: Double(entries.first?.scaleEntry?.weight ?? 0),
            latest: latest.toOperationDTO(),
            longestStreak: longestStreak,
            month: Int(month),
            percent: nil,
            total: Double(total),
            week: Int(week),
            year: Int(year)
        )
    }

    func getStreak() async throws -> Streak {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: OperationType.create.rawValue)
        // Group by day (YYYY-MM-DD)
        let days = Set(entries.map { String($0.entryTimestamp.prefix(10)) })
        let sortedDays = days.sorted()
        var maxStreak = 0
        var currentStreak = 0
        var prevDate: Date? = nil
        let formatter = ISO8601DateFormatter()
        for day in sortedDays {
            guard let date = formatter.date(from: day + "T00:00:00Z") else { continue }
            if let prev = prevDate {
                let diff = Calendar.current.dateComponents([.day], from: prev, to: date).day ?? 0
                if diff == 1 {
                    currentStreak += 1
                } else {
                    currentStreak = 1
                }
            } else {
                currentStreak = 1
            }
            maxStreak = max(maxStreak, currentStreak)
            prevDate = date
        }
        return Streak(current: currentStreak, max: maxStreak)
    }

    // MARK: - Migration Logic
    /// Migrates data from Ionic app's SQLite database to SwiftData if needed
    /// Should be called once on app startup before other operations
    /// This method migrates data for ALL users found in the opStack tables
    public func migrateFromSQLiteIfNeeded() async {
        guard migrationService.isMigrationNeeded() else {
            logger.log(level: .info, tag: tag, message: "No SQLite migration needed")
            return
        }
        
        do {
            logger.log(level: .info, tag: tag, message: "Starting SQLite migration for all users in opStack")
            
            // Migrate data for all users found in the opStack tables
            let migratedData = try await migrationService.migrateAllUsersEntryData()
            
            let totalMigrated = migratedData.values.reduce(0, +)
            logger.log(level: .info, tag: tag, message: "SQLite migration completed: \(totalMigrated) entries migrated for \(migratedData.count) users")
            
            // Log migration details per user
            for (userId, count) in migratedData {
                logger.log(level: .info, tag: tag, message: "User \(userId): \(count) entries migrated")
            }
            
            // Update dashboard data after migration (only for current active user if available)
            do {
                let accountId = try await getAccountId()
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
            logger.log(level: .info, tag: tag, message: "✅ SQLite database cleaned up successfully")
            logger.log(level: .info, tag: tag, message: "🎉 Migration process completed!")
            
        } catch {
            logger.log(level: .error, tag: tag, message: "SQLite migration failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Sync Logic
    /// Sync all unsynced entries with the remote backend. Call this on app start or after network recovery.
    public func syncAllEntriesWithRemote() async {
        isSyncing = true
        defer { isSyncing = false }

        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            logger.log(level: .error, tag: tag, message: "Sync failed: No account ID available")
            return
        }

        do {
            // 1. Push unsynced entries to remote
            await pushUnsyncedEntriesToRemote(accountId: accountId)

            // 2. Fetch latest from remote and merge, using last sync timestamp
            let lastSyncTimestamp = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)
            print("Last sync timestamp: \(String(describing: lastSyncTimestamp))", "Last sync timestamp")
            let remoteOps = try await remoteRepo.fetchOperations(startTimestamp: lastSyncTimestamp)
            print("Fetched remote operations: \(remoteOps.operations.count) operations", "Remote operations count Last sync timestamp")
            await mergeRemoteOperations(remoteOps.operations, accountId: accountId)

            // 5. Update sync timestamp and local state
            try await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: remoteOps.timestamp)
            lastSyncTime = Date()

            // 6. Update progress, streak, and check for goal alerts
            await updateProgressAndStreakInternal()

            logger.log(level: .info, tag: tag, message: "Full sync completed successfully")

        } catch {
            logger.log(level: .error, tag: tag, message: "Sync failed: \(error.localizedDescription)")
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
                      // Log based on operation type
                      logger.log(level: .info, tag: tag, message: "Entry create/update synced: \(operation.id)")
                  } else {
                    try await localRepo.deleteEntry(byId: operation.id.uuidString)
                    try await handleEntryDeleted(operation)
                      // Log based on operation type
                      logger.log(level: .info, tag: tag, message: "Entry deleted: \(operation.id)")
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
                  logger.log(level: .error, tag: tag, message: "Sync failed: \(error)")
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
        isSyncing = true
        defer { isSyncing = false }

        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            logger.log(level: .error, tag: tag, message: "Unsynced entries sync failed: No account ID available")
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
            logger.log(level: .error, tag: tag, message: "Unsynced entries sync failed: \(error.localizedDescription)")
        }
    }

    /// Merge remote operations into local DB, resolving conflicts (latest wins by timestamp)
    private func mergeRemoteOperations(_ remoteOps: [BathScaleOperationDTO], accountId: String) async {
        // Group operations by timestamp to determine final state for each timestamp
        let groupedOps = Dictionary(grouping: remoteOps) { op in
            op.entryTimestamp ?? ""
        }
        print("Grouped operations by timestamp: \(groupedOps.keys.count) unique timestamps Last sync timestamp", groupedOps.count)
        for (timestamp, ops) in groupedOps {
            guard !timestamp.isEmpty else { continue }

            // Sort operations by serverTimestamp to process in chronological order
            let sortedOps = ops.sorted {
                ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "")
            }

            // Find the final operation for this timestamp (the latest one)
            guard let finalOp = sortedOps.last else { continue }

            // Check if local entry exists with this timestamp
            let localEntries = try? await localRepo.fetchEntriesOfTimestamp(forUserId: accountId, timestamp: timestamp)
            let localEntry = localEntries?.first

            if let localEntry = localEntry {
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
                    }
                }
            } else {
                // No local entry - only create if final operation is create
                if finalOp.operationType == OperationType.create.rawValue {
                    // Final state is create - add to local storage
                    let newEntry = Entry(from: finalOp, accountId: accountId, isSynced: true)
                    try? await localRepo.saveEntry(newEntry)
                }
                // If final operation is delete and no local entry exists, nothing to do
                // (entry was already deleted or never existed locally)
            }
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
        let _ = try? await remoteRepo.exportCsv(useR4Endpoint: useR4Endpoint)
    }

    // MARK: - Aggregation Helpers
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

            func avg(_ values: [Double?]) -> Double? {
                let vals = values.compactMap { $0 }
                return vals.isEmpty ? nil : vals.reduce(0, +) / Double(vals.count)
            }

            return BathScaleWeightSummary(
                accountId: accountId,
                period: day,
                entryTimestamp: latestTimestamp,
                date: date,
                count: count,
                weight: avg(validEntries.compactMap { $0.scaleEntry?.weight.map(Double.init) }) ?? 0,
                bodyFat: avg(validEntries.compactMap { $0.scaleEntry?.bodyFat.map(Double.init) }),
                muscleMass: avg(validEntries.compactMap { $0.scaleEntry?.muscleMass.map(Double.init) }),
                water: avg(validEntries.compactMap { $0.scaleEntry?.water.map(Double.init) }),
                bmi: avg(validEntries.compactMap { $0.scaleEntry?.bmi.map(Double.init) }),
                bmr: avg(validEntries.compactMap { $0.scaleEntryMetric?.bmr.map(Double.init) }),
                metabolicAge: avg(validEntries.compactMap { $0.scaleEntryMetric?.metabolicAge.map(Double.init) }),
                proteinPercent: avg(validEntries.compactMap { $0.scaleEntryMetric?.proteinPercent.map(Double.init) }),
                pulse: avg(validEntries.compactMap { $0.scaleEntryMetric?.pulse.map(Double.init) }),
                skeletalMusclePercent: avg(validEntries.compactMap { $0.scaleEntryMetric?.skeletalMusclePercent.map(Double.init) }),
                subcutaneousFatPercent: avg(validEntries.compactMap { $0.scaleEntryMetric?.subcutaneousFatPercent.map(Double.init) }),
                visceralFatLevel: avg(validEntries.compactMap { $0.scaleEntryMetric?.visceralFatLevel.map(Double.init) }),
                boneMass: avg(validEntries.compactMap { $0.scaleEntryMetric?.boneMass.map(Double.init) }),
                impedance: avg(validEntries.compactMap { $0.scaleEntryMetric?.impedance.map(Double.init) })
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

            func avg(_ values: [Double?]) -> Double? {
                let vals = values.compactMap { $0 }
                return vals.isEmpty ? nil : vals.reduce(0, +) / Double(vals.count)
            }

            return BathScaleWeightSummary(
                accountId: accountId,
                period: month,
                entryTimestamp: latestTimestamp,
                date: date,
                count: count,
                weight: avg(validEntries.compactMap { $0.scaleEntry?.weight.map(Double.init) }) ?? 0,
                bodyFat: avg(validEntries.compactMap { $0.scaleEntry?.bodyFat.map(Double.init) }),
                muscleMass: avg(validEntries.compactMap { $0.scaleEntry?.muscleMass.map(Double.init) }),
                water: avg(validEntries.compactMap { $0.scaleEntry?.water.map(Double.init) }),
                bmi: avg(validEntries.compactMap { $0.scaleEntry?.bmi.map(Double.init) }),
                bmr: avg(validEntries.compactMap { $0.scaleEntryMetric?.bmr.map(Double.init) }),
                metabolicAge: avg(validEntries.compactMap { $0.scaleEntryMetric?.metabolicAge.map(Double.init) }),
                proteinPercent: avg(validEntries.compactMap { $0.scaleEntryMetric?.proteinPercent.map(Double.init) }),
                pulse: avg(validEntries.compactMap { $0.scaleEntryMetric?.pulse.map(Double.init) }),
                skeletalMusclePercent: avg(validEntries.compactMap { $0.scaleEntryMetric?.skeletalMusclePercent.map(Double.init) }),
                subcutaneousFatPercent: avg(validEntries.compactMap { $0.scaleEntryMetric?.subcutaneousFatPercent.map(Double.init) }),
                visceralFatLevel: avg(validEntries.compactMap { $0.scaleEntryMetric?.visceralFatLevel.map(Double.init) }),
                boneMass: avg(validEntries.compactMap { $0.scaleEntryMetric?.boneMass.map(Double.init) }),
                impedance: avg(validEntries.compactMap { $0.scaleEntryMetric?.impedance.map(Double.init) })
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
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to evaluate goal alerts: \(error.localizedDescription)")
        }
    }

    private static func buildHistoryMonth(monthKey: String, monthEntries: [Entry]) -> HistoryMonth {
        // Build the `weights` concatenated string  "<w>|<ts>,<w>|<ts>"  like the SQL query
        let weightPairs = monthEntries.compactMap { e -> String? in
            guard let w = e.scaleEntry?.weight else { return nil }
            return "\(w)|\(e.entryTimestamp)"
        }
        let weightsConcat = weightPairs.joined(separator: ",")

        // Numeric helpers
        let weightValues = monthEntries.compactMap { $0.scaleEntry?.weight }.map(Double.init)
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
          logger.log(level: .info, tag: tag, message: "Loading dashboard data")
          // Get all entries for the account
          let entries = try await getAllEntries()
          // Aggregate data by day and month
          let dailyData = aggregateByDay(entries: entries, accountId: accountId)
          let monthlyData = aggregateByMonth(entries: entries, accountId: accountId)

          // Update published arrays
          dailySummaries = dailyData.compactMap { $0 }.sorted { $0.period < $1.period }
          monthlySummaries = monthlyData.compactMap { $0 }.sorted { $0.period < $1.period }

          logger.log(level: .info, tag: tag, message: "Dashboard data loaded - Daily: \(dailySummaries.count), Monthly: \(monthlySummaries.count)")
        } catch {
          logger.log(level: .error, tag: tag, message: "Failed to load entries: \(error.localizedDescription)")
        }
    }

    /// Handles entry addition by updating affected day and month summaries
    func handleEntryAdded(_ entry: Entry) async throws {
        let accountId = try await getAccountId()

        logger.log(level: .info, tag: tag, message: "Handling entry addition: \(entry.id)")

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
            logger.log(level: .error, tag: tag, message: "Failed to sync new entry to integrations: \(error.localizedDescription)")
        }
        
        logger.log(level: .info, tag: tag, message: "Entry addition handled successfully")
    }

    /// Handles entry update by treating as delete + add
    func handleEntryUpdated(_ entry: Entry) async throws {
        logger.log(level: .info, tag: tag, message: "Handling entry update: \(entry.id)")

        // For updates, we can treat as delete + add for simplicity
        try await handleEntryDeleted(entry)
        try await handleEntryAdded(entry)

        logger.log(level: .info, tag: tag, message: "Entry update handled successfully")
    }

    /// Handles entry deletion by updating affected day and month summaries
    func handleEntryDeleted(_ entry: Entry) async throws {
        let accountId = try await getAccountId()

        logger.log(level: .info, tag: tag, message: "Handling entry deletion: \(entry.id)")

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
            logger.log(level: .error, tag: tag, message: "Failed to delete entry from integrations: \(error.localizedDescription)")
        }
        
        logger.log(level: .info, tag: tag, message: "Entry deletion handled successfully")
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



}
