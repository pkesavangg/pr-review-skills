import Charts
import Foundation
@testable import meApp
import Testing

@MainActor
extension DashboardGraphManagerTests {

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
    func xAxisMathHelpersReturnExpectedDates() throws {
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
        let snapped = sut.snapScrollPosition(try #require(ISO8601DateFormatter().date(from: "2026-03-03T12:34:00Z")), for: .week)

        #expect(xValues.isEmpty == false)
        #expect(clamped != outOfRange)
        #expect(snapped != (try #require(ISO8601DateFormatter().date(from: "2026-03-03T12:34:00Z"))))
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
    func formattingAndMidpointHelpersReturnExpectedValues() throws {
        let sut = makeSUT()
        let operations = DashboardTestFixtures.makeSortedDailySummaries()
        let minDate = try #require(operations.first).date
        let maxDate = try #require(operations.last).date
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
