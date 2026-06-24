//
//  MetricInfoWrapper.swift
//  meApp
//
//  Created by Lakshmi Priya on 04/07/25.
//

import Foundation

// MARK: - Identifiable Wrapper for Metric Info
struct MetricInfoWrapper: Equatable, Identifiable {
    let id = UUID()
    let metricLabel: String
}
