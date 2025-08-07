//
//  DashboardCombinedLayoutView.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import SwiftUI
import UIKit

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
struct DashboardCombinedLayoutView: UIViewRepresentable {
    
    @ObservedObject var store: DashboardStore
    @State private var isDragging: Bool = false
    @State private var draggedItemId: String?
    var onMetricLongPress: ((String) -> Void)? = nil
    
    func makeUIView(context: Context) -> UICollectionView {
        let layout = createLayout()
        let collectionView = createCollectionView(with: layout)
        setupCollectionView(collectionView, context: context)
        return collectionView
    }
    
    func updateUIView(_ uiView: UICollectionView, context: Context) {
        context.coordinator.store = store
        
        // Check if we need to restart wiggle animations (when gridLayoutId changes)
        let shouldRestartWiggle = context.coordinator.lastGridLayoutId != store.state.ui.gridLayoutId
        context.coordinator.lastGridLayoutId = store.state.ui.gridLayoutId
        
        // Safety check: force clear drag state if it's been stuck for too long
        if isDragging && !store.state.ui.isEditMode {
            context.coordinator.forceClearDragState()
            isDragging = false
        }
        
        if !isDragging {
            uiView.reloadData()
            // Force layout update to ensure proper content size calculation
            DispatchQueue.main.async {
                uiView.layoutIfNeeded()
                uiView.collectionViewLayout.invalidateLayout()
                uiView.collectionViewLayout.prepare()
                // Force content size calculation
                uiView.setNeedsLayout()
                uiView.layoutIfNeeded()
                // Notify SwiftUI that the view size has changed
                uiView.invalidateIntrinsicContentSize()
                
                // Restart wiggle animations if needed
                if shouldRestartWiggle && store.state.ui.isEditMode {
                    context.coordinator.restartWiggleAnimations(for: uiView)
                }
            }
        } else {
            context.coordinator.forceReconfigureVisibleCells(uiView)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    
    private func createLayout() -> LeadingAlignedFlowLayout {
        let layout = LeadingAlignedFlowLayout()
        layout.minimumInteritemSpacing = 16
        layout.minimumLineSpacing = 16
        layout.sectionInset = UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        return layout
    }
    
    private func createCollectionView(with layout: LeadingAlignedFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.dragInteractionEnabled = store.state.ui.isEditMode // Only enable drag in edit mode
        collectionView.register(MetricCell.self, forCellWithReuseIdentifier: "MetricCell")
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakItemCell.self, forCellWithReuseIdentifier: "StreakItemCell")
        collectionView.register(UICollectionViewCell.self, forCellWithReuseIdentifier: "DividerCell")
        collectionView.allowsSelection = false
        
        // Disable user scrolling but allow content size calculation
        collectionView.isScrollEnabled = false
        collectionView.showsVerticalScrollIndicator = false
        collectionView.showsHorizontalScrollIndicator = false
        
        // Ensure the collection view can calculate its full content size
        collectionView.contentInsetAdjustmentBehavior = .never
        
        return collectionView
    }
    
    private func setupCollectionView(_ collectionView: UICollectionView, context: Context) {
        collectionView.delegate = context.coordinator
        collectionView.dataSource = context.coordinator
        collectionView.dragDelegate = context.coordinator
        collectionView.dropDelegate = context.coordinator
    }
}

// MARK: - Coordinator

extension DashboardCombinedLayoutView {
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        
        var parent: DashboardCombinedLayoutView
        var store: DashboardStore
        private var draggedItemId: String?
        var lastGridLayoutId: UUID? = nil // Added to track gridLayoutId changes
        
        init(_ parent: DashboardCombinedLayoutView) {
            self.parent = parent
            self.store = parent.store
        }
        
        // MARK: - App Lifecycle Handling
        
