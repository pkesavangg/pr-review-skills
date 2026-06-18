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
    let lang = DashboardStrings.self
    @State private var selectedEntry: Entry?
    @State private var selectedMetric: BodyMetric?
    @State private var selectedMetricInfo: String?
    @State private var openMetricInfoWithoutSelection: MetricInfoWrapper?
    @State private var suppressOutsideCancel = false
    @State private var metricInfoEntry: Entry?
    @State private var isProductTypeSelectorPresented = false
    @State private var isInProductDashboard = false
    /// Tracks whether the per-session persistence redirect has already been applied,
    /// so subsequent re-appearances (tab switch, background/foreground) don't override
    /// the user's in-session navigation choices.
    @State private var hasInitializedProductRedirect = false
    private var canShowSnapshotOverview: Bool {
        store.canShowSnapshotOverview
    }

    private var shouldShowSnapshotOverview: Bool {
        canShowSnapshotOverview && !isInProductDashboard
    }

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                if shouldShowSnapshotOverview {
                    snapshotLogo()
                    ScrollView(showsIndicators: false) {
                        MultiDeviceSnapshotView(
                            availableItems: store.availableProductItems,
                            selectedItem: store.selectedProductItem,
                            onSelectItem: { selectedItem in
                                store.selectProductItem(selectedItem)
                                isInProductDashboard = true
                            },
                            onAddBaby: {
                                tabViewModel.navigateToSettings(route: .addBaby)
                            }
                        )
                    }
                } else {
                    navbarHeader()
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if store.state.ui.isEditMode {
                                store.cancelEdit()
                            }
                        }
                    dashboardScroll(availableHeight: proxy.size.height)
                }
            }
        }
        .refreshable {
            await store.lifecycleManager.refreshAll()
        }
        .onAppear(perform: store.lifecycleManager.onAppearActions)
        .onAppear {
            // Handles the case where availableProductItems is already populated when the
            // view first renders (data loaded before first appear). The onChange below
            // covers the async case where data arrives after appear.
            applyInitialProductRedirectIfNeeded()
        }
        .onChange(of: canShowSnapshotOverview) { _, isAvailable in
            guard isAvailable else { return }
            if let redirected = ProductTypeStore.resolveInitialProductRedirect(
                hasInitializedProductRedirect: hasInitializedProductRedirect,
                canShowSnapshotOverview: isAvailable,
                productTypeStore: store.productTypeSelectorStore
            ) {
                isInProductDashboard = redirected
                hasInitializedProductRedirect = true
            } else if hasInitializedProductRedirect {
                // Mid-session: a new product type became available (e.g. device added).
                // Show the snapshot overview so the user sees the updated device list.
                isInProductDashboard = false
            }
        }
        .ignoresSafeArea(.all, edges: canShowSnapshotOverview ? .bottom : .all)
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
        let isProductDashboardFromSnapshot = canShowSnapshotOverview && isInProductDashboard
        // Show the product-selector title + chevron whenever there are multiple selectable items.
        // This covers two cases:
        //   1. Multi-product: user drilled in from the snapshot overview (isProductDashboardFromSnapshot).
        //   2. Single-product-type with multiple profiles: e.g. only a baby scale paired but 2+ baby
        //      profiles exist — no snapshot overview shows, but the dropdown is still needed.
        let showProductSelector = isProductDashboardFromSnapshot ||
            (!canShowSnapshotOverview && store.availableProductItems.count > 1)
        return NavbarHeaderView<AppIconView, EmptyView>(
            title: showProductSelector ? store.selectedProductItem.dashboardTitle : nil,
            leadingContent: isProductDashboardFromSnapshot
                ? { AppIconView(icon: AppAssets.chevronLeft) }
                : nil,
            onLeadingTap: isProductDashboardFromSnapshot ? { isInProductDashboard = false } : nil,
            onTitleTap: showProductSelector ? {
                isProductTypeSelectorPresented = true
            } : nil,
            canShowBorder: false,
            canShowTitleChevron: showProductSelector
        )
        .sheet(isPresented: $isProductTypeSelectorPresented) {
            ProductTypeSelectorSheet(
                store: store.productTypeSelectorStore,
                isPresented: $isProductTypeSelectorPresented,
                title: DashboardStrings.selectGraph
            )
        }
        .zIndex(100)
    }
    
    private func dashboardScroll(availableHeight: CGFloat) -> some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                if case .baby(let profile) = store.selectedProductItem {
                    babyDashboardContent(babyProfile: profile)
                } else if store.productType == .bpm {
                    bpmDashboardContent(availableHeight: availableHeight)
                } else {
                    weightDashboardContent(availableHeight: availableHeight)
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
    private func weightDashboardContent(availableHeight: CGFloat) -> some View {
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
    }

    @ViewBuilder
    private func bpmDashboardContent(availableHeight: CGFloat) -> some View {
        BpmTrendView(dashboardStore: store)
        if store.state.data.hasAnyEntries {
            BpmMetricsSection(store: store)
        }
    }

    @ViewBuilder
    private func babyDashboardContent(babyProfile: BabyProfile) -> some View {
        if babyProfile.isPendingSelection {
            NoBabySnapshotCard {
                tabViewModel.navigateToSettings(route: .addBaby)
            }
            .padding(.horizontal, .spacingMD)
            .padding(.top, .spacingXL)
        } else {
            BabyTrendView(dashboardStore: store, babyProfile: babyProfile)
        }
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

    // MARK: - Persistence Redirect

    private func applyInitialProductRedirectIfNeeded() {
        guard let redirected = ProductTypeStore.resolveInitialProductRedirect(
            hasInitializedProductRedirect: hasInitializedProductRedirect,
            canShowSnapshotOverview: canShowSnapshotOverview,
            productTypeStore: store.productTypeSelectorStore
        ) else { return }
        isInProductDashboard = redirected
        hasInitializedProductRedirect = true
    }
}
