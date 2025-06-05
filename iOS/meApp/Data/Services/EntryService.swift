import Foundation

@MainActor
final class EntryService: EntryServiceProtocol {
    private let accountService: AccountServiceProtocol
    private let localRepo: EntryRepositoryProtocol = EntryRepository()
    private let localKVRepo: EntryRepositoryLocal = EntryRepositoryLocal()
    private let remoteRepo: EntryRepositoryAPIProtocol = EntryRepositoryAPI()

    init(accountService: AccountServiceProtocol) {
        self.accountService = accountService
    }

    // MARK: - Helper
    private func getAccountId() async throws -> String {
        guard let account = try await accountService.getActiveAccount() else {
            throw NSError(domain: "EntryService", code: 401, userInfo: [NSLocalizedDescriptionKey: "No active account"])
        }
        return ""
    }

    // MARK: - CRUD
    func clearAllData() async {
        try? await localRepo.deleteAllEntries()
    }

    func saveNewEntry(_ entry: Entry) async throws {
        let entry = entry
        entry.isSynced = false
        try await localRepo.saveEntry(entry)
        await syncUnsyncedEntries()
    }

    func saveNewEntries(_ entries: [Entry]) async throws {
      for entry in entries {
            entry.isSynced = false
            try await localRepo.saveEntry(entry)
        }
        await syncUnsyncedEntries()
    }

    func deleteEntry(_ entry: Entry) async throws {
        let deletedEntry = entry
        deletedEntry.operationType = "delete"
        deletedEntry.isSynced = false
        try await localRepo.saveEntry(deletedEntry)
        await syncUnsyncedEntries()
    }

    // MARK: - Query
    func getAllEntries() async throws -> [Entry] {
        let accountId = try await getAccountId()
        return try await localRepo.fetchEntries(forUserId: accountId)
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
        let entries = try await localRepo.fetchEntries(forUserId: accountId)
        let grouped = Dictionary(grouping: entries) { entry in
            String(entry.entryTimestamp.prefix(7))
        }
        var result: [HistoryMonth] = []
        for (month, entries) in grouped {
            // Calculate weights string
            let weightPairs: [String] = entries.compactMap { e in
                guard let w = e.scaleEntry?.weight else { return nil }
                return "\(w)|\(e.entryTimestamp)"
            }
            let weights = weightPairs.joined(separator: ",")
            // Calculate weight values
            let weightValues: [Double] = entries.compactMap {
                guard let w = $0.scaleEntry?.weight else { return nil }
                return Double(w)
            }
            let avgWeight: Double? = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count)
            let minWeight = weightValues.min()
            let maxWeight = weightValues.max()
            // Calculate change
            let firstEntry = entries.min(by: { $0.entryTimestamp < $1.entryTimestamp })
            let lastEntry = entries.max(by: { $0.entryTimestamp < $1.entryTimestamp })
            let change: String? = {
                guard let first = firstEntry?.scaleEntry?.weight,
                      let last = lastEntry?.scaleEntry?.weight else { return nil }
                return String(format: "%.1f", last - first)
            }()
            let historyMonth = HistoryMonth(
                id: month,
                weight: avgWeight,
                entryTimestamp: month,
                count: entries.count,
                weights: weights,
                change: change,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                date: nil,
                time: nil,
                month: String(month.suffix(2)),
                year: String(month.prefix(4)),
                min: minWeight,
                max: maxWeight
            )
            result.append(historyMonth)
        }
        return result.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    func getMonthDetail(month: String) async throws -> [Entry] {
        return try await getEntries(forMonth: month)
    }

    func getMonthYear() async throws -> [HistoryMonth] {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId)
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
            guard let entries = grouped[month], !entries.isEmpty else { continue }
            let weights = entries.compactMap { e in
                if let w = e.scaleEntry?.weight {
                    return "\(w)|\(e.entryTimestamp)"
                } else {
                    return nil
                }
            }.joined(separator: ",")
            let weightValues: [Double] = entries.compactMap {
                guard let w = $0.scaleEntry?.weight else { return nil }
                return Double(w)
            }
            let avgWeight: Double? = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count)
            let minWeight = weightValues.min()
            let maxWeight = weightValues.max()
            let change: String? = {
                guard let first = entries.min(by: { $0.entryTimestamp < $1.entryTimestamp })?.scaleEntry?.weight,
                      let last = entries.max(by: { $0.entryTimestamp < $1.entryTimestamp })?.scaleEntry?.weight else { return nil }
                return String(format: "%.1f", Double(last) - Double(first))
            }()
            let historyMonth = HistoryMonth(
                id: month,
                weight: avgWeight,
                entryTimestamp: month,
                count: entries.count,
                weights: weights,
                change: change,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                date: nil,
                time: nil,
                month: String(month.suffix(2)),
                year: String(month.prefix(4)),
                min: minWeight,
                max: maxWeight
            )
            result.append(historyMonth)
        }
        return result
    }

    // MARK: - Progress/Stats
    func getProgress() async throws -> Progress {
        let accountId = try await getAccountId()
        let entries = try await localRepo.fetchEntries(forUserId: accountId).sorted { $0.entryTimestamp < $1.entryTimestamp }
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
        let entries = try await localRepo.fetchEntries(forUserId: accountId)
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
            // If sync fails, leave as isSynced = false for retry
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
                    let updated = Entry(from: remoteOp, isSynced: true)
                    try? await localRepo.updateEntry(updated)
                }
            } else {
                // Not found locally, insert
                let newEntry = Entry(from: remoteOp, isSynced: true)
                try? await localRepo.saveEntry(newEntry)
            }
        }
    }
}
