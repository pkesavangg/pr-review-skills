//
//  DashboardCombinedLayoutView.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import SwiftUI
import UIKit

/// Wrapper class to distinguish between different types of drag items
class DragItemWrapper {
    enum ItemType {
        case metric
        case streak
    }
    
    let type: ItemType
    let item: MetricItem
    
    init(type: ItemType, item: MetricItem) {
        self.type = type
        self.item = item
    }
}

/// A SwiftUI wrapper around UICollectionView that displays all dashboard items in a single layout
/// Provides iOS home screen-like behavior with wiggle animations and instant positioning
/// Combines metric grid, goal card, and streak items in one collection view like movingGridsLearning
struct DashboardCombinedLayoutView: UIViewRepresentable {
    
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
        collectionView.register(MetricCell.self, forCellWithReuseIdentifier: "MetricCell")
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakItemCell.self, forCellWithReuseIdentifier: "StreakItemCell")
        collectionView.register(UICollectionViewCell.self, forCellWithReuseIdentifier: "DividerCell")
        
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

extension DashboardCombinedLayoutView {
    /// Coordinator class that handles all UICollectionView delegate methods
    /// and manages the interaction between UIKit and SwiftUI
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        
        // MARK: - Properties
        
        var parent: DashboardCombinedLayoutView
        var store: DashboardStore
        private var draggedItemId: String?
        
        // MARK: - Initialization
        
        init(_ parent: DashboardCombinedLayoutView) {
            self.parent = parent
            self.store = parent.store
        }
        
        // MARK: - UICollectionViewDataSource
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            let streakItemsCount = store.streakItemsToShow.count
            
            // Total items: metrics + divider + goal card + streak items
            return metricsCount + dividerCount + goalCardCount + streakItemsCount
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < metricsCount {
                // Metric cell
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "MetricCell", for: indexPath) as! MetricCell
                let item = store.metricsToShow[indexPath.item]
                cell.configure(with: item, dashboardType: store.state.metrics.dashboardType, store: store)
                cell.rowIndex = indexPath.row
                cell.isWiggling = store.state.ui.isEditMode
                
