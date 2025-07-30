//
//  DashboardCombinedGridUIKitView.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import SwiftUI
import UIKit

/// A SwiftUI wrapper around UICollectionView that displays goal card and streak items as widgets
/// Provides iOS home screen-like behavior with wiggle animations and instant positioning
/// Goal card is treated as a large widget, streak items as medium widgets (2 per row)
struct DashboardCombinedGridUIKitView: UIViewRepresentable {
    
    // MARK: - Properties
    
    @ObservedObject var store: DashboardStore
    @State private var isDragging: Bool = false
    @State private var draggedItemId: String?
    
    // MARK: - UIViewRepresentable
    
    func makeUIView(context: Context) -> UICollectionView {
        let layout = createLayout()
        let collectionView = createCollectionView(with: layout)
        setupCollectionView(collectionView, context: context)
        return collectionView
    }
    
    func updateUIView(_ uiView: UICollectionView, context: Context) {
        context.coordinator.store = store
        
        // Reload data when edit mode changes to update wiggle state
        // But don't reload if currently dragging to avoid interrupting the drag
        if !isDragging {
            uiView.reloadData()
        } else {
            // If dragging, just reconfigure visible cells to update EditModeOverlay
            context.coordinator.forceReconfigureVisibleCells(uiView)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    // MARK: - Private Methods
    
    /// Creates the collection view layout with proper spacing and insets
    private func createLayout() -> UICollectionViewFlowLayout {
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 16
        layout.minimumLineSpacing = 16
        layout.sectionInset = UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        return layout
    }
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: UICollectionViewFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.dragInteractionEnabled = true
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakItemCell.self, forCellWithReuseIdentifier: "StreakItemCell")
        
        // Disable selection to prevent visual feedback
        collectionView.allowsSelection = false
        
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

extension DashboardCombinedGridUIKitView {
    /// Coordinator class that handles all UICollectionView delegate methods
    /// and manages the interaction between UIKit and SwiftUI
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        
        // MARK: - Properties
        
        var parent: DashboardCombinedGridUIKitView
        var store: DashboardStore
        private var draggedItemId: String?
        
        // MARK: - Initialization
        
        init(_ parent: DashboardCombinedGridUIKitView) {
            self.parent = parent
            self.store = parent.store
        }
        
        // MARK: - UICollectionViewDataSource
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            // Goal card (1) + streak items
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            let streakItemsCount = store.streakItemsToShow.count
            return goalCardCount + streakItemsCount
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < goalCardCount {
                // Goal card cell (large widget)
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GoalCardCell", for: indexPath) as! GoalCardCell
                cell.configure(with: store)
                cell.rowIndex = indexPath.row
                cell.isWiggling = store.state.ui.isEditMode
                
                // Hide delete button if this is the currently dragged item
                if let draggedId = draggedItemId, draggedId == "goalCard" {
                    // The EditModeOverlay will automatically hide based on isBeingDragged state
                }
                
                // Set up delete callback (EditModeOverlay handles the UI)
                cell.onDeleteTapped = {
                    self.store.toggleGoalCardRemoval()
                }
                
                return cell
            } else {
                // Streak item cell (medium widget)
                let streakIndex = indexPath.item - goalCardCount
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "StreakItemCell", for: indexPath) as! StreakItemCell
                let item = store.streakItemsToShow[streakIndex]
                cell.configure(with: item, store: store)
                cell.rowIndex = indexPath.row
                cell.isWiggling = store.state.ui.isEditMode
                
                // Hide delete button if this is the currently dragged item
                if let draggedId = draggedItemId, draggedId == item.id.uuidString {
                    // The EditModeOverlay will automatically hide based on isBeingDragged state
                }
                
                // Set up delete callback (EditModeOverlay handles the UI)
                cell.onDeleteTapped = {
                    if let originalIndex = self.store.state.streak.streakItems.firstIndex(where: { $0.id == item.id }) {
                        Task {
                            try? await self.store.streakManager.toggleStreakVisibility(at: originalIndex)
                        }
                    }
                }
                
                return cell
            }
        }
        
