//
//  DashboardScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI

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
        .onAppear(perform: onAppearActions)
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
            handleSelectedMetricInfoChange(newValue)
        }
        .onChange(of: store.state.ui.selectedMetricLabel) { _, newValue in
            handleSelectedMetricLabelChange(newValue)
        }
        .onChange(of: selectedEntry) { _, newValue in
            handleSelectedEntryChange(newValue)
        }
        .onChange(of: openMetricInfoWithoutSelection) { _, newValue in
            handleMetricInfoSheetDismiss(newValue)
        }
        .onChange(of: store.state.ui.isEditMode) { _, _ in store.resetDragState() }
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
                metricGridSection()
                    .padding(.top, .spacingSM)
                if !store.metricsToShow.isEmpty {
                    Divider()
                        .foregroundColor(theme.statusUtilityPrimary)
                        .padding(.vertical, .spacingSM)
                        .padding(.horizontal, .spacing2XL)
                }
                goalCardSection()
                if store.shouldShowStreakGrid {
                    streakAndLossGrid()
                }
            }
            actionButtonsSection()
                .padding(.top, store.allContentRemoved ? .spacing6XL : .spacingSM)
        }
        .padding(.top, .zero)
    }

    // MARK: - Metric Grid Section
    private func metricGridSection() -> some View {
        LazyVGrid(columns: store.metricGridColumns, spacing: 16) {
            ForEach(store.metricsToShow) { item in
                metricCardView(for: item)
            }
        }
        .padding(.horizontal, .spacingSM)
        .id(store.state.ui.gridLayoutId)
        .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
    }

    // MARK: - Metric Card View Helper
    private func metricCardView(for item: MetricItem) -> some View {
        let index = store.metricsToShow.firstIndex(of: item) ?? 0
        let isRemoved = store.isMetricRemovedInReorderedArray(at: index)
        let isSelected = store.state.ui.selectedMetricLabel == item.label
        let verticalPadding = store.state.metrics.metricType == .twelve ? MetricCardView.twelveCardVerticalPadding : MetricCardView.fourCardVerticalPadding

        let card = MetricCardView(
            value: store.formattedMetricValue(for: (item.preLabel, item.value)),
            label: item.label,
            metricType: store.state.metrics.metricType,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: isRemoved,
            isSelected: isSelected,
            onToggleRemoval: {
                if let idx = store.metricsToShow.firstIndex(of: item) {
                    store.toggleMetricRemovalInReorderedArray(at: idx)
                }
            },
            onTap: {
                store.selectMetric(item.label)
                // Only select the metric and show the line chart on tap
                // Metric info sheet should only open on long press
            },
            isDropTarget: store.state.ui.dropHoverId == item.label,
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            verticalPadding: verticalPadding
        )

        return card
            .editModeOverlay(
                isEditMode: store.state.ui.isEditMode,
                isRemoved: isRemoved,
                onToggleRemoval: {
                    if let idx = store.metricsToShow.firstIndex(of: item) {
                        store.toggleMetricRemovalInReorderedArray(at: idx)
                    }
                },
                isBeingDragged: store.state.ui.draggingMetric?.id == item.id,
                isDropTarget: store.state.ui.dropHoverId == item.label
            )
            .draggableReorder(
                item: item,
                draggingItem: $store.state.ui.draggingMetric,
                items: $store.state.metrics.metrics,
                isDraggable: store.state.ui.isEditMode && !isRemoved,
                onDropTargetChanged: { isTargeted in
                    store.state.ui.dropHoverId = isTargeted ? item.label : nil
                }
            )
            .longPressGesture(isEditMode: store.state.ui.isEditMode) {
                handleMetricLongPress(for: item.label)
            }
    }

    // MARK: - Streak and Loss Grid
    private func streakAndLossGrid() -> some View {
        LazyVGrid(columns: store.streakColumns, spacing: 16) {
            ForEach(store.streakItemsToShow) { item in
                streakCardView(for: item)
            }
        }
        .padding(.bottom, .spacingSM)
        .padding(.top, (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : .spacingSM)
        .padding(.horizontal, .spacingSM)
        .id(store.state.ui.gridLayoutId)
        .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
    }

    // MARK: - Streak Card View Helper
    private func streakCardView(for item: MetricItem) -> some View {
        let index = store.streakItemsToShow.firstIndex(of: item) ?? 0
        let isRemoved = store.isStreakRemovedInReorderedArray(at: index)

        let card = StreakCardView(
            value: item.value,
            label: item.label,
            icon: item.icon,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: isRemoved,
            isDropTarget: store.state.ui.dropHoverId == item.label,
            onToggleRemoval: {
                if let idx = store.streakItemsToShow.firstIndex(of: item) {
                    store.toggleStreakRemovalInReorderedArray(at: idx)
                }
            },
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in }
        )

        return card
            .editModeOverlay(
                isEditMode: store.state.ui.isEditMode,
                isRemoved: isRemoved,
                onToggleRemoval: {
                    if let idx = store.streakItemsToShow.firstIndex(of: item) {
                        store.toggleStreakRemovalInReorderedArray(at: idx)
                    }
                },
                isBeingDragged: store.state.ui.draggingStreak?.id == item.id,
                isDropTarget: store.state.ui.dropHoverId == item.label
            )
            .draggableReorder(
                item: item,
                draggingItem: $store.state.ui.draggingStreak,
                items: $store.state.streak.streakItems,
                isDraggable: store.state.ui.isEditMode && !isRemoved,
                onDropTargetChanged: { isTargeted in
                    store.state.ui.dropHoverId = isTargeted ? item.label : nil
                }
            )
    }

    // MARK: - Goal Progress Section
    private func goalCardSection() -> some View {
        if !store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved {
            return AnyView(EmptyView())
        }
        return AnyView(goalCardView())
    }

    // MARK: - Goal Card View Helper
    private func goalCardView() -> some View {
        GoalProgressCardView(
            delta: store.state.goal.goalDelta,
            startWeight: store.state.goal.goalStartWeight,
            goalWeight: store.state.goal.goalWeight,
            unit: store.state.goal.goalUnit.rawValue,
            isRemoved: store.state.ui.isGoalCardRemoved,
            progress: store.state.goal.goalProgress,
            goalType: store.state.goal.goalType,
            isWeightlessMode: store.isWeightlessModeEnabled
        )
        .editModeOverlay(
            isEditMode: store.state.ui.isEditMode,
            isRemoved: store.state.ui.isGoalCardRemoved,
            onToggleRemoval: { store.toggleGoalCardRemoval() },
            isBeingDragged: false,
            isDropTarget: false
        )
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Action Buttons
    private func actionButtonsSection() -> some View {
        VStack(alignment: .center, spacing: .spacingSM) {
            if store.state.ui.isEditMode {
                ButtonView(text: lang.saveChanges, type: .filledPrimary, size: .large, isDisabled: false, action: {
                    store.saveChanges()
                    store.resetDragState()
                })
                ButtonView(text: lang.resetDashboard, type: .textPrimary, size: .large, isDisabled: false, action: {
                    store.showResetDashboardAlert()
                })
            } else {
                ButtonView(text: lang.editDashboard, type: .outlinedPrimary, size: .large, isDisabled: false, action: {
                    store.state.ui.isEditMode.toggle()
                    if store.state.ui.isEditMode {
                        store.resetDragState()
                    }
                })
                ButtonView(text: lang.updateGoal, type: .textPrimary, size: .large, isDisabled: false, action: {
                    tabViewModel.navigateToGoalSetting()
                })
                ButtonView(text: lang.metricInfo, type: .textPrimary, size: .large, isDisabled: false, action: {
                    // If a metric is selected, open its metric info
                    if let selectedLabel = store.state.ui.selectedMetricLabel {
                        selectedMetricInfo = selectedLabel
                    } else {
                        // If no metric is selected, open with default .weight metric
                        openMetricInfoWithoutSelection = MetricInfoWrapper(metricLabel: DashboardStrings.weight)
                    }
                })
            }
        }
        .padding(.bottom, .spacingLG)
    }

    // MARK: - Helper Methods
    private func handleMetricLongPress(for metricLabel: String) {
        // Update selection state if needed
        if store.state.ui.selectedMetricLabel != metricLabel {
            store.selectMetric(metricLabel)
        }
        // Open metric info sheet
        selectedMetricInfo = metricLabel
    }

    private func onAppearActions() {
        store.loadLatestEntryData()
        store.loadGoalCardData()
        // Handle any settings changes
        store.handleSettingsChange()
        // Ensure chart shows the latest entries by default
        store.ensureLatestEntriesVisible()
    }

    private func handleSelectedMetricInfoChange(_ newValue: String?) {
        guard let label = newValue else { return }
        store.state.ui.selectedMetricLabel = label
        selectedEntry = store.createEntryForMetricInfo()
        selectedMetric = store.selectedBodyMetric
        selectedMetricInfo = nil
    }

    private func handleSelectedMetricLabelChange(_ newValue: String?) {
        if newValue == nil {
            selectedEntry = nil
            selectedMetric = nil
        }
    }

    private func handleSelectedEntryChange(_ newValue: Entry?) {
        if newValue == nil {
            store.state.ui.selectedMetricLabel = nil
        }
    }

    private func handleMetricInfoSheetDismiss(_ newValue: MetricInfoWrapper?) {
        if newValue == nil {
            store.state.ui.selectedMetricLabel = nil
        }
    }
}
