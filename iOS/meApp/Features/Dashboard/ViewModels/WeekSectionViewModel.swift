//
//  WeekSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Foundation
import SwiftUI
import Charts

/// ViewModel specifically for the Week time period chart view
/// Handles all week-specific chart logic, scrolling, and day-based data processing
@MainActor
class WeekSectionViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var selectedPoint: BathScaleWeightSummary?
    @Published var selectedDate: Date?
    @Published var showCrosshair: Bool = false
    @Published var scrollPosition: Date = Date()
    @Published var isScrolling: Bool = false
    
    // MARK: - Chart Configuration
    private(set) var chartFrame: CGRect = .zero
    private(set) var yAxisDomain: ClosedRange<Double> = 0...100
    private(set) var yAxisTicks: [Double] = []
    
    // MARK: - Dependencies (injected from parent)
    private var dashboardStore: DashboardStore?
    
    // MARK: - Computed Properties
    
    /// All operations for week view (daily data)
    var chartOperations: [BathScaleWeightSummary] {
        return dashboardStore?.continuousOperations ?? []
    }
    
    /// Chart series data for week view
    var chartSeriesData: [GraphSeries] {
        guard let store = dashboardStore else { return [] }
        return store.chartSeriesData
    }
    
    /// Visible domain length for scrolling (1 week)
    var visibleDomainLength: TimeInterval {
        return dashboardStore?.visibleDomainLength(for: .week) ?? (7 * 24 * 60 * 60)
    }
    
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
    
    /// X-axis values with buffer for week period
    var xAxisValues: [Date] {
        guard let store = dashboardStore else { return [] }
        return store.xAxisValuesWithBuffer(for: .week)
    }
    
    /// Determines if the chart is scrolled to the leftmost boundary
    var isAtLeftBoundary: Bool {
        guard let store = dashboardStore, !chartOperations.isEmpty else { return true }
        
        let operations = chartOperations
        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min() else { return true }
        
        let domainLength = visibleDomainLength
        let visibleStart = scrollPosition.addingTimeInterval(-domainLength / 2)
        
        // Consider at boundary if visible start is at or before the minimum data date
        let boundaryThreshold: TimeInterval = 24 * 60 * 60 // 1 day
        return visibleStart <= minDate.addingTimeInterval(boundaryThreshold)
    }
    
    // MARK: - Initialization
    func configure(with store: DashboardStore) {
        self.dashboardStore = store
        self.scrollPosition = store.state.graph.xScrollPosition
        self.isScrolling = store.state.graph.isScrolling
        updateYAxisConfiguration()
    }
    
    // MARK: - Chart State Management
    
    /// Updates Y-axis configuration based on current data and goal
    func updateYAxisConfiguration() {
        guard let store = dashboardStore else { return }
        
        // Use visible operations for Y-axis calculation in week view (different from Total)
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
        
        self.yAxisDomain = yAxisScale.domain
        self.yAxisTicks = yAxisScale.ticks
    }
    
    /// Updates chart frame and recalculates Y-axis if needed
    func updateChartFrame(_ frame: CGRect) {
        self.chartFrame = frame
        // Recalculate Y-axis if frame changed significantly
        if abs(frame.height - chartFrame.height) > 10 {
            updateYAxisConfiguration()
        }
    }
    
    // MARK: - Scroll Management
    
    /// Handles scroll position changes
    func handleScrollPositionChange(_ newPosition: Date?) {
        guard let newPosition = newPosition else { return }
        self.scrollPosition = newPosition
        
        // Update dashboard store scroll position
        dashboardStore?.handleScrollPositionChange(newPosition)
    }
    
    /// Handles scroll start
    func handleScrollStart() {
        self.isScrolling = true
        dashboardStore?.handleScrollStart()
    }
    
    /// Handles scroll end
    func handleScrollEnd() {
        self.isScrolling = false
        dashboardStore?.handleScrollEndOptimized()
    }
    
    // MARK: - Selection Management
    
    /// Handles chart selection at a specific date
    func handleChartSelection(at date: Date?) {
        guard let date = date else {
            clearSelection()
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
    
    /// Calculates goal chip position for week view (with X-axis adjustment)
    func getGoalChipPosition() -> (yPosition: CGFloat, placement: GoalPlacement) {
        let goalWeight = self.goalWeight
        let domain = yAxisDomain
        
        // Calculate proportional position within the chart
        let domainRange = domain.upperBound - domain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }
        
        // Account for X-axis height for week period (18px adjustment)
        let availableChartHeight = chartFrame.height - 18
        
        // If goal weight is outside domain, show at edges
        if goalWeight > domain.upperBound {
            return (yPosition: -25, placement: .top)
        }
        
        if goalWeight < domain.lowerBound {
            return (yPosition: chartFrame.height + 20, placement: .bottom)
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
            return 32 // More space for 3+ digit values
        } else {
            return 28 // Less space for 1-2 digit values
        }
    }
    
    // MARK: - Chart Position Calculations
    
    /// Calculates chart position for a given date and value (for selection callout)
    func getChartPosition(for date: Date, value: Double) -> CGPoint? {
        guard chartFrame.width > 0 else { return nil }
        
        // For week period, use scroll-based calculation
        let xScrollPosition = self.scrollPosition
        let visibleDomainLength = self.visibleDomainLength
        guard visibleDomainLength.isFinite && visibleDomainLength > 0 else { return nil }
        
        let timeFromScrollPosition = date.timeIntervalSince(xScrollPosition)
        let xRatio = timeFromScrollPosition / visibleDomainLength
        let xPosition = chartFrame.width * xRatio
        
        // Calculate y position relative to y-axis domain
        let domainRange = yAxisDomain.upperBound - yAxisDomain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        // Account for X-axis height for week period (18px adjustment)
        let availableChartHeight = chartFrame.height + 18
        
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
    
    // MARK: - X-Axis Label Generation
    
    /// Formats X-axis label for week view (day names)
    func formatXAxisLabel(for date: Date) -> String? {
        return dashboardStore?.xLabelString(for: date, period: .week)
    }
    
    // MARK: - Chart Content Helpers
    
    /// Returns connected segments for line drawing (prevents gaps in data)
    func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        guard !dataPoints.isEmpty else { return [] }
        
        var segments: [[GraphSeries]] = []
        var currentSegment: [GraphSeries] = []
        
        let sortedPoints = dataPoints.sorted { $0.date < $1.date }
        let maxGap: TimeInterval = 14 * 24 * 60 * 60 // 14 days gap for week view (matches original week behavior)
        
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
    
    /// Returns appropriate point size for week view
    func getPointSizeForWeek() -> CGFloat {
        return 64 // Larger points for week view (daily data points)
    }
    
    /// Determines if chart data should animate based on scrolling state
    var shouldAnimateChartData: Bool {
        return !isScrolling && !chartOperations.isEmpty
    }
    
    // MARK: - Data Refresh
    
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
    
    /// Initialize chart for week section
    func initializeChart() {
        dashboardStore?.initializeChart()
    }
}
