import Combine
import Foundation
@testable import meApp
import Testing

@MainActor
extension DashboardDataManagerTests {

    @Test("getLatestEntry: throws DashboardError.dataLoadingFailed when entry service throws")
    func getLatestEntryError() async {
        let sut = DashboardTestFixtures.makeDataManagerSUT(hasActiveAccount: false).sut

        do {
            _ = try await sut.getLatestEntry()
            Issue.record("Expected getLatestEntry to throw")
        } catch {
            if case DashboardError.dataLoadingFailed = error {
                // Expected error type
            } else {
                Issue.record("Expected DashboardError.dataLoadingFailed, got \(error)")
            }
        }
    }

    @Test("getLatestEntry: returns latest entry when multiple entries exist")
    func getLatestEntryMultiple() async throws {
        let entries = [
            EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-05T08:00:00Z", weight: 1850),
            EntryTestFixtures.makeEntry(timestamp: "2026-03-03T08:00:00Z", weight: 1820)
        ]
        let sut = DashboardTestFixtures.makeDataManagerSUT(entries: entries).sut

        let result = try await sut.getLatestEntry()

        #expect(result?.scaleEntry?.weight == 1850)
        #expect(sut.state.latestWeightStored == 1850)
    }

    // MARK: - loadLatestEntryData Tests

    @Test("loadLatestEntryData: returns entry and weight when available")
    func loadLatestEntryDataWithWeight() async throws {
        let entry = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-01T08:00:00Z",
            weight: 1800
        )
        let sut = DashboardTestFixtures.makeDataManagerSUT(entries: [entry]).sut

        let result = try await sut.loadLatestEntryData()

