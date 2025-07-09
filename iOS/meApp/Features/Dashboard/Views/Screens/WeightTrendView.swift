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
                weightText: dashboardStore.formatWeightDisplayText(
                    dashboardStore.displayWeightForSelection
                ),
                unitText: dashboardStore.accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
            )
        }
    }
}

#Preview {
    WeightTrendView(dashboardStore: DashboardStore())
}