        // MARK: - UICollectionViewDelegateFlowLayout
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < goalCardCount {
                // Goal card - large widget (full width)
                let availableWidth = collectionView.bounds.width - 40 // Account for insets
                return CGSize(width: availableWidth, height: 140)
            } else {
                // Streak items - medium widgets (2 per row)
                let availableWidth = collectionView.bounds.width - 40 - 16 // Account for insets and spacing
                let itemWidth = availableWidth / 2
                return CGSize(width: itemWidth, height: 100)
            }
        }
        
        // MARK: - UICollectionViewDragDelegate
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            print("Hello: GRID DRAG START - IndexPath: \(indexPath.item), Total items: \(collectionView.numberOfItems(inSection: 0))")
            print("Hello: GRID CONTEXT - GoalCard: \(goalCardCount), Streak items: \(store.streakItemsToShow.count)")
            
            // Print current positions of all items
            print("Hello: GRID CURRENT POSITIONS - Goal card: \(!store.state.ui.isGoalCardRemoved ? "present" : "removed")")
            print("Hello: GRID CURRENT POSITIONS - Streak items: \(store.streakItemsToShow.map { "\($0.label)(\($0.id))" })")
            
            if indexPath.item < goalCardCount {
                // Goal card drag (large widget)
                print("Hello: GOAL CARD GRID DRAG START - Index: \(indexPath.item)")
                
                let itemProvider = NSItemProvider(object: "goalCard" as NSString)
                let dragItem = UIDragItem(itemProvider: itemProvider)
                dragItem.localObject = "goalCard"
                
                // Store the source index path for use in drag preview
                session.localContext = indexPath
                
                // Track the dragged item ID
                draggedItemId = "goalCard"
                
                // Update store drag state for EditModeOverlay visibility IMMEDIATELY
                store.startDraggingGoalCard()
                
                // Immediately reconfigure the dragged cell to hide EditModeOverlay
                if let cell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                    // Force immediate reconfiguration to update EditModeOverlay
                    DispatchQueue.main.async {
                        cell.configure(with: self.store)
                    }
                }
                
                // Provide haptic feedback
                HapticFeedbackService.medium()
                
                print("Hello: GOAL CARD GRID DRAG ALLOWED")
                return [dragItem]
            } else {
                // Streak item drag (medium widget) - only allow if not removed
                let streakIndex = indexPath.item - goalCardCount
                let item = store.streakItemsToShow[streakIndex]
                
                print("Hello: STREAK GRID DRAG START - Item: \(item.label), StreakIndex: \(streakIndex), CollectionIndex: \(indexPath.item)")
                
                // Check if the streak item is removed - if so, don't allow dragging
                let isRemoved = store.isStreakRemovedInReorderedArray(at: streakIndex)
                print("Hello: STREAK GRID DRAG CHECK - IsRemoved: \(isRemoved)")
                
                if isRemoved {
                    print("Hello: STREAK GRID DRAG BLOCKED - Item is removed, cannot drag")
                    return [] // Return empty array to prevent drag
                }
                
                let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
                let dragItem = UIDragItem(itemProvider: itemProvider)
                // Wrap the streak item with a type identifier
                dragItem.localObject = DragItemWrapper(type: .streak, item: item)
                
                // Store the source index path for use in drag preview
                session.localContext = indexPath
                
                // Track the dragged item ID
                draggedItemId = item.id.uuidString
                
                // Update store drag state for EditModeOverlay visibility IMMEDIATELY
                store.startDraggingStreak(item)
                
                // Immediately reconfigure the dragged cell to hide EditModeOverlay
                if let cell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                    // Force immediate reconfiguration to update EditModeOverlay
                    DispatchQueue.main.async {
                        cell.configure(with: item, store: self.store)
                    }
                }
                
                // Provide haptic feedback
                HapticFeedbackService.medium()
                
                print("Hello: STREAK GRID DRAG ALLOWED - Item: \(item.label), ID: \(item.id)")
                return [dragItem]
            }
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let parameters = UIDragPreviewParameters()
            parameters.backgroundColor = .clear

            if let cell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 16)
            } else if let cell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 16)
            }

            return parameters
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewForLiftingItem item: UIDragItem,
                            session: UIDragSession) -> UITargetedDragPreview? {
            
            if let dragItem = item.localObject as? String, dragItem == "goalCard" {
                // Goal card preview
                guard let goalCell = collectionView.visibleCells.first(where: {
                    $0 is GoalCardCell
                }) as? GoalCardCell else {
                    return nil
                }
                
                let previewView = UIView(frame: goalCell.contentView.frame)
                previewView.backgroundColor = goalCell.contentView.backgroundColor
                previewView.layer.cornerRadius = goalCell.contentView.layer.cornerRadius
                previewView.layer.shadowColor = goalCell.contentView.layer.shadowColor
                previewView.layer.shadowOffset = goalCell.contentView.layer.shadowOffset
                previewView.layer.shadowRadius = goalCell.contentView.layer.shadowRadius
                previewView.layer.shadowOpacity = goalCell.contentView.layer.shadowOpacity
                previewView.alpha = 1.0
                
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 16)
                
                let target = UIDragPreviewTarget(container: collectionView, center: goalCell.center)
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
                
            } else if let dragItem = item.localObject as? DragItemWrapper {
                // Streak item preview
                guard let streakCell = collectionView.visibleCells.first(where: {
                    guard let cellItem = ($0 as? StreakItemCell)?.representedItem else { return false }
                    return cellItem.id.uuidString == dragItem.item.id.uuidString
                }) as? StreakItemCell else {
                    return nil
                }
                
                let previewView = UIView(frame: streakCell.contentView.frame)
                previewView.backgroundColor = streakCell.contentView.backgroundColor
                previewView.layer.cornerRadius = streakCell.contentView.layer.cornerRadius
                previewView.layer.shadowColor = streakCell.contentView.layer.shadowColor
                previewView.layer.shadowOffset = streakCell.contentView.layer.shadowOffset
                previewView.layer.shadowRadius = streakCell.contentView.layer.shadowRadius
                previewView.layer.shadowOpacity = streakCell.contentView.layer.shadowOpacity
                previewView.alpha = 1.0
                
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 16)
                
                let target = UIDragPreviewTarget(container: collectionView, center: streakCell.center)
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
            }
            
            return nil
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionWillBegin session: UIDragSession) {
            parent.isDragging = true
            
            // Immediately hide EditModeOverlay on the dragged cell
            if let draggedItem = session.items.first,
               let dragItem = draggedItem.localObject as? DragItemWrapper {
                // Find and reconfigure the dragged streak cell to hide EditModeOverlay
                for cell in collectionView.visibleCells {
                    if let streakCell = cell as? StreakItemCell,
                       streakCell.representedItem?.id == dragItem.item.id {
                        // Update store drag state immediately
                        store.startDraggingStreak(dragItem.item)
                        // Force immediate reconfiguration to update EditModeOverlay
                        streakCell.configure(with: dragItem.item, store: store)
                        break
                    }
                }
            } else if let draggedItem = session.items.first,
                      let dragItem = draggedItem.localObject as? String,
                      dragItem == "goalCard" {
                // Find and reconfigure the goal card cell to hide EditModeOverlay
                for cell in collectionView.visibleCells {
                    if let goalCell = cell as? GoalCardCell {
                        // Update store drag state immediately
                        store.startDraggingGoalCard()
                        // Force immediate reconfiguration to update EditModeOverlay
                        goalCell.configure(with: store)
                        break
                    }
                }
            }
        }
        
        /// Forces reconfiguration of visible cells to update EditModeOverlay visibility
        func forceReconfigureVisibleCells(_ collectionView: UICollectionView) {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            for cell in collectionView.visibleCells {
                if let indexPath = collectionView.indexPath(for: cell) {
                    if indexPath.item < goalCardCount {
                        // Reconfigure goal card cell
                        if let goalCell = cell as? GoalCardCell {
                            goalCell.configure(with: store)
                        }
                    } else {
                        // Reconfigure streak cell
                        if let streakCell = cell as? StreakItemCell {
                            let streakIndex = indexPath.item - goalCardCount
                            let item = store.streakItemsToShow[streakIndex]
                            streakCell.configure(with: item, store: store)
                        }
                    }
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            parent.isDragging = false
            draggedItemId = nil
            
            // Clear store drag state
            store.endDragging()
            
            // Restore EditModeOverlay visibility on all cells if in edit mode
            if store.state.ui.isEditMode {
                DispatchQueue.main.async {
                    self.forceReconfigureVisibleCells(collectionView)
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, item: UIDragItem, willAnimateCancelWith animator: UIDragAnimating) {
            animator.addCompletion { _ in
                self.draggedItemId = nil
                
                // Clear store drag state
                self.store.endDragging()
                
                // Restore EditModeOverlay visibility on all cells if in edit mode
                if self.store.state.ui.isEditMode {
                    DispatchQueue.main.async {
                        self.forceReconfigureVisibleCells(collectionView)
                    }
                }
            }
        }
        
        // MARK: - UICollectionViewDropDelegate
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnter session: UIDropSession) {
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.commit()
        }
        
        func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
            guard let destinationIndexPath = coordinator.destinationIndexPath,
                  let item = coordinator.items.first,
                  let sourceIndexPath = item.sourceIndexPath else { 
                print("Hello: GRID DROP FAILED - Missing required data")
                return 
            }
            
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            print("Hello: GRID DROP ATTEMPT - Source: \(sourceIndexPath.item), Destination: \(destinationIndexPath.item)")
            print("Hello: GRID DROP CONTEXT - GoalCard: \(goalCardCount), Total: \(collectionView.numberOfItems(inSection: 0))")
            
            // Print current positions before drop
            print("Hello: GRID BEFORE DROP - Goal card: \(!store.state.ui.isGoalCardRemoved ? "present" : "removed")")
            print("Hello: GRID BEFORE DROP - Streak items: \(store.streakItemsToShow.map { "\($0.label)(\($0.id))" })")
            
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            UIView.performWithoutAnimation {
                collectionView.performBatchUpdates({
                    // Determine the type of item being dropped based on source index
                    if sourceIndexPath.item < goalCardCount {
                        // This is a goal card
                        if let dragItem = item.dragItem.localObject as? String, dragItem == "goalCard" {
                            print("Hello: GOAL CARD GRID DROP - Source: \(sourceIndexPath.item), Dest: \(destinationIndexPath.item)")
                            
                            // Goal card reordering - only allow if goal card is not removed
                            if !store.state.ui.isGoalCardRemoved {
                                // Goal card can be moved anywhere but maintain its large widget size
                                print("Hello: GOAL CARD GRID DROP EXECUTING - Goal card not removed, allowing reorder")
                                // The goal card position is managed by the store, so we just reload
                                collectionView.reloadData()
                                print("Hello: GOAL CARD GRID DROP SUCCESS")
                            } else {
                                // Goal card is removed, don't allow reordering
                                print("Hello: GOAL CARD GRID DROP BLOCKED - Goal card is removed")
                                collectionView.reloadData()
                            }
                        } else {
                            print("Hello: GOAL CARD GRID DROP ERROR - Invalid drag item type")
                            collectionView.reloadData()
                        }
                    } else {
                        // This is a streak item
                        if let dragItem = item.dragItem.localObject as? DragItemWrapper,
                           dragItem.type == .streak {
                            print("Hello: STREAK GRID DROP - Item: \(dragItem.item.label), Source: \(sourceIndexPath.item), Dest: \(destinationIndexPath.item)")
                            
                            // Streak item reordering - only allow if both source and destination are not removed
                            let sourceStreakIndex = sourceIndexPath.item - goalCardCount
                            let destStreakIndex = destinationIndexPath.item - goalCardCount
                            
                            print("Hello: STREAK GRID DROP CALCULATION - SourceStreakIndex: \(sourceStreakIndex), DestStreakIndex: \(destStreakIndex)")
                            
                            // Validate indices
                            guard sourceStreakIndex >= 0 && sourceStreakIndex < store.streakManager.state.streakItems.count,
                                  destStreakIndex >= 0 && destStreakIndex < store.streakManager.state.streakItems.count else {
                                print("Hello: STREAK GRID DROP INVALID - Invalid streak indices: source=\(sourceStreakIndex), dest=\(destStreakIndex), count=\(store.streakManager.state.streakItems.count)")
                                collectionView.reloadData()
                                return
                            }
                            
                            let sourceIsRemoved = store.isStreakRemovedInReorderedArray(at: sourceStreakIndex)
                            let destIsRemoved = store.isStreakRemovedInReorderedArray(at: destStreakIndex)
                            
                            print("Hello: STREAK GRID DROP CHECK - SourceRemoved: \(sourceIsRemoved), DestRemoved: \(destIsRemoved)")
                            
                            // Only allow move if neither source nor destination is removed
                            if !sourceIsRemoved && !destIsRemoved {
                                // Move streak item in the data source
                                let movedItem = store.streakManager.state.streakItems.remove(at: sourceStreakIndex)
                                store.streakManager.state.streakItems.insert(movedItem, at: destStreakIndex)
                                
                                // Provide haptic feedback for successful move
                                HapticFeedbackService.light()
                                
                                // Log the move for debugging
                                print("Hello: STREAK GRID DROP EXECUTING - Moving from \(sourceStreakIndex) to \(destStreakIndex)")
                                
                                collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                                print("Hello: STREAK GRID DROP SUCCESS")
                            } else {
                                // If trying to move to/from a removed item, just refresh the UI
                                print("Hello: STREAK GRID DROP BLOCKED - Cannot move to/from removed streak item")
                                collectionView.reloadData()
                            }
                        } else {
                            print("Hello: STREAK GRID DROP ERROR - Invalid drag item type")
                            collectionView.reloadData()
                        }
                    }
                })
            }
            CATransaction.commit()
            
            // Print positions after drop
            print("Hello: GRID AFTER DROP - Goal card: \(!store.state.ui.isGoalCardRemoved ? "present" : "removed")")
            print("Hello: GRID AFTER DROP - Streak items: \(store.streakItemsToShow.map { "\($0.label)(\($0.id))" })")
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnd session: UIDropSession) {
            draggedItemId = nil
            
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
        }
    }
}

// MARK: - Custom Collection View

/// Custom UICollectionView that suppresses system-generated drag preview views
/// to provide a cleaner drag-and-drop experience
public class CustomCollectionView: UICollectionView {
    
    override public func didAddSubview(_ subview: UIView) {
        super.didAddSubview(subview)
        
        // Fade the system-generated drag preview views instead of removing them
        if "\(type(of: subview))" == "_UIPlatterView" {
            subview.alpha = 0
        }
    }
} 