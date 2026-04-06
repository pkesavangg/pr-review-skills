//
//  BabyPercentileChartPoint.swift
//  meApp
//
//  A single chart-ready point on a WHO percentile reference curve.
//

import Foundation

struct BabyPercentileChartPoint: Identifiable {
    let id: String
    let date: Date
    let value: Double
    let line: BabyPercentileLine

    init(date: Date, value: Double, line: BabyPercentileLine) {
        self.id = "\(line.rawValue)_\(date.timeIntervalSince1970)"
        self.date = date
        self.value = value
        self.line = line
    }
}
