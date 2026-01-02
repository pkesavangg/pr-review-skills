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
            VStack(alignment: .leading,spacing: 0) {
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
            // Determine if we're zooming in (higher to lower granularity) or zooming out
            // Zooming OUT (week→month, month→year): Use midpoint to center the new view
            // Zooming IN (year→month, month→week): Use end date to show latest detail
            let isZoomingIn = newValue.isMoreDetailedThan(oldValue)
            
            let anchorDate: Date?
            if oldValue == .total {
                anchorDate = nil
            } else if isZoomingIn {
                // Zooming in: anchor to the latest visible date
                anchorDate = dashboardStore.graphManager.visibleEndDate(for: oldValue)
            } else {
                // Zooming out: anchor to the midpoint
                anchorDate = dashboardStore.graphManager.visibleMidpoint(for: oldValue)
            }
            
            // Debug logging
            if let anchor = anchorDate {
                let formatter = DateFormatter()
                formatter.dateStyle = .medium
                formatter.timeStyle = .short
                print("DEBUG: Captured anchor date: \(formatter.string(from: anchor)) (zoomingIn=\(isZoomingIn)) when switching from \(oldValue) to \(newValue)")
            }
            
            dashboardStore.updateSelectedPeriod(newValue, anchorDate: anchorDate)
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
