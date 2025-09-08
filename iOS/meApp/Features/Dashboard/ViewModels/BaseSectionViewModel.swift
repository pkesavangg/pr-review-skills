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
    
    var maxGapForConnectedSegments: TimeInterval {
        fatalError("Must be overridden by subclass")
    }
    
    var pointSize: CGFloat {
        return 64 // Default point size
    }
    
    var hasXAxis: Bool {
        return timePeriod != .total // Total period has no X-axis
    }

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
    
    /// Goal weight for display
    var goalWeight: Double {
        return dashboardStore?.goalWeightForDisplay ?? 0
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
        guard let store = dashboardStore else { return [] }
        // Use the live scroll position from this view model so axis ticks (including
        // trailing phantom ticks) reflect the current gesture immediately and avoid
        // the "jump" when scroll ends.
        let liveScrollPosition = self.scrollPosition
        return store.graphManager.generateVisibleXAxisValues(
            for: timePeriod,
            from: store.continuousOperations,
            scrollPosition: liveScrollPosition
        )
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
        
        // Use visible operations for Y-axis calculation (different from Total)
        let operations = store.visibleOperations.isEmpty ? chartOperations : store.visibleOperations
        
        // Get Y-axis scale from graph manager
        let yAxisScale = store.graphManager.getYAxisScale(
            from: operations,
            goalWeight: goalWeight,
            isWeightlessMode: store.isWeightlessModeEnabled,
            anchorWeight: store.weightlessAnchorWeight,
            convertWeight: store.goalManager.convertWeightToDisplay,
            chartHeight: chartFrame.height
        )
        
        // Animate domain changes only
        withAnimation(.easeInOut(duration: 0.25)) {
            self.yAxisDomain = yAxisScale.domain
        }
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
        let goalWeight = self.dashboardStore?.roundedGoalWeight(self.goalWeight) ?? 0
        let domain = yAxisDomain
        
        // Calculate proportional position within the chart
        let domainRange = domain.upperBound - domain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }
        
        // Account for X-axis height if this period has X-axis (18px adjustment)
        let availableChartHeight = chartFrame.height - (18)
        
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
        guard let store = dashboardStore else { return 28 }
        
        let formattedText = store.formatYAxisTickLabel(goalWeight)
        
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
        
        // Account for X-axis height if this period has X-axis (18px adjustment)
        let availableChartHeight = chartFrame.height - (hasXAxis ? 18 : 0)
        
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
        return dashboardStore?.xLabelString(for: date, period: timePeriod)?.lowercased()
    }
    
    func formatSelectedXAxisLabel() -> String? {
        guard
            let store = dashboardStore,
            let date = store.state.graph.selectedPoint?.date 
        else { return nil }

        return dashboardStore?.graphManager.formatSelectedDate(date, for: store.state.graph.selectedPeriod)
    }
    
    // MARK: - Chart Content Helpers
    
    /// Returns connected segments for line drawing (prevents gaps in data)
    func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        guard !dataPoints.isEmpty else { return [] }
        
        var segments: [[GraphSeries]] = []
        var currentSegment: [GraphSeries] = []
        
        let sortedPoints = dataPoints.sorted { $0.date < $1.date }
        let maxGap = maxGapForConnectedSegments
        
        for point in sortedPoints {
            if currentSegment.isEmpty {
                currentSegment.append(point)
            } else {
                let lastPoint = currentSegment.last!
                let timeDifference = point.date.timeIntervalSince(lastPoint.date)
                
                if timeDifference <= maxGap {
                    // Continue current segment
                    currentSegment.append(point)
                } else {
                    // Start new segment due to gap
                    if !currentSegment.isEmpty {
                        segments.append(currentSegment)
                    }
                    currentSegment = [point]
                }
            }
        }
        
        // Add the last segment
        if !currentSegment.isEmpty {
            segments.append(currentSegment)
        }
        
        return segments
    }
    
    /// Determines if chart data should animate based on scrolling state
    var shouldAnimateChartData: Bool {
        return !isScrolling && !chartOperations.isEmpty
    }
    
    // MARK: - Data Management
    
    /// Called when data changes to update chart state
    func refreshData() {
        updateYAxisConfiguration()
        // Maintain selection if still valid
        if let selectedDate = selectedDate {
            handleChartSelection(at: selectedDate)
        }
    }
    
    /// Called when settings change (unit, weightless mode, etc.)
    func handleSettingsChange() {
        updateYAxisConfiguration()
        clearSelection() // Clear selection as values may have changed
    }
    
    /// Called when scroll position is updated programmatically
    func updateScrollPosition(to position: Date) {
        self.scrollPosition = position
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
            // Animate domain transition when cache updates
            withAnimation(.easeInOut(duration: 0.25)) {
                self.yAxisDomain = cachedDomain
            }
        }

        if let cachedTicks = store.state.graph.cachedYAxisTicks {
            // Suppress animation for tick changes
            withTransaction(Transaction(animation: nil)) {
                self.yAxisTicks = cachedTicks
            }
        }
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
}
