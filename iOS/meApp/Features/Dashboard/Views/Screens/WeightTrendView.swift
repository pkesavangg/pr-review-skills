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
        ZStack {
            VStack(alignment: .leading,spacing: 0) {
                
               weightInfoSection(dashboardStore: dashboardStore)

               GraphView(dashboardStore: dashboardStore)

                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: Binding(
                        get: { dashboardStore.selectedPeriod },
                        set: { dashboardStore.updateSelectedPeriod($0) }
                    )
                )
                .padding(.vertical, .spacingSM)
                .padding(.horizontal, 15)
            }
            .padding(.top, .spacingMD)
            .background(theme.textInverse)
            .edgesIgnoringSafeArea(.all)
            .zIndex(1)
        }
    }
    
    @ViewBuilder
    func weightInfoSection(
        dashboardStore: DashboardStore
    ) -> some View {
        VStack(alignment: .leading, spacing: .zero) {
            // Show label based on selection state
            Text(dashboardStore.weightDisplayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            WeightDisplayView(
                weightText: {
                    // If a point is selected, show its weight value
                    if let displayWeight = dashboardStore.displayWeight {
                        let formattedWeight = dashboardStore.formatWeightDisplayText(displayWeight)
                        print("Hello: WeightTrendView - Display weight: \(displayWeight)")
                        print("Hello: WeightTrendView - Formatted weight text: \(formattedWeight)")
                        return formattedWeight
                    } else {
                        // Fallback to average weight
                        let averageWeight = dashboardStore.getCurrentAverageWeight()
                        let formattedWeight = dashboardStore.formatWeightDisplayText(averageWeight)
                        print("Hello: WeightTrendView - Average weight: \(averageWeight)")
                        print("Hello: WeightTrendView - Formatted weight text: \(formattedWeight)")
                        return formattedWeight
                    }
                }(),
                unitText: dashboardStore.accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
            )
        }
    }
}

#Preview {
    WeightTrendView(dashboardStore: DashboardStore())
}
