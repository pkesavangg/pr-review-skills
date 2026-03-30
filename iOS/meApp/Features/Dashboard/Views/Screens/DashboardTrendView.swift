//
//  DashboardTrendView.swift
//  meApp
//

import SwiftUI

struct DashboardTrendView<TopContent: View>: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    @State private var localSelectedPeriod: TimePeriod = .week

    private let topContent: () -> TopContent

    init(
        dashboardStore: DashboardStore,
        @ViewBuilder topContent: @escaping () -> TopContent
    ) {
        self.dashboardStore = dashboardStore
        self.topContent = topContent
    }

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                topContent()
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
            localSelectedPeriod = dashboardStore.state.graph.selectedPeriod
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newValue in
            if localSelectedPeriod != newValue {
                localSelectedPeriod = newValue
            }
        }
        .onChange(of: localSelectedPeriod) { oldValue, newValue in
            guard newValue != dashboardStore.state.graph.selectedPeriod else { return }
            let anchorDate: Date?
            if oldValue == .total {
                anchorDate = nil
            } else {
                anchorDate = dashboardStore.graphManager.visibleMidpoint(for: oldValue)
            }
            dashboardStore.chartManager.updateSelectedPeriod(newValue, anchorDate: anchorDate)
        }
    }
}
