//
//  TotalSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Foundation
import SwiftUI
import Charts

/// ViewModel specifically for the Total time period chart view
/// Handles all total-specific chart logic, state management, and data processing
@MainActor
class TotalSectionViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var selectedPoint: BathScaleWeightSummary?
    @Published var selectedDate: Date?
    @Published var showCrosshair: Bool = false
    
    // MARK: - Chart Configuration
    private(set) var chartFrame: CGRect = .zero
    private(set) var yAxisDomain: ClosedRange<Double> = 0...100
    private(set) var yAxisTicks: [Double] = []
    
    // MARK: - Dependencies (injected from parent)
    private var dashboardStore: DashboardStore?
    
    // MARK: - Computed Properties
    
    /// All operations for total view (no filtering)
    var chartOperations: [BathScaleWeightSummary] {
        return dashboardStore?.continuousOperations ?? []
    }
    
    /// Chart series data for total view
    var chartSeriesData: [GraphSeries] {
        guard let store = dashboardStore else { return [] }
        return store.chartSeriesData
    }
    
    /// Full date range for X-axis domain
    var dateRange: ClosedRange<Date> {
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
    
    // MARK: - Initialization
    func configure(with store: DashboardStore) {
        self.dashboardStore = store
        updateYAxisConfiguration()
    }
    
    // MARK: - Chart State Management
    
    /// Updates Y-axis configuration based on current data and goal
    func updateYAxisConfiguration() {
        guard let store = dashboardStore else { return }
        
        // Use all operations for Y-axis calculation in total view
        let operations = chartOperations
        
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
    
    /// Calculates goal chip position for total view (no X-axis)
    func getGoalChipPosition() -> (yPosition: CGFloat, placement: GoalPlacement) {
        let goalWeight = self.goalWeight
        let domain = yAxisDomain
        
        // Calculate proportional position within the chart
        let domainRange = domain.upperBound - domain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }
        
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
        
        // Calculate position within full chart area (no X-axis adjustment for total)
        let yPosition = chartFrame.height * (1 - yRatio) // Invert because chart y grows downward
        
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
        let operations = chartOperations
        guard !operations.isEmpty, chartFrame.width > 0 else { return nil }
        
        // For total period, calculate position based on actual data range
        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return nil }
        
        let xPosition: CGFloat
        let totalTimeRange = maxDate.timeIntervalSince(minDate)
        if totalTimeRange > 0 {
            let timeFromStart = date.timeIntervalSince(minDate)
            let xRatio = timeFromStart / totalTimeRange
            xPosition = chartFrame.width * xRatio
        } else {
            xPosition = chartFrame.width / 2 // Single point, center it
        }
        
        // Calculate y position relative to y-axis domain
        let domainRange = yAxisDomain.upperBound - yAxisDomain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        let yRatio = (value - yAxisDomain.lowerBound) / domainRange
        guard yRatio.isFinite else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        // Calculate position within full chart area (no X-axis adjustment for total)
        let yPosition = chartFrame.height * (1 - yRatio) // Invert because chart y grows downward
        
        return CGPoint(x: xPosition, y: yPosition)
    }
    
    // MARK: - Chart Content Helpers
    
    /// Returns connected segments for line drawing (prevents gaps in data)
    func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        guard !dataPoints.isEmpty else { return [] }
        
        var segments: [[GraphSeries]] = []
        var currentSegment: [GraphSeries] = []
        
        let sortedPoints = dataPoints.sorted { $0.date < $1.date }
        let maxGap: TimeInterval = 365 * 24 * 60 * 60 // 1 year gap for total view
        
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
    
    /// Returns appropriate point size for total view
    func getPointSizeForTotal() -> CGFloat {
        return 16 // Smaller points for total view (many data points)
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
}

// MARK: - Goal Placement Enum
enum GoalPlacement {
    case top
    case bottom
    case middle
}
