//
//  BpmAverage.swift
//  meApp
//

import Foundation

/// Aggregated blood pressure average for a visible chart period.
struct BpmAverage {
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let classification: AhaPressureClass
}
