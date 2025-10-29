//
//  GoalStreakGridUIKitView.swift
//  meApp
//
//  Created by Lakshmi Priya on 07/08/25.
//

import SwiftUI
import UIKit

struct GoalStreakGridUIKitView: UIViewRepresentable {
    var parentView: DashboardMetricsParentView = .dashboard
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
        // Reentrancy guard to avoid SwiftUI AttributeGraph update cycles and console spam
        if coordinator.isUpdating { return }

        // Avoid reloading/invalidating layout while a drag operation is active to prevent mid-drag resizing
        if coordinator.interactiveMovingIndexPath != nil {
            coordinator.lastIsEditMode = store.state.ui.isEditMode
            // Keep system drag disabled; we use interactive movement with strict clamping
            collectionView.dragInteractionEnabled = false
            return
        }

        // Rebuild model and compare to previous for minimal updates
        let newModel = buildGridModelFromStoreState()
        let newIsEditMode = store.state.ui.isEditMode
        let newRemovedStreaks = store.state.ui.removedStreaks
        let newGoalCardRemoved = store.state.ui.isGoalCardRemoved
        let newGoalCardPosition = store.state.ui.goalCardPosition
        let newStreakGridOrder = store.state.ui.streakGridOrder

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
        let goalCardStateChanged = newGoalCardRemoved != coordinator.lastGoalCardRemoved
        let goalCardPositionChanged = newGoalCardPosition != coordinator.lastGoalCardPosition
        let streakOrderChanged = newStreakGridOrder != coordinator.lastStreakGridOrder

        // Check if this is a reset operation (all items restored and order reset)
        // Note: This check was previously stored in an unused variable, now removed for clarity

        if contentChanged || removalStateChanged || goalCardStateChanged || goalCardPositionChanged || streakOrderChanged {
            coordinator.isUpdating = true
            coordinator.gridModel = newModel
            collectionView.collectionViewLayout.invalidateLayout()
            UIView.performWithoutAnimation {
                collectionView.reloadData()
            }
            coordinator.lastRemovedStreaks = newRemovedStreaks
            coordinator.lastGoalCardRemoved = newGoalCardRemoved
            coordinator.lastGoalCardPosition = newGoalCardPosition
            coordinator.lastStreakGridOrder = newStreakGridOrder
            
            // Force collection view to recalculate its intrinsic content size
            collectionView.layoutIfNeeded()
            if let customCollectionView = collectionView as? CustomCollectionView {
                customCollectionView.invalidateIntrinsicContentSize()
            }
            coordinator.isUpdating = false
        } else {
            // Only wiggle state might have changed; update visible cells without reload
            if newIsEditMode != coordinator.lastIsEditMode {
                coordinator.isUpdating = true
                UIView.performWithoutAnimation {
                    collectionView.reloadData()
                }
                coordinator.isUpdating = false
            }
        }

        coordinator.lastIsEditMode = newIsEditMode
        
