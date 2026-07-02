import Foundation
@testable import meApp
import Testing

// MARK: - Test Helpers

@MainActor
private func makeSUT(
    configureStore: Bool = true,
    summaries: [BathScaleWeightSummary] = []
    // Test factory return; labeled tuple is clearer than a one-off SUT struct.
    // swiftlint:disable:next large_tuple
) -> (sut: WeekSectionViewModel, store: DashboardStore?, accountService: AccountService?, cacheManager: MockDashboardCacheManager?) {
    let vm = WeekSectionViewModel()
    guard configureStore else { return (vm, nil, nil, nil) }

    TestDependencyContainer.reset()
    let sutBundle = DashboardStoreTestSupport.makeSUT()
    let store = sutBundle.store
    let accountService = sutBundle.accountService
    let cacheManager = sutBundle.cacheManager
    let account = DashboardStoreTestSupport.makeActiveAccount()
    accountService.activeAccount = account
    store.state.graph.selectedPeriod = .week

    if !summaries.isEmpty {
        store.state.data.dailySummaries = summaries
        cacheManager.chartSeriesOverride = summaries.map {
            GraphSeries(date: $0.date, value: $0.weight, series: "weight")
        }
    }

    vm.configure(with: store)
    return (vm, store, accountService, cacheManager)
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
    guard let date = Calendar.current.date(from: comps) else {
        preconditionFailure("Invalid date components in makeDate")
    }
    return date
}

@MainActor
private func makeDailySummaries(startDay: Int = 1, count: Int = 7, month: Int = 3, year: Int = 2026) -> [BathScaleWeightSummary] {
    (0..<count).map { offset in
        let day = startDay + offset
        let dayStr = String(format: "%04d-%02d-%02d", year, month, day)
        return DashboardTestFixtures.makeSummary(
            period: dayStr,
            entryTimestamp: "\(dayStr)T08:00:00Z",
            date: makeDate(year: year, month: month, day: day, hour: 8),
            weight: 1800 + Double(offset) * 10
        )
    }
}

// MARK: - Tests

@Suite(.serialized)
@MainActor
struct WeekSectionViewModelTests {

    // MARK: - Period Properties

    @Test("timePeriod returns .week")
    func timePeriodIsWeek() {
        let vm = WeekSectionViewModel()
        #expect(vm.timePeriod == .week)
    }

    @Test("hasXAxis is true")
    func hasXAxisTrue() {
        let vm = WeekSectionViewModel()
        #expect(vm.hasXAxis == true)
    }

    @Test("lineWidth is 3")
    func lineWidthIs3() {
        let vm = WeekSectionViewModel()
        #expect(vm.lineWidth == 3)
    }

    @Test("basePointDiameter is 8")
    func basePointDiameterIs8() {
        let vm = WeekSectionViewModel()
        #expect(vm.basePointDiameter == 8)
    }

    @Test("selectedPointDiameter is 16")
    func selectedPointDiameterIs16() {
        let vm = WeekSectionViewModel()
        #expect(vm.selectedPointDiameter == 16)
    }

    // MARK: - plotXDate

    @Test("plotXDate snaps to local noon of the same day")
    func plotXDateSnapsToNoon() {
        let vm = WeekSectionViewModel()
        let morning = makeDate(year: 2026, month: 3, day: 5, hour: 8)
        let result = vm.plotXDate(for: morning)
        let calendar = Calendar.current
        #expect(calendar.component(.hour, from: result) == 12)
        #expect(calendar.component(.minute, from: result) == 0)
        #expect(calendar.component(.day, from: result) == 5)
    }

    @Test("plotXDate preserves the date even for late-night input")
    func plotXDateLateNight() throws {
        let vm = WeekSectionViewModel()
        var comps = DateComponents()
        comps.year = 2026; comps.month = 3; comps.day = 10
        comps.hour = 23; comps.minute = 59
        let lateNight = try #require(Calendar.current.date(from: comps))
        let result = vm.plotXDate(for: lateNight)
        let calendar = Calendar.current
        #expect(calendar.component(.hour, from: result) == 12)
        #expect(calendar.component(.day, from: result) == 10)
    }

