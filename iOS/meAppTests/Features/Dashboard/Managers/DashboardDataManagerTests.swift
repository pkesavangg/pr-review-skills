import Combine
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardDataManagerTests {

    // MARK: - Initialization Tests

    @Test("init: creates manager with default empty state")
    func initDefaultState() {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

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
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut
        try await sut.loadInitialData()
        // Should complete without throwing
    }

    @Test("initializeDataManager: delegates to loadInitialData without error")
    func initializeDataManagerDelegates() async throws {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut
        try await sut.initializeDataManager()
        // Should complete without throwing
    }

    // MARK: - Data Synchronization Tests (Combine Pipeline)

    @Test("daily summaries binding: updates state, cache, and date bounds when EntryService publishes")
    func dailySummariesBindingUpdatesState() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

        let single = [DashboardTestFixtures.makeSummary(period: "2026-03-15", weight: 1800)]
        entryService.dailySummaries = single

        await DashboardTestFixtures.waitUntil { sut.cachedDailyMinDate != nil }

        #expect(sut.cachedDailyMinDate == sut.cachedDailyMaxDate)
        #expect(sut.getContinuousOperations(for: .week).count == 1)
    }

    @Test("daily summaries binding: builds dailyCache dictionary keyed by period")
    func dailySummariesBuildsCacheDictionary() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService

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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        let ops = sut.getContinuousOperations(for: .week)
        #expect(ops.count == 5)
    }

    @Test("getContinuousOperations: month period returns daily summaries")
    func getContinuousOperationsMonth() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.dailySummaries.count == 5 }

        let ops = sut.getContinuousOperations(for: .month)
        #expect(ops.count == 5)
    }

    @Test("getContinuousOperations: year period returns monthly summaries")
    func getContinuousOperationsYear() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        let ops = sut.getContinuousOperations(for: .year)
        #expect(ops.count == 3)
    }

    @Test("getContinuousOperations: total period returns monthly summaries")
    func getContinuousOperationsTotal() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.state.monthlySummaries.count == 3 }

        let ops = sut.getContinuousOperations(for: .total)
        #expect(ops.count == 3)
    }

    @Test("getContinuousOperations: returns empty when no data is loaded")
    func getContinuousOperationsEmpty() {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

        #expect(sut.getContinuousOperations(for: .week).isEmpty)
        #expect(sut.getContinuousOperations(for: .month).isEmpty)
        #expect(sut.getContinuousOperations(for: .year).isEmpty)
        #expect(sut.getContinuousOperations(for: .total).isEmpty)
    }

    // MARK: - getDateBounds Tests

    @Test("getDateBounds: week period returns daily min/max dates")
    func getDateBoundsWeek() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
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
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedDailyMinDate != nil }

        let bounds = sut.getDateBounds(for: .month)
        #expect(bounds != nil)
    }

    @Test("getDateBounds: year period returns monthly min/max dates")
    func getDateBoundsYear() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedMonthlyMinDate != nil }

        let bounds = sut.getDateBounds(for: .year)
        #expect(bounds != nil)
    }

    @Test("getDateBounds: total period returns monthly min/max dates")
    func getDateBoundsTotal() async throws {
        let sutBundle = DashboardTestFixtures.makeDataManagerSUT()
        let sut = sutBundle.sut
        let entryService = sutBundle.entryService
        entryService.monthlySummaries = DashboardTestFixtures.makeSortedMonthlySummaries()
        await DashboardTestFixtures.waitUntil { sut.cachedMonthlyMinDate != nil }

        let bounds = sut.getDateBounds(for: .total)
        #expect(bounds != nil)
    }

    @Test("getDateBounds: returns nil when no data is loaded")
    func getDateBoundsNil() {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

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
        let sut = DashboardTestFixtures.makeDataManagerSUT(entries: [entry]).sut

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
        let sut = DashboardTestFixtures.makeDataManagerSUT(entries: [entry]).sut

        let result = try await sut.getLatestEntry()

        #expect(result != nil)
        #expect(sut.state.latestWeightStored == 0)
    }

    @Test("getLatestEntry: returns nil when no entries exist")
    func getLatestEntryNil() async throws {
        let sut = DashboardTestFixtures.makeDataManagerSUT().sut

        let result = try await sut.getLatestEntry()

        #expect(result == nil)
    }

}