        #expect(result.entry != nil)
        #expect(result.weight == 1800)
        #expect(sut.state.latestWeightStored == 1800)
    }

    @Test("loadLatestEntryData: returns entry and nil weight when no scale entry")
    func loadLatestEntryDataWithoutWeight() async throws {
        let entry = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-01T08:00:00Z",
            weight: nil
        )
        let sut = DashboardTestFixtures.makeDataManagerSUT(entries: [entry]).sut

        let result = try await sut.loadLatestEntryData()

        #expect(result.entry != nil)
        #expect(result.weight == nil)
    }

    @Test("loadLatestEntryData: returns nil entry and nil weight when no entries")
    func loadLatestEntryDataNil() async throws {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

        let result = try await sut.loadLatestEntryData()

        #expect(result.entry == nil)
        #expect(result.weight == nil)
    }

    @Test("loadLatestEntryData: rethrows when entry service fails")
    func loadLatestEntryDataError() async {
        let sut = DashboardTestFixtures.makeDataManagerSUT(hasActiveAccount: false).sut

        do {
            _ = try await sut.loadLatestEntryData()
            Issue.record("Expected loadLatestEntryData to throw")
        } catch {
            // Expected: error is rethrown
        }
    }

    @Test("loadLatestEntryData: propagates DashboardError.dataLoadingFailed mapping")
    func loadLatestEntryDataPropagatesMappedDashboardError() async {
        let sut = DashboardTestFixtures.makeDataManagerSUT(hasActiveAccount: false).sut

        do {
            _ = try await sut.loadLatestEntryData()
            Issue.record("Expected loadLatestEntryData to throw")
        } catch {
            #expect(error as? DashboardError == DashboardError.dataLoadingFailed(
                NSError(domain: "EntryService", code: 401, userInfo: [NSLocalizedDescriptionKey: "No active account"])
            ))
        }
    }

    // MARK: - getLatestEntrySync Tests

    @Test("getLatestEntrySync: always returns nil")
    func getLatestEntrySyncReturnsNil() {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut
        #expect(sut.getLatestEntrySync() == nil)
    }

    @Test("getLatestEntrySync: returns nil even after loading entries")
    func getLatestEntrySyncReturnsNilAfterLoad() async throws {
        let entry = EntryTestFixtures.makeEntry(weight: 1800)
        let sut = DashboardTestFixtures.makeDataManagerSUT(entries: [entry]).sut

        _ = try await sut.getLatestEntry()
        #expect(sut.getLatestEntrySync() == nil)
    }

    // MARK: - clearCache Tests

    @Test("clearCache: resets all state properties to empty/zero")
    func clearCacheResetsState() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // Populate state via Combine
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil {
            sut.state.dailySummaries.count == 5 && sut.state.monthlySummaries.count == 3
        }

        // Also set latestWeightStored
        sut.state.latestWeightStored = 1800

        try await sut.clearCache()

        #expect(sut.state.dailySummaries.isEmpty)
        #expect(sut.state.monthlySummaries.isEmpty)
        #expect(sut.state.dailyCache.isEmpty)
        #expect(sut.state.monthlyCache.isEmpty)
        #expect(sut.state.latestWeightStored == 0)
    }

    @Test("clearCache: clears sorted caches and date bounds")
    func clearCacheClearsSortedCaches() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil {
            sut.cachedDailyMinDate != nil && sut.cachedMonthlyMinDate != nil
        }

        try await sut.clearCache()

        #expect(sut.getContinuousOperations(for: .week).isEmpty)
        #expect(sut.getContinuousOperations(for: .year).isEmpty)
        #expect(sut.cachedDailyMinDate == nil)
        #expect(sut.cachedDailyMaxDate == nil)
        #expect(sut.cachedMonthlyMinDate == nil)
        #expect(sut.cachedMonthlyMaxDate == nil)
    }

    @Test("clearCache: idempotent when already empty")
    func clearCacheIdempotent() async throws {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

        try await sut.clearCache()

        #expect(sut.state.dailySummaries.isEmpty)
        #expect(sut.state.monthlySummaries.isEmpty)
        #expect(sut.cachedDailyMinDate == nil)
    }

    // MARK: - validateCacheConsistency Tests

    @Test("validateCacheConsistency: passes when state matches EntryService counts")
    func validateCacheConsistencyPasses() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // Set up matching counts via Combine pipeline
        let dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        let monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        entryService.dailySummaries = dailySummaries
        entryService.monthlySummaries = monthlySummaries

        await DashboardTestFixtures.waitUntil {
            sut.state.dailySummaries.count == 5 && sut.state.monthlySummaries.count == 3
        }

        // Should not throw
        try sut.validateCacheConsistency()
    }

    @Test("validateCacheConsistency: passes when both are empty")
    func validateCacheConsistencyBothEmpty() throws {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

        // Both entryService and state are empty by default
        try sut.validateCacheConsistency()
    }

    @Test("validateCacheConsistency: throws when daily counts mismatch")
    func validateCacheConsistencyDailyMismatch() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // Set up data via Combine
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        // Manually add an extra item to state to create mismatch
        sut.state.dailySummaries.append(DashboardTestFixtures.makeSummary(period: "2026-03-10"))

        do {
            try sut.validateCacheConsistency()
            Issue.record("Expected validateCacheConsistency to throw for daily mismatch")
        } catch {
            if case DashboardError.cacheUpdateFailed(let message) = error {
                #expect(message.contains("Daily cache inconsistency"))
            } else {
                Issue.record("Expected DashboardError.cacheUpdateFailed, got \(error)")
            }
        }
    }

    @Test("validateCacheConsistency: throws when monthly counts mismatch")
    func validateCacheConsistencyMonthlyMismatch() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // Set up matching daily data
        let dailySummaries = [DashboardTestFixtures.makeSummary()]
        entryService.dailySummaries = dailySummaries
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 1 }

        // Monthly: entryService has 0, state will have extra
        sut.state.monthlySummaries.append(DashboardTestFixtures.makeSummary(period: "2026-01"))

        do {
            try sut.validateCacheConsistency()
            Issue.record("Expected validateCacheConsistency to throw for monthly mismatch")
        } catch {
            if case DashboardError.cacheUpdateFailed(let message) = error {
                #expect(message.contains("Monthly cache inconsistency"))
            } else {
                Issue.record("Expected DashboardError.cacheUpdateFailed, got \(error)")
            }
        }
    }

    @Test("validateCacheConsistency: nil values in state summaries are not counted")
    func validateCacheConsistencyHandlesNils() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // EntryService has 1 daily summary
        let summary = DashboardTestFixtures.makeSummary()
        entryService.dailySummaries = [summary]
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 1 }

        // Add a nil to state summaries - the compactMap { $0 } should filter it out
        sut.state.dailySummaries.append(nil)

        // Count after compactMap should still be 1, matching entryService
        try sut.validateCacheConsistency()
    }

    // MARK: - getDataAnalytics Tests

    @Test("getDataAnalytics: returns correct analytics when data exists")
    func getDataAnalyticsWithData() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil {
            sut.state.dailyCache.count == 5 && sut.state.monthlyCache.count == 3
        }

        let analytics = sut.getDataAnalytics()

        #expect(analytics.dailyEntries == 5)
        #expect(analytics.monthlyEntries == 3)
        #expect(analytics.totalEntries == 8)
        #expect(analytics.cacheSize > 0)
        #expect(analytics.lastUpdated <= Date())
    }

    @Test("getDataAnalytics: returns zero entries when no data")
    func getDataAnalyticsEmpty() {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

        let analytics = sut.getDataAnalytics()

        #expect(analytics.totalEntries == 0)
        #expect(analytics.dailyEntries == 0)
        #expect(analytics.monthlyEntries == 0)
        #expect(analytics.dateRange == nil)
        #expect(analytics.dataCompleteness == 0.0)
        #expect(analytics.cacheSize == 0)
    }

    @Test("getDataAnalytics: calculates date range from cache values")
    func getDataAnalyticsDateRange() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 5 }

        let analytics = sut.getDataAnalytics()

        #expect(analytics.dateRange != nil)
        #expect(analytics.dateRange?.start != nil)
        #expect(analytics.dateRange?.end != nil)
    }

    @Test("getDataAnalytics: calculates cache size as 200 bytes per entry")
    func getDataAnalyticsCacheSize() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 5 }

        let analytics = sut.getDataAnalytics()

        // 5 daily * 200 + 0 monthly * 200 = 1000
        #expect(analytics.cacheSize == 1000)
    }

    @Test("getDataAnalytics: data completeness is ratio of actual to expected days")
    func getDataAnalyticsCompleteness() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // 5 entries over a 4-day span (Mar 1-5)
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 5 }

        let analytics = sut.getDataAnalytics()

        #expect(analytics.dataCompleteness > 0.0)
    }

    @Test("getDataAnalytics: data completeness is zero when only one day of data")
    func getDataAnalyticsCompletenessOneDay() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        entryService.dailySummaries = [DashboardTestFixtures.makeSummary()]
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 1 }

        let analytics = sut.getDataAnalytics()

        // With one data point, totalDays == 0, so completeness is 0
        #expect(analytics.dataCompleteness == 0.0)
    }

    // MARK: - State Consistency After Multiple Updates

    @Test("multiple rapid updates: final state reflects last update")
    func multipleRapidUpdates() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // Rapidly set summaries multiple times
        entryService.dailySummaries = [DashboardTestFixtures.makeSummary(weight: 1800)]
        entryService.dailySummaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1850)
        ]
        entryService.dailySummaries = [
            DashboardTestFixtures.makeSummary(period: "2026-04-01", weight: 1900)
        ]

        await DashboardTestFixtures.waitUntil { sut.state.dailyCache["2026-04-01"] != nil }

        #expect(sut.state.dailySummaries.count == 1)
        #expect(sut.state.dailyCache["2026-04-01"]?.weight == 1900)
    }

    @Test("daily and monthly updates: independent and do not interfere")
    func dailyAndMonthlyIndependent() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()

        await DashboardTestFixtures.waitUntil {
            sut.state.dailySummaries.count == 5 && sut.state.monthlySummaries.count == 3
        }

        // Daily and monthly should be independent
        #expect(sut.getContinuousOperations(for: .week).count == 5)
        #expect(sut.getContinuousOperations(for: .year).count == 3)
        #expect(sut.getDateBounds(for: .week) != nil)
        #expect(sut.getDateBounds(for: .year) != nil)
    }

    // MARK: - Edge Cases

    @Test("large dataset: handles many summaries without issue")
    func largeDataset() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        // Create 365 daily summaries
        var summaries: [BathScaleWeightSummary] = []
        let calendar = Calendar.current
        let startDate = DateTimeTools.getDateFromDateString("2025-01-01", format: "yyyy-MM-dd")
        for dayOffset in 0..<365 {
            let date = try #require(calendar.date(byAdding: .day, value: dayOffset, to: startDate))
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd"
            let period = formatter.string(from: date)
            summaries.append(DashboardTestFixtures.makeSummary(
                period: period,
                entryTimestamp: "\(period)T08:00:00Z",
                date: date,
                weight: Double(1800 + dayOffset)
            ))
        }

        entryService.dailySummaries = summaries
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 365 }

        #expect(sut.getContinuousOperations(for: .week).count == 365)
        #expect(sut.cachedDailyMinDate != nil)
        #expect(sut.cachedDailyMaxDate != nil)
    }

    @Test("summaries with different periods: each period gets its own cache entry")
    func differentPeriods() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1850),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", weight: 1900)
        ]
        entryService.dailySummaries = summaries
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 3 }

        #expect(sut.state.dailyCache.count == 3)
        #expect(sut.state.dailyCache["2026-03-01"]?.weight == 1800)
        #expect(sut.state.dailyCache["2026-03-02"]?.weight == 1850)
        #expect(sut.state.dailyCache["2026-03-03"]?.weight == 1900)
    }
}
