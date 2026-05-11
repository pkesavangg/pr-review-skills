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
        let newDashboardType = store.metrics.dashboardType
        let newIsEditMode = store.ui.isEditMode
        let newSelectedLabel = store.ui.selectedMetricLabel
        let newRemovedMetrics = store.ui.removedMetrics
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
        if store.ui.isResettingDashboard {
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
                // Update long press gesture duration based on edit mode.
                // Disable and re-enable the recognizer to cancel any in-progress gesture
                // so the new minimumPressDuration is applied consistently.
                if let longPress = coordinator.longPressGestureRecognizer {
                    let newDuration: TimeInterval = newIsEditMode ? 0.15 : 0.5
                    if longPress.minimumPressDuration != newDuration {
                        longPress.isEnabled = false
                        longPress.minimumPressDuration = newDuration
                        longPress.isEnabled = true
                    }
                }
                
                uiView.visibleCells.forEach { cell in
                    if let metricCell = cell as? MetricCell {
                        metricCell.isWiggling = newIsEditMode
                        // Reconfigure SwiftUI content to reflect new edit state and hide overlays
                        if let item = metricCell.representedItem {
                            metricCell.configure(
                                with: item,
                                dashboardType: store.metrics.dashboardType,
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
                            dashboardType: store.metrics.dashboardType,
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
                                dashboardType: store.metrics.dashboardType,
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
        GridUIKitInteractionManager.applyCommonCollectionViewConfiguration(collectionView)
        collectionView.register(MetricCell.self, forCellWithReuseIdentifier: "MetricCell")
        
        return collectionView
    }
    
    /// Sets up the collection view with delegates and gesture recognizers
    private func setupCollectionView(_ collectionView: UICollectionView, context: Context) {
        collectionView.delegate = context.coordinator
        collectionView.dataSource = context.coordinator
        // System drag/drop is disabled for this grid; we use interactive movement instead.
        collectionView.dragDelegate = nil
        collectionView.dropDelegate = nil

        // Add long-press gesture for interactive movement with clamped bounds
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleLongPress(_:)))
        // Use longer duration when not in edit mode (to enter edit mode), shorter when in edit mode (for dragging)
        longPress.minimumPressDuration = context.coordinator.store.ui.isEditMode ? 0.15 : 0.5
        longPress.cancelsTouchesInView = false
        longPress.delaysTouchesBegan = false
        context.coordinator.longPressGestureRecognizer = longPress
        collectionView.addGestureRecognizer(longPress)

        GridUIKitInteractionManager.addTapSink(to: collectionView, target: context.coordinator, action: #selector(Coordinator.consumeTap))
    }
}

// MARK: - Coordinator

extension MetricGridUIKitView {
    /// Coordinator class that handles all UICollectionView delegate methods
    /// and manages the interaction between UIKit and SwiftUI
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout {
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
            return metrics.count - store.ui.removedMetrics.count
        }
        
        /// Returns the first index of removed metrics (where dropping should be prevented)
        private var firstRemovedIndex: Int {
            return activeMetricsCount
        }
        
        // MARK: - Properties
        
        var parent: MetricGridUIKitView
        var store: DashboardStore
        var longPressGestureRecognizer: UILongPressGestureRecognizer?
        
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
            
            // Configure cell - the configure method handles synchronous updates internally
            cell.configure(
                with: item,
                dashboardType: store.metrics.dashboardType,
                store: store,
                isBeingDragged: false,
                parentView: parent.parentView,
                onMetricLongPress: parent.onMetricLongPress,
                onSelectMetric: { label in
                    if label.isEmpty {
                        self.store.ui.selectedMetricLabel = nil
                    } else {
                        self.store.ui.selectedMetricLabel = label
                    }
                    // Publish selection change so the grid reconfigures visible cells immediately
                    self.store.objectWillChange.send()
                }
            )
            cell.rowIndex = indexPath.row
            cell.isWiggling = store.ui.isEditMode
            // Reflect removal status on the cell so UI can render accordingly
            cell.isRemoved = store.isMetricRemoved(item.label)
            // Do not add custom gesture recognizers in edit mode; allow SwiftUI buttons to receive taps.
            // Reorder is handled via UICollectionView interactive movement (long-press + beginInteractiveMovementForItem).
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
            let verticalPadding = store.metrics.dashboardType == .dashboard12
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
        
        // MARK: - System Drag/Drop (Disabled)
        
        func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
            // Only allow moving items that are non-removed (active) items
            return store.ui.isEditMode && indexPath.item < firstRemovedIndex
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

        // MARK: - Gesture Sink
        @objc func consumeTap(_ sender: UITapGestureRecognizer) {
            guard store.ui.isEditMode,
                  let collectionView = sender.view as? UICollectionView else {
                return
            }
            let location = sender.location(in: collectionView)
            for cell in collectionView.visibleCells {
                guard let metricCell = cell as? MetricCell else { continue }
                let pointInCell = collectionView.convert(location, to: metricCell)
                if metricCell.bounds.contains(pointInCell) {
                    continue
                }
                if metricCell.handleOverlayTapIfNeeded(at: pointInCell) {
                    return
                }
            }
        }

        // MARK: - Interactive Movement with Clamped Bounds
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard let collectionView = gesture.view as? UICollectionView else { return }

            let location = gesture.location(in: collectionView)

            switch gesture.state {
            case .began:
                // Determine which item was long-pressed, if any
                guard let indexPath = collectionView.indexPathForItem(at: location) else { return }
                
                // If not in edit mode, enter edit mode on long press of a metric cell,
                // then immediately proceed to start the drag for the same cell.
                if !store.ui.isEditMode {
                    store.toggleEditMode()
                }
                
                // Only allow interactive movement when edit mode is ON
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
