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
    
    override var maxGapForConnectedSegments: TimeInterval {
        return 60 * 24 * 60 * 60 // 60 days gap for month view
    }
}
