//
//  YearSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Foundation
import SwiftUI
import Charts

/// ViewModel specifically for the Year time period chart view
/// Handles all year-specific chart logic, scrolling, and month-based data processing
@MainActor
final class YearSectionViewModel: BaseSectionViewModel {
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .year
    }
    
    /// Connect across any gap in Year view
    override func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        let sorted = dataPoints.sorted { $0.date < $1.date }
        return sorted.isEmpty ? [] : [sorted]
    }

    /// Snap to nearest month tick; only show crosshair if the snapped date lies
    /// within the range of plotted data (first..last). Otherwise hide it.
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard dashboardStore != nil else { return }

        // Exclude trailing phantom tick when snapping
        let ticks = xAxisValues
        let realTicks: [Date] = ticks.count > 1 ? Array(ticks.dropLast()) : ticks
        guard !realTicks.isEmpty else { return }

        // Snap to nearest tick by absolute time distance
        let snapped = realTicks.min { a, b in
            abs(a.timeIntervalSince(date)) < abs(b.timeIntervalSince(date))
        } ?? date

        // Determine first/last plotted months and only show selection if snapped month
        // lies within [firstMonth, lastMonth]. Otherwise, hide it (no line area).
        let effectiveDates = chartOperations
            .map { plotXDate(for: $0.date) }
            .sorted()
        
        if let first = effectiveDates.first, let last = effectiveDates.last {
            let cal = Calendar.current
            let snappedMonth = cal.date(from: cal.dateComponents([.year, .month], from: snapped)) ?? snapped
            let firstMonth = cal.date(from: cal.dateComponents([.year, .month], from: first)) ?? first
            let lastMonth = cal.date(from: cal.dateComponents([.year, .month], from: last)) ?? last
            
            if snappedMonth >= firstMonth && snappedMonth <= lastMonth {
                selectedDate = snappedMonth
                showCrosshair = true
            } else {
                selectedDate = nil
                showCrosshair = false
            }
        } else {
            selectedDate = nil
            showCrosshair = false
        }
    }
}
