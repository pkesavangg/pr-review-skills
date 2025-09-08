//
//  MonthSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Foundation
import SwiftUI
import Charts

/// ViewModel specifically for the Month time period chart view
/// Handles all month-specific chart logic, scrolling, and day-based data processing
@MainActor
final class MonthSectionViewModel: BaseSectionViewModel {
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .month
    }
    
    /// Connect across any gap in Month view
    override func getConnectedSegments(from dataPoints: [GraphSeries]) -> [[GraphSeries]] {
        let sorted = dataPoints.sorted { $0.date < $1.date }
        return sorted.isEmpty ? [] : [sorted]
    }

    /// Month selection rules:
    /// - Determine the current X-axis section [startTick, endTick) using month ticks (1, 8, 15, 22, 29).
    /// - If there are chart points within this section, select the nearest point to the touch inside the section.
    /// - If there are no points inside the section, select the section's start tick (e.g., Jul 8).
    /// - Crosshair only shows when the touch is within [firstPoint, lastPoint].
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard dashboardStore != nil else { return }

        // Build the effective X dates actually plotted for the weight series
        let effectiveDates = chartSeriesData
            .map { plotXDate(for: $0.date) }
            .sorted()

        guard let first = effectiveDates.first, let last = effectiveDates.last else {
            // No data → hide selection
            selectedDate = nil
            showCrosshair = false
            return
        }
        // Only allow selection near the plotted data range. Permit a small right-side slack
        // (half a section length) to account for UTC offsets and human inaccuracy.
        let allTicks = xAxisValues
        let defaultSectionLen: TimeInterval = 7 * 24 * 60 * 60
        let lastSectionLen: TimeInterval = {
            if let lastTick = allTicks.last, allTicks.count >= 2 {
                let prevTick = allTicks[allTicks.count - 2]
                return max(lastTick.timeIntervalSince(prevTick), 24 * 60 * 60)
            }
            return defaultSectionLen
        }()
        let rightSlack = lastSectionLen * 0.5
        guard date >= first && date <= last.addingTimeInterval(rightSlack) else {
            selectedDate = nil
            showCrosshair = false
            return
        }

        // Clamp selection to visible domain (with right bound at the last X tick)
        let rightBound = allTicks.last ?? last
        let clampedDate = min(max(date, first), rightBound)

        // Determine the section [startTick, endTick) using X-axis ticks
        let startCandidates: [Date] = allTicks.count > 1 ? Array(allTicks.dropLast()) : allTicks
        guard let startTick = startCandidates.last(where: { $0 <= clampedDate }) ?? startCandidates.first else {
            // Fallback: behave like left-bias floor on all points
            if let fallback = effectiveDates.last(where: { $0 <= clampedDate }) ?? effectiveDates.last ?? effectiveDates.first {
                selectedDate = fallback
                showCrosshair = true
            } else {
                selectedDate = nil
                showCrosshair = false
            }
            return
        }
        // If the chosen section starts strictly after the last data point, suppress selection
        if startTick > last {
            selectedDate = nil
            showCrosshair = false
            return
        }
        let startIndex = allTicks.lastIndex(of: startTick) ?? 0
        let sectionEnd: Date = {
            if startIndex + 1 < allTicks.count {
                return allTicks[startIndex + 1] // this may be the phantom tick, which is desired
            }
            // Estimate end using previous section length or default 7 days
            let defaultLen: TimeInterval = 7 * 24 * 60 * 60
            if startIndex > 0 {
                let prevLen = allTicks[startIndex].timeIntervalSince(allTicks[startIndex - 1])
                return allTicks[startIndex].addingTimeInterval(max(prevLen, 24 * 60 * 60))
            }
            return startTick.addingTimeInterval(defaultLen)
        }()

        // Candidates within the section
        let candidates = effectiveDates.filter { d in d >= startTick && d < sectionEnd }

        if candidates.isEmpty {
            // No data in section → select the start tick
            selectedDate = startTick
            showCrosshair = true
        } else {
            // Pick the nearest candidate inside the section
            let chosen = candidates.min { a, b in
                abs(a.timeIntervalSince(clampedDate)) < abs(b.timeIntervalSince(clampedDate))
            } ?? candidates.first!
            selectedDate = chosen
            showCrosshair = true
        }
        // Do not compute selectedPoint here; DashboardStore updates metrics using nearest point
    }
}
