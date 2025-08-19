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
    @State private var suppressOutsideCancel = false

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
        .refreshable {
            // TODO: Implement refresh logic if needed
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
        .onChange(of: store.state.ui.isEditMode) { _, isEdit in
            // Only rebuild layout when entering edit to start wiggle animations
            if isEdit { store.resetDragState() }
        }
        .onChange(of: store.currentUnit) { _, _ in
            store.handleUnitChange()
        }
        .onChange(of: store.state.data.latestWeightStored) { _, _ in
            store.resetMetricsToLatestEntry()
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
                    .onTapGesture {
                        if store.state.ui.isEditMode && store.state.ui.alertData == nil {
                            store.cancelEdit()
                        }
                    }
                if !store.allContentRemoved {
                    if !store.metricsToShow.isEmpty {
                        MetricGridUIKitView(store: store, onMetricLongPress: { label in
                            store.state.ui.selectedMetricLabel = label
                            openMetricInfoWithoutSelection = MetricInfoWrapper(metricLabel: label)
                        })
                            .frame(minHeight: DevicePlatform.isTablet ? 74 : 200)
                            .padding(.top, .spacingSM)
                            .id(store.state.ui.gridLayoutId)
                            .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
                    }

                    if !store.metricsToShow.isEmpty && (!store.state.ui.isGoalCardRemoved || !store.streakItemsToShow.isEmpty) {
                        Divider()
                            .foregroundColor(theme.statusUtilityPrimary)
                            .padding(.horizontal, .spacingLG)
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
        }
        .background(
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture {
                    if store.state.ui.isEditMode && store.state.ui.alertData == nil && suppressOutsideCancel == false {
                        store.cancelEdit()
                    }
                }
        )
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
                    let label = store.state.ui.selectedMetricLabel ?? DashboardStrings.weight
                    openMetricInfoWithoutSelection = MetricInfoWrapper(metricLabel: label)
                })

            }
        }
        .padding(.bottom, .spacingLG)
    }

}
