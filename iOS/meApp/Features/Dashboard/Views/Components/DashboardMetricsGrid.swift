//
//  DashboardMetricsGrid.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/07/25.
//

import SwiftUI
import UIKit

/// A SwiftUI grid that provides drag-and-drop functionality
/// for reordering dashboard metric items, similar to iOS home screen behavior
struct DashboardMetricsGrid: View {
    
    // MARK: - Properties
    
    @ObservedObject var store: DashboardStore
    @State private var isDragging: Bool = false
    @State private var draggedItemId: UUID?
    
    // MARK: - Body
    
    var body: some View {
        LazyVGrid(columns: store.metricGridColumns, spacing: 16) {
            ForEach(store.metricsToShow) { item in
                DashboardMetricCellWithOverlay(
                    item: item,
                    store: store,
                    onToggleRemoval: {
                        let index = store.metricsToShow.firstIndex(of: item) ?? 0
                        store.toggleMetricRemovalInReorderedArray(at: index)
                    },
                    onTap: {
                        if !store.state.ui.isEditMode {
                            store.selectMetric(item.label)
                        }
                    }
                )
                .draggableReorder(
                    item: item,
                    draggingItem: store.draggingMetricBinding,
                    items: store.metricsBinding,
                    isDraggable: store.state.ui.isEditMode && !store.isMetricRemovedInReorderedArray(at: store.metricsToShow.firstIndex(of: item) ?? 0),
                    onDropTargetChanged: { isTargeted in
                        store.updateDropTarget(isTargeted ? item.label : nil)
                    },
                    onDragEnd: {
                        store.handleMetricDragEnd()
                    }
                )
            }
        }
        .id("\(store.state.ui.gridLayoutId)-\(store.currentUnitText)")
        .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
        .onLongPressGesture {
            store.toggleEditMode()
        }
        .onTapGesture {
            if store.state.ui.isEditMode {
                store.toggleEditMode()
            }
        }
    }
}