//
//  BaseSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Foundation
import SwiftUI
import Charts

/// Base class implementing common functionality for all section view models
@MainActor
class BaseSectionViewModel: ObservableObject, SectionViewModelProtocol {
    
    // MARK: - Published Properties
    @Published var selectedPoint: BathScaleWeightSummary?
    @Published var selectedDate: Date?
    @Published var showCrosshair: Bool = false
    @Published var scrollPosition: Date = Date()
    @Published var isScrolling: Bool = false
    
    /// Default implementation simply returns the current `selectedDate`.
    /// Subclasses can override by setting `selectedDate` to a snapped value
    /// or by providing a different preferred date if needed.
    var preferredSelectedDate: Date? { selectedDate }
    
    // MARK: - Chart Configuration
    var chartFrame: CGRect = .zero
    var yAxisDomain: ClosedRange<Double> = 0...100
    var yAxisTicks: [Double] = []
    
    // MARK: - Dependencies (injected from parent)
    var dashboardStore: DashboardStore?
    
    // MARK: - Period-specific properties (to be overridden)
    var timePeriod: TimePeriod {
        fatalError("Must be overridden by subclass")
    }
    
    var visibleDomainLength: TimeInterval {
        return dashboardStore?.visibleDomainLength(for: timePeriod) ?? (7 * 24 * 60 * 60)
    }
    
    
    var pointSize: CGFloat {
        return 64 // Default point size
    }
    
    var hasXAxis: Bool {
        return timePeriod != .total // Total period has no X-axis
    }

    // MARK: - Layout Constants
    private enum Layout {
        static let xAxisReservedHeight: CGFloat = 18
    }

    private var xAxisReservedHeight: CGFloat { Layout.xAxisReservedHeight }

    // MARK: - Stroke & Point Sizing (moved from view)
    
    /// Line width used by charts for this period (3 for week/month/year, 2 for total)
    var lineWidth: CGFloat {
        return hasXAxis ? 3 : 2
    }
    
    /// Base point diameter for this period (8 for week/month/year, 4 for total)
    var basePointDiameter: CGFloat {
        return hasXAxis ? 8 : 4
    }
    
    /// Selected point diameter for this period (16 for week/month/year, 8 for total)
    var selectedPointDiameter: CGFloat {
        return hasXAxis ? 16 : 8
    }
    
    /// Area value expected by Charts' `symbolSize` for the base point
    var basePointArea: CGFloat { symbolArea(forDiameter: basePointDiameter) }
    
    /// Area value expected by Charts' `symbolSize` for the selected point
    var selectedPointArea: CGFloat { symbolArea(forDiameter: selectedPointDiameter) }
    
    /// Returns the point area based on selection state
    /// - Parameter isSelected: whether the point is selected
    /// - Returns: area in pt^2 for use with `symbolSize`
    func pointArea(isSelected: Bool) -> CGFloat {
        return isSelected ? selectedPointArea : basePointArea
    }
    
    /// Converts a circle diameter (in pt) to the area value expected by Charts' `symbolSize`
    func symbolArea(forDiameter diameter: CGFloat) -> CGFloat {
        let r = diameter / 2
        return .pi * r * r
    }
    
    var dateRange: ClosedRange<Date> {
        // Default implementation for scrollable views - use visible domain
        let operations = chartOperations
        guard !operations.isEmpty else {
            let now = Date()
            return now...now
        }
        
        let dates = operations.map { $0.date }
        guard let minDate = dates.min(), let maxDate = dates.max() else {
            let now = Date()
            return now...now
        }
        
        return minDate...maxDate
    }
    
    // MARK: - Common Computed Properties
    
    /// All operations for the view
    var chartOperations: [BathScaleWeightSummary] {
        return dashboardStore?.continuousOperations ?? []
    }
    
