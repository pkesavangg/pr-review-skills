//
//  BpmDisplayData.swift
//  meApp
//

import Foundation

/// Data shown in the BPM headline area — either a selected point or visible window average.
struct BpmDisplayData {
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let classification: AhaPressureClass
    let label: String
}
