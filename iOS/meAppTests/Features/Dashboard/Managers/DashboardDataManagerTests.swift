import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardDataManagerTests {

    // MARK: - Initialization Tests

    @Test("init: creates manager with default empty state")
    func initDefaultState() {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        #expect(sut.state.dailySummaries.isEmpty)
        #expect(sut.state.monthlySummaries.isEmpty)
        #expect(sut.state.dailyCache.isEmpty)
        #expect(sut.state.monthlyCache.isEmpty)
        #expect(sut.state.latestWeightStored == 0)
        #expect(sut.cachedDailyMinDate == nil)
        #expect(sut.cachedDailyMaxDate == nil)
        #expect(sut.cachedMonthlyMinDate == nil)
        #expect(sut.cachedMonthlyMaxDate == nil)
    }

    @Test("init: creates manager with custom initial state")
    func initCustomState() {
        TestDependencyContainer.reset()
        _ = TestDependencyContainer.registerDashboardConcreteDependencies()

        var customState = DataState()
        customState.latestWeightStored = 1800
        let sut = DashboardDataManager(initialState: customState)

        #expect(sut.state.latestWeightStored == 1800)
    }

    // MARK: - Data Loading Tests

    @Test("loadInitialData: completes without error")
    func loadInitialDataSuccess() async throws {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()
        try await sut.loadInitialData()
        // Should complete without throwing
    }

    @Test("initializeDataManager: delegates to loadInitialData without error")
    func initializeDataManagerDelegates() async throws {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()
        try await sut.initializeDataManager()
        // Should complete without throwing
    }

    // MARK: - Data Synchronization Tests (Combine Pipeline)

    @Test("daily summaries binding: updates state, cache, and date bounds when EntryService publishes")
    func dailySummariesBindingUpdatesState() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let summaries = DashboardTestFixtures.makeSortedDailySummaries()
        entryService.dailySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        #expect(sut.state.dailySummaries.count == 5)
        #expect(sut.state.dailyCache.count == 5)
        #expect(sut.state.dailyCache["2026-03-01"] != nil)
        #expect(sut.state.dailyCache["2026-03-05"] != nil)
        #expect(sut.cachedDailyMinDate != nil)
        #expect(sut.cachedDailyMaxDate != nil)
    }

    @Test("daily summaries binding: sorts unsorted summaries correctly")
    func dailySummariesSortsUnsorted() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let unsorted = DashboardTestFixtures.makeUnsortedDailySummaries()
        entryService.dailySummaries = unsorted

        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        let ops = sut.getContinuousOperations(for: .week)
        #expect(ops.count == 5)
        // Verify sorted order by checking weights match expected sorted sequence
        #expect(ops[0].weight == 1800)  // 2026-03-01
        #expect(ops[1].weight == 1810)  // 2026-03-02
        #expect(ops[2].weight == 1820)  // 2026-03-03
        #expect(ops[3].weight == 1830)  // 2026-03-04
        #expect(ops[4].weight == 1840)  // 2026-03-05
    }

    @Test("daily summaries binding: caches correct min/max date bounds")
    func dailySummariesDateBounds() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let summaries = DashboardTestFixtures.makeUnsortedDailySummaries()
        entryService.dailySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.cachedDailyMinDate != nil }

        let minDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let maxDate = DateTimeTools.getDateFromDateString("2026-03-05", format: "yyyy-MM-dd")
        #expect(sut.cachedDailyMinDate == minDate)
        #expect(sut.cachedDailyMaxDate == maxDate)
    }

    @Test("monthly summaries binding: updates state, cache, and date bounds when EntryService publishes")
    func monthlySummariesBindingUpdatesState() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let summaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        entryService.monthlySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        #expect(sut.state.monthlySummaries.count == 3)
        #expect(sut.state.monthlyCache.count == 3)
        #expect(sut.state.monthlyCache["2026-01"] != nil)
        #expect(sut.state.monthlyCache["2026-03"] != nil)
        #expect(sut.cachedMonthlyMinDate != nil)
        #expect(sut.cachedMonthlyMaxDate != nil)
    }

    @Test("monthly summaries binding: sorts unsorted summaries correctly")
    func monthlySummariesSortsUnsorted() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let unsorted = DashboardTestFixtures.makeUnsortedMonthlySummaries()
        entryService.monthlySummaries = unsorted

        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        let ops = sut.getContinuousOperations(for: .year)
        #expect(ops.count == 3)
        #expect(ops[0].weight == 1800)  // 2026-01
        #expect(ops[1].weight == 1810)  // 2026-02
        #expect(ops[2].weight == 1820)  // 2026-03
    }

    @Test("monthly summaries binding: caches correct min/max date bounds")
    func monthlySummariesDateBounds() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let summaries = DashboardTestFixtures.makeUnsortedMonthlySummaries()
        entryService.monthlySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.cachedMonthlyMinDate != nil }

        let minDate = DateTimeTools.getDateFromDateString("2026-01-01", format: "yyyy-MM-dd")
        let maxDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        #expect(sut.cachedMonthlyMinDate == minDate)
        #expect(sut.cachedMonthlyMaxDate == maxDate)
    }

    @Test("daily summaries binding: empty array clears caches and bounds")
    func dailySummariesEmptyArrayClearsCaches() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        // First populate
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        // Then clear
        entryService.dailySummaries = []
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.isEmpty }

        #expect(sut.state.dailySummaries.isEmpty)
        #expect(sut.state.dailyCache.isEmpty)
        #expect(sut.cachedDailyMinDate == nil)
        #expect(sut.cachedDailyMaxDate == nil)
        #expect(sut.getContinuousOperations(for: .week).isEmpty)
    }

    @Test("monthly summaries binding: empty array clears caches and bounds")
    func monthlySummariesEmptyArrayClearsCaches() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        // First populate
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        // Then clear
        entryService.monthlySummaries = []
        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.isEmpty }

        #expect(sut.state.monthlySummaries.isEmpty)
        #expect(sut.state.monthlyCache.isEmpty)
        #expect(sut.cachedMonthlyMinDate == nil)
        #expect(sut.cachedMonthlyMaxDate == nil)
        #expect(sut.getContinuousOperations(for: .year).isEmpty)
    }

    @Test("daily summaries binding: single summary sets min and max to same date")
    func dailySummariesSingleItem() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let single = [DashboardTestFixtures.makeSummary(period: "2026-03-15", weight: 1800)]
        entryService.dailySummaries = single

        await DashboardTestFixtures.waitUntil { sut.cachedDailyMinDate != nil }

        #expect(sut.cachedDailyMinDate == sut.cachedDailyMaxDate)
        #expect(sut.getContinuousOperations(for: .week).count == 1)
    }

    @Test("daily summaries binding: builds dailyCache dictionary keyed by period")
    func dailySummariesBuildsCacheDictionary() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1810)
        ]
        entryService.dailySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 2 }

        #expect(sut.state.dailyCache["2026-03-01"]?.weight == 1800)
        #expect(sut.state.dailyCache["2026-03-02"]?.weight == 1810)
    }

    @Test("daily summaries binding: state publication sees refreshed continuous cache")
    func dailySummariesPublicationSeesUpdatedContinuousCache() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        let first = DashboardTestFixtures.makeSortedDailySummaries()
        let second = [
            DashboardTestFixtures.makeSummary(period: "2026-04-01", weight: 1900),
            DashboardTestFixtures.makeSummary(period: "2026-04-02", weight: 1910)
        ]
        let expectedSnapshots = [first.map(\.period), second.map(\.period)]
        var observedSnapshots: [[String]] = []
        var cancellable: AnyCancellable?

        cancellable = sut.$state
            .dropFirst()
            .sink { _ in
                observedSnapshots.append(sut.getContinuousOperations(for: .week).map(\.period))
            }

        entryService.dailySummaries = first
        await DashboardTestFixtures.waitUntil { observedSnapshots.contains(expectedSnapshots[0]) }

        entryService.dailySummaries = second
        await DashboardTestFixtures.waitUntil { observedSnapshots.contains(expectedSnapshots[1]) }

        #expect(observedSnapshots.last == expectedSnapshots[1])
        _ = cancellable
    }

    @Test("monthly summaries binding: builds monthlyCache dictionary keyed by period")
    func monthlySummariesBuildsCacheDictionary() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-01", entryTimestamp: "2026-01-01T00:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-02", entryTimestamp: "2026-02-01T00:00:00Z", weight: 1810)
        ]
        entryService.monthlySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.state.monthlyCache.count == 2 }

        #expect(sut.state.monthlyCache["2026-01"]?.weight == 1800)
        #expect(sut.state.monthlyCache["2026-02"]?.weight == 1810)
    }

    @Test("monthly summaries binding: state publication sees refreshed continuous cache")
    func monthlySummariesPublicationSeesUpdatedContinuousCache() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        let first = DashboardTestFixtures.makeSortedMonthlySummaries()
        let second = [
            DashboardTestFixtures.makeSummary(period: "2026-04", entryTimestamp: "2026-04-01T00:00:00Z", weight: 1900),
            DashboardTestFixtures.makeSummary(period: "2026-05", entryTimestamp: "2026-05-01T00:00:00Z", weight: 1910)
        ]
        let expectedSnapshots = [first.map(\.period), second.map(\.period)]
        var observedSnapshots: [[String]] = []
        var cancellable: AnyCancellable?

        cancellable = sut.$state
            .dropFirst()
            .sink { _ in
                observedSnapshots.append(sut.getContinuousOperations(for: .year).map(\.period))
            }

        entryService.monthlySummaries = first
        await DashboardTestFixtures.waitUntil { observedSnapshots.contains(expectedSnapshots[0]) }

        entryService.monthlySummaries = second
        await DashboardTestFixtures.waitUntil { observedSnapshots.contains(expectedSnapshots[1]) }

        #expect(observedSnapshots.last == expectedSnapshots[1])
        _ = cancellable
    }

    // MARK: - getContinuousOperations Tests

    @Test("getContinuousOperations: week period returns daily summaries")
    func getContinuousOperationsWeek() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        let ops = sut.getContinuousOperations(for: .week)
        #expect(ops.count == 5)
    }

    @Test("getContinuousOperations: month period returns daily summaries")
    func getContinuousOperationsMonth() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        let ops = sut.getContinuousOperations(for: .month)
        #expect(ops.count == 5)
    }

    @Test("getContinuousOperations: year period returns monthly summaries")
    func getContinuousOperationsYear() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        let ops = sut.getContinuousOperations(for: .year)
        #expect(ops.count == 3)
    }

    @Test("getContinuousOperations: total period returns monthly summaries")
    func getContinuousOperationsTotal() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        let ops = sut.getContinuousOperations(for: .total)
        #expect(ops.count == 3)
    }

    @Test("getContinuousOperations: returns empty when no data is loaded")
    func getContinuousOperationsEmpty() {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        #expect(sut.getContinuousOperations(for: .week).isEmpty)
        #expect(sut.getContinuousOperations(for: .month).isEmpty)
        #expect(sut.getContinuousOperations(for: .year).isEmpty)
        #expect(sut.getContinuousOperations(for: .total).isEmpty)
    }

    // MARK: - getDateBounds Tests

    @Test("getDateBounds: week period returns daily min/max dates")
    func getDateBoundsWeek() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedDailyMinDate != nil }

        let bounds = sut.getDateBounds(for: .week)
        #expect(bounds != nil)
        let minDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let maxDate = DateTimeTools.getDateFromDateString("2026-03-05", format: "yyyy-MM-dd")
        #expect(bounds?.min == minDate)
        #expect(bounds?.max == maxDate)
    }

    @Test("getDateBounds: month period returns daily min/max dates")
    func getDateBoundsMonth() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedDailyMinDate != nil }

        let bounds = sut.getDateBounds(for: .month)
        #expect(bounds != nil)
    }

    @Test("getDateBounds: year period returns monthly min/max dates")
    func getDateBoundsYear() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedMonthlyMinDate != nil }

        let bounds = sut.getDateBounds(for: .year)
        #expect(bounds != nil)
    }

    @Test("getDateBounds: total period returns monthly min/max dates")
    func getDateBoundsTotal() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedMonthlyMinDate != nil }

        let bounds = sut.getDateBounds(for: .total)
        #expect(bounds != nil)
    }

    @Test("getDateBounds: returns nil when no data is loaded")
    func getDateBoundsNil() {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        #expect(sut.getDateBounds(for: .week) == nil)
        #expect(sut.getDateBounds(for: .month) == nil)
        #expect(sut.getDateBounds(for: .year) == nil)
        #expect(sut.getDateBounds(for: .total) == nil)
    }

    // MARK: - getLatestEntry Tests

    @Test("getLatestEntry: returns entry and updates latestWeightStored when weight exists")
    func getLatestEntryWithWeight() async throws {
        let entry = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-01T08:00:00Z",
            weight: 1800
        )
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(entries: [entry])

        let result = try await sut.getLatestEntry()

        #expect(result != nil)
        #expect(result?.scaleEntry?.weight == 1800)
        #expect(sut.state.latestWeightStored == 1800)
    }

    @Test("getLatestEntry: returns entry without updating latestWeightStored when no weight")
    func getLatestEntryWithoutWeight() async throws {
        let entry = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-01T08:00:00Z",
            weight: nil
        )
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(entries: [entry])

        let result = try await sut.getLatestEntry()

        #expect(result != nil)
        #expect(sut.state.latestWeightStored == 0)
    }

    @Test("getLatestEntry: returns nil when no entries exist")
    func getLatestEntryNil() async throws {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        let result = try await sut.getLatestEntry()

        #expect(result == nil)
    }

    @Test("getLatestEntry: throws DashboardError.dataLoadingFailed when entry service throws")
    func getLatestEntryError() async {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(hasActiveAccount: false)

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(entries: entries)

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(entries: [entry])

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(entries: [entry])

        let result = try await sut.loadLatestEntryData()

        #expect(result.entry != nil)
        #expect(result.weight == nil)
    }

    @Test("loadLatestEntryData: returns nil entry and nil weight when no entries")
    func loadLatestEntryDataNil() async throws {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        let result = try await sut.loadLatestEntryData()

        #expect(result.entry == nil)
        #expect(result.weight == nil)
    }

    @Test("loadLatestEntryData: rethrows when entry service fails")
    func loadLatestEntryDataError() async {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(hasActiveAccount: false)

        do {
            _ = try await sut.loadLatestEntryData()
            Issue.record("Expected loadLatestEntryData to throw")
        } catch {
            // Expected: error is rethrown
        }
    }

    @Test("loadLatestEntryData: propagates DashboardError.dataLoadingFailed mapping")
    func loadLatestEntryDataPropagatesMappedDashboardError() async {
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(hasActiveAccount: false)

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()
        #expect(sut.getLatestEntrySync() == nil)
    }

    @Test("getLatestEntrySync: returns nil even after loading entries")
    func getLatestEntrySyncReturnsNilAfterLoad() async throws {
        let entry = EntryTestFixtures.makeEntry(weight: 1800)
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT(entries: [entry])

        _ = try await sut.getLatestEntry()
        #expect(sut.getLatestEntrySync() == nil)
    }

    // MARK: - clearCache Tests

    @Test("clearCache: resets all state properties to empty/zero")
    func clearCacheResetsState() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        try await sut.clearCache()

        #expect(sut.state.dailySummaries.isEmpty)
        #expect(sut.state.monthlySummaries.isEmpty)
        #expect(sut.cachedDailyMinDate == nil)
    }

    // MARK: - validateCacheConsistency Tests

    @Test("validateCacheConsistency: passes when state matches EntryService counts")
    func validateCacheConsistencyPasses() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

        // Both entryService and state are empty by default
        try sut.validateCacheConsistency()
    }

    @Test("validateCacheConsistency: throws when daily counts mismatch")
    func validateCacheConsistencyDailyMismatch() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, _, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 5 }

        let analytics = sut.getDataAnalytics()

        #expect(analytics.dateRange != nil)
        #expect(analytics.dateRange?.start != nil)
        #expect(analytics.dateRange?.end != nil)
    }

    @Test("getDataAnalytics: calculates cache size as 200 bytes per entry")
    func getDataAnalyticsCacheSize() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 5 }

        let analytics = sut.getDataAnalytics()

        // 5 daily * 200 + 0 monthly * 200 = 1000
        #expect(analytics.cacheSize == 1000)
    }

    @Test("getDataAnalytics: data completeness is ratio of actual to expected days")
    func getDataAnalyticsCompleteness() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        // 5 entries over a 4-day span (Mar 1-5)
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 5 }

        let analytics = sut.getDataAnalytics()

        #expect(analytics.dataCompleteness > 0.0)
    }

    @Test("getDataAnalytics: data completeness is zero when only one day of data")
    func getDataAnalyticsCompletenessOneDay() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        entryService.dailySummaries = [DashboardTestFixtures.makeSummary()]
        await DashboardTestFixtures.waitUntil { sut.state.dailyCache.count == 1 }

        let analytics = sut.getDataAnalytics()

        // With one data point, totalDays == 0, so completeness is 0
        #expect(analytics.dataCompleteness == 0.0)
    }

    // MARK: - State Consistency After Multiple Updates

    @Test("multiple rapid updates: final state reflects last update")
    func multipleRapidUpdates() async throws {
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

        // Create 365 daily summaries
        var summaries: [BathScaleWeightSummary] = []
        let calendar = Calendar.current
        let startDate = DateTimeTools.getDateFromDateString("2025-01-01", format: "yyyy-MM-dd")
        for dayOffset in 0..<365 {
            let date = calendar.date(byAdding: .day, value: dayOffset, to: startDate)!
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
        let (sut, entryService, _) = DashboardTestFixtures.makeDataManagerSUT()

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
