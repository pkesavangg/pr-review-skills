import Foundation
@testable import meApp
import Testing

// MARK: - Data, Selection & Line Rendering Tests
//
// Split out of BaseSectionViewModelTests.swift to keep each file/type body under the
// SwiftLint file_length / type_body_length limits. These are `extension` methods on the
// same `@Suite(.serialized)` type, so they stay in one serialized suite (shared DI state
// is never exercised in parallel). Helpers live in BaseSectionViewModelTests.swift.

extension BaseSectionViewModelTests {

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

    @Test("getChartPosition returns nil when visible domain length is invalid")
    func chartPositionNilInvalidVisibleDomainLength() {
        let vm = BaseSectionVMTestsInvalidDomainViewModel(period: .week, visibleDomainLength: 0)
        vm.updateChartFrame(CGRect(x: 0, y: 0, width: 300, height: 200))

        #expect(vm.getChartPosition(for: Date(), value: 150) == nil)
    }

    // MARK: - shouldShowSolidLine

    @Test("shouldShowSolidLine for week returns true on first weekday")
    func shouldShowSolidLineWeekFirstDay() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
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
        let vm = BaseSectionVMTestsSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month], from: Date())
        comps.day = 1
        let firstOfMonth = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: firstOfMonth) == true)
    }

    @Test("shouldShowSolidLine for month returns false on 15th")
    func shouldShowSolidLineMonthMiddle() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month], from: Date())
        comps.day = 15
        let mid = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: mid) == false)
    }

    @Test("shouldShowSolidLine for year returns true on January 1")
    func shouldShowSolidLineYearJanFirst() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .year)
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
        let vm = BaseSectionVMTestsSectionViewModel(period: .year)
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
        let vm = BaseSectionVMTestsSectionViewModel(period: .total)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 1
        comps.day = 1
        let jan1 = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        #expect(vm.shouldShowSolidLine(for: jan1) == false)
    }
}
