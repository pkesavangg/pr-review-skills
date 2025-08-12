//
//  DashboardScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI
import UniformTypeIdentifiers

struct DashboardScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Environment(\.scenePhase) private var scenePhase
    @StateObject var store = DashboardStore()
    let lang = DashboardStrings.self
    @State private var selectedEntry: Entry? = nil
    @State private var selectedMetric: BodyMetric? = nil
    @State private var selectedMetricInfo: String?
    @State private var openMetricInfoWithoutSelection: MetricInfoWrapper?

    var body: some View {
        VStack(spacing: 0) {
            navbarHeaderSection()
                .contentShape(Rectangle())
                .onTapGesture {
                    if store.state.ui.isEditMode {
                        store.cancelEdit()
                    }
                }
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
                // Clear selection after info sheet is dismissed
                store.state.ui.selectedMetricLabel = nil
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
            // Clear selection after info sheet is dismissed
            store.state.ui.selectedMetricLabel = nil
        }
        .onChange(of: store.state.ui.isEditMode) { _, isEdit in
            // Only rebuild layout when entering edit to start wiggle animations
            if isEdit { store.resetDragState() }
        }
        .onChange(of: store.currentUnit) { _, _ in
            store.handleUnitChange()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            // Restart wiggle animations when app becomes active from background
            if store.state.ui.isEditMode {
                // Force a small delay to ensure the view is fully loaded
                DispatchQueue.main.asyncAfter(deadline: .now() + WiggleAnimationConstants.wiggleRestartDelayAfterAppActive) {
                    store.restartWiggleAnimations()
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            if store.state.ui.isEditMode { store.cancelEdit() }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
            if store.state.ui.isEditMode { store.cancelEdit() }
        }
        .presentAlert(alertData: $store.state.ui.alertData)
        .presentLoader(loaderData: store.loaderData)
        .onChange(of: scenePhase) { _, newPhase in
            if store.state.ui.isEditMode && (newPhase == .background || newPhase == .inactive) {
                store.cancelEdit()
            }
        }
        .onChange(of: tabViewModel.selectedTab) { _, newTab in
            if store.state.ui.isEditMode && newTab != .dash {
                store.cancelEdit()
            }
            if newTab == .dash {
                DispatchQueue.main.async {
                    store.state.ui.gridLayoutId = UUID()
                }
            }
        }
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
            VStack(spacing: 0) {
                WeightTrendView(dashboardStore: store)
                    .contentShape(Rectangle())
                if !store.allContentRemoved {
                    if !store.metricsToShow.isEmpty {
                        MetricGridUIKitView(store: store)
                            .frame(minHeight: 200)
                            .padding(.top, .spacingSM)
                            .id(store.state.ui.gridLayoutId)
                            .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
                    }

                    if !store.metricsToShow.isEmpty && (!store.state.ui.isGoalCardRemoved || !store.streakItemsToShow.isEmpty) {
                        Divider()
                            .padding(.horizontal, .spacingLG)
                            .padding(.vertical, .spacingSM)
                    }

                    if !store.state.ui.isGoalCardRemoved || !store.streakItemsToShow.isEmpty {
                        GoalStreakGridUIKitView(store: store)
                            .frame(minHeight: 200)
                            .id(store.state.ui.gridLayoutId)
                            .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
                    }
                }
                actionButtonsSection()
                    .padding(.top, store.allContentRemoved ? .spacing6XL : .spacingSM)
            }
            .contentShape(Rectangle())
            .simultaneousGesture(TapGesture().onEnded({
                if store.state.ui.isEditMode {
                    store.cancelEdit()
                    // Force a lightweight refresh of visible cells to clear overlay/wiggle without full reload
                    DispatchQueue.main.async {
                        store.objectWillChange.send()
                    }
                }
            }))
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
                    if !store.state.ui.isEditMode {
                        store.beginEdit()
                    }
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
