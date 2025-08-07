//
//  GraphSeries.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/07/25.
//

import Foundation

struct GraphSeries: Identifiable, Equatable, Hashable {
    let id: String
    let date: Date
    let value: Double
    let series: String

    init(date: Date, value: Double, series: String) {
        self.id = "\(series)_\(date.timeIntervalSince1970)"
        self.date = date
        self.value = value
        self.series = series
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: GraphSeries, rhs: GraphSeries) -> Bool {
        return lhs.id == rhs.id && lhs.value == rhs.value
    }
}
