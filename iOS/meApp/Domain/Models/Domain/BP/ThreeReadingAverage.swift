//
//  ThreeReadingAverage.swift
//  meApp
//

import Foundation

/// The last N blood pressure readings averaged together, per the Figma "three entry average" card.
struct ThreeReadingAverage {
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let count: Int
    let label: String
    let classification: AhaPressureClass

    /// Display label adapts to how many readings were available.
    static func displayLabel(for count: Int) -> String {
        switch count {
        case 3...: return "three entry average"
        case 2:    return "two entry average"
        default:   return "last reading"
        }
    }
}
