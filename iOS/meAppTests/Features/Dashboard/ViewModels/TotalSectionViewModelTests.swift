import Foundation
import Testing
@testable import meApp

// MARK: - Test Helpers

@MainActor
private func makeSUT(
    configureStore: Bool = true,
    summaries: [BathScaleWeightSummary] = []
) -> (sut: TotalSectionViewModel, store: DashboardStore?, cacheManager: MockDashboardCacheManager?) {
    let vm = TotalSectionViewModel()
    guard configureStore else { return (vm, nil, nil) }

    TestDependencyContainer.reset()
    let (store, accountService, cacheManager) = DashboardStoreTestSupport.makeSUT()
    let account = DashboardStoreTestSupport.makeActiveAccount()
    accountService.activeAccount = account
    store.state.graph.selectedPeriod = .total

    if !summaries.isEmpty {
        store.state.data.monthlySummaries = summaries
        cacheManager.chartSeriesOverride = summaries.map {
            GraphSeries(date: $0.date, value: $0.weight, series: "weight")
        }
    }

    vm.configure(with: store)
    return (vm, store, cacheManager)
}

@MainActor
private func makeDate(year: Int = 2026, month: Int = 1, day: Int = 1, hour: Int = 12) -> Date {
    var comps = DateComponents()
    comps.year = year
    comps.month = month
    comps.day = day
    comps.hour = hour
    comps.minute = 0
    comps.second = 0
    return Calendar.current.date(from: comps)!
}

@MainActor
private func makeMonthlySummaries(startMonth: Int = 1, count: Int = 12, year: Int = 2026) -> [BathScaleWeightSummary] {
    (0..<count).map { offset in
        let month = startMonth + offset
        let effectiveYear = year + (month - 1) / 12
        let effectiveMonth = ((month - 1) % 12) + 1
        let monthStr = String(format: "%04d-%02d", effectiveYear, effectiveMonth)
        return DashboardTestFixtures.makeSummary(
            period: monthStr,
            entryTimestamp: "\(monthStr)-01T00:00:00Z",
            date: makeDate(year: effectiveYear, month: effectiveMonth, day: 1, hour: 0),
            weight: 1800 + Double(offset) * 10
        )
    }
}

// MARK: - Tests

@Suite(.serialized)
@MainActor
struct TotalSectionViewModelTests {

    // MARK: - Period Properties

    @Test("timePeriod returns .total")
    func timePeriodIsTotal() {
        let vm = TotalSectionViewModel()
        #expect(vm.timePeriod == .total)
    }

    @Test("hasXAxis is false")
    func hasXAxisFalse() {
        let vm = TotalSectionViewModel()
        #expect(vm.hasXAxis == false)
    }

    @Test("lineWidth is 2")
    func lineWidthIs2() {
        let vm = TotalSectionViewModel()
        #expect(vm.lineWidth == 2)
    }

    @Test("basePointDiameter is 4")
    func basePointDiameterIs4() {
        let vm = TotalSectionViewModel()
        #expect(vm.basePointDiameter == 4)
    }

    @Test("selectedPointDiameter is 8")
    func selectedPointDiameterIs8() {
        let vm = TotalSectionViewModel()
        #expect(vm.selectedPointDiameter == 8)
    }

    @Test("pointSize is 16")
    func pointSizeIs16() {
        let vm = TotalSectionViewModel()
        #expect(vm.pointSize == 16)
    }

    @Test("isAtLeftBoundary always true")
    func isAtLeftBoundaryAlwaysTrue() {
        let vm = TotalSectionViewModel()
        #expect(vm.isAtLeftBoundary == true)
    }

    @Test("isAtLeftBoundary true even after configure")
    func isAtLeftBoundaryTrueAfterConfigure() {
        let (sut, _, _) = makeSUT()
        #expect(sut.isAtLeftBoundary == true)
    }

    // MARK: - shouldAnimateChartData

    @Test("shouldAnimateChartData is false when no operations exist")
    func shouldAnimateChartDataFalseNoOps() {
        let (sut, _, _) = makeSUT()
        #expect(sut.shouldAnimateChartData == false)
    }

