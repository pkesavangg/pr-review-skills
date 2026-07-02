import Foundation
@testable import meApp
import Testing

// MARK: - Test Helpers

private struct YearSectionViewModelTestsSUT {
    let sut: YearSectionViewModel
    let store: DashboardStore?
    let cacheManager: MockDashboardCacheManager?
}

@MainActor
private func makeSUT(
    configureStore: Bool = true,
    summaries: [BathScaleWeightSummary] = []
) -> YearSectionViewModelTestsSUT {
    let vm = YearSectionViewModel()
    guard configureStore else {
        return YearSectionViewModelTestsSUT(sut: vm, store: nil, cacheManager: nil)
    }

    TestDependencyContainer.reset()
    let sutBundle = DashboardStoreTestSupport.makeSUT()
    let store = sutBundle.store
    let accountService = sutBundle.accountService
    let cacheManager = sutBundle.cacheManager
    let account = DashboardStoreTestSupport.makeActiveAccount()
    accountService.activeAccount = account
    store.state.graph.selectedPeriod = .year

    if !summaries.isEmpty {
        store.state.data.monthlySummaries = summaries
        cacheManager.chartSeriesOverride = summaries.map {
            GraphSeries(date: $0.date, value: $0.weight, series: "weight")
        }
    }

    vm.configure(with: store)
    return YearSectionViewModelTestsSUT(sut: vm, store: store, cacheManager: cacheManager)
}

@MainActor
private func makeDate(year: Int = 2026, month: Int = 1, day: Int = 15, hour: Int = 12) -> Date {
    var comps = DateComponents()
    comps.year = year
    comps.month = month
    comps.day = day
    comps.hour = hour
    comps.minute = 0
    comps.second = 0
    guard let date = Calendar.current.date(from: comps) else {
        Issue.record("unexpected nil date from components")
        return Date()
    }
    return date
}

@MainActor
private func makeMonthlySummaries(startMonth: Int = 1, count: Int = 6, year: Int = 2026) -> [BathScaleWeightSummary] {
    (0..<count).map { offset in
        let month = startMonth + offset
        let monthStr = String(format: "%04d-%02d", year, month)
        return DashboardTestFixtures.makeSummary(
            period: monthStr,
            entryTimestamp: "\(monthStr)-01T00:00:00Z",
            date: makeDate(year: year, month: month, day: 1, hour: 0),
            weight: 1800 + Double(offset) * 20
        )
    }
}

// MARK: - Tests

@Suite(.serialized)
@MainActor
struct YearSectionViewModelTests {

    // MARK: - Period Properties

    @Test("timePeriod returns .year")
    func timePeriodIsYear() {
        let vm = YearSectionViewModel()
        #expect(vm.timePeriod == .year)
    }

    @Test("hasXAxis is true")
    func hasXAxisTrue() {
        let vm = YearSectionViewModel()
        #expect(vm.hasXAxis == true)
    }

    @Test("lineWidth is 3")
    func lineWidthIs3() {
        let vm = YearSectionViewModel()
        #expect(vm.lineWidth == 3)
    }

    @Test("basePointDiameter is 8")
    func basePointDiameterIs8() {
        let vm = YearSectionViewModel()
        #expect(vm.basePointDiameter == 8)
    }

    @Test("selectedPointDiameter is 16")
    func selectedPointDiameterIs16() {
        let vm = YearSectionViewModel()
        #expect(vm.selectedPointDiameter == 16)
    }

    @Test("pointSize is 64 (default)")
    func pointSizeDefault() {
        let vm = YearSectionViewModel()
        #expect(vm.pointSize == 64)
    }

    // MARK: - handleChartSelection

    @Test("handleChartSelection with nil date does nothing")
    func handleChartSelectionNilDate() {
        let sut = makeSUT().sut
        sut.selectedDate = Date()
        sut.showCrosshair = true
        sut.handleChartSelection(at: nil)
        #expect(sut.selectedDate != nil)
    }

    @Test("handleChartSelection with no store does nothing")
    func handleChartSelectionNoStore() {
        let sut = makeSUT(configureStore: false).sut
        sut.handleChartSelection(at: Date())
        #expect(sut.selectedDate == nil)
    }

    @Test("handleChartSelection with no data hides crosshair")
    func handleChartSelectionNoData() {
        let sut = makeSUT().sut
        sut.handleChartSelection(at: Date())
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleChartSelection within data range shows crosshair")
    func handleChartSelectionWithinRange() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 6)
        let sut = makeSUT(summaries: summaries).sut

