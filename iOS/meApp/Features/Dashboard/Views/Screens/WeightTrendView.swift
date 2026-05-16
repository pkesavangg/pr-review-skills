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
    @State private var localSelectedPeriod: TimePeriod = DefaultGraphPeriodPreference.fallback

    var body: some View {
        ZStack {
            VStack(alignment: .leading,spacing: 0) {
                weightInfoSection(dashboardStore: dashboardStore)
                GraphView(dashboardStore: dashboardStore)
                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: $localSelectedPeriod,
                    useUniformFontScaling: true
                )
                .padding(.vertical, .spacingSM)
                .padding(.horizontal, 15)
            }
            .padding(.top, .spacingMD)
            .background(theme.textInverse)
            .edgesIgnoringSafeArea(.all)
            .zIndex(1)
        }
        .onAppear {
            // Ensure local pill state matches current store on first appear
            localSelectedPeriod = dashboardStore.graph.selectedPeriod
        }
        .onChange(of: dashboardStore.graph.selectedPeriod) { _, newValue in
            if localSelectedPeriod != newValue {
                localSelectedPeriod = newValue
            }
        }
        // Switching Week/Month/Year/Total always snaps to the latest entry and
        // auto-selects it so the header reflects the most recent data point.
        .onChange(of: localSelectedPeriod) { _, newValue in
            dashboardStore.updateSelectedPeriod(newValue)
        }
    }

    @ViewBuilder
    func weightInfoSection(
        dashboardStore: DashboardStore
    ) -> some View {
        VStack(alignment: .leading, spacing: .zero) {
            // Show label based on selection state.
            Text(dashboardStore.weightDisplayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            WeightDisplayView(
                weightText: {
                    // Prefer current selection (exact point or interpolated crosshair) when available
                    if let displayWeight = dashboardStore.displayWeight {
                        if abs(displayWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                            return "000.0"
                        }
                        return dashboardStore.formatWeightDisplayText(displayWeight)
                    }
                    // Fallback to average weight
                    let averageWeight = dashboardStore.getCurrentAverageWeight()
                    if abs(averageWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                        return "000.0"
                    }
                    return dashboardStore.formatWeightDisplayText(averageWeight)
                }(),
                unitText: dashboardStore.displayUnitText
            )
        }
    }
}

#Preview {
    WeightTrendView(dashboardStore: DashboardStore())
}