        // Keep system drag disabled; we use interactive movement with strict clamping
        collectionView.dragInteractionEnabled = false
    }
    
    func makeCoordinator() -> Coordinator {
        let c = Coordinator(store: store, gridModel: buildGridModelFromStoreState())
        c.parentView = parentView
        return c
    }
    
    // MARK: - Private Methods
    
    /// Creates the collection view layout with proper spacing and insets
    private func createLayout() -> UICollectionViewFlowLayout {
        let layout = LeadingAlignedFlowLayout()
        layout.minimumInteritemSpacing = 16         // gap between columns
        layout.minimumLineSpacing = 32              // gap between rows
        layout.sectionInset = UIEdgeInsets(top: 16, left: 16, bottom: 32, right: 16) // fine-tuned bottom margin for last row
        layout.estimatedItemSize = .zero            // ensure fixed, non-estimated sizing
        return layout
    }
    
    private struct DropTargetConfig {
        /// Minimum time between drop target changes to prevent excessive haptic feedback
        static let changeThreshold: TimeInterval = 2.0
        /// Minimum grid position change to trigger haptic feedback
        static let minimumGridPositionChange: Int = 8
        /// Whether to use zone-based detection instead of individual cell detection
        static let useZoneBasedDetection = true
        /// Zone size for grouping nearby cells (reduces feedback frequency)
        static let zoneSize: Int = 6
        /// Minimum time between haptic feedback events to prevent vibration spam
        static let hapticFeedbackThreshold: TimeInterval = 2.5
    }
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: UICollectionViewFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.hideDragPlatter = true // hide system drag preview platter
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakCardCell.self, forCellWithReuseIdentifier: "StreakCardCell")
        collectionView.reorderingCadence = .immediate
        // Allow the lifted item overlay to render beyond bounds without clipping
        collectionView.clipsToBounds = false
        
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
        collectionView.dataSource = context.coordinator
        collectionView.delegate = context.coordinator
        // Disable system drag/drop; use interactive movement with strict clamping
        collectionView.dragDelegate = nil
        collectionView.dropDelegate = nil
        collectionView.dragInteractionEnabled = false
        
        // Set up boundary detection for custom collection view
        if let customCollectionView = collectionView as? CustomCollectionView {
            customCollectionView.boundaryDetector = context.coordinator.boundaryDetector
        }

        // Add long-press gesture for interactive movement
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(context.coordinator.handleLongPress(_:)))
        longPress.minimumPressDuration = 0.15
        collectionView.addGestureRecognizer(longPress)
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

    if isEditMode {
        if !isGoalCardRemoved {
            let streakCount = nonRemovedStreaks.count
            let columns = DevicePlatform.isTablet ? 4 : 2
            let hasRemovedStreaks = !removedStreaks.isEmpty
            
            
            if streakCount == 0 {
                // No streaks, just add goal card
                widgets.append(.goalCard)
            } else if hasRemovedStreaks {
                var goalCardAdded = false
                let maxPosition = streakCount
                
                // Respect the user's exact goal card position when streaks are removed
                for i in 0...maxPosition {
                    if i == goalCardPos && !goalCardAdded {
                        widgets.append(.goalCard)
                        goalCardAdded = true
                    }
                    if i < streakCount {
                        widgets.append(.streak(nonRemovedStreaks[i]))
                    }
                }
                
                // Fallback: if goal card wasn't added, add it at the end
                if !goalCardAdded {
                    widgets.append(.goalCard)
                }
            } else {
                // When all streaks are present in edit mode: Apply even position restriction
                let maxPosition = streakCount
                let clampedGoalCardPos = min(goalCardPos, maxPosition)
                
                // Apply even position restriction when all streaks are present
                let adjustedGoalCardPos = (clampedGoalCardPos % 2 == 0) ? clampedGoalCardPos : clampedGoalCardPos - 1
                let finalGoalCardPos = max(0, adjustedGoalCardPos)
                
                
                var goalCardAdded = false
                
                for i in 0...maxPosition {
                    if i == finalGoalCardPos {
                        widgets.append(.goalCard)
                        goalCardAdded = true
                    }
                    if i < streakCount {
                        widgets.append(.streak(nonRemovedStreaks[i]))
                    }
                }
                
                // Fallback: if goal card wasn't added, add it at the end
                if !goalCardAdded {
                    widgets.append(.goalCard)
                }
            }
        } else {
            widgets = nonRemovedStreaks.map { .streak($0) }
        }

        for streak in removedStreaks {
            widgets.append(.streak(streak))
        }

        if isGoalCardRemoved {
            widgets.append(.goalCard)
        }
    } else {

        if !isGoalCardRemoved {
            let streakCount = nonRemovedStreaks.count
            let columns = DevicePlatform.isTablet ? 4 : 2
            let hasRemovedStreaks = !removedStreaks.isEmpty
            
            
            if streakCount == 0 {
                // No streaks, just add goal card
                widgets.append(.goalCard)
            } else if hasRemovedStreaks {
                // When streaks are removed: Respect user's exact positioning
                // Build the grid based on the saved order from the store
                var goalCardAdded = false
                let maxPosition = streakCount
                
                // Respect the user's exact goal card position when streaks are removed
                for i in 0...maxPosition {
                    if i == goalCardPos && !goalCardAdded {
                        widgets.append(.goalCard)
                        goalCardAdded = true
                    }
                    if i < streakCount {
                        widgets.append(.streak(nonRemovedStreaks[i]))
                    }
                }
                
                // Fallback: if goal card wasn't added, add it at the end
                if !goalCardAdded {
                    widgets.append(.goalCard)
                }
            } else {
                // When all streaks are present (non-edit mode): force row-start placement
                // If the stored position is 1, snap to 0. Generally, enforce start of row.
                let maxPosition = streakCount
                let clampedGoalCardPos = min(goalCardPos, maxPosition)
                let gridColumns = DevicePlatform.isTablet ? 4 : 2
                let rowStart = (clampedGoalCardPos / gridColumns) * gridColumns

                var goalCardAdded = false
                for i in 0...maxPosition {
                    if i == rowStart {
                        widgets.append(.goalCard)
                        goalCardAdded = true
                    }
                    if i < streakCount {
                        widgets.append(.streak(nonRemovedStreaks[i]))
                    }
                }
                if !goalCardAdded { widgets.append(.goalCard) }
            }
        } else {
            widgets = nonRemovedStreaks.map { .streak($0) }
        }
    }
        
        // Create the grid model and apply row-wise reordering
        var gridModel = MileStoneGridModel(mileStones: widgets)
        let spanCount = DevicePlatform.isTablet ? 4 : 2
        let hasRemovedStreaks = !removedStreaks.isEmpty
        gridModel.reorderGrid(spanCount: spanCount, hasRemovedStreaks: hasRemovedStreaks)
        
        return gridModel
    }
    
    // MARK: - Coordinator
    
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {
        // Reentrancy guard for updateUIView to avoid AttributeGraph cycles
        var isUpdating: Bool = false
        var lastIsEditMode: Bool = false
        var lastRemovedStreaks: Set<String> = []
        var lastGoalCardRemoved: Bool = false
        var lastGoalCardPosition: Int = 0
        var lastStreakGridOrder: [String] = []
        var store: DashboardStore
        var gridModel: MileStoneGridModel
        var parentView: DashboardMetricsParentView = .dashboard

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
        
        // Track dropped items for overlay restoration after layout rerender
        private var lastDroppedStreakId: String?
        private var lastDroppedGoalCard: Bool = false
        private var isAwaitingDropEnd: Bool = false
        
        // Interactive movement tracking to preserve shape/size and clamp by full frame
        public var interactiveMovingIndexPath: IndexPath?
        private var interactiveMovingItemSize: CGSize = .zero
        private var lastBoundaryHapticTime: Date?
        // Preserve identity of dragged item so size doesn't morph when index changes
        private var draggedStreakId: String?
        private var isDraggingGoalCard: Bool = false
        // Track the original index of the moving item so we can simulate the live layout mapping
        private var originalMovingIndex: Int?
        
        // MARK: - Boundary Detection Properties
        public var boundaryDetector: GridBoundaryDetector
        
        // MARK: - Boundary Detection for Active vs Removed Items
        
        /// Returns the number of non-removed (active) items that can be reordered
        private var activeItemsCount: Int {
            let activeStreaks = gridModel.mileStones.filter { widget in
                switch widget {
                case .goalCard:
                    return !store.state.ui.isGoalCardRemoved
                case .streak(let item):
                    return !store.isStreakRemoved(item.label)
                }
            }
            return activeStreaks.count
        }
        
        /// Returns the first index of removed items (where dropping should be prevented)
        private var firstRemovedIndex: Int {
            return activeItemsCount
        }
        
        /// Returns true if the item at the given index is removed
        private func isItemRemoved(at index: Int) -> Bool {
            guard index < gridModel.mileStones.count else { return false }
            let widget = gridModel.mileStones[index]
            switch widget {
            case .goalCard:
                return store.state.ui.isGoalCardRemoved
            case .streak(let item):
                return store.isStreakRemoved(item.label)
            }
        }
        
        init(store: DashboardStore, gridModel: MileStoneGridModel) {
            self.store = store
            self.gridModel = gridModel
            self.boundaryDetector = GridBoundaryDetector()
        }
        
        // MARK: - Boundary Detection Methods
        
        /// Configures boundary constraints based on current grid layout
        /// Creates STRICT boundaries to prevent items from even touching the divider
        private func configureBoundaryConstraints(for collectionView: UICollectionView) {
            // Calculate the actual content height of the goal/streak grid
            let contentSize = collectionView.collectionViewLayout.collectionViewContentSize
            let contentInsets = collectionView.contentInset
            let actualGridHeight = contentSize.height + contentInsets.top + contentInsets.bottom
            
            // Add optimal bottom slack during interactive movement so last row has sufficient space to move
            // Use balanced slack: 1.3x item height + row spacing + bottom padding
            let extraBottomSlack: CGFloat = {
                guard interactiveMovingItemSize != .zero else { 
                    // Fallback calculation when item size is not available
                    let rowSpacing: CGFloat = 32
                    let bottomInset: CGFloat = 32 // Use the fine-tuned bottom inset
                    let estimatedItemHeight: CGFloat = 70 // Typical streak item height
                    return estimatedItemHeight * 1.3 + rowSpacing + bottomInset
                }
                let rowSpacing: CGFloat = 32
                let bottomInset: CGFloat = 32 // Use the fine-tuned bottom inset
                return interactiveMovingItemSize.height * 1.3 + rowSpacing + bottomInset
            }()
            
            // Use full grid height with generous bottom slack; block only a compact strip above the divider via exclude zone
            let dividerY = actualGridHeight
            boundaryDetector.updateGoalStreakConstraints(gridHeight: actualGridHeight + extraBottomSlack, dividerY: dividerY)
            
            
        }

        // MARK: - Interactive Movement (Strictly Clamped)
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
                guard let collectionView = gesture.view as? UICollectionView else {
                    return
                }
            guard store.state.ui.isEditMode else { 
                return 
            }

            switch gesture.state {
            case .began:
                let location = gesture.location(in: collectionView)
                if let indexPath = collectionView.indexPathForItem(at: location), indexPath.item < firstRemovedIndex {
                    configureBoundaryConstraints(for: collectionView)
                    boundaryDetector.updateGridBounds(for: collectionView)
                    interactiveMovingIndexPath = indexPath
                    // Capture current item size to prevent shape change during drag
                    if let cell = collectionView.cellForItem(at: indexPath) {
                        interactiveMovingItemSize = cell.bounds.size
                        
                        if let streakCell = cell as? StreakCardCell {
                            streakCell.updateDragState(true)
                        } else if let goalCell = cell as? GoalCardCell {
                            goalCell.updateDragState(true)
                        }
                    } else if let attrs = collectionView.layoutAttributesForItem(at: indexPath) {
                        interactiveMovingItemSize = attrs.bounds.size
                    } else {
                        // Fallback compute from model
                        let widget = gridModel.mileStones[indexPath.item]
                        let contentWidth = collectionView.bounds.width - 32 // section insets left+right
                        let interItemSpacing: CGFloat = 16
                        switch widget {
                        case .goalCard:
                            interactiveMovingItemSize = CGSize(width: contentWidth, height: 120)
                        case .streak:
                            let columns: CGFloat = DevicePlatform.isTablet ? 4 : 2
                            let itemWidth = (contentWidth - interItemSpacing * (columns - 1)) / columns
                            interactiveMovingItemSize = CGSize(width: itemWidth, height: 70)
                        }
                    }
                    // Track dragged item identity to preserve size regardless of index changes
                    let widget = gridModel.mileStones[indexPath.item]
                    switch widget {
                    case .goalCard:
                        isDraggingGoalCard = true
                        draggedStreakId = nil
                    case .streak(let item):
                        isDraggingGoalCard = false
                        draggedStreakId = item.id.uuidString
                    }
                    // Capture the original index for virtual mapping during sizing
                    originalMovingIndex = indexPath.item
                    collectionView.beginInteractiveMovementForItem(at: indexPath)
                }
            case .changed:
                // Clamp the movement strictly away from divider/metric grid
                let location = gesture.location(in: collectionView)
                let clampedPoint = boundaryDetector.constrainDragLocation(location, in: collectionView)
                // Further clamp by full frame so item never clips at edges
                var targetCenter = clampedPoint
                if interactiveMovingItemSize != .zero {
                    // Build frame centered at clampedPoint in collection coords
                    let frame = CGRect(
                        x: clampedPoint.x - interactiveMovingItemSize.width / 2,
                        y: clampedPoint.y - interactiveMovingItemSize.height / 2,
                        width: interactiveMovingItemSize.width,
                        height: interactiveMovingItemSize.height
                    )
                    // Constrain using detector (returns frame in collection coords after conversion)
                    let constrainedFrame = boundaryDetector.constrainDragFrame(frame, in: collectionView)
                    targetCenter = CGPoint(x: constrainedFrame.midX, y: constrainedFrame.midY)
                }
                // Provide subtle haptic when hitting boundary (throttled)
                if abs(targetCenter.y - location.y) > 0.5 {
                    let now = Date()
                    if lastBoundaryHapticTime == nil || now.timeIntervalSince(lastBoundaryHapticTime!) > 0.25 {
                        boundaryDetector.provideBoundaryFeedback()
                        lastBoundaryHapticTime = now
                    }
                }
                collectionView.updateInteractiveMovementTargetPosition(targetCenter)
            case .ended:
                collectionView.endInteractiveMovement()
                // Recompute bounds after drop as height/order may have changed
                configureBoundaryConstraints(for: collectionView)
                boundaryDetector.updateGridBounds(for: collectionView)
                collectionView.collectionViewLayout.invalidateLayout()
                collectionView.layoutIfNeeded()
                // Hard refresh visible items to clear any cached sizes from interactive movement
                UIView.performWithoutAnimation {
                    let visible = collectionView.indexPathsForVisibleItems
                    if !visible.isEmpty { collectionView.reloadItems(at: visible) }
                    collectionView.layoutIfNeeded()
                }
                if let custom = collectionView as? CustomCollectionView {
                    custom.invalidateIntrinsicContentSize()
                }
                interactiveMovingIndexPath = nil
                interactiveMovingItemSize = .zero
                isDraggingGoalCard = false
                draggedStreakId = nil
                originalMovingIndex = nil
                // Clear drag state highlight from all visible cells
                for cell in collectionView.visibleCells {
                    if let streakCell = cell as? StreakCardCell {
                        streakCell.updateDragState(false)
                        streakCell.setOverlaySuppressed(false)
                        streakCell.clearAllShadowEffects()
                    } else if let goalCell = cell as? GoalCardCell {
                        goalCell.updateDragState(false)
                        goalCell.setOverlaySuppressed(false)
                        goalCell.clearAllShadowEffects()
                    }
                }
            default:
                collectionView.cancelInteractiveMovement()
                configureBoundaryConstraints(for: collectionView)
                boundaryDetector.updateGridBounds(for: collectionView)
                collectionView.collectionViewLayout.invalidateLayout()
                UIView.performWithoutAnimation {
                    let visible = collectionView.indexPathsForVisibleItems
                    if !visible.isEmpty { collectionView.reloadItems(at: visible) }
                    collectionView.layoutIfNeeded()
                }
                interactiveMovingIndexPath = nil
                interactiveMovingItemSize = .zero
                isDraggingGoalCard = false
                draggedStreakId = nil
                originalMovingIndex = nil
                // Clear drag state highlight from all visible cells on cancel
                for cell in collectionView.visibleCells {
                    if let streakCell = cell as? StreakCardCell {
                        streakCell.updateDragState(false)
                        streakCell.setOverlaySuppressed(false)
                        streakCell.clearAllShadowEffects()
                    } else if let goalCell = cell as? GoalCardCell {
                        goalCell.updateDragState(false)
                        goalCell.setOverlaySuppressed(false)
                        goalCell.clearAllShadowEffects()
                    }
                }
            }
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
                cell.isRemoved = store.state.ui.isGoalCardRemoved
                return cell
            case .streak(let item):
                let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "StreakCardCell", for: indexPath) as! StreakCardCell
                cell.parentView = parentView
                cell.configure(
                    with: item, 
                    store: store
                )
                cell.isWiggling = store.state.ui.isEditMode
                cell.rowIndex = indexPath.item
                cell.isRemoved = store.isStreakRemoved(item.label)
                return cell
            }
        }
        
        // MARK: - UICollectionViewDelegateFlowLayout
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            // Preserve shape/size of the dragged item regardless of its index during reordering
            if interactiveMovingItemSize != .zero {
                // If this cell is the actively moving item, always lock to the captured size
                if let moving = interactiveMovingIndexPath, moving == indexPath {
                    return interactiveMovingItemSize
                }
                // Never shrink the actual goal card while dragging a streak.
                // Determine goal card presence using a virtual mapping that simulates the in-flight move
                if !isDraggingGoalCard {
                    // Build a virtual array representing the current visual order while dragging
                    var virtual = gridModel.mileStones
                    if let from = originalMovingIndex, let to = interactiveMovingIndexPath?.item,
                       from >= 0, from < virtual.count, to >= 0, to < virtual.count {
                        let moving = virtual.remove(at: from)
                        virtual.insert(moving, at: to)
                    }
                    if indexPath.item >= 0, indexPath.item < virtual.count {
                        let candidate = virtual[indexPath.item]
                        if case .goalCard = candidate {
                            let contentWidth = collectionView.bounds.width - 32
                            return CGSize(width: contentWidth, height: 120)
                        }
                    }
                }
                // While dragging goal card, ensure only the moving item is full-width; others size as streaks
                if isDraggingGoalCard {
                    let contentWidth = collectionView.bounds.width - 32
                    let interItemSpacing: CGFloat = 16
                    let columns: CGFloat = DevicePlatform.isTablet ? 4 : 2
                    let itemWidth = (contentWidth - interItemSpacing * (columns - 1)) / columns
                    return CGSize(width: itemWidth, height: 70)
                }
                // While dragging a streak, keep ONLY the actual goal card full-width; all other non-moving
                // cells remain streak-sized. This prevents any temporary expansion of streak cells when
                // interchanging across the goal card's position.
                if draggedStreakId != nil {
                    let contentWidth = collectionView.bounds.width - 32
                    let interItemSpacing: CGFloat = 16
                    let columns: CGFloat = DevicePlatform.isTablet ? 4 : 2
                    let itemWidth = (contentWidth - interItemSpacing * (columns - 1)) / columns
                    // Use the virtual mapping so goal card stays full-width wherever it visually sits
                    var virtual = gridModel.mileStones
                    if let from = originalMovingIndex, let to = interactiveMovingIndexPath?.item,
                       from >= 0, from < virtual.count, to >= 0, to < virtual.count {
                        let moving = virtual.remove(at: from)
                        virtual.insert(moving, at: to)
                    }
                    if indexPath.item >= 0, indexPath.item < virtual.count {
                        let candidate = virtual[indexPath.item]
                        if case .goalCard = candidate {
                            return CGSize(width: contentWidth, height: 120)
                        }
                    }
                    // All other non-moving cells render as streaks
                    return CGSize(width: itemWidth, height: 70)
                }

                let widget = gridModel.mileStones[indexPath.item]
                switch widget {
                case .goalCard:
                    if isDraggingGoalCard { return interactiveMovingItemSize }
                case .streak(let item):
                    if let draggedId = draggedStreakId, draggedId == item.id.uuidString {
                        return interactiveMovingItemSize
                    }
                }
            }
            let widget = gridModel.mileStones[indexPath.item]
            let contentWidth = collectionView.bounds.width - 32
            let interItemSpacing: CGFloat = 16

            switch widget {
            case .goalCard:
                return CGSize(width: contentWidth, height: 120)
            case .streak:
                let columns: CGFloat = DevicePlatform.isTablet ? 4 : 2
                let itemWidth = (contentWidth - interItemSpacing * (columns - 1)) / columns
                return CGSize(width: itemWidth, height: 70)
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, insetForSectionAt section: Int) -> UIEdgeInsets {
            let topInset: CGFloat = store.state.ui.isGoalCardRemoved ? 32.0 : 16.0
            return UIEdgeInsets(top: topInset, left: 16.0, bottom: 32.0, right: 16.0) // fine-tuned bottom inset for last row
        }
        
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumLineSpacingForSectionAt section: Int) -> CGFloat {
            // Vertical gap between rows, including streak rows and goal card rows
            return 32
        }
        
        // MARK: - Drag & Drop
        
        func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
            // Only allow moving items that are non-removed (active) items
            return store.state.ui.isEditMode && indexPath.item < firstRemovedIndex
        }
        
        func collectionView(_ collectionView: UICollectionView, targetIndexPathForMoveFromItemAt originalIndexPath: IndexPath, toProposedIndexPath proposedIndexPath: IndexPath) -> IndexPath {
            let maxValidIndex = firstRemovedIndex - 1
            
            // Prevent any moves to/from removed item indices
            if originalIndexPath.item >= firstRemovedIndex || proposedIndexPath.item >= firstRemovedIndex {
                interactiveMovingIndexPath = originalIndexPath
                return originalIndexPath // Return original position to cancel the move
            }
            
            // Get the widget being moved
            let widget = gridModel.mileStones[originalIndexPath.item]
            
            switch widget {
            case .goalCard:
                // Goal card positioning logic based on streak removal state
                let hasRemovedStreaks = !store.state.ui.removedStreaks.isEmpty
                let allStreaksPresent = isAllStreaksPresent()
                          
                if hasRemovedStreaks {
                    // When streaks are removed, allow flexible positioning but clamp to valid range
                    let remainingStreakCount = gridModel.mileStones.compactMap { widget -> String? in
                        switch widget {
                        case .goalCard: return nil
                        case .streak(let streakItem):
                            return store.isStreakRemoved(streakItem.label) ? nil : streakItem.label
                        }
                    }.count
                    
                    let maxValidPosition = remainingStreakCount
                    let clampedPosition = min(proposedIndexPath.item, maxValidPosition)
                    
                    let snapped = IndexPath(item: clampedPosition, section: proposedIndexPath.section)
                    interactiveMovingIndexPath = snapped
                    return snapped
                } else {
                    // When all streaks are present, enforce row boundaries and handle adjacent-drop-forward rule
                    let columns: Int = DevicePlatform.isTablet ? 4 : 2
                    let sourceRowStart = (originalIndexPath.item / columns) * columns
                    let targetRow = proposedIndexPath.item / columns
                    let rowStartIndex = targetRow * columns
                    let endOfSourceRow = sourceRowStart + (columns - 1)
                    
                  
                    // Special case: if goal card is at row start and user drops on the adjacent slot in the same row,
                    // snap to the start of the NEXT row (e.g., 0->1 => 2, 2->3 => 4, 4->5 => 6)
                    if originalIndexPath.item == sourceRowStart && proposedIndexPath.item == endOfSourceRow {
                        let nextRowStart = min(sourceRowStart + columns, maxValidIndex)
                        let snapped = IndexPath(item: nextRowStart, section: proposedIndexPath.section)
                        interactiveMovingIndexPath = snapped
                        return snapped
                    }
                    
                    // Default: snap to the start of the proposed row
                    let snapped = IndexPath(item: min(rowStartIndex, maxValidIndex), section: proposedIndexPath.section)
                    interactiveMovingIndexPath = snapped
                    return snapped
                }
                
            case .streak:
                // Find goal card position
                let goalCardIndex = gridModel.mileStones.firstIndex { widget in
                    if case .goalCard = widget { return true }
                    return false
                }
                
                // If trying to move to goal card position, adjust the target
                if let goalPos = goalCardIndex, proposedIndexPath.item == goalPos {
                    // If moving from before goal card, place before it
                    if originalIndexPath.item < goalPos {
                        let snapped = IndexPath(item: max(0, goalPos - 1), section: proposedIndexPath.section)
                        interactiveMovingIndexPath = snapped
                        return snapped
                    } else {
                        // If moving from after goal card, place after it
                        let snapped = IndexPath(item: min(maxValidIndex, goalPos + 1), section: proposedIndexPath.section)
                        interactiveMovingIndexPath = snapped
                        return snapped
                    }
                }

                // If crossing the goal card row, snap to the nearest valid row start on that side
                if let goalPos = goalCardIndex {
                    // Crossing from above to below the goal card
                    if originalIndexPath.item < goalPos && proposedIndexPath.item > goalPos {
                        let snapped = IndexPath(item: min(maxValidIndex, goalPos + 1), section: proposedIndexPath.section)
                        interactiveMovingIndexPath = snapped
                        return snapped
                    }
                    // Crossing from below to above the goal card
                    if originalIndexPath.item > goalPos && proposedIndexPath.item < goalPos {
                        let columns: Int = DevicePlatform.isTablet ? 4 : 2
                        let snappedIndex = max(0, goalPos - columns)
                        let snapped = IndexPath(item: snappedIndex, section: proposedIndexPath.section)
                        interactiveMovingIndexPath = snapped
                        return snapped
                    }
                }
            }
            
            // Ensure proposed destination is within valid range (0 to maxValidIndex)
            if proposedIndexPath.item < 0 {
                let snapped = IndexPath(item: 0, section: proposedIndexPath.section)
                interactiveMovingIndexPath = snapped
                return snapped
            } else if proposedIndexPath.item >= firstRemovedIndex {
                let snapped = IndexPath(item: maxValidIndex, section: proposedIndexPath.section)
                interactiveMovingIndexPath = snapped
                return snapped
            }
            
            interactiveMovingIndexPath = proposedIndexPath
            return proposedIndexPath
        }
        
        func collectionView(_ collectionView: UICollectionView, moveItemAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) {
            // Update the underlying model to match the interactive movement
            guard sourceIndexPath.item < firstRemovedIndex && destinationIndexPath.item < firstRemovedIndex else { 
                return 
            }
            
            let sourceIndex = sourceIndexPath.item
            var destinationIndex = destinationIndexPath.item
            
            // Immediate neighbor swap across goal card (keep goal card fixed)
            if let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                let isNeighborSwap = (sourceIndex == goalPos - 1 && destinationIndex == goalPos + 1)
                || (sourceIndex == goalPos + 1 && destinationIndex == goalPos - 1)
                if isNeighborSwap {
                    gridModel.moveWidget(from: sourceIndex, to: destinationIndex)
                    persistGridOrderToStore()
                    return
                }
            }

            // Debug: Show what's at each position
            for (index, widget) in gridModel.mileStones.enumerated() {
                switch widget {
                case .goalCard:
                    break
                case .streak(let item):
                    break
                }
            }
            
            // Get the widget being moved
            let movedWidget = gridModel.mileStones[sourceIndex]
            
            // Check for special case
            if case .goalCard = movedWidget {
                let allStreaksPresent = isAllStreaksPresent()
                let hasRemovedStreaks = !store.state.ui.removedStreaks.isEmpty
                
                
                
                if allStreaksPresent && !hasRemovedStreaks {
                    // Enforce row-start placement when all streaks are present (works for iPhone/iPad)
                    let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                    let sourceRowStart = (sourceIndex / gridColumns) * gridColumns
                    let endOfSourceRow = sourceRowStart + (gridColumns - 1)
                    let maxValidIndex = firstRemovedIndex - 1
                    
                    // Adjacent-drop-forward rule: if dropping from row start to the adjacent slot in same row,
                    // push the goal card to the start of the next row
                    if sourceIndex == sourceRowStart && destinationIndex == endOfSourceRow {
                        let nextRowStart = min(sourceRowStart + gridColumns, maxValidIndex)
                        
                        gridModel.moveWidget(from: sourceIndex, to: nextRowStart)
                    } else {
                        let rowStart = (destinationIndex / gridColumns) * gridColumns
                        
                        gridModel.moveWidget(from: sourceIndex, to: rowStart)
                    }
                    // Final normalization: if goal card still ends on an odd index (1 or 3 for 2-column), snap appropriately
                    if let finalGoalIndex = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                        let col = finalGoalIndex % gridColumns
                        if col != 0 {
                            let finalRowStart = (finalGoalIndex / gridColumns) * gridColumns
                            // If move was adjacent-forward (from row start to adjacent), push to next row-start; else snap back to row-start
                            if sourceIndex == finalRowStart && finalGoalIndex == finalRowStart + 1 {
                                let nextRowStart = min(finalRowStart + gridColumns, maxValidIndex)
                                if nextRowStart != finalGoalIndex {
                                    
                                    gridModel.moveWidget(from: finalGoalIndex, to: nextRowStart)
                                }
                            } else {
                                if finalRowStart != finalGoalIndex {
                                    
                                    gridModel.moveWidget(from: finalGoalIndex, to: finalRowStart)
                                }
                            }
                        }
                    }
                } else if hasRemovedStreaks {
                    // When streaks are removed, allow flexible positioning but ensure valid placement
                    // Calculate the maximum valid position based on remaining streaks
                    let remainingStreakCount = gridModel.mileStones.compactMap { widget -> String? in
                        switch widget {
                        case .goalCard: return nil
                        case .streak(let streakItem):
                            return store.isStreakRemoved(streakItem.label) ? nil : streakItem.label
                        }
                    }.count
                    
                    let maxValidPosition = remainingStreakCount
                    let clampedDestination = min(destinationIndex, maxValidPosition)
                    
                    gridModel.moveWidget(from: sourceIndex, to: clampedDestination)
                } else {
                    // Fallback: keep row-start alignment for safety
                    let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                    let rowStart = (destinationIndex / gridColumns) * gridColumns
                    gridModel.moveWidget(from: sourceIndex, to: rowStart)
                }
            } else {
                // For streak moves, avoid mid-drag type morphing across the goal card by snapping
                // crossing moves to the nearest row start on the destination side
                if let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                    let maxValidIndex = firstRemovedIndex - 1
                    if sourceIndex < goalPos && destinationIndex > goalPos {
                        destinationIndex = min(maxValidIndex, goalPos + 1)
                    } else if sourceIndex > goalPos && destinationIndex < goalPos {
                        let columns: Int = DevicePlatform.isTablet ? 4 : 2
                        destinationIndex = max(0, goalPos - columns)
                    }
                }
                gridModel.moveWidget(from: sourceIndex, to: destinationIndex)
            }
            
            persistGridOrderToStore()
        }
        
        func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
            guard store.state.ui.isEditMode else { 
                return [] 
            }
            
            // Only allow dragging of non-removed (active) items
            if isItemRemoved(at: indexPath.item) || indexPath.item >= firstRemovedIndex {
                return [] // Return empty array to prevent drag of removed items
            }
            
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
            // Store source index path for preview targeting like Metric grid
            session.localContext = indexPath
            
            return [dragItem]
        }
        
        func collectionView(_ collectionView: UICollectionView, dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let params = UIDragPreviewParameters()
            params.backgroundColor = .clear
            if let cell = collectionView.cellForItem(at: indexPath) {
                params.visiblePath = UIBezierPath(roundedRect: cell.bounds, cornerRadius: .radiusSM)
            }
            return params
        }

        func collectionView(_ collectionView: UICollectionView,
                            dragPreviewForLiftingItem item: UIDragItem,
                            session: UIDragSession) -> UITargetedDragPreview? {
            // If we stored the index path, prefer it to resolve the cell fast
            if let indexPath = session.localContext as? IndexPath,
               let cell = collectionView.cellForItem(at: indexPath) {
                let previewView: UIView
                if let streakCell = cell as? StreakCardCell {
                    previewView = streakCell.snapshotForPreview()
                } else if let goalCell = cell as? GoalCardCell {
                    previewView = goalCell.snapshotForPreview()
                } else {
                    previewView = cell.snapshotView(afterScreenUpdates: true) ?? UIView(frame: cell.bounds)
                }
                let params = UIDragPreviewParameters()
                params.backgroundColor = .clear
                // Match Metric grid: build path from previewView bounds and use .radiusSM corner radius
                params.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: .radiusSM)

                // Match Metric grid: use configured preview scale for consistency
                let scale = DashboardConstants.UI.dragPreviewScale

                let target = UIDragPreviewTarget(
                    container: collectionView,
                    center: cell.center,
                    transform: CGAffineTransform(scaleX: scale, y: scale)
                )
                return UITargetedDragPreview(view: previewView, parameters: params, target: target)
            }

            // Fallback: resolve the dragged milestone from localObject and find its cell
            let milestone: MileStoneType?
            if let wrapper = item.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.goalStreak {
                milestone = wrapper.item as? MileStoneType
            } else if let direct = item.localObject as? MileStoneType {
                milestone = direct
            } else {
                milestone = nil
            }

            if let milestone = milestone,
               let idx = gridModel.mileStones.firstIndex(of: milestone) {
                let ip = IndexPath(item: idx, section: 0)
                if let cell = collectionView.cellForItem(at: ip) {
                    let previewView: UIView
                    if let streakCell = cell as? StreakCardCell {
                        previewView = streakCell.snapshotForPreview()
                    } else if let goalCell = cell as? GoalCardCell {
                        previewView = goalCell.snapshotForPreview()
                    } else {
                        previewView = cell.snapshotView(afterScreenUpdates: true) ?? UIView(frame: cell.bounds)
                    }
                    let params = UIDragPreviewParameters()
                    params.backgroundColor = .clear
                    params.visiblePath = UIBezierPath(roundedRect: previewView.bounds, cornerRadius: .radiusSM)

                    // Match Metric grid: use configured preview scale for consistency
                    let scale = DashboardConstants.UI.dragPreviewScale
                    let target = UIDragPreviewTarget(
                        container: collectionView,
                        center: cell.center,
                        transform: CGAffineTransform(scaleX: scale, y: scale)
                    )
                    return UITargetedDragPreview(view: previewView, parameters: params, target: target)
                }
            }
            return nil
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
            guard store.state.ui.isEditMode else {
                // Use .cancel to avoid showing the slashed-circle icon
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Configure strict boundary constraints first
            configureBoundaryConstraints(for: collectionView)
            
            // Get current drag location and apply STRICT constraints
            let dropLocation = session.location(in: collectionView)
            let constrainedLocation = boundaryDetector.constrainDragLocation(dropLocation, in: collectionView)
            
            // Check if the location needed to be constrained (STRICT boundary enforcement)
            let isLocationConstrained = !dropLocation.equalTo(constrainedLocation)
            
            if isLocationConstrained {
                // Provide immediate haptic feedback when hitting STRICT boundary
                boundaryDetector.provideBoundaryFeedback()
                
                // Use .forbidden to show immediate "not allowed" feedback
                // This prevents any false hope of being able to drop in forbidden areas
                return UICollectionViewDropProposal(operation: .forbidden)
            }
            
            // Additional check: ensure we're not in any exclude zones
            let canDragAtLocation = boundaryDetector.canDragAtLocation(constrainedLocation, in: collectionView)
            
            if !canDragAtLocation {
                // Double-check with haptic feedback for exclude zones
                boundaryDetector.provideBoundaryFeedback()
                return UICollectionViewDropProposal(operation: .forbidden)
            }
            
            // Only accept drops from the same grid (goal/streak items)
            let items = session.items
            
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

            // Completely prevent any drops on removed items
            // Use .cancel instead of .forbidden to avoid slashed circle visual effect
            if let destinationIndexPath = destinationIndexPath, destinationIndexPath.item >= firstRemovedIndex {
                return UICollectionViewDropProposal(operation: .cancel)
            }
            
            // Also check if destination would cause issues - only allow drops on active items
            guard let destinationIndexPath = destinationIndexPath, 
                  destinationIndexPath.item >= 0 && destinationIndexPath.item < firstRemovedIndex else {
                return UICollectionViewDropProposal(operation: .cancel)
            }

            // Neutralize haptics when hovering over the goal card by using an unspecified intent
            if destinationIndexPath.item >= 0, destinationIndexPath.item < gridModel.mileStones.count {
                let target = gridModel.mileStones[destinationIndexPath.item]
                if case .goalCard = target {
                    let intent: UICollectionViewDropProposal.Intent = .unspecified
                    // Avoid changing proposal repeatedly while staying on goal card
                    if lastProposalIntent == intent && lastProposalIndexPath == destinationIndexPath {
                        return UICollectionViewDropProposal(operation: .move, intent: intent)
                    } else {
                        lastProposalIntent = intent
                        lastProposalIndexPath = destinationIndexPath
                        return UICollectionViewDropProposal(operation: .move, intent: intent)
                    }
                }
            }

            // Only show drop target indicator when there's a meaningful change
            let shouldShowIndicator = shouldShowDropTargetIndicator(
                at: destinationIndexPath,
                in: collectionView,
                for: session
            )
            
            // Special validation for goal card: enforce row-start indices when all streaks are present
            if let dragItem = session.items.first {
                var isGoalCard = false
                if let wrapper = dragItem.localObject as? DragItemWrapper,
                   wrapper.type == DragItemWrapper.ItemType.goalStreak,
                   let mileStone = wrapper.item as? MileStoneType {
                    if case .goalCard = mileStone { isGoalCard = true }
                } else if let mileStone = dragItem.localObject as? MileStoneType {
                    if case .goalCard = mileStone { isGoalCard = true }
                }

                if isGoalCard {
                    let allStreaksPresent = isAllStreaksPresent()
                    if allStreaksPresent {
                        let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                        if destinationIndexPath.item % gridColumns != 0 {
                            return UICollectionViewDropProposal(operation: .forbidden)
                        }
                    }
                }
            }
            
            if shouldShowIndicator {
                showDropTargetIndicator(at: destinationIndexPath, in: collectionView)
            }
            // Cache proposal for non-goal targets to avoid frequent system changes
            lastProposalIntent = .insertAtDestinationIndexPath
            lastProposalIndexPath = destinationIndexPath
            
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
                  lastDropTargetItemType != nil else {
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
        
        /// TODO: Implement minimal haptic feedback for meaningful drop target changes.
        /// This should trigger a subtle haptic when the user crosses a significant grid boundary.
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
            guard store.state.ui.isEditMode else { 
                return 
            }
            guard let destinationIndexPath = coordinator.destinationIndexPath,
                  let item = coordinator.items.first,
                  let sourceIndexPath = item.sourceIndexPath else { 
                return 
            }

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


            // Prevent dropping on removed items - only allow drops on active items
            if destinationIndexPath.item >= firstRemovedIndex {
                collectionView.reloadData() // Reset to original state
                return
            }

            // Also prevent moving FROM removed items (should already be blocked, but extra safety)
            if sourceIndexPath.item >= firstRemovedIndex {
                collectionView.reloadData() // Reset to original state
                return
            }

            // Widgets and apps are pushed to maintain proper spacing
            let sourceIndex = sourceIndexPath.item
            var destinationIndex = destinationIndexPath.item


            // Immediate neighbor swap across goal card (streak moves only)
            if case .streak = widget {
                let src = sourceIndex
                let dst = destinationIndex
                if let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                    let isNeighborSwap = (src == goalPos - 1 && dst == goalPos + 1)
                    || (src == goalPos + 1 && dst == goalPos - 1)
                    if isNeighborSwap {
                        if let custom = collectionView as? CustomCollectionView { 
                            custom.suspendIntrinsicInvalidation = true 
                        }
                        collectionView.performBatchUpdates({
                            gridModel.moveWidget(from: src, to: dst)
                            collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
                        }, completion: { _ in
                            if let custom = collectionView as? CustomCollectionView {
                                custom.suspendIntrinsicInvalidation = false
                                custom.invalidateIntrinsicContentSize()
                            }
                        })
                        persistGridOrderToStore()
                        coordinator.drop(item.dragItem, toItemAt: destinationIndexPath)
                        store.state.ui.isGoalCardBeingDragged = false
                        return
                    }
                }
            }

            // Snap goal card drop to row start immediately (e.g., index 1 -> 0) when all streaks present
            if case .goalCard = widget, isAllStreaksPresent() {
                let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                destinationIndex = (destinationIndex / gridColumns) * gridColumns
            }

            // Calculate the actual insertion index considering widget/app spacing
            let actualInsertionIndex = calculateActualInsertionIndex(
                from: sourceIndex,
                to: destinationIndex,
                for: widget
            )


        // Special validation for goal card dragged from last position
            if case .goalCard = widget {
                let isFromLastPosition = sourceIndex == gridModel.mileStones.count - 1
                let isFromFourthPosition = sourceIndex == 3
                let isFromSecondPosition = sourceIndex == 2
                let hasRemovedStreaks = !store.state.ui.removedStreaks.isEmpty

            }

            // Keep smooth animations during the drop operation for beautiful cell movement
            // Only disable animations at the very end for instant final positioning
            if let custom = collectionView as? CustomCollectionView { 
                custom.suspendIntrinsicInvalidation = true 
            }
            
            // Determine final destination index for goal card
            let finalDestinationIndex: Int
            if case .goalCard = widget {
                let allStreaksPresent = isAllStreaksPresent()
                let hasRemovedStreaks = !store.state.ui.removedStreaks.isEmpty
                
                if allStreaksPresent && !hasRemovedStreaks {
                    // Snap to row start (index % columns == 0)
                    let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                    let targetRow = actualInsertionIndex / gridColumns
                    finalDestinationIndex = targetRow * gridColumns
                } else if hasRemovedStreaks {
                    // When streaks are removed, allow flexible positioning but ensure valid placement
                    let remainingStreakCount = gridModel.mileStones.compactMap { widget -> String? in
                        switch widget {
                        case .goalCard: return nil
                        case .streak(let streakItem):
                            return store.isStreakRemoved(streakItem.label) ? nil : streakItem.label
                        }
                    }.count
                    
                    let maxValidPosition = remainingStreakCount
                    finalDestinationIndex = min(actualInsertionIndex, maxValidPosition)
                } else {
                    // Fallback: keep insertion index
                    finalDestinationIndex = actualInsertionIndex
                }
            } else {
                finalDestinationIndex = actualInsertionIndex
            }

            // Use smooth animations for the actual reordering
            collectionView.performBatchUpdates({
                gridModel.moveWidget(from: sourceIndex, to: finalDestinationIndex)
                collectionView.moveItem(at: sourceIndexPath, to: IndexPath(item: finalDestinationIndex, section: 0))
            }, completion: { _ in
                collectionView.collectionViewLayout.invalidateLayout()
                collectionView.layoutIfNeeded()

                // Now disable animations for the final positioning to prevent jump
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                CATransaction.setAnimationDuration(0)
                
                UIView.performWithoutAnimation {
                    // Update all visible cells instantly without reconfiguration
                    let visibleIndexPaths = collectionView.indexPathsForVisibleItems
                    for indexPath in visibleIndexPaths {
                        guard let cell = collectionView.cellForItem(at: indexPath) else { continue }
                        
                        if let streakCell = cell as? StreakCardCell {
                            // Clear any shadow effects that might remain without reconfiguring
                            streakCell.clearAllShadowEffects()
                        } else if let goalCell = cell as? GoalCardCell {
                            // Clear any shadow effects that might remain without reconfiguring
                            goalCell.clearAllShadowEffects()
                        }
                    }
                }
                
                CATransaction.commit()
                
                // Final safety: snap goal card to row start if needed
                if case .goalCard = widget, self.isAllStreaksPresent() {
                    let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                    let rowStart = (finalDestinationIndex / gridColumns) * gridColumns
                    if rowStart != finalDestinationIndex {
                        UIView.performWithoutAnimation {
                            self.gridModel.moveWidget(from: finalDestinationIndex, to: rowStart)
                            collectionView.moveItem(at: IndexPath(item: finalDestinationIndex, section: 0), to: IndexPath(item: rowStart, section: 0))
                            collectionView.collectionViewLayout.invalidateLayout()
                            collectionView.layoutIfNeeded()
                        }
                    }
                    // Reorder grid to enforce row-start placement based on span
                    self.gridModel.reorderGrid(spanCount: gridColumns, hasRemovedStreaks: !self.store.state.ui.removedStreaks.isEmpty)
                    collectionView.reloadData()
                }

                if let custom = collectionView as? CustomCollectionView {
                    custom.suspendIntrinsicInvalidation = false
                    custom.invalidateIntrinsicContentSize()
                }
            })

            persistGridOrderToStore()

            // Use the same approach as MetricGridUIKitView for consistency
            // Use finalDestinationIndex to ensure the corrected position is used
            coordinator.drop(item.dragItem, toItemAt: IndexPath(item: finalDestinationIndex, section: 0))
            
            // Clear drag state
            store.state.ui.isGoalCardBeingDragged = false

            // Track the dropped item for overlay restoration after layout rerender
            if let wrapper = item.dragItem.localObject as? DragItemWrapper,
               wrapper.type == DragItemWrapper.ItemType.goalStreak,
               let droppedItem = wrapper.item as? MileStoneType {
                if case .streak(let streakItem) = droppedItem {
                    // Store the dropped streak item ID for later overlay restoration
                    lastDroppedStreakId = streakItem.id.uuidString
                    
                    // Make sure the overlay is suppressed during the drop
                    if let targetCell = collectionView.visibleCells.first(where: { cell in
                        guard let streakCell = cell as? StreakCardCell, let rep = streakCell.representedItem else { return false }
                        return rep.id.uuidString == streakItem.id.uuidString
                    }) as? StreakCardCell {
                        targetCell.setOverlaySuppressed(true)
                    }
                } else if droppedItem == .goalCard {
                    // Mark that goal card was dropped
                    lastDroppedGoalCard = true
                    
                    // Make sure the overlay is suppressed during the drop
                    if let targetCell = collectionView.visibleCells.first(where: { cell in
                        return cell is GoalCardCell
                    }) as? GoalCardCell {
                        targetCell.setOverlaySuppressed(true)
                    }
                }
            }

            // Set flag to await drop session end before restoring overlays
            isAwaitingDropEnd = true

            // Subtle haptic confirmation
            provideDropConfirmationHapticFeedback()
        }

        // Provide a transparent, rounded drop preview to eliminate the white platter animation
        func collectionView(_ collectionView: UICollectionView,
                            dropPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
            let params = UIDragPreviewParameters()
            params.backgroundColor = .clear
            if let cell = collectionView.cellForItem(at: indexPath) {
                params.visiblePath = UIBezierPath(roundedRect: cell.bounds, cornerRadius: .radiusSM)
            }
            return params
        }

        // Supply an almost invisible preview for the drop animation
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

        private func calculateActualInsertionIndex(from sourceIndex: Int, to destinationIndex: Int, for widget: MileStoneType) -> Int {
            let currentModel = gridModel.mileStones
            
            // If moving to the same position, no change needed
            if sourceIndex == destinationIndex {
                return sourceIndex
            }
            
            // Determine if this is a widget (goal card) or app (streak item)
            let isWidget = widget == .goalCard
            let columns: Int = DevicePlatform.isTablet ? 4 : 2
  
            if isWidget {
                // Widget (goal card) logic: Full-width items that push others
                // Goal cards should be placed at row boundaries for proper layout
                let targetRow = destinationIndex / columns
                let rowStartIndex = targetRow * columns
                
                // Check if there are any items in the target row
                let rowEndIndex = min(rowStartIndex + columns, currentModel.count)
                let hasItemsInRow = (rowStartIndex..<rowEndIndex).contains { index in
                    index < currentModel.count && currentModel[index] != .goalCard
                }
                
                if hasItemsInRow {
                    // If row has items, insert at the beginning to push them down
                    return rowStartIndex
                } else {
                    // If row is empty, place at the calculated position
                    return min(destinationIndex, currentModel.count)
                }
            } else {
                // Streak item logic with goal card position preservation
                
                // Find goal card position if it exists
                var goalCardIndex: Int? = nil
                for (index, item) in currentModel.enumerated() {
                    if case .goalCard = item {
                        goalCardIndex = index
                        break
                    }
                }
                
                // If there's a goal card between source and destination
                if let goalPos = goalCardIndex {
                    let isGoalBetween = (sourceIndex < goalPos && destinationIndex > goalPos) ||
                                      (sourceIndex > goalPos && destinationIndex < goalPos)
                    
                    if isGoalBetween {
                        // For streak items moving across the goal card:
                        // Return the destination index - the streak items will swap positions
                        // while the goal card stays in place
                        return destinationIndex
                    }
                }
                
                // Calculate the target row and column
                let targetRow = destinationIndex / columns
                let targetColumn = destinationIndex % columns
                
                // Ensure the target position is valid for grid layout
                let validTargetIndex = targetRow * columns + targetColumn
                
                // If the target is occupied by a goal card, maintain its position
                if validTargetIndex < currentModel.count {
                    let targetWidget = currentModel[validTargetIndex]
                    if targetWidget == .goalCard {
                        // If moving down, place before the goal card
                        if sourceIndex < validTargetIndex {
                            return validTargetIndex - 1
                        }
                        // If moving up, place after the goal card
                        else {
                            return validTargetIndex + 1
                        }
                    }
                }

                return validTargetIndex
            }
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionWillBegin session: UIDragSession) {
            // Initialize boundary detection
            configureBoundaryConstraints(for: collectionView)
            boundaryDetector.updateGridBounds(for: collectionView)
            
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
            
            // Hide EditModeOverlay on the dragged cell
            if let draggedItem = session.items.first {
                let milestone: MileStoneType?
                if let wrapper = draggedItem.localObject as? DragItemWrapper,
                   wrapper.type == DragItemWrapper.ItemType.goalStreak {
                    milestone = wrapper.item as? MileStoneType
                } else {
                    milestone = draggedItem.localObject as? MileStoneType
                }
                
                if let milestone = milestone {
                    // Find and hide EditModeOverlay on the dragged cell
                    for cell in collectionView.visibleCells {
                        if let streakCell = cell as? StreakCardCell,
                           case .streak(let item) = milestone,
                           streakCell.representedItem?.id == item.id {
                            streakCell.updateDragState(true)
                            break
                        } else if let goalCell = cell as? GoalCardCell,
                                  milestone == .goalCard {
                            goalCell.updateDragState(true)
                            break
                        }
                    }
                }
            }
            
            // Reset drop target tracking for new drag session
            resetDropTargetTracking()
        }
        
        func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
            // Clear boundary detection state
            boundaryDetector.resetBoundaryState()
            
            // Clear drag operation flag
            if let custom = collectionView as? CustomCollectionView {
                custom.isInDragOperation = false
            }
            
            // Re-enable animations after drag ends
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.commit()
            
            // Clear drag state
            store.state.ui.isGoalCardBeingDragged = false

            // Only restore overlays if we're actually in edit mode to prevent unnecessary updates
            if store.state.ui.isEditMode {
                for cell in collectionView.visibleCells {
                    if let streakCell = cell as? StreakCardCell {
                        streakCell.updateDragState(false)
                        streakCell.clearAllShadowEffects()
                        // Force reconfigure to ensure overlay is properly shown
                        if let item = streakCell.representedItem {
                            streakCell.configure(
                                with: item,
                                store: self.store,
                                onMetricLongPress: { label in
                                    // Handle long press for streak items if needed
                                },
                                onSelectMetric: { label in
                                    // Handle selection for streak items if needed
                                }
                            )
                        }
                    } else if let goalCell = cell as? GoalCardCell {
                        goalCell.updateDragState(false)
                        goalCell.clearAllShadowEffects()
                        goalCell.configure(with: self.store)
                    }
                }
            }
            
            // Reset drop target tracking
            resetDropTargetTracking()
            
            // Reset haptic feedback tracking
            resetHapticFeedbackTracking()
            
            // Reset drag tracking properties
            isAwaitingDropEnd = false
            lastDroppedStreakId = nil
            lastDroppedGoalCard = false
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
                // Clear drag operation flag
                if let custom = collectionView as? CustomCollectionView {
                    custom.isInDragOperation = false
                }
                
                // Clear the store's drag state
                self.store.state.ui.isGoalCardBeingDragged = false
                
                // Reset tracking properties since drag was cancelled
                self.isAwaitingDropEnd = false
                self.lastDroppedStreakId = nil
                self.lastDroppedGoalCard = false
                
                // Immediately restore EditModeOverlay visibility on all cells if in edit mode
                if self.store.state.ui.isEditMode {
                    // Update all visible cells to restore EditModeOverlay visibility
                    for cell in collectionView.visibleCells {
                        if let streakCell = cell as? StreakCardCell {
                            streakCell.updateDragState(false) // Use the new method for more reliable state management
                            // Clear any shadow effects
                            streakCell.clearAllShadowEffects()
                            // Force reconfigure to ensure overlay is properly shown
                            if let item = streakCell.representedItem {
                                streakCell.configure(
                                    with: item,
                                    store: self.store,
                                    onMetricLongPress: { label in
                                        // Handle long press for streak items if needed
                                    },
                                    onSelectMetric: { label in
                                        // Handle selection for streak items if needed
                                    }
                                )
                            }
                        } else if let goalCell = cell as? GoalCardCell {
                            goalCell.updateDragState(false) // Use the new method for more reliable state management
                            // Clear any shadow effects
                            goalCell.clearAllShadowEffects()
                            goalCell.configure(with: self.store)
                        }
                    }
                }
            }
        }
        
        /// Resets haptic feedback tracking to allow fresh feedback in next session
        private func resetHapticFeedbackTracking() {
            lastHapticFeedbackTime = nil
            lastHapticFeedbackRow = nil
            lastHapticFeedbackZone = nil
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnter session: UIDropSession) {
            // Immediately clear any existing drag state when drop session enters
            store.state.ui.isGoalCardBeingDragged = false
            
            // Clear tracking properties when drop session enters
            isAwaitingDropEnd = false
            lastDroppedStreakId = nil
            lastDroppedGoalCard = false
            
            // Keep smooth animations enabled during drop session for beautiful cell movement
            UIView.setAnimationsEnabled(true)
            CATransaction.begin()
            CATransaction.setDisableActions(false)
            CATransaction.setAnimationDuration(0.3)
            CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: .easeInEaseOut))
            CATransaction.commit()
        }
        
        func collectionView(_ collectionView: UICollectionView, dropSessionDidEnd session: UIDropSession) {
            // Immediately clear drag state when drop session ends
            store.state.ui.isGoalCardBeingDragged = false
            
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
                if let streakCell = cell as? StreakCardCell {
                    streakCell.setOverlaySuppressed(false)
                    // Force clear all shadow effects to prevent shadow artifacts
                    streakCell.clearAllShadowEffects()
                } else if let goalCell = cell as? GoalCardCell {
                    goalCell.setOverlaySuppressed(false)
                    // Force clear all shadow effects to prevent shadow artifacts
                    goalCell.clearAllShadowEffects()
                }
            }
            
            // Restore collection view's layer actions
            collectionView.layer.actions = originalActions
            CATransaction.commit()

            if store.state.ui.isEditMode {
                let restore = {
                    if let targetId = self.lastDroppedStreakId,
                       let targetCell = collectionView.visibleCells.first(where: { cell in
                           guard let streakCell = cell as? StreakCardCell, let rep = streakCell.representedItem else { return false }
                           return rep.id.uuidString == targetId
                       }) as? StreakCardCell {
                        targetCell.setOverlaySuppressed(false)
                        targetCell.setNeedsLayout()
                        targetCell.layoutIfNeeded()
                    } else if self.lastDroppedGoalCard,
                              let targetCell = collectionView.visibleCells.first(where: { cell in
                                  return cell is GoalCardCell
                              }) as? GoalCardCell {
                        targetCell.setOverlaySuppressed(false)
                        targetCell.setNeedsLayout()
                        targetCell.layoutIfNeeded()
                    } else {
                        // Fallback: restore all if we cannot identify the dropped cell
                        for cell in collectionView.visibleCells {
                            if let streakCell = cell as? StreakCardCell {
                                streakCell.setOverlaySuppressed(false)
                                streakCell.setNeedsLayout()
                                streakCell.layoutIfNeeded()
                            } else if let goalCell = cell as? GoalCardCell {
                                goalCell.setOverlaySuppressed(false)
                                goalCell.setNeedsLayout()
                                goalCell.layoutIfNeeded()
                            }
                        }
                    }
                    self.isAwaitingDropEnd = false
                    self.lastDroppedStreakId = nil
                    self.lastDroppedGoalCard = false
                }
                restore()
            } else {
                isAwaitingDropEnd = false
                lastDroppedStreakId = nil
                lastDroppedGoalCard = false
            }
            
            // Reset drop target tracking
            resetDropTargetTracking()
        }
        
        /// Saves the current grid order to DashboardStore UI state
        private func persistGridOrderToStore() {
            // Preserve user's exact positioning - no reordering
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

        /// Resets the drop target tracking state
        private func resetDropTargetTracking() {
            lastDropTargetIndexPath = nil
            lastDropTargetItemType = nil
            lastDropTargetChangeTime = nil
        }
        
        /// Helper function to check if all streaks are present (not removed)
        private func isAllStreaksPresent() -> Bool {
            // Count actual streak items (not removed)
            let allStreakLabels = gridModel.mileStones.compactMap { widget -> String? in
                switch widget {
                case .goalCard: return nil
                case .streak(let streakItem):
                    return store.isStreakRemoved(streakItem.label) ? nil : streakItem.label
                }
            }
            let streakCount = allStreakLabels.count
            let result = streakCount == 6
            
            // All streaks present means we have exactly 6 streak items
            return result
        }
        
    }
}
