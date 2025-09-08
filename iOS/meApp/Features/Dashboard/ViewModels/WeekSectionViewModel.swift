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
final class WeekSectionViewModel: BaseSectionViewModel {
    
    // MARK: - Period-specific properties
    override var timePeriod: TimePeriod {
        return .week
    }
    
    override var maxGapForConnectedSegments: TimeInterval {
        return 14 * 24 * 60 * 60 // 14 days gap for week view
    }

    /// Plot daily aggregates midway between adjacent day ticks.
    /// Our X-axis ticks for week are anchored at local noon for each day (see
    /// `generateVisibleWeeklyXAxisWithBuffer`). To render a day's value between
    /// its label and the next day's label, we use the midpoint between the two
    /// consecutive noons, which typically lands at local midnight between them
    /// (DST-safe).
    override func plotXDate(for original: Date) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Calendar.current.timeZone
        cal.locale = Calendar.current.locale

        let dayStart = cal.startOfDay(for: original)
        guard let noon = cal.date(byAdding: .hour, value: 12, to: dayStart),
              let nextNoon = cal.date(byAdding: .hour, value: 0, to: noon) else {
            return super.plotXDate(for: original)
        }

        let half = nextNoon.timeIntervalSince(noon) / 2
        return noon.addingTimeInterval(half)
    }
}
