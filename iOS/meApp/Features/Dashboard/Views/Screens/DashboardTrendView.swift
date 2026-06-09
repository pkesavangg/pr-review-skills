//
//  DashboardTrendView.swift
//  meApp
//

import SwiftUI

struct DashboardTrendView<TopContent: View, ChartFooter: View>: View {
    @ObservedObject var dashboardStore: DashboardStore
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Environment(\.appTheme) private var theme
    @State private var localSelectedPeriod: TimePeriod = .week

    private let topContent: () -> TopContent
    private let chartFooter: () -> ChartFooter

    @ViewBuilder
    private func noEntriesFooter() -> some View {
        VStack(spacing: .spacingMD) {
            Text(DashboardStrings.noEntriesMessage)
                .fontOpenSans(.body2)
                .foregroundStyle(theme.textBody)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)
            ButtonView(
                text: DashboardStrings.connectDevice,
                type: .filledPrimary,
                size: .large,
                isDisabled: false
            ) {
                tabViewModel.pendingSettingsNavigation = .addEditScales
                tabViewModel.selectedTab = .settings
                tabViewModel.settingsNavigationSourceTab = .dash
            }
            .padding(.horizontal, .spacingLG)
        }
        .padding(.vertical, .spacingMD)
        .frame(maxWidth: .infinity)
    }

    init(
        dashboardStore: DashboardStore,
        @ViewBuilder topContent: @escaping () -> TopContent,
        @ViewBuilder chartFooter: @escaping () -> ChartFooter
    ) {
        self.dashboardStore = dashboardStore
        self.topContent = topContent
        self.chartFooter = chartFooter
    }

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                topContent()
                GraphView(dashboardStore: dashboardStore)
                chartFooter()
                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: $localSelectedPeriod
                )
                .padding(.vertical, .spacingSM)
                .padding(.horizontal, 15)
                if !dashboardStore.state.data.hasAnyEntries {
                    noEntriesFooter()
                }
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
            // Dispatch outside the current animation transaction so the segment
            // button highlight animates without being blocked by chart recalculation.
            Task { @MainActor in
                dashboardStore.chartManager.updateSelectedPeriod(newValue, anchorDate: anchorDate)
            }
        }
    }
}

extension DashboardTrendView where ChartFooter == EmptyView {
    init(
        dashboardStore: DashboardStore,
        @ViewBuilder topContent: @escaping () -> TopContent
    ) {
        self.init(dashboardStore: dashboardStore, topContent: topContent) { EmptyView() }
    }
}