                return cell
            } else if indexPath.item < metricsCount + dividerCount {
                // Divider cell - create a simple UIView cell for the divider
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "DividerCell", for: indexPath)
                cell.backgroundColor = .clear
                
                // Add divider view to the cell
                let dividerView = UIView()
                dividerView.backgroundColor = UIColor.systemGray4
                dividerView.translatesAutoresizingMaskIntoConstraints = false
                cell.contentView.addSubview(dividerView)
                
                NSLayoutConstraint.activate([
                    dividerView.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 20),
                    dividerView.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -20),
                    dividerView.centerYAnchor.constraint(equalTo: cell.contentView.centerYAnchor),
                    dividerView.heightAnchor.constraint(equalToConstant: 1)
                ])
                
                return cell
            } else {
                // This is the combined goal card + streak items grid
                let positionAfterDivider = indexPath.item - metricsCount - dividerCount
                
                // Check if this position should be the goal card
                if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                    // Goal card cell (large widget) - positioned based on goalCardPosition
                    let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GoalCardCell", for: indexPath) as! GoalCardCell
                    cell.configure(with: store)
                    cell.rowIndex = indexPath.row
                    cell.isWiggling = store.state.ui.isEditMode
                    
                    return cell
                } else {
                    // Streak item cell (medium widget) - adjust index based on goal card position
                    let streakIndex = positionAfterDivider - (positionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                    let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "StreakItemCell", for: indexPath) as! StreakItemCell
                    let item = store.streakItemsToShow[streakIndex]
                    cell.configure(with: item, store: store)
                    cell.rowIndex = indexPath.row
                    cell.isWiggling = store.state.ui.isEditMode
                    
                    return cell
                }
            }
        }
        
        // MARK: - UICollectionViewDelegateFlowLayout
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < metricsCount {
                // Metric cell - 3 columns
                let verticalPadding = store.state.metrics.dashboardType == .dashboard12 
                    ? MetricCardView.twelveCardVerticalPadding 
                    : MetricCardView.fourCardVerticalPadding
                
                let itemWidth = (collectionView.bounds.width - 40 - 32) / 3 // 3 columns with spacing
                let itemHeight = 70 + (verticalPadding * 2) // Base height + padding
                
                return CGSize(width: itemWidth, height: itemHeight)
            } else if indexPath.item < metricsCount + dividerCount {
                // Divider cell - full width
                return CGSize(width: collectionView.bounds.width, height: 1)
            } else if indexPath.item < metricsCount + dividerCount + goalCardCount {
                // Goal card - large widget (full width) - maintains size regardless of position
                let availableWidth = collectionView.bounds.width - 40 // Account for insets
                return CGSize(width: availableWidth, height: 140)
            } else {
                // Streak items - medium widgets (2 per row) - maintains size regardless of position
                let availableWidth = collectionView.bounds.width - 40 - 16 // Account for insets and spacing
                let itemWidth = availableWidth / 2
                return CGSize(width: itemWidth, height: 100)
            }
        }
        
        // MARK: - UICollectionViewDragDelegate
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            print("Hello: DRAG START - IndexPath: \(indexPath.item), Total items: \(collectionView.numberOfItems(inSection: 0))")
            print("Hello: Metrics count: \(metricsCount), Goal card count: \(goalCardCount), Streak items: \(store.streakItemsToShow.count)")
            
            // Print current positions of all items
            print("Hello: CURRENT POSITIONS - Metrics: \(store.metricsToShow.map { "\($0.label)(\($0.id))" })")
            print("Hello: CURRENT POSITIONS - Goal card: \(!store.state.ui.isGoalCardRemoved ? "present" : "removed")")
            print("Hello: CURRENT POSITIONS - Streak items: \(store.streakItemsToShow.map { "\($0.label)(\($0.id))" })")
            
            if indexPath.item < metricsCount {
                // Metric drag - only allow if not removed
                let item = store.metricsToShow[indexPath.item]
                
                // Check if the metric is removed - if so, don't allow dragging
                let isRemoved = store.isMetricRemovedInReorderedArray(at: indexPath.item)
                print("Hello: METRIC DRAG START - Item: \(item.label), Index: \(indexPath.item), IsRemoved: \(isRemoved)")
                
                if isRemoved {
                    print("Hello: METRIC DRAG BLOCKED - Item is removed, cannot drag")
                    return [] // Return empty array to prevent drag
                }
                
                let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
                let dragItem = UIDragItem(itemProvider: itemProvider)
                dragItem.localObject = DragItemWrapper(type: .metric, item: item)
                
                // Store the source index path for use in drag preview
                session.localContext = indexPath
                
                // Track the dragged item ID
                draggedItemId = item.id.uuidString
                
                // Update store drag state for EditModeOverlay visibility IMMEDIATELY
                store.startDraggingMetric(item)
                
                // Immediately reconfigure the dragged cell to hide EditModeOverlay
                if let metricCell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                    // Force immediate reconfiguration to update EditModeOverlay
                    DispatchQueue.main.async {
                        metricCell.configure(with: item, dashboardType: self.store.state.metrics.dashboardType, store: self.store)
                    }
                }
                
                // Provide haptic feedback
                HapticFeedbackService.medium()
                
                print("Hello: METRIC DRAG ALLOWED - Item: \(item.label), ID: \(item.id)")
                return [dragItem]
            } else if indexPath.item < metricsCount + dividerCount {
                // Divider is not draggable - return empty array
                print("Hello: DIVIDER DRAG BLOCKED - Dividers cannot be dragged")
                return []
            } else {
                // Check if this position should be a goal card or streak item
                let positionAfterDivider = indexPath.item - metricsCount - dividerCount
                
                if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                    // Goal card drag (large widget)
                    print("Hello: GOAL CARD DRAG START - Index: \(indexPath.item), Position: \(store.state.ui.goalCardPosition)")
                    
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
                    if let goalCell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                        // Force immediate reconfiguration to update EditModeOverlay
                        DispatchQueue.main.async {
                            goalCell.configure(with: self.store)
                        }
                    }
                    
                    // Provide haptic feedback
                    HapticFeedbackService.medium()
                    
                    print("Hello: GOAL CARD DRAG ALLOWED")
                    return [dragItem]
                } else {
                    // Streak item drag (medium widget) - only allow if not removed
                    let streakIndex = positionAfterDivider - (positionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                    let item = store.streakItemsToShow[streakIndex]
                    
                    // Check if the streak item is removed - if so, don't allow dragging
                    let isRemoved = store.isStreakRemovedInReorderedArray(at: streakIndex)
                    print("Hello: STREAK DRAG START - Item: \(item.label), Index: \(streakIndex), IsRemoved: \(isRemoved)")
                    
                    if isRemoved {
                        print("Hello: STREAK DRAG BLOCKED - Item is removed, cannot drag")
                        return [] // Return empty array to prevent drag
                    }
                    
                    let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
                    let dragItem = UIDragItem(itemProvider: itemProvider)
                    dragItem.localObject = DragItemWrapper(type: .streak, item: item)
                    
                    // Store the source index path for use in drag preview
                    session.localContext = indexPath
                    
                    // Track the dragged item ID
                    draggedItemId = item.id.uuidString
                    
                    // Update store drag state for EditModeOverlay visibility IMMEDIATELY
                    store.startDraggingStreak(item)
                    
                    // Immediately reconfigure the dragged cell to hide EditModeOverlay
                    if let streakCell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                        // Force immediate reconfiguration to update EditModeOverlay
                        DispatchQueue.main.async {
                            streakCell.configure(with: item, store: self.store)
                        }
                    }
                    
                    // Provide haptic feedback
                    HapticFeedbackService.medium()
                    
                    print("Hello: STREAK DRAG ALLOWED - Item: \(item.label), ID: \(item.id)")
                    return [dragItem]
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let parameters = UIDragPreviewParameters()
            parameters.backgroundColor = .clear

            if let cell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 8)
            } else if let cell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 16)
            } else if let cell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 16)
            } else if let cell = collectionView.cellForItem(at: indexPath) as? UICollectionViewCell {
                // For divider cell
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 0) // No corner radius for divider
            }

            return parameters
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewForLiftingItem item: UIDragItem,
                            session: UIDragSession) -> UITargetedDragPreview? {
            
            if let dragItem = item.localObject as? DragItemWrapper {
                // Metric or Streak item preview
                guard let metricCell = collectionView.visibleCells.first(where: {
                    guard let cellItem = ($0 as? MetricCell)?.representedItem else { return false }
                    return cellItem.id.uuidString == dragItem.item.id.uuidString
                }) as? MetricCell else {
                    // Try streak cell if metric cell not found
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
                
                let previewView = UIView(frame: metricCell.contentView.frame)
                previewView.backgroundColor = metricCell.contentView.backgroundColor
                previewView.layer.cornerRadius = metricCell.contentView.layer.cornerRadius
                previewView.layer.shadowColor = metricCell.contentView.layer.shadowColor
                previewView.layer.shadowOffset = metricCell.contentView.layer.shadowOffset
                previewView.layer.shadowRadius = metricCell.contentView.layer.shadowRadius
                previewView.layer.shadowOpacity = metricCell.contentView.layer.shadowOpacity
                previewView.alpha = 1.0
                
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 8)
                
                let target = UIDragPreviewTarget(container: collectionView, center: metricCell.center)
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
                
            } else if let dragItem = item.localObject as? String, dragItem == "goalCard" {
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
            }
            
            return nil
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionWillBegin session: UIDragSession) {
            parent.isDragging = true
            
            // Immediately hide EditModeOverlay on the dragged cell
            if let draggedItem = session.items.first {
                if let dragItem = draggedItem.localObject as? DragItemWrapper {
                    // Update store drag state immediately
                    if dragItem.type == .metric {
                        store.startDraggingMetric(dragItem.item)
                    } else if dragItem.type == .streak {
                        store.startDraggingStreak(dragItem.item)
                    }
                    
                    // Find and reconfigure the dragged cell to hide EditModeOverlay
                    for cell in collectionView.visibleCells {
                        if let metricCell = cell as? MetricCell,
                           metricCell.representedItem?.id == dragItem.item.id {
                            // Force immediate reconfiguration to update EditModeOverlay
                            metricCell.configure(with: dragItem.item, dashboardType: self.store.state.metrics.dashboardType, store: self.store)
                            break
                        } else if let streakCell = cell as? StreakItemCell,
                                  streakCell.representedItem?.id == dragItem.item.id {
                            // Force immediate reconfiguration to update EditModeOverlay
                            streakCell.configure(with: dragItem.item, store: self.store)
                            break
                        }
                    }
                } else if let dragItem = draggedItem.localObject as? String,
                          dragItem == "goalCard" {
                    // Update store drag state immediately
                    store.startDraggingGoalCard()
                    
                    // Find and reconfigure the goal card cell to hide EditModeOverlay
                    for cell in collectionView.visibleCells {
                        if let goalCell = cell as? GoalCardCell {
                            // Force immediate reconfiguration to update EditModeOverlay
                            goalCell.configure(with: self.store)
                            break
                        }
                    }
                }
            }
        }
        
        /// Forces reconfiguration of visible cells to update EditModeOverlay visibility
        func forceReconfigureVisibleCells(_ collectionView: UICollectionView) {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            for cell in collectionView.visibleCells {
                if let indexPath = collectionView.indexPath(for: cell) {
                    if indexPath.item < metricsCount {
                        // Reconfigure metric cell
                        if let metricCell = cell as? MetricCell {
                            let item = store.metricsToShow[indexPath.item]
                            metricCell.configure(with: item, dashboardType: store.state.metrics.dashboardType, store: store)
                        }
                    } else if indexPath.item < metricsCount + dividerCount {
                        // Reconfigure divider cell
                        if let dividerCell = cell as? UICollectionViewCell {
                            // No specific reconfiguration needed for divider
                        }
                    } else {
                        // Reconfigure goal card cell or streak cell
                        let positionAfterDivider = indexPath.item - metricsCount - dividerCount
                        
                        if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                            // Reconfigure goal card cell
                            if let goalCell = cell as? GoalCardCell {
                                goalCell.configure(with: store)
                            }
                        } else {
                            // Reconfigure streak cell
                            let streakIndex = positionAfterDivider - (positionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                            if let streakCell = cell as? StreakItemCell {
                                let item = store.streakItemsToShow[streakIndex]
                                streakCell.configure(with: item, store: store)
                            }
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
        
        // MARK: - Drag Session Management
        
        /// Prevents drag timeout for metric items by returning a long timeout
        func collectionView(_ collectionView: UICollectionView, dragSessionAllowsMoveOperation session: UIDragSession) -> Bool {
            // Allow move operations for all drag sessions (no timeout)
            return true
        }
        
        /// Prevents drag timeout by returning a long timeout duration
        func collectionView(_ collectionView: UICollectionView, dragSessionDidUpdate session: UIDragSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            // Return a proposal that allows the drag to continue indefinitely
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
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
                print("Hello: DROP FAILED - Missing required data")
                return 
            }
            
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            print("Hello: DROP ATTEMPT - Source: \(sourceIndexPath.item), Destination: \(destinationIndexPath.item)")
            print("Hello: DROP CONTEXT - Metrics: \(metricsCount), Divider: \(dividerCount), GoalCard: \(goalCardCount), Total: \(collectionView.numberOfItems(inSection: 0))")
            
            // Print current positions before drop
            print("Hello: BEFORE DROP - Metrics: \(store.metricsToShow.map { "\($0.label)(\($0.id))" })")
            print("Hello: BEFORE DROP - Goal card: \(!store.state.ui.isGoalCardRemoved ? "present" : "removed")")
            print("Hello: BEFORE DROP - Streak items: \(store.streakItemsToShow.map { "\($0.label)(\($0.id))" })")
            
            // Completely disable ALL animations and force instant positioning (like iOS home screen)
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            UIView.performWithoutAnimation {
                collectionView.performBatchUpdates({
                    if sourceIndexPath.item < metricsCount {
                        // This is a metric item
                        if let dragItem = item.dragItem.localObject as? DragItemWrapper,
                           dragItem.type == .metric {
                            print("Hello: METRIC DROP - Source: \(sourceIndexPath.item), Dest: \(destinationIndexPath.item)")
                            
                            // Metric reordering - only allow if both source and destination are not removed
                            let sourceMetricIndex = sourceIndexPath.item
                            let destMetricIndex = destinationIndexPath.item
                            
                            let sourceIsRemoved = store.isMetricRemovedInReorderedArray(at: sourceMetricIndex)
                            let destIsRemoved = store.isMetricRemovedInReorderedArray(at: destMetricIndex)
                            
                            print("Hello: METRIC DROP CHECK - SourceRemoved: \(sourceIsRemoved), DestRemoved: \(destIsRemoved)")
                            
                            // Only allow move if neither source nor destination is removed
                            if !sourceIsRemoved && !destIsRemoved {
                                // Move metric in the data source
                                let movedMetric = store.metricsManager.state.metrics.remove(at: sourceMetricIndex)
                                store.metricsManager.state.metrics.insert(movedMetric, at: destMetricIndex)
                                
                                // Provide haptic feedback for successful move
                                HapticFeedbackService.light()
                                
                                print("Hello: METRIC DROP EXECUTING - Moving from \(sourceMetricIndex) to \(destMetricIndex)")
                                collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                                print("Hello: METRIC DROP SUCCESS")
                            } else {
                                // If trying to move to/from a removed item, just refresh the UI
                                print("Hello: METRIC DROP BLOCKED - Cannot move to/from removed metric item")
                                collectionView.reloadData()
                            }
                        } else {
                            print("Hello: METRIC DROP ERROR - Invalid drag item type")
                            collectionView.reloadData()
                        }
                    } else if sourceIndexPath.item < metricsCount + dividerCount {
                        // This is a divider - dividers cannot be reordered
                        print("Hello: DIVIDER DROP BLOCKED - Dividers cannot be reordered")
                        collectionView.reloadData()
                    } else {
                        // This is either a goal card or streak item in the combined grid
                        let positionAfterDivider = sourceIndexPath.item - metricsCount - dividerCount
                        let destPositionAfterDivider = destinationIndexPath.item - metricsCount - dividerCount
                        
                        if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                            // This is a goal card being dragged
                            if item.dragItem.localObject as? String == "goalCard" {
                                print("Hello: GOAL CARD DROP - Source: \(sourceIndexPath.item), Dest: \(destinationIndexPath.item)")
                                
                                // Goal card reordering - only allow if goal card is not removed
                                if !store.state.ui.isGoalCardRemoved {
                                    // Calculate the new position for the goal card within the streak grid
                                    let newPositionAfterDivider = destPositionAfterDivider
                                    
                                    print("Hello: GOAL CARD DROP EXECUTING - Goal card not removed, moving from position \(store.state.ui.goalCardPosition) to \(newPositionAfterDivider)")
                                    
                                    // Update the goal card position in the store
                                    store.updateGoalCardPosition(newPositionAfterDivider)
                                    
                                    // Force UI update to reflect the new position
                                    collectionView.reloadData()
                                    
                                    // Provide haptic feedback for successful move
                                    HapticFeedbackService.light()
                                    
                                    print("Hello: GOAL CARD DROP SUCCESS - Moved to position \(store.state.ui.goalCardPosition)")
                                } else {
                                    // Goal card is removed, don't allow reordering
                                    print("Hello: GOAL CARD DROP BLOCKED - Goal card is removed")
                                    collectionView.reloadData()
                                }
                            } else {
                                print("Hello: GOAL CARD DROP ERROR - Invalid drag item type")
                                collectionView.reloadData()
                            }
                        } else {
                            // This is a streak item being dragged
                            if let dragItem = item.dragItem.localObject as? DragItemWrapper,
                               dragItem.type == .streak {
                                print("Hello: STREAK DROP - Item: \(dragItem.item.label), Source: \(sourceIndexPath.item), Dest: \(destinationIndexPath.item)")
                                
                                // Calculate streak indices accounting for goal card position
                                let sourceStreakIndex = positionAfterDivider - (positionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                                let destStreakIndex = destPositionAfterDivider - (destPositionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                                
                                print("Hello: STREAK DROP CALCULATION - SourceStreakIndex: \(sourceStreakIndex), DestStreakIndex: \(destStreakIndex)")
                                
                                // Validate indices
                                guard sourceStreakIndex >= 0 && sourceStreakIndex < store.streakManager.state.streakItems.count,
                                      destStreakIndex >= 0 && destStreakIndex < store.streakManager.state.streakItems.count else {
                                    print("Hello: STREAK DROP INVALID - Invalid streak indices: source=\(sourceStreakIndex), dest=\(destStreakIndex), count=\(store.streakManager.state.streakItems.count)")
                                    collectionView.reloadData()
                                    return
                                }
                                
                                let sourceIsRemoved = store.isStreakRemovedInReorderedArray(at: sourceStreakIndex)
                                let destIsRemoved = store.isStreakRemovedInReorderedArray(at: destStreakIndex)
                                
                                print("Hello: STREAK DROP CHECK - SourceRemoved: \(sourceIsRemoved), DestRemoved: \(destIsRemoved)")
                                
                                // Only allow move if neither source nor destination is removed
                                if !sourceIsRemoved && !destIsRemoved {
                                    // Move streak item in the data source
                                    let movedItem = store.streakManager.state.streakItems.remove(at: sourceStreakIndex)
                                    store.streakManager.state.streakItems.insert(movedItem, at: destStreakIndex)
                                    
                                    // Provide haptic feedback for successful move
                                    HapticFeedbackService.light()
                                    
                                    // Log the move for debugging
                                    print("Hello: STREAK DROP EXECUTING - Moving from \(sourceStreakIndex) to \(destStreakIndex)")
                                    
                                    collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                                    print("Hello: STREAK DROP SUCCESS")
                                } else {
                                    // If trying to move to/from a removed item, just refresh the UI
                                    print("Hello: STREAK DROP BLOCKED - Cannot move to/from removed streak item")
                                    collectionView.reloadData()
                                }
                            } else {
                                print("Hello: STREAK DROP ERROR - Invalid drag item type")
                                collectionView.reloadData()
                            }
                        }
                    }
                })
            }
            CATransaction.commit()
            
            // Print positions after drop
            print("Hello: AFTER DROP - Metrics: \(store.metricsToShow.map { "\($0.label)(\($0.id))" })")
            print("Hello: AFTER DROP - Goal card: \(!store.state.ui.isGoalCardRemoved ? "present" : "removed")")
            print("Hello: AFTER DROP - Streak items: \(store.streakItemsToShow.map { "\($0.label)(\($0.id))" })")
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