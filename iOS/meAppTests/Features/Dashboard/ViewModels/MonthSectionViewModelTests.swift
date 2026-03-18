 import Foundation
import Testing
@testable import meApp

// MARK: - Test Helpers

@MainActor
private func makeSUT(
    configureStore: Bool = true,
    summaries: [BathScaleWeightSummary] = [],
    scrollPosition: Date? = nil
) -> (sut: MonthSectionViewModel, store: DashboardStore?, cacheManager: MockDashboardCacheManager?) {
    let vm = MonthSectionViewModel()
    guard configureStore else { return (vm, nil, nil) }
    let scrollPosition = scrollPosition ?? makeDate(year: 2026, month: 3, day: 15, hour: 12)

    TestDependencyContainer.reset()
    let (store, accountService, cacheManager) = DashboardStoreTestSupport.makeSUT()
    let account = DashboardStoreTestSupport.makeActiveAccount()
    accountService.activeAccount = account
    store.state.graph.selectedPeriod = .month
    store.state.graph.xScrollPosition = scrollPosition
    store.graphManager.state.selectedPeriod = .month
    store.graphManager.state.xScrollPosition = scrollPosition

    if !summaries.isEmpty {
        store.state.data.dailySummaries = summaries
        cacheManager.chartSeriesOverride = summaries.map {
            GraphSeries(date: $0.date, value: $0.weight, series: "weight")
        }
    }

    vm.configure(with: store)
    return (vm, store, cacheManager)
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
    return Calendar.current.date(from: comps)!
}

@MainActor
private func makeMonthSummaries(startDay: Int = 1, count: Int = 15, month: Int = 3, year: Int = 2026) -> [BathScaleWeightSummary] {
    (0..<count).map { offset in
        let day = startDay + offset
        let dayStr = String(format: "%04d-%02d-%02d", year, month, day)
        return DashboardTestFixtures.makeSummary(
            period: dayStr,
            entryTimestamp: "\(dayStr)T08:00:00Z",
            date: makeDate(year: year, month: month, day: day, hour: 8),
            weight: 1800 + Double(offset) * 5
        )
    }
}

@MainActor
private func localNoon(year: Int = 2026, month: Int = 3, day: Int) -> Date {
    makeDate(year: year, month: month, day: day, hour: 12)
}

// MARK: - Tests

@Suite(.serialized)
@MainActor
struct MonthSectionViewModelTests {

    // MARK: - Period Properties

    @Test("timePeriod returns .month")
    func timePeriodIsMonth() {
        let vm = MonthSectionViewModel()
        #expect(vm.timePeriod == .month)
    }

    @Test("hasXAxis is true")
    func hasXAxisTrue() {
        let vm = MonthSectionViewModel()
        #expect(vm.hasXAxis == true)
    }

    @Test("lineWidth is 3")
    func lineWidthIs3() {
        let vm = MonthSectionViewModel()
        #expect(vm.lineWidth == 3)
    }

    @Test("basePointDiameter is 8")
    func basePointDiameterIs8() {
        let vm = MonthSectionViewModel()
        #expect(vm.basePointDiameter == 8)
    }

    @Test("selectedPointDiameter is 16")
    func selectedPointDiameterIs16() {
        let vm = MonthSectionViewModel()
        #expect(vm.selectedPointDiameter == 16)
    }

    // MARK: - plotXDate

    @Test("plotXDate snaps to local noon of the same day")
    func plotXDateSnapsToNoon() {
        let vm = MonthSectionViewModel()
        let morning = makeDate(year: 2026, month: 3, day: 10, hour: 7)
        let result = vm.plotXDate(for: morning)
        let calendar = Calendar.current
        #expect(calendar.component(.hour, from: result) == 12)
        #expect(calendar.component(.minute, from: result) == 0)
        #expect(calendar.component(.day, from: result) == 10)
    }

    @Test("plotXDate preserves day for late evening input")
    func plotXDateLateEvening() {
        let vm = MonthSectionViewModel()
        var comps = DateComponents()
        comps.year = 2026; comps.month = 3; comps.day = 15
        comps.hour = 22; comps.minute = 30
        let evening = Calendar.current.date(from: comps)!
        let result = vm.plotXDate(for: evening)
        let calendar = Calendar.current
        #expect(calendar.component(.hour, from: result) == 12)
        #expect(calendar.component(.day, from: result) == 15)
    }

