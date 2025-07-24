//
//  DashboardMetricCellWrapper.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/07/25.
//

import SwiftUI

/// SwiftUI view that provides metric card with edit overlay functionality
struct DashboardMetricCellWithOverlay: View {
    
    // MARK: - Properties
    
    let item: MetricItem
    let store: DashboardStore
    let onToggleRemoval: () -> Void
    let onTap: () -> Void
    
    // MARK: - Computed Properties
    
    private var isRemoved: Bool {
        store.isMetricRemovedInReorderedArray(at: store.metricsToShow.firstIndex(of: item) ?? 0)
    }
    
    private var isBeingDragged: Bool {
        store.state.ui.draggingMetric?.id == item.id
    }
    
    private var isDropTarget: Bool {
        store.state.ui.dropHoverId == item.label
    }
    
    private var isSelected: Bool {
        store.state.ui.selectedMetricLabel == item.label
    }
    
    private var verticalPadding: CGFloat {
        store.state.metrics.dashboardType == .dashboard12 ? 
            MetricCardView.twelveCardVerticalPadding : 
            MetricCardView.fourCardVerticalPadding
    }
    
    // MARK: - Body
    
    var body: some View {
        MetricCardView(
            value: store.formattedMetricValue(for: (item.preLabel, item.value)),
            label: item.label,
            dashboardType: store.state.metrics.dashboardType,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: isRemoved,
            isSelected: isSelected,
            onToggleRemoval: onToggleRemoval,
            onTap: onTap,
            isDropTarget: isDropTarget,
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            verticalPadding: verticalPadding
        )
        .editModeOverlay(
            isEditMode: store.state.ui.isEditMode,
            isRemoved: isRemoved,
            onToggleRemoval: onToggleRemoval,
            isBeingDragged: isBeingDragged,
            isDropTarget: isDropTarget
        )
    }
} 