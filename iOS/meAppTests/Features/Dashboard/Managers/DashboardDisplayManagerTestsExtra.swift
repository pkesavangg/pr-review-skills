import Combine
import Foundation
@testable import meApp
import Testing

@MainActor
extension DashboardDisplayManagerTests {

    @Test("createEntryForMetricInfo: selected point builds an entry mirroring that point")
    func createEntryForMetricInfoUsesSelectedPoint() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        let summary = DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 250)
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: [summary])

        store.graphManager.state.selectedPoint = summary
        store.graphManager.state.selectedPeriod = .week
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let entry = store.displayManager.createEntryForMetricInfo()

        #expect(entry.scaleEntry?.weight == 1810)
        #expect(entry.scaleEntry?.bodyFat == 250)
        #expect(entry.entryTimestamp == DateTimeTools.isoFormatter().string(from: summary.date))
    }

    @Test("updateMetricsForCurrentView: crosshair selection sets placeholders and marks metrics loaded")
    func updateMetricsForCurrentViewWithCrosshairSetsPlaceholders() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.state.ui.hasLoadedDashboardConfig = true
        store.graphManager.state.selectedXValue = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateMetricsForCurrentView()

        #expect(store.state.ui.hasLoadedMetricValues == true)
        #expect(store.metricsManager.state.metrics.first { $0.label == DashboardStrings.bodyFat }?.value == DashboardStrings.placeholder)
    }

    @Test("updateMetricsWithVisibleRegionAverage: loads visible metric averages and marks values loaded")
    func updateMetricsWithVisibleRegionAverage() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        let daily = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1800, bodyFat: 200),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 300)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateMetricsWithVisibleRegionAverage()

        await DashboardTestFixtures.waitUntil {
            store.state.ui.hasLoadedMetricValues
        }

        #expect(store.state.ui.hasLoadedMetricValues == true)
        #expect(store.metricsManager.state.metrics.first { $0.label == DashboardStrings.bodyFat }?.value != DashboardStrings.placeholder)
    }

    @Test("weightLabel: empty state uses the date-range manager label for each period")
    func weightLabelEmptyStateUsesPeriodFallback() {
        let (store, _, _) = makeSUT()
        let today = Date()

        for period in [TimePeriod.week, .month, .year, .total] {
            store.graphManager.state.selectedPeriod = period
            DashboardManagerTestSupport.syncStoreGraphState(store)

            #expect(
                store.displayManager.weightLabel ==
                store.dateRangeManager.emptyStatePeriodLabel(for: period, today: today)
            )
        }
    }

    @Test("weightLabel: selected entry without an embedded date falls back to the matching continuous-operation date")
    func weightLabelSelectedEntryFallsBackToContinuousOperationDate() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let summary = DashboardTestFixtures.makeSummary(period: "2026-03-05", entryTimestamp: "2026-03-05T08:00:00Z", weight: 1840)
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: [summary])

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedEntry = BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: summary.entryTimestamp,
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: nil,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: summary.weight
        )
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.weightLabel == store.graphManager.formatSelectedDate(summary.date, for: .week))
    }

    @Test("weightDisplayLabel: selected year and total crosshair use month-average labels")
    func weightDisplayLabelMonthAverageForLongPeriods() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries(),
            monthly: DashboardTestFixtures.makeSortedMonthlySummaries()
        )

        for period in [TimePeriod.year, .total] {
            store.graphManager.state.selectedPeriod = period
            store.graphManager.state.selectedXValue = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
            DashboardManagerTestSupport.syncStoreGraphState(store)

            #expect(store.displayManager.weightDisplayLabel == "month average")
        }
    }

    @Test("weightDisplayLabel: without selection it uses the goal-manager label for the current period")
    func weightDisplayLabelUsesGoalManagerLabelWithoutSelection() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        store.graphManager.state.selectedXValue = nil
        store.graphManager.state.selectedPoint = nil
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.weightDisplayLabel == store.goalManager.getWeightDisplayLabel(for: .month))
    }

    @Test("updateVisibleDataAfterScroll: schedules a UI update for visible data recalculation")
    func updateVisibleDataAfterScrollSchedulesStoreUpdate() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        var cancellable: AnyCancellable?
        var updateCount = 0
        cancellable = store.objectWillChange.sink { updateCount += 1 }

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateVisibleDataAfterScroll()

        await DashboardTestFixtures.waitUntil { updateCount > 0 }
        cancellable?.cancel()

        #expect(updateCount > 0)
    }

    @Test("metricInfoDateLabel: nil parsed dates use the non-history fallback path")
    func metricInfoDateLabelNilParsedDateUsesFallbackArguments() {
        let formatter = MockDashboardFormatter()
        formatter.metricInfoDateLabelResult = "fallback"
        formatter.parsedEntryDate = nil
        let sut = makeSUT(formatter: formatter)
        let store = sut.store

        store.graphManager.state.selectedPeriod = .month
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let dto = BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "2026-03-03T08:00:00Z",
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: nil,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: 1800
        )

        #expect(store.displayManager.metricInfoDateLabel(for: dto) == "fallback")
        #expect(formatter.lastMetricInfoDateLabelArgs?.entryDate == nil)
        #expect(formatter.lastMetricInfoDateLabelArgs?.isFromHistory == false)
        #expect(formatter.lastMetricInfoDateLabelArgs?.period == .month)
    }

    @Test("createEntryForMetricInfoAsync: returns a dashboard entry without crashing")
    func createEntryForMetricInfoAsyncBuildsEntry() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let summary = DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-04", weight: 1830, bodyFat: 255)
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: [summary])

        store.graphManager.state.selectedPoint = summary
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let asyncEntry = await store.displayManager.createEntryForMetricInfoAsync(metricLabel: DashboardStrings.bodyFat)

        #expect(asyncEntry.accountId == "dashboard")
        #expect(asyncEntry.entryTimestamp.isEmpty == false)
        #expect(asyncEntry.scaleEntry != nil)
    }
}
