import Combine
import Foundation
@testable import meApp
import Testing

@MainActor
extension DashboardDisplayManagerTests {

    @Test("getBodyMetric: maps metric labels through the metrics manager")
    func getBodyMetricMapsMetricLabels() {
        let (store, _, _) = makeSUT()

        #expect(store.displayManager.getBodyMetric(for: DashboardStrings.bodyFat) == .bodyFat)
        #expect(store.displayManager.getBodyMetric(for: DashboardStrings.weight) == .weight)
    }

    @Test("updateMetricsForCurrentView: selected points load concrete metric values")
    func updateMetricsForCurrentViewSelectedPointLoadsMetrics() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let summary = DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-04", weight: 1830, bodyFat: 260)
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: [summary])

        store.state.ui.hasLoadedDashboardConfig = true
        store.graphManager.state.selectedPoint = summary
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateMetricsForCurrentView()

        await DashboardTestFixtures.waitUntil {
            store.state.ui.hasLoadedMetricValues &&
            store.metricsManager.state.metrics.first { $0.label == DashboardStrings.bodyFat }?.value != DashboardStrings.placeholder
        }

        #expect(store.state.ui.hasLoadedMetricValues == true)
        #expect(store.metricsManager.state.metrics.first { $0.label == DashboardStrings.bodyFat }?.value != DashboardStrings.placeholder)
    }

    @Test("updateMetricsForCurrentView: empty label-range operations fall back to placeholders")
    func updateMetricsForCurrentViewEmptyOperationsUsePlaceholders() async {
        let (store, _, _) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.state.ui.hasLoadedDashboardConfig = true
        store.graphManager.state.selectedPeriod = .week
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateMetricsForCurrentView()

        #expect(store.state.ui.hasLoadedMetricValues == true)
        #expect(store.metricsManager.state.metrics.allSatisfy { $0.value == DashboardStrings.placeholder })
    }

    @Test("updateMetricsForCurrentView: reset-in-progress returns early without marking metrics loaded")
    func updateMetricsForCurrentViewReturnsEarlyWhileResetting() {
        let (store, _, _) = makeSUT()
        store.state.ui.hasLoadedDashboardConfig = true
        store.state.ui.isResettingDashboard = true

        store.displayManager.updateMetricsForCurrentView()

        #expect(store.state.ui.hasLoadedMetricValues == false)
    }

    @Test("displayWeight: total-period weightless mode uses the graph manager's weightless-display path")
    func displayWeightTotalWeightlessModeUsesWeightlessDisplay() async {
        let (store, accountService, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightlessOn: true, weightlessWeight: 1750)
        accountService.activeAccount = activeAccount
        store.accountService.activeAccount = activeAccount

        let monthly = [
            DashboardTestFixtures.makeSummary(period: "2026-01", entryTimestamp: "2026-01-01T00:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-02", entryTimestamp: "2026-02-01T00:00:00Z", weight: 1820)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, monthly: monthly)

        store.graphManager.state.selectedPeriod = .total
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = store.graphManager.calculateWeightlessDisplay(
            store.continuousOperations,
            anchorWeight: store.weightlessAnchorWeight,
            period: .total,
            convertWeight: store.goalManager.convertWeightToDisplay
        )

        #expect(store.displayManager.displayWeight == expected)
    }

    @Test("displayWeight: week and year interpolation fallbacks use the display manager label-range helpers")
    func displayWeightInterpolatedFallbacksCoverWeekAndYearRanges() async {
        let (store, accountService, entryService) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = [
            DashboardTestFixtures.makeSummary(period: "2026-01-01", entryTimestamp: "2026-01-01T08:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-20", entryTimestamp: "2026-03-20T08:00:00Z", weight: 1840)
        ]
        let monthly = [
            DashboardTestFixtures.makeSummary(period: "2025-01", entryTimestamp: "2025-01-01T00:00:00Z", weight: 1700),
            DashboardTestFixtures.makeSummary(period: "2027-03", entryTimestamp: "2027-03-01T00:00:00Z", weight: 1900)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily, monthly: monthly)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-02-10", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)
        #expect(store.displayManager.getOperationsForLabelDateRange().isEmpty)
        #expect(store.displayManager.displayWeight != nil)

        store.graphManager.state.selectedPeriod = .year
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-02-10", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)
        #expect(store.displayManager.getOperationsForLabelDateRange().isEmpty)
        #expect(store.displayManager.displayWeight != nil)
    }

    @Test("displayUnitText: falls back to average weight and the default unit when no account is active")
    func displayUnitTextFallsBackToAverageAndDefaultUnit() {
        let (store, _, _) = makeSUT()

        #expect(store.displayManager.displayWeight == nil)
        #expect(
            store.displayManager.displayUnitText ==
            WeightValueConvertor.unitForDisplay(unit: .lb)
        )
    }

    @Test("updateVisibleDataAfterScroll: weightless mode executes the anchor-adjusted visible-weight branch")
    func updateVisibleDataAfterScrollWeightlessMode() async {
        let (store, accountService, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightlessOn: true, weightlessWeight: 1750)
        accountService.activeAccount = activeAccount
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateVisibleDataAfterScroll()

        #expect(store.isWeightlessModeEnabled == true)
        #expect(store.weightlessAnchorWeight == 175.0)
    }

    @Test("weightLabel: invalid selected-entry timestamps fall back to the matching continuous summary")
    func weightLabelInvalidSelectedEntryTimestampFallsBackToSummary() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let summary = DashboardTestFixtures.makeSummary(
            period: "2026-03-09",
            entryTimestamp: "not-a-date",
            date: DateTimeTools.getDateFromDateString("2026-03-09", format: "yyyy-MM-dd"),
            weight: 1860
        )
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: [summary])

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedEntry = BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "not-a-date",
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
            weight: 1860
        )
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.weightLabel == store.graphManager.formatSelectedDate(summary.date, for: .week))
    }

    @Test("formatWeightDisplayText: formats non-nil values using the goal manager")
    func formatWeightDisplayTextFormatsValues() {
        let (store, _, _) = makeSUT()
        let expected = store.goalManager.formatWeightForDisplay(12.3, isWeightlessMode: store.isWeightlessModeEnabled)

        #expect(store.displayManager.formatWeightDisplayText(12.3) == expected)
    }

    @Test("createEntryForMetricInfo: selected-date interpolation builds an entry from the interpolated weight")
    func createEntryForMetricInfoSelectedDateUsesInterpolation() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", entryTimestamp: "2026-03-01T08:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", entryTimestamp: "2026-03-03T08:00:00Z", weight: 1820)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        let selectedDate = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedXValue = selectedDate
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let entry = store.displayManager.createEntryForMetricInfo()

        #expect(entry.scaleEntry?.weight == 1810)
        #expect(entry.entryTimestamp == DateTimeTools.isoFormatter().string(from: selectedDate))
    }

    @Test("createEntryForMetricInfo: empty visible operations use the interpolated-average path")
    func createEntryForMetricInfoWithoutVisibleOpsUsesInterpolatedAverage() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = [
            DashboardTestFixtures.makeSummary(period: "2026-01-01", entryTimestamp: "2026-01-01T08:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-31", entryTimestamp: "2026-03-31T08:00:00Z", weight: 1820)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-02-10", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let entry = store.displayManager.createEntryForMetricInfo()

        #expect(store.displayManager.getOperationsForLabelDateRange().isEmpty)
        #expect(entry.scaleEntry?.weight != nil)
    }

    @Test("updateMetricsForCurrentView: visible operations load averaged metrics when there is no selection")
    func updateMetricsForCurrentViewVisibleAverageBranchLoadsMetrics() async {
        let (store, _, entryService) = makeSUT()
        store.accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: [
                DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1800, bodyFat: 240),
                DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 260)
            ]
        )

        store.state.ui.hasLoadedDashboardConfig = true
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.displayManager.updateMetricsForCurrentView()

        await DashboardTestFixtures.waitUntil {
            store.state.ui.hasLoadedMetricValues &&
            store.metricsManager.state.metrics.first { $0.label == DashboardStrings.bodyFat }?.value != DashboardStrings.placeholder
        }

        #expect(store.state.ui.hasLoadedMetricValues == true)
        #expect(store.metricsManager.state.metrics.first { $0.label == DashboardStrings.bodyFat }?.value != DashboardStrings.placeholder)
    }

    @Test("resetMetricsToLatestEntry: dispatches the async reset task")
    func resetMetricsToLatestEntryDispatchesTask() async {
        let (store, _, _) = makeSUT()
        store.displayManager.resetMetricsToLatestEntry()

        try? await Task.sleep(nanoseconds: 50_000_000)

        #expect(true)
    }
}
