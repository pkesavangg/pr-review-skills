//
//  GoalStreakGridUIKitView.swift
//  meApp
//
//  Created by Lakshmi Priya on 07/08/25.
//

import SwiftUI
import UIKit

/// A SwiftUI wrapper around UICollectionView that displays goal card and streak items with drag-and-drop functionality
/// Expands to fit content, does not have its own scroll view, and is placed inside the dashboard's ScrollView
struct GoalStreakGridUIKitView: UIViewRepresentable {
    @ObservedObject var store: DashboardStore
    
    func makeUIView(context: Context) -> UICollectionView {
        let layout = createLayout()
        let collectionView = createCollectionView(with: layout)
        setupCollectionView(collectionView, context: context)
        return collectionView
    }
    
    func updateUIView(_ collectionView: UICollectionView, context: Context) {
        let coordinator = context.coordinator
        coordinator.store = store

        // Rebuild model and compare to previous for minimal updates
        let newModel = buildGridModelFromStoreState()
        let newIsEditMode = store.state.ui.isEditMode
        let newRemovedStreaks = store.state.ui.removedStreaks

        let oldIds = coordinator.gridModel.mileStones.map { widget -> String in
            switch widget {
            case .goalCard: return "goalCard"
            case .streak(let item): return item.id.uuidString
            }
        }
        let newIds = newModel.mileStones.map { widget -> String in
            switch widget {
            case .goalCard: return "goalCard"
            case .streak(let item): return item.id.uuidString
            }
        }

        let contentChanged = oldIds != newIds
        let removalStateChanged = newRemovedStreaks != coordinator.lastRemovedStreaks

        if contentChanged || removalStateChanged {
            coordinator.gridModel = newModel
            collectionView.collectionViewLayout.invalidateLayout()
            UIView.performWithoutAnimation {
                collectionView.reloadData()
            }
        } else {
            // Only wiggle state might have changed; update visible cells without reload
            if newIsEditMode != coordinator.lastIsEditMode {
                collectionView.visibleCells.forEach { cell in
                    if let goal = cell as? GoalCardCell {
                        goal.isWiggling = newIsEditMode
                        goal.configure(with: coordinator.store)
                    } else if let streak = cell as? StreakCardCell {
                        streak.isWiggling = newIsEditMode
                        if let item = streak.representedItem {
                            streak.configure(with: item, store: coordinator.store)
                        }
                    }
                }
            }
        }

        coordinator.lastIsEditMode = newIsEditMode
        coordinator.lastRemovedStreaks = newRemovedStreaks
        
        // Update drag interaction enabled state
        collectionView.dragInteractionEnabled = newIsEditMode
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(store: store, gridModel: buildGridModelFromStoreState())
    }
    
    // MARK: - Private Methods
    
