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
            },
            isDropTarget: store.state.ui.dropHoverId == item.label,
            onDrop: { _, _ in true },
            onDropTargetChanged: { isTargeted in
                store.updateDropTarget(isTargeted ? item.label : nil)
            },
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
                draggingItem: store.draggingMetricBinding,
                items: store.metricsBinding,
                isDraggable: store.state.ui.isEditMode && !isRemoved,
                onDropTargetChanged: { isTargeted in
                    store.updateDropTarget(isTargeted ? item.label : nil)
                },
                onDragEnd: {
                    store.handleMetricDragEnd()
                }
            )
            .longPressGesture(isEditMode: store.state.ui.isEditMode) {
                store.handleMetricLongPress(for: item.label, selectedEntry: $selectedEntry, selectedMetric: $selectedMetric)
            }
    }

    // MARK: - Streak and Loss Grid
    private func streakAndLossGrid() -> some View {
        LazyVGrid(columns: store.streakColumns, spacing: 16) {
            ForEach(store.streakItemsToShow) { item in
                streakCardView(for: item)
            }
        }
        .id("\(store.state.ui.gridLayoutId)-\(store.currentUnitText)") // Force refresh when unit changes
        .padding(.bottom, .spacingSM)
        .padding(.top, (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : .spacingSM)
        .padding(.horizontal, .spacingSM)
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
            onDropTargetChanged: { isTargeted in
                store.updateDropTarget(isTargeted ? item.label : nil)
            }
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
                draggingItem: store.draggingStreakBinding,
                items: store.streakItemsBinding,
                isDraggable: store.state.ui.isEditMode && !isRemoved,
                onDropTargetChanged: { isTargeted in
                    store.updateDropTarget(isTargeted ? item.label : nil)
                },
                onDragEnd: {
                    store.handleStreakDragEnd()
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
            unit: store.currentUnitText,
            isRemoved: store.state.ui.isGoalCardRemoved,
            progress: store.state.goal.goalProgress,
            goalType: store.state.goal.goalType,
            isWeightlessMode: store.isWeightlessModeEnabled
        )
        .id(store.currentUnitText) // Force refresh when unit changes
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
                    selectedMetricInfo = store.state.ui.selectedMetricLabel ?? DashboardStrings.weight
                })
            }
        }
        .padding(.bottom, .spacingLG)
    }

}
