//
//  DashboardScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//
//  Wiggle Animation Implementation:
//  - Metric grid uses app icon-style wiggle (0.135s/0.125s duration, 0.04 radians)
//  - Goal card uses medium-speed wiggle (0.18s duration, 0.045 radians)
//  - Streak grid uses medium-speed wiggle with alternating timing (0.18s/0.16s duration, 0.045 radians)
//  - Widget wiggle uses alternating timing (0.35s/0.33s duration, 0.045 radians)
//  - This provides a balanced wiggle that's faster than widgets but gentler than app icons
//

import SwiftUI
import UniformTypeIdentifiers

struct DashboardScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @StateObject var store = DashboardStore()
    let lang = DashboardStrings.self
    @State private var selectedEntry: Entry? = nil
    @State private var selectedMetric: BodyMetric? = nil
    @State private var selectedMetricInfo: String?
    @State private var openMetricInfoWithoutSelection: MetricInfoWrapper?

    var body: some View {
        VStack(spacing: 0) {
            navbarHeaderSection()
            dashboardScrollView()
        }
        .onAppear(perform: store.onAppearActions)
        .ignoresSafeArea(.all)
        .background(theme.backgroundSecondary)
        .sheet(item: $selectedEntry) { entry in
            ScaleMetricsView(entry: entry, selectedMetric: selectedMetric ?? .bmi)
        }
        .sheet(item: $openMetricInfoWithoutSelection) { wrapper in
            ScaleMetricsView(
                entry: store.createEntryForMetricInfo(metricLabel: wrapper.metricLabel),
                selectedMetric: store.getBodyMetric(for: wrapper.metricLabel)
            )
        }
        .onChange(of: selectedMetricInfo) { _, newValue in
            Task {
                await store.handleSelectedMetricInfoChange(newValue, selectedEntry: $selectedEntry, selectedMetric: $selectedMetric)
                // Clear the selectedMetricInfo after handling
                selectedMetricInfo = nil
            }
        }
        .onChange(of: store.state.ui.selectedMetricLabel) { _, newValue in
            store.handleSelectedMetricLabelChange(newValue)
        }
        .onChange(of: selectedEntry) { _, newValue in
            store.handleSelectedEntryChange(newValue)
        }
        .onChange(of: openMetricInfoWithoutSelection) { _, newValue in
            store.handleMetricInfoSheetDismiss(newValue)
        }
        .onChange(of: store.state.ui.isEditMode) { _, _ in store.resetDragState() }
        .onChange(of: store.currentUnit) { _, _ in 
            store.handleUnitChange()
        }
        .presentAlert(alertData: $store.state.ui.alertData)
        .presentLoader(loaderData: store.loaderData)
    }

    // MARK: - Sections split for type-checking

    @ViewBuilder
    private func navbarHeaderSection() -> some View {
        NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false)
            .zIndex(100)
    }

    @ViewBuilder
    private func dashboardScrollView() -> some View {
        ScrollView(showsIndicators: false) {
            WeightTrendView(dashboardStore: store)
            if !store.allContentRemoved {
                // Use single combined layout view for all items
                DashboardCombinedLayoutView(store: store)
                    .frame(minHeight: 600)
                    .padding(.top, .spacingSM)
                    .id(store.state.ui.gridLayoutId)
                    .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
            }
            actionButtonsSection()
                .padding(.top, store.allContentRemoved ? .spacing6XL : .spacingSM)
        }
        .padding(.top, .zero)
    }

    // MARK: - Action Buttons
    private func actionButtonsSection() -> some View {
        VStack(alignment: .center, spacing: .spacingSM) {
            if store.state.ui.isEditMode {
                ButtonView(text: lang.saveChanges, type: .filledPrimary, size: .large, isDisabled: store.state.ui.isLoading, action: {
                    store.saveChanges()
                    store.resetDragState()
                })
                ButtonView(text: lang.resetDashboard, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading, action: {
                    store.showResetDashboardAlert()
                })
            } else {
                ButtonView(text: lang.editDashboard, type: .outlinedPrimary, size: .large, isDisabled: store.state.ui.isLoading, action: {
                    store.state.ui.isEditMode.toggle()
                    if store.state.ui.isEditMode {
                        store.resetDragState()
                    }
                })
                ButtonView(text: lang.updateGoal, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading, action: {
                    tabViewModel.navigateToGoalSetting()
                })
                ButtonView(text: lang.metricInfo, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading, action: {
                    selectedMetricInfo = store.state.ui.selectedMetricLabel ?? DashboardStrings.weight
                })
                
                // Add button to switch to 12 metrics if currently showing 4 metrics
                if store.state.metrics.dashboardType == .dashboard4 {
                    ButtonView(text: lang.switchTo12Metrics, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading, action: {
                        store.switchTo12MetricsDashboard()
                    })
                }
            }
        }
        .padding(.bottom, .spacingLG)
    }

}
