//
//  SelectionData.swift
//  meApp
//

import Foundation

/// Represents the data shown when a user taps a point on the graph.
enum SelectionData {
    case weight(value: Double, unit: String)
    case bpm(systolic: Int, diastolic: Int, pulse: Int, classification: AhaPressureClass)

    var isEmpty: Bool {
        switch self {
        case .weight(let value, _): return value == 0
        case .bpm(let sys, _, _, _): return sys == 0
        }
    }
}
