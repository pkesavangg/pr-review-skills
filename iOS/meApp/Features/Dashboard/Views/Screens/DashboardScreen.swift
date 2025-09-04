//
//  DashboardScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI
import UniformTypeIdentifiers
import Combine

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
            navbarHeader()
                .contentShape(Rectangle())
                .onTapGesture {
                    if store.state.ui.isEditMode {
                        store.cancelEdit()
                    }
                }
              dashboardScroll()
        }
        .refreshable {
            await store.syncEntries()
            store.onAppearActions()
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
        .task(id: selectedMetricInfo) {
            if let newValue = selectedMetricInfo {
                await store.handleSelectedMetricInfoChange(newValue, selectedEntry: $selectedEntry, selectedMetric: $selectedMetric)
                selectedMetricInfo = nil
            }
        }
        .task(id: store.state.ui.selectedMetricLabel) {
            store.handleSelectedMetricLabelChange(store.state.ui.selectedMetricLabel)
        }
        .task(id: selectedEntry) {
            store.handleSelectedEntryChange(selectedEntry)
        }
        .task(id: openMetricInfoWithoutSelection) {
            store.handleMetricInfoSheetDismiss(openMetricInfoWithoutSelection)
        }
        .task(id: store.currentUnit) {
            store.handleUnitChange()
        }
        .task(id: store.state.data.latestWeightStored) {
            store.resetMetricsToLatestEntry()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            if store.state.ui.isEditMode {
                DispatchQueue.main.asyncAfter(deadline: .now() + WiggleAnimationConstants.wiggleRestartDelayAfterAppActive) {
store.restartWiggleAnimations()
                }
            }
            Task {
                await store.loadDashboardConfigurationFromAPI()
                await MainActor.run {
                    store.objectWillChange.send()
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .dashboardMetricsUpdated)) { _ in
            Task {
                await store.loadDashboardConfigurationFromAPI()
                // Force UI update to reflect the changes
                await MainActor.run {
                    store.objectWillChange.send()
                    // Force a complete refresh of the dashboard state
                    store.refreshDashboardState()
                }
            }
        }
        .onAppear {
            Task {
                await store.loadDashboardConfigurationFromAPI()
                await MainActor.run {
                    store.objectWillChange.send()
                }
            }
        }
        .onReceive(
            Publishers.MergeMany([
                NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification),
                NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
            ])
        ) { _ in
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
                // Immediately refresh dashboard data when switching to dashboard tab
                // This ensures changes from scale setup are reflected
                print("📱 DashboardScreen: Switched to dashboard tab, refreshing data...")
                Task {
                    await store.loadDashboardConfigurationFromAPI()
                    await MainActor.run {
                        store.objectWillChange.send()
                    }
                    print("📱 DashboardScreen: Dashboard data refreshed on tab switch")
                }
                
                DispatchQueue.main.async { 
                    store.resetGridLayout()
                }
            }
        }
    }
    
    private func navbarHeader() -> some View {
        NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false).zIndex(100)
    }
    
    private func dashboardScroll() -> some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                WeightTrendView(dashboardStore: store)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if store.state.ui.isEditMode && store.state.ui.alertData == nil {
                            store.cancelEdit()
                        }
                    }
                if !store.allContentRemoved && store.state.data.hasAnyEntries {
                    DashboardMetricsSection(store: store, parentView: .dashboard, openMetricInfoWithoutSelection: $openMetricInfoWithoutSelection)
                }
                if store.state.data.hasAnyEntries {
                    actionButtons()
                        .padding(.top, store.allContentRemoved || !store.state.data.hasAnyEntries ? .spacingLG : .spacingSM)
                }
                if !store.state.data.hasAnyEntries {
                    noEntrySection()
                }
            }
        }
        .background(
            Color.clear.contentShape(Rectangle())
                .onTapGesture {
                    if store.state.ui.isEditMode && store.state.ui.alertData == nil && !suppressOutsideCancel {
                        store.cancelEdit()
                    }
                }
        )
        .padding(.top, .zero)
    }

    private func actionButtons() -> some View {
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
                    store.toggleEditMode()
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
    
    private func noEntrySection() -> some View {
        NoEntryView(
            title: nil,
            description: DashboardStrings.noEntriesMessage,
            onButtonTap: {
                tabViewModel.pendingSettingsNavigation = .addEditScales
                tabViewModel.selectedTab = .settings
            }
        )
    }
}
