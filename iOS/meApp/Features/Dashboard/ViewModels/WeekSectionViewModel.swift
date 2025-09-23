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
final class WeekSectionViewModel: BaseSectionViewModel, Equatable {
    
    static func == (lhs: WeekSectionViewModel, rhs: WeekSectionViewModel) -> Bool {
        // Compare essential properties that affect rendering
        lhs.timePeriod == rhs.timePeriod &&
        lhs.selectedDate == rhs.selectedDate &&
        lhs.showCrosshair == rhs.showCrosshair &&
        lhs.scrollPosition == rhs.scrollPosition &&
        lhs.isScrolling == rhs.isScrolling &&
        lhs.yAxisDomain == rhs.yAxisDomain &&
        lhs.yAxisTicks == rhs.yAxisTicks &&
        lhs.chartFrame == rhs.chartFrame &&
        lhs.dashboardStore === rhs.dashboardStore  // Reference equality for store
    }
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .week
    }
    

    /// Returns the X-axis date used to plot a single-day aggregate in Week view.
    /// We place each day's value at that day's local noon:
    /// - Visually centers the point within the day's time span on the timeline.
    /// - Avoids DST boundary issues since noon is safely within the day.
    /// The week chart's X-axis ticks are generated in local time as well, so
    /// aligning points to local noon keeps them consistently aligned with labels.
    override func plotXDate(for original: Date) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Calendar.current.timeZone
        cal.locale = Calendar.current.locale

        let dayStart = cal.startOfDay(for: original)
        guard let noon = cal.date(byAdding: .hour, value: 12, to: dayStart) else {
            return super.plotXDate(for: original)
        }
        return noon
    }

    /// Override selection to snap to nearest day tick (noon) and
    /// hide the crosshair if the snapped day has no data.
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard dashboardStore != nil else { return }

        // Exclude the trailing phantom tick when snapping
        let ticks = xAxisValues
        let realTicks: [Date] = ticks.count > 1 ? Array(ticks.dropLast()) : ticks
        guard !realTicks.isEmpty else { return }

        // Snap to nearest tick by absolute time distance
        let snapped = realTicks.min { a, b in
            abs(a.timeIntervalSince(date)) < abs(b.timeIntervalSince(date))
        } ?? date

        // Determine whether the snapped X falls within the drawn line bounds.
        // For week view a continuous line exists only between the first and last points.
        // Show crosshair if snapped is within [firstPoint, lastPoint], else hide it.
        let effectiveDates = chartOperations
            .map { plotXDate(for: $0.date) }
            .sorted()

        if let first = effectiveDates.first, let last = effectiveDates.last {
            if snapped >= first && snapped <= last {
                selectedDate = snapped
                showCrosshair = true
            } else {
                selectedDate = nil
                showCrosshair = false
            }
        } else {
            // No data → hide selection
            selectedDate = nil
            showCrosshair = false
        }
        // Do not compute selectedPoint here; DashboardStore will update metrics using nearest point
    }

}
