//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import SwiftUI

struct WeightTrendView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    var body: some View {
        DashboardTrendView(dashboardStore: dashboardStore) {
            weightInfoSection(dashboardStore: dashboardStore)
        }
    }

    @ViewBuilder
    func weightInfoSection(
        dashboardStore: DashboardStore
    ) -> some View {
        VStack(alignment: .leading, spacing: .zero) {
            Text(dashboardStore.displayManager.weightDisplayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            WeightDisplayView(
                weightText: {
                    if let displayWeight = dashboardStore.displayManager.displayWeight {
                        if abs(displayWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                            return "000.0"
                        }
                        return dashboardStore.displayManager.formatWeightDisplayText(displayWeight)
                    }
                    let averageWeight = dashboardStore.displayManager.getCurrentAverageWeight()
                    if abs(averageWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                        return "000.0"
                    }
                    return dashboardStore.displayManager.formatWeightDisplayText(averageWeight)
                }(),
                unitText: dashboardStore.displayManager.displayUnitText
            )
        }
    }
}

#Preview {
    WeightTrendView(dashboardStore: DashboardStore())
}
