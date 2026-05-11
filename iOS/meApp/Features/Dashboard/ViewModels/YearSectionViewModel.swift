//
//  YearSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Charts
import Foundation
import SwiftUI

/// ViewModel specifically for the Year time period chart view
/// Handles all year-specific chart logic, scrolling, and month-based data processing
@MainActor
final class YearSectionViewModel: BaseSectionViewModel {
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .year
    }

    /// Align plotted monthly points to the same month-start noon positions used by
    /// year-view tick generation and selection snapping.
    override func plotXDate(for original: Date) -> Date {
        let cal = localCalendar
        var components = cal.dateComponents([.year, .month], from: original)
        components.day = 1
        components.hour = 12
        components.minute = 0
        components.second = 0
        return cal.date(from: components) ?? super.plotXDate(for: original)
    }

    /// Keep year scrolling quantized to month boundaries during drag updates so
    /// live drag behavior matches final settled snapping.
    override func handleScrollPositionChange(_ newPosition: Date?) {
        guard let newPosition = newPosition else { return }
        let snapped = dashboardStore?.graphManager.snapScrollPosition(newPosition, for: .year) ?? newPosition
        super.handleScrollPositionChange(snapped)
    }

    /// Snap to nearest month tick; only show crosshair if the snapped date lies
    /// within the range of plotted data (first..last). Otherwise hide it.
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard let store = dashboardStore else { return }

        // Exclude trailing phantom tick when snapping
        let ticks = xAxisValues
        let realTicks: [Date] = ticks.count > 1 ? Array(ticks.dropLast()) : ticks
        guard !realTicks.isEmpty else { return }

        // Snap to nearest tick by absolute time distance
        let snappedTick = realTicks.min { firstTick, secondTick in
            abs(firstTick.timeIntervalSince(date)) < abs(secondTick.timeIntervalSince(date))
        } ?? date
        let snapped = store.graphManager.snapScrollPosition(snappedTick, for: .year)

        // Determine first/last plotted months and only show selection if snapped month
        // lies within [firstMonth, lastMonth]. Otherwise, hide it (no line area).
        let effectiveDates = chartOperations
            .map { plotXDate(for: $0.date) }
            .sorted()
        
        if let first = effectiveDates.first, let last = effectiveDates.last {
            let firstMonth = store.graphManager.snapScrollPosition(first, for: .year)
            let lastMonth = store.graphManager.snapScrollPosition(last, for: .year)
            
            if snapped >= firstMonth && snapped <= lastMonth {
                selectedDate = snapped
                selectedPoint = chartOperations.first {
                    Calendar.current.isDate($0.date, equalTo: snapped, toGranularity: .month)
                }
                showCrosshair = true
            } else {
                selectedDate = nil
                selectedPoint = nil
                showCrosshair = false
            }
        } else {
            selectedDate = nil
            selectedPoint = nil
            showCrosshair = false
        }
    }
}
