//
//  MetricGridUIKitView.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import SwiftUI
import UIKit

/// A SwiftUI wrapper around UICollectionView that displays metric items with drag-and-drop functionality
/// Provides iOS home screen-like behavior with wiggle animations and instant positioning
/// Uses existing EditModeOverlay for delete buttons instead of custom UIKit delete buttons
struct MetricGridUIKitView: UIViewRepresentable {
    
    // MARK: - Properties
    
    @ObservedObject var store: DashboardStore
    @State private var isDragging: Bool = false
    @State private var draggedItemId: String?
    var onMetricLongPress: ((String) -> Void)? = nil
    
    // MARK: - UIViewRepresentable
    
    func makeUIView(context: Context) -> UICollectionView {
        let layout = createLayout()
        let collectionView = createCollectionView(with: layout)
        setupCollectionView(collectionView, context: context)
        return collectionView
    }
    
    func updateUIView(_ uiView: UICollectionView, context: Context) {
        let coordinator = context.coordinator
        coordinator.store = store

        // Determine if content or layout actually changed
        let newIds = store.metricsToShow.map { $0.id }
        let newDashboardType = store.state.metrics.dashboardType
        let newIsEditMode = store.state.ui.isEditMode
        let newSelectedLabel = store.state.ui.selectedMetricLabel
        let contentChanged = newIds != coordinator.lastItemIds
        let layoutChanged = newDashboardType != coordinator.lastDashboardType
        let selectionChanged = newSelectedLabel != coordinator.lastSelectedMetricLabel

        // Keep drag interaction in sync with edit mode
        uiView.dragInteractionEnabled = newIsEditMode

        if contentChanged || layoutChanged {
            // When item count or layout changes, avoid batch updates; do a full, animation-less reload
            uiView.collectionViewLayout.invalidateLayout()
            UIView.performWithoutAnimation {
                uiView.reloadData()
            }
            coordinator.lastItemIds = newIds
            coordinator.lastDashboardType = newDashboardType
        } else {
            // No content/layout changes. Avoid explicit reloads; just update wiggle state if needed
            if newIsEditMode != coordinator.lastIsEditMode {
                uiView.visibleCells.forEach { cell in
                    if let metricCell = cell as? MetricCell {
                        metricCell.isWiggling = newIsEditMode
                        // Reconfigure SwiftUI content to reflect new edit state and hide overlays
                        if let item = metricCell.representedItem {
                            metricCell.configure(
                                with: item,
                                dashboardType: store.state.metrics.dashboardType,
                                store: store,
                                isBeingDragged: false
                            )
                        }
                    }
                }
            }

            // If selection changed, reconfigure visible cells to update highlight immediately
            if selectionChanged {
                uiView.visibleCells.forEach { cell in
                    if let metricCell = cell as? MetricCell, let item = metricCell.representedItem {
                        metricCell.configure(
                            with: item,
                            dashboardType: store.state.metrics.dashboardType,
                            store: store,
                            isBeingDragged: false
                        )
                    }
                }
            }
        }

        coordinator.lastIsEditMode = newIsEditMode
        coordinator.lastSelectedMetricLabel = newSelectedLabel
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    // MARK: - Private Methods
    
    /// Creates the collection view layout with proper spacing and insets
    private func createLayout() -> LeadingAlignedFlowLayout {
        let layout = LeadingAlignedFlowLayout()
        layout.minimumInteritemSpacing = .spacingSM
        layout.minimumLineSpacing = .spacingSM
        layout.sectionInset = UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        return layout
    }
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: LeadingAlignedFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.dragInteractionEnabled = store.state.ui.isEditMode // Only enable drag in edit mode
        collectionView.hideDragPlatter = false // show system drag preview platter
        collectionView.register(MetricCell.self, forCellWithReuseIdentifier: "MetricCell")
        
        // Disable selection to prevent visual feedback
        collectionView.allowsSelection = false
        
        // Disable user scrolling but allow content size calculation
        collectionView.isScrollEnabled = false
        collectionView.showsVerticalScrollIndicator = false
        collectionView.showsHorizontalScrollIndicator = false
        
        // Ensure the collection view can calculate its full content size
        collectionView.contentInsetAdjustmentBehavior = .never
        
        return collectionView
    }
    
    /// Sets up the collection view with delegates and gesture recognizers
    private func setupCollectionView(_ collectionView: UICollectionView, context: Context) {
        collectionView.delegate = context.coordinator
        collectionView.dataSource = context.coordinator
        collectionView.dragDelegate = context.coordinator
        collectionView.dropDelegate = context.coordinator
    }
}

// MARK: - Coordinator

extension MetricGridUIKitView {
    /// Coordinator class that handles all UICollectionView delegate methods
    /// and manages the interaction between UIKit and SwiftUI
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        // Cache for differential updates
        var lastItemIds: [UUID] = []
        var lastDashboardType: DashboardType = .dashboard12
        var lastIsEditMode: Bool = false
        var lastSelectedMetricLabel: String? = nil
        
        // MARK: - Properties
        
        var parent: MetricGridUIKitView
        var store: DashboardStore
        private var draggedItemId: String?
        