    // MARK: - handleScrollPositionChange (no-op)

    @Test("handleScrollPositionChange is a no-op")
    func handleScrollPositionChangeNoOp() {
        let (sut, _, _) = makeSUT()
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(Date().addingTimeInterval(1000))
        #expect(sut.scrollPosition == original)
    }

    @Test("handleScrollPositionChange with nil is also a no-op")
    func handleScrollPositionChangeNilNoOp() {
        let (sut, _, _) = makeSUT()
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(nil)
        #expect(sut.scrollPosition == original)
    }

    // MARK: - handleScrollStart / handleScrollEnd (no-ops)

    @Test("handleScrollStart is a no-op — isScrolling stays false")
    func handleScrollStartNoOp() {
        let (sut, _, _) = makeSUT()
        sut.handleScrollStart()
        #expect(sut.isScrolling == false)
    }

    @Test("handleScrollEnd is a no-op")
    func handleScrollEndNoOp() {
        let (sut, _, _) = makeSUT()
        sut.isScrolling = true
        sut.handleScrollEnd()
        #expect(sut.isScrolling == true)
    }

    // MARK: - updateScrollPosition (no-op)

    @Test("updateScrollPosition is a no-op")
    func updateScrollPositionNoOp() {
        let (sut, _, _) = makeSUT()
        let original = sut.scrollPosition
        sut.updateScrollPosition(to: Date().addingTimeInterval(5000))
        #expect(sut.scrollPosition == original)
    }

    // MARK: - handleChartSelection

    @Test("handleChartSelection with nil date does nothing")
    func handleChartSelectionNilDate() {
        let (sut, _, _) = makeSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true
        sut.handleChartSelection(at: nil)
        #expect(sut.selectedDate != nil)
    }

    @Test("handleChartSelection with no operations hides crosshair")
    func handleChartSelectionNoOps() {
        let (sut, _, _) = makeSUT()
        sut.handleChartSelection(at: Date())
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleChartSelection within data range snaps to nearest real point")
    func handleChartSelectionWithinRange() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let near = makeDate(year: 2026, month: 3, day: 10)
        sut.handleChartSelection(at: near)

        #expect(sut.selectedDate != nil)
        #expect(sut.showCrosshair == true)
    }

    @Test("handleChartSelection snaps to nearest data point date")
    func handleChartSelectionSnapsToNearest() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let between = makeDate(year: 2026, month: 2, day: 20)
        sut.handleChartSelection(at: between)

