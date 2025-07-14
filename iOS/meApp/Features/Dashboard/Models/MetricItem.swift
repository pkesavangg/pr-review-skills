//
//  MetricItem.swift
//  meApp
//
//  Created by Lakshmi Priya on 04/07/25.
//

import Foundation

struct MetricItem: Identifiable, Equatable {
    let id = UUID()
    var value: String
    let label: String
    let unit: String?
    let preLabel: String?
    let icon: String?
}
