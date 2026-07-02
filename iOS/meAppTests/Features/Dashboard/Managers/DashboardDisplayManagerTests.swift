import Combine
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardDisplayManagerTests {

    func makeSUT() -> DashboardManagerTestSupport.StoreSUT {
        DashboardManagerTestSupport.makeStore(
            cacheManager: DashboardCacheManager(),
            formatter: MockDashboardFormatter()
        )
    }

    func makeSUT(formatter: DashboardFormatterProtocol) -> DashboardManagerTestSupport.StoreSUT {
        DashboardManagerTestSupport.makeStore(
            cacheManager: DashboardCacheManager(),
            formatter: formatter
        )
    }

    @Test("getOperationsForLabelDateRange: week period returns operations inside the visible label range")
    func getOperationsForLabelDateRangeWeek() async {
        let (store, accountService, entryService) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = (1...10).map { day in
            DashboardTestFixtures.makeSummary(
                period: String(format: "2026-03-%02d", day),
                entryTimestamp: String(format: "2026-03-%02dT08:00:00Z", day),
                weight: Double(1800 + (day - 1) * 10)
            )
        }
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let operations = store.displayManager.getOperationsForLabelDateRange()

        #expect(operations.map(\.period) == [
            "2026-03-03", "2026-03-04", "2026-03-05", "2026-03-06", "2026-03-07", "2026-03-08", "2026-03-09"
        ])
    }

    @Test("displayWeight: selected point shows exact converted weight for the current point")
    func displayWeightSelectedPoint() async {
        let (store, accountService, entryService) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = daily[2]
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.displayWeight == 182.0)
    }

    @Test("displayWeight: selected date uses interpolated graph weight when no exact point is selected")
    func displayWeightSelectedDateUsesInterpolation() async {
        let (store, accountService, entryService) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", entryTimestamp: "2026-03-01T08:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", entryTimestamp: "2026-03-03T08:00:00Z", weight: 1820)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        let selectedDate = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedXValue = selectedDate
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = store.graphManager.interpolatedDisplayWeight(
            at: selectedDate,
            from: store.continuousOperations,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: store.goalManager.convertWeightToDisplay
        )

        #expect(store.displayManager.displayWeight == expected)
    }

    @Test("displayWeight: selected point in weightless mode shows the anchor-adjusted difference")
    func displayWeightWeightlessMode() async {
        let (store, accountService, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            weightlessOn: true,
            weightlessWeight: 1750
        )
        accountService.activeAccount = activeAccount
        store.accountService.activeAccount = activeAccount

        let daily = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", entryTimestamp: "2026-03-01T08:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", entryTimestamp: "2026-03-02T08:00:00Z", weight: 1810)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = daily[1]
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = store.goalManager.convertWeightToDisplay(Int(daily[1].weight)) - (store.weightlessAnchorWeight ?? 0)

        #expect(store.isWeightlessModeEnabled == true)
        #expect(store.weightlessAnchorWeight == 175.0)
        #expect(store.displayManager.displayWeight == expected)
        #expect(store.displayManager.displayWeight == 6.0)
    }

    @Test("displayWeight: falls back to interpolated average when the current label range has no operations")
    func displayWeightInterpolatedAverageFallback() async {
        let (store, accountService, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        accountService.activeAccount = activeAccount
        store.accountService.activeAccount = activeAccount

        let daily = [
            DashboardTestFixtures.makeSummary(period: "2026-01-01", entryTimestamp: "2026-01-01T08:00:00Z", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-31", entryTimestamp: "2026-03-31T08:00:00Z", weight: 1820)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-02-10", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let opsForLabel = store.displayManager.getOperationsForLabelDateRange()
        let labelRange = store.dateRangeManager.getLabelDateRangeForMonth(
            xScrollPosition: store.graphManager.state.xScrollPosition,
            visibleDomainLength: store.graphManager.visibleDomainLength(for: .month),
            continuousOperations: store.continuousOperations
        )
        let expected = store.graphManager.calculateInterpolatedAverageForVisibleRange(
            from: store.continuousOperations,
            period: .month,
            isWeightlessMode: false,
            anchorWeight: store.weightlessAnchorWeight,
            convertWeight: store.goalManager.convertWeightToDisplay,
            labelRange: labelRange
        )

        #expect(opsForLabel.isEmpty)
        #expect(store.displayManager.displayWeight == expected)
    }

    @Test("weightDisplayLabel: returns no entries when nothing is visible or selectable")
    func weightDisplayLabelNoEntries() {
        let (store, _, _) = makeSUT()

        #expect(store.displayManager.weightDisplayLabel == "no entries")
    }

    @Test("weightDisplayLabel: crosshair selection shows day average for week and month periods")
    func weightDisplayLabelSelectedDateUsesDayAverage() async {
        let (store, accountService, entryService) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedXValue = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.weightDisplayLabel == "day average")
    }

    @Test("weightDisplayLabel: selecting the most recent day shows latest entry for week and month")
    func weightDisplayLabelLatestDayUsesLatestEntry() async {
        let (store, accountService, entryService) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        // 2026-03-05 is the newest entry in the fixture — selecting it must read as "latest entry".
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedXValue = DateTimeTools.getDateFromDateString("2026-03-05", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.isLatestDaySelected == true)
        #expect(store.displayManager.weightDisplayLabel == "latest entry")
    }

    @Test("activeMonthInterval: month mode exposes the contained interval and other periods do not")
    func activeMonthIntervalFollowsSelectedPeriod() async throws {
        let (store, accountService, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        accountService.activeAccount = activeAccount
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let interval = try #require(store.displayManager.activeMonthInterval)
        #expect(interval.contains(DateTimeTools.getDateFromDateString("2026-03-15", format: "yyyy-MM-dd")))

        store.graphManager.state.selectedPeriod = .week
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.activeMonthInterval == nil)
    }

    @Test("getOperationsForLabelDateRange: cache manager receives the current period and scroll position")
    func getOperationsForLabelDateRangeUsesCacheManagerRequestKey() {
        let cacheManager = MockDashboardCacheManager()
        let sut = DashboardManagerTestSupport.makeStore(
            cacheManager: cacheManager,
            formatter: MockDashboardFormatter()
        )
        let store = sut.store
        let expectedScrollPosition = DateTimeTools.getDateFromDateString("2026-04-01", format: "yyyy-MM-dd")

        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.xScrollPosition = expectedScrollPosition
        DashboardManagerTestSupport.syncStoreGraphState(store)

        _ = store.displayManager.getOperationsForLabelDateRange()

        #expect(cacheManager.getLabelDateRangeOperationsCalls == 1)
        #expect(cacheManager.lastLabelDateRangeRequest?.period == .month)
        #expect(cacheManager.lastLabelDateRangeRequest?.scrollPosition == expectedScrollPosition)
    }

    @Test("weightLabel: selected point uses the graph manager's formatted selection date")
    func weightLabelSelectedPoint() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = daily[1]
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = store.graphManager.formatSelectedDate(daily[1].date, for: .week)

        #expect(store.displayManager.weightLabel == expected)
    }

    @Test("weightLabel: total view uses the overall date bounds")
    func weightLabelTotalPeriod() async throws {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries(),
            monthly: DashboardTestFixtures.makeSortedMonthlySummaries()
        )

        store.graphManager.state.selectedPeriod = .total
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let bounds = try #require(store.dataManager.getDateBounds(for: .total))
        let expected = store.graphManager.formatDateRange(minDate: bounds.min, maxDate: bounds.max, for: .total)

        #expect(store.displayManager.weightLabel == expected)
    }

    @Test("weightLabel: year view uses the visible year gridline range")
    func weightLabelYearPeriod() async throws {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            monthly: DashboardTestFixtures.makeSortedMonthlySummaries()
        )

        let scrollPosition = DateTimeTools.getDateFromDateString("2026-01-01", format: "yyyy-MM-dd")
        store.graphManager.state.selectedPeriod = .year
        store.graphManager.state.xScrollPosition = scrollPosition
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let range = try #require(store.dateRangeManager.getYearLabelDateRange(xScrollPosition: scrollPosition))
        let expected = store.graphManager.formatDateRange(minDate: range.start, maxDate: range.end, for: .year)

        #expect(store.displayManager.weightLabel == expected)
    }

    @Test("weightLabel: month view uses the visible month gridline range")
    func weightLabelMonthPeriod() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        let scrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        store.graphManager.state.selectedPeriod = .month
        store.graphManager.state.xScrollPosition = scrollPosition
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let range = store.dateRangeManager.getLabelDateRangeForMonth(
            xScrollPosition: scrollPosition,
            visibleDomainLength: store.graphManager.visibleDomainLength(for: .month),
            continuousOperations: store.continuousOperations
        )
        let expected = store.graphManager.formatDateRange(minDate: range.start, maxDate: range.end, for: .month)

        #expect(store.displayManager.weightLabel == expected)
    }

    @Test("weightLabel: week view uses the visible week gridline range")
    func weightLabelWeekPeriod() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        let scrollPosition = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = scrollPosition
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = store.dateRangeManager.labelForWeekGridlines(
            xScrollPosition: scrollPosition
        ) { min, max, period in
                store.graphManager.formatDateRange(minDate: min, maxDate: max, for: period)
            }

        #expect(store.displayManager.weightLabel == expected)
    }

    @Test("getCurrentAverageWeight: averages the current label-range operations")
    func getCurrentAverageWeightUsesLabelRangeOperations() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        store.accountService.activeAccount = activeAccount

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = store.displayManager.getOperationsForLabelDateRange()
            .map { store.goalManager.convertWeightToDisplay(Int($0.weight)) }
            .reduce(0, +) / Double(store.displayManager.getOperationsForLabelDateRange().count)

        #expect(store.displayManager.getCurrentAverageWeight() == expected)
    }

    @Test("displayUnitText: uses the unit for the current display weight")
    func displayUnitTextUsesDisplayWeight() async {
        let (store, _, entryService) = makeSUT()
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightUnit: .lb)
        store.accountService.activeAccount = activeAccount

        let daily = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = daily[0]
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let expected = WeightValueConvertor.unitForDisplay(value: 180.0, unit: .lb)

        #expect(store.displayManager.displayUnitText == expected)
    }

    @Test("formatting helpers: delegate to the formatter")
    func formattingHelpersDelegateToFormatter() {
        let formatter = MockDashboardFormatter()
        formatter.yAxisTickLabelResult = "tick"
        formatter.roundedGoalWeightResult = 123.4
        formatter.chartDateResult = "chart"
        formatter.formattedMetricValueResult = "metric"
        let sut = makeSUT(formatter: formatter)
        let store = sut.store

        store.graphManager.state.selectedPeriod = .month
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.displayManager.formatWeightDisplayText(nil) == "0.0")
        #expect(store.displayManager.formatYAxisTickLabel(1.5) == "tick")
        #expect(store.displayManager.formatChartDate(Date()) == "chart")
        #expect(formatter.lastChartDatePeriod == .month)
        #expect(store.displayManager.roundedGoalWeight(200.0) == 123.4)
        #expect(store.displayManager.formattedMetricValue(for: (preLabel: nil, value: "12")) == "metric")
    }

    @Test("metricInfoDateLabel: passes formatter context including history and selection state")
    func metricInfoDateLabelUsesFormatterContext() {
        let formatter = MockDashboardFormatter()
        formatter.metricInfoDateLabelResult = "info"
        formatter.parsedEntryDate = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        formatter.dashboardEntryResult = false
        let sut = makeSUT(formatter: formatter)
        let store = sut.store

        store.state.data.dailySummaries = [DashboardTestFixtures.makeSummary()]
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.selectedPoint = DashboardTestFixtures.makeSummary(period: "2026-03-02")
        store.graphManager.state.selectedXValue = DateTimeTools.getDateFromDateString("2026-03-04", format: "yyyy-MM-dd")
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

        let result = store.displayManager.metricInfoDateLabel(for: dto)

        #expect(result == "info")
        #expect(formatter.lastMetricInfoDateLabelArgs?.isFromHistory == true)
        #expect(formatter.lastMetricInfoDateLabelArgs?.period == .week)
        #expect(formatter.lastMetricInfoDateLabelArgs?.selectedPointDate == store.graphManager.state.selectedPoint?.date)
        #expect(formatter.lastMetricInfoDateLabelArgs?.crosshairDate == store.graphManager.state.selectedXValue)
    }

    @Test("metric info selection: dashboard type constrains allowed metrics and invalid selections fall back")
    func metricInfoSelectionValidation() {
        let (store, _, _) = makeSUT()

        store.state.metrics.dashboardType = .dashboard4
        let allowed = store.displayManager.allowedMetricsForMetricInfo()

        #expect(allowed == [.weight, .bmi, .bodyFat, .muscleMass, .water])
        #expect(store.displayManager.validateMetricInfoSelection(.pulse) == .weight)

        store.state.metrics.dashboardType = .dashboard12
        #expect(store.displayManager.allowedMetricsForMetricInfo().contains(.pulse))
        #expect(store.displayManager.validateMetricInfoSelection(.pulse) == .pulse)
    }

}
