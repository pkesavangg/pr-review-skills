//
//  BabyGrowthChartEnvironment.swift
//  meApp
//

import SwiftUI

private struct BabyGrowthChartCalloutDateStyleKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// When true, the dashboard weight graph uses `BabyPercentileGrowthReference.formatChartSelectionDate`
    /// for the selection callout (Smart Baby `graph.service.ts` focus date style).
    var babyGrowthChartCalloutDateStyle: Bool {
        get { self[BabyGrowthChartCalloutDateStyleKey.self] }
        set { self[BabyGrowthChartCalloutDateStyleKey.self] = newValue }
    }
}
