import Foundation
import SwiftUI

/// Manages all data caching and API synchronization for the dashboard
@MainActor
class DashboardDataManager: ObservableObject, DashboardDataManaging {

    // MARK: - Dependencies
    @Injector private var accountService: AccountService
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService

    // MARK: - Published Properties
    @Published var state: DataState

    // MARK: - Private Properties
    private let calendar = Calendar.current

    // MARK: - Initialization
    init(initialState: DataState = DataState()) {
        self.state = initialState
    }

    // MARK: - Data Loading
    func loadInitialData() async throws {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw DashboardError.noActiveAccount
        }

        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Loading initial dashboard data")

            // Get all entries for the account
            let entries = try await entryService.getAllEntries()

            // Aggregate data by day and month
            let dailyData = entryService.aggregateByDay(entries: entries, accountId: accountId)
            let monthlyData = entryService.aggregateByMonth(entries: entries, accountId: accountId)

            // Update caches
            state.dailyCache = Dictionary(
                uniqueKeysWithValues: dailyData.compactMap { summary in
                    guard let summary = summary else { return nil }
                    return (summary.period, summary)
                }
            )

            state.monthlyCache = Dictionary(
                uniqueKeysWithValues: monthlyData.compactMap { summary in
                    guard let summary = summary else { return nil }
                    return (summary.period, summary)
                }
            )

            // Update published arrays
            updatePublishedArrays()