    /// Chart series data with scroll performance optimization
    var chartSeriesData: [GraphSeries] {
        guard let store = dashboardStore else { return [] }
        
        // During scrolling, use cached data to prevent expensive recalculations
        if isScrolling && !cachedChartSeriesData.isEmpty {
            return cachedChartSeriesData
        }
        
        let seriesData = store.chartSeriesData
        
        // Cache the data for potential reuse during scrolling
        if !isScrolling {
            cachedChartSeriesData = seriesData
        }
        
        return seriesData
    }
    
    // Cache for chart series data during scrolling
    private var cachedChartSeriesData: [GraphSeries] = []
    
    // MARK: - Persistent Cache (survives view recreation)
    private var cachedSeriesData: [GraphSeries] = []
    private var cachedGroupedSeries: [String: [GraphSeries]] = [:]
    private var lastCacheUpdateHash: Int = 0

    /// Visible series filtered by the current scroll position and visible domain
    var visibleChartSeriesData: [GraphSeries] {
        guard hasXAxis else { return chartSeriesData }
        let domainLength = visibleDomainLength
        guard domainLength.isFinite && domainLength > 0 else { return chartSeriesData }
        let left = scrollPosition.addingTimeInterval(-domainLength / 2)
        let right = scrollPosition.addingTimeInterval(domainLength / 2)
        let data = chartSeriesData
        // Keep only points whose plotted X-date is within visible window
        return data.filter { point in
            let xDate = plotXDate(for: point.date)
            return xDate >= left && xDate <= right
        }
    }
    
    /// Goal weight for display (nil if no goal is set)
    var goalWeight: Double? {
        return dashboardStore?.goalWeightForDisplay
    }
    
    /// Current display weight
    var displayWeight: Double? {
        return dashboardStore?.displayWeight
    }
    
    /// Weight label for current selection or period
    var weightLabel: String {
        return dashboardStore?.weightLabel ?? ""
    }
    
    /// X-axis values with buffer
    var xAxisValues: [Date] {
        // If we have the store, try generating ticks from graphManager first
        if let store = dashboardStore {
            // Use the live scroll position from this view model so axis ticks (including
            // trailing phantom ticks) reflect the current gesture immediately and avoid
            // the "jump" when scroll ends.
            let liveScrollPosition = self.scrollPosition
            let ticks = store.graphManager.generateVisibleXAxisValues(
                for: timePeriod,
                from: store.continuousOperations,
                scrollPosition: liveScrollPosition
            )
            // If manager returned ticks, use them; otherwise fall back to calendar-based ticks
            if !ticks.isEmpty { return ticks.sorted() }
        }
        // Fallback: generate calendar-based ticks so X-axis labels show even with no data
        return fallbackXAxisValues().sorted()
    }
    
    /// Determines if the chart is scrolled to the leftmost boundary
    var isAtLeftBoundary: Bool {
        guard dashboardStore != nil, !chartOperations.isEmpty else { return true }
        
        let operations = chartOperations
        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min() else { return true }
        
        let domainLength = visibleDomainLength
        let visibleStart = scrollPosition.addingTimeInterval(-domainLength / 2)
        
        // Consider at boundary if visible start is at or before the minimum data date
        let boundaryThreshold: TimeInterval = 24 * 60 * 60 // 1 day
        return visibleStart <= minDate.addingTimeInterval(boundaryThreshold)
    }
    
    // MARK: - Initialization and Configuration
    func configure(with store: DashboardStore) {
        self.dashboardStore = store
        
        // For scrollable periods, ensure we show recent entries by using optimal scroll position
        if hasXAxis {
            let optimalPosition = store.graphManager.calculateOptimalScrollPosition(
                for: timePeriod,
                from: store.continuousOperations,
                showingLatest: true
            )
            self.scrollPosition = optimalPosition
            // Also update the store's scroll position to match
            store.graphManager.updateScrollPosition(to: optimalPosition)
        } else {
            // Non-scrollable periods (Total) don't need scroll positioning
            self.scrollPosition = store.state.graph.xScrollPosition
        }
        
        self.isScrolling = store.state.graph.isScrolling
        updateYAxisConfiguration()
        // Sync with any existing cached Y-axis values from the store
        syncYAxisFromStore()
        // Initialize cache with current data (safe during configuration)
        updateCachedSeriesData()
    }
    
