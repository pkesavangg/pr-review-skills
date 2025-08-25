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
}
