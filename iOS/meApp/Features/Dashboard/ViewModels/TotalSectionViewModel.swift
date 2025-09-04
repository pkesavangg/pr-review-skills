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
final class TotalSectionViewModel: BaseSectionViewModel {
    
    // MARK: - Constants
    
    /// Number of days to expand domain when there's only one data point
    private static let domainExpansionDays: Int = 3
    
    /// Fallback time interval for domain expansion (90 days in seconds)
    private static let fallbackDomainExpansion: TimeInterval = 90 * 24 * 60 * 60
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .total
    }
    
    /// Connect across any gap in Total view
    override func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        let sorted = dataPoints.sorted { $0.date < $1.date }
        return sorted.isEmpty ? [] : [sorted]
    }
    
    override var hasXAxis: Bool {
        return false // Total period has no X-axis
    }
    
    override var pointSize: CGFloat {
        return 16 // Smaller points for total view (many data points)
    }

    // Explicitly restate for clarity (optional overrides)
    override var lineWidth: CGFloat { 2 }
    override var basePointDiameter: CGFloat { 4 }
    override var selectedPointDiameter: CGFloat { 8 }
    
    // MARK: - Total-specific properties
    
    /// Full date range for X-axis domain
    override var dateRange: ClosedRange<Date> {
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

        // If there is only one point, expand the domain by 3 months on both sides
        if minDate == maxDate {
            let calendar = Calendar.current
            let expandedStart = calendar.date(byAdding: .month, value: -Self.domainExpansionDays, to: minDate)
                ?? minDate.addingTimeInterval(-Self.fallbackDomainExpansion)
            let expandedEnd = calendar.date(byAdding: .month, value: Self.domainExpansionDays, to: maxDate)
                ?? maxDate.addingTimeInterval(Self.fallbackDomainExpansion)
            return expandedStart...expandedEnd
        }

        return minDate...maxDate
    }
    
    /// Always at left boundary for total view (no scrolling)
    override var isAtLeftBoundary: Bool {
        return true
    }
    
    /// Never animate scrolling for total view since there's no scrolling
    override var shouldAnimateChartData: Bool {
        return !chartOperations.isEmpty // Always allow animation for total view
    }
    
    // MARK: - Overridden methods for total-specific behavior
    
    override func updateYAxisConfiguration() {
        guard let store = dashboardStore else { return }
        
        // Use all operations for Y-axis calculation in total view (different from scrolling views)
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
    
    override func configure(with store: DashboardStore) {
        self.dashboardStore = store
        // No scroll positioning for total view - use store's current position
        self.scrollPosition = store.state.graph.xScrollPosition
        self.isScrolling = store.state.graph.isScrolling
        updateYAxisConfiguration()
        // Sync with any existing cached Y-axis values from the store
        syncYAxisFromStore()
    }
    
    override func getChartPosition(for date: Date, value: Double) -> CGPoint? {
        guard chartFrame.width > 0 else { return nil }
        
        // For TOTAL period, calculate position based on actual data range
        let allOperations = chartOperations
        guard !allOperations.isEmpty else { return nil }
        
        let allDates = allOperations.map { $0.date }
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return nil }
        
        let totalTimeRange = maxDate.timeIntervalSince(minDate)
        let xPosition: CGFloat
        if totalTimeRange > 0 {
            let timeFromStart = date.timeIntervalSince(minDate)
            let xRatio = timeFromStart / totalTimeRange
            xPosition = chartFrame.width * xRatio
        } else {
            xPosition = chartFrame.width > 0 ? chartFrame.width / 2 : 0 // Single point, center it
        }
        
        // Calculate y position relative to y-axis domain
        let domainRange = yAxisDomain.upperBound - yAxisDomain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        // No X-axis adjustment needed for total period
        let availableChartHeight = chartFrame.height
        
        let yRatio = (value - yAxisDomain.lowerBound) / domainRange
        guard yRatio.isFinite else {
            return CGPoint(x: xPosition, y: chartFrame.height / 2)
        }
        
        // Calculate position within the available chart area
        let yPosition = (availableChartHeight * (1 - yRatio)) // Invert because chart y grows downward
        
        // Add padding offsets for left boundary
        let adjustedX = xPosition + 4 // spacingXS approximation
        let adjustedY = yPosition
        
        return CGPoint(x: adjustedX, y: adjustedY)
    }
    
    // MARK: - Total-specific methods
    
    /// Returns appropriate point size for total view
    func getPointSizeForTotal() -> CGFloat {
        return pointSize // Use the base point size
    }
    
    // MARK: - No-op methods for total view (no scrolling)
    override func handleScrollPositionChange(_ newPosition: Date?) {
        // Total view doesn't scroll - no-op
    }
    
    override func handleScrollStart() {
        // Total view doesn't scroll - no-op
    }
    
    override func handleScrollEnd() {
        // Total view doesn't scroll - no-op
    }
    
    override func updateScrollPosition(to position: Date) {
        // Total view doesn't scroll - no-op
    }
}
