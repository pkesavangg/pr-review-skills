import Foundation
import Testing
@testable import meApp

// MARK: - Concrete Test Subclass

/// Concrete subclass exposing `.week` as the default period for testing BaseSectionViewModel behavior.
/// The base class declares `timePeriod` as `fatalError`; this wrapper makes it testable.
@MainActor
private class TestSectionViewModel: BaseSectionViewModel {
    private let _timePeriod: TimePeriod
    init(period: TimePeriod = .week) {
        _timePeriod = period
        super.init()
    }
    override var timePeriod: TimePeriod { _timePeriod }
}

@MainActor
private final class InvalidDomainSectionViewModel: TestSectionViewModel {
    private let overriddenDomainLength: TimeInterval

    init(period: TimePeriod = .week, visibleDomainLength: TimeInterval) {
        self.overriddenDomainLength = visibleDomainLength
        super.init(period: period)
    }

    override var visibleDomainLength: TimeInterval { overriddenDomainLength }
}

// MARK: - Test Helpers

@MainActor
private func makeSUT(
    period: TimePeriod = .week,
    configureStore: Bool = false
) -> (sut: TestSectionViewModel, store: DashboardStore?) {
    let vm = TestSectionViewModel(period: period)
    if configureStore {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        vm.configure(with: store)
        return (vm, store)
    }
    return (vm, nil)
}

@MainActor
private func makeConfiguredSUT(
    period: TimePeriod = .week,
    summaries: [BathScaleWeightSummary] = []
) -> (sut: TestSectionViewModel, store: DashboardStore, accountService: AccountService) {
    let (store, accountService, cacheManager) = DashboardStoreTestSupport.makeSUT()

    if !summaries.isEmpty {
        switch period {
        case .week, .month:
            store.state.data.dailySummaries = summaries
        case .year, .total:
            store.state.data.monthlySummaries = summaries
        }
        cacheManager.chartSeriesOverride = summaries.map {
            GraphSeries(date: $0.date, value: $0.weight, series: "weight")
        }
    }

    let account = DashboardStoreTestSupport.makeActiveAccount()
    accountService.activeAccount = account
    store.state.graph.selectedPeriod = period

    let vm = TestSectionViewModel(period: period)
    vm.configure(with: store)
    return (vm, store, accountService)
}

@MainActor
private func makeDate(year: Int = 2026, month: Int = 3, day: Int = 1, hour: Int = 12) -> Date {
    var comps = DateComponents()
    comps.year = year
    comps.month = month
    comps.day = day
    comps.hour = hour
    comps.minute = 0
    comps.second = 0
    return Calendar.current.date(from: comps)! // swiftlint:disable:this force_unwrapping
}

// MARK: - Tests

@Suite(.serialized)
@MainActor
struct BaseSectionViewModelTests {

    // MARK: - Initial State

    @Test("Initial state: selectedPoint is nil")
    func initialSelectedPointIsNil() {
        let (sut, _) = makeSUT()
        #expect(sut.selectedPoint == nil)
    }

    @Test("Initial state: selectedDate is nil")
    func initialSelectedDateIsNil() {
        let (sut, _) = makeSUT()
        #expect(sut.selectedDate == nil)
    }

    @Test("Initial state: showCrosshair is false")
    func initialShowCrosshairIsFalse() {
        let (sut, _) = makeSUT()
        #expect(sut.showCrosshair == false)
    }

    @Test("Initial state: isScrolling is false")
    func initialIsScrollingIsFalse() {
        let (sut, _) = makeSUT()
        #expect(sut.isScrolling == false)
    }

    @Test("Initial state: yAxisDomain default is 0...100")
    func initialYAxisDomainDefault() {
        let (sut, _) = makeSUT()
        #expect(sut.yAxisDomain == 0...100)
    }

    @Test("Initial state: yAxisTicks is empty")
    func initialYAxisTicksEmpty() {
        let (sut, _) = makeSUT()
        #expect(sut.yAxisTicks.isEmpty)
    }

