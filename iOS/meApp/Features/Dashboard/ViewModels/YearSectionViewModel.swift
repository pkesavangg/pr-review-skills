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
    
    override var maxGapForConnectedSegments: TimeInterval {
        return 365 * 24 * 60 * 60 // 1 year gap for year view
    }

    /// Plot monthly aggregates midway between the labeled months.
    /// If a point belongs to September, it will be plotted at mid-September,
    /// visually falling between the Sep and Oct ticks.
    override func plotXDate(for original: Date) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Calendar.current.timeZone
        cal.locale = Calendar.current.locale

        // Start of the month at 00:00 local
        guard let monthStart = cal.dateInterval(of: .month, for: original)?.start else {
            return super.plotXDate(for: original)
        }

        // Compute mid-month by adding half the month length.
        // Use the end of month from dateInterval to get exact length.
        let monthInterval = cal.dateInterval(of: .month, for: original)!
        let half = monthInterval.duration / 2
        let midMonth = monthStart.addingTimeInterval(half)

        // Anchor to noon to avoid DST boundary issues
        if let noon = cal.date(bySettingHour: 12, minute: 0, second: 0, of: midMonth) {
            return noon
        }
        return midMonth
    }
}