    // MARK: - Chart State Management
    
    /// Updates Y-axis configuration based on current data and goal
    func updateYAxisConfiguration() {
        guard let store = dashboardStore else { return }
        
        // Skip Y-axis updates during scrolling to prevent performance issues
        // Y-axis should be stable during scroll and only update when scroll ends
        guard !isScrolling else { 
            return 
        }
        
        // Use visible operations plus bracketing points for Y-axis calculation
        // This ensures the Y-axis accounts for line segments extending beyond the visible window
        // 
        // PROBLEM: When viewing "this week" with start value 50, but previous week's Saturday was 200,
        // the line goes up from 200->50 but Y-axis only considers visible points (50), making the
        // line appear to come from nowhere and extend beyond the chart bounds.
        //
        // SOLUTION: Include bracketing points (previous/next operations outside visible window)
        // so Y-axis domain accounts for the full range of the connecting line segments.
        var operations: [BathScaleWeightSummary]
        if hasXAxis {
            let visible = store.visibleOperations
            let bracket = store.graphManager.getBracketingOperations(from: chartOperations)
            
            // Combine visible operations with bracketing points for comprehensive Y-axis calculation
            // This prevents Y-axis from being too narrow when lines extend beyond visible window
            if visible.isEmpty {
                // No visible points - use bracketing points or all data as fallback
                operations = bracket.isEmpty ? chartOperations : bracket
            } else {
                // Combine visible points with bracketing points for complete line coverage
                // Manually deduplicate by entryTimestamp since BathScaleWeightSummary doesn't conform to Hashable
                var combinedOperations = visible
                for bracketOp in bracket {
                    // Only add if not already present (check by entryTimestamp for uniqueness)
                    if !combinedOperations.contains(where: { $0.entryTimestamp == bracketOp.entryTimestamp }) {
                        combinedOperations.append(bracketOp)
                    }
                }
                operations = combinedOperations.sorted { $0.entryTimestamp < $1.entryTimestamp }
            }
        } else {
            operations = chartOperations
        }
        
        // Get Y-axis scale from graph manager using the calculated operations (visible + bracketing)
        let yAxisScale = store.graphManager.getYAxisScale(
            from: operations,
            goalWeight: goalWeight,
            isWeightlessMode: store.isWeightlessModeEnabled,
            anchorWeight: store.weightlessAnchorWeight,
            convertWeight: store.goalManager.convertWeightToDisplay,
            chartHeight: chartFrame.height
        )
        
        // Animate domain changes smoothly
        self.yAxisDomain = yAxisScale.domain
        // Do not animate tick updates
        withTransaction(Transaction(animation: nil)) {
            self.yAxisTicks = yAxisScale.ticks
        }
    }
    
    /// Updates chart frame and recalculates Y-axis if needed
    func updateChartFrame(_ frame: CGRect) {
        let previousHeight = chartFrame.height
        self.chartFrame = frame
        // Recalculate Y-axis if frame changed significantly
        if abs(frame.height - previousHeight) > 10 {
            updateYAxisConfiguration()
        }
    }
    
    // MARK: - Scroll Management
    
    /// Handles scroll position changes
    func handleScrollPositionChange(_ newPosition: Date?) {
        guard let newPosition = newPosition else { return }
        
        // Only update if position actually changed to prevent redundant updates
        guard abs(newPosition.timeIntervalSince(scrollPosition)) > 0.1 else { return }
        
        self.scrollPosition = newPosition
        
        // Update dashboard store scroll position
        dashboardStore?.handleScrollPositionChange(newPosition)
    }
    
