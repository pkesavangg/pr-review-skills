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
}
