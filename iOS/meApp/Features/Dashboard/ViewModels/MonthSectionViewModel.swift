//
//  MonthSectionViewModel.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Charts
import Foundation
import SwiftUI

/// Cached gregorian calendar configured with the current locale/timezone.
/// `plotXDate(for:)` is invoked once per cached point on every chart cache
/// refresh; allocating a fresh `Calendar(identifier:)` per call showed up
/// in the same `_LocaleICU.minimumDaysInFirstWeek` lookup pattern the April
/// investigation patched on the chart render path.
private let monthPlotCalendar: Calendar = {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = Calendar.current.timeZone
    cal.locale = Calendar.current.locale
    return cal
}()

/// ViewModel specifically for the Month time period chart view
/// Handles all month-specific chart logic, scrolling, and day-based data processing
@MainActor
final class MonthSectionViewModel: BaseSectionViewModel {

    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .month
    }

    /// Align plotted daily points to local noon so they overlap month X-axis Sunday ticks,
    /// which are generated at local noon.
    override func plotXDate(for original: Date) -> Date {
        let cal = localCalendar
        let dayStart = cal.startOfDay(for: original)
        guard let noon = cal.date(byAdding: .hour, value: 12, to: dayStart) else {
            return super.plotXDate(for: original)
        }
        return noon
    }

    // Month selection rules:
    // - Determine the current X-axis section [startTick, endTick) using Sunday month ticks.
    // - If there are chart points within this section, select the nearest point to the touch inside the section.
    // - If there are no points inside the section, select the section's start tick (e.g., Jul 8).
    // - Crosshair only shows when the touch is within [firstPoint, lastPoint].
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    override func handleChartSelection(at date: Date?) {
        guard let date = date else { return }
        guard dashboardStore != nil else { return }

        // Build the effective X dates actually plotted for the weight series
        let effectiveDates = chartSeriesData
            .map { plotXDate(for: $0.date) }
            .sorted()

        guard let first = effectiveDates.first, let last = effectiveDates.last else {
            // No data → hide selection
            clearSelection()
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
            clearSelection()
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
                selectedPoint = closestOperation(to: fallback)
            } else {
                clearSelection()
            }
            return
        }
        // If the chosen section starts strictly after the last data point, suppress selection
        if startTick > last {
            clearSelection()
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
        let candidates = effectiveDates.filter { date in date >= startTick && date < sectionEnd }

        if candidates.isEmpty {
            // No data in section → select the start tick
            selectedDate = startTick
            showCrosshair = true
            selectedPoint = nil
        } else {
            // Find nearest data point in the section
            let nearestCandidate = candidates.min { first, second in
                let firstDistance = abs(first.timeIntervalSince(clampedDate))
                let secondDistance = abs(second.timeIntervalSince(clampedDate))
                if firstDistance == secondDistance { return first < second }
                return firstDistance < secondDistance
            }

            // Also find nearest grid tick within the current section
            let realTicks: [Date] = allTicks.count > 1 ? Array(allTicks.dropLast()) : allTicks
            let sectionTicks = realTicks.filter { $0 >= startTick && $0 < sectionEnd }
            let nearestTick = sectionTicks.min {
                abs($0.timeIntervalSince(clampedDate)) < abs($1.timeIntervalSince(clampedDate))
            }

            // Select whichever is closer: the grid tick or the data point.
            // On ties, prefer the tick so grid lines are always selectable.
            if let candidate = nearestCandidate, let tick = nearestTick {
                let distToCandidate = abs(candidate.timeIntervalSince(clampedDate))
                let distToTick = abs(tick.timeIntervalSince(clampedDate))
                selectedDate = distToTick <= distToCandidate ? tick : candidate
            } else {
                selectedDate = nearestCandidate ?? startTick
            }
            showCrosshair = true
        }
    }

}
