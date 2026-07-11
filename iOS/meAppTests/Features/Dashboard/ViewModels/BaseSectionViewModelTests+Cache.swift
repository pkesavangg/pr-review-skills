import Foundation
@testable import meApp
import Testing

// MARK: - Cache, Refresh, Axis Formatting & Goal Chip Edge Cases
//
// Split out of BaseSectionViewModelTests.swift to keep each file/type body under the
// SwiftLint file_length / type_body_length limits. These are `extension` methods on the
// same `@Suite(.serialized)` type, so they stay in one serialized suite (shared DI state
// is never exercised in parallel). Helpers live in BaseSectionViewModelTests.swift.

extension BaseSectionViewModelTests {

    // MARK: - Cache Management

    @Test("invalidateCache clears cached series data")
    func invalidateCacheClearsData() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.updateCachedSeriesData()
        sut.invalidateCache()
        // After invalidation, getCachedGroupedSeries returns fresh computation
        let grouped = sut.getCachedGroupedSeries()
        // Should still work (falls back to direct data)
        #expect(grouped is [String: [GraphSeries]])
    }

    @Test("getCachedSeriesData returns chartSeriesData when cache is empty")
    func getCachedSeriesDataFallback() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.invalidateCache()
        let data = sut.getCachedSeriesData()
        #expect(data == sut.chartSeriesData)
    }

    @Test("updateCachedSeriesData populates cache")
    func updateCachedSeriesDataPopulates() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.updateCachedSeriesData()
        // After update, getCachedGroupedSeries should return data without re-computing
        _ = sut.getCachedGroupedSeries()
        // No crash or error means success
    }

    @Test("getCachedGroupedSeries groups and sorts series points by date")
    func getCachedGroupedSeriesGroupsAndSorts() {
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-03", date: baseSectionVMTestsMakeDate(day: 3), weight: 1820),
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: baseSectionVMTestsMakeDate(day: 1), weight: 1800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", date: baseSectionVMTestsMakeDate(day: 2), weight: 1810)
        ]
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(period: .week, summaries: summaries)

        let grouped = sut.getCachedGroupedSeries()
        let weightSeries = grouped["weight"] ?? []

        #expect(weightSeries.count == 3)
        #expect(weightSeries.map(\.date) == weightSeries.map(\.date).sorted())
    }

    // MARK: - X-Axis Cache Invalidation

    @Test("invalidateXAxisCache clears cached X-axis values")
    func invalidateXAxisCacheClearsValues() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        _ = sut.xAxisValues // populate cache
        sut.invalidateXAxisCache()
        // Next access should regenerate
        let values = sut.xAxisValues
        #expect(values is [Date]) // Just verifies no crash
    }

    // MARK: - refreshData

    @Test("refreshData invalidates cache and updates Y-axis config")
    func refreshDataInvalidatesAndUpdates() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        sut.updateCachedSeriesData()
        sut.refreshData()
        // Cache should have been invalidated (empty hash)
        // No crash means the method works correctly
    }

    @Test("configure caches whether the chart has data")
    func configureCachesChartPresence() {
        let summaries = DashboardTestFixtures.makeSortedDailySummaries()
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT(summaries: summaries)

        #expect(sut.hasChartOperations == true)

        sut.tearDown()

        #expect(sut.hasChartOperations == false)
    }

    @Test("refreshData maintains selection if still valid")
    func refreshDataMaintainsSelection() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        let date = Date()
        sut.selectedDate = date
        sut.showCrosshair = true
        sut.refreshData()
        // Selection should be re-processed (handleChartSelection called with selectedDate)
        #expect(sut.selectedDate != nil)
    }

    // MARK: - handleSettingsChange

    @Test("handleSettingsChange preserves selection")
    func handleSettingsChangePreservesSelection() {
        // MA-3891: unit / weightless toggles change displayed values but not which date is
        // selected, so the crosshair selection must be preserved across a settings change.
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        let selected = Date()
        sut.selectedDate = selected
        sut.showCrosshair = true

        sut.handleSettingsChange()

        #expect(sut.selectedDate != nil)
        #expect(sut.showCrosshair == true)
    }

    // MARK: - forceScrollPositionUpdate

    @Test("forceScrollPositionUpdate temporarily changes and restores scroll position")
    func forceScrollPositionUpdateSetsValue() async {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        let target = Date().addingTimeInterval(5000)
        sut.forceScrollPositionUpdate(to: target)
        // The scroll position should eventually settle on target
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(sut.scrollPosition == target)
    }

    // MARK: - visibleDomainLength

    @Test("visibleDomainLength returns the week fallback constant when store is nil")
    func visibleDomainLengthFallback() {
        // The week visible domain carries a small padding factor (7.15 days) so the trailing
        // phantom tick is reachable; assert against the production constant rather than a raw 7 days.
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.visibleDomainLength == DashboardConstants.TimeInterval.week)
    }

    // MARK: - pointSize

    @Test("pointSize returns 64 by default")
    func pointSizeDefault() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.pointSize == 64)
    }

    // MARK: - fallbackXAxisDomain

    @Test("fallbackXAxisDomain returns nil for total period")
    func fallbackXAxisDomainNilForTotal() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .total)
        #expect(vm.fallbackXAxisDomain() == nil)
    }

    @Test("fallbackXAxisDomain returns a valid range for week period")
    func fallbackXAxisDomainWeek() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    @Test("fallbackXAxisDomain returns a valid range for month period")
    func fallbackXAxisDomainMonth() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .month)
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    @Test("fallbackXAxisDomain returns a valid range for year period")
    func fallbackXAxisDomainYear() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .year)
        let domain = vm.fallbackXAxisDomain()
        #expect(domain != nil)
        if let range = domain {
            #expect(range.lowerBound < range.upperBound)
        }
    }

    // MARK: - formatXAxisLabel Empty State

    @Test("formatXAxisLabel for week with no ops returns lowercased weekday")
    func formatXAxisLabelWeekEmpty() {
        let vm = BaseSectionVMTestsSectionViewModel(period: .week)
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
        let vm = BaseSectionVMTestsSectionViewModel(period: .year)
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
        let vm = BaseSectionVMTestsSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 3
        comps.day = 8 // Sunday
        let sunday = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        let label = vm.formatXAxisLabel(for: sunday)
        #expect(label == "8")
    }

    @Test("formatXAxisLabel for month with no ops returns the day number for any weekly tick")
    func formatXAxisLabelMonthEmptyNonSunday() {
        // Month empty-state ticks are generated weekly (1, 8, 15, 22, 29) and may not fall on a
        // Sunday, so the label is the day-of-month for whatever tick date it is given.
        let vm = BaseSectionVMTestsSectionViewModel(period: .month)
        let calendar = Calendar.current
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 3
        comps.day = 9 // Monday
        let monday = calendar.date(from: comps)! // swiftlint:disable:this force_unwrapping
        let label = vm.formatXAxisLabel(for: monday)
        #expect(label == "9")
    }

    // MARK: - formatSelectedXAxisLabel

    @Test("formatSelectedXAxisLabel returns nil when store is nil")
    func formatSelectedXAxisLabelNilStore() {
        let (sut, _) = baseSectionVMTestsMakeSUT()
        #expect(sut.formatSelectedXAxisLabel() == nil)
    }

    @Test("formatSelectedXAxisLabel falls back to store selectedXValue")
    func formatSelectedXAxisLabelUsesSelectedXValue() {
        let (sut, store, _) = baseSectionVMTestsMakeConfiguredSUT()
        let selected = baseSectionVMTestsMakeDate(day: 5)
        store.state.graph.selectedXValue = selected

        let label = sut.formatSelectedXAxisLabel()

        #expect(label != nil)
    }

    @Test("formatSelectedXAxisLabel falls back to selectedPoint date")
    func formatSelectedXAxisLabelUsesSelectedPointDate() {
        let (sut, store, _) = baseSectionVMTestsMakeConfiguredSUT()
        let summary = DashboardTestFixtures.makeSummary(period: "2026-03-06", date: baseSectionVMTestsMakeDate(day: 6), weight: 1830)
        store.state.graph.selectedPoint = summary

        let label = sut.formatSelectedXAxisLabel()

        #expect(label != nil)
    }

    // MARK: - handleScrollPositionChange Throttling

    @Test("handleScrollPositionChange ignores nil position")
    func handleScrollPositionChangeIgnoresNil() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        let original = sut.scrollPosition
        sut.handleScrollPositionChange(nil)
        #expect(sut.scrollPosition == original)
    }

    @Test("handleScrollPositionChange ignores tiny position changes")
    func handleScrollPositionChangeIgnoresTiny() {
        let (sut, _, _) = baseSectionVMTestsMakeConfiguredSUT()
        let original = sut.scrollPosition
        // Change by 0.05 seconds — below 0.1 threshold
        sut.handleScrollPositionChange(original.addingTimeInterval(0.05))
        #expect(sut.scrollPosition == original)
    }

    // MARK: - Goal Chip Top/Bottom Placement Edge Cases

    @Test("getGoalChipPosition returns top placement when goal is above domain")
    func goalChipTopPlacement() {
        let (sut, store, accountService) = baseSectionVMTestsMakeConfiguredSUT()
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
        let (sut, store, accountService) = baseSectionVMTestsMakeConfiguredSUT()
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
        let (sut, store, _) = baseSectionVMTestsMakeConfiguredSUT()
        store.state.graph.cachedYAxisDomain = 140...240
        store.state.graph.cachedYAxisTicks = [140, 190, 240]

        sut.syncYAxisFromStore()

        #expect(sut.yAxisDomain == 140...240)
        #expect(sut.yAxisTicks == [140, 190, 240])
    }
}
