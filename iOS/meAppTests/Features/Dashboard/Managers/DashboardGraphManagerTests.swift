import Charts
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardGraphManagerTests {

    private func makeSUT(initialState: GraphState = GraphState()) -> DashboardGraphManager {
        TestDependencyContainer.reset()
        _ = TestDependencyContainer.registerDashboardConcreteDependencies()
        return DashboardGraphManager(initialState: initialState)
    }

    @Test("generateChartData: builds weight series from operations")
    func generateChartDataBuildsWeightSeries() {
        let sut = makeSUT()
        let operations = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 1810)
        ]

        let series = sut.generateChartData(
            from: operations,
            selectedMetric: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(series.count == 2)
        #expect(series.map(\.series) == [DashboardStrings.weight, DashboardStrings.weight])
        #expect(series.map(\.value) == [180.0, 181.0])
    }

    @Test("generateChartDataWithYAxisDomain: returns empty for empty operations")
    func generateChartDataWithYAxisDomainEmpty() {
        let sut = makeSUT()

        let series = sut.generateChartDataWithYAxisDomain(
            from: [],
            visibleOperations: [],
            selectedMetric: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        #expect(series.isEmpty)
    }

    @Test("generateChartDataWithYAxisDomain: reuses cached series while scrolling when the cache key matches")
    func generateChartDataWithYAxisDomainUsesCacheWhileScrolling() {
        let sut = makeSUT()
        let firstOps = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1800, bodyFat: 250),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 260)
        ]
        let secondOps = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1900, bodyFat: 350),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1910, bodyFat: 360)
        ]

        let first = sut.generateChartDataWithYAxisDomain(
            from: firstOps,
            visibleOperations: firstOps,
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        sut.state.isScrolling = true

        let second = sut.generateChartDataWithYAxisDomain(
            from: secondOps,
            visibleOperations: secondOps,
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        #expect(second == first)
    }

    @Test("generateChartDataWithYAxisDomain: metric changes bypass the scrolling cache")
    func generateChartDataWithYAxisDomainMetricChangeRebuildsSeries() {
        let sut = makeSUT()
        let operations = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1800, bodyFat: 250),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 260)
        ]

        let withMetric = sut.generateChartDataWithYAxisDomain(
            from: operations,
            visibleOperations: operations,
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        sut.state.isScrolling = true

        let weightOnly = sut.generateChartDataWithYAxisDomain(
            from: operations,
            visibleOperations: operations,
            selectedMetric: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        #expect(withMetric.contains { $0.series == DashboardStrings.bodyFat })
        #expect(weightOnly.allSatisfy { $0.series == DashboardStrings.weight })
        #expect(weightOnly != withMetric)
    }

    @Test("updateSelectedPeriod: clears selection and invalidates cached chart data")
    func updateSelectedPeriodClearsSelectionAndChartCache() {
        let sut = makeSUT()
        let firstOps = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1800, bodyFat: 250),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1810, bodyFat: 260)
        ]
        let secondOps = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", weight: 1900, bodyFat: 350),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", weight: 1910, bodyFat: 360)
        ]

        sut.state.selectedPoint = firstOps[0]
        sut.state.selectedXValue = firstOps[0].date
        let cached = sut.generateChartDataWithYAxisDomain(
            from: firstOps,
            visibleOperations: firstOps,
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        sut.updateSelectedPeriod(.month)
        sut.state.isScrolling = true

        let recalculated = sut.generateChartDataWithYAxisDomain(
            from: secondOps,
            visibleOperations: secondOps,
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        #expect(sut.state.selectedPeriod == .month)
        #expect(sut.state.selectedPoint == nil)
        #expect(sut.state.selectedXValue == nil)
        #expect(recalculated != cached)
    }

    @Test("handleCompleteChartSelection: exact match selects the point and updates metrics")
    func handleCompleteChartSelectionSelectsExactPoint() async {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        sut.state.selectedPeriod = .week
        var updatedPoint: BathScaleWeightSummary?
        var resetCalls = 0
        var placeholderCalls = 0
        let selectedDate = operations[1].date

        await sut.handleCompleteChartSelection(
            at: selectedDate,
            operations: operations,
            updateMetrics: { point in
                updatedPoint = point
            },
            resetMetrics: {
                resetCalls += 1
            },
            setMetricPlaceholders: {
                placeholderCalls += 1
            }
        )

        #expect(updatedPoint?.period == "2026-03-02")
        #expect(sut.state.selectedPoint?.period == "2026-03-02")
        #expect(sut.state.selectedXValue == selectedDate)
        #expect(sut.state.showCrosshair == true)
        #expect(resetCalls == 0)
        #expect(placeholderCalls == 0)
    }

    @Test("handleCompleteChartSelection: missing exact match clears selected point and uses placeholders")
    func handleCompleteChartSelectionMissingPointUsesPlaceholders() async {
        let sut = makeSUT()
        let operations = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", weight: 1820)
        ]
        sut.state.selectedPeriod = .week
        let selectedDate = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        var placeholderCalls = 0

        await sut.handleCompleteChartSelection(
            at: selectedDate,
            operations: operations,
            updateMetrics: { _ in },
            resetMetrics: {},
            setMetricPlaceholders: {
                placeholderCalls += 1
            }
        )

        #expect(sut.state.selectedPoint == nil)
        #expect(sut.state.selectedXValue == selectedDate)
        #expect(sut.state.showCrosshair == true)
        #expect(placeholderCalls == 1)
    }

    @Test("handleCompleteChartSelection: year view matches points by month granularity")
    func handleCompleteChartSelectionMatchesMonthForYearPeriod() async {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedMonthlySummaries()
        sut.state.selectedPeriod = .year
        let selectedDate = DateTimeTools.getDateFromDateString("2026-02-15", format: "yyyy-MM-dd")
        var updatedPoint: BathScaleWeightSummary?

        await sut.handleCompleteChartSelection(
            at: selectedDate,
            operations: operations,
            updateMetrics: { point in
                updatedPoint = point
            },
            resetMetrics: {},
            setMetricPlaceholders: {}
        )

        #expect(updatedPoint?.period == "2026-02")
        #expect(sut.state.selectedPoint?.period == "2026-02")
    }

    @Test("handleCompleteChartSelection: metric update failures reset metrics instead of leaving stale state")
    func handleCompleteChartSelectionFailureResetsMetrics() async {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        sut.state.selectedPeriod = .week
        var resetCalls = 0
        var placeholderCalls = 0

        await sut.handleCompleteChartSelection(
            at: operations[0].date,
            operations: operations,
            updateMetrics: { _ in
                throw DashboardTestError.simulatedFailure
            },
            resetMetrics: {
                resetCalls += 1
            },
            setMetricPlaceholders: {
                placeholderCalls += 1
            }
        )

        #expect(sut.state.selectedPoint?.period == "2026-03-01")
        #expect(resetCalls == 1)
        #expect(placeholderCalls == 0)
        #expect(sut.state.showCrosshair == true)
    }

    @Test("handleCompleteChartSelection: scrolling state suppresses selection changes")
    func handleCompleteChartSelectionWhileScrollingDoesNothing() async {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        sut.state.isScrolling = true

        await sut.handleCompleteChartSelection(
            at: operations[0].date,
            operations: operations,
            updateMetrics: { _ in },
            resetMetrics: {},
            setMetricPlaceholders: {}
        )

        #expect(sut.state.selectedPoint == nil)
        #expect(sut.state.selectedXValue == nil)
        #expect(sut.state.showCrosshair == false)
    }

    @Test("handleChartSelection: nil clears any existing selection when not scrolling")
    func handleChartSelectionNilClearsSelection() async {
        let sut = makeSUT()
        sut.state.selectedPoint = DashboardTestFixtures.makeSummary()
        sut.state.selectedXValue = Date()
        sut.state.showCrosshair = true

        await sut.handleChartSelection(at: nil)

        #expect(sut.state.selectedPoint == nil)
        #expect(sut.state.selectedXValue == nil)
        #expect(sut.state.showCrosshair == false)
    }

    @Test("updateSelectedPoint: replaces the selected point in graph state")
    func updateSelectedPointReplacesState() {
        let sut = makeSUT()
        let point = DashboardTestFixtures.makeSummary(period: "2026-03-09", weight: 1880)

        sut.updateSelectedPoint(point)

        #expect(sut.state.selectedPoint == point)
    }

    @Test("handleScrollStart: enters scrolling mode and clears selection")
    func handleScrollStartClearsSelection() {
        let sut = makeSUT()
        sut.state.selectedPoint = DashboardTestFixtures.makeSummary()
        sut.state.selectedXValue = Date()

        sut.handleScrollStart()

        #expect(sut.state.isScrolling == true)
        #expect(sut.state.selectedPoint == nil)
        #expect(sut.state.selectedXValue == nil)
    }

    @Test("handleScrollPhaseChange: idle commits the buffered scroll position and exits scrolling")
    func handleScrollPhaseChangeIdleCommitsBufferedPosition() async {
        let sut = makeSUT()
        let expectedPosition = DateTimeTools.getDateFromDateString("2026-03-15", format: "yyyy-MM-dd")

        if #available(iOS 18.0, *) {
            sut.handleScrollStart()
            sut.handleScrollPositionChange(expectedPosition)

            await sut.handleScrollPhaseChange(.idle)

            #expect(sut.state.xScrollPosition == expectedPosition)
            #expect(sut.state.isScrolling == false)
            #expect(sut.state.showCrosshair == false)
        }
    }

    @Test("handleScrollPhaseChange: interacting enters scrolling mode once and clears selection")
    func handleScrollPhaseChangeInteractingBeginsGesture() async {
        let sut = makeSUT()
        sut.state.selectedPoint = DashboardTestFixtures.makeSummary()
        sut.state.selectedXValue = Date()

        if #available(iOS 18.0, *) {
            await sut.handleScrollPhaseChange(.tracking)
            await sut.handleScrollPhaseChange(.interacting)

            #expect(sut.state.isScrolling == true)
            #expect(sut.state.hasDetectedScrollInCurrentGesture == true)
            #expect(sut.state.selectedPoint == nil)
            #expect(sut.state.selectedXValue == nil)
        }
    }

    @Test("handleScrollEnd: commits the buffered position after the debounce timer")
    func handleScrollEndCommitsBufferedPosition() async {
        let sut = makeSUT()
        let bufferedPosition = DateTimeTools.getDateFromDateString("2026-03-18", format: "yyyy-MM-dd")

        sut.handleScrollStart()
        sut.handleScrollPositionChange(bufferedPosition)
        await sut.handleScrollEnd()

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
            sut.state.isScrolling == false && sut.state.xScrollPosition == bufferedPosition
        }

        #expect(sut.state.xScrollPosition == bufferedPosition)
        #expect(sut.state.isScrolling == false)
    }

    @Test("handleScrollEndOptimized: ends scrolling and invokes all supplied refresh closures")
    func handleScrollEndOptimizedInvokesRefreshClosures() async {
        let sut = makeSUT()
        sut.state.isScrolling = true
        sut.state.hasDetectedScrollInCurrentGesture = true
        var weightUpdates = 0
        var yAxisUpdates = 0
        var metricUpdates = 0

        sut.handleScrollEndOptimized(
            updateWeightDisplay: { weightUpdates += 1 },
            recalculateYAxis: { yAxisUpdates += 1 },
            updateMetrics: { metricUpdates += 1 }
        )

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
            sut.state.isScrolling == false && metricUpdates == 1
        }

        #expect(weightUpdates == 1)
        #expect(yAxisUpdates == 1)
        #expect(metricUpdates == 1)
        #expect(sut.state.hasDetectedScrollInCurrentGesture == false)
    }

    @Test("endScrollingImmediately: clears scroll state and chart cache")
    func endScrollingImmediatelyClearsStateAndCache() {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()

        _ = sut.generateChartDataWithYAxisDomain(
            from: operations,
            visibleOperations: operations,
            selectedMetric: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )
        sut.state.isScrolling = true
        sut.handleScrollPositionChange(Date())

        sut.endScrollingImmediately()
        let rebuilt = sut.generateChartDataWithYAxisDomain(
            from: operations,
            visibleOperations: operations,
            selectedMetric: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 170.0 ... 190.0
        )

        #expect(sut.state.isScrolling == false)
        #expect(sut.state.hasDetectedScrollInCurrentGesture == false)
        #expect(rebuilt.isEmpty == false)
    }

    @Test("handleChartSelection: non-nil selections set the selected x-value when not scrolling")
    func handleChartSelectionStoresSelectionDate() async {
        let sut = makeSUT()
        let selectedDate = DateTimeTools.getDateFromDateString("2026-03-12", format: "yyyy-MM-dd")

        await sut.handleChartSelection(at: selectedDate)

        #expect(sut.state.selectedXValue == selectedDate)
        #expect(sut.state.showCrosshair == false)
    }

    @Test("visible operations helpers: visible, strict visible, and bracketing operations return scoped subsets")
    func visibleOperationHelpersReturnExpectedScopes() {
        let sut = makeSUT()
        let operations = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-05", weight: 1810),
            DashboardTestFixtures.makeSummary(period: "2026-03-10", weight: 1820),
            DashboardTestFixtures.makeSummary(period: "2026-03-18", weight: 1830)
        ]
        sut.state.selectedPeriod = .week
        sut.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-05", format: "yyyy-MM-dd")

        let visible = sut.getVisibleOperations(from: operations)
        let strictVisible = sut.getStrictVisibleOperations(from: operations)
        let bracketing = sut.getBracketingOperations(from: operations)

        #expect(visible.isEmpty == false)
        #expect(strictVisible.isEmpty == false)
        #expect(bracketing.count <= 2)
    }

    @Test("ensureLatestEntriesVisible: updates the scroll position when scrolling is idle and ignores scrolling state")
    func ensureLatestEntriesVisibleUpdatesPositionWhenEligible() {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        sut.state.selectedPeriod = .week
        sut.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-02-01", format: "yyyy-MM-dd")

        sut.ensureLatestEntriesVisible(from: operations)
        let updatedPosition = sut.state.xScrollPosition

        sut.state.isScrolling = true
        sut.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-02-01", format: "yyyy-MM-dd")
        sut.ensureLatestEntriesVisible(from: operations)

        #expect(updatedPosition != DateTimeTools.getDateFromDateString("2026-02-01", format: "yyyy-MM-dd"))
        #expect(sut.state.xScrollPosition == DateTimeTools.getDateFromDateString("2026-02-01", format: "yyyy-MM-dd"))
    }

    @Test("x-axis math helpers: generate values, clamp positions, and snap positions")
    func xAxisMathHelpersReturnExpectedDates() {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        let scrollPosition = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")
        let minDate = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")
        let maxDate = DateTimeTools.getDateFromDateString("2026-03-05", format: "yyyy-MM-dd")
        sut.state.selectedPeriod = .week
        sut.state.xScrollPosition = scrollPosition

        let xValues = sut.generateVisibleXAxisValues(for: .week, from: operations, scrollPosition: scrollPosition)
        let outOfRange = DateTimeTools.getDateFromDateString("2026-04-01", format: "yyyy-MM-dd")
        let clamped = sut.clampScrollPosition(outOfRange, for: .week, minDate: minDate, maxDate: maxDate)
        let snapped = sut.snapScrollPosition(ISO8601DateFormatter().date(from: "2026-03-03T12:34:00Z")!, for: .week)

        #expect(xValues.isEmpty == false)
        #expect(clamped != outOfRange)
        #expect(snapped != ISO8601DateFormatter().date(from: "2026-03-03T12:34:00Z")!)
    }

    @Test("stat helpers: interpolated, average, and weightless display calculations return converted values")
    func statHelpersReturnExpectedValues() {
        let sut = makeSUT()
        let operations = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", weight: 1820)
        ]
        sut.state.selectedPeriod = .week
        sut.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd")

        let interpolated = sut.interpolatedDisplayWeight(
            at: DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd"),
            from: operations,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )
        let average = sut.getCurrentAverageWeight(
            from: operations,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )
        let weightless = sut.calculateWeightlessDisplay(
            operations,
            anchorWeight: 175.0,
            period: .week,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(interpolated == 181.0)
        #expect(average == 181.0)
        #expect(weightless != nil)
    }

    @Test("metric helpers: metric extraction, availability, and display checks reflect operation contents")
    func metricHelpersReflectOperationContents() {
        let sut = makeSUT()
        let operations = [
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-01", bodyFat: 250, pulse: 70),
            DashboardTestFixtures.makeSummaryWithAllMetrics(period: "2026-03-02", bodyFat: 260, pulse: 75)
        ]

        #expect(sut.getMetricValue(for: DashboardStrings.bodyFat, from: operations[0]) == 250)
        #expect(sut.canDisplayMetric(DashboardStrings.bodyFat, from: operations) == true)
        #expect(sut.canDisplayMetric(DashboardStrings.heartBpm, from: [operations[0]]) == false)
        #expect(sut.getAvailableMetrics(from: operations).contains(DashboardStrings.bodyFat))
    }

    @Test("formatting and midpoint helpers: provide non-empty labels and deterministic midpoints")
    func formattingAndMidpointHelpersReturnExpectedValues() {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        let minDate = operations.first!.date
        let maxDate = operations.last!.date
        sut.state.selectedPeriod = .week
        sut.state.xScrollPosition = minDate

        let xLabel = sut.formatXAxisLabel(for: minDate, period: .week, operations: operations)
        let selectedDate = sut.formatSelectedDate(minDate, for: .week)
        let dateRange = sut.formatDateRange(minDate: minDate, maxDate: maxDate, for: .week)
        let fallback = sut.fallbackTimeLabel(for: .month)
        let midpoint = sut.currentVisibleMidpoint
        let explicitMidpoint = sut.visibleMidpoint(for: .week)
        let samples = sut.generateSampleDatesForVisibleRange(for: .week)

        #expect(xLabel != nil)
        #expect(selectedDate.isEmpty == false)
        #expect(dateRange.isEmpty == false)
        #expect(fallback.isEmpty == false)
        #expect(midpoint == explicitMidpoint)
        #expect(samples.isEmpty == false)
    }

    @Test("chart buffer and trigger helpers: window data, increment change state, and dispatch callbacks")
    func chartBufferAndTriggerHelpersDispatchCallbacks() async {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        sut.state.selectedPeriod = .week
        sut.state.xScrollPosition = DateTimeTools.getDateFromDateString("2026-03-02", format: "yyyy-MM-dd")

        let bufferedOps = sut.getChartOperationsWithBuffer(
            from: operations,
            scrollPosition: sut.state.xScrollPosition,
            period: .week
        )

        var weightUpdates = 0
        sut.updateWeightDisplayForCurrentView { weightUpdates += 1 }

        let previousTrigger = sut.state.dataChangeTrigger
        sut.recalculateYAxisForVisibleData { }

        var updatedPoint: BathScaleWeightSummary?
        var resetCalls = 0
        sut.updateMetricsForCurrentView(
            selectedPoint: operations[0],
            visibleOperations: operations,
            updateMetrics: { point in
                updatedPoint = point
            },
            resetMetrics: { resetCalls += 1 }
        )

        await DashboardTestFixtures.waitUntil { updatedPoint != nil }

        sut.updateMetricsForCurrentView(
            selectedPoint: nil,
            visibleOperations: operations,
            updateMetrics: { _ in },
            resetMetrics: { resetCalls += 1 }
        )

        #expect(bufferedOps.isEmpty == false)
        #expect(weightUpdates == 1)
        #expect(sut.state.dataChangeTrigger == previousTrigger + 1)
        #expect(updatedPoint?.period == operations[0].period)
        #expect(resetCalls == 1)
        #expect(sut.lastXAxisValues == [])
    }
}
