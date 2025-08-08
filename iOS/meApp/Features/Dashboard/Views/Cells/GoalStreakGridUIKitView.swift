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
        context.coordinator.store = store
        context.coordinator.gridModel = buildGridModelFromStoreState()
        
        // Reload data when edit mode changes to update wiggle state
        collectionView.reloadData()
        collectionView.collectionViewLayout.invalidateLayout()
        collectionView.layoutIfNeeded()
        collectionView.invalidateIntrinsicContentSize()
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
        collectionView.dragInteractionEnabled = true
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

        // Partition streaks into non-removed and removed
        let nonRemovedStreaks = orderedStreaks.filter { streak in
            let index = orderedStreaks.firstIndex(where: { $0.id == streak.id }) ?? 0
            return !store.isStreakRemovedInReorderedArray(at: index)
        }
        let removedStreaks = orderedStreaks.filter { streak in
            let index = orderedStreaks.firstIndex(where: { $0.id == streak.id }) ?? 0
            return store.isStreakRemovedInReorderedArray(at: index)
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
                // 2 columns per row, gap between columns is 16px
                let itemWidth = (contentWidth - interItemSpacing) / 2.0
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
            let widget = gridModel.mileStones[indexPath.item]
            let itemProvider: NSItemProvider
            switch widget {
            case .goalCard:
                itemProvider = NSItemProvider(object: "goalCard" as NSString)
            case .streak(let item):
                itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
            }
            let dragItem = UIDragItem(itemProvider: itemProvider)
            dragItem.localObject = widget
            return [dragItem]
        }
        
        func collectionView(_ collectionView: UICollectionView, dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let params = UIDragPreviewParameters()
            params.backgroundColor = .clear
            params.visiblePath = UIBezierPath(roundedRect: collectionView.cellForItem(at: indexPath)?.bounds ?? .zero, cornerRadius: 10)
            return params
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
        }
        
        func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
            guard let destinationIndexPath = coordinator.destinationIndexPath,
                  let item = coordinator.items.first,
                  let sourceIndexPath = item.sourceIndexPath else { return }

            gridModel.moveWidget(from: sourceIndexPath.item, to: destinationIndexPath.item)
            collectionView.performBatchUpdates({
                collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
            }, completion: nil)

            // Save the new order to DashboardStore UI state
            persistGridOrderToStore()
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
    }
}