        #expect(sut.selectedDate != nil)
        if let selected = sut.selectedDate {
            let calendar = Calendar.current
            let month = calendar.component(.month, from: selected)
            #expect(month == 2 || month == 3)
        }
    }

    @Test("handleChartSelection far before first data point hides crosshair")
    func handleChartSelectionBeforeFirst() {
        let summaries = makeMonthlySummaries(startMonth: 3, count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let before = makeDate(year: 2025, month: 1, day: 1)
        sut.handleChartSelection(at: before)

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleChartSelection far after last data point hides crosshair")
    func handleChartSelectionAfterLast() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let farAfter = makeDate(year: 2030, month: 12, day: 1)
        sut.handleChartSelection(at: farAfter)

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    // MARK: - dateRange

    @Test("dateRange with no data provides non-zero domain around scrollPosition")
    func dateRangeNoData() {
        let (sut, _, _) = makeSUT()
        let range = sut.dateRange
        let span = range.upperBound.timeIntervalSince(range.lowerBound)
        // Should be 2 * 24 * 60 * 60 = 2 days (halfWindow = 1 day each side)
        #expect(abs(span - 2 * 24 * 60 * 60) < 1)
    }

    @Test("dateRange lower and upper bounds are different even with no data")
    func dateRangeNoDataBoundsDistinct() {
        let (sut, _, _) = makeSUT()
        let range = sut.dateRange
        #expect(range.lowerBound < range.upperBound)
    }

    @Test("dateRange expands around a single point with padding")
    func dateRangeSinglePointExpands() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-06",
                entryTimestamp: "2026-06-01T00:00:00Z",
                date: makeDate(year: 2026, month: 6, day: 1, hour: 0),
                weight: 1800
            )
        ]
        let (sut, _, _) = makeSUT(summaries: summaries)

        let range = sut.dateRange

        #expect(range.lowerBound < summaries[0].date)
        #expect(range.upperBound > summaries[0].date)
    }

    // MARK: - Single Item

    @Test("Single item: handleChartSelection selects the single point")
    func singleItemSelection() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-06",
                entryTimestamp: "2026-06-01T00:00:00Z",
                date: makeDate(year: 2026, month: 6, day: 1, hour: 0),
                weight: 1800
            )
        ]
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 6, day: 1))
        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate != nil)
    }

    @Test("Single item: selection far from point hides crosshair")
    func singleItemFarSelection() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-06",
                entryTimestamp: "2026-06-01T00:00:00Z",
                date: makeDate(year: 2026, month: 6, day: 1, hour: 0),
                weight: 1800
            )
        ]
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2020, month: 1, day: 1))
        #expect(sut.showCrosshair == false)
    }

    // MARK: - getChartPosition

    @Test("getChartPosition returns nil when chartFrame width is 0")
    func getChartPositionNilZeroWidth() {
        let (sut, _, _) = makeSUT()
        #expect(sut.getChartPosition(for: Date(), value: 150) == nil)
    }

    @Test("getChartPosition returns a point when frame is set")
    func getChartPositionReturnsPoint() {
        let (sut, _, _) = makeSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...200

        let point = sut.getChartPosition(for: sut.scrollPosition, value: 150)
        #expect(point != nil)
    }

    @Test("getChartPosition returns midpoint Y when domain range is 0")
    func getChartPositionZeroDomainRange() {
        let (sut, _, _) = makeSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...100

        let point = sut.getChartPosition(for: Date(), value: 100)
        #expect(point != nil)
        if let p = point {
            #expect(p.y == 100) // height/2
        }
    }

    @Test("getChartPosition no X-axis adjustment: uses full chart height")
    func getChartPositionFullHeight() {
        let (sut, _, _) = makeSUT()
        sut.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))
        sut.yAxisDomain = 100...200

        // Value at domain lowerBound should be at bottom (200)
        let bottomPoint = sut.getChartPosition(for: sut.scrollPosition, value: 100)
        // Value at domain upperBound should be at top (0)
        let topPoint = sut.getChartPosition(for: sut.scrollPosition, value: 200)

        #expect(bottomPoint != nil)
        #expect(topPoint != nil)
        if let bp = bottomPoint, let tp = topPoint {
            // Bottom Y should be greater than top Y (chart Y grows downward)
            #expect(bp.y > tp.y)
        }
    }

    // MARK: - configure

    @Test("configure sets dashboardStore")
    func configureSetsStore() {
        let (sut, store, _) = makeSUT()
        #expect(sut.dashboardStore != nil)
        #expect(sut.dashboardStore === store)
    }

    @Test("configure syncs scrollPosition from store")
    func configureSyncsScrollPosition() {
        TestDependencyContainer.reset()
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        let date = Date().addingTimeInterval(-5000)
        store.state.graph.xScrollPosition = date

        let vm = TotalSectionViewModel()
        vm.configure(with: store)

        #expect(vm.scrollPosition == date)
    }

    @Test("configure syncs isScrolling from store")
    func configureSyncsIsScrolling() {
        TestDependencyContainer.reset()
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.graph.isScrolling = true

        let vm = TotalSectionViewModel()
        vm.configure(with: store)

        #expect(vm.isScrolling == true)
    }

    // MARK: - Edge Cases

    @Test("clearSelection resets all state")
    func clearSelectionResetsAll() {
        let summaries = makeMonthlySummaries(count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 3))
        sut.clearSelection()

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
        #expect(sut.selectedPoint == nil)
    }

    @Test("Multiple selections keep last value")
    func multipleSelectionsKeepLast() {
        let summaries = makeMonthlySummaries(count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 2))
        let first = sut.selectedDate

        sut.handleChartSelection(at: makeDate(year: 2026, month: 5))
        let second = sut.selectedDate

        #expect(first != second)
    }

    @Test("fallbackXAxisDomain returns nil for total")
    func fallbackXAxisDomainNil() {
        let vm = TotalSectionViewModel()
        #expect(vm.fallbackXAxisDomain() == nil)
    }

    @Test("shouldShowSolidLine always false for total")
    func shouldShowSolidLineAlwaysFalse() {
        let vm = TotalSectionViewModel()
        let jan1 = makeDate(year: 2026, month: 1, day: 1)
        #expect(vm.shouldShowSolidLine(for: jan1) == false)
    }

    @Test("shouldShowSolidLine false for arbitrary dates")
    func shouldShowSolidLineArbitrary() {
        let vm = TotalSectionViewModel()
        let mid = makeDate(year: 2026, month: 7, day: 15)
        #expect(vm.shouldShowSolidLine(for: mid) == false)
    }

    @Test("xAxisValues is empty for total (no X-axis)")
    func xAxisValuesEmptyForTotal() {
        let (sut, _, _) = makeSUT()
        let values = sut.xAxisValues
        #expect(values.isEmpty)
    }

    @Test("getPointSizeForTotal returns 16")
    func getPointSizeForTotal() {
        let vm = TotalSectionViewModel()
        #expect(vm.getPointSizeForTotal() == 16)
    }

    @Test("basePointArea for total matches pi*r^2 for diameter 4")
    func basePointAreaTotal() {
        let vm = TotalSectionViewModel()
        let expected = CGFloat.pi * 4 // pi * 2^2
        #expect(abs(vm.basePointArea - expected) < 0.001)
    }

    @Test("selectedPointArea for total matches pi*r^2 for diameter 8")
    func selectedPointAreaTotal() {
        let vm = TotalSectionViewModel()
        let expected = CGFloat.pi * 16 // pi * 4^2
        #expect(abs(vm.selectedPointArea - expected) < 0.001)
    }

    @Test("pointArea returns correct areas based on selection")
    func pointAreaTotal() {
        let vm = TotalSectionViewModel()
        #expect(vm.pointArea(isSelected: true) == vm.selectedPointArea)
        #expect(vm.pointArea(isSelected: false) == vm.basePointArea)
    }

    @Test("handleChartSelection with right-edge slack selects last point")
    func handleChartSelectionRightSlack() {
        let summaries = makeMonthlySummaries(startMonth: 1, count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let lastDate = summaries.last!.date
        let slightlyPast = lastDate.addingTimeInterval(5 * 24 * 60 * 60)
        sut.handleChartSelection(at: slightlyPast)

        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate != nil)
    }

    @Test("goalWeight returns nil when store has no goal")
    func goalWeightNilNoGoal() {
        let (sut, _, _) = makeSUT()
        #expect(sut.goalWeight == nil)
    }

    @Test("displayWeight returns nil initially")
    func displayWeightNilInitially() {
        let (sut, _, _) = makeSUT()
        #expect(sut.displayWeight == nil)
    }

    @Test("weightLabel returns empty string initially")
    func weightLabelEmptyInitially() {
        let (sut, _, _) = makeSUT()
        #expect(sut.weightLabel == "")
    }

    @Test("visibleChartSeriesData returns all chartSeriesData for total (no filtering)")
    func visibleChartSeriesDataNoFilter() {
        let summaries = makeMonthlySummaries(count: 3)
        let (sut, _, _) = makeSUT(summaries: summaries)
        // For total, hasXAxis is false, so visibleChartSeriesData == chartSeriesData
        #expect(sut.visibleChartSeriesData == sut.chartSeriesData)
    }

    @Test("chartOperations empty initially")
    func chartOperationsEmpty() {
        let (sut, _, _) = makeSUT()
        #expect(sut.chartOperations.isEmpty)
    }

    @Test("Selection then clearSelection then selection again works")
    func selectionClearReselection() {
        let summaries = makeMonthlySummaries(count: 6)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 2))
        #expect(sut.showCrosshair == true)

        sut.clearSelection()
        #expect(sut.showCrosshair == false)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 4))
        #expect(sut.showCrosshair == true)
    }
}