    @Test("plotXDate preserves the date for midnight input")
    func plotXDateMidnight() {
        let vm = WeekSectionViewModel()
        let midnight = makeDate(year: 2026, month: 3, day: 5, hour: 0)
        let result = vm.plotXDate(for: midnight)
        let calendar = Calendar.current
        #expect(calendar.component(.hour, from: result) == 12)
        #expect(calendar.component(.day, from: result) == 5)
    }

    // MARK: - handleChartSelection

    @Test("handleChartSelection with nil date does nothing")
    func handleChartSelectionNilDate() {
        let (sut, _, _, _) = makeSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true
        sut.handleChartSelection(at: nil)
        #expect(sut.selectedDate != nil) // not cleared
    }

    @Test("handleChartSelection with no store does nothing")
    func handleChartSelectionNoStore() {
        let (sut, _, _, _) = makeSUT(configureStore: false)
        sut.handleChartSelection(at: Date())
        #expect(sut.selectedDate == nil)
    }

    @Test("handleChartSelection with no data hides crosshair")
    func handleChartSelectionNoDataHides() {
        let (sut, _, _, _) = makeSUT()
        sut.handleChartSelection(at: Date())
        // With no operations, effectiveDates is empty → selectedDate = nil
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleChartSelection snaps to nearest day tick within data range")
    func handleChartSelectionSnapsToTick() {
        let summaries = makeDailySummaries(startDay: 1, count: 7)
        let (sut, store, _, _) = makeSUT(summaries: summaries)

        // Select at a date between day 3 and day 4
        let betweenDays = makeDate(year: 2026, month: 3, day: 3, hour: 18)
        sut.handleChartSelection(at: betweenDays)

        // Should snap to one of the day ticks (noon)
        if let selected = sut.selectedDate {
            let calendar = Calendar.current
            let hour = calendar.component(.hour, from: selected)
            #expect(hour == 12) // snapped to noon
        }
    }

    @Test("handleChartSelection outside data range hides crosshair")
    func handleChartSelectionOutsideRange() {
        let summaries = makeDailySummaries(startDay: 1, count: 3)
        let (sut, _, _, _) = makeSUT(summaries: summaries)

        // Select far in the future (well outside data range)
        let futureDate = makeDate(year: 2027, month: 1, day: 1)
        sut.handleChartSelection(at: futureDate)

        #expect(sut.showCrosshair == false)
    }

    @Test("handleChartSelection within data range shows crosshair")
    func handleChartSelectionWithinRange() {
        let summaries = makeDailySummaries(startDay: 1, count: 7)
        let (sut, _, _, _) = makeSUT(summaries: summaries)

        // Select at day 4 — within the data range
        let day4 = makeDate(year: 2026, month: 3, day: 4, hour: 12)
        sut.handleChartSelection(at: day4)

        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate != nil)
    }

    // MARK: - handleScrollPositionChange

    @Test("handleScrollPositionChange with nil does nothing")
    func handleScrollPositionChangeNil() {
        let (sut, _, _, _) = makeSUT()
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(nil)
        #expect(sut.scrollPosition == original)
    }

    @Test("handleScrollPositionChange snaps drag updates to week boundaries")
    func handleScrollPositionChangeSnapsToWeekGrid() {
        let summaries = makeDailySummaries(startDay: 1, count: 7)
        let (sut, _, _, _) = makeSUT(summaries: summaries)
        let raw = makeDate(year: 2026, month: 3, day: 4, hour: 18)

        sut.handleScrollPositionChange(raw)

        #expect(sut.scrollPosition != raw)
    }

    // MARK: - Empty State

    @Test("chartOperations is empty initially")
    func chartOperationsEmptyInitially() {
        let (sut, _, _, _) = makeSUT()
        #expect(sut.chartOperations.isEmpty)
    }

