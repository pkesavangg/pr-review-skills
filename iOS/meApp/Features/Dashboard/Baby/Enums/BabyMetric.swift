//
//  BabyMetric.swift
//  meApp
//
//  Toggle between weight and height on the baby growth chart.
//

import Foundation

enum BabyMetric: String, CaseIterable, Identifiable, Hashable {
    case weight = "Weight"
    case height = "Height"

    var id: String { rawValue }
}