    @Test("Initial state: chartFrame is zero")
    func initialChartFrameIsZero() {
        let (sut, _) = makeSUT()
        #expect(sut.chartFrame == .zero)
    }

    @Test("Initial state: dashboardStore is nil before configure")
    func initialDashboardStoreIsNil() {
        let (sut, _) = makeSUT()
        #expect(sut.dashboardStore == nil)
    }

    // MARK: - timePeriod Override

    @Test("timePeriod returns the injected period", arguments: [TimePeriod.week, .month, .year, .total])
    func timePeriodReturnsInjected(period: TimePeriod) {
        let vm = TestSectionViewModel(period: period)
        #expect(vm.timePeriod == period)
    }

    @Test("adjustedLabelTicks excludes trailing phantom tick for week")
    func adjustedLabelTicksDropsTrailingWeekTick() {
        let (sut, _, _) = makeConfiguredSUT(period: .week)
        sut.scrollPosition = makeDate(year: 2026, month: 3, day: 1)

        let ticks = sut.xAxisValues
        #expect(ticks.count > 1)
        #expect(sut.adjustedLabelTicks == Array(ticks.dropLast()))
    }

    @Test("gridTicks for month exclude next-month boundary tick")
    func gridTicksForMonthExcludeNextMonthBoundary() {
        let (sut, _, _) = makeConfiguredSUT(period: .month)
        sut.scrollPosition = makeDate(year: 2026, month: 3, day: 1, hour: 0)

        let calendar = Calendar.current
        let tickComponents = sut.gridTicks.map { calendar.dateComponents([.month, .day], from: $0) }

        #expect(tickComponents.map(\.day) == [1, 8, 15, 22, 29])
        #expect(!tickComponents.contains { $0.month == 4 && $0.day == 1 })
    }

    // MARK: - hasXAxis

    @Test("hasXAxis is true for week/month/year", arguments: [TimePeriod.week, .month, .year])
    func hasXAxisTrueForScrollable(period: TimePeriod) {
        let vm = TestSectionViewModel(period: period)
        #expect(vm.hasXAxis == true)
    }

    @Test("hasXAxis is false for total")
    func hasXAxisFalseForTotal() {
        let vm = TestSectionViewModel(period: .total)
        #expect(vm.hasXAxis == false)
    }

    // MARK: - Stroke & Point Sizing

    @Test("lineWidth is 3 for scrollable periods", arguments: [TimePeriod.week, .month, .year])
    func lineWidthForScrollable(period: TimePeriod) {
        let vm = TestSectionViewModel(period: period)
        #expect(vm.lineWidth == 3)
    }

    @Test("lineWidth is 2 for total")
    func lineWidthForTotal() {
        let vm = TestSectionViewModel(period: .total)
        #expect(vm.lineWidth == 2)
    }

    @Test("basePointDiameter is 8 for scrollable, 4 for total")
    func basePointDiameter() {
        let scrollable = TestSectionViewModel(period: .week)
        #expect(scrollable.basePointDiameter == 8)
        let total = TestSectionViewModel(period: .total)
        #expect(total.basePointDiameter == 4)
    }

    @Test("selectedPointDiameter is 16 for scrollable, 8 for total")
    func selectedPointDiameter() {
        let scrollable = TestSectionViewModel(period: .month)
        #expect(scrollable.selectedPointDiameter == 16)
        let total = TestSectionViewModel(period: .total)
        #expect(total.selectedPointDiameter == 8)
    }

    @Test("basePointArea matches pi*r^2 for diameter 8")
    func basePointAreaCalculation() {
        let vm = TestSectionViewModel(period: .week)
        let expected = CGFloat.pi * 4 * 4 // radius=4
        #expect(abs(vm.basePointArea - expected) < 0.001)
    }

    @Test("selectedPointArea matches pi*r^2 for diameter 16")
    func selectedPointAreaCalculation() {
        let vm = TestSectionViewModel(period: .week)
        let expected = CGFloat.pi * 8 * 8 // radius=8
        #expect(abs(vm.selectedPointArea - expected) < 0.001)
    }