        // MARK: - Initialization
        
        init(_ parent: MetricGridUIKitView) {
            self.parent = parent
            self.store = parent.store
        }
        
        // MARK: - UICollectionViewDataSource
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            let count = store.metricsToShow.count
            return count
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "MetricCell", for: indexPath) as! MetricCell
            let item = store.metricsToShow[indexPath.item]
            
            // Check if this cell is currently being dragged
            let isBeingDragged = draggedItemId == item.id.uuidString
            
            cell.configure(
                with: item,
                dashboardType: store.state.metrics.dashboardType,
                store: store,
                isBeingDragged: isBeingDragged, // Pass drag state to cell
                onMetricLongPress: parent.onMetricLongPress,
                onSelectMetric: { label in
                    if label.isEmpty {
                        self.store.state.ui.selectedMetricLabel = nil
                    } else {
                        self.store.state.ui.selectedMetricLabel = label
                    }
                    // Publish selection change so the grid reconfigures visible cells immediately
                    self.store.objectWillChange.send()
                }
            )
            cell.rowIndex = indexPath.row
            cell.isWiggling = store.state.ui.isEditMode
            cell.gestureRecognizers?.forEach { cell.removeGestureRecognizer($0) }
            if store.state.ui.isEditMode {
                // No long-press delay drag start
                let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleMetricDragLongPress(_:)))
                longPress.minimumPressDuration = 0.0
                cell.addGestureRecognizer(longPress)
                cell.tag = indexPath.item
            }
            cell.isUserInteractionEnabled = true
            
            // Set up delete callback (EditModeOverlay handles the UI)
            cell.onDeleteTapped = {
                // Find the original index in the metrics array
                if let originalIndex = self.store.metricsManager.state.metrics.firstIndex(where: { $0.id == item.id }) {
                    Task {
                        try? await self.store.metricsManager.toggleMetricVisibility(at: originalIndex)
                    }
                }
            }
            
            return cell
        }
        
        @objc func handleMetricDragLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard gesture.state == .began,
                  let cell = gesture.view as? MetricCell,
                  let item = cell.representedItem,
                  store.state.ui.isEditMode else { return }
            // UIKit grid will handle drag-and-drop
        }
        
        // MARK: - UICollectionViewDelegateFlowLayout
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            // Calculate item size based on dashboard type
            let verticalPadding = store.state.metrics.dashboardType == .dashboard12 
                ? MetricCardView.twelveCardVerticalPadding 
                : MetricCardView.fourCardVerticalPadding
            
            
            let columns: Int
            if DevicePlatform.isTablet {
                columns = 4
            } else {
                // Use shared constants and add a safe fallback on item count to ensure
                // 3 columns for the 12-metric dashboard on mobile
                if store.state.metrics.dashboardType == .dashboard12 || store.metricsToShow.count >= 12 {
                    columns = DashboardConstants.UI.twelveMetricGridColumns
                } else {
                    columns = DashboardConstants.UI.fourMetricGridColumns
                }
            }
            let totalSpacing = 40 + CGFloat((columns - 1)) * .spacingSM
            let itemWidth = (collectionView.bounds.width - totalSpacing) / CGFloat(columns)
            let itemHeight = 70 + (verticalPadding * 2) // Base height + padding
            
            return CGSize(width: itemWidth, height: itemHeight)
        }
        
        // MARK: - UICollectionViewDragDelegate
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            let item = store.metricsToShow[indexPath.item]
            let isRemoved = store.isMetricRemovedInReorderedArray(at: indexPath.item)
            
            if isRemoved {
                return [] // Return empty array to prevent drag
            }
            
            if !store.state.ui.isEditMode { return [] } // Prevent drag if not in edit mode
            
            let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
            let dragItem = UIDragItem(itemProvider: itemProvider)
            dragItem.localObject = item
            
            // Store the source index path for use in drag preview
            session.localContext = indexPath
            
            // Track the dragged item ID
            draggedItemId = item.id.uuidString
            
            // Update store drag state for EditModeOverlay visibility IMMEDIATELY
            store.startDraggingMetric(item)
            
            // Immediately hide EditModeOverlay on the dragged cell
            if let cell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                cell.updateDragState(true) // Use the new method for more reliable state management
            }
            
            // Provide haptic feedback
            HapticFeedbackService.medium()
            return [dragItem]
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let parameters = UIDragPreviewParameters()
            parameters.backgroundColor = .clear
            if let cell = collectionView.cellForItem(at: indexPath) {
                parameters.visiblePath = UIBezierPath(roundedRect: cell.bounds, cornerRadius: 16)
            }
            return parameters
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewForLiftingItem item: UIDragItem,
                            session: UIDragSession) -> UITargetedDragPreview? {
            if let indexPath = session.localContext as? IndexPath,
               let metricCell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                let previewView = metricCell.snapshotForPreview()
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 16)
                let s = DashboardConstants.UI.dragPreviewScale
                let target = UIDragPreviewTarget(
                    container: collectionView,
                    center: metricCell.center,
                    transform: CGAffineTransform(scaleX: s, y: s)
                )
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
            }
            if let metricCell = collectionView.visibleCells.first(where: {
                guard let cellItem = ($0 as? MetricCell)?.representedItem,
                      let dragItem = item.localObject as? MetricItem else { return false }
                return cellItem.id.uuidString == dragItem.id.uuidString
            }) as? MetricCell {
                let previewView = metricCell.snapshotForPreview()
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 16)
                let s = DashboardConstants.UI.dragPreviewScale
                let target = UIDragPreviewTarget(
                    container: collectionView,
                    center: metricCell.center,
                    transform: CGAffineTransform(scaleX: s, y: s)
                )
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
            }
            return nil
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionWillBegin session: UIDragSession) {
            parent.isDragging = true
            
            // Immediately hide EditModeOverlay on the dragged cell
            if let draggedItem = session.items.first,
               let dragItem = draggedItem.localObject as? MetricItem {
                // Find and hide EditModeOverlay on the dragged metric cell
                for cell in collectionView.visibleCells {
                    if let metricCell = cell as? MetricCell,
                       metricCell.representedItem?.id == dragItem.id {
                        metricCell.updateDragState(true) // Use the new method for more reliable state management
                        break
                    }
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            parent.isDragging = false
            draggedItemId = nil
            
            // Clear the store's drag state
            store.endDragging()
            
            // Immediately restore EditModeOverlay visibility on all cells if in edit mode
            if store.state.ui.isEditMode {
                // Update all visible cells to restore EditModeOverlay visibility
                for cell in collectionView.visibleCells {
                    if let metricCell = cell as? MetricCell {
                        metricCell.updateDragState(false) // Use the new method for more reliable state management
                    }
                }
            }
        }
        
        // Additional drag preview methods to fully suppress all visual feedback
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewForCancelling item: UIDragItem,
                            withDefault defaultPreview: UITargetedDragPreview) -> UITargetedDragPreview? {
            return defaultPreview
        }
        
        func collectionView(_ collectionView: UICollectionView, item: UIDragItem, willAnimateCancelWith animator: UIDragAnimating) {
            // Suppress any cancel animation visual feedback
            animator.addCompletion { _ in
                // Clear the dragged item ID when drag is cancelled
                self.draggedItemId = nil
                
                // Clear the store's drag state
                self.store.endDragging()
                
                // Immediately restore EditModeOverlay visibility on all cells if in edit mode
                if self.store.state.ui.isEditMode {
                    // Update all visible cells to restore EditModeOverlay visibility
                    for cell in collectionView.visibleCells {
                        if let metricCell = cell as? MetricCell {
                            metricCell.updateDragState(false) // Use the new method for more reliable state management
                        }
                    }
                }
            }
        }
        
        // MARK: - UICollectionViewDropDelegate
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            guard store.state.ui.isEditMode else {
                return UICollectionViewDropProposal(operation: .forbidden)
            }
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnter session: UIDropSession) {
            // Immediately clear any existing drag state when drop session enters
            store.endDragging()
            draggedItemId = nil
            parent.isDragging = false
            
            // Disable animations when drop session enters
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.commit()
        }
        
        func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
            guard store.state.ui.isEditMode else { return }
            guard let destinationIndexPath = coordinator.destinationIndexPath,
                  let item = coordinator.items.first,
                  let sourceIndexPath = item.sourceIndexPath else {
                return 
            }
            
            // Completely disable ALL animations and force instant positioning (like iOS home screen)
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            UIView.performWithoutAnimation {
                collectionView.performBatchUpdates({
                    // Check if both source and destination are not removed
                    let sourceIsRemoved = store.isMetricRemovedInReorderedArray(at: sourceIndexPath.item)
                    let destIsRemoved = store.isMetricRemovedInReorderedArray(at: destinationIndexPath.item)
  
                    // Only allow move if neither source nor destination is removed
                    if !sourceIsRemoved && !destIsRemoved {
                        store.moveMetric(from: sourceIndexPath.item, to: destinationIndexPath.item)
                        collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                    } else {
                        collectionView.reloadData()
                    }
                })
            }
            CATransaction.commit()
            
            // Immediately clear drag state after drop is executed
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            
            // Immediately restore EditModeOverlay visibility on all cells if in edit mode
            if store.state.ui.isEditMode {
                for cell in collectionView.visibleCells {
                    if let metricCell = cell as? MetricCell {
                        metricCell.updateDragState(false)
                    }
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnd session: UIDropSession) {
            // Immediately clear drag state when drop session ends
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            UIView.performWithoutAnimation {
                collectionView.layoutIfNeeded()
                collectionView.visibleCells.forEach { cell in
                    cell.layer.removeAllAnimations()
                    cell.contentView.layer.removeAllAnimations()
                    cell.transform = .identity
                    cell.contentView.transform = .identity
                }
            }
            CATransaction.commit()
            
            // Immediately restore EditModeOverlay visibility on all cells if in edit mode
            if store.state.ui.isEditMode {
                for cell in collectionView.visibleCells {
                    if let metricCell = cell as? MetricCell {
                        metricCell.updateDragState(false)
                    }
                }
            }
        }
    }
} 