    /// Handles scroll start
    func handleScrollStart() {
        self.isScrolling = true
        // Immediately clear selection when scroll starts to hide crosshair and label
        clearSelection()
        // Clear cached data to ensure fresh data on scroll end
        cachedChartSeriesData = []
        dashboardStore?.handleScrollStart()
    }
    
    /// Handles scroll end
    func handleScrollEnd() {
        self.isScrolling = false
        // Clear cached data to ensure fresh data is generated
        cachedChartSeriesData = []
        dashboardStore?.handleScrollEndOptimized()
        
        // Sync Y-axis values from store cache after scroll end (with delay to allow store to update)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            self.syncYAxisFromStore()
        }
    }
    
    // MARK: - Selection Management
    
    /// Handles chart selection at a specific date
    func handleChartSelection(at date: Date?) {
        // Only process valid dates - don't clear selection for nil dates
        // Selection clearing should only happen on scroll start
        guard let date = date else {
            return
        }
        
        selectedDate = date
        showCrosshair = true
        
        // Find the closest operation to the selected date
        let operations = chartOperations
        selectedPoint = operations.min { op1, op2 in
            abs(op1.date.timeIntervalSince(date)) < abs(op2.date.timeIntervalSince(date))
        }
    }
    
    /// Clears all selection state
    func clearSelection() {
        selectedPoint = nil
        selectedDate = nil
        showCrosshair = false
    }
    
    // MARK: - Goal Chip Positioning
    
    /// Calculates goal chip position (with X-axis adjustment)
    func getGoalChipPosition() -> (yPosition: CGFloat, placement: GoalPlacement) {
        // Return default position if no goal is set
        guard let goalWeightValue = self.goalWeight else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }
        // Use rounded goal weight in display space for positioning, matching label rounding.
        // In weightless mode this is the rounded goal delta (goal - anchor).
        let goalWeight = self.dashboardStore?.roundedGoalWeight(goalWeightValue) ?? goalWeightValue
        let domain = yAxisDomain
        
        // Calculate proportional position within the chart
        let domainRange = domain.upperBound - domain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }
        
        // Account for X-axis height only when this period has an X-axis
        // (week/month/year). Total view has no X-axis, so no subtraction.
        let availableChartHeight = chartFrame.height - xAxisReservedHeight
        
        // If goal weight is outside domain, show at edges
        if goalWeight > domain.upperBound {
            return (yPosition: -20, placement: .top)
        }
        
        if goalWeight < domain.lowerBound {
            return (yPosition: chartFrame.height, placement: .bottom)
        }
        
        // Goal weight is within domain, calculate proportional position
        let yRatio = (goalWeight - domain.lowerBound) / domainRange
        guard yRatio.isFinite else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }
        
        // Calculate position within the available chart area (excluding X-axis)
        let yPosition = (availableChartHeight * (1 - yRatio)) // Invert because chart y grows downward
        
        return (yPosition: yPosition, placement: .middle)
    }
    
    /// Calculates goal chip X offset based on text width
    func getGoalChipXOffset() -> CGFloat {
        guard let store = dashboardStore, let goalWeightValue = goalWeight else { return 28 }
        
        let formattedText = store.formatYAxisTickLabel(goalWeightValue)
        
        // Check if it's a 3-digit value or longer
        if formattedText.count >= 3 {
            return 22 // More space for 3+ digit values
        } else {
            return 18 // Less space for 1-2 digit values
        }
    }
    
    // MARK: - Chart Position Calculations
    
    /// Calculates chart position for a given date and value (for selection callout)
    func getChartPosition(for date: Date, value: Double) -> CGPoint? {
        guard chartFrame.width > 0 else { return nil }
        
        // Use scroll-based calculation for scrollable periods
        let xScrollPosition = self.scrollPosition
        let visibleDomainLength = self.visibleDomainLength
        guard visibleDomainLength.isFinite && visibleDomainLength > 0 else { return nil }
        
        // Use the plotting X-date which may be shifted (e.g., mid-month for year view)
        let effectiveDate = plotXDate(for: date)
        let timeFromScrollPosition = effectiveDate.timeIntervalSince(xScrollPosition)
        let xRatio = timeFromScrollPosition / visibleDomainLength
        let xPosition = chartFrame.width * xRatio
        
        // Calculate y position relative to y-axis domain
        let domainRange = yAxisDomain.upperBound - yAxisDomain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        // Account for X-axis height if this period has X-axis
        let availableChartHeight = chartFrame.height - xAxisReservedHeight
        
        let yRatio = (value - yAxisDomain.lowerBound) / domainRange
        guard yRatio.isFinite else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        // Calculate position within the available chart area (excluding X-axis)
        let yPosition = (availableChartHeight * (1 - yRatio)) // Invert because chart y grows downward
        
        // Add padding offsets for left boundary
        let adjustedX = xPosition + (isAtLeftBoundary ? 4 : 0) // spacingXS approximation
        let adjustedY = yPosition
        
        return CGPoint(x: adjustedX, y: adjustedY)
    }

    /// Default implementation: use the original date for plotting
    func plotXDate(for original: Date) -> Date {
        return original
    }
    
    // MARK: - X-Axis Label Generation
    
    /// Formats X-axis label (to be overridden by subclasses if needed)
    func formatXAxisLabel(for date: Date) -> String? {
        // If there are no operations, use deterministic labels matching product spec
        if chartOperations.isEmpty {
            let calendar = Calendar(identifier: .gregorian)
            switch timePeriod {
            case .week:
                // 3-letter weekday starting from Sunday, lowercased (sun, mon, ... sat)
                let fmt = DateFormatter()
                fmt.locale = Locale(identifier: "en_US_POSIX")
                fmt.calendar = calendar
                fmt.dateFormat = "EEE"
                return fmt.string(from: date).lowercased()
            case .month:
                // Show day-of-month numerals: 1, 8, 15, 22, 29
                let day = calendar.component(.day, from: date)
                return String(day)
            case .year:
                // Single-letter month initials (j, f, m, a, m, j, j, a, s, o, n, d)
                let fmt = DateFormatter()
                fmt.locale = Locale(identifier: "en_US_POSIX")
                fmt.calendar = calendar
                fmt.dateFormat = "MMM"
                return String(fmt.string(from: date).prefix(1)).lowercased()
            default:
                break
            }
        }
        return dashboardStore?.xLabelString(for: date, period: timePeriod)?.lowercased()
    }
    
    func formatSelectedXAxisLabel() -> String? {
        guard let store = dashboardStore else { return nil }
        // Prefer the view model's snapped selection first for immediate UI sync
        let date: Date? = self.selectedDate ?? store.state.graph.selectedXValue ?? store.state.graph.selectedPoint?.date
        guard let date else { return nil }
        return store.graphManager.formatSelectedDate(date, for: store.state.graph.selectedPeriod)
    }
    
    // MARK: - Chart Content Helpers
    
    /// Determines if chart data should animate based on scrolling state
    var shouldAnimateChartData: Bool {
        return !isScrolling && !chartOperations.isEmpty
    }
    
    // MARK: - Data Management
    
    /// Called when data changes to update chart state
    func refreshData() {
        // Invalidate cache since underlying data changed
        invalidateCache()
        updateYAxisConfiguration()
        // Maintain selection if still valid
        if let selectedDate = selectedDate {
            handleChartSelection(at: selectedDate)
        }
    }
    
    /// Called when settings change (unit, weightless mode, etc.)
    func handleSettingsChange() {
        // Update store's Y-axis cache FIRST before invalidating local cache
        dashboardStore?.updateYAxisCache(force: true)
        
        invalidateCache()
        updateYAxisConfiguration()
        syncYAxisFromStore()
        clearSelection() // Clear selection as values may have changed
    }
    
    /// Called when scroll position is updated programmatically
    func updateScrollPosition(to position: Date) {
        self.scrollPosition = position
    }
    
    /// Forces scroll position update with binding refresh
    func forceScrollPositionUpdate(to position: Date) {
        // Force a small change to trigger binding update
        let temp = position.addingTimeInterval(0.001)
        self.scrollPosition = temp
        DispatchQueue.main.async {
            self.scrollPosition = position
        }
    }
    
    /// Initialize chart
    func initializeChart() {
        dashboardStore?.initializeChart()
    }
    
    /// Sync Y-axis domain and ticks from dashboard store cache
    /// Called when the dashboard store's cached Y-axis values change during scrolling
    func syncYAxisFromStore() {
        guard let store = dashboardStore else { return }

        // Read cached values from dashboard store
        if let cachedDomain = store.state.graph.cachedYAxisDomain {
            // Animate domain changes smoothly
            withAnimation(.easeInOut(duration: 0.15)) {
                self.yAxisDomain = cachedDomain
            }
        }

        if let cachedTicks = store.state.graph.cachedYAxisTicks {
            // Suppress animation for tick changes
            withTransaction(Transaction(animation: nil)) {
                self.yAxisTicks = cachedTicks
            }
        }

        // Y-axis domain/ticks affect normalized metric series values.
        // Invalidate and refresh cached series so plotted points match the new domain.
        invalidateCache()
        updateCachedSeriesData()
    }

    /// Determines if a date should show a solid vertical line (start of week/month/year)
    /// - Parameter date: The date to check
    /// - Returns: True if the date represents the start of a major period
    func shouldShowSolidLine(for date: Date) -> Bool {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.weekday, .day, .month], from: date)

        switch timePeriod {
        case .week:
            // For week view, show solid line for the start of the week per locale
            guard let weekday = components.weekday else { return false }
            return weekday == calendar.firstWeekday

        case .month:
            // For month view, show solid line for 1st of month
            return components.day == 1

        case .year:
            // For year view, show solid line for January 1st
            return components.month == 1 && components.day == 1

        default:
            return false
        }
    }
    
    // MARK: - Persistent Cache Management
    
    /// Updates cached series data only if the underlying data has changed
    func updateCachedSeriesData() {
        let newData = chartSeriesData
        
        // Create a simple hash to detect data changes
        var hasher = Hasher()
        hasher.combine(newData.count)
        if !newData.isEmpty {
            // Sample a few points to create a lightweight hash
            let indices = newData.count <= 3 ? Array(0..<newData.count) : [0, newData.count/2, newData.count-1]
            for i in indices {
                let point = newData[i]
                hasher.combine(point.date.timeIntervalSince1970.bitPattern)
                hasher.combine(point.value.bitPattern)
                hasher.combine(point.series)
            }
        }
        let newHash = hasher.finalize()
        
        // Only update cache if data actually changed
        if newHash != lastCacheUpdateHash || cachedSeriesData.isEmpty {
            // Update cache synchronously since these are no longer @Published
            cachedSeriesData = newData
            
            // Group series and pre-sort each series by date
            let grouped = Dictionary(grouping: cachedSeriesData) { $0.series }
            cachedGroupedSeries = grouped.mapValues { seriesPoints in
                seriesPoints.sorted { $0.date < $1.date }
            }
            
            lastCacheUpdateHash = newHash
        }
    }
    
    /// Async version for updating cache from view updates without publishing warnings
    func updateCachedSeriesDataAsync() {
        Task { @MainActor in
            updateCachedSeriesData()
        }
    }
    
    /// Returns cached grouped series data, updating cache if needed
    func getCachedGroupedSeries() -> [String: [GraphSeries]] {
        if cachedGroupedSeries.isEmpty {
            // Use direct data if cache is empty to avoid publishing issues
            let newData = chartSeriesData
            let grouped = Dictionary(grouping: newData) { $0.series }
            return grouped.mapValues { seriesPoints in
                seriesPoints.sorted { $0.date < $1.date }
            }
        }
        return cachedGroupedSeries
    }
    
    /// Invalidates the cache when underlying data changes
    func invalidateCache() {
        cachedSeriesData = []
        cachedGroupedSeries = [:]
        lastCacheUpdateHash = 0
    }
    
    /// Returns cached series data, updating cache if needed
    func getCachedSeriesData() -> [GraphSeries] {
        if cachedSeriesData.isEmpty {
            // Use direct data if cache is empty to avoid publishing issues
            return chartSeriesData
        }
        return cachedSeriesData
    }

    // MARK: - Fallback X-Axis Ticks (for empty data)
    /// Generates calendar-based X-axis ticks for current period when there are no entries.
    /// Ensures week/month/year views still show X-axis labels with a trailing phantom tick.
    private func fallbackXAxisValues() -> [Date] {
        guard hasXAxis else { return [] }
        let calendar = Calendar.current
        let position = scrollPosition
        // Helper to set midday for better alignment with scroll behavior (hour: 12)
        func midday(_ date: Date) -> Date {
            return calendar.date(bySettingHour: 12, minute: 0, second: 0, of: date) ?? date
        }
        switch timePeriod {
        case .week:
            // Always start from the most recent Sunday at 00:00 local time
            let startOfDay = calendar.startOfDay(for: position)
            let sundayStart = calendar.nextDate(after: startOfDay,
                                                matching: DateComponents(weekday: 1),
                                                matchingPolicy: .nextTime,
                                                direction: .backward)
                ?? startOfDay
            var ticks: [Date] = []
            for offset in 0...7 { // include phantom tick at +7 days
                if let day = calendar.date(byAdding: .day, value: offset, to: sundayStart) {
                    ticks.append(midday(day))
                }
            }
            return ticks
        case .month:
            // Day-of-month ticks with February rule: exclude 29 even in leap years
            guard let monthInterval = calendar.dateInterval(of: .month, for: position) else { return [] }
            // Include the 29th only for months with 30+ days. This excludes February (28/29).
            let daysRange = calendar.range(of: .day, in: .month, for: monthInterval.start) ?? 1..<32
            let include29 = daysRange.count >= 30
            let validDays: [Int] = include29 ? [1, 8, 15, 22, 29, 31] : [1, 8, 15, 22, 29]
            var ticks: [Date] = []
            let compsYM = calendar.dateComponents([.year, .month], from: monthInterval.start)
            for d in validDays {
                var c = compsYM
                c.day = d
                if let date = calendar.date(from: c), monthInterval.contains(date) {
                    ticks.append(midday(date))
                }
            }
            return ticks
        case .year:
            // Start at Jan 1 of current year
            var comps = calendar.dateComponents([.year], from: position)
            comps.month = 1
            comps.day = 1
            guard let yearStart = calendar.date(from: comps) else { return [] }
            var ticks: [Date] = []
            // 13 ticks: each month start plus phantom at next year's Jan 1
            for m in 0...12 {
                if let monthStart = calendar.date(byAdding: DateComponents(month: m), to: yearStart) {
                    let monthComps = calendar.dateComponents([.year, .month], from: monthStart)
                    let firstOfMonth = calendar.date(from: monthComps) ?? monthStart
                    ticks.append(midday(firstOfMonth))
                }
            }
            return ticks
        case .total:
            return []
        }
    }

    /// Returns a fixed X-axis domain for empty-state rendering so labels appear left-to-right.
    func fallbackXAxisDomain() -> ClosedRange<Date>? {
        var ticks = fallbackXAxisValues()
        guard !ticks.isEmpty else { return nil }
        ticks.sort() // ensure chronological order
        guard let first: Date = ticks.first, let last = ticks.last, first < last else { return nil }
        return first...last
    }
}
