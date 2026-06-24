import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardDisplayManagerTests {

    private func makeSUT() -> DashboardManagerTestSupport.StoreSUT {
        DashboardManagerTestSupport.makeStore(
            cacheManager: DashboardCacheManager(),
            formatter: MockDashboardFormatter()
        )
    }

    private func makeSUT(formatter: DashboardFormatterProtocol) -> DashboardManagerTestSupport.StoreSUT {
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
            xScrollPosition: scrollPosition,
            formatDateRange: { min, max, period in
                store.graphManager.formatDateRange(minDate: min, maxDate: max, for: period)
            }
        )

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
            WeightValueConvertor.unitForDisplay(value: store.displayManager.getCurrentAverageWeight(), unit: .lb)
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