        /// Restarts wiggle animations for all visible MetricCells when app becomes active
        func restartWiggleAnimations(for collectionView: UICollectionView) {
            guard store.state.ui.isEditMode else { return }
            
            // Restart wiggle animations for all visible MetricCells
            for cell in collectionView.visibleCells {
                if let metricCell = cell as? MetricCell {
                    metricCell.restartWiggleAnimation()
                }
            }
        }
        
        func forceReconfigureVisibleCells(_ collectionView: UICollectionView) {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            for cell in collectionView.visibleCells {
                if let indexPath = collectionView.indexPath(for: cell) {
                    if indexPath.item < metricsCount {
                        if let metricCell = cell as? MetricCell {
                            let item = store.metricsToShow[indexPath.item]
                            metricCell.configure(with: item, dashboardType: store.state.metrics.dashboardType, store: store)
                        }
                    } else if indexPath.item < metricsCount + dividerCount {
                        // Divider cell doesn't need reconfiguration
                    } else {
                        let positionAfterDivider = indexPath.item - metricsCount - dividerCount
                        if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                            if let goalCell = cell as? GoalCardCell {
                                goalCell.configure(with: store)
                            }
                        } else {
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

        // Add a safety method to force clear drag state
        func forceClearDragState() {
            store.endDragging()
            draggedItemId = nil
            parent.isDragging = false
        }
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            let streakItemsCount = store.streakItemsToShow.count
            return metricsCount + dividerCount + goalCardCount + streakItemsCount
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < metricsCount {
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "MetricCell", for: indexPath) as! MetricCell
                let item = store.metricsToShow[indexPath.item]
                cell.configure(
                    with: item,
                    dashboardType: store.state.metrics.dashboardType,
                    store: store,
                    onMetricLongPress: parent.onMetricLongPress,
                    onSelectMetric: { label in
                        if label.isEmpty {
                            self.store.state.ui.selectedMetricLabel = nil
                        } else {
                            self.store.state.ui.selectedMetricLabel = label
                        }
                    }
                )
                cell.rowIndex = indexPath.row
                cell.isWiggling = store.state.ui.isEditMode
                // Remove previous gesture recognizers
                cell.gestureRecognizers?.forEach { cell.removeGestureRecognizer($0) }
                if store.state.ui.isEditMode {
                    // Add drag-and-drop gesture in edit mode (handled by UIKit grid)
                    let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleMetricDragLongPress(_:)))
                    longPress.minimumPressDuration = 0.5
                    cell.addGestureRecognizer(longPress)
                    cell.tag = indexPath.item
                }
                // Tapping a metric sets it as selected
                cell.isUserInteractionEnabled = true
                return cell
            } else if indexPath.item < metricsCount + dividerCount {
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "DividerCell", for: indexPath)
                cell.backgroundColor = .clear
                let dividerView = UIView()
                dividerView.backgroundColor = UIColor.systemGray4
                dividerView.translatesAutoresizingMaskIntoConstraints = false
                cell.contentView.addSubview(dividerView)
                NSLayoutConstraint.activate([
                    dividerView.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor),
                    dividerView.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor),
                    dividerView.centerYAnchor.constraint(equalTo: cell.contentView.centerYAnchor),
                    dividerView.heightAnchor.constraint(equalToConstant: 1)
                ])
                return cell
            } else {
                let positionAfterDivider = indexPath.item - metricsCount - dividerCount
                if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                    let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GoalCardCell", for: indexPath) as! GoalCardCell
                    cell.configure(with: store)
                    cell.rowIndex = indexPath.row
                    cell.isWiggling = store.state.ui.isEditMode
                    return cell
                } else {
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
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < metricsCount {
                let verticalPadding = store.state.metrics.dashboardType == .dashboard12
                    ? MetricCardView.twelveCardVerticalPadding
                    : MetricCardView.fourCardVerticalPadding
                let itemWidth = (collectionView.bounds.width - 40 - 32) / 3
                let itemHeight = 70 + (verticalPadding * 2)
                return CGSize(width: itemWidth, height: itemHeight)
            } else if indexPath.item < metricsCount + dividerCount {
                return CGSize(width: collectionView.bounds.width - 40, height: 1)
            } else if indexPath.item < metricsCount + dividerCount + goalCardCount {
                let availableWidth = collectionView.bounds.width - 40
                return CGSize(width: availableWidth, height: 140)
            } else {
                let availableWidth = collectionView.bounds.width - 40 - 16
                let itemWidth = availableWidth / 2
                return CGSize(width: itemWidth, height: 100)
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < metricsCount {
                let item = store.metricsToShow[indexPath.item]
                let isRemoved = store.isMetricRemovedInReorderedArray(at: indexPath.item)
                if isRemoved { return [] }
                if !store.state.ui.isEditMode { return [] } // Prevent drag if not in edit mode
                let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
                let dragItem = UIDragItem(itemProvider: itemProvider)
                dragItem.localObject = DragItemWrapper(type: .metric, item: item)
                session.localContext = indexPath
                draggedItemId = item.id.uuidString
                store.startDraggingMetric(item)
                if let metricCell = collectionView.cellForItem(at: indexPath) as? MetricCell {
                    DispatchQueue.main.async {
                        metricCell.configure(with: item, dashboardType: self.store.state.metrics.dashboardType, store: self.store)
                    }
                }
                HapticFeedbackService.medium()
                return [dragItem]
            } else if indexPath.item < metricsCount + dividerCount {
                return []
            } else {
                let positionAfterDivider = indexPath.item - metricsCount - dividerCount
                if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                    let itemProvider = NSItemProvider(object: "goalCard" as NSString)
                    let dragItem = UIDragItem(itemProvider: itemProvider)
                    dragItem.localObject = "goalCard"
                    session.localContext = indexPath
                    draggedItemId = "goalCard"
                    store.startDraggingGoalCard()
                    if let goalCell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                        DispatchQueue.main.async {
                            goalCell.configure(with: self.store)
                        }
                    }
                    HapticFeedbackService.medium()
                    return [dragItem]
                } else {
                    let streakIndex = positionAfterDivider - (positionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                    let item = store.streakItemsToShow[streakIndex]
                    let isRemoved = store.isStreakRemovedInReorderedArray(at: streakIndex)
                    if isRemoved { return [] }
                    if !store.state.ui.isEditMode { return [] } // Prevent drag if not in edit mode
                    let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
                    let dragItem = UIDragItem(itemProvider: itemProvider)
                    dragItem.localObject = DragItemWrapper(type: .streak, item: item)
                    session.localContext = indexPath
                    draggedItemId = item.id.uuidString
                    store.startDraggingStreak(item)
                    if let streakCell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                        DispatchQueue.main.async {
                            streakCell.configure(with: item, store: self.store)
                        }
                    }
                    HapticFeedbackService.medium()
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
            } else if let cell = collectionView.cellForItem(at: indexPath) {
                let contentFrame = cell.contentView.frame
                parameters.visiblePath = UIBezierPath(roundedRect: contentFrame, cornerRadius: 0)
            }

            return parameters
        }
        
        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewForLiftingItem item: UIDragItem,
                            session: UIDragSession) -> UITargetedDragPreview? {
            
            if let dragItem = item.localObject as? DragItemWrapper {
                guard let metricCell = collectionView.visibleCells.first(where: {
                    guard let cellItem = ($0 as? MetricCell)?.representedItem else { return false }
                    return cellItem.id.uuidString == dragItem.item.id.uuidString
                }) as? MetricCell else {
                    guard let streakCell = collectionView.visibleCells.first(where: {
                        guard let cellItem = ($0 as? StreakItemCell)?.representedItem else { return false }
                        return cellItem.id.uuidString == dragItem.item.id.uuidString
                    }) as? StreakItemCell else {
                        return nil
                    }
                    let previewView = UIView(frame: streakCell.contentView.frame)
                    previewView.backgroundColor = streakCell.contentView.backgroundColor
                   previewView.layer.cornerRadius = streakCell.contentView.layer.cornerRadius
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
                previewView.alpha = 1.0
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 8)
                let target = UIDragPreviewTarget(container: collectionView, center: metricCell.center)
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
            } else if let dragItem = item.localObject as? String, dragItem == "goalCard" {
                guard let goalCell = collectionView.visibleCells.first(where: {
                    $0 is GoalCardCell
                }) as? GoalCardCell else {
                    return nil
                }
                let previewView = UIView(frame: goalCell.contentView.frame)
                previewView.backgroundColor = goalCell.contentView.backgroundColor
                previewView.layer.cornerRadius = goalCell.contentView.layer.cornerRadius
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
            guard store.state.ui.isEditMode else { return }
            parent.isDragging = true
            if let draggedItem = session.items.first {
                if let dragItem = draggedItem.localObject as? DragItemWrapper {
                    if dragItem.type == .metric {
                        store.startDraggingMetric(dragItem.item)
                    } else if dragItem.type == .streak {
                        store.startDraggingStreak(dragItem.item)
                    }
                    // Immediately reconfigure ALL visible cells to reflect the new drag state
                    forceReconfigureVisibleCells(collectionView)
                } else if let dragItem = draggedItem.localObject as? String,
                          dragItem == "goalCard" {
                    store.startDraggingGoalCard()
                    // Immediately reconfigure ALL visible cells to reflect the new drag state
                    forceReconfigureVisibleCells(collectionView)
                }
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            
            // Immediately reconfigure cells to restore overlay visibility
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
            }
        }

        // Add additional drag session handling methods
        func collectionView(_ collectionView: UICollectionView, dropSessionDidExit session: UIDropSession) {
            // Immediately clear drag state when drag exits
            store.endDragging()
            draggedItemId = nil
            parent.isDragging = false
            // Immediately reconfigure cells to restore overlay visibility
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
            }
        }

        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession, withOperation operation: UIDropOperation) {
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            
            // Immediately reconfigure cells to restore overlay visibility
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, item: UIDragItem, willAnimateCancelWith animator: UIDragAnimating) {
            animator.addCompletion { _ in
                self.draggedItemId = nil
                self.store.endDragging()
                self.parent.isDragging = false
                // Immediately reconfigure cells to restore overlay visibility
                if self.store.state.ui.isEditMode {
                    self.forceReconfigureVisibleCells(collectionView)
                }
            }
        }
        
        // Add immediate drag state clearing methods
        func collectionView(_ collectionView: UICollectionView, dragSessionAllowsMoveOperation session: UIDragSession) -> Bool {
            return store.state.ui.isEditMode
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidUpdate session: UIDragSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            return store.state.ui.isEditMode ? UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath) : UICollectionViewDropProposal(operation: .forbidden)
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            return store.state.ui.isEditMode ? UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath) : UICollectionViewDropProposal(operation: .forbidden)
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnter session: UIDropSession) {
            // Immediately clear any existing drag state when drop session enters
            store.endDragging()
            draggedItemId = nil
            parent.isDragging = false
            
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
            
            let metricsCount = store.metricsToShow.count
            let dividerCount = (metricsCount > 0 && (!store.state.ui.isGoalCardRemoved || store.streakItemsToShow.count > 0)) ? 1 : 0
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            UIView.performWithoutAnimation {
                collectionView.performBatchUpdates({
                    if sourceIndexPath.item < metricsCount {
                        if let dragItem = item.dragItem.localObject as? DragItemWrapper,
                           dragItem.type == .metric {
                            // Add guard clauses to ensure indices are within bounds
                            let sourceMetricIndex = sourceIndexPath.item
                            let destMetricIndex = destinationIndexPath.item

                            // Ensure indices are within bounds before accessing the array
                            guard sourceMetricIndex >= 0 && sourceMetricIndex < store.metricsManager.state.metrics.count,
                                  destMetricIndex >= 0 && destMetricIndex < store.metricsManager.state.metrics.count else {
                                collectionView.reloadData()
                                return
                            }

                            // Proceed with moving the metric if indices are valid
                            let movedMetric = store.metricsManager.state.metrics.remove(at: sourceMetricIndex)
                            store.metricsManager.state.metrics.insert(movedMetric, at: destMetricIndex)
                            HapticFeedbackService.light()
                            collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                        } else {
                            collectionView.reloadData()
                        }
                    } else if sourceIndexPath.item < metricsCount + dividerCount {
                        collectionView.reloadData()
                    } else {
                        let positionAfterDivider = sourceIndexPath.item - metricsCount - dividerCount
                        let destPositionAfterDivider = destinationIndexPath.item - metricsCount - dividerCount
                        if positionAfterDivider == store.state.ui.goalCardPosition && goalCardCount > 0 {
                            if item.dragItem.localObject as? String == "goalCard" {
                                if !store.state.ui.isGoalCardRemoved {
                                    let newPositionAfterDivider = destPositionAfterDivider
                                    store.updateGoalCardPosition(newPositionAfterDivider)
                                    collectionView.reloadData()
                                    HapticFeedbackService.light()
                                } else {
                                    collectionView.reloadData()
                                }
                            } else {
                                collectionView.reloadData()
                            }
                        } else {
                            if let dragItem = item.dragItem.localObject as? DragItemWrapper,
                               dragItem.type == .streak {
                                let sourceStreakIndex = positionAfterDivider - (positionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                                let destStreakIndex = destPositionAfterDivider - (destPositionAfterDivider > store.state.ui.goalCardPosition ? 1 : 0)
                                guard sourceStreakIndex >= 0 && sourceStreakIndex < store.streakManager.state.streakItems.count,
                                      destStreakIndex >= 0 && destStreakIndex < store.streakManager.state.streakItems.count else {
                                    collectionView.reloadData()
                                    return
                                }
                                let sourceIsRemoved = store.isStreakRemovedInReorderedArray(at: sourceStreakIndex)
                                let destIsRemoved = store.isStreakRemovedInReorderedArray(at: destStreakIndex)
                                if !sourceIsRemoved && !destIsRemoved {
                                    let movedItem = store.streakManager.state.streakItems.remove(at: sourceStreakIndex)
                                    store.streakManager.state.streakItems.insert(movedItem, at: destStreakIndex)
                                    HapticFeedbackService.light()
                                    collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                                } else {
                                    collectionView.reloadData()
                                }
                            } else {
                                collectionView.reloadData()
                            }
                        }
                    }
                })
            }
            CATransaction.commit()
            
            // Immediately clear drag state after drop is executed
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            
            // Immediately reconfigure cells to restore overlay visibility
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
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
            
            // Immediately reconfigure cells to restore overlay visibility
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
            }
        }
        
        @objc func handleMetricLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard gesture.state == .began,
                  let cell = gesture.view as? MetricCell,
                  let item = cell.representedItem,
                  !store.state.ui.isEditMode else { return }
            parent.onMetricLongPress?(item.label)
        }
        
        @objc func handleMetricDragLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard let cell = gesture.view as? MetricCell else { return }
            switch gesture.state {
            case .began:
                // Hide the EditModeOverlay and shadow
                cell.updateDragState(true)
                cell.contentView.layer.shadowOpacity = 0
            case .ended, .cancelled:
                // Restore the EditModeOverlay and shadow
                cell.updateDragState(false)
                cell.contentView.layer.shadowOpacity = 1
            default:
                break
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, willBeginInteractiveMovementForItemAt indexPath: IndexPath) {
            if let cell = collectionView.cellForItem(at: indexPath) {
                cell.layer.shadowOpacity = 0
                cell.layer.shadowRadius = 0
            }
        }
    }
}