    @Test("plotXDate preserves day for midnight")
    func plotXDateMidnight() {
        let vm = MonthSectionViewModel()
        let midnight = makeDate(year: 2026, month: 3, day: 20, hour: 0)
        let result = vm.plotXDate(for: midnight)
        let calendar = Calendar.current
        #expect(calendar.component(.hour, from: result) == 12)
        #expect(calendar.component(.day, from: result) == 20)
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

    @Test("handleChartSelection with no store does nothing")
    func handleChartSelectionNoStore() {
        let (sut, _, _) = makeSUT(configureStore: false)
        sut.handleChartSelection(at: Date())
        #expect(sut.selectedDate == nil)
    }

    @Test("handleChartSelection with no data hides crosshair")
    func handleChartSelectionNoData() {
        let (sut, _, _) = makeSUT()
        sut.handleChartSelection(at: Date())
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleChartSelection within data range shows crosshair")
    func handleChartSelectionWithinRange() {
        let summaries = makeMonthSummaries(startDay: 1, count: 15)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let mid = makeDate(year: 2026, month: 3, day: 8, hour: 14)
        sut.handleChartSelection(at: mid)

        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate != nil)
    }

    @Test("handleChartSelection far outside data range hides crosshair")
    func handleChartSelectionFarOutsideRange() {
        let summaries = makeMonthSummaries(startDay: 1, count: 10)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let farFuture = makeDate(year: 2027, month: 6, day: 1)
        sut.handleChartSelection(at: farFuture)

        #expect(sut.showCrosshair == false)
        #expect(sut.selectedDate == nil)
    }

    @Test("handleChartSelection selects nearest point within section")
    func handleChartSelectionNearestInSection() {
        let summaries = makeMonthSummaries(startDay: 1, count: 15)
        let (sut, _, _) = makeSUT(summaries: summaries)

        // Touch near day 5
        let nearDay5 = makeDate(year: 2026, month: 3, day: 5, hour: 10)
        sut.handleChartSelection(at: nearDay5)

        #expect(sut.selectedDate != nil)
        #expect(sut.showCrosshair == true)
    }

    @Test("handleChartSelection before first data point hides crosshair")
    func handleChartSelectionBeforeFirst() {
        let summaries = makeMonthSummaries(startDay: 5, count: 10)
        let (sut, _, _) = makeSUT(summaries: summaries)

        let beforeFirst = makeDate(year: 2026, month: 2, day: 1)
        sut.handleChartSelection(at: beforeFirst)

        #expect(sut.showCrosshair == false)
    }

    // MARK: - Empty State

    @Test("chartOperations empty initially")
    func chartOperationsEmpty() {
        let (sut, _, _) = makeSUT()
        #expect(sut.chartOperations.isEmpty)
    }

    @Test("xAxisValues fallback returns values for month")
    func xAxisValuesFallbackNonEmpty() {
        let (sut, _, _) = makeSUT()
        let values = sut.xAxisValues
        // Month fallback generates Sunday ticks
        #expect(!values.isEmpty)
    }

    // MARK: - Single Item

    @Test("Single item: selection at that point shows crosshair")
    func singleItemSelection() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-03-10",
                entryTimestamp: "2026-03-10T08:00:00Z",
                date: makeDate(year: 2026, month: 3, day: 10, hour: 8),
                weight: 1800
            )
        ]
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 3, day: 10, hour: 12))
        #expect(sut.showCrosshair == true)
    }

    // MARK: - Section Selection Logic

    @Test("Selection in empty section picks start tick")
    func selectionInEmptySectionPicksStartTick() {
        // Create summaries with a full empty Sunday bucket from Mar 8..<Mar 15.
        let early = makeMonthSummaries(startDay: 1, count: 3)
        let late = makeMonthSummaries(startDay: 16, count: 3)
        let summaries = early + late
        let (sut, _, _) = makeSUT(summaries: summaries)

        // Select in the gap area (Mar 10) — no points exist in the Mar 8..<Mar 15 section.
        let gapDate = makeDate(year: 2026, month: 3, day: 10, hour: 12)
        sut.handleChartSelection(at: gapDate)

        // Should still show crosshair (selects start tick of section)
        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate == localNoon(day: 8))
    }

    @Test("Selection in phantom section after the last point hides crosshair")
    func selectionInPhantomSectionAfterLastPointHidesCrosshair() {
        let summaries = makeMonthSummaries(startDay: 1, count: 12)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: localNoon(day: 15))

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("Selection tie picks the earlier point within the section")
    func selectionTiePicksEarlierPoint() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-03-03",
                entryTimestamp: "2026-03-03T08:00:00Z",
                date: makeDate(year: 2026, month: 3, day: 3, hour: 8),
                weight: 1800
            ),
            DashboardTestFixtures.makeSummary(
                period: "2026-03-05",
                entryTimestamp: "2026-03-05T08:00:00Z",
                date: makeDate(year: 2026, month: 3, day: 5, hour: 8),
                weight: 1810
            )
        ]
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: localNoon(day: 4))

        #expect(sut.selectedDate == localNoon(day: 3))
        #expect(sut.showCrosshair == true)
    }

    @Test("Selection near a point chooses the nearest plotted day instead of the section start")
    func selectionPrefersNearestPointOverSectionStart() {
        let summaries = makeMonthSummaries(startDay: 1, count: 10)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 3, day: 5, hour: 10))

        #expect(sut.selectedDate == localNoon(day: 5))
        #expect(sut.showCrosshair == true)
    }

    // MARK: - Scroll Behavior

    @Test("handleScrollStart clears selection")
    func handleScrollStartClearsSelection() {
        let (sut, _, _) = makeSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true

        sut.handleScrollStart()
        #expect(sut.isScrolling == true)
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleScrollEnd resets isScrolling")
    func handleScrollEndResets() {
        let (sut, _, _) = makeSUT()
        sut.isScrolling = true
        sut.handleScrollEnd()
        #expect(sut.isScrolling == false)
    }

    // MARK: - Edge Cases

    @Test("clearSelection after selection clears all state")
    func clearSelectionAfterSelection() {
        let summaries = makeMonthSummaries(startDay: 1, count: 15)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 3, day: 5))
        sut.clearSelection()

        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
        #expect(sut.selectedPoint == nil)
    }

    @Test("Multiple selections keep last value")
    func multipleSelectionsKeepLast() {
        let summaries = makeMonthSummaries(startDay: 1, count: 20)
        let (sut, _, _) = makeSUT(summaries: summaries)

        sut.handleChartSelection(at: makeDate(year: 2026, month: 3, day: 3))
        sut.handleChartSelection(at: makeDate(year: 2026, month: 3, day: 15))

        #expect(sut.selectedDate != nil)
    }

    @Test("fallbackXAxisDomain returns valid range")
    func fallbackXAxisDomainValid() {
        let vm = MonthSectionViewModel()
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let d = domain {
            #expect(d.lowerBound < d.upperBound)
        }
    }

    @Test("shouldShowSolidLine returns true on 1st of month")
    func shouldShowSolidLineMonthFirst() {
        let vm = MonthSectionViewModel()
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month], from: Date())
        comps.day = 1
        let first = calendar.date(from: comps)!
        #expect(vm.shouldShowSolidLine(for: first) == true)
    }

    @Test("shouldShowSolidLine returns false on 15th of month")
    func shouldShowSolidLineMonth15th() {
        let vm = MonthSectionViewModel()
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month], from: Date())
        comps.day = 15
        let mid = calendar.date(from: comps)!
        #expect(vm.shouldShowSolidLine(for: mid) == false)
    }

    @Test("shouldAnimateChartData is false with no data")
    func shouldAnimateChartDataNoData() {
        let (sut, _, _) = makeSUT()
        #expect(sut.shouldAnimateChartData == false)
    }

    @Test("pointSize returns 64 (default)")
    func pointSizeDefault() {
        let vm = MonthSectionViewModel()
        #expect(vm.pointSize == 64)
    }

    @Test("symbolArea is correct for month diameters")
    func symbolAreaMonth() {
        let vm = MonthSectionViewModel()
        let base = vm.symbolArea(forDiameter: 8)
        let expected = CGFloat.pi * 16 // pi * 4^2
        #expect(abs(base - expected) < 0.001)
    }

    @Test("handleScrollPositionChange with nil does nothing")
    func handleScrollPositionChangeNil() {
        let (sut, _, _) = makeSUT()
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(nil)
        #expect(sut.scrollPosition == original)
    }

    @Test("Selection with right-edge slack near last point still selects")
    func selectionWithRightSlack() {
        let summaries = makeMonthSummaries(startDay: 1, count: 10)
        let (sut, _, _) = makeSUT(summaries: summaries)

        // Touch just past the last point (within slack)
        let nearLast = makeDate(year: 2026, month: 3, day: 10, hour: 18)
        sut.handleChartSelection(at: nearLast)

        // Should show crosshair if within right slack
        #expect(sut.showCrosshair == true)
    }
}
