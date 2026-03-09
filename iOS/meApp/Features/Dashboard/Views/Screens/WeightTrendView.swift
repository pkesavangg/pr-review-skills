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
    @State private var localSelectedPeriod: TimePeriod = .week

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                weightInfoSection(dashboardStore: dashboardStore)
                GraphView(dashboardStore: dashboardStore)
                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: $localSelectedPeriod
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
            localSelectedPeriod = dashboardStore.state.graph.selectedPeriod
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newValue in
            if localSelectedPeriod != newValue {
                localSelectedPeriod = newValue
            }
        }
        // Update store period immediately without animating the whole subtree
        // Capture anchor date using the OLD period for temporal context preservation
        .onChange(of: localSelectedPeriod) { oldValue, newValue in
            // Always use midpoint as anchor to ensure consistent positioning
            // regardless of zoom direction. This keeps the same date centered
            // even when switching between periods multiple times.
            let anchorDate: Date?
            if oldValue == .total {
                anchorDate = nil
            } else {
                // Always anchor to midpoint for consistent behavior
                anchorDate = dashboardStore.graphManager.visibleMidpoint(for: oldValue)
            }

            dashboardStore.chartManager.updateSelectedPeriod(newValue, anchorDate: anchorDate)
        }
    }

    @ViewBuilder
    func weightInfoSection(
        dashboardStore: DashboardStore
    ) -> some View {
        VStack(alignment: .leading, spacing: .zero) {
            // Show label based on selection state
            Text(dashboardStore.displayManager.weightDisplayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            WeightDisplayView(
                weightText: {
                    // Prefer current selection (exact point or interpolated crosshair) when available
                    if let displayWeight = dashboardStore.displayManager.displayWeight {
                        if abs(displayWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                            return "000.0"
                        }
                        return dashboardStore.displayManager.formatWeightDisplayText(displayWeight)
                    }
                    // Fallback to average weight
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
