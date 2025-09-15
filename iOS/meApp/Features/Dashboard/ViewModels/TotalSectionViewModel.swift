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
    
    /// Number of months to expand domain for small/medium spans
    private static let domainExpansionMonths: Int = 3
    /// Number of months to expand domain for very large spans (> 2 years)
    private static let longSpanExpansionMonths: Int = 6
    
    /// Fallback time interval for domain expansion (90 days in seconds)
    private static let fallbackDomainExpansion: TimeInterval = 90 * 24 * 60 * 60
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .total
    }
    
    // MARK: - Connected Segments Caching
    private var cachedSegmentsHash: Int = 0
    private var cachedSegmentsResult: [[GraphSeries]] = []
    
    /// Connect across any gap in Total view
    override func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        // Create lightweight hash of the data to detect changes
        let currentHash = createDataHash(from: dataPoints)
        
        // Return cached result if data hasn't changed
        if currentHash == cachedSegmentsHash && !cachedSegmentsResult.isEmpty {
            return cachedSegmentsResult
        }
        
        // Calculate new result
        let sorted = dataPoints.sorted { $0.date < $1.date }
        let result = sorted.isEmpty ? [] : [sorted]
        
        // Cache the result
        cachedSegmentsHash = currentHash
        cachedSegmentsResult = result
        return result
    }
    
    /// Creates a lightweight hash from GraphSeries data for caching
    private func createDataHash(from dataPoints: [GraphSeries]) -> Int {
        var hasher = Hasher()
        hasher.combine(dataPoints.count)
        
        // Sample a few key points to create hash without iterating entire array
        if !dataPoints.isEmpty {
            let sortedData = dataPoints.sorted { $0.date < $1.date }
            hasher.combine(sortedData.first?.date.timeIntervalSince1970)
            hasher.combine(sortedData.last?.date.timeIntervalSince1970)
            
            // Add middle point if we have enough data
            if sortedData.count > 2 {
                let midIndex = sortedData.count / 2
                hasher.combine(sortedData[midIndex].date.timeIntervalSince1970)
            }
        }
        
        return hasher.finalize()
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

        let calendar = Calendar.current

        // Determine padding months based on overall span
        let yearDiff = calendar.dateComponents([.year], from: minDate, to: maxDate).year ?? 0
        let paddingMonths: Int
        if minDate == maxDate {
            // Single point → 3 months padding each side (existing behavior)
            paddingMonths = Self.domainExpansionMonths
        } else if yearDiff > 2 {
            // More than 2 years span → add 6 months padding on both sides
            paddingMonths = Self.longSpanExpansionMonths
        } else {
            // Up to and including 2 years span → add 3 months padding on both sides
            paddingMonths = Self.domainExpansionMonths
        }

        let expandedStart = calendar.date(byAdding: .month, value: -paddingMonths, to: minDate)
            ?? minDate.addingTimeInterval(-Self.fallbackDomainExpansion)
        let expandedEnd = calendar.date(byAdding: .month, value: paddingMonths, to: maxDate)
            ?? maxDate.addingTimeInterval(Self.fallbackDomainExpansion)

        return expandedStart...expandedEnd
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
        // Clear segments cache when reconfiguring
        invalidateSegmentsCache()
        updateYAxisConfiguration()
        // Sync with any existing cached Y-axis values from the store
        syncYAxisFromStore()
    }
    
    /// Total view selection behavior:
    /// - Only allow selection within the actual data range [firstPoint, lastPoint].
    /// - Snap the selection to the nearest real data point date.
    /// - Clear selection if tapping in padded areas outside data (domain padding exists in total view).
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard !chartOperations.isEmpty else {
            selectedDate = nil
            showCrosshair = false
            return
        }

        // Determine actual plotted data bounds (without padded domain)
        let ops = chartOperations.sorted { $0.date < $1.date }
        guard let first = ops.first?.date, let last = ops.last?.date else {
            selectedDate = nil
            showCrosshair = false
            return
        }

        // Allow a small right-edge slack so the last point is selectable
        // even if the tap falls slightly into the padded area.
        let paddedUpper = self.dateRange.upperBound
        let rightPadding = max(0, paddedUpper.timeIntervalSince(last))
        let rightSlack = min(rightPadding * 0.5, 14 * 24 * 60 * 60) // up to 14 days or half the padding

        guard date >= first && date <= last.addingTimeInterval(rightSlack) else {
            selectedDate = nil
            showCrosshair = false
            return
        }

        // Clamp comparison baseline within [first, last] to bias toward the last point
        let clampedDate = min(max(date, first), last)

        // Snap to the nearest real data point date
        if let nearest = ops.min(by: { a, b in
            abs(a.date.timeIntervalSince(clampedDate)) < abs(b.date.timeIntervalSince(clampedDate))
        }) {
            selectedDate = nearest.date
            showCrosshair = true
            // Let DashboardStore update metrics via handleChartSelection(at:)
        } else {
            selectedDate = nil
            showCrosshair = false
        }
    }

    override func getChartPosition(for date: Date, value: Double) -> CGPoint? {
        guard chartFrame.width > 0 else { return nil }

        // Use the padded dateRange to align overlay positions with Chart plotting
        let domain = self.dateRange
        let totalTimeRange = domain.upperBound.timeIntervalSince(domain.lowerBound)
        let xPosition: CGFloat
        if totalTimeRange > 0 {
            let timeFromStart = date.timeIntervalSince(domain.lowerBound)
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
    
    /// Invalidates the segments cache when data changes
    private func invalidateSegmentsCache() {
        cachedSegmentsHash = 0
        cachedSegmentsResult = []
    }
    
    /// Override to invalidate segments cache when data refreshes
    override func refreshData() {
        invalidateSegmentsCache()
        super.refreshData()
    }
    
    /// Override to invalidate segments cache when settings change
    override func handleSettingsChange() {
        invalidateSegmentsCache()
        super.handleSettingsChange()
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
