//
//  GraphSeries.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/07/25.
//

import Foundation

struct GraphSeries: Identifiable, Equatable {
    let id = UUID()
    let date: Date
    let value: Double
    let series: String
} 