            logger.log(level: .info, tag: "DashboardDataManager", message: "Initial data loaded successfully - Daily: \(state.dailyCache.count), Monthly: \(state.monthlyCache.count)")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to load initial data: \(error)")
            throw DashboardError.dataLoadingFailed(error)
        }
    }

    func refreshData() async throws {
        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Refreshing dashboard data")

            // Clear existing caches
            state.dailyCache.removeAll()
            state.monthlyCache.removeAll()

            // Reload all data
            try await loadInitialData()

            logger.log(level: .info, tag: "DashboardDataManager", message: "Data refreshed successfully")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to refresh data: \(error)")
            throw DashboardError.dataLoadingFailed(error)
        }
    }

    // MARK: - Entry Management
    func handleEntryAdded(_ entry: Entry) async throws {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw DashboardError.noActiveAccount
        }

        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Handling entry addition: \(entry.id)")

            let dayKey = DateTimeTools.getDateStringFromDate(entry.entryTimestamp)
            let monthKey = DateTimeTools.getMonthStringFromDate(entry.entryTimestamp)

            // Fetch all entries for the affected day and month
            let dayEntries = try await fetchEntriesForPeriod(dayKey, .day)
            let monthEntries = try await fetchEntriesForPeriod(monthKey, .month)

            // Update caches with aggregated data
            if let daySummary = entryService.aggregateByDay(entries: dayEntries, accountId: accountId).first {
                state.dailyCache[dayKey] = daySummary
            }

            if let monthSummary = entryService.aggregateByMonth(entries: monthEntries, accountId: accountId).first {
                state.monthlyCache[monthKey] = monthSummary
            }

            // Update published arrays
            updatePublishedArrays()

            logger.log(level: .info, tag: "DashboardDataManager", message: "Entry addition handled successfully")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to handle entry addition: \(error)")
            throw DashboardError.cacheUpdateFailed("Failed to update cache for entry addition")
        }
    }

    func handleEntryUpdated(_ entry: Entry) async throws {
        // For updates, we can treat as delete + add for simplicity
        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Handling entry update: \(entry.id)")

            try await handleEntryDeleted(entry)
            try await handleEntryAdded(entry)

            logger.log(level: .info, tag: "DashboardDataManager", message: "Entry update handled successfully")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to handle entry update: \(error)")
            throw DashboardError.cacheUpdateFailed("Failed to update cache for entry update")
        }
    }

    func handleEntryDeleted(_ entry: Entry) async throws {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw DashboardError.noActiveAccount
        }

        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Handling entry deletion: \(entry.id)")

            let dayKey = DateTimeTools.getDateStringFromDate(entry.entryTimestamp)
            let monthKey = DateTimeTools.getMonthStringFromDate(entry.entryTimestamp)

            // Fetch remaining entries for the affected day and month
            let dayEntries = try await fetchEntriesForPeriod(dayKey, .day)
            let monthEntries = try await fetchEntriesForPeriod(monthKey, .month)

            // Update or remove cache entries
            if let daySummary = entryService.aggregateByDay(entries: dayEntries, accountId: accountId).first {
                state.dailyCache[dayKey] = daySummary
            } else {
                state.dailyCache.removeValue(forKey: dayKey)
            }

            if let monthSummary = entryService.aggregateByMonth(entries: monthEntries, accountId: accountId).first {
                state.monthlyCache[monthKey] = monthSummary
            } else {
                state.monthlyCache.removeValue(forKey: monthKey)
            }

            // Update published arrays
            updatePublishedArrays()

            logger.log(level: .info, tag: "DashboardDataManager", message: "Entry deletion handled successfully")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to handle entry deletion: \(error)")
            throw DashboardError.cacheUpdateFailed("Failed to update cache for entry deletion")
        }
    }

    // MARK: - Data Retrieval
    func getContinuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary] {
        switch period {
        case .week, .month:
            // For week view, use daily summaries
            return state.dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        case .year, .total:
            // For year view, use monthly summaries but limit to 12 values per year
            return state.monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        }
    }

    func getLatestEntry() async throws -> Entry? {
        do {
            let latestEntry = try await entryService.getLatestEntry()

            // Update latest weight stored if available
            if let weight = latestEntry?.scaleEntry?.weight {
                state.latestWeightStored = weight
            }

            return latestEntry

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to get latest entry: \(error)")
            throw DashboardError.dataLoadingFailed(error)
        }
    }

    // Synchronous version for UI operations
    func getLatestEntrySync() -> Entry? {
        // Return the latest entry from state if available
        // This is a simplified version for UI operations
        // For now, we'll return nil to use the fallback method
        // In a real implementation, you might cache the latest entry in state
        return nil
    }

    // MARK: - Cache Management
    func clearCache() async throws {
      logger.log(level: .info, tag: "DashboardDataManager", message: "Clearing dashboard cache")

      state.dailyCache.removeAll()
      state.monthlyCache.removeAll()
      state.dailySummaries.removeAll()
      state.monthlySummaries.removeAll()
      state.latestWeightStored = 0

      logger.log(level: .info, tag: "DashboardDataManager", message: "Cache cleared successfully")
    }

    // MARK: - Data Validation
    func validateCacheConsistency() throws {
        // Check that published arrays match cache data
        let dailyCacheCount = state.dailyCache.count
        let dailySummariesCount = state.dailySummaries.compactMap { $0 }.count

        let monthlyCacheCount = state.monthlyCache.count
        let monthlySummariesCount = state.monthlySummaries.compactMap { $0 }.count

        guard dailyCacheCount == dailySummariesCount else {
            throw DashboardError.cacheUpdateFailed("Daily cache inconsistency: cache=\(dailyCacheCount), summaries=\(dailySummariesCount)")
        }

        guard monthlyCacheCount == monthlySummariesCount else {
            throw DashboardError.cacheUpdateFailed("Monthly cache inconsistency: cache=\(monthlyCacheCount), summaries=\(monthlySummariesCount)")
        }

        logger.log(level: .info, tag: "DashboardDataManager", message: "Cache consistency validation passed")
    }

    // MARK: - Data Analytics
    func getDataAnalytics() -> DataAnalytics {
        let totalEntries = state.dailyCache.count + state.monthlyCache.count
        let dailyEntries = state.dailyCache.count
        let monthlyEntries = state.monthlyCache.count

        let dateRange = calculateDateRange()
        let dataCompleteness = calculateDataCompleteness()

        return DataAnalytics(
            totalEntries: totalEntries,
            dailyEntries: dailyEntries,
            monthlyEntries: monthlyEntries,
            dateRange: dateRange,
            dataCompleteness: dataCompleteness,
            cacheSize: calculateCacheSize(),
            lastUpdated: Date()
        )
    }

    // MARK: - Private Methods
    private func updatePublishedArrays() {
        // Update published arrays from cache
        state.dailySummaries = Array(state.dailyCache.values).sorted { $0.period < $1.period }
        state.monthlySummaries = Array(state.monthlyCache.values).sorted { $0.period < $1.period }
    }

    private func limitToYearlyData(_ monthlyData: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        var result: [BathScaleWeightSummary] = []
        var currentYear: Int?
        var yearCount = 0

        for summary in monthlyData {
            let year = calendar.component(.year, from: summary.date)

            if currentYear != year {
                currentYear = year
                yearCount = 0
            }

            if yearCount < 12 {
                result.append(summary)
                yearCount += 1
            }
        }

        return result
    }

    private func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }

        // Validate that all summaries have valid dates
        let validSummaries = summaries.filter { summary in
            // Ensure the date is not in the distant past or future (basic validation)
            let year = calendar.component(.year, from: summary.date)
            return year >= 1900 && year <= 2100
        }

        guard !validSummaries.isEmpty else { return true }

        let years = Set(validSummaries.map { calendar.component(.year, from: $0.date) })
        return years.count == 1
    }

    private func fetchEntriesForPeriod(_ periodKey: String, _ type: PeriodType) async throws -> [Entry] {
        do {
            switch type {
            case .day:
                return try await entryService.getEntries(forDay: periodKey)
            case .month:
                return try await entryService.getEntries(forMonth: periodKey)
            }
        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to fetch entries for period \(periodKey): \(error)")
            return []
        }
    }

    private func calculateDateRange() -> DateRange? {
        let allDates = state.dailyCache.values.map { $0.date } + state.monthlyCache.values.map { $0.date }
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return nil }
        return DateRange(start: minDate, end: maxDate)
    }

    private func calculateDataCompleteness() -> Double {
        guard let dateRange = calculateDateRange() else { return 0.0 }

        let totalDays = Calendar.current.dateComponents([.day], from: dateRange.start, to: dateRange.end).day ?? 0
        let actualDays = state.dailyCache.count

        guard totalDays > 0 else { return 0.0 }
        return Double(actualDays) / Double(totalDays)
    }

    private func calculateCacheSize() -> Int {
        // Rough estimate of cache size in bytes
        let dailySize = state.dailyCache.count * 200 // Approximate size per daily summary
        let monthlySize = state.monthlyCache.count * 200 // Approximate size per monthly summary
        return dailySize + monthlySize
    }

    // MARK: - Cache Optimization
    func optimizeCache() async throws {
        logger.log(level: .info, tag: "DashboardDataManager", message: "Optimizing cache")

        // Remove old entries beyond a certain threshold (e.g., 1 year)
        let cutoffDate = Calendar.current.date(byAdding: .year, value: -1, to: Date()) ?? Date()

        // Filter out old daily entries
        let filteredDailyCache = state.dailyCache.filter { _, summary in
            summary.date >= cutoffDate
        }

        // Filter out old monthly entries (keep longer for monthly view)
        let monthlyCutoffDate = Calendar.current.date(byAdding: .year, value: -2, to: Date()) ?? Date()
        let filteredMonthlyCache = state.monthlyCache.filter { _, summary in
            summary.date >= monthlyCutoffDate
        }

        // Update caches if optimization removed entries
        if filteredDailyCache.count < state.dailyCache.count {
            state.dailyCache = filteredDailyCache
            logger.log(level: .info, tag: "DashboardDataManager", message: "Optimized daily cache: removed \(state.dailyCache.count - filteredDailyCache.count) old entries")
        }

        if filteredMonthlyCache.count < state.monthlyCache.count {
            state.monthlyCache = filteredMonthlyCache
            logger.log(level: .info, tag: "DashboardDataManager", message: "Optimized monthly cache: removed \(state.monthlyCache.count - filteredMonthlyCache.count) old entries")
        }

        // Update published arrays
        updatePublishedArrays()

        logger.log(level: .info, tag: "DashboardDataManager", message: "Cache optimization completed")
    }

    
}