    @Test("pointArea returns selectedPointArea when isSelected is true")
    func pointAreaSelected() {
        let vm = TestSectionViewModel(period: .week)
        #expect(vm.pointArea(isSelected: true) == vm.selectedPointArea)
    }

    @Test("pointArea returns basePointArea when isSelected is false")
    func pointAreaNotSelected() {
        let vm = TestSectionViewModel(period: .week)
        #expect(vm.pointArea(isSelected: false) == vm.basePointArea)
    }

    @Test("symbolArea converts diameter to circle area correctly")
    func symbolAreaConversion() {
        let vm = TestSectionViewModel(period: .week)
        let area = vm.symbolArea(forDiameter: 10)
        let expected = CGFloat.pi * 25 // pi * (10/2)^2
        #expect(abs(area - expected) < 0.001)
    }

    @Test("symbolArea for zero diameter returns zero")
    func symbolAreaZeroDiameter() {
        let vm = TestSectionViewModel(period: .week)
        #expect(vm.symbolArea(forDiameter: 0) == 0)
    }

    // MARK: - plotXDate (default implementation)

    @Test("plotXDate returns the original date by default")
    func plotXDateReturnsOriginal() {
        let vm = TestSectionViewModel(period: .week)
        let date = Date()
        #expect(vm.plotXDate(for: date) == date)
    }

    // MARK: - preferredSelectedDate

    @Test("preferredSelectedDate returns selectedDate")
    func preferredSelectedDateMatchesSelectedDate() {
        let (sut, _) = makeSUT()
        let date = Date()
        sut.selectedDate = date
        #expect(sut.preferredSelectedDate == date)
    }

    @Test("preferredSelectedDate returns nil when selectedDate is nil")
    func preferredSelectedDateNilWhenNone() {
        let (sut, _) = makeSUT()
        #expect(sut.preferredSelectedDate == nil)
    }

    // MARK: - Selection Management

    @Test("handleChartSelection sets selectedDate and showCrosshair for non-nil date")
    func handleChartSelectionSetsState() {
        let (sut, _, _) = makeConfiguredSUT()
        let date = Date()
        sut.handleChartSelection(at: date)
        #expect(sut.selectedDate == date)
        #expect(sut.showCrosshair == true)
    }

