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
            VStack(spacing: 0) {
                HStack{
                    VStack(alignment: .leading, spacing: .zero ) {
                        Text("\(dashboardStore.selectedPeriod.rawValue) average")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.leading, .spacingSM)

                        WeightDisplayView(
                            weightText: dashboardStore.formatWeightDisplayText(
                                dashboardStore.selectedEntry != nil
                                    ? dashboardStore.convertStoredWeightToDisplay(Int(dashboardStore.selectedEntry?.weight ?? 0))
                                    : dashboardStore.displayWeight
                            ),
                            unitText: dashboardStore.accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
                        )

                        if let label = dashboardStore.weightLabel {
                            Text(label)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                                .padding(.leading, .spacingSM)
                                .padding(.top, .spacingXS)
                        }
                    }

                    Spacer()
                }
                .padding(.bottom, 8)

               // GraphView(dashboardStore: dashboardStore)

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
}

#Preview {
    WeightTrendView(dashboardStore: DashboardStore())
}
