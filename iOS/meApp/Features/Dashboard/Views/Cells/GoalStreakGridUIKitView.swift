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

    // MARK: - UI Constants
    private enum UIConstants {
        static let sectionInsets = UIEdgeInsets(top: 16, left: 16, bottom: 32, right: 16)
        static let interItemSpacing: CGFloat = 16
        static let lineSpacing: CGFloat = 32
        static let goalCardHeight: CGFloat = 120
        static let streakCardHeight: CGFloat = 70
        static let goalCardRemovedTopInset: CGFloat = 32
        static let goalCardPresentTopInset: CGFloat = 16
    }
    
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

        // Suppress reloads during reset to prevent flickering
        if store.state.ui.isResettingDashboard {
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
        layout.minimumInteritemSpacing = UIConstants.interItemSpacing         // gap between columns
        layout.minimumLineSpacing = UIConstants.lineSpacing                   // gap between rows
        layout.sectionInset = UIConstants.sectionInsets                       // fine-tuned bottom margin for last row
        layout.estimatedItemSize = .zero            // ensure fixed, non-estimated sizing
        return layout
    }
    
    // Note: System drag/drop support is intentionally disabled for this grid.
    // We use interactive movement via long-press + beginInteractiveMovementForItem,
    // with strict clamping and explicit persistence logic.
    
    /// Creates and configures the collection view with drag-and-drop support
    private func createCollectionView(with layout: UICollectionViewFlowLayout) -> UICollectionView {
        let collectionView = CustomCollectionView(frame: .zero, collectionViewLayout: layout)
        GridUIKitInteractionManager.applyCommonCollectionViewConfiguration(collectionView)
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakCardCell.self, forCellWithReuseIdentifier: "StreakCardCell")
        
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

        GridUIKitInteractionManager.addTapSink(
            to: collectionView,
            target: context.coordinator,
            action: #selector(Coordinator.consumeTap)
        )
    }
    
    /// Builds the grid model using the saved order from DashboardStore UI state
    private func buildGridModelFromStoreState() -> MileStoneGridModel {
        let allStreaks = store.streakItemsToShow
        let goalCardPos = store.state.ui.goalCardPosition
        let streakOrder = store.state.ui.streakGridOrder
        let isGoalCardRemoved = store.state.ui.isGoalCardRemoved
        let isEditMode = store.state.ui.isEditMode
        let hasLoadedProgressMetrics = store.state.ui.hasLoadedProgressMetrics
        let hasStreaks = !allStreaks.isEmpty
        let hasValidGoal = !isGoalCardRemoved
        if !hasStreaks && !hasValidGoal {
            return MileStoneGridModel(mileStones: [])
        }
        // Prevent showing streaks/goal card until removal state and order are ready
        if !isEditMode {
            if !hasLoadedProgressMetrics {
                return MileStoneGridModel(
                    mileStones: hasValidGoal ? [.goalCard] : []
                )
            }
        }

        func orderedStreaks(from all: [MetricItem], using order: [String]) -> [MetricItem] {
            if isEditMode {
                // In edit mode, show ALL streaks (including removed ones)
                // Order them according to the saved order, then append any missing ones at the end
                var ordered = order.compactMap { id in all.first(where: { $0.id.uuidString == id }) }
                let missing = all.filter { streak in !order.contains(streak.id.uuidString) }
                ordered.append(contentsOf: missing)
                return ordered
            } else {
                // In non-edit mode, only show streaks that are in the order (removed streaks already filtered by streakItemsToShow)
                if order.isEmpty {
                    return all
                }
                return order.compactMap { id in all.first(where: { $0.id.uuidString == id }) }
            }
        }

        func splitByRemoval(_ streaks: [MetricItem]) -> (active: [MetricItem], removed: [MetricItem]) {
            var active: [MetricItem] = []
            var removed: [MetricItem] = []
            active.reserveCapacity(streaks.count)
            removed.reserveCapacity(streaks.count)
            for streak in streaks {
                if store.isStreakRemoved(streak.label) { removed.append(streak) }
                else { active.append(streak) }
            }
            return (active, removed)
        }

        func effectiveGoalIndex(
            clampedGoalPos: Int,
            streakCount: Int,
            columns: Int,
            isEditMode: Bool,
            hasRemovedStreaks: Bool
        ) -> Int {
            let lastRowIncomplete = (streakCount % columns) != 0

            // When streaks are removed (or removed section exists), enforce row-start placement unless last row is incomplete.
            if hasRemovedStreaks {
                return lastRowIncomplete ? clampedGoalPos : ((clampedGoalPos / columns) * columns)
            }

            // When all streaks are present:
            // - Edit mode: if full rows, keep previous even-position snapping; else allow exact.
            // - Non-edit mode: if full rows, keep row-start placement; else allow exact.
            if lastRowIncomplete {
                return clampedGoalPos
            }

            if isEditMode {
                let adjusted = (clampedGoalPos % 2 == 0) ? clampedGoalPos : clampedGoalPos - 1
                return max(0, adjusted)
            } else {
                return (clampedGoalPos / columns) * columns
            }
        }

        func buildWidgetsWithGoalCard(
            activeStreaks: [MetricItem],
            removedStreaks: [MetricItem],
            goalCardPos: Int,
            isEditMode: Bool,
            isGoalCardRemoved: Bool
        ) -> [MileStoneType] {
            var widgets: [MileStoneType] = []

            if isGoalCardRemoved {
                widgets.append(contentsOf: activeStreaks.map { .streak($0) })
            } else {
                let streakCount = activeStreaks.count
                // Show goal card if not removed; goal card handles "Set a Goal" button when no goal is set
                if streakCount == 0 {
                    if !isGoalCardRemoved {
                        widgets.append(.goalCard)
                    }
                } else {
                    // Use the saved goal card position in both edit and non-edit mode
                    // This ensures the position persists when user exits edit mode
                    let columns = DevicePlatform.isTablet ? 4 : 2
                    let hasRemovedStreaks = !removedStreaks.isEmpty
                    let maxPosition = streakCount
                    let clampedGoal = min(goalCardPos, maxPosition)
                    let goalIndex = effectiveGoalIndex(
                        clampedGoalPos: clampedGoal,
                        streakCount: streakCount,
                        columns: columns,
                        isEditMode: isEditMode,
                        hasRemovedStreaks: hasRemovedStreaks
                    )

                    var goalAdded = false
                    for i in 0...maxPosition {
                        if i == goalIndex && !goalAdded {
                            widgets.append(.goalCard)
                            goalAdded = true
                        }
                        if i < streakCount {
                            widgets.append(.streak(activeStreaks[i]))
                        }
                    }
                    if !goalAdded { widgets.append(.goalCard) }
                }
            }

            // In edit mode, show removed streaks after active section, and place goal card at the end if removed.
            if isEditMode {
                widgets.append(contentsOf: removedStreaks.map { .streak($0) })
                if isGoalCardRemoved { widgets.append(.goalCard) }
            }

            return widgets
        }

        let ordered = orderedStreaks(from: allStreaks, using: streakOrder)
        let split = splitByRemoval(ordered)
        let widgets = buildWidgetsWithGoalCard(
            activeStreaks: split.active,
            removedStreaks: split.removed,
            goalCardPos: goalCardPos,
            isEditMode: isEditMode,
            isGoalCardRemoved: isGoalCardRemoved
        )
        
        // Create the grid model and apply row-wise reordering
        var gridModel = MileStoneGridModel(mileStones: widgets)
        let spanCount = DevicePlatform.isTablet ? 4 : 2
        let hasRemovedStreaks = !split.removed.isEmpty
        gridModel.reorderGrid(spanCount: spanCount, hasRemovedStreaks: hasRemovedStreaks)
        
        return gridModel
    }
    
    // MARK: - Coordinator
    
    class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
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

        // MARK: - Haptics (system-like: prepared + throttled)
        private let boundaryFeedback = UIImpactFeedbackGenerator(style: .light)
        private let dropFeedback = UIImpactFeedbackGenerator(style: .medium)
        
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
        
        private func activeStreakCount() -> Int {
            var count = 0
            for widget in gridModel.mileStones {
                switch widget {
                case .goalCard:
                    continue
                case .streak(let item):
                    if !store.isStreakRemoved(item.label) { count += 1 }
                }
            }
            return count
        }

        private func isLastRowIncomplete(columns: Int) -> Bool {
            let count = activeStreakCount()
            return (count % columns) != 0
        }
        
        init(store: DashboardStore, gridModel: MileStoneGridModel) {
            self.store = store
            self.gridModel = gridModel
            self.boundaryDetector = GridBoundaryDetector()
        }

        // MARK: - Gesture Sink
        @objc func consumeTap(_ sender: UITapGestureRecognizer) {
            guard store.state.ui.isEditMode,
                  let collectionView = sender.view as? UICollectionView else {
                return
            }
            let location = sender.location(in: collectionView)
            for cell in collectionView.visibleCells {
                if let goalCell = cell as? GoalCardCell {
                    let pointInCell = collectionView.convert(location, to: goalCell)
                    if goalCell.bounds.contains(pointInCell) {
                        continue
                    }
                    if goalCell.handleOverlayTapIfNeeded(at: pointInCell) {
                        return
                    }
                } else if let streakCell = cell as? StreakCardCell {
                    let pointInCell = collectionView.convert(location, to: streakCell)
                    if streakCell.bounds.contains(pointInCell) {
                        continue
                    }
                    if streakCell.handleOverlayTapIfNeeded(at: pointInCell) {
                        return
                    }
                }
            }
        }

        private func prepareHapticsForDrag() {
            boundaryFeedback.prepare()
            dropFeedback.prepare()
        }

        private func fireBoundaryHapticIfNeeded(now: Date = Date(), minInterval: TimeInterval = 0.25) {
            guard lastBoundaryHapticTime == nil || now.timeIntervalSince(lastBoundaryHapticTime!) > minInterval else {
                return
            }
            boundaryFeedback.prepare()
            boundaryFeedback.impactOccurred(intensity: 0.25)
            lastBoundaryHapticTime = now
        }

        private func fireDropConfirmationHaptic() {
            dropFeedback.prepare()
            dropFeedback.impactOccurred(intensity: 0.5)
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
                prepareHapticsForDrag()
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
                    fireBoundaryHapticIfNeeded()
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
                fireDropConfirmationHaptic()
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
        
        // MARK: - Interactive Movement (Reordering)
        
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
                    // When all streaks are present
                    let columns: Int = DevicePlatform.isTablet ? 4 : 2
                    // If last row is incomplete, allow exact placement (no row-start snapping)
                    if isLastRowIncomplete(columns: columns) {
                        let clamped = IndexPath(item: min(proposedIndexPath.item, maxValidIndex), section: proposedIndexPath.section)
                        interactiveMovingIndexPath = clamped
                        return clamped
                    }
                    // Otherwise, enforce row boundaries and handle adjacent-drop-forward rule
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
                    // If last row is incomplete, allow flexible placement and keep exact destination
                    let gridColumns: Int = DevicePlatform.isTablet ? 4 : 2
                    if isLastRowIncomplete(columns: gridColumns) {
                        gridModel.moveWidget(from: sourceIndex, to: destinationIndex)
                    } else {
                        // Enforce row-start placement for full rows
                        let sourceRowStart = (sourceIndex / gridColumns) * gridColumns
                        let endOfSourceRow = sourceRowStart + (gridColumns - 1)
                        let maxValidIndex = firstRemovedIndex - 1
                        
                        // Adjacent-drop-forward rule: if dropping from row start to adjacent slot, push goal to next row-start
                        if sourceIndex == sourceRowStart && destinationIndex == endOfSourceRow {
                            let nextRowStart = min(sourceRowStart + gridColumns, maxValidIndex)
                            gridModel.moveWidget(from: sourceIndex, to: nextRowStart)
                        } else {
                            let rowStart = (destinationIndex / gridColumns) * gridColumns
                            gridModel.moveWidget(from: sourceIndex, to: rowStart)
                        }
                        // Normalize to row-start if still off-grid
                        if let finalGoalIndex = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                            let col = finalGoalIndex % gridColumns
                            if col != 0 {
                                let finalRowStart = (finalGoalIndex / gridColumns) * gridColumns
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
