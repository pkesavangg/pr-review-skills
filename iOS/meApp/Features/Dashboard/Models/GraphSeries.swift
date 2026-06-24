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

// Enhanced point with precomputed xDate and stable identity.
struct PlottedGraphSeries: Identifiable, Equatable, Hashable {
    let id: String
    let original: GraphSeries
    let xDate: Date

    init(original: GraphSeries, xDate: Date) {
        self.id = original.id
        self.original = original
        self.xDate = xDate
    }

    var date: Date { original.date }
}