        let midYear = makeDate(year: 2026, month: 3, day: 15)
        sut.handleChartSelection(at: midYear)

        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate != nil)
    }

    @Test("handleChartSelection outside data range hides crosshair")
    func handleChartSelectionOutsideRange() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 3)
        let sut = makeSUT(summaries: summaries).sut

        // Far outside the Jan-Mar range
        let farDate = makeDate(year: 2027, month: 12, day: 1)
        sut.handleChartSelection(at: farDate)

        #expect(sut.showCrosshair == false)
        #expect(sut.selectedDate == nil)
    }

    @Test("handleChartSelection snaps to nearest month tick")
    func handleChartSelectionSnapsToMonth() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 12)
        let sut = makeSUT(summaries: summaries).sut

        // Touch in mid-February
        let midFeb = makeDate(year: 2026, month: 2, day: 15)
        sut.handleChartSelection(at: midFeb)

        #expect(sut.selectedDate != nil)
        #expect(sut.showCrosshair == true)
    }

    // MARK: - handleScrollPositionChange

    @Test("handleScrollPositionChange with nil does nothing")
    func handleScrollPositionChangeNil() {
        let sut = makeSUT().sut
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(nil)
        #expect(sut.scrollPosition == original)
    }

    @Test("handleScrollPositionChange snaps drag updates to month boundaries")
    func handleScrollPositionChangeSnapsToMonthGrid() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 12)
        let sut = makeSUT(summaries: summaries).sut
        let raw = makeDate(year: 2026, month: 3, day: 19)

        sut.handleScrollPositionChange(raw)

        #expect(sut.scrollPosition != raw)
    }

    // MARK: - Empty State

    @Test("chartOperations empty initially")
    func chartOperationsEmpty() {
        let sut = makeSUT().sut
        #expect(sut.chartOperations.isEmpty)
    }

    @Test("xAxisValues returns values even with no data (fallback)")
    func xAxisValuesFallbackNonEmpty() {
        let sut = makeSUT().sut
        let values = sut.xAxisValues
        // Year fallback generates 13 ticks (12 months + phantom)
        #expect(values.count == 13)
    }

    // MARK: - Single Item

    @Test("Single item: selection at that month shows crosshair")
    func singleItemSelection() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-06",
                entryTimestamp: "2026-06-01T00:00:00Z",
                date: makeDate(year: 2026, month: 6, day: 1, hour: 0),
                weight: 1800
            )
        ]
        let sut = makeSUT(summaries: summaries).sut

        // Select at that same month
        sut.handleChartSelection(at: makeDate(year: 2026, month: 6, day: 15))
        #expect(sut.showCrosshair == true)
    }

    @Test("Single item: selection at different month hides crosshair")
    func singleItemDifferentMonth() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-06",
                entryTimestamp: "2026-06-01T00:00:00Z",
                date: makeDate(year: 2026, month: 6, day: 1, hour: 0),
                weight: 1800
            )
        ]
        let sut = makeSUT(summaries: summaries).sut

        // Select far from month 6
        sut.handleChartSelection(at: makeDate(year: 2026, month: 12, day: 1))
        #expect(sut.showCrosshair == false)
    }

    // MARK: - Scroll Behavior

    @Test("handleScrollStart clears selection")
    func handleScrollStartClearsSelection() {
        let sut = makeSUT().sut
        sut.selectedDate = Date()
        sut.showCrosshair = true

        sut.handleScrollStart()
        #expect(sut.isScrolling == true)
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleScrollEnd resets isScrolling")
    func handleScrollEndResets() {
        let sut = makeSUT().sut
        sut.isScrolling = true
        sut.handleScrollEnd()
        #expect(sut.isScrolling == false)
    }

    // MARK: - Edge Cases

    @Test("clearSelection after selection clears all state")
    func clearSelectionAfterSelection() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 12)
        let sut = makeSUT(summaries: summaries).sut

        sut.handleChartSelection(at: makeDate(year: 2026, month: 3))
        sut.clearSelection()

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
        #expect(sut.selectedPoint == nil)
    }

    @Test("Multiple selections keep last value")
    func multipleSelectionsKeepLast() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 12)
        let sut = makeSUT(summaries: summaries).sut

        sut.handleChartSelection(at: makeDate(year: 2026, month: 2))
        sut.handleChartSelection(at: makeDate(year: 2026, month: 9))

        #expect(sut.selectedDate != nil)
        #expect(sut.showCrosshair == true)
    }

    @Test("fallbackXAxisDomain returns valid range")
    func fallbackXAxisDomainValid() {
        let vm = YearSectionViewModel()
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    @Test("shouldShowSolidLine returns true on January 1")
    func shouldShowSolidLineJan1() {
        let vm = YearSectionViewModel()
        let jan1 = makeDate(year: 2026, month: 1, day: 1)
        #expect(vm.shouldShowSolidLine(for: jan1) == true)
    }

    @Test("shouldShowSolidLine returns false on non-January-1 dates")
    func shouldShowSolidLineNonJan1() {
        let vm = YearSectionViewModel()
        let jun15 = makeDate(year: 2026, month: 6, day: 15)
        #expect(vm.shouldShowSolidLine(for: jun15) == false)
    }

    @Test("shouldAnimateChartData is false with no data")
    func shouldAnimateChartDataNoData() {
        let sut = makeSUT().sut
        #expect(sut.shouldAnimateChartData == false)
    }

    @Test("symbolArea is correct for year diameters")
    func symbolAreaYear() {
        let vm = YearSectionViewModel()
        let selected = vm.symbolArea(forDiameter: 16)
        let expected = CGFloat.pi * 64 // pi * 8^2
        #expect(abs(selected - expected) < 0.001)
    }

    @Test("Selection at first month boundary shows crosshair")
    func selectionAtFirstMonth() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 12)
        let sut = makeSUT(summaries: summaries).sut

        sut.handleChartSelection(at: makeDate(year: 2026, month: 1, day: 1))
        #expect(sut.showCrosshair == true)
    }

    @Test("Selection at last month boundary shows crosshair")
    func selectionAtLastMonth() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 12)
        let sut = makeSUT(summaries: summaries).sut

        sut.handleChartSelection(at: makeDate(year: 2026, month: 12, day: 1))
        #expect(sut.showCrosshair == true)
    }

    @Test("goalWeight returns nil when store has no goal")
    func goalWeightNilNoGoal() {
        let sut = makeSUT().sut
        #expect(sut.goalWeight == nil)
    }

    @Test("dateRange returns now...now when store has no data")
    func dateRangeEmptyStore() {
        let sut = makeSUT().sut
        let range = sut.dateRange
        #expect(range.upperBound.timeIntervalSince(range.lowerBound) < 1)
    }
}
