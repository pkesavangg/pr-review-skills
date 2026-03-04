import Combine
import Foundation
import SwiftUI

/// Manages UI state and coordinates with EntryService for dashboard data operations
@MainActor
class DashboardDataManager: ObservableObject, DashboardDataManaging {

    // MARK: - Dependencies
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: DataState

    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Cached Sorted Data (Performance Optimization)
    /// Pre-sorted daily summaries to avoid repeated sorting on every access
    private var cachedSortedDailySummaries: [BathScaleWeightSummary] = []
    /// Pre-sorted monthly summaries to avoid repeated sorting on every access
    private var cachedSortedMonthlySummaries: [BathScaleWeightSummary] = []

    // MARK: - Cached Date Bounds (Performance Optimization)
    /// Cached minimum date for daily summaries
    private(set) var cachedDailyMinDate: Date?
    /// Cached maximum date for daily summaries
    private(set) var cachedDailyMaxDate: Date?
    /// Cached minimum date for monthly summaries
    private(set) var cachedMonthlyMinDate: Date?
    /// Cached maximum date for monthly summaries
    private(set) var cachedMonthlyMaxDate: Date?

    // MARK: - Initialization
    init(initialState: DataState = DataState()) {
        self.state = initialState
        setupEntryServiceBindings()
    }

