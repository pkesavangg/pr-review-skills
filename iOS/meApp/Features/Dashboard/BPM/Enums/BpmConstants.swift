//
//  BpmConstants.swift
//  meApp
//

import Foundation

enum BpmConstants {

    // MARK: - AHA Reference Lines (drawn on chart)
    static let normalSystolic: Int = 120
    static let normalDiastolic: Int = 80

    // MARK: - Validation Ranges (manual entry)
    static let systolicRange: ClosedRange<Int> = 60...250
    static let diastolicRange: ClosedRange<Int> = 30...150
    static let pulseRange: ClosedRange<Int> = 20...200

    // MARK: - Y-Axis Defaults
    static let defaultYMin: Double = 40
    static let defaultYMax: Double = 200
    static let yAxisPadding: Double = 10

    // MARK: - Three-Reading Average
    static let readingAverageCount: Int = 3
}