    /// Creates the collection view layout with proper spacing and insets
    private func createLayout() -> UICollectionViewFlowLayout {
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 16         // gap between columns
        layout.minimumLineSpacing = 32              // gap between rows
        layout.sectionInset = UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16) // outer margin
        return layout
    }
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: UICollectionViewFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.hideDragPlatter = false // show system drag preview platter
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakCardCell.self, forCellWithReuseIdentifier: "StreakCardCell")
        
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
        collectionView.dataSource = context.coordinator
        collectionView.delegate = context.coordinator
        collectionView.dragDelegate = context.coordinator
        collectionView.dropDelegate = context.coordinator
        collectionView.dragInteractionEnabled = store.state.ui.isEditMode
    }
    
    /// Builds the grid model using the saved order from DashboardStore UI state
    private func buildGridModelFromStoreState() -> MileStoneGridModel {
        var widgets: [MileStoneType] = []
        let allStreaks = store.streakItemsToShow
        let goalCardPos = store.state.ui.goalCardPosition
        let streakOrder = store.state.ui.streakGridOrder
        let isGoalCardRemoved = store.state.ui.isGoalCardRemoved
        let isEditMode = store.state.ui.isEditMode

        var orderedStreaks: [MetricItem]
        if streakOrder.isEmpty {
            orderedStreaks = allStreaks
        } else {
            orderedStreaks = streakOrder.compactMap { id in
                allStreaks.first(where: { $0.id.uuidString == id })
            }
            let missing = allStreaks.filter { s in !streakOrder.contains(s.id.uuidString) }
            orderedStreaks.append(contentsOf: missing)
        }

        // Partition streaks into non-removed and removed using new removal state
        let nonRemovedStreaks = orderedStreaks.filter { streak in
            return !store.isStreakRemoved(streak.label)
        }
        let removedStreaks = orderedStreaks.filter { streak in
            return store.isStreakRemoved(streak.label)
        }

        // In edit mode, show all items (including removed ones)
        if isEditMode {
            // Insert goal card at correct position if not removed
            if !isGoalCardRemoved {
                for i in 0...nonRemovedStreaks.count {
                    if i == goalCardPos {
                        widgets.append(.goalCard)
                    }
                    if i < nonRemovedStreaks.count {
                        widgets.append(.streak(nonRemovedStreaks[i]))
                    }
                }
            } else {
                widgets = nonRemovedStreaks.map { .streak($0) }
            }

            // Add removed streaks at the end
            for streak in removedStreaks {
                widgets.append(.streak(streak))
            }
            // Add removed goal card at the end
            if isGoalCardRemoved {
                widgets.append(.goalCard)
            }
        } else {
            // Not in edit mode - only show non-removed items
            // Insert goal card at correct position if not removed
            if !isGoalCardRemoved {
                for i in 0...nonRemovedStreaks.count {
                    if i == goalCardPos {
                        widgets.append(.goalCard)
                    }
                    if i < nonRemovedStreaks.count {
                        widgets.append(.streak(nonRemovedStreaks[i]))
                    }
                }
            } else {
                widgets = nonRemovedStreaks.map { .streak($0) }
            }
            // Don't add removed items when not in edit mode
        }
        
        return MileStoneGridModel(mileStones: widgets)
    }
    
    // MARK: - Coordinator
    
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        var lastIsEditMode: Bool = false
        var lastRemovedStreaks: Set<String> = []
        var store: DashboardStore
        var gridModel: MileStoneGridModel
        
        init(store: DashboardStore, gridModel: MileStoneGridModel) {
            self.store = store
            self.gridModel = gridModel
        }
        
        // MARK: - UICollectionViewDataSource
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            gridModel.mileStones.count
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let widget = gridModel.mileStones[indexPath.item]
            switch widget {
            case .goalCard:
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GoalCardCell", for: indexPath) as! GoalCardCell
                cell.configure(with: store)
                cell.isWiggling = store.state.ui.isEditMode
                cell.rowIndex = indexPath.item
                return cell
            case .streak(let item):
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "StreakCardCell", for: indexPath) as! StreakCardCell
                cell.configure(with: item, store: store)
                cell.isWiggling = store.state.ui.isEditMode
                cell.rowIndex = indexPath.item
                return cell
            }
        }
        
        // MARK: - UICollectionViewDelegateFlowLayout
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            let widget = gridModel.mileStones[indexPath.item]
            let contentWidth = collectionView.bounds.width - 32 // 16px * 2 for left and right insets
            let interItemSpacing: CGFloat = 16

            switch widget {
            case .goalCard:
                return CGSize(width: contentWidth, height: 120) // Large widget spans full width
            case .streak:

                // Device-aware columns: iPad=4, iPhone=2
                let columns: CGFloat = DevicePlatform.isTablet ? 4 : 2
                let itemWidth = (contentWidth - interItemSpacing * (columns - 1)) / columns
                return CGSize(width: itemWidth, height: 70) // Small widget
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, insetForSectionAt section: Int) -> UIEdgeInsets {
            // The outer margin for the whole grid
            return UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)
        }
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumLineSpacingForSectionAt section: Int) -> CGFloat {
            // Vertical gap between rows, including streak rows and goal card rows
            return 32
        }
        
        // MARK: - Drag & Drop
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            guard store.state.ui.isEditMode else { return [] }
            let widget = gridModel.mileStones[indexPath.item]
            let itemProvider: NSItemProvider
            switch widget {
            case .goalCard:
                itemProvider = NSItemProvider(object: "goalCard" as NSString)
            case .streak(let item):
                itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
            }
            let dragItem = UIDragItem(itemProvider: itemProvider)
            
            // Mark this as a goal/streak grid item to prevent cross-grid dragging
            dragItem.localObject = DragItemWrapper(type: DragItemWrapper.ItemType.goalStreak, item: widget)
            
            return [dragItem]
        }
        
        func collectionView(_ collectionView: UICollectionView, dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let params = UIDragPreviewParameters()
            params.backgroundColor = .clear
            params.visiblePath = UIBezierPath(roundedRect: collectionView.cellForItem(at: indexPath)?.bounds ?? .zero, cornerRadius: 10)
            return params
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            guard store.state.ui.isEditMode else {
                return UICollectionViewDropProposal(operation: .forbidden)
            }
            
            // Only accept drops from the same grid (goal/streak items)
            guard let items = session.items as? [UIDragItem] else {
                return UICollectionViewDropProposal(operation: .forbidden)
            }
            
            // Check if all items are from the goal/streak grid
            for dragItem in items {
                if let wrapper = dragItem.localObject as? DragItemWrapper {
                    if wrapper.type != DragItemWrapper.ItemType.goalStreak {
                        return UICollectionViewDropProposal(operation: .forbidden)
                    }
                } else {
                    // Legacy support for direct widget objects
                    if !(dragItem.localObject is MileStoneType) {
                        return UICollectionViewDropProposal(operation: .forbidden)
                    }
                }
            }

            if let destinationIndexPath = destinationIndexPath {
                // Show drop target indicator
                showDropTargetIndicator(at: destinationIndexPath, in: collectionView)
            }
            
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
        }
        
        /// iOS Home Screen Logic: Show drop target indicator to prevent flickering
        private func showDropTargetIndicator(at indexPath: IndexPath, in collectionView: UICollectionView) {
            // Disable animations for immediate feedback
            CATransaction.begin()
            CATransaction.setDisableActions(true)            
            CATransaction.commit()
        }
        
        func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
            guard store.state.ui.isEditMode else { return }
            guard let destinationIndexPath = coordinator.destinationIndexPath,
                  let item = coordinator.items.first,
                  let sourceIndexPath = item.sourceIndexPath else { return }

            // Extract the widget from the DragItemWrapper
            let widget: MileStoneType
            if let wrapper = item.dragItem.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.goalStreak {
                widget = wrapper.item as! MileStoneType
            } else if let directWidget = item.dragItem.localObject as? MileStoneType {
                widget = directWidget
            } else {
                return // Invalid drop item
            }

            // iOS Home Screen Logic: Immediately rearrange items to prevent flickering
            // Widgets and apps are pushed to maintain proper spacing
            let sourceIndex = sourceIndexPath.item
            let destinationIndex = destinationIndexPath.item
            
            // Calculate the actual insertion index considering widget/app spacing
            let actualInsertionIndex = calculateActualInsertionIndex(
                from: sourceIndex,
                to: destinationIndex,
                for: widget
            )
            
            // Perform the reorder with immediate visual feedback
            performImmediateReorder(
                collectionView: collectionView,
                from: sourceIndex,
                to: actualInsertionIndex,
                widget: widget
            )

            // Save the new order to DashboardStore UI state
            persistGridOrderToStore()
        }
        
        /// iOS Home Screen Logic: Calculate actual insertion index considering widget/app spacing
        private func calculateActualInsertionIndex(from sourceIndex: Int, to destinationIndex: Int, for widget: MileStoneType) -> Int {
            let currentModel = gridModel.mileStones
            
            // If moving to the same position, no change needed
            if sourceIndex == destinationIndex {
                return sourceIndex
            }
            
            // Determine if this is a widget (goal card) or app (streak item)
            let isWidget = widget == .goalCard
            
            // iOS Home Screen Logic: Widgets and apps have different spacing rules
            if isWidget {
                // Widget (goal card) logic: Full-width items that push others
                // Widgets can be placed at any row boundary
                return destinationIndex
            } else {
                // App (streak item) logic: Grid items that maintain spacing
                // Apps are placed in grid positions, maintaining column alignment
                let columns: Int = DevicePlatform.isTablet ? 4 : 2
                
                // Calculate the target row and column
                let targetRow = destinationIndex / columns
                let targetColumn = destinationIndex % columns
                
                // Ensure the target position is valid for grid layout
                let validTargetIndex = targetRow * columns + targetColumn
                
                // If the target is occupied by a widget, find the next available position
                if validTargetIndex < currentModel.count {
                    let targetWidget = currentModel[validTargetIndex]
                    if targetWidget == .goalCard {
                        // Widget is here, find next available position
                        return findNextAvailablePosition(from: validTargetIndex, in: currentModel, columns: columns)
                    }
                }
                
                return validTargetIndex
            }
        }
        
        /// iOS Home Screen Logic: Find next available position when target is occupied
        private func findNextAvailablePosition(from startIndex: Int, in model: [MileStoneType], columns: Int) -> Int {
            let maxIndex = model.count - 1
            
            // Look forward first
            for i in startIndex...maxIndex {
                if i < model.count && model[i] != .goalCard {
                    return i
                }
            }
            
            // Look backward if no forward position found
            for i in (0..<startIndex).reversed() {
                if i < model.count && model[i] != .goalCard {
                    return i
                }
            }
            
            // Fallback to start index
            return startIndex
        }
        
        /// iOS Home Screen Logic: Perform immediate reorder with visual feedback
        private func performImmediateReorder(collectionView: UICollectionView, from sourceIndex: Int, to destinationIndex: Int, widget: MileStoneType) {
            // iOS Home Screen Logic: Disable all animations for instant positioning
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            
            UIView.performWithoutAnimation {
                // Update the model first to prevent state inconsistencies
                gridModel.moveWidget(from: sourceIndex, to: destinationIndex)
                
                // Perform the collection view update with completion handler
                collectionView.performBatchUpdates({
                    collectionView.moveItem(at: IndexPath(item: sourceIndex, section: 0),
                                         to: IndexPath(item: destinationIndex, section: 0))
                }, completion: { _ in
                    // iOS Home Screen Logic: Update grid layout efficiently
                    self.updateGridLayoutEfficiently(in: collectionView)
                    
                    // Validate the grid layout after reordering
                    self.validateGridLayoutAfterReorder(in: collectionView)
                })
            }
            
            CATransaction.commit()
            
            // iOS Home Screen Logic: Force additional layout passes to ensure stability
            DispatchQueue.main.async {
                collectionView.layoutIfNeeded()
                
                // Final layout pass to ensure all cells are properly positioned
                DispatchQueue.main.async {
                    collectionView.layoutIfNeeded()
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionWillBegin session: UIDragSession) {
            // Disable animations during drag to prevent flickering
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.commit()
            
            // Mark the dragged cell to prevent layout conflicts
            if let draggedItem = session.items.first,
               let wrapper = draggedItem.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.goalStreak {
                // Store the dragged index to prevent flickering
                store.state.ui.isGoalCardBeingDragged = (wrapper.item as? MileStoneType) == .goalCard
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            // Re-enable animations after drag ends
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.commit()
            
            // Clear drag state
            store.state.ui.isGoalCardBeingDragged = false
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnter session: UIDropSession) {
            // Disable animations when drop session enters to prevent flickering
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.commit()
            
            // Prepare for immediate reordering
            collectionView.layoutIfNeeded()
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnd session: UIDropSession) {
            // Re-enable animations after drop session ends
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.commit()
            
            // Force layout update to ensure proper positioning
            UIView.performWithoutAnimation {
                collectionView.layoutIfNeeded()
            }
            
            // Clear any remaining drag state
            store.state.ui.isGoalCardBeingDragged = false
        }
        
        /// Saves the current grid order to DashboardStore UI state
        private func persistGridOrderToStore() {
            var newStreakOrder: [MetricItem] = []
            var goalCardPosition: Int? = nil

            for (index, widget) in gridModel.mileStones.enumerated() {
                switch widget {
                case .goalCard:
                    goalCardPosition = index
                case .streak(let streakItem):
                    newStreakOrder.append(streakItem)
                }
            }

            // Save streak order as array of IDs
            store.state.ui.streakGridOrder = newStreakOrder.map { $0.id.uuidString }
            
            // Save goal card position
            store.state.ui.goalCardPosition = goalCardPosition ?? 0
            
            // Force UI update to reflect the changes
            store.objectWillChange.send()
        }
        
        /// iOS Home Screen Logic: Update grid layout efficiently to prevent flickering
        private func updateGridLayoutEfficiently(in collectionView: UICollectionView) {
            // iOS Home Screen Logic: Disable animations during layout updates
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            
            // Force layout update
            collectionView.layoutIfNeeded()
            
            // Ensure all cells are properly positioned
            collectionView.visibleCells.forEach { cell in
                if let indexPath = collectionView.indexPath(for: cell) {
                    // Get the layout attributes for this cell
                    if let attributes = collectionView.layoutAttributesForItem(at: indexPath) {
                        // Apply the correct frame immediately
                        cell.frame = attributes.frame
                        
                        // Remove any transform animations
                        cell.transform = .identity
                        cell.contentView.transform = .identity
                    }
                }
            }
            
            CATransaction.commit()
        }
        
        /// iOS Home Screen Logic: Validate grid layout after reordering
        private func validateGridLayoutAfterReorder(in collectionView: UICollectionView) {
            // Ensure the collection view layout is valid
            collectionView.collectionViewLayout.invalidateLayout()
            
            // Force a layout pass
            collectionView.layoutIfNeeded()
            
            // Verify all cells are in correct positions
            collectionView.visibleCells.forEach { cell in
                if let indexPath = collectionView.indexPath(for: cell) {
                    let expectedFrame = collectionView.layoutAttributesForItem(at: indexPath)?.frame
                    if let expectedFrame = expectedFrame, cell.frame != expectedFrame {
                        // Correct the cell position if it's wrong
                        UIView.performWithoutAnimation {
                            cell.frame = expectedFrame
                        }
                    }
                }
            }
        }
    }
}
