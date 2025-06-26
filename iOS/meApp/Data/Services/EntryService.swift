import Foundation
import Combine

@MainActor
final class EntryService: EntryServiceProtocol {
    @Injector var logger: LoggerService
    private let accountService: AccountServiceProtocol
    private let localRepo: EntryRepositoryProtocol = EntryRepository()
    private let localKVRepo: EntryRepositoryLocal = EntryRepositoryLocal()
    private let remoteRepo: EntryRepositoryAPIProtocol = EntryRepositoryAPI()
    static let shared = EntryService(accountService: AccountService.shared)
    // MARK: - Publishers ------------------------------------------------

    /// Emits each time a new entry is locally stored (create/delete/update).
    let entrySaved = PassthroughSubject<Entry, Never>()

    let tag = "EntryService"

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
    
    func saveNewEntry(_ entry: Entry) async throws {
        let entry = entry
        entry.isSynced = false
        try await localRepo.saveEntry(entry)
        // Broadcast change
        entrySaved.send(entry)
        await syncUnsyncedEntries()
    }
    
    func saveNewEntries(_ entries: [Entry]) async throws {
        for entry in entries {
            entry.isSynced = false
            try await localRepo.saveEntry(entry)
            entrySaved.send(entry)
        }
        await syncUnsyncedEntries()
    }
    
    func deleteEntry(_ entry: Entry) async throws {
        let deletedEntry = entry
        deletedEntry.operationType = "delete"
        deletedEntry.isSynced = false
        try await localRepo.saveEntry(deletedEntry)
        entrySaved.send(deletedEntry)
        await syncUnsyncedEntries()
    }
    
    // MARK: - Query
    func getAllEntries() async throws -> [Entry] {
        let accountId = try await getAccountId()
        return try await localRepo.fetchEntries(forUserId: accountId, operationType: "create")
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
        return try await localRepo.fetchEntries(forMonth: month, userId: accountId)
    }
    
    // MARK: - Month/History
    	func getMonthsAll() async throws -> [HistoryMonth] {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: "create")
        // Group by YYYY-MM prefix
        let grouped = Dictionary(grouping: entries) { String($0.entryTimestamp.prefix(7)) }

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
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: "create")
        // Get last 12 months
        let calendar = Calendar.current
        let now = Date()
        let months = (0..<12).map { offset -> String in
            let date = calendar.date(byAdding: .month, value: -offset, to: now)!
            let comps = calendar.dateComponents([.year, .month], from: date)
            return String(format: "%04d-%02d", comps.year ?? 0, comps.month ?? 0)
        }
        let grouped = Dictionary(grouping: entries) { (entry) -> String in
            String(entry.entryTimestamp.prefix(7))
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
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: "create").sorted { $0.entryTimestamp < $1.entryTimestamp }
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
        let entries = try await localRepo.fetchEntries(forUserId: accountId, operationType: "create")
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
    
    // MARK: - Sync Logic
    /// Sync all unsynced entries with the remote backend. Call this on app start or after network recovery.
    public func syncAllEntriesWithRemote() async {
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            return
        }
        // 1. Get all unsynced entries
        let unsynced = (try? await localRepo.fetchUnsyncedEntries(forUserId: accountId)) ?? []
        let dtos = unsynced.map { $0.toOperationDTO() }
        do {
            // 2. Try to sync with backend
            if !dtos.isEmpty {
                try await remoteRepo.syncOperations(operations: dtos)
                // 3. On success, mark as synced
                for entry in unsynced {
                    entry.isSynced = true
                    try? await localRepo.updateEntry(entry)
                }
            }
            // 4. Fetch latest from remote and merge, using last sync timestamp
            let lastSyncTimestamp = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)
            let remoteOps = try await remoteRepo.fetchOperations(startTimestamp: lastSyncTimestamp)
            await mergeRemoteOperations(remoteOps.operations, accountId: accountId)
            
            try? await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: remoteOps.timestamp)
        } catch {
            // If sync fails, leave as isSynced = false for retry
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
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            return
        }
        let unsynced = (try? await localRepo.fetchUnsyncedEntries(forUserId: accountId)) ?? []
        let dtos = unsynced.map { $0.toOperationDTO() }
        do {
            if !dtos.isEmpty {
                try await remoteRepo.syncOperations(operations: dtos)
                for entry in unsynced {
                    entry.isSynced = true
                    try? await localRepo.updateEntry(entry)
                }
            }
            // After syncing, update last sync timestamp
            let now = ISO8601DateFormatter().string(from: Date())
            try? await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: now)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to sync unsynced entries:", data: error.localizedDescription)
        }
    }
    
    /// Merge remote operations into local DB, resolving conflicts (latest wins by timestamp)
    private func mergeRemoteOperations(_ remoteOps: [BathScaleOperationDTO], accountId: String) async {
        // For each remote op, check if local entry exists
        for remoteOp in remoteOps {
            guard let remoteTimestamp = remoteOp.entryTimestamp else { continue }
            let localEntries = try? await localRepo.fetchEntriesOfTimestamp(forUserId: accountId, timestamp: remoteTimestamp)
            if let localEntry = localEntries?.first {
                // Conflict: keep the one with the latest serverTimestamp
                let localServerTS = localEntry.serverTimestamp ?? ""
                let remoteServerTS = remoteOp.serverTimestamp ?? ""
                if remoteServerTS > localServerTS {
                    // Update local with remote
                    let updated = Entry(from: remoteOp,accountId: accountId, isSynced: true)
                    try? await localRepo.updateEntry(updated)
                }
            } else {
                // Not found locally, insert
              let newEntry = Entry(from: remoteOp, accountId: accountId, isSynced: true)
                try? await localRepo.saveEntry(newEntry)
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
    
    // MARK: - Helpers ---------------------------------------------------

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
}