    // MARK: - Setup Bindings
    private func setupEntryServiceBindings() {
        // Directly bind to EntryService's published properties
        // Ensure updates happen on main thread to avoid publishing from background threads
        entryService.$dailySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] dailySummaries in
                self?.updateStateFromDailySummaries(dailySummaries)
            }
            .store(in: &cancellables)

        entryService.$monthlySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] monthlySummaries in
                self?.updateStateFromMonthlySummaries(monthlySummaries)
            }
            .store(in: &cancellables)
    }

    // MARK: - Data Loading
    func loadInitialData() async throws {
        logger.log(
            level: .debug,
            tag: "DashboardDataManager",
            message: "Dashboard data manager initialized - listening to EntryService published arrays"
        )
    
        // No need to load data here - ContentView handles data loading
        // We just listen to EntryService's published arrays via setupEntryServiceBindings()
    }

    /// Initialize the data manager (sets up bindings and prepares for data loading)
    func initializeDataManager() async throws {
        try await loadInitialData()
    }

    /// Loads the latest entry data and updates internal state
    /// - Returns: The latest entry if available, along with its weight
    func loadLatestEntryData() async throws -> (entry: Entry?, weight: Int?) {
        do {
            guard let latestEntry = try await getLatestEntry() else {
                return (nil, nil)
            }

            // Extract relationship data immediately after fetch, before any further await
            let weight = latestEntry.scaleEntry?.weight

            // Update latest weight stored if available
            if let weight = weight {
                state.latestWeightStored = weight
            }

            return (latestEntry, weight)
        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to load latest entry data: \(error)")
            throw error
        }
    }

    // MARK: - Data Retrieval

    /// Returns pre-sorted operations for the given time period.
    /// Uses cached sorted arrays to avoid repeated sorting on every access.
    /// - Parameter period: The time period to get operations for
    /// - Returns: Pre-sorted array of weight summaries
    func getContinuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary] {
        switch period {
        case .week, .month:
            // Return pre-sorted daily summaries (sorted once when data changes)
            return cachedSortedDailySummaries
        case .year, .total:
            // Return pre-sorted monthly summaries (sorted once when data changes)
            return cachedSortedMonthlySummaries
        }
    }

    /// Returns cached date bounds for the given time period.
    /// Uses pre-calculated min/max dates to avoid repeated O(n) calculations.
    /// - Parameter period: The time period to get bounds for
    /// - Returns: Tuple of (min, max) dates, or nil if no data
    func getDateBounds(for period: TimePeriod) -> (min: Date, max: Date)? {
        switch period {
        case .week, .month:
            guard let min = cachedDailyMinDate, let max = cachedDailyMaxDate else { return nil }
            return (min, max)
        case .year, .total:
            guard let min = cachedMonthlyMinDate, let max = cachedMonthlyMaxDate else { return nil }
            return (min, max)
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
        // Clear local state
        state.dailyCache.removeAll()
        state.monthlyCache.removeAll()
        state.dailySummaries.removeAll()
        state.monthlySummaries.removeAll()
        state.latestWeightStored = 0

        // Clear sorted caches
        cachedSortedDailySummaries.removeAll()
        cachedSortedMonthlySummaries.removeAll()

        // Clear date bounds
        cachedDailyMinDate = nil
        cachedDailyMaxDate = nil
        cachedMonthlyMinDate = nil
        cachedMonthlyMaxDate = nil
    }

    // MARK: - Data Validation
    func validateCacheConsistency() throws {
        // Check that published arrays match EntryService data
        let entryServiceDailyCount = entryService.dailySummaries.count
        let stateDailyCount = state.dailySummaries.compactMap { $0 }.count

        let entryServiceMonthlyCount = entryService.monthlySummaries.count
        let stateMonthlyCount = state.monthlySummaries.compactMap { $0 }.count

        guard entryServiceDailyCount == stateDailyCount else {
            throw DashboardError.cacheUpdateFailed("Daily cache inconsistency: EntryService=\(entryServiceDailyCount), state=\(stateDailyCount)")
        }

        guard entryServiceMonthlyCount == stateMonthlyCount else {
            throw DashboardError.cacheUpdateFailed(
                "Monthly cache inconsistency: EntryService=\(entryServiceMonthlyCount), state=\(stateMonthlyCount)"
            )
        }

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
    private func updateStateFromDailySummaries(_ dailySummaries: [BathScaleWeightSummary]) {
        // Update caches FIRST — state mutation fires $state synchronously and
        // subscribers (e.g. DashboardStore chart re-init) read cachedSortedDailySummaries
        // via getContinuousOperations(). If state is set first, subscribers see stale caches.
        cachedSortedDailySummaries = dailySummaries.sorted { $0.date < $1.date }
        cachedDailyMinDate = cachedSortedDailySummaries.first?.date
        cachedDailyMaxDate = cachedSortedDailySummaries.last?.date

        // Update state LAST — triggers $state publisher and downstream subscribers
        state.dailySummaries = dailySummaries.map { $0 }
        state.dailyCache = Dictionary(
            uniqueKeysWithValues: dailySummaries.map { ($0.period, $0) }
        )

        logger.log(
            level: .debug,
            tag: "DashboardDataManager",
            message: "Updated daily summaries cache: \(cachedSortedDailySummaries.count) items, " +
                "bounds: \(cachedDailyMinDate?.description ?? "nil") to \(cachedDailyMaxDate?.description ?? "nil")"
        )
    
    }

    private func updateStateFromMonthlySummaries(_ monthlySummaries: [BathScaleWeightSummary]) {
        // Update caches FIRST (same reason as daily — see above)
        cachedSortedMonthlySummaries = monthlySummaries.sorted { $0.date < $1.date }
        cachedMonthlyMinDate = cachedSortedMonthlySummaries.first?.date
        cachedMonthlyMaxDate = cachedSortedMonthlySummaries.last?.date

        // Update state LAST
        state.monthlySummaries = monthlySummaries.map { $0 }
        state.monthlyCache = Dictionary(
            uniqueKeysWithValues: monthlySummaries.map { ($0.period, $0) }
        )

        logger.log(
            level: .debug,
            tag: "DashboardDataManager",
            message: "Updated monthly summaries cache: \(cachedSortedMonthlySummaries.count) items, " +
                "bounds: \(cachedMonthlyMinDate?.description ?? "nil") to \(cachedMonthlyMaxDate?.description ?? "nil")"
        )
    
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
}