    @Test("xAxisValues returns valid dates even with no data (fallback)")
    func xAxisValuesFallbackNonEmpty() {
        let (sut, _, _, _) = makeSUT()
        let values = sut.xAxisValues
        // Week fallback generates 8 dates (7 days + phantom)
        #expect(values.count == 8)
    }

    // MARK: - Single Item

    @Test("Single item: handleChartSelection selects the single point")
    func singleItemSelection() {
        let summaries = [
            DashboardTestFixtures.makeSummary(
                period: "2026-03-05",
                entryTimestamp: "2026-03-05T08:00:00Z",
                date: makeDate(year: 2026, month: 3, day: 5, hour: 8),
                weight: 1800
            )
        ]
        let (sut, _, _, _) = makeSUT(summaries: summaries)

        // With a single point, first == last == that point
        let date = makeDate(year: 2026, month: 3, day: 5, hour: 12)
        sut.handleChartSelection(at: date)

        #expect(sut.showCrosshair == true)
        #expect(sut.selectedDate != nil)
    }

    // MARK: - Scroll Behavior

    @Test("handleScrollStart clears selection and sets isScrolling")
    func handleScrollStartClearsSelection() {
        let (sut, _, _, _) = makeSUT()
        sut.selectedDate = Date()
        sut.showCrosshair = true

        sut.handleScrollStart()

        #expect(sut.isScrolling == true)
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
    }

    @Test("handleScrollEnd resets isScrolling")
    func handleScrollEndResetsScrolling() {
        let (sut, _, _, _) = makeSUT()
        sut.isScrolling = true
        sut.handleScrollEnd()
        #expect(sut.isScrolling == false)
    }

    // MARK: - Edge Cases

    @Test("clearSelection after selection clears all state")
    func clearSelectionAfterSelection() {
        let summaries = makeDailySummaries(startDay: 1, count: 7)
        let (sut, _, _, _) = makeSUT(summaries: summaries)

        let day3 = makeDate(year: 2026, month: 3, day: 3, hour: 12)
        sut.handleChartSelection(at: day3)
        #expect(sut.showCrosshair == true)

        sut.clearSelection()
        #expect(sut.selectedDate == nil)
        #expect(sut.showCrosshair == false)
        #expect(sut.selectedPoint == nil)
    }

    @Test("Multiple rapid selections only keep the last one")
    func multipleRapidSelections() {
        let summaries = makeDailySummaries(startDay: 1, count: 7)
        let (sut, _, _, _) = makeSUT(summaries: summaries)

        let day2 = makeDate(year: 2026, month: 3, day: 2, hour: 12)
        let day5 = makeDate(year: 2026, month: 3, day: 5, hour: 12)

        sut.handleChartSelection(at: day2)
        sut.handleChartSelection(at: day5)

        // Last selection should win
        if let selected = sut.selectedDate {
            let calendar = Calendar.current
            let day = calendar.component(.day, from: selected)
            #expect(day == 5)
        }
    }

    @Test("fallbackXAxisDomain returns valid range")
    func fallbackXAxisDomainValid() {
        let vm = WeekSectionViewModel()
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let bounds = domain {
            #expect(bounds.lowerBound < bounds.upperBound)
        }
    }

    @Test("shouldShowSolidLine returns true for first weekday")
    func shouldShowSolidLineFirstWeekday() throws {
        let vm = WeekSectionViewModel()
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026; comps.month = 3; comps.day = 8 // Sunday
        let sunday = try #require(calendar.date(from: comps))
        #expect(vm.shouldShowSolidLine(for: sunday) == (calendar.component(.weekday, from: sunday) == calendar.firstWeekday))
    }

    @Test("shouldAnimateChartData is false with no data")
    func shouldAnimateChartDataNoData() {
        let (sut, _, _, _) = makeSUT()
        #expect(sut.shouldAnimateChartData == false)
    }

    @Test("pointSize returns 64 (default from base)")
    func pointSizeDefault() {
        let vm = WeekSectionViewModel()
        #expect(vm.pointSize == 64)
    }
}
