import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardChartManagerTests {

    // Test factory return; labeled tuple is clearer than a one-off SUT struct.
    // swiftlint:disable:next large_tuple
    private func makeSUT() -> (
        store: DashboardStore,
        accountService: AccountService,
        entryService: EntryService,
        cacheManager: MockDashboardCacheManager
    ) {
        makeSUT(cacheManager: MockDashboardCacheManager())
    }

    // Test factory return; labeled tuple is clearer than a one-off SUT struct.
    // swiftlint:disable:next large_tuple
    private func makeSUT(cacheManager: MockDashboardCacheManager) -> (
        store: DashboardStore,
        accountService: AccountService,
        entryService: EntryService,
        cacheManager: MockDashboardCacheManager
    ) {
        let sut = DashboardManagerTestSupport.makeStore(
            cacheManager: cacheManager,
            formatter: MockDashboardFormatter()
        )
        return (sut.store, sut.accountService, sut.entryService, cacheManager)
    }

    @Test("chart series: builds weight-only data from operations and expands when metric changes")
    func chartSeriesChangesWhenMetricChanges() async {
        let (store, accountService, entryService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let daily = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1800, bodyFat: 250),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 260),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-03", weight: 1820, bodyFat: 270)
        ]
        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: daily)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.state.ui.selectedMetricLabel = nil
        let weightOnly = store.chartSeriesData

        store.state.ui.selectedMetricLabel = DashboardStrings.bodyFat
        let withMetric = store.chartSeriesData

        #expect(weightOnly.count == 3)
        #expect(Set(weightOnly.map(\.series)) == [DashboardStrings.weight])
        #expect(withMetric.count > weightOnly.count)
        #expect(withMetric.contains { $0.series == DashboardStrings.bodyFat })
    }

    @Test("chart series: returns empty when there are no operations")
    func chartSeriesEmptyWithoutData() {
        let (store, _, _, _) = makeSUT()

        #expect(store.chartSeriesData.isEmpty)
    }

    @Test("updateSelectedPeriod: switches to monthly data for year view")
    func updateSelectedPeriodUsesNewPeriodData() async {
        let (store, accountService, entryService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries(),
            monthly: DashboardTestFixtures.makeSortedMonthlySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.chartManager.updateSelectedPeriod(.year)
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.state.graph.selectedPeriod == .year)
        #expect(store.chartSeriesData.count == 3)
        #expect(Set(store.chartSeriesData.map(\.series)) == [DashboardStrings.weight])
    }

    @Test("getVisibleOperations: returns cache-managed visible operations for the current request")
    func getVisibleOperationsUsesCacheManager() {
        let cacheManager = MockDashboardCacheManager()
        let (store, _, _, _) = makeSUT(cacheManager: cacheManager)
        let override = [DashboardTestFixtures.makeSummary(period: "2026-03-04", weight: 1830)]
        cacheManager.visibleOperationsOverride = override

        let result = store.chartManager.getVisibleOperations()

        #expect(result == override)
        #expect(cacheManager.getVisibleOperationsCalls == 1)
        #expect(cacheManager.lastVisibleIsScrolling == false)
    }

    @Test("updateYAxisCache: invalidates cached chart series when the computed domain changes")
    func updateYAxisCacheInvalidatesChartSeriesCacheOnDomainChange() async {
        let cacheManager = MockDashboardCacheManager()
        let (store, accountService, entryService, _) = makeSUT(cacheManager: cacheManager)
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        accountService.activeAccount = activeAccount
        store.accountService.activeAccount = activeAccount

        let firstBatch = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1800)
        ]
        let secondBatch = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 2400),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 2900),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", weight: 3300)
        ]

        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: firstBatch)
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        store.graphManager.state.chartHeight = 220
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.chartManager.updateYAxisCache(force: true)
        await DashboardTestFixtures.waitUntil {
            store.state.graph.cachedYAxisDomain != nil
        }
        let firstDomain = store.state.graph.cachedYAxisDomain
        DashboardManagerTestSupport.syncStoreGraphState(store)

        await DashboardManagerTestSupport.loadData(into: store, entryService: entryService, daily: secondBatch)
        // Loading data also invalidates the chart-series cache via DashboardStore's content-
        // signature sink, so isolate the domain-change invalidation with a before/after delta.
        let invalidationsBeforeDomainChange = cacheManager.invalidateChartSeriesCalls
        store.chartManager.updateYAxisCache(force: true)
        await DashboardTestFixtures.waitUntil {
            store.state.graph.cachedYAxisDomain != nil &&
            store.state.graph.cachedYAxisDomain != firstDomain
        }

        #expect(firstDomain != nil)
        #expect(store.state.graph.cachedYAxisDomain != firstDomain)
        #expect(cacheManager.invalidateChartSeriesCalls - invalidationsBeforeDomainChange == 1)
    }

    @Test("chart series: cache manager is used to serve chart data requests")
    func chartSeriesUsesCacheManager() {
        let cacheManager = MockDashboardCacheManager()
        let (store, _, _, _) = makeSUT(cacheManager: cacheManager)
        let expected = [
            GraphSeries(
                date: DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd"),
                value: 180,
                series: DashboardStrings.weight
            )
        ]
        cacheManager.chartSeriesOverride = expected

        let result = store.chartSeriesData

        #expect(result == expected)
        #expect(cacheManager.getChartSeriesDataCalls == 1)
    }

    @Test("getVisibleOperations: forwards scrolling state to the cache manager")
    func getVisibleOperationsPassesScrollingState() {
        let cacheManager = MockDashboardCacheManager()
        let (store, _, _, _) = makeSUT(cacheManager: cacheManager)
        store.state.graph.isScrolling = true

        _ = store.chartManager.getVisibleOperations()

        #expect(cacheManager.lastVisibleIsScrolling == true)
    }

    @Test("updateYAxisCache: ignores recalculation while scrolling unless forced")
    func updateYAxisCacheSkipsWhileScrollingWithoutForce() async {
        let cacheManager = MockDashboardCacheManager()
        let (store, accountService, entryService, _) = makeSUT(cacheManager: cacheManager)
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.isScrolling = true
        store.graphManager.state.chartHeight = 220
        DashboardManagerTestSupport.syncStoreGraphState(store)

        // Loading data invalidates the chart-series cache via the content-signature sink, so
        // assert the unforced, scrolling updateYAxisCache adds no *further* invalidation.
        let invalidationsBeforeUpdate = cacheManager.invalidateChartSeriesCalls
        store.chartManager.updateYAxisCache()

        #expect(store.state.graph.cachedYAxisDomain == nil)
        #expect(cacheManager.invalidateChartSeriesCalls == invalidationsBeforeUpdate)
    }

    @Test("updateSelectedPeriod: total view clears caches and refreshes metrics immediately")
    func updateSelectedPeriodTotalRefreshesMetrics() async {
        let cacheManager = MockDashboardCacheManager()
        let (store, accountService, entryService, _) = makeSUT(cacheManager: cacheManager)
        let displayManager = MockDashboardDisplayManager()
        store.chartManager.displayManager = displayManager
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries(),
            monthly: DashboardTestFixtures.makeSortedMonthlySummaries()
        )

        store.chartManager.updateSelectedPeriod(.total)
        DashboardManagerTestSupport.syncStoreGraphState(store)

        #expect(store.state.graph.selectedPeriod == .total)
        #expect(cacheManager.clearAllCachesCalls == 1)
        #expect(displayManager.updateMetricsForCurrentViewCalls == 1)
    }

    @Test("clearAllCaches: resets processing state and delegates cache clearing")
    func clearAllCachesResetsProcessingState() {
        let cacheManager = MockDashboardCacheManager()
        let (store, _, _, _) = makeSUT(cacheManager: cacheManager)
        store.chartManager.isProcessingScrollEnd = true

        store.chartManager.clearAllCaches()

        #expect(store.chartManager.isProcessingScrollEnd == false)
        #expect(cacheManager.clearAllCachesCalls == 1)
    }

    @Test("handleChartSelection: nil selection clears chart state and refreshes metrics")
    func handleChartSelectionNilClearsSelection() async {
        let (store, _, _, _) = makeSUT()
        let displayManager = MockDashboardDisplayManager()
        store.chartManager.displayManager = displayManager
        store.graphManager.state.selectedPoint = DashboardTestFixtures.makeSummary()
        store.graphManager.state.selectedXValue = Date()
        DashboardManagerTestSupport.syncStoreGraphState(store)

        await store.chartManager.handleChartSelection(at: nil)

        await DashboardTestFixtures.waitUntil {
            store.graphManager.state.selectedPoint == nil &&
            store.graphManager.state.selectedXValue == nil &&
            displayManager.updateMetricsForCurrentViewCalls == 1
        }

        #expect(store.graphManager.state.selectedPoint == nil)
        #expect(store.graphManager.state.selectedXValue == nil)
        #expect(displayManager.updateMetricsForCurrentViewCalls == 1)
    }

    @Test("handleScrollPhaseChange: idle refreshes visible metrics after scroll settles")
    func handleScrollPhaseChangeIdleRefreshesMetrics() async {
        let (store, _, _, _) = makeSUT()
        let displayManager = MockDashboardDisplayManager()
        store.chartManager.displayManager = displayManager

        if #available(iOS 18.0, *) {
            await store.chartManager.handleScrollPhaseChange(to: .idle)

            await DashboardTestFixtures.waitUntil {
                displayManager.updateMetricsForCurrentViewCalls == 1
            }

            #expect(displayManager.updateMetricsForCurrentViewCalls == 1)
        }
    }

    @Test("yAxis accessors: use defaults until cached values exist, then return cached values")
    func yAxisAccessorsUseCacheWhenAvailable() {
        let (store, _, _, _) = makeSUT()

        #expect(store.chartManager.yAxisDomain == 0.0 ... 100.0)
        #expect(store.chartManager.yAxisTicks == [0.0, 25.0, 50.0, 75.0, 100.0])

        store.state.graph.cachedYAxisDomain = 170.0 ... 190.0
        store.state.graph.cachedYAxisTicks = [170.0, 180.0, 190.0]

        #expect(store.chartManager.yAxisDomain == 170.0 ... 190.0)
        #expect(store.chartManager.yAxisTicks == [170.0, 180.0, 190.0])
    }

    @Test("getYAxisScale: total period uses continuous operations while shorter periods use visible operations")
    func getYAxisScaleUsesPeriodSpecificOperations() async {
        let cacheManager = MockDashboardCacheManager()
        let (store, accountService, entryService, _) = makeSUT(cacheManager: cacheManager)
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let allOperations = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 2600)
        ]
        let monthlyOperations = [
            DashboardTestFixtures.makeSummary(period: "2026-01", entryTimestamp: "2026-01-01T00:00:00Z", weight: 1600),
            DashboardTestFixtures.makeSummary(period: "2026-02", entryTimestamp: "2026-02-01T00:00:00Z", weight: 2800)
        ]
        let visibleOnly = [DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800)]
        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: allOperations,
            monthly: monthlyOperations
        )

        cacheManager.visibleOperationsOverride = visibleOnly
        store.state.graph.chartHeight = 220

        store.graphManager.state.selectedPeriod = .week
        DashboardManagerTestSupport.syncStoreGraphState(store)
        let weekScale = store.chartManager.getYAxisScale()
        let expectedWeek = store.graphManager.getYAxisScale(
            from: visibleOnly,
            goalWeight: store.goalWeightForDisplay,
            isWeightlessMode: store.isWeightlessModeEnabled,
            anchorWeight: store.weightlessAnchorWeight,
            convertWeight: store.goalManager.convertWeightToDisplay,
            chartHeight: 220
        )

        store.graphManager.state.selectedPeriod = .total
        DashboardManagerTestSupport.syncStoreGraphState(store)
        let totalScale = store.chartManager.getYAxisScale()
        let expectedTotal = store.graphManager.getYAxisScale(
            from: monthlyOperations,
            goalWeight: store.goalWeightForDisplay,
            isWeightlessMode: store.isWeightlessModeEnabled,
            anchorWeight: store.weightlessAnchorWeight,
            convertWeight: store.goalManager.convertWeightToDisplay,
            chartHeight: 220
        )

        #expect(weekScale.domain == expectedWeek.domain)
        #expect(totalScale.domain == expectedTotal.domain)
        #expect(cacheManager.getVisibleOperationsCalls == 2)
    }

    @Test("initializeChart: first initialization computes scroll state and eventually marks the graph ready")
    func initializeChartSetsReadyState() async {
        let (store, accountService, entryService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.chartManager.initializeChart()

        await DashboardTestFixtures.waitUntil {
            store.state.ui.hasInitializedChart && store.graphManager.state.isGraphReady
        }

        #expect(store.state.ui.hasInitializedChart == true)
        #expect(store.graphManager.state.isGraphReady == true)
    }

    @Test("initializeChart: already-initialized charts only refresh readiness and display state")
    func initializeChartWhenAlreadyInitializedMarksGraphReady() {
        let (store, _, _, _) = makeSUT()
        store.state.ui.hasInitializedChart = true
        store.graphManager.state.isGraphReady = false
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.chartManager.initializeChart()

        #expect(store.graphManager.state.isGraphReady == true)
        #expect(store.state.ui.hasInitializedChart == true)
    }

    @Test("handleScrollPositionChange and handleScrollStart: buffer the position and enter scrolling mode")
    func handleScrollPositionChangeAndStartTrackScrollState() async {
        let (store, _, _, _) = makeSUT()
        // Use .week so the committed position is the raw buffered value; .month (the default)
        // snaps the commit to the month boundary, which this test is not exercising.
        store.graphManager.state.selectedPeriod = .week
        let position = DateTimeTools.getDateFromDateString("2026-03-10", format: "yyyy-MM-dd")

        store.chartManager.handleScrollStart()
        store.chartManager.handleScrollPositionChange(position)

        if #available(iOS 18.0, *) {
            await store.chartManager.handleScrollPhaseChange(to: .idle)
        }

        #expect(store.chartManager.lastUserScrollTime != nil)
        #expect(store.graphManager.state.xScrollPosition == position)
    }

    @Test("handleScrollEndOptimized: runs the deferred scroll-end work and clears processing state")
    func handleScrollEndOptimizedRunsDeferredWork() async {
        let (store, accountService, entryService, _) = makeSUT()
        let displayManager = MockDashboardDisplayManager()
        store.chartManager.displayManager = displayManager
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.chartHeight = 220
        DashboardManagerTestSupport.syncStoreGraphState(store)

        store.chartManager.handleScrollEndOptimized()

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 1_500_000_000) {
            displayManager.updateMetricsForCurrentViewCalls == 1 &&
            store.chartManager.isProcessingScrollEnd == false
        }

        #expect(displayManager.updateMetricsForCurrentViewCalls == 1)
        #expect(store.chartManager.isProcessingScrollEnd == false)
        #expect(store.state.graph.cachedYAxisDomain != nil)
    }

    @Test("delegations: x-axis values and labels are served by the graph manager")
    func xAxisDelegationsReturnGraphResults() async {
        let (store, accountService, entryService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        let scrollPosition = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        let labelDate = DateTimeTools.getDateFromDateString("2026-03-03", format: "yyyy-MM-dd")
        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = scrollPosition
        DashboardManagerTestSupport.syncStoreGraphState(store)

        let xAxisValues = store.chartManager.xAxisValuesWithBuffer(for: .week)
        let expectedLabel = store.graphManager.formatXAxisLabel(for: labelDate, period: .week, operations: store.continuousOperations)

        #expect(xAxisValues.isEmpty == false)
        #expect(store.chartManager.xLabelString(for: labelDate, period: .week) == expectedLabel)
    }

    @Test("selectEntry and ensureLatestEntriesVisible: trigger UI updates and respect recent-scroll suppression")
    func selectEntryAndEnsureLatestEntriesVisible() async {
        let (store, accountService, entryService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        await DashboardManagerTestSupport.loadData(
            into: store,
            entryService: entryService,
            daily: DashboardTestFixtures.makeSortedDailySummaries()
        )

        let originalPosition = store.graphManager.state.xScrollPosition
        store.chartManager.selectEntry(store.continuousOperations.last)
        store.chartManager.ensureLatestEntriesVisible()
        let updatedPosition = store.graphManager.state.xScrollPosition

        store.chartManager.lastUserScrollTime = Date()
        store.graphManager.state.xScrollPosition = originalPosition
        store.chartManager.ensureLatestEntriesVisible()

        #expect(updatedPosition != originalPosition)
        #expect(store.graphManager.state.xScrollPosition == originalPosition)
    }
}
