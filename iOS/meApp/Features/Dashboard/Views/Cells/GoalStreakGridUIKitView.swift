//
//  GoalStreakGridUIKitView.swift
//  meApp
//
//  Created by Lakshmi Priya on 07/08/25.
//

import SwiftUI
import UIKit

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
    
    private struct DropTargetConfig {
        /// Minimum time between drop target changes to prevent excessive haptic feedback
        static let changeThreshold: TimeInterval = 2.0
        
        /// Minimum distance change to consider a drop target change meaningful
        static let minimumDistanceThreshold: Int = 12
        
        /// Whether to enable smart drop target detection (reduces haptic feedback)
        static let enableSmartDetection = true
        
        /// Whether to show drop indicators only at valid grid boundaries
        static let showOnlyAtBoundaries = true
        
        /// Minimum grid position change to trigger haptic feedback
        static let minimumGridPositionChange: Int = 8
        
        /// Whether to use zone-based detection instead of individual cell detection
        static let useZoneBasedDetection = true
        
        /// Zone size for grouping nearby cells (reduces feedback frequency)
        static let zoneSize: Int = 6

        /// Minimum time between haptic feedback events to prevent vibration spam
        static let hapticFeedbackThreshold: TimeInterval = 2.5
        
        /// Whether to use iOS Home Screen-style drop target validation
        static let useHomeScreenValidation = true
        
        /// Minimum movement distance before considering drop target changes
        static let minimumMovementThreshold: CGFloat = 40.0
    }
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: UICollectionViewFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.hideDragPlatter = true // hide system drag preview platter
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

        private var lastDropTargetIndexPath: IndexPath?
        private var lastDropTargetItemType: MileStoneType?
        private var dropTargetChangeThreshold: TimeInterval = GoalStreakGridUIKitView.DropTargetConfig.changeThreshold
        private var lastDropTargetChangeTime: Date?
        
        // Minimal haptic feedback tracking
        private var lastHapticFeedbackTime: Date?

        /// Prevents vibration spam during smooth dragging operations
        private var lastHapticFeedbackRow: Int?
        private var lastHapticFeedbackZone: Int?
        // Cache last drop proposal to avoid oscillation that can trigger system haptics
        private var lastProposalIntent: UICollectionViewDropProposal.Intent?
        private var lastProposalIndexPath: IndexPath?
        
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
                // Use .cancel to avoid showing the slashed-circle icon
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Only accept drops from the same grid (goal/streak items)
            guard let items = session.items as? [UIDragItem] else {
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Check if all items are from the goal/streak grid
            for dragItem in items {
                if let wrapper = dragItem.localObject as? DragItemWrapper {
                    if wrapper.type != DragItemWrapper.ItemType.goalStreak {
                        // Use .cancel to suppress forbidden icon for cross-grid drags
                        return UICollectionViewDropProposal(operation: .cancel)
                    }
                } else {
                    // Legacy support for direct widget objects
                    if !(dragItem.localObject is MileStoneType) {
                        return UICollectionViewDropProposal(operation: .cancel)
                    }
                }
            }

            // Neutralize haptics when hovering over the goal card by using an unspecified intent
            if let indexPath = destinationIndexPath, indexPath.item >= 0, indexPath.item < gridModel.mileStones.count {
                let target = gridModel.mileStones[indexPath.item]
                if case .goalCard = target {
                    let intent: UICollectionViewDropProposal.Intent = .unspecified
                    // Avoid changing proposal repeatedly while staying on goal card
                    if lastProposalIntent == intent && lastProposalIndexPath == indexPath {
                        return UICollectionViewDropProposal(operation: .move, intent: intent)
                    } else {
                        lastProposalIntent = intent
                        lastProposalIndexPath = indexPath
                        return UICollectionViewDropProposal(operation: .move, intent: intent)
                    }
                }
            }

            if let destinationIndexPath = destinationIndexPath {
                // Only show drop target indicator when there's a meaningful change
                let shouldShowIndicator = shouldShowDropTargetIndicator(
                    at: destinationIndexPath,
                    in: collectionView,
                    for: session
                )
                
                if shouldShowIndicator {
                    showDropTargetIndicator(at: destinationIndexPath, in: collectionView)
                }
                // Cache proposal for non-goal targets to avoid frequent system changes
                lastProposalIntent = .insertAtDestinationIndexPath
                lastProposalIndexPath = destinationIndexPath
            }
            
            return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
        }
        
        private func shouldShowDropTargetIndicator(
            at indexPath: IndexPath,
            in collectionView: UICollectionView,
            for session: UIDropSession
        ) -> Bool {
            // Get the current drag item to determine its type
            guard let dragItem = session.items.first,
                  let wrapper = dragItem.localObject as? DragItemWrapper,
                  wrapper.type == DragItemWrapper.ItemType.goalStreak else {
                return false
            }
            
            let widget = wrapper.item as! MileStoneType
            
            // Check if this is a meaningful drop target change
            let isMeaningfulChange = isDropTargetMeaningfullyChanged(
                newIndexPath: indexPath,
                newItemType: widget
            )
            
            // If not a meaningful change, don't show indicator
            guard isMeaningfulChange else {
                return false
            }
            
            // Additional validation: Check if the drop target is in a valid logical area
            let isValidLogicalArea = isDropTargetInValidLogicalArea(
                at: indexPath,
                for: widget
            )
            
            // Only show indicator if both conditions are met
            return isValidLogicalArea
        }

        private func isDropTargetMeaningfullyChanged(
            newIndexPath: IndexPath,
            newItemType: MileStoneType
        ) -> Bool {
            let now = Date()
            
            // If this is the first drop target, it's meaningful
            guard let lastIndexPath = lastDropTargetIndexPath,
                  let lastItemType = lastDropTargetItemType else {
                updateDropTargetTracking(newIndexPath: newIndexPath, newItemType: newItemType, timestamp: now)
                return true
            }

            if let lastChangeTime = lastDropTargetChangeTime,
               now.timeIntervalSince(lastChangeTime) < DropTargetConfig.changeThreshold {
                return false
            }
            
            // Check if the target actually changed meaningfully
            let hasIndexChanged = newIndexPath.item != lastIndexPath.item
            
            // If no index change, no meaningful change
            guard hasIndexChanged else { return false }

            if DropTargetConfig.useZoneBasedDetection {
                let isZoneChange = isZoneMeaningfullyChanged(
                    from: lastIndexPath,
                    to: newIndexPath,
                    itemType: newItemType
                )
                
                if isZoneChange {
                    updateDropTargetTracking(newIndexPath: newIndexPath, newItemType: newItemType, timestamp: now)
                    return true
                }
                return false
            }

            return isIndividualCellMeaningfullyChanged(
                from: lastIndexPath,
                to: newIndexPath,
                itemType: newItemType,
                timestamp: now
            )
        }
        
        /// Zone-based detection: Groups nearby cells into zones to reduce feedback frequency
        private func isZoneMeaningfullyChanged(
            from lastIndexPath: IndexPath,
            to newIndexPath: IndexPath,
            itemType: MileStoneType
        ) -> Bool {
            let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
            let zoneSize = DropTargetConfig.zoneSize
            
            // Calculate zone coordinates (group cells into 3x3 zones for iOS Home Screen-like behavior)
            let lastZone = (lastIndexPath.item / (gridColumns * zoneSize * 2), (lastIndexPath.item % gridColumns) / zoneSize)
            let newZone = (newIndexPath.item / (gridColumns * zoneSize * 2), (newIndexPath.item % gridColumns) / zoneSize)
            
            // Only trigger if moving to a different zone
            let hasZoneChanged = lastZone != newZone
 
            if itemType == .goalCard {
                let lastMajorRow = lastIndexPath.item / (gridColumns * zoneSize * 4) // Much larger zones
                let newMajorRow = newIndexPath.item / (gridColumns * zoneSize * 4)
                let hasMajorRowChanged = lastMajorRow != newMajorRow
                
                // Only consider it meaningful if crossing major boundaries
                return hasZoneChanged || hasMajorRowChanged
            }

            let lastStreakZone = lastIndexPath.item / (gridColumns * zoneSize * 2)
            let newStreakZone = newIndexPath.item / (gridColumns * zoneSize * 2)
            let hasStreakZoneChanged = lastStreakZone != newStreakZone
            
            return hasZoneChanged || hasStreakZoneChanged
        }
        
        /// Individual cell detection with reduced sensitivity
        private func isIndividualCellMeaningfullyChanged(
            from lastIndexPath: IndexPath,
            to newIndexPath: IndexPath,
            itemType: MileStoneType,
            timestamp: Date
        ) -> Bool {
            let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
            
            // Calculate grid positions
            let lastGridPosition = (lastIndexPath.item / gridColumns, lastIndexPath.item % gridColumns)
            let newGridPosition = (newIndexPath.item / gridColumns, newIndexPath.item % gridColumns)
            
            // Calculate Manhattan distance between positions
            let rowDistance = abs(newGridPosition.0 - lastGridPosition.0)
            let colDistance = abs(newGridPosition.1 - lastGridPosition.1)
            let totalDistance = rowDistance + colDistance
            
            // Only consider it meaningful if moving at least minimum distance
            guard totalDistance >= DropTargetConfig.minimumGridPositionChange else {
                return false
            }

            if itemType == .goalCard {
                let hasRowChanged = rowDistance >= 2 // Require 2+ row change
                if hasRowChanged {
                    return true
                }
                return false
            }

            let hasSignificantPositionChange = totalDistance >= (DropTargetConfig.minimumGridPositionChange * 2) // Double the threshold
            
            if hasSignificantPositionChange {
                return true
            }
            
            return false
        }
        
        /// Additional optimization: Check if the drop target is in a valid logical area
        /// This prevents haptic feedback when hovering over invalid drop zones
        /// Now more permissive to reduce excessive feedback
        private func isDropTargetInValidLogicalArea(
            at indexPath: IndexPath,
            for itemType: MileStoneType
        ) -> Bool {
            let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
            
            // Use zone-based validation to reduce feedback frequency
            if DropTargetConfig.useZoneBasedDetection {
                return isDropTargetInValidZone(at: indexPath, for: itemType, gridColumns: gridColumns)
            }
            
            // Legacy strict validation (less restrictive now)
            switch itemType {
            case .goalCard:
                // Goal cards can be placed at row boundaries (more flexible now)
                // Allow placement in a wider range to reduce feedback
                let columnIndex = indexPath.item % gridColumns
                return columnIndex == 0 || columnIndex == 1 // Allow first two columns
                
            case .streak:
                // Streak items can be placed in any valid grid position
                let columnIndex = indexPath.item % gridColumns
                return columnIndex < gridColumns
            }
        }
        
        /// Zone-based validation: More permissive drop zones to reduce feedback
        private func isDropTargetInValidZone(
            at indexPath: IndexPath,
            for itemType: MileStoneType,
            gridColumns: Int
        ) -> Bool {
            let zoneSize = DropTargetConfig.zoneSize
            
            switch itemType {
            case .goalCard:
                // Goal cards can be placed in larger zones (reduces feedback)
                let zoneIndex = indexPath.item / (gridColumns * zoneSize)
                let columnInZone = (indexPath.item % gridColumns) / zoneSize
                
                // Allow placement in first column of any zone, or anywhere in first zone
                return columnInZone == 0 || zoneIndex == 0
                
            case .streak:
                // Streak items can be placed in any zone
                return true
            }
        }
        
        /// Updates the drop target tracking state
        private func updateDropTargetTracking(
            newIndexPath: IndexPath,
            newItemType: MileStoneType,
            timestamp: Date
        ) {
            lastDropTargetIndexPath = newIndexPath
            lastDropTargetItemType = newItemType
            lastDropTargetChangeTime = timestamp
        }

        private func showDropTargetIndicator(at indexPath: IndexPath, in collectionView: UICollectionView) {
            // Disable animations for immediate feedback
            CATransaction.begin()
            CATransaction.setDisableActions(true)            
            CATransaction.commit()
        }
        
        private func provideMinimalHapticFeedback() {
            return

        }
        
        /// Determines if the current drop target change is meaningful enough for haptic feedback
        /// Returns true ONLY for significant changes, false for everything else
        private func isMeaningfulDropTargetChange() -> Bool {
            guard let lastIndexPath = lastDropTargetIndexPath,
                  let lastItemType = lastDropTargetItemType else {
                // First drop target - this is meaningful
                return true
            }
            
            // Check if enough time has passed since last meaningful change
            let now = Date()
            if let lastChangeTime = lastDropTargetChangeTime,
               now.timeIntervalSince(lastChangeTime) < DropTargetConfig.changeThreshold {
                return false // Too soon - not meaningful
            }
            
            // For goal cards: ONLY feedback when crossing major row boundaries (4+ rows)
            if lastItemType == .goalCard {
                let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                let currentRow = lastIndexPath.item / gridColumns
                
                if let previousRow = lastHapticFeedbackRow {
                    let rowChange = abs(currentRow - previousRow)
                    // ONLY meaningful if crossing 4+ row boundaries
                    if rowChange >= 4 {
                        lastHapticFeedbackRow = currentRow
                        return true
                    } else {
                        return false // Not meaningful enough
                    }
                } else {
                    // First feedback for goal card
                    lastHapticFeedbackRow = currentRow
                    return true
                }
            }
            
            // For streak items: ONLY feedback when crossing multiple zone boundaries
            if case .streak = lastItemType {
                let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                let zoneSize = DropTargetConfig.zoneSize
                let currentZone = lastIndexPath.item / (gridColumns * zoneSize * 2) // Larger zones
                
                if let previousZone = lastHapticFeedbackZone {
                    // ONLY meaningful if crossing to a different major zone
                    if currentZone != previousZone {
                        lastHapticFeedbackZone = currentZone
                        return true
                    } else {
                        return false // Same zone - not meaningful
                    }
                } else {
                    // First feedback for streak item
                    lastHapticFeedbackZone = currentZone
                    return true
                }
            }
            
            return false // Default: not meaningful
        }
        
        /// Helper method to provide the actual haptic feedback
        /// Uses iOS Home Screen-like intensity and style
        private func provideHapticFeedback() {
            let impactFeedback = UIImpactFeedbackGenerator(style: .light)
            impactFeedback.prepare()
            
            // iOS Home Screen uses very subtle feedback (0.2-0.3 intensity)
            impactFeedback.impactOccurred(intensity: 0.25)
        }
        
        /// Provides haptic feedback ONLY when an actual drop occurs
        /// This gives user confirmation that the reorder was successful
        /// NO feedback during dragging - eliminates vibration spam completely
        private func provideDropConfirmationHapticFeedback() {
            // Use medium feedback to confirm successful drop
            let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
            impactFeedback.prepare()
            impactFeedback.impactOccurred(intensity: 0.5)
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

            provideDropConfirmationHapticFeedback()
        }

        private func calculateActualInsertionIndex(from sourceIndex: Int, to destinationIndex: Int, for widget: MileStoneType) -> Int {
            let currentModel = gridModel.mileStones
            
            // If moving to the same position, no change needed
            if sourceIndex == destinationIndex {
                return sourceIndex
            }
            
            // Determine if this is a widget (goal card) or app (streak item)
            let isWidget = widget == .goalCard
  
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
                
                // If the target is occupied by a widget, allow inserting at that index to push it down
                if validTargetIndex < currentModel.count {
                    let targetWidget = currentModel[validTargetIndex]
                    if targetWidget == .goalCard {
                        // Insert at goal card index so the goal card is pushed down
                        return validTargetIndex
                    }
                }

                return validTargetIndex
            }
        }

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

        private func performImmediateReorder(collectionView: UICollectionView, from sourceIndex: Int, to destinationIndex: Int, widget: MileStoneType) {

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
                    self.updateGridLayoutEfficiently(in: collectionView)
                    self.validateGridLayoutAfterReorder(in: collectionView)
                })
            }
            
            CATransaction.commit()

            DispatchQueue.main.async {
                collectionView.layoutIfNeeded()
 
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
            
            // Reset drop target tracking for new drag session
            resetDropTargetTracking()
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            // Re-enable animations after drag ends
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.commit()
            
            // Clear drag state
            store.state.ui.isGoalCardBeingDragged = false
            
            // Reset drop target tracking
            resetDropTargetTracking()
            
            // Reset haptic feedback tracking
            resetHapticFeedbackTracking()
        }
        
        /// Resets haptic feedback tracking to allow fresh feedback in next session
        private func resetHapticFeedbackTracking() {
            lastHapticFeedbackTime = nil
            lastHapticFeedbackRow = nil
            lastHapticFeedbackZone = nil
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
            
            // Reset drop target tracking
            resetDropTargetTracking()
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

        private func updateGridLayoutEfficiently(in collectionView: UICollectionView) {
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
        
        /// Resets the drop target tracking state
        private func resetDropTargetTracking() {
            lastDropTargetIndexPath = nil
            lastDropTargetItemType = nil
            lastDropTargetChangeTime = nil
        }
    }
}
