//
//  DashboardScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import Combine
import SwiftUI
import UniformTypeIdentifiers

struct DashboardScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Environment(\.scenePhase) private var scenePhase
    @StateObject var store = DashboardStore()
    @ObservedObject private var productTypeStore = ProductTypeStore.shared
    let lang = DashboardStrings.self
    @State private var selectedEntry: Entry?
    @State private var selectedMetric: BodyMetric?
    @State private var selectedMetricInfo: String?
    @State private var openMetricInfoWithoutSelection: MetricInfoWrapper?
    @State private var suppressOutsideCancel = false
    @State private var metricInfoEntry: Entry?
    @State private var isProductTypeSelectorPresented = false
    @State private var isInProductDashboard = false

    var body: some View {
        VStack(spacing: 0) {
            if productTypeStore.availableItems.count > 1 && !isInProductDashboard {
                snapshotLogo()
                ScrollView(showsIndicators: false) {
                    MultiDeviceSnapshotView(productTypeStore: productTypeStore) { selectedItem in
                        let newType: EntryType = selectedItem == .myBloodPressure ? .bpm : .wg
                        store.switchProductType(to: newType)
                        productTypeStore.select(selectedItem)
                        isInProductDashboard = true
                    }
                }
            } else {
                navbarHeader()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if store.state.ui.isEditMode {
                            store.cancelEdit()
                        }
                    }
                dashboardScroll()
            }
        }
        .refreshable {
            await store.lifecycleManager.refreshAll()
        }
        .onAppear(perform: store.lifecycleManager.onAppearActions)
        .ignoresSafeArea(.all, edges: productTypeStore.availableItems.count > 1 ? .bottom : .all)
        .background(theme.backgroundSecondary)
        .sheet(item: $selectedEntry) { entry in
            RefetchedEntryWrapper(entryId: entry.id, selectedMetric: selectedMetric ?? .bmi, dashboardStore: store)
        }
        .sheet(item: $openMetricInfoWithoutSelection) { wrapper in
            MetricInfoSheetWrapper(
                entry: metricInfoEntry ?? store.displayManager.createEntryForMetricInfo(metricLabel: wrapper.metricLabel),
                selectedMetric: store.displayManager.getBodyMetric(for: wrapper.metricLabel),
                dashboardStore: store
            )
        }
        .task(id: selectedMetricInfo) {
            if let newValue = selectedMetricInfo {
                await store.lifecycleManager.handleSelectedMetricInfoChange(newValue, selectedEntry: $selectedEntry, selectedMetric: $selectedMetric)
                selectedMetricInfo = nil
            }
        }
        .task(id: openMetricInfoWithoutSelection) {
            if let wrapper = openMetricInfoWithoutSelection {
                metricInfoEntry = store.displayManager.createEntryForMetricInfo(metricLabel: wrapper.metricLabel)
            }
            store.lifecycleManager.handleMetricInfoSheetDismiss(openMetricInfoWithoutSelection)
        }
        // Keep the metric info entry in sync with metric tile values while the sheet is open
        .task(id: store.state.metrics.metrics) {
            if let wrapper = openMetricInfoWithoutSelection {
                metricInfoEntry = store.displayManager.createEntryForMetricInfo(metricLabel: wrapper.metricLabel)
            }
        }
        // Update metric info entry when time period changes
        .task(id: store.state.graph.selectedPeriod) {
            if let wrapper = openMetricInfoWithoutSelection {
                metricInfoEntry = store.displayManager.createEntryForMetricInfo(metricLabel: wrapper.metricLabel)
            }
        }
        .task(id: store.state.ui.selectedMetricLabel) {
            store.lifecycleManager.handleSelectedMetricLabelChange(store.state.ui.selectedMetricLabel)
        }
        .task(id: selectedEntry) {
            store.lifecycleManager.handleSelectedEntryChange(selectedEntry)
        }
        .task(id: store.currentUnit) {
            store.lifecycleManager.handleUnitChange()
        }
        .task(id: store.state.data.latestWeightStored) {
            store.displayManager.resetMetricsToLatestEntry()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            if store.state.ui.isEditMode {
                Task { @MainActor in
                    try? await Task.sleep(
                        nanoseconds: UInt64(WiggleAnimationConstants.wiggleRestartDelayAfterAppActive * 1_000_000_000)
                    )
                    store.gridEditingManager.restartWiggleAnimations()
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .dashboardMetricsUpdated)) { _ in
            Task { await store.lifecycleManager.reloadDashboardConfiguration(fullRefresh: true) }
        }
        .onReceive(
            Publishers.MergeMany([
                NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification),
                NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
            ])
        ) { _ in
            if store.state.ui.isEditMode { store.cancelEdit() }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if store.state.ui.isEditMode && (newPhase == .background || newPhase == .inactive) {
                store.cancelEdit()
            }
        }
        .onChange(of: tabViewModel.selectedTab) { _, newTab in
            if store.state.ui.isEditMode && newTab != .dash {
                store.cancelEdit()
            }

        }
    }
    
    private func snapshotLogo() -> some View {
        AppIconView(icon: AppAssets.wgLogo, size: IconSize(width: 45, height: 45))
            .foregroundColor(theme.textSubheading)
            .frame(maxWidth: .infinity)
            .padding(.vertical, .spacingXS)
    }

    private func navbarHeader() -> some View {
        NavbarHeaderView<EmptyView, EmptyView>(
            title: productTypeStore.availableItems.count > 1 && isInProductDashboard
                ? productTypeStore.selectedItem.dashboardTitle
                : nil,
            onTitleTap: productTypeStore.availableItems.count > 1 && isInProductDashboard ? {
                isProductTypeSelectorPresented = true
            } : nil,
            canShowBorder: false,
            canShowTitleChevron: productTypeStore.availableItems.count > 1 && isInProductDashboard
        )
        .sheet(isPresented: $isProductTypeSelectorPresented) {
            ProductTypeSelectorSheet(
                store: productTypeStore,
                isPresented: $isProductTypeSelectorPresented,
                title: ProductTypeStrings.myDashboard
            )
        }
        .zIndex(100)
    }
    
    private func dashboardScroll() -> some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                if store.productType == .bpm {
                    bpmDashboardContent()
                } else {
                    weightDashboardContent()
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

    @ViewBuilder
    private func weightDashboardContent() -> some View {
        WeightTrendView(dashboardStore: store)
            .contentShape(Rectangle())
            .onTapGesture {
                if store.state.ui.isEditMode && store.state.ui.alertData == nil {
                    store.cancelEdit()
                }
            }
        if store.state.ui.isEditMode || (!store.allContentRemoved && store.state.data.hasAnyEntries) {
            DashboardMetricsSection(store: store, parentView: .dashboard, openMetricInfoWithoutSelection: $openMetricInfoWithoutSelection)
        }

        if store.state.data.hasAnyEntries {
            let hasNoContentToShow = store.metricsToShow.isEmpty &&
                                   (!store.streakManager.shouldShowStreakGrid() || store.state.ui.isGoalCardRemoved)
            actionButtons()
                .padding(.top, (store.allContentRemoved || hasNoContentToShow) ? .spacingLG : .spacingSM)
        }
        if !store.state.data.hasAnyEntries {
            VStack {
                Spacer(minLength: 0)
                noEntrySection()
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: UIScreen.main.bounds.height - 650)
        }
    }

    @ViewBuilder
    private func bpmDashboardContent() -> some View {
        if store.state.data.hasAnyEntries {
            BpmTrendView(dashboardStore: store)
            BpmMetricsSection(store: store)
        } else {
            bpmEmptyState()
        }
    }

    @ViewBuilder
    private func bpmEmptyState() -> some View {
        VStack(spacing: .spacingLG) {
            Spacer(minLength: .spacingXL)
            Image(systemName: "heart.text.square")
                .font(.system(size: 60))
                .foregroundColor(theme.textSubheading.opacity(0.5))
            Text(BpmDashboardStrings.noReadingsTitle)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(BpmDashboardStrings.noReadingsSubtitle)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)
            ButtonView(text: BpmDashboardStrings.addReading, type: .filledPrimary, size: .large, isDisabled: false) {
                tabViewModel.selectTab(.entry)
            }
            Spacer(minLength: .spacingXL)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: UIScreen.main.bounds.height - 400)
    }

    private func actionButtons() -> some View {
        VStack(alignment: .center, spacing: .spacingSM) {
            if store.state.ui.isEditMode {
                ButtonView(text: lang.saveChanges, type: .filledPrimary, size: .large, isDisabled: store.state.ui.isLoading) {
                    store.lifecycleManager.saveChanges()
                    store.gridEditingManager.resetDragState()
                }
                ButtonView(text: lang.resetDashboard, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading) {
                    store.lifecycleManager.showResetDashboardAlert()
                }
            } else {
                ButtonView(text: lang.editDashboard, type: .outlinedPrimary, size: .large, isDisabled: store.state.ui.isLoading) {
                    store.gridEditingManager.toggleEditMode()
                }
                if store.hasGoalSet {
                    ButtonView(text: lang.updateGoal, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading) {
                        tabViewModel.navigateToGoalSetting()
                    }
                }
                ButtonView(text: lang.metricInfo, type: .textPrimary, size: .large, isDisabled: store.state.ui.isLoading) {
                    let label = store.state.ui.selectedMetricLabel ?? DashboardStrings.weight
                    openMetricInfoWithoutSelection = MetricInfoWrapper(metricLabel: label)
                }
            }
        }
        .padding(.bottom, .spacingLG)
        .frame(maxWidth: .infinity)
        .background(
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture {
                    // Dismiss edit mode when tapping on horizontal spaces around action buttons
                    if store.state.ui.isEditMode && store.state.ui.alertData == nil {
                        store.cancelEdit()
                    }
                }
        )
    }
    
    private func noEntrySection() -> some View {
        NoEntryView(
            title: nil,
            description: DashboardStrings.noEntriesMessage
        ) {
                tabViewModel.pendingSettingsNavigation = .addEditScales
                tabViewModel.selectedTab = .settings
                tabViewModel.settingsNavigationSourceTab = .dash
            }
    }
}
