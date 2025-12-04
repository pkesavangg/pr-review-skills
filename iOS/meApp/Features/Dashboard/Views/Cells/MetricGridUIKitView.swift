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
    // Parent context (dashboard vs setup)
    var parentView: DashboardMetricsParentView = .dashboard
    
    // MARK: - Properties
    
    @ObservedObject var store: DashboardStore
    @State private var isDragging: Bool = false
    @State private var draggedItemId: String?
    @State private var isDragOutsideBounds: Bool = false
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
        let newRemovedMetrics = store.state.ui.removedMetrics
        let newActiveMetricsCount = store.metricsManager.state.activeMetricsCount
        
        let contentChanged = newIds != coordinator.lastItemIds
        let layoutChanged = newDashboardType != coordinator.lastDashboardType
        let selectionChanged = newSelectedLabel != coordinator.lastSelectedMetricLabel
        let removalStateChanged = newRemovedMetrics != coordinator.lastRemovedMetrics
        let activeMetricsCountChanged = newActiveMetricsCount != coordinator.lastActiveMetricsCount
        
        // Check if this is a reset operation (all metrics restored and order reset)
        let isResetOperation = newRemovedMetrics.isEmpty && 
                              newActiveMetricsCount == store.metricsManager.state.metrics.count &&
                              coordinator.lastRemovedMetrics.count > 0
        
        // Disable system drag interaction; we use interactive movement with a clamped gesture
        uiView.dragInteractionEnabled = false

        // Suppress reloads during reset to prevent flickering
        if store.state.ui.isResettingDashboard {
            return
        }

        if coordinator.suppressNextReload && contentChanged && !layoutChanged && !removalStateChanged {
            coordinator.lastItemIds = newIds
            coordinator.lastDashboardType = newDashboardType
            coordinator.lastActiveMetricsCount = newActiveMetricsCount
            coordinator.suppressNextReload = false
            uiView.collectionViewLayout.invalidateLayout()
            uiView.layoutIfNeeded()
        } else if contentChanged || layoutChanged || removalStateChanged || activeMetricsCountChanged {
            // When item count or layout changes, avoid batch updates; do a full, animation-less reload
            uiView.collectionViewLayout.invalidateLayout()
            UIView.performWithoutAnimation {
                uiView.reloadData()
            }
            coordinator.lastItemIds = newIds
            coordinator.lastDashboardType = newDashboardType
            coordinator.lastActiveMetricsCount = newActiveMetricsCount
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
                                isBeingDragged: false,
                                parentView: parentView
                            )
                            metricCell.isRemoved = store.isMetricRemoved(item.label)
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
                            isBeingDragged: false,
                            parentView: parentView
                        )
                        metricCell.isRemoved = store.isMetricRemoved(item.label)
                    }
                }
            }
        }

        coordinator.lastIsEditMode = newIsEditMode
        coordinator.lastSelectedMetricLabel = newSelectedLabel
        coordinator.lastRemovedMetrics = newRemovedMetrics
        
        // Ensure system drag interaction stays disabled (we use interactive movement)
        uiView.dragInteractionEnabled = false
        
        if !newIsEditMode {
            UIView.performWithoutAnimation {
                uiView.visibleCells.forEach { cell in
                    if let metricCell = cell as? MetricCell {
                        metricCell.isWiggling = false
                        metricCell.updateDragState(false)
                        metricCell.setOverlaySuppressed(false)
                        if let item = metricCell.representedItem {
                            metricCell.configure(
                                with: item,
                                dashboardType: store.state.metrics.dashboardType,
                                store: store,
                                isBeingDragged: false,
                                parentView: parentView
                            )
                        }
                    }
                }
            }
        }
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
        // Add bottom padding to accommodate wiggle animation bounce effect (3.0 points + safety margin)
        layout.sectionInset = UIEdgeInsets(top: 20, left: 20, bottom: 8, right: 20)
        return layout
    }
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: LeadingAlignedFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        // Disable system drag interaction; use interactive movement with our own gesture
        collectionView.dragInteractionEnabled = false
        // Allow dragged cell to overlay at edges without being clipped
        collectionView.clipsToBounds = false
        collectionView.layer.masksToBounds = false
        collectionView.hideDragPlatter = true // hide system drag preview platter (slashed circle)
        if #available(iOS 11.0, *) {
            collectionView.reorderingCadence = .immediate
        }
        collectionView.register(MetricCell.self, forCellWithReuseIdentifier: "MetricCell")
        
        // Disable selection to prevent visual feedback
        collectionView.allowsSelection = false
        
        // Disable user scrolling but allow content size calculation
        collectionView.isScrollEnabled = false
        collectionView.showsVerticalScrollIndicator = false
        collectionView.showsHorizontalScrollIndicator = false
        
        // Ensure the collection view can calculate its full content size
        collectionView.contentInsetAdjustmentBehavior = .never
        
        // Suppress implicit layer animations for smooth drag and drop
        collectionView.layer.actions = [
            "position": NSNull(),
            "bounds": NSNull(),
            "transform": NSNull(),
            "opacity": NSNull(),
            "onOrderIn": NSNull(),
            "onOrderOut": NSNull(),
            "sublayers": NSNull(),
            "contents": NSNull(),
            "hidden": NSNull(),
            "cornerRadius": NSNull()
        ]
        
        return collectionView
    }
    
    /// Sets up the collection view with delegates and gesture recognizers
    private func setupCollectionView(_ collectionView: UICollectionView, context: Context) {
        collectionView.delegate = context.coordinator
        collectionView.dataSource = context.coordinator
        collectionView.dragDelegate = context.coordinator
        collectionView.dropDelegate = context.coordinator

        // Add long-press gesture for interactive movement with clamped bounds
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleLongPress(_:)))
        longPress.minimumPressDuration = 0.15
        longPress.cancelsTouchesInView = false
        collectionView.addGestureRecognizer(longPress)

        let tapBlocker = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.consumeTap))
        tapBlocker.cancelsTouchesInView = false
        tapBlocker.delaysTouchesBegan = false
        tapBlocker.delaysTouchesEnded = false
        collectionView.addGestureRecognizer(tapBlocker)
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
        var lastRemovedMetrics: Set<String> = []
        var lastActiveMetricsCount: Int = 0
        var suppressNextReload: Bool = false
        
        /// Returns the number of non-removed (active) metrics that can be reordered
        private var activeMetricsCount: Int {
            let metrics = store.metricsToShow
            return metrics.count - store.state.ui.removedMetrics.count
        }
        
        /// Returns the first index of removed metrics (where dropping should be prevented)
        private var firstRemovedIndex: Int {
            return activeMetricsCount
        }
        
        // MARK: - Properties
        
        var parent: MetricGridUIKitView
        var store: DashboardStore
        private var draggedItemId: String?
        private var isAwaitingDropEnd: Bool = false
        private var lastDroppedMetricId: String?
        
        // MARK: - Drag Boundary Properties
        private var boundaryDetector: GridBoundaryDetector
        private var currentItemHalfSize: CGSize = .zero
        private var currentDraggingIndexPath: IndexPath?
        private var originalLayerActions: [String: CAAction]?
        
        // MARK: - Initialization
        
        init(_ parent: MetricGridUIKitView) {
            self.parent = parent
            self.store = parent.store
            self.boundaryDetector = GridBoundaryDetector()
        }
        
        // MARK: - Drag Boundary Methods
        
        /// Updates drag state based on boundary detection
        private func updateDragBoundaryState(_ isOutside: Bool, for collectionView: UICollectionView) {
            parent.isDragOutsideBounds = isOutside
            
            boundaryDetector.updateDragBoundaryState(
                isOutside,
                for: collectionView,
                draggedItemId: draggedItemId
            ) { [weak self] (draggedId: String?, isOutsideBounds: Bool) in
                self?.updateDraggedCellBoundaryState(isOutsideBounds: isOutsideBounds, in: collectionView)
            }
        }
        
        /// Updates the visual state of the dragged cell based on boundary status
        private func updateDraggedCellBoundaryState(isOutsideBounds: Bool, in collectionView: UICollectionView) {
            guard let draggedId = draggedItemId else { return }
            
            // Find the dragged cell and update its appearance
            for cell in collectionView.visibleCells {
                if let metricCell = cell as? MetricCell,
                   metricCell.representedItem?.id.uuidString == draggedId {
                    
                    // Use the dedicated boundary state method
                    metricCell.updateBoundaryState(isOutsideBounds)
                    break
                }
            }
        }
        
        // MARK: - UICollectionViewDataSource
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            let count = store.metricsToShow.count
            return count
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "MetricCell", for: indexPath) as! MetricCell
            
            // Ensure we're using the current metricsToShow array to prevent stale data during reloads
            guard indexPath.item < store.metricsToShow.count else {
                // Fallback: return cell with placeholder configuration
                return cell
            }
            
            let item = store.metricsToShow[indexPath.item]
            
            // Check if this cell is currently being dragged
            let isBeingDragged = draggedItemId == item.id.uuidString
            
            // Configure cell - the configure method handles synchronous updates internally
            cell.configure(
                with: item,
                dashboardType: store.state.metrics.dashboardType,
                store: store,
                isBeingDragged: isBeingDragged, // Pass drag state to cell
                parentView: parent.parentView,
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
            // Reflect removal status on the cell so UI can render accordingly
            cell.isRemoved = store.isMetricRemoved(item.label)
            // Do not add custom gesture recognizers in edit mode; allow SwiftUI buttons to receive taps.
            // Drag & drop is handled by UICollectionViewDragDelegate without custom recognizers.
            cell.isUserInteractionEnabled = true
            
            // Set up delete callback (EditModeOverlay handles the UI)
            cell.onDeleteTapped = {
                // Find the original index in the metrics array
                if let originalIndex = self.store.metricsManager.state.metrics.firstIndex(where: { $0.id == item.id }) {
                    Task {
                        try? await self.store.metricsManager.toggleMetricVisibility(at: originalIndex)
                        
                        // Sync the UI state with the metrics manager after the change
                        await MainActor.run {
                            self.store.syncRemovalStateFromMetricsManager()
                        }
                    }
                }
            }
            
            return cell
        }
        
        // Removed custom long-press recognizer; drag is handled by system drag interaction
        
        // MARK: - UICollectionViewDelegateFlowLayout
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            // Calculate item size based on device type and dashboard type
            let verticalPadding = store.state.metrics.dashboardType == .dashboard12
                ? MetricCardView.twelveCardVerticalPadding
                : MetricCardView.fourCardVerticalPadding

            // Determine columns using centralized helper
            let columns: Int = store.metricsManager.getMetricGridColumnCount(for: store.effectiveDashboardType)

            // Spacing and insets from layout if available
            let (horizontalInsets, interItemSpacing): (CGFloat, CGFloat) = {
                if let flow = collectionView.collectionViewLayout as? UICollectionViewFlowLayout {
                    return (flow.sectionInset.left + flow.sectionInset.right, flow.minimumInteritemSpacing)
                } else {
                    return (40, .spacingSM) // Fallback to defaults used in createLayout()
                }
            }()

            let totalSpacing = horizontalInsets + CGFloat(max(columns - 1, 0)) * interItemSpacing
            let itemWidth = (collectionView.bounds.width - totalSpacing) / CGFloat(columns)
            let itemHeight = 70 + (verticalPadding * 2) // Base height + padding

            return CGSize(width: itemWidth, height: itemHeight)
        }
        
        // MARK: - UICollectionViewDragDelegate
        
        func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
            // Only allow moving items that are non-removed (active) items
            return store.state.ui.isEditMode && indexPath.item < firstRemovedIndex
        }
        
        func collectionView(_ collectionView: UICollectionView, targetIndexPathForMoveFromItemAt originalIndexPath: IndexPath, toProposedIndexPath proposedIndexPath: IndexPath) -> IndexPath {
            let maxValidIndex = firstRemovedIndex - 1
            
            // Prevent any moves to/from removed item indices
            if originalIndexPath.item >= firstRemovedIndex || proposedIndexPath.item >= firstRemovedIndex {
                return originalIndexPath // Return original position to cancel the move
            }
            
            // Ensure proposed destination is within valid range (0 to maxValidIndex)
            if proposedIndexPath.item < 0 {
                return IndexPath(item: 0, section: proposedIndexPath.section)
            } else if proposedIndexPath.item >= firstRemovedIndex {
                return IndexPath(item: maxValidIndex, section: proposedIndexPath.section)
            }
            
            return proposedIndexPath
        }
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            let item = store.metricsToShow[indexPath.item]
            let isRemoved = store.isMetricRemoved(item.label)
            
            // Only allow dragging of non-removed items (active metrics)
            if isRemoved || indexPath.item >= firstRemovedIndex {
                return [] // Return empty array to prevent drag of removed items
            }
            
            if !store.state.ui.isEditMode { return [] } // Prevent drag if not in edit mode
            
            // Only allow dragging of non-removed items
            
            let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
            let dragItem = UIDragItem(itemProvider: itemProvider)
            
            // Mark this as a metric grid item to prevent cross-grid dragging
            dragItem.localObject = DragItemWrapper(type: DragItemWrapper.ItemType.metric, item: item)
            
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

            return [dragItem]
        }

        // MARK: - Interactive Movement (Data Source update)
        func collectionView(_ collectionView: UICollectionView, moveItemAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) {
            // Restrict moves to active (non-removed) range
            guard sourceIndexPath.item < firstRemovedIndex,
                  destinationIndexPath.item < firstRemovedIndex else {
                return
            }
            store.moveMetric(from: sourceIndexPath.item, to: destinationIndexPath.item)
            HapticFeedbackService.light()
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let parameters = UIDragPreviewParameters()
            parameters.backgroundColor = .clear
            if let cell = collectionView.cellForItem(at: indexPath) {
                parameters.visiblePath = UIBezierPath(roundedRect: cell.bounds, cornerRadius: .radiusSM)
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
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: .radiusSM)
                let s = DashboardConstants.UI.dragPreviewScale
                let target = UIDragPreviewTarget(
                    container: collectionView,
                    center: metricCell.center,
                    transform: CGAffineTransform(scaleX: s, y: s)
                )
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
            }
            
            // Fallback: find cell by matching the dragged item
            let metricItem: MetricItem?
            if let wrapper = item.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.metric {
                metricItem = wrapper.item as? MetricItem
            } else {
                metricItem = item.localObject as? MetricItem
            }
            
            if let metricItem = metricItem,
               let metricCell = collectionView.visibleCells.first(where: {
                guard let cellItem = ($0 as? MetricCell)?.representedItem else { return false }
                return cellItem.id.uuidString == metricItem.id.uuidString
            }) as? MetricCell {
                let previewView = metricCell.snapshotForPreview()
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: .radiusSM)
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
            
            // Initialize boundary detection
            boundaryDetector.updateGridBounds(for: collectionView)
            parent.isDragOutsideBounds = false
            
            // Set drag operation flag for smooth animations
            if let custom = collectionView as? CustomCollectionView {
                custom.isInDragOperation = true
            }
            
            // ENABLE smooth animations during drag for beautiful cell movement
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.setAnimationDuration(0.3)
            CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: .easeInEaseOut))
            CATransaction.commit()
            
            // Immediately hide EditModeOverlay on the dragged cell
            if let draggedItem = session.items.first {
                let metricItem: MetricItem?
                if let wrapper = draggedItem.localObject as? DragItemWrapper,
                   wrapper.type == DragItemWrapper.ItemType.metric {
                    metricItem = wrapper.item as? MetricItem
                } else {
                    metricItem = draggedItem.localObject as? MetricItem
                }
                
                if let metricItem = metricItem {
                    // Find and hide EditModeOverlay on the dragged metric cell
                    for cell in collectionView.visibleCells {
                        if let metricCell = cell as? MetricCell,
                           metricCell.representedItem?.id == metricItem.id {
                            metricCell.updateDragState(true) // Use the new method for more reliable state management
                            break
                        }
                    }
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            parent.isDragging = false
            draggedItemId = nil
            
            // Reset boundary detection state
            parent.isDragOutsideBounds = false
            boundaryDetector.resetBoundaryState()
            
            // Clear drag operation flag
            if let custom = collectionView as? CustomCollectionView {
                custom.isInDragOperation = false
            }
            
            // Re-enable animations after drag ends
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.commit()
            
            // Clear the store's drag state
            store.endDragging()
            
            // Do not restore overlays here if we're awaiting the drop session end
            if store.state.ui.isEditMode && !isAwaitingDropEnd {
                for cell in collectionView.visibleCells {
                    if let metricCell = cell as? MetricCell {
                        metricCell.updateDragState(false)
                        // Clear any shadow effects and boundary visual feedback
                        metricCell.clearAllShadowEffects()
                        metricCell.updateBoundaryState(false)
                    }
                }
            }
        }
        
        /// Monitors drag session location to detect boundary violations
        func collectionView(_ collectionView: UICollectionView, dragSessionDidMove session: UIDragSession) {
            // Get the current drag location in the collection view
            let location = session.location(in: collectionView)
            
            // Check if drag is within precise grid boundaries
            let isWithinBounds = boundaryDetector.isDragLocationWithinBounds(location, in: collectionView)
            
            // Update boundary state if it changed
            updateDragBoundaryState(!isWithinBounds, for: collectionView)
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
                
                // Reset boundary detection state
                self.parent.isDragOutsideBounds = false
                self.boundaryDetector.resetBoundaryState()
                
                // Clear drag operation flag
                if let custom = collectionView as? CustomCollectionView {
                    custom.isInDragOperation = false
                }
                
                // Clear the store's drag state
                self.store.endDragging()
                
                // Immediately restore EditModeOverlay visibility on all cells if in edit mode
                if self.store.state.ui.isEditMode {
                    // Update all visible cells to restore EditModeOverlay visibility
                    for cell in collectionView.visibleCells {
                        if let metricCell = cell as? MetricCell {
                            metricCell.updateDragState(false) // Use the new method for more reliable state management
                            // Clear any shadow effects and boundary visual feedback
                            metricCell.clearAllShadowEffects()
                            metricCell.updateBoundaryState(false)
                        }
                    }
                }
            }
        }
        
        // MARK: - UICollectionViewDropDelegate
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            guard store.state.ui.isEditMode else {
                // Use .cancel to avoid showing the slashed-circle icon
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Check if drop is within precise grid boundaries
            let dropLocation = session.location(in: collectionView)
            let isWithinBounds = boundaryDetector.isDragLocationWithinBounds(dropLocation, in: collectionView)
            
            if !isWithinBounds {
                // Update boundary state for visual feedback
                updateDragBoundaryState(true, for: collectionView)
                return UICollectionViewDropProposal(operation: .cancel)
            } else {
                // Restore normal state when back within bounds
                updateDragBoundaryState(false, for: collectionView)
            }
            
            // Only accept drops from the metric grid
            guard let items = session.items as? [UIDragItem] else {
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Check if all items are from the metric grid
            for dragItem in items {
                if let wrapper = dragItem.localObject as? DragItemWrapper {
                    if wrapper.type != DragItemWrapper.ItemType.metric {
                        // Use .cancel to suppress forbidden icon for cross-grid drags
                        return UICollectionViewDropProposal(operation: .cancel)
                    }
                } else {
                    // Legacy support for direct MetricItem objects
                    if !(dragItem.localObject is MetricItem) {
                        return UICollectionViewDropProposal(operation: .cancel)
                    }
                }
            }

            // Completely prevent any drops on removed items
            // Use .cancel instead of .forbidden to avoid slashed circle visual effect
            if let destinationIndexPath = destinationIndexPath, destinationIndexPath.item >= firstRemovedIndex {
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Also check if destination would cause issues - only allow drops on active metrics
            guard let destinationIndexPath = destinationIndexPath, 
                  destinationIndexPath.item >= 0 && destinationIndexPath.item < firstRemovedIndex else {
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnter session: UIDropSession) {
            // Immediately clear any existing drag state when drop session enters
            store.endDragging()
            draggedItemId = nil
            parent.isDragging = false
            
            // Keep smooth animations enabled during drop session for beautiful cell movement
            UIView.setAnimationsEnabled(true)
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.setAnimationDuration(0.3)
            CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: .easeInEaseOut))
            CATransaction.commit()
        }
        
        func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
            guard store.state.ui.isEditMode else { return }
            guard let destinationIndexPath = coordinator.destinationIndexPath,
                  let item = coordinator.items.first,
                  let sourceIndexPath = item.sourceIndexPath else {
                return 
            }

            // Validate that this is a valid metric item drop
            let metricItem: MetricItem?
            if let wrapper = item.dragItem.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.metric {
                metricItem = wrapper.item as? MetricItem
            } else if let directItem = item.dragItem.localObject as? MetricItem {
                metricItem = directItem
            } else {
                return // Invalid drop item
            }
            guard metricItem != nil else { return }

            // Prevent dropping on removed items - only allow drops on active metrics
            if destinationIndexPath.item >= firstRemovedIndex {
                collectionView.reloadData() // Reset to original state
                return
            }

            // Also prevent moving FROM removed items (should already be blocked, but extra safety)
            if sourceIndexPath.item >= firstRemovedIndex {
                collectionView.reloadData() // Reset to original state
                return
            }

            if let custom = collectionView as? CustomCollectionView { 
                custom.suspendIntrinsicInvalidation = true 
            }
            
            self.suppressNextReload = true
            
            // Use smooth animations for the actual reordering
            UIView.animate(withDuration: 0.35,
                           delay: 0,
                           usingSpringWithDamping: 0.85,
                           initialSpringVelocity: 0.5,
                           options: [.allowUserInteraction, .beginFromCurrentState]) {
                collectionView.performBatchUpdates({
                    self.store.moveMetric(from: sourceIndexPath.item, to: destinationIndexPath.item)
                    collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                }, completion: { _ in
                    collectionView.collectionViewLayout.invalidateLayout()
                    collectionView.layoutIfNeeded()
                })
            } completion: { _ in
                if let custom = collectionView as? CustomCollectionView {
                    custom.suspendIntrinsicInvalidation = false
                    custom.invalidateIntrinsicContentSize()
                }
                let visibleIndexPaths = collectionView.indexPathsForVisibleItems
                for indexPath in visibleIndexPaths {
                    guard indexPath.item < self.store.metricsToShow.count,
                          let cell = collectionView.cellForItem(at: indexPath) as? MetricCell else { continue }
                    let itemForCell = self.store.metricsToShow[indexPath.item]
                    cell.configure(
                        with: itemForCell,
                        dashboardType: self.store.state.metrics.dashboardType,
                        store: self.store,
                        isBeingDragged: false,
                        parentView: self.parent.parentView,
                        onMetricLongPress: self.parent.onMetricLongPress,
                        onSelectMetric: { label in
                            if label.isEmpty { self.store.state.ui.selectedMetricLabel = nil }
                            else { self.store.state.ui.selectedMetricLabel = label }
                            self.store.objectWillChange.send()
                        }
                    )
                    cell.isRemoved = self.store.isMetricRemoved(itemForCell.label)
                    cell.clearAllShadowEffects()
                }
            }

            coordinator.drop(item.dragItem, toItemAt: destinationIndexPath)
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()

            if let wrapper = item.dragItem.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.metric,
               let droppedItem = wrapper.item as? MetricItem {
                lastDroppedMetricId = droppedItem.id.uuidString
            } else if let directItem = item.dragItem.localObject as? MetricItem {
                lastDroppedMetricId = directItem.id.uuidString
            }
            isAwaitingDropEnd = true

            if let id = lastDroppedMetricId,
               let destCell = collectionView.visibleCells.first(where: { cell in
                   guard let mc = cell as? MetricCell, let rep = mc.representedItem else { return false }
                   return rep.id.uuidString == id
               }) as? MetricCell {
                destCell.setOverlaySuppressed(true)
                destCell.setNeedsLayout()
                destCell.layoutIfNeeded()
            }

            self.lastItemIds = self.store.metricsToShow.map { $0.id }
            self.lastDashboardType = self.store.state.metrics.dashboardType
        }

        func collectionView(_ collectionView: UICollectionView,
                            dropPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let params = UIDragPreviewParameters()
            params.backgroundColor = .clear
            if let cell = collectionView.cellForItem(at: indexPath) {
                params.visiblePath = UIBezierPath(roundedRect: cell.bounds, cornerRadius: .radiusSM)
            }
            return params
        }

        func collectionView(_ collectionView: UICollectionView,
                            dropPreviewForDropping item: UIDragItem,
                            withDefault defaultPreview: UITargetedDragPreview) -> UITargetedDragPreview? {
            let clearView = UIView(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
            clearView.backgroundColor = .clear
            let params = UIDragPreviewParameters()
            params.backgroundColor = .clear
            params.visiblePath = UIBezierPath(rect: CGRect(x: 0, y: 0, width: 1, height: 1))
            return UITargetedDragPreview(view: clearView, parameters: params, target: defaultPreview.target)
        }

        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnd session: UIDropSession) {
            // Immediately clear drag state when drop session ends
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            
            // Reset boundary detection state
            parent.isDragOutsideBounds = false
            boundaryDetector.resetBoundaryState()
            
            // Clear drag operation flag
            if let custom = collectionView as? CustomCollectionView {
                custom.isInDragOperation = false
            }
            
            // Force instant layout update with scoped animation disabling
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            
            // Only disable animations on this collection view, not globally
            let originalActions = collectionView.layer.actions
            collectionView.layer.actions = ["position": NSNull(), "bounds": NSNull(), "transform": NSNull()]
            
            collectionView.layoutIfNeeded()
            // Force all visible cells to update their appearance instantly
            collectionView.visibleCells.forEach { cell in
                cell.layer.removeAllAnimations()
                cell.contentView.layer.removeAllAnimations()
                // Ensure no transform animations
                cell.transform = .identity
                cell.contentView.transform = .identity
                
                // Restore overlay visibility if in edit mode
                if let metricCell = cell as? MetricCell {
                    metricCell.setOverlaySuppressed(false)
                    // Force clear all shadow effects to prevent shadow artifacts
                    metricCell.clearAllShadowEffects()
                }
            }
            
            // Restore collection view's layer actions
            collectionView.layer.actions = originalActions
            CATransaction.commit()

            if store.state.ui.isEditMode {
                let restore = {
                    if let targetId = self.lastDroppedMetricId,
                       let targetCell = collectionView.visibleCells.first(where: { cell in
                           guard let metricCell = cell as? MetricCell, let rep = metricCell.representedItem else { return false }
                           return rep.id.uuidString == targetId
                       }) as? MetricCell {
                        targetCell.setOverlaySuppressed(false)
                        targetCell.setNeedsLayout()
                        targetCell.layoutIfNeeded()
                    } else {
                        // Fallback: restore all if we cannot identify the dropped cell
                        for cell in collectionView.visibleCells {
                            if let metricCell = cell as? MetricCell {
                                metricCell.setOverlaySuppressed(false)
                                metricCell.setNeedsLayout()
                                metricCell.layoutIfNeeded()
                            }
                        }
                    }
                    self.isAwaitingDropEnd = false
                    self.lastDroppedMetricId = nil
                }
                restore()
            } else {
                isAwaitingDropEnd = false
                lastDroppedMetricId = nil
            }
        }

        // MARK: - Gesture Sink
        @objc func consumeTap(_ sender: UITapGestureRecognizer) {
            // No-op; presence of this recognizer ensures taps in the grid are handled here
            // and not propagated to parent background .onTapGesture that cancels edit mode.
        }

        // MARK: - Interactive Movement with Clamped Bounds
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard let collectionView = gesture.view as? UICollectionView else { return }

            let location = gesture.location(in: collectionView)

            switch gesture.state {
            case .began:
                // Only allow when edit mode is ON, otherwise let regular taps work
                guard store.state.ui.isEditMode else { return }
                guard let indexPath = collectionView.indexPathForItem(at: location) else { return }
                // Only allow interactive movement for active (non-removed) metrics
                guard indexPath.item < firstRemovedIndex else { return }

                currentDraggingIndexPath = indexPath
                if let cell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                    let size = cell.bounds.size
                    currentItemHalfSize = CGSize(width: size.width * 0.5, height: size.height * 0.5)
                    cell.updateDragState(true)
                    cell.layer.zPosition = 1000
                }

                // Temporarily allow animations by clearing suppressed actions
                if originalLayerActions == nil {
                    originalLayerActions = collectionView.layer.actions as? [String: CAAction]
                    collectionView.layer.actions = [:]
                }

                if let custom = collectionView as? CustomCollectionView {
                    custom.isInDragOperation = true
                }

                // Begin interactive movement
                _ = collectionView.beginInteractiveMovementForItem(at: indexPath)

            case .changed:
                // Clamp target within collectionView bounds accounting for item size
                let clamped = clampedTargetPosition(location, in: collectionView)
                collectionView.updateInteractiveMovementTargetPosition(clamped)

            case .ended:
                // Slow settling animation similar to iOS home screen
                CATransaction.begin()
                CATransaction.setAnimationDuration(0.28)
                CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: .easeInEaseOut))
                collectionView.endInteractiveMovement()
                CATransaction.commit()

                UIView.animate(withDuration: 0.35,
                               delay: 0,
                               usingSpringWithDamping: 0.85,
                               initialSpringVelocity: 0.5,
                               options: [.allowUserInteraction, .beginFromCurrentState]) {
                    collectionView.layoutIfNeeded()
                } completion: { _ in
                    // Restore layer action suppression after settle
                    if let original = self.originalLayerActions {
                        collectionView.layer.actions = original
                    }
                    self.originalLayerActions = nil
                    if let custom = collectionView as? CustomCollectionView {
                        custom.isInDragOperation = false
                    }
                    // Robust cleanup: ensure ALL visible cells clear drag/shadow state post-drop
                    self.cleanupVisibleCells(collectionView)
                }
                if let indexPath = currentDraggingIndexPath,
                   let cell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                    cell.updateDragState(false)
                    cell.layer.zPosition = 0
                }
                currentDraggingIndexPath = nil
                currentItemHalfSize = .zero

            default:
                // Cancel when finger leaves or gesture fails
                collectionView.cancelInteractiveMovement()
                if let indexPath = currentDraggingIndexPath,
                   let cell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                    cell.updateDragState(false)
                    cell.layer.zPosition = 0
                }
                currentDraggingIndexPath = nil
                currentItemHalfSize = .zero
                // Restore layer actions on cancel
                if let original = originalLayerActions {
                    collectionView.layer.actions = original
                }
                originalLayerActions = nil
                if let custom = collectionView as? CustomCollectionView {
                    custom.isInDragOperation = false
                }
                // Robust cleanup on cancel as well
                self.cleanupVisibleCells(collectionView)
            }
        }

        private func clampedTargetPosition(_ position: CGPoint, in collectionView: UICollectionView) -> CGPoint {
            // Clamp within the actual content bounds height and the view width
            boundaryDetector.updateGridBounds(for: collectionView)

            // Convert gridBounds (in superview coords) to collectionView coords for comparison
            if let superview = collectionView.superview {
                let gridBounds = boundaryDetector.getGridBounds()
                let gridInCollection = superview.convert(gridBounds, to: collectionView)
                let insetRect = gridInCollection.insetBy(dx: currentItemHalfSize.width, dy: currentItemHalfSize.height)

                let x = max(insetRect.minX, min(position.x, insetRect.maxX))
                let y = max(insetRect.minY, min(position.y, insetRect.maxY))
                return CGPoint(x: x, y: y)
            }

            // Fallback to collection bounds if conversion fails
            let bounds = collectionView.bounds.insetBy(dx: currentItemHalfSize.width, dy: currentItemHalfSize.height)
            let x = max(bounds.minX, min(position.x, bounds.maxX))
            let y = max(bounds.minY, min(position.y, bounds.maxY))
            return CGPoint(x: x, y: y)
        }

        // MARK: - Cleanup Helpers
        private func cleanupVisibleCells(_ collectionView: UICollectionView) {
            for cell in collectionView.visibleCells {
                if let metricCell = cell as? MetricCell {
                    metricCell.updateDragState(false)
                    metricCell.clearAllShadowEffects()
                    metricCell.updateBoundaryState(false)
                }
            }
        }
    }
} 
