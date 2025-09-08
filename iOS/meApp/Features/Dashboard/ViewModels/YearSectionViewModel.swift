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
}
