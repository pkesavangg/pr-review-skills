//
//  DashboardView.swift
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

            NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false)
                .zIndex(100)
            
            ScrollView(showsIndicators: false) {
                WeightTrendView()
                    .frame(height: 490)
                    .padding(.top, .spacingLG)

                if !store.allContentRemoved {
                    metricGridSection()
                        .padding(.top,.spacingSM)

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
        }
        .onAppear(){
            store.loadLatestEntryData()
        }
        .ignoresSafeArea(.all)
        .background(theme.backgroundSecondary)
        .sheet(item: $selectedEntry) { entry in
            ScaleMetricsView(entry: entry, selectedMetric: selectedMetric ?? .bmi)
        }
        .sheet(item: $openMetricInfoWithoutSelection) { wrapper in
            // Open metric info without affecting selection state
            ScaleMetricsView(
                entry: store.createEntryForMetricInfo(metricLabel: wrapper.metricLabel),
                selectedMetric: store.getBodyMetric(for: wrapper.metricLabel)
            )
        }
        .onChange(of: selectedMetricInfo) { oldValue, newValue in
            guard let label = newValue else { return }
            store.selectedMetricLabel = label
            selectedEntry = store.createEntryForMetricInfo()
            selectedMetric = store.selectedBodyMetric
            selectedMetricInfo = nil
        }
        .onChange(of: store.selectedMetricLabel) { oldValue, newValue in
            // Handle deselection: if selectedMetricLabel becomes nil, close the sheet
            if newValue == nil && oldValue != nil {
                selectedEntry = nil
                selectedMetric = nil
            }
        }
        .onChange(of: store.isEditMode) { oldValue, newValue in
            store.resetDragState()
        }
        .presentAlert(alertData: $store.alertData)
        .presentLoader(loaderData: store.loaderData)
        .onAppear {
            store.loadGoalCardData()
        }
    }

    // MARK: - Metric Grid Section
    private func metricGridSection() -> some View {
        LazyVGrid(columns: store.metricGridColumns, spacing: 16) {
            ForEach(store.metricsToShow) { item in
                metricCardView(for: item)
            }
        }
        .padding(.top, .spacing3XL)
        .padding(.horizontal, .spacingSM)
        .id(store.gridLayoutId)
        .animation(.easeInOut(duration: 0.3), value: store.gridLayoutId)
    }
    
    // MARK: - Metric Card View Helper
    private func metricCardView(for item: MetricItem) -> some View {
        let isRemoved = store.isMetricRemovedInReorderedArray(at: store.metricsToShow.firstIndex(of: item) ?? 0)
        let verticalPadding = store.metricType == .twelve ? MetricCardView.twelveCardVerticalPadding : MetricCardView.fourCardVerticalPadding
        
        return MetricCardView(
            value: store.formattedMetricValue(for: (item.preLabel, item.value)),
            label: item.label,
            metricType: store.metricType,
            isEditMode: store.isEditMode,
            isRemoved: isRemoved,
            isSelected: store.selectedMetricLabel == item.label,
            onToggleRemoval: {
                if let index = store.metricsToShow.firstIndex(of: item) {
                    store.toggleMetricRemovalInReorderedArray(at: index)
                }
            },
            onTap: {
                store.selectMetric(item.label)
            },
            isDropTarget: store.dropHoverId == item.label,
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            verticalPadding: verticalPadding
        )
        .editModeOverlay(
            isEditMode: store.isEditMode,
            isRemoved: isRemoved,
            onToggleRemoval: {
                if let index = store.metricsToShow.firstIndex(of: item) {
                    store.toggleMetricRemovalInReorderedArray(at: index)
                }
            },
            isBeingDragged: store.draggingMetric?.id == item.id,
            isDropTarget: store.dropHoverId == item.label
        )
        .draggableReorder(
            item: item,
            draggingItem: $store.draggingMetric,
            items: $store.metrics,
            isDraggable: store.isEditMode && !isRemoved,
            onDropTargetChanged: { isTargeted in
                store.dropHoverId = isTargeted ? item.label : nil
            }
        )
        .longPressGesture(isEditMode: store.isEditMode) {
            // Handle long press for metric info
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
        .padding(.top, (!store.isEditMode && store.isGoalCardRemoved) ? 0 : .spacingSM)
        .padding(.horizontal, .spacingSM)
        .id(store.gridLayoutId)
        .animation(.easeInOut(duration: 0.3), value: store.gridLayoutId)
    }
    
    // MARK: - Streak Card View Helper
    private func streakCardView(for item: MetricItem) -> some View {
        let isRemoved = store.isStreakRemovedInReorderedArray(at: store.streakItemsToShow.firstIndex(of: item) ?? 0)
        
        return StreakCardView(
            value: item.value,
            label: item.label,
            icon: item.icon,
            isEditMode: store.isEditMode,
            isRemoved: isRemoved,
            isDropTarget: store.dropHoverId == item.label,
            onToggleRemoval: {
                if let index = store.streakItemsToShow.firstIndex(of: item) {
                    store.toggleStreakRemovalInReorderedArray(at: index)
                }
            },
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in }
        )
        .editModeOverlay(
            isEditMode: store.isEditMode,
            isRemoved: isRemoved,
            onToggleRemoval: {
                if let index = store.streakItemsToShow.firstIndex(of: item) {
                    store.toggleStreakRemovalInReorderedArray(at: index)
                }
            },
            isBeingDragged: store.draggingStreak?.id == item.id,
            isDropTarget: store.dropHoverId == item.label
        )
        .draggableReorder(
            item: item,
            draggingItem: $store.draggingStreak,
            items: $store.streakItems,
            isDraggable: store.isEditMode && !isRemoved,
            onDropTargetChanged: { isTargeted in
                store.dropHoverId = isTargeted ? item.label : nil
            }
        )
    }
    
    // MARK: - Goal Progress Section
    private func goalCardSection() -> some View {
        if !store.isEditMode && store.isGoalCardRemoved {
            return AnyView(EmptyView())
        }
        
        return AnyView(
            goalCardView()
        )
    }
    
    // MARK: - Goal Card View Helper
    private func goalCardView() -> some View {
        GoalProgressCardView(
            delta: store.goalDelta,
            startWeight: store.goalStartWeight,
            goalWeight: store.goalWeight,
            unit: store.goalUnit.rawValue,
            isRemoved: store.isGoalCardRemoved,
            progress: store.goalProgress,
            goalType: store.goalType
        )
        .editModeOverlay(
            isEditMode: store.isEditMode,
            isRemoved: store.isGoalCardRemoved,
            onToggleRemoval: {
                store.toggleGoalCardRemoval()
            },
            isBeingDragged: false,
            isDropTarget: false
        )
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Action Buttons
    private func actionButtonsSection() -> some View {
        VStack(alignment: .center, spacing: .spacingSM) {
            if store.isEditMode {
                ButtonView(text: lang.saveChanges, type: .filledPrimary, size: .large, isDisabled: false, action: {
                    store.saveChanges()
                    store.resetDragState()
                })
                ButtonView(text: lang.resetDashboard, type: .textPrimary, size: .large, isDisabled: false, action: {
                    store.showResetDashboardAlert()
                })
            } else {
                ButtonView(text: lang.editDashboard, type: .outlinedPrimary, size: .large, isDisabled: false, action: {
                    store.isEditMode.toggle()
                    if store.isEditMode {
                        store.resetDragState()
                    }
                })
                ButtonView(text: lang.updateGoal, type: .textPrimary, size: .large, isDisabled: false, action: {
                    tabViewModel.navigateToGoalSetting()
                })
                ButtonView(text: lang.metricInfo, type: .textPrimary, size: .large, isDisabled: false, action: {
                    // If a metric is selected, open its metric info
                    if let selectedLabel = store.selectedMetricLabel {
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
        if store.selectedMetricLabel != metricLabel {
            store.selectMetric(metricLabel)
        }
        
        // Open metric info sheet
        selectedMetricInfo = metricLabel
    }
}