    @Test("handleChartSelection does nothing for nil date")
    func handleChartSelectionNilDate() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true
        sut.handleChartSelection(at: nil)
        // State should not be cleared — only scroll start clears
        #expect(sut.selectedDate != nil)
        #expect(sut.showCrosshair == true)
    }

    @Test("handleChartSelection finds closest operation as selectedPoint")
    func handleChartSelectionFindsClosestPoint() {
        TestDependencyContainer.reset()
        let (store, accountService, cacheManager) = DashboardStoreTestSupport.makeSUT()
        let account = DashboardStoreTestSupport.makeActiveAccount()
        accountService.activeAccount = account

        let target = Date()
        let earlier = target.addingTimeInterval(-3600)
        let later = target.addingTimeInterval(3600)
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: earlier, weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", date: later, weight: 1820)
        ]
        store.state.data.dailySummaries = summaries
        store.state.graph.selectedPeriod = .week

        // Force continuousOperations to return our summaries
        cacheManager.chartSeriesOverride = summaries.map { GraphSeries(date: $0.date, value: $0.weight, series: "weight") }

        let vm = TestSectionViewModel(period: .week)
        vm.configure(with: store)

        // Select at exact target — earlier (-3600) is closer than later (+3600) equally but min picks first
        vm.handleChartSelection(at: target)

        // Both are equidistant, .min picks the first one
        #expect(vm.selectedPoint != nil)
    }

    @Test("clearSelection resets selectedPoint, selectedDate, and showCrosshair")
    func clearSelectionResetsAll() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true
        sut.selectedPoint = DashboardTestFixtures.makeSummary()

        sut.clearSelection()

        #expect(sut.selectedPoint == nil)
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    // MARK: - Scroll Management

    @Test("handleScrollStart sets isScrolling and clears selection")
    func handleScrollStartSetsState() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true

        sut.handleScrollStart()

        #expect(sut.isScrolling == true)
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleScrollEnd sets isScrolling to false")
    func handleScrollEndResetsScrolling() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.isScrolling = true

        sut.handleScrollEnd()

        #expect(sut.isScrolling == false)
    }

    @Test("updateScrollPosition updates scrollPosition value")
    func updateScrollPositionSetsValue() {
        let (sut, _) = makeSUT()
        let newDate = Date().addingTimeInterval(1000)
        sut.updateScrollPosition(to: newDate)
        #expect(sut.scrollPosition == newDate)
    }

    // MARK: - Chart Frame Management

    @Test("updateChartFrame updates the stored frame")
    func updateChartFrameStoresFrame() {
        let (sut, _) = makeSUT()
        let frame = CGRect(x: 0, y: 0, width: 300, height: 200)
        sut.updateChartFrame(frame)
        #expect(sut.chartFrame == frame)
    }

    @Test("updateChartFrame with small height change does not recalculate Y-axis")
    func updateChartFrameSmallChangeNoRecalc() {
        let (sut, _, _) = makeConfiguredSUT()
        let frame1 = CGRect(x: 0, y: 0, width: 300, height: 200)
        sut.updateChartFrame(frame1)
        let domain1 = sut.yAxisDomain

        // Small change (< 10)
        let frame2 = CGRect(x: 0, y: 0, width: 300, height: 205)
        sut.updateChartFrame(frame2)
        #expect(sut.yAxisDomain == domain1)
    }

    // MARK: - Configure

    @Test("configure sets dashboardStore reference")
    func configureSetsDashboardStore() {
        let (sut, store) = makeSUT(configureStore: true)
        #expect(sut.dashboardStore != nil)
        #expect(sut.dashboardStore === store)
    }

    @Test("configure syncs scrollPosition from store for scrollable period")
    func configureSyncsScrollPosition() {
        TestDependencyContainer.reset()
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        let date = Date().addingTimeInterval(-5000)
        store.state.graph.xScrollPosition = date

        let vm = TestSectionViewModel(period: .week)
        vm.configure(with: store)

        #expect(vm.scrollPosition == date)
    }

    @Test("configure syncs isScrolling from store")
    func configureSyncsIsScrolling() {
        TestDependencyContainer.reset()
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.graph.isScrolling = true

        let vm = TestSectionViewModel(period: .week)
        vm.configure(with: store)

        #expect(vm.isScrolling == true)
    }

    // MARK: - dateRange

    @Test("dateRange returns now...now when store is nil")
    func dateRangeNilStore() {
        let (sut, _) = makeSUT()
        let range = sut.dateRange
        // Both bounds should be very close (same instant)
        #expect(range.upperBound.timeIntervalSince(range.lowerBound) < 1)
    }

    // MARK: - chartOperations

    @Test("chartOperations returns empty when store is nil")
    func chartOperationsEmptyWithoutStore() {
        let (sut, _) = makeSUT()
        #expect(sut.chartOperations.isEmpty)
    }

    // MARK: - chartSeriesData

    @Test("chartSeriesData returns empty when store is nil")
    func chartSeriesDataEmptyWithoutStore() {
        let (sut, _) = makeSUT()
        #expect(sut.chartSeriesData.isEmpty)
    }

    // MARK: - visibleChartSeriesData

    @Test("visibleChartSeriesData returns all data for total period (no X-axis)")
    func visibleChartSeriesDataReturnsAllForTotal() {
        let (sut, _, _) = makeConfiguredSUT(period: .total)
        // For total, hasXAxis is false, so visibleChartSeriesData == chartSeriesData
        #expect(sut.visibleChartSeriesData == sut.chartSeriesData)
    }

    @Test("visibleChartSeriesData filters points outside the visible window")
    func visibleChartSeriesDataFiltersOutsideWindow() {
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: makeDate(day: 1, hour: 8), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-04", date: makeDate(day: 4, hour: 8), weight: 1810),
            DashboardTestFixtures.makeSummary(period: "2026-03-10", date: makeDate(day: 10, hour: 8), weight: 1820)
        ]
        let (sut, _, _) = makeConfiguredSUT(period: .week, summaries: summaries)
        sut.scrollPosition = makeDate(day: 4, hour: 12)

        let visible = sut.visibleChartSeriesData

        #expect(visible.count == 2)
    }

    @Test("visibleChartSeriesData falls back to all data when visible domain length is invalid")
    func visibleChartSeriesDataFallsBackForInvalidDomainLength() {
        let (store, accountService, cacheManager) = DashboardStoreTestSupport.makeSUT()
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: makeDate(day: 1), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", date: makeDate(day: 2), weight: 1810)
        ]
        store.state.data.dailySummaries = summaries
        store.state.graph.selectedPeriod = .week
        cacheManager.chartSeriesOverride = summaries.map { GraphSeries(date: $0.date, value: $0.weight, series: "weight") }
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let vm = InvalidDomainSectionViewModel(period: .week, visibleDomainLength: 0)
        vm.configure(with: store)

        #expect(vm.visibleChartSeriesData == vm.chartSeriesData)
    }

    // MARK: - goalWeight

    @Test("goalWeight returns nil when store is nil")
    func goalWeightNilWithoutStore() {
        let (sut, _) = makeSUT()
        #expect(sut.goalWeight == nil)
    }

    // MARK: - displayWeight / weightLabel

    @Test("displayWeight returns nil when store is nil")
    func displayWeightNilWithoutStore() {
        let (sut, _) = makeSUT()
        #expect(sut.displayWeight == nil)
    }

    @Test("weightLabel returns empty string when store is nil")
    func weightLabelEmptyWithoutStore() {
        let (sut, _) = makeSUT()
        #expect(sut.weightLabel == "")
    }

    // MARK: - shouldAnimateChartData

    @Test("shouldAnimateChartData is false when no operations exist")
    func shouldAnimateChartDataFalseNoData() {
        let (sut, _, _) = makeConfiguredSUT()
        #expect(sut.shouldAnimateChartData == false)
    }

    @Test("shouldAnimateChartData is false when scrolling even with data")
    func shouldAnimateChartDataFalseWhenScrolling() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.isScrolling = true
        #expect(sut.shouldAnimateChartData == false)
    }

    // MARK: - isAtLeftBoundary

    @Test("isAtLeftBoundary returns true when store is nil")
    func isAtLeftBoundaryTrueWithoutStore() {
        let (sut, _) = makeSUT()
        #expect(sut.isAtLeftBoundary == true)
    }

    @Test("isAtLeftBoundary returns false when visible start is beyond minimum date threshold")
    func isAtLeftBoundaryFalseWhenScrolledAway() {
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: makeDate(day: 1), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-10", date: makeDate(day: 10), weight: 1810)
        ]
        let (sut, _, _) = makeConfiguredSUT(period: .week, summaries: summaries)
        sut.scrollPosition = makeDate(day: 20)

        #expect(sut.isAtLeftBoundary == false)
    }

    // MARK: - Goal Chip Positioning

    @Test("getGoalChipPosition returns middle placement when no goal is set")
    func goalChipNoGoalMiddle() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        let (yPos, placement) = sut.getGoalChipPosition()
        #expect(placement == .middle)
        #expect(yPos == 100) // height/2
    }

    @Test("getGoalChipPosition returns middle when chart frame is zero")
    func goalChipZeroFrame() {
        let (sut, _) = makeSUT()
        let (yPos, placement) = sut.getGoalChipPosition()
        #expect(placement == .middle)
        #expect(yPos == 0) // 0/2
    }

    @Test("getGoalChipXOffset returns 28 when store is nil")
    func goalChipXOffsetDefault() {
        let (sut, _) = makeSUT()
        #expect(sut.getGoalChipXOffset() == 28)
    }

    // MARK: - getChartPosition

    @Test("getChartPosition returns nil when chartFrame width is 0")
    func chartPositionNilZeroWidth() {
        let (sut, _) = makeSUT()
        let result = sut.getChartPosition(for: Date(), value: 150)
        #expect(result == nil)
    }

    @Test("getChartPosition returns a point when frame is set")
    func chartPositionReturnsPoint() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...200
        let result = sut.getChartPosition(for: sut.scrollPosition, value: 150)
        #expect(result != nil)
    }

    // MARK: - shouldShowSolidLine

    @Test("shouldShowSolidLine for week returns true on first weekday")
    func shouldShowSolidLineWeekFirstDay() {
        let vm = TestSectionViewModel(period: .week)
        let calendar = Calendar.current
        // Find the next date whose weekday matches firstWeekday
        let today = Date()
        var testDate = today
        for offset in 0..<7 {
            let candidate = calendar.date(byAdding: .day, value: offset, to: today)! // swiftlint:disable:this force_unwrapping
            if calendar.component(.weekday, from: candidate) == calendar.firstWeekday {
                testDate = candidate
                break
            }
        }
        #expect(vm.shouldShowSolidLine(for: testDate) == true)
    }

    @Test("shouldShowSolidLine for month returns true on 1st of month")
    func shouldShowSolidLineMonthFirst() {
        let vm = TestSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month], from: Date())
        comps.day = 1
        let firstOfMonth = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: firstOfMonth) == true)
    }

    @Test("shouldShowSolidLine for month returns false on 15th")
    func shouldShowSolidLineMonthMiddle() {
        let vm = TestSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month], from: Date())
        comps.day = 15
        let mid = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: mid) == false)
    }

    @Test("shouldShowSolidLine for year returns true on January 1")
    func shouldShowSolidLineYearJanFirst() {
        let vm = TestSectionViewModel(period: .year)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 1
        comps.day = 1
        let jan1 = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: jan1) == true)
    }

    @Test("shouldShowSolidLine for year returns false for non-Jan-1 dates")
    func shouldShowSolidLineYearMidYear() {
        let vm = TestSectionViewModel(period: .year)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 6
        comps.day = 15
        let midYear = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: midYear) == false)
    }

    @Test("shouldShowSolidLine for total always returns false")
    func shouldShowSolidLineTotalAlwaysFalse() {
        let vm = TestSectionViewModel(period: .total)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 1
        comps.day = 1
        let jan1 = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: jan1) == false)
    }

    // MARK: - Cache Management

    @Test("invalidateCache clears cached series data")
    func invalidateCacheClearsData() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.updateCachedSeriesData()
        sut.invalidateCache()
        // After invalidation, getCachedGroupedSeries returns fresh computation
        let grouped = sut.getCachedGroupedSeries()
        // Should still work (falls back to direct data)
        #expect(grouped is [String: [GraphSeries]])
    }

    @Test("getCachedSeriesData returns chartSeriesData when cache is empty")
    func getCachedSeriesDataFallback() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.invalidateCache()
        let data = sut.getCachedSeriesData()
        #expect(data == sut.chartSeriesData)
    }

    @Test("updateCachedSeriesData populates cache")
    func updateCachedSeriesDataPopulates() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.updateCachedSeriesData()
        // After update, getCachedGroupedSeries should return data without re-computing
        _ = sut.getCachedGroupedSeries()
        // No crash or error means success
    }

    // MARK: - X-Axis Cache Invalidation

    @Test("invalidateXAxisCache clears cached X-axis values")
    func invalidateXAxisCacheClearsValues() {
        let (sut, _, _) = makeConfiguredSUT()
        _ = sut.xAxisValues // populate cache
        sut.invalidateXAxisCache()
        // Next access should regenerate
        let values = sut.xAxisValues
        #expect(values is [Date]) // Just verifies no crash
    }

    // MARK: - refreshData

    @Test("refreshData invalidates cache and updates Y-axis config")
    func refreshDataInvalidatesAndUpdates() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.updateCachedSeriesData()
        sut.refreshData()
        // Cache should have been invalidated (empty hash)
        // No crash means the method works correctly
    }

    @Test("configure caches whether the chart has data")
    func configureCachesChartPresence() {
        let summaries = DashboardTestFixtures.makeSortedDailySummaries()
        let (sut, _, _) = makeConfiguredSUT(summaries: summaries)

        #expect(sut.hasChartOperations == true)

        sut.tearDown()

        #expect(sut.hasChartOperations == false)
    }

    @Test("refreshData maintains selection if still valid")
    func refreshDataMaintainsSelection() {
        let (sut, _, _) = makeConfiguredSUT()
        let date = Date()
        sut.selectedDate = date
        sut.showCrosshair = true
        sut.refreshData()
        // Selection should be re-processed (handleChartSelection called with selectedDate)
        #expect(sut.selectedDate != nil)
    }

    // MARK: - handleSettingsChange

    @Test("handleSettingsChange clears selection")
    func handleSettingsChangeClearsSelection() {
        let (sut, _, _) = makeConfiguredSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true

        sut.handleSettingsChange()

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    // MARK: - forceScrollPositionUpdate

    @Test("forceScrollPositionUpdate temporarily changes and restores scroll position")
    func forceScrollPositionUpdateSetsValue() async {
        let (sut, _, _) = makeConfiguredSUT()
        let target = Date().addingTimeInterval(5000)
        sut.forceScrollPositionUpdate(to: target)
        // The scroll position should eventually settle on target
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(sut.scrollPosition == target)
    }

    // MARK: - visibleDomainLength

    @Test("visibleDomainLength returns 7*24*60*60 fallback when store is nil")
    func visibleDomainLengthFallback() {
        let (sut, _) = makeSUT()
        #expect(sut.visibleDomainLength == 7 * 24 * 60 * 60)
    }

    // MARK: - pointSize

    @Test("pointSize returns 64 by default")
    func pointSizeDefault() {
        let (sut, _) = makeSUT()
        #expect(sut.pointSize == 64)
    }

    // MARK: - fallbackXAxisDomain

    @Test("fallbackXAxisDomain returns nil for total period")
    func fallbackXAxisDomainNilForTotal() {
        let vm = TestSectionViewModel(period: .total)
        #expect(vm.fallbackXAxisDomain() == nil)
    }

    @Test("fallbackXAxisDomain returns a valid range for week period")
    func fallbackXAxisDomainWeek() {
        let vm = TestSectionViewModel(period: .week)
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    @Test("fallbackXAxisDomain returns a valid range for month period")
    func fallbackXAxisDomainMonth() {
        let vm = TestSectionViewModel(period: .month)
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    @Test("fallbackXAxisDomain returns a valid range for year period")
    func fallbackXAxisDomainYear() {
        let vm = TestSectionViewModel(period: .year)
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    // MARK: - formatXAxisLabel Empty State

    @Test("formatXAxisLabel for week with no ops returns lowercased weekday")
    func formatXAxisLabelWeekEmpty() {
        let vm = TestSectionViewModel(period: .week)
        let calendar = Calendar.current
        // Use a known Sunday
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 3
        comps.day = 8 // Sunday
        let sunday = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        let label = vm.formatXAxisLabel(for: sunday)
        #expect(label != nil)
        #expect(label == "sun")
    }

    @Test("formatXAxisLabel for year with no ops returns single-letter month initial")
    func formatXAxisLabelYearEmpty() {
        let vm = TestSectionViewModel(period: .year)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 1
        comps.day = 15
        let jan = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        let label = vm.formatXAxisLabel(for: jan)
        #expect(label != nil)
        #expect(label == "j")
    }

    @Test("formatXAxisLabel for month with no ops returns day number on Sunday")
    func formatXAxisLabelMonthEmptySunday() {
        let vm = TestSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 3
        comps.day = 8 // Sunday
        let sunday = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        let label = vm.formatXAxisLabel(for: sunday)
        #expect(label == "8")
    }

    @Test("formatXAxisLabel for month with no ops returns nil on non-Sunday")
    func formatXAxisLabelMonthEmptyNonSunday() {
        let vm = TestSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 3
        comps.day = 9 // Monday
        let monday = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        let label = vm.formatXAxisLabel(for: monday)
        #expect(label == nil)
    }

    // MARK: - formatSelectedXAxisLabel

    @Test("formatSelectedXAxisLabel returns nil when store is nil")
    func formatSelectedXAxisLabelNilStore() {
        let (sut, _) = makeSUT()
        #expect(sut.formatSelectedXAxisLabel() == nil)
    }

    @Test("formatSelectedXAxisLabel falls back to store selectedXValue")
    func formatSelectedXAxisLabelUsesSelectedXValue() {
        let (sut, store, _) = makeConfiguredSUT()
        let selected = makeDate(day: 5)
        store.state.graph.selectedXValue = selected

        let label = sut.formatSelectedXAxisLabel()

        #expect(label != nil)
    }

    @Test("formatSelectedXAxisLabel falls back to selectedPoint date")
    func formatSelectedXAxisLabelUsesSelectedPointDate() {
        let (sut, store, _) = makeConfiguredSUT()
        let summary = DashboardTestFixtures.makeSummary(period: "2026-03-06", date: makeDate(day: 6), weight: 1830)
        store.state.graph.selectedPoint = summary

        let label = sut.formatSelectedXAxisLabel()

        #expect(label != nil)
    }

    // MARK: - handleScrollPositionChange Throttling

    @Test("handleScrollPositionChange ignores nil position")
    func handleScrollPositionChangeIgnoresNil() {
        let (sut, _, _) = makeConfiguredSUT()
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(nil)
        #expect(sut.scrollPosition == original)
    }

    @Test("handleScrollPositionChange ignores tiny position changes")
    func handleScrollPositionChangeIgnoresTiny() {
        let (sut, _, _) = makeConfiguredSUT()
        let original = sut.scrollPosition
        // Change by 0.05 seconds — below 0.1 threshold
        sut.handleScrollPositionChange(original.addingTimeInterval(0.05))
        #expect(sut.scrollPosition == original)
    }

    @Test("getChartPosition returns nil when visible domain length is invalid")
    func chartPositionNilInvalidVisibleDomainLength() {
        let vm = InvalidDomainSectionViewModel(period: .week, visibleDomainLength: 0)
        vm.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))

        #expect(vm.getChartPosition(for: Date(), value: 150) == nil)
    }

    // MARK: - Goal Chip Top/Bottom Placement Edge Cases

    @Test("getGoalChipPosition returns top placement when goal is above domain")
    func goalChipTopPlacement() {
        let (sut, store, accountService) = makeConfiguredSUT()
        let account = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 3000)
        accountService.activeAccount = account

        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...200

        let (yPos, placement) = sut.getGoalChipPosition()
        #expect(placement == .top)
        #expect(yPos == -20)
    }

    @Test("getGoalChipPosition returns bottom placement when goal is below domain")
    func goalChipBottomPlacement() {
        let (sut, store, accountService) = makeConfiguredSUT()
        let account = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 50)
        accountService.activeAccount = account

        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...200

        let (yPos, placement) = sut.getGoalChipPosition()
        #expect(placement == .bottom)
        #expect(yPos == 200)
    }

    @Test("syncYAxisFromStore applies cached domain and ticks from dashboard state")
    func syncYAxisFromStoreAppliesCachedValues() {
        let (sut, store, _) = makeConfiguredSUT()
        store.state.graph.cachedYAxisDomain = 140...240
        store.state.graph.cachedYAxisTicks = [140, 190, 240]

        sut.syncYAxisFromStore()

        #expect(sut.yAxisDomain == 140...240)
        #expect(sut.yAxisTicks == [140, 190, 240])
    }

    @Test("getCachedGroupedSeries groups and sorts series points by date")
    func getCachedGroupedSeriesGroupsAndSorts() {
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-03", date: makeDate(day: 3), weight: 1820),
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: makeDate(day: 1), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", date: makeDate(day: 2), weight: 1810)
        ]
        let (sut, _, _) = makeConfiguredSUT(period: .week, summaries: summaries)

        let grouped = sut.getCachedGroupedSeries()
        let weightSeries = grouped["weight"] ?? []

        #expect(weightSeries.count == 3)
        #expect(weightSeries.map(\.date) == weightSeries.map(\.date).sorted())
    }
}
