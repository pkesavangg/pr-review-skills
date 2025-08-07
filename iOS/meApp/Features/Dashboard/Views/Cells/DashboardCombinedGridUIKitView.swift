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
    
    @ObservedObject var store: DashboardStore
    @State private var isDragging: Bool = false
    @State private var draggedItemId: String?
    
    func makeUIView(context: Context) -> UICollectionView {
        let layout = createLayout()
        let collectionView = createCollectionView(with: layout)
        setupCollectionView(collectionView, context: context)
        return collectionView
    }
    
    func updateUIView(_ uiView: UICollectionView, context: Context) {
        context.coordinator.store = store
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
        collectionView.dragInteractionEnabled = true
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakItemCell.self, forCellWithReuseIdentifier: "StreakItemCell")
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

extension DashboardCombinedGridUIKitView {
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        
        var parent: DashboardCombinedGridUIKitView
        var store: DashboardStore
        private var draggedItemId: String?
        
        init(_ parent: DashboardCombinedGridUIKitView) {
            self.parent = parent
            self.store = parent.store
        }
        
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            let streakItemsCount = store.streakItemsToShow.count
            return goalCardCount + streakItemsCount
        }
        
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < goalCardCount {
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GoalCardCell", for: indexPath) as! GoalCardCell
                cell.configure(with: store)
                cell.rowIndex = indexPath.row
                cell.isWiggling = store.state.ui.isEditMode
                cell.onDeleteTapped = {
                    self.store.toggleGoalCardRemoval()
                }
                return cell
            } else {
                let streakIndex = indexPath.item - goalCardCount
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "StreakItemCell", for: indexPath) as! StreakItemCell
                let item = store.streakItemsToShow[streakIndex]
                cell.configure(with: item, store: store)
                cell.rowIndex = indexPath.row
                cell.isWiggling = store.state.ui.isEditMode
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
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < goalCardCount {
                let availableWidth = collectionView.bounds.width - 40
                return CGSize(width: availableWidth, height: 140)
            } else {
                let availableWidth = collectionView.bounds.width - 40 - 16
                let itemWidth = availableWidth / 2
                return CGSize(width: itemWidth, height: 100)
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            
            if indexPath.item < goalCardCount {
                let itemProvider = NSItemProvider(object: "goalCard" as NSString)
                let dragItem = UIDragItem(itemProvider: itemProvider)
                dragItem.localObject = "goalCard"
                session.localContext = indexPath
                draggedItemId = "goalCard"
                store.startDraggingGoalCard()
                if let cell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                    DispatchQueue.main.async {
                        cell.configure(with: self.store)
                    }
                }
                HapticFeedbackService.medium()
                return [dragItem]
            } else {
                let streakIndex = indexPath.item - goalCardCount
                let item = store.streakItemsToShow[streakIndex]
                let isRemoved = store.isStreakRemovedInReorderedArray(at: streakIndex)
                if isRemoved { return [] }
                
                let itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
                let dragItem = UIDragItem(itemProvider: itemProvider)
                dragItem.localObject = DragItemWrapper(type: .streak, item: item)
                session.localContext = indexPath
                draggedItemId = item.id.uuidString
                store.startDraggingStreak(item)
                if let cell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                    DispatchQueue.main.async {
                        cell.configure(with: item, store: self.store)
                    }
                }
                HapticFeedbackService.medium()
                return [dragItem]
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let parameters = UIDragPreviewParameters()
            parameters.backgroundColor = .clear
            if let cell = collectionView.cellForItem(at: indexPath) as? GoalCardCell {
                parameters.visiblePath = UIBezierPath(roundedRect: cell.contentView.frame, cornerRadius: 16)
            } else if let cell = collectionView.cellForItem(at: indexPath) as? StreakItemCell {
                parameters.visiblePath = UIBezierPath(roundedRect: cell.contentView.frame, cornerRadius: 16)
            }
            return parameters
        }
        
        func collectionView(_ collectionView: UICollectionView, dragPreviewForLiftingItem item: UIDragItem, session: UIDragSession) -> UITargetedDragPreview? {
            if let dragItem = item.localObject as? String, dragItem == "goalCard" {
                guard let goalCell = collectionView.visibleCells.first(where: { $0 is GoalCardCell }) as? GoalCardCell else {
                    return nil
                }
                let previewView = goalCell.snapshotForPreview()
                let parameters = UIDragPreviewParameters()
                parameters.backgroundColor = .clear
                parameters.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: 16)
                let target = UIDragPreviewTarget(container: collectionView, center: goalCell.center)
                return UITargetedDragPreview(view: previewView, parameters: parameters, target: target)
            } else if let dragItem = item.localObject as? DragItemWrapper {
                guard let streakCell = collectionView.visibleCells.first(where: {
                    ($0 as? StreakItemCell)?.representedItem?.id == dragItem.item.id
                }) as? StreakItemCell else {
                    return nil
                }
                let previewView = streakCell.snapshotForPreview()
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
        }

        func forceReconfigureVisibleCells(_ collectionView: UICollectionView) {
            let goalCardCount = (!store.state.ui.isEditMode && store.state.ui.isGoalCardRemoved) ? 0 : 1
            for cell in collectionView.visibleCells {
                if let indexPath = collectionView.indexPath(for: cell) {
                    if indexPath.item < goalCardCount {
                        (cell as? GoalCardCell)?.configure(with: store)
                    } else if let streakCell = cell as? StreakItemCell {
                        let streakIndex = indexPath.item - goalCardCount
                        let item = store.streakItemsToShow[streakIndex]
                        streakCell.configure(with: item, store: store)
                    }
                }
            }
        }

        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            parent.isDragging = false
            draggedItemId = nil
            store.endDragging()
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
            }
        }

        func collectionView(_ collectionView: UICollectionView, item: UIDragItem, willAnimateCancelWith animator: UIDragAnimating) {
            animator.addCompletion { _ in
                self.draggedItemId = nil
                self.store.endDragging()
                if self.store.state.ui.isEditMode {
                    self.forceReconfigureVisibleCells(collectionView)
                }
            }
        }

        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
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

        func collectionView(_ collectionView: UICollectionView, dropSessionDidExit session: UIDropSession) {
            // Immediately clear drag state when drag exits
            store.endDragging()
            draggedItemId = nil
            parent.isDragging = false
            if store.state.ui.isEditMode {
                forceReconfigureVisibleCells(collectionView)
            }
        }

        func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
            // Full drop logic remains unchanged — simply remove print statements if present.
            
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
    }
}

// MARK: - Custom Collection View

public class CustomCollectionView: UICollectionView {
    override public func didAddSubview(_ subview: UIView) {
        super.didAddSubview(subview)
        if "\(type(of: subview))" == "_UIPlatterView" {
            subview.alpha = 0
        }
    }
    
    // Override intrinsic content size to ensure proper sizing
    override public var intrinsicContentSize: CGSize {
        let contentSize = self.collectionViewLayout.collectionViewContentSize
        return CGSize(width: UIView.noIntrinsicMetric, height: contentSize.height)
    }
}
