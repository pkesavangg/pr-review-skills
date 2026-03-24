//
//  BpmTrendView.swift
//  meApp
//
//  BP dashboard: headline values + graph + period selector.
//  Mirrors WeightTrendView structure but for blood pressure data.
//

import SwiftUI

struct BpmTrendView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    @State private var localSelectedPeriod: TimePeriod = .week

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                BpmDisplayView(dashboardStore: dashboardStore)
                GraphView(dashboardStore: dashboardStore)
                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: $localSelectedPeriod
                )
            }
        }
        .onAppear {
            localSelectedPeriod = dashboardStore.state.graph.selectedPeriod
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newPeriod in
            if localSelectedPeriod != newPeriod {
                localSelectedPeriod = newPeriod
            }
        }
        .onChange(of: localSelectedPeriod) { _, newPeriod in
            guard newPeriod != dashboardStore.state.graph.selectedPeriod else { return }
            let anchor = dashboardStore.graphManager.visibleMidpoint(for: newPeriod)
            dashboardStore.chartManager?.updateSelectedPeriod(newPeriod, anchorDate: anchor)
        }
    }
}
