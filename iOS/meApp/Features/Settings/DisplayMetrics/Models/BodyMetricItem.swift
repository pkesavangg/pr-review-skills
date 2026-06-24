//
//  BodyMetricItem.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import Foundation

// MARK: - Metric Toggle State
struct BodyMetricItem: Identifiable {
    let id: BodyMetric
    var isOn: Bool
    var isDisabled: Bool = false
}
