import Foundation
@testable import meApp
import Testing

// MARK: - Concrete Test Subclass

/// Concrete subclass exposing `.week` as the default period for testing BaseSectionViewModel behavior.
/// The base class declares `timePeriod` as `fatalError`; this wrapper makes it testable.
@MainActor
class BaseSectionVMTestsSectionViewModel: BaseSectionViewModel {
    private let _timePeriod: TimePeriod
    init(period: TimePeriod = .week) {
        _timePeriod = period
        super.init()
    }
    override var timePeriod: TimePeriod { _timePeriod }
}

@MainActor
final class BaseSectionVMTestsInvalidDomainViewModel: BaseSectionVMTestsSectionViewModel {
    private let overriddenDomainLength: TimeInterval

    init(period: TimePeriod = .week, visibleDomainLength: TimeInterval) {
        self.overriddenDomainLength = visibleDomainLength
        super.init(period: period)
    }

    override var visibleDomainLength: TimeInterval { overriddenDomainLength }
}

// MARK: - Test Helpers

@MainActor
func baseSectionVMTestsMakeSUT(
    period: TimePeriod = .week,
    configureStore: Bool = false
) -> (sut: BaseSectionVMTestsSectionViewModel, store: DashboardStore?) {
    let vm = BaseSectionVMTestsSectionViewModel(period: period)
    if configureStore {
        let store = DashboardStoreTestSupport.makeSUT().store
        vm.configure(with: store)
        return (vm, store)
    }
    return (vm, nil)
}

@MainActor
// swiftlint:disable large_tuple
func baseSectionVMTestsMakeConfiguredSUT(
    period: TimePeriod = .week,
    summaries: [BathScaleWeightSummary] = []
) -> (sut: BaseSectionVMTestsSectionViewModel, store: DashboardStore, accountService: AccountService) {
    // swiftlint:enable large_tuple
    let sutBundle = DashboardStoreTestSupport.makeSUT()
    let store = sutBundle.store
    let accountService = sutBundle.accountService
    let cacheManager = sutBundle.cacheManager

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

    let vm = BaseSectionVMTestsSectionViewModel(period: period)
    vm.configure(with: store)
    return (vm, store, accountService)
}

