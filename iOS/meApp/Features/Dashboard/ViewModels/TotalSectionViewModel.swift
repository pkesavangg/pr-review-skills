//
//  TotalSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Charts
import Foundation
import SwiftUI

/// ViewModel specifically for the Total time period chart view
/// Handles all total-specific chart logic, state management, and data processing
@MainActor
final class TotalSectionViewModel: BaseSectionViewModel {
    
    // MARK: - Constants
    
    /// Number of months to expand domain for small spans (< 1 year)
    private static let domainExpansionMonths: Int = 3
    
    /// Fallback time interval for domain expansion (90 days in seconds)
    private static let fallbackDomainExpansion: TimeInterval = 90 * 24 * 60 * 60
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .total
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
        guard let store = dashboardStore else {
            // Empty-state: provide a non-zero domain so leading/trailing baselines render
            // Center around current scroll position to keep UX consistent with other sections
            let center = scrollPosition
            let halfWindow: TimeInterval = 24 * 60 * 60 // 1 day
            return center.addingTimeInterval(-halfWindow)...center.addingTimeInterval(halfWindow)
        }

        let bounds: (min: Date, max: Date)
        if let cachedBounds = store.dataManager.getDateBounds(for: timePeriod) {
            bounds = cachedBounds
        } else {
            let operations = store.continuousOperations
            guard let minDate = operations.first?.date, let maxDate = operations.last?.date else {
                let center = scrollPosition
                let halfWindow: TimeInterval = 24 * 60 * 60
                return center.addingTimeInterval(-halfWindow)...center.addingTimeInterval(halfWindow)
            }
            bounds = (minDate, maxDate)
        }

        let minDate = bounds.min
        let maxDate = bounds.max
        let calendar = Calendar.current

        // Calculate the actual data span in years
        let yearDiff = calendar.dateComponents([.year], from: minDate, to: maxDate).year ?? 0
        
        // Determine padding based on span length
        let paddingMonths: Int
        if minDate == maxDate {
            // Single point → 3 months padding each side
            paddingMonths = Self.domainExpansionMonths
        } else if yearDiff < 1 {
            // Less than 1 year → 3 months padding each side
            paddingMonths = Self.domainExpansionMonths
        } else if yearDiff < 10 {
            // 1 to <10 years → 6 months padding each side
            paddingMonths = 6
        } else if yearDiff < 50 {
            // 10 to <50 years → 2.5 years (30 months) padding each side
            paddingMonths = 30
        } else {
            // 50+ years → 5 years (60 months) padding each side
            paddingMonths = 60
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
        let yAxisScale = store.yAxisScale(for: operations, chartHeight: chartFrame.height)
        
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
    
    /// Total view selection behavior:
    /// - Only allow selection within the actual data range [firstPoint, lastPoint].
    /// - Snap the selection to the nearest real data point date.
    /// - Clear selection if tapping in padded areas outside data (domain padding exists in total view).
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard !chartSeriesData.isEmpty else {
            selectedDate = nil
            showCrosshair = false
            return
        }

        // Snap against the actual plotted X values, not the raw operation dates.
        // This matters for BPM total/year charts where the chart renders month aggregates.
        let plottedDates = Array(Set(chartSeriesData.map { plotXDate(for: $0.date) })).sorted()
        guard let first = plottedDates.first, let last = plottedDates.last else {
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
        if let nearest = plottedDates.min(by: { firstDate, secondDate in
            abs(firstDate.timeIntervalSince(clampedDate)) < abs(secondDate.timeIntervalSince(clampedDate))
        }) {
            selectedDate = nearest
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
        let effectiveDate = plotXDate(for: date)
        let xPosition: CGFloat
        if totalTimeRange > 0 {
            let timeFromStart = effectiveDate.timeIntervalSince(domain.lowerBound)
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
