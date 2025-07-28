import Foundation
import SwiftUI
import Combine

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

    // MARK: - Initialization
    init(initialState: DataState = DataState()) {
        self.state = initialState
        setupEntryServiceBindings()
    }

    // MARK: - Setup Bindings
    private func setupEntryServiceBindings() {
        // Directly bind to EntryService's published properties
        entryService.$dailySummaries
            .sink { [weak self] dailySummaries in
                self?.updateStateFromDailySummaries(dailySummaries)
            }
            .store(in: &cancellables)

        entryService.$monthlySummaries
            .sink { [weak self] monthlySummaries in
                self?.updateStateFromMonthlySummaries(monthlySummaries)
            }
            .store(in: &cancellables)
    }

    // MARK: - Data Loading
    func loadInitialData() async throws {
        logger.log(level: .info, tag: "DashboardDataManager", message: "Dashboard data manager initialized - listening to EntryService published arrays")
        // No need to load data here - ContentView handles data loading
        // We just listen to EntryService's published arrays via setupEntryServiceBindings()
    }

    // MARK: - Entry Management
    func handleEntryAdded(_ entry: Entry) async throws {
        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Handling entry addition: \(entry.id)")

            // Use EntryService to handle entry addition
            try await entryService.handleEntryAdded(entry)

            logger.log(level: .info, tag: "DashboardDataManager", message: "Entry addition handled successfully")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to handle entry addition: \(error)")
            throw DashboardError.cacheUpdateFailed("Failed to update cache for entry addition")
        }
    }

    func handleEntryUpdated(_ entry: Entry) async throws {
        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Handling entry update: \(entry.id)")

            // Use EntryService to handle entry update
            try await entryService.handleEntryUpdated(entry)

            logger.log(level: .info, tag: "DashboardDataManager", message: "Entry update handled successfully")

        } catch {
            logger.log(level: .error, tag: "DashboardDataManager", message: "Failed to handle entry update: \(error)")
            throw DashboardError.cacheUpdateFailed("Failed to update cache for entry update")
        }
    }

    func handleEntryDeleted(_ entry: Entry) async throws {
        do {
            logger.log(level: .info, tag: "DashboardDataManager", message: "Handling entry deletion: \(entry.id)")

            // Use EntryService to handle entry deletion
            try await entryService.handleEntryDeleted(entry)

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


        // Clear local state
        state.dailyCache.removeAll()
        state.monthlyCache.removeAll()
        state.dailySummaries.removeAll()
        state.monthlySummaries.removeAll()
        state.latestWeightStored = 0

        logger.log(level: .info, tag: "DashboardDataManager", message: "Cache cleared successfully")
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
            throw DashboardError.cacheUpdateFailed("Monthly cache inconsistency: EntryService=\(entryServiceMonthlyCount), state=\(stateMonthlyCount)")
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
    private func updateStateFromDailySummaries(_ dailySummaries: [BathScaleWeightSummary]) {
        // Update state from EntryService published properties
        state.dailySummaries = dailySummaries.map { $0 }

        // Update cache for backward compatibility
        state.dailyCache = Dictionary(
            uniqueKeysWithValues: dailySummaries.map { ($0.period, $0) }
        )
    }

    private func updateStateFromMonthlySummaries(_ monthlySummaries: [BathScaleWeightSummary]) {
        // Update state from EntryService published properties
        state.monthlySummaries = monthlySummaries.map { $0 }

        // Update cache for backward compatibility
        state.monthlyCache = Dictionary(
            uniqueKeysWithValues: monthlySummaries.map { ($0.period, $0) }
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