@MainActor
func baseSectionVMTestsMakeDate(year: Int = 2026, month: Int = 3, day: Int = 1, hour: Int = 12) -> Date {
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
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.selectedPoint == nil)
    }

    @Test("Initial state: selectedDate is nil")
    func initialSelectedDateIsNil() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.selectedDate == nil)
    }

    @Test("Initial state: showCrosshair is false")
    func initialShowCrosshairIsFalse() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.showCrosshair == false)
    }

    @Test("Initial state: isScrolling is false")
    func initialIsScrollingIsFalse() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.isScrolling == false)
    }

    @Test("Initial state: yAxisDomain default is 0...100")
    func initialYAxisDomainDefault() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.yAxisDomain == 0...100)
    }

    @Test("Initial state: yAxisTicks is empty")
    func initialYAxisTicksEmpty() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.yAxisTicks.isEmpty)
    }

    @Test("Initial state: chartFrame is zero")
    func initialChartFrameIsZero() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.chartFrame == .zero)
    }

    @Test("Initial state: dashboardStore is nil before configure")
    func initialDashboardStoreIsNil() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.dashboardStore == nil)
    }

    // MARK: - timePeriod Override

    @Test("timePeriod returns the injected period", arguments: [TimePeriod.week, .month, .year, .total])
    func timePeriodReturnsInjected(period: TimePeriod) {
        let vm = BaseSectionVMTestsSectionViewModel(period: period)
        #expect(vm.timePeriod == period)
    }

    @Test("adjustedLabelTicks excludes trailing phantom tick for week")
    func adjustedLabelTicksDropsTrailingWeekTick() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(period: .week)
        sut.scrollPosition = baseSectionVMTestsMakeDate(year: 2026, month: 3, day: 1)

        let ticks = sut.xAxisValues
        #expect(ticks.count > 1)
        #expect(sut.adjustedLabelTicks == Array(ticks.dropLast()))
    }

    @Test("gridTicks for month exclude next-month boundary tick")
    func gridTicksForMonthExcludeNextMonthBoundary() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(period: .month)
        sut.scrollPosition = baseSectionVMTestsMakeDate(year: 2026, month: 3, day: 1, hour: 0)

        let calendar = Calendar.current
        let tickComponents = sut.gridTicks.map { calendar.dateComponents([.month, .day], from: $0) }

        #expect(tickComponents.map(\.day) == [1, 8, 15, 22, 29])
        #expect(!tickComponents.contains { $0.month == 4 && $0.day == 1 })
    }

    // MARK: - hasXAxis

    @Test("hasXAxis is true for week/month/year", arguments: [TimePeriod.week, .month, .year])
    func hasXAxisTrueForScrollable(period: TimePeriod) {
        let vm = BaseSectionVMTestsSectionViewModel(period: period)
        #expect(vm.hasXAxis == true)
    }

    @Test("hasXAxis is false for total")
    func hasXAxisFalseForTotal() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .total)
        #expect(vm.hasXAxis == false)
    }

    // MARK: - Stroke & Point Sizing

    @Test("lineWidth is 3 for scrollable periods", arguments: [TimePeriod.week, .month, .year])
    func lineWidthForScrollable(period: TimePeriod) {
        let vm = BaseSectionVMTestsSectionViewModel(period: period)
        #expect(vm.lineWidth == 3)
    }

    @Test("lineWidth is 2 for total")
    func lineWidthForTotal() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .total)
        #expect(vm.lineWidth == 2)
    }

    @Test("basePointDiameter is 8 for scrollable, 4 for total")
    func basePointDiameter() {
        let scrollable = BaseSectionVMTestsSectionViewModel(period: .week)
        #expect(scrollable.basePointDiameter == 8)
        let total = BaseSectionVMTestsSectionViewModel(period: .total)
        #expect(total.basePointDiameter == 4)
    }

    @Test("selectedPointDiameter is 16 for scrollable, 8 for total")
    func selectedPointDiameter() {
        let scrollable = BaseSectionVMTestsSectionViewModel(period: .month)
        #expect(scrollable.selectedPointDiameter == 16)
        let total = BaseSectionVMTestsSectionViewModel(period: .total)
        #expect(total.selectedPointDiameter == 8)
    }

    @Test("basePointArea matches pi*r^2 for diameter 8")
    func basePointAreaCalculation() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        let expected = CGFloat.pi * 4 * 4 // radius=4
        #expect(abs(vm.basePointArea - expected) < 0.001)
    }

    @Test("selectedPointArea matches pi*r^2 for diameter 16")
    func selectedPointAreaCalculation() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        let expected = CGFloat.pi * 8 * 8 // radius=8
        #expect(abs(vm.selectedPointArea - expected) < 0.001)
    }

    @Test("pointArea returns selectedPointArea when isSelected is true")
    func pointAreaSelected() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        #expect(vm.pointArea(isSelected: true) == vm.selectedPointArea)
    }

    @Test("pointArea returns basePointArea when isSelected is false")
    func pointAreaNotSelected() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        #expect(vm.pointArea(isSelected: false) == vm.basePointArea)
    }

    @Test("symbolArea converts diameter to circle area correctly")
    func symbolAreaConversion() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        let area = vm.symbolArea(forDiameter: 10)
        let expected = CGFloat.pi * 25 // pi * (10/2)^2
        #expect(abs(area - expected) < 0.001)
    }

    @Test("symbolArea for zero diameter returns zero")
    func symbolAreaZeroDiameter() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        #expect(vm.symbolArea(forDiameter: 0) == 0)
    }

    // MARK: - plotXDate (default implementation)

    @Test("plotXDate returns the original date by default")
    func plotXDateReturnsOriginal() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        let date = Date()
        #expect(vm.plotXDate(for: date) == date)
    }

    // MARK: - preferredSelectedDate

    @Test("preferredSelectedDate returns selectedDate")
    func preferredSelectedDateMatchesSelectedDate() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        let date = Date()
        sut.selectedDate = date
        #expect(sut.preferredSelectedDate == date)
    }

    @Test("preferredSelectedDate returns nil when selectedDate is nil")
    func preferredSelectedDateNilWhenNone() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.preferredSelectedDate == nil)
    }

    // MARK: - Selection Management

    @Test("handleChartSelection sets selectedDate and showCrosshair for non-nil date")
    func handleChartSelectionSetsState() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        let date = Date()
        sut.handleChartSelection(at: date)
        #expect(sut.selectedDate == date)
        #expect(sut.showCrosshair == true)
    }

    @Test("handleChartSelection does nothing for nil date")
    func handleChartSelectionNilDate() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
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
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        let cacheManager = sutBundle.cacheManager
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

        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        vm.configure(with: store)

        // Select at exact target — earlier (-3600) is closer than later (+3600) equally but min picks first
        vm.handleChartSelection(at: target)

        // Both are equidistant, .min picks the first one
        #expect(vm.selectedPoint != nil)
    }

    @Test("clearSelection resets selectedPoint, selectedDate, and showCrosshair")
    func clearSelectionResetsAll() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
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
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true

        sut.handleScrollStart()

        #expect(sut.isScrolling == true)
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleScrollEnd sets isScrolling to false")
    func handleScrollEndResetsScrolling() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.isScrolling = true

        sut.handleScrollEnd()

        #expect(sut.isScrolling == false)
    }

    @Test("updateScrollPosition updates scrollPosition value")
    func updateScrollPositionSetsValue() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        let newDate = Date().addingTimeInterval(1000)
        sut.updateScrollPosition(to: newDate)
        #expect(sut.scrollPosition == newDate)
    }

    // MARK: - Chart Frame Management

    @Test("updateChartFrame updates the stored frame")
    func updateChartFrameStoresFrame() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        let frame = CGRect(x: 0, y: 0, width: 300, height: 200)
        sut.updateChartFrame(frame)
        #expect(sut.chartFrame == frame)
    }

    @Test("updateChartFrame with small height change does not recalculate Y-axis")
    func updateChartFrameSmallChangeNoRecalc() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
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
        let (sut, store) = baseSectionVMTestsMakeSUT(configureStore: true)
        #expect(sut.dashboardStore != nil)
        #expect(sut.dashboardStore === store)
    }

    @Test("configure syncs scrollPosition from store for scrollable period")
    func configureSyncsScrollPosition() {
        TestDependencyContainer.reset()
        let store = DashboardStoreTestSupport.makeSUT().store
        let date = Date().addingTimeInterval(-5000)
        store.state.graph.xScrollPosition = date

        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        vm.configure(with: store)

        #expect(vm.scrollPosition == date)
    }

    @Test("configure syncs isScrolling from store")
    func configureSyncsIsScrolling() {
        TestDependencyContainer.reset()
        let store = DashboardStoreTestSupport.makeSUT().store
        store.state.graph.isScrolling = true

        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        vm.configure(with: store)

        #expect(vm.isScrolling == true)
    }

    // MARK: - dateRange

    @Test("dateRange returns now...now when store is nil")
    func dateRangeNilStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        let range = sut.dateRange
        // Both bounds should be very close (same instant)
        #expect(range.upperBound.timeIntervalSince(range.lowerBound) < 1)
    }

    // MARK: - chartOperations

    @Test("chartOperations returns empty when store is nil")
    func chartOperationsEmptyWithoutStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.chartOperations.isEmpty)
    }

    // MARK: - chartSeriesData

    @Test("chartSeriesData returns empty when store is nil")
    func chartSeriesDataEmptyWithoutStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.chartSeriesData.isEmpty)
    }

    // MARK: - visibleChartSeriesData

    @Test("visibleChartSeriesData returns all data for total period (no X-axis)")
    func visibleChartSeriesDataReturnsAllForTotal() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(period: .total)
        // For total, hasXAxis is false, so visibleChartSeriesData == chartSeriesData
        #expect(sut.visibleChartSeriesData == sut.chartSeriesData)
    }

    @Test("visibleChartSeriesData filters points outside the visible window")
    func visibleChartSeriesDataFiltersOutsideWindow() {
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: baseSectionVMTestsMakeDate(day: 1, hour: 8), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-04", date: baseSectionVMTestsMakeDate(day: 4, hour: 8), weight: 1810),
            DashboardTestFixtures.makeSummary(period: "2026-03-10", date: baseSectionVMTestsMakeDate(day: 10, hour: 8), weight: 1820)
        ]
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(period: .week, summaries: summaries)
        sut.scrollPosition = baseSectionVMTestsMakeDate(day: 4, hour: 12)

        let visible = sut.visibleChartSeriesData

        #expect(visible.count == 2)
    }

    @Test("visibleChartSeriesData falls back to all data when visible domain length is invalid")
    func visibleChartSeriesDataFallsBackForInvalidDomainLength() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        let cacheManager = sutBundle.cacheManager
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: baseSectionVMTestsMakeDate(day: 1), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", date: baseSectionVMTestsMakeDate(day: 2), weight: 1810)
        ]
        store.state.data.dailySummaries = summaries
        store.state.graph.selectedPeriod = .week
        cacheManager.chartSeriesOverride = summaries.map { GraphSeries(date: $0.date, value: $0.weight, series: "weight") }
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let vm = BaseSectionVMTestsInvalidDomainViewModel(period: .week, visibleDomainLength: 0)
        vm.configure(with: store)

        #expect(vm.visibleChartSeriesData == vm.chartSeriesData)
    }

    // MARK: - goalWeight

    @Test("goalWeight returns nil when store is nil")
    func goalWeightNilWithoutStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.goalWeight == nil)
    }

    // MARK: - displayWeight / weightLabel

    @Test("displayWeight returns nil when store is nil")
    func displayWeightNilWithoutStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.displayWeight == nil)
    }

    @Test("weightLabel returns empty string when store is nil")
    func weightLabelEmptyWithoutStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.weightLabel.isEmpty)
    }

    // MARK: - shouldAnimateChartData

    @Test("shouldAnimateChartData is false when no operations exist")
    func shouldAnimateChartDataFalseNoData() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        #expect(sut.shouldAnimateChartData == false)
    }

    @Test("shouldAnimateChartData is false when scrolling even with data")
    func shouldAnimateChartDataFalseWhenScrolling() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.isScrolling = true
        #expect(sut.shouldAnimateChartData == false)
    }

    // MARK: - isAtLeftBoundary

    @Test("isAtLeftBoundary returns true when store is nil")
    func isAtLeftBoundaryTrueWithoutStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.isAtLeftBoundary == true)
    }

    @Test("isAtLeftBoundary returns false when visible start is beyond minimum date threshold")
    func isAtLeftBoundaryFalseWhenScrolledAway() {
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: baseSectionVMTestsMakeDate(day: 1), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-10", date: baseSectionVMTestsMakeDate(day: 10), weight: 1810)
        ]
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(period: .week, summaries: summaries)
        sut.scrollPosition = baseSectionVMTestsMakeDate(day: 20)

        #expect(sut.isAtLeftBoundary == false)
    }

    // MARK: - Goal Chip Positioning

    @Test("getGoalChipPosition returns middle placement when no goal is set")
    func goalChipNoGoalMiddle() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        let (yPos, placement) = sut.getGoalChipPosition()
        #expect(placement == .middle)
        #expect(yPos == 100) // height/2
    }

    @Test("getGoalChipPosition returns middle when chart frame is zero")
    func goalChipZeroFrame() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        let (yPos, placement) = sut.getGoalChipPosition()
        #expect(placement == .middle)
        #expect(yPos == 0) // 0/2
    }

    @Test("getGoalChipXOffset returns 28 when store is nil")
    func goalChipXOffsetDefault() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.getGoalChipXOffset() == 28)
    }

    // MARK: - getChartPosition

    @Test("getChartPosition returns nil when chartFrame width is 0")
    func chartPositionNilZeroWidth() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        let result = sut.getChartPosition(for: Date(), value: 150)
        #expect(result == nil)
    }

    @Test("getChartPosition returns a point when frame is set")
    func chartPositionReturnsPoint() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...200
        let result = sut.getChartPosition(for: sut.scrollPosition, value: 150)
        #expect(result != nil)
    }

}
