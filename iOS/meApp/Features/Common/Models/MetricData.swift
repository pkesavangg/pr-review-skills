//
//  BodyMetrics.swift
//  meApp
//
//  Created by Code Conversion on 19/06/25.
//
//  NOTE: This file was auto-converted from a JavaScript constant to Swift. It now provides a strongly typed
//  configuration map that can be accessed throughout the Swift codebase.
//

import Foundation

/// Describes metadata for a body metric such as BMI, Body-Fat, etc.
struct MetricData {
    let unit: String
    let label: String
    let expandedLabel: String?
    let bodyCompositionRelated: Bool
    let icon: String
    let min: Double?
    let max: Double?
    let isWholeNumber: Bool
    let preLabel: String?

    init(unit: String,
         label: String,
         expandedLabel: String? = nil,
         bodyCompositionRelated: Bool,
         icon: String,
         min: Double? = nil,
         max: Double? = nil,
         isWholeNumber: Bool = false,
         preLabel: String? = nil) {
        self.unit = unit
        self.label = label
        self.expandedLabel = expandedLabel
        self.bodyCompositionRelated = bodyCompositionRelated
        self.icon = icon
        self.min = min
        self.max = max
        self.isWholeNumber = isWholeNumber
        self.preLabel = preLabel
    }
}


