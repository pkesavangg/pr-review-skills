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
            coordinator.lastIsEditMode = store.ui.isEditMode
            // Keep system drag disabled; we use interactive movement with strict clamping
            collectionView.dragInteractionEnabled = false
            return
        }

        // Suppress reloads during reset to prevent flickering
        if store.ui.isResettingDashboard {
            return
        }

        // Rebuild model and compare to previous for minimal updates
        let newModel = buildGridModelFromStoreState()
        let newIsEditMode = store.ui.isEditMode
        let newRemovedStreaks = store.ui.removedStreaks
        let newGoalCardRemoved = store.ui.isGoalCardRemoved
        let newGoalCardPosition = store.ui.goalCardPosition
        let newStreakGridOrder = store.ui.streakGridOrder

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
                // Update long press gesture duration based on edit mode
                if let longPress = coordinator.longPressGestureRecognizer {
                    longPress.isEnabled = false
                    longPress.minimumPressDuration = newIsEditMode ? 0.15 : 0.5
                    longPress.isEnabled = true
                }

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
        let coordinator = Coordinator(store: store, gridModel: buildGridModelFromStoreState())
        coordinator.parentView = parentView
        return coordinator
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
        // Use longer duration when not in edit mode (to enter edit mode), shorter when in edit mode (for dragging)
        longPress.minimumPressDuration = context.coordinator.store.ui.isEditMode ? 0.15 : 0.5
        longPress.cancelsTouchesInView = false
        longPress.delaysTouchesBegan = false
        longPress.delaysTouchesEnded = false
        context.coordinator.longPressGestureRecognizer = longPress
        collectionView.addGestureRecognizer(longPress)

        GridUIKitInteractionManager.addTapSink(
            to: collectionView,
            target: context.coordinator,
            action: #selector(Coordinator.consumeTap)
        )

        // Re-evaluate row spacing/insets when the user changes Dynamic Type at runtime
        NotificationCenter.default.addObserver(
            context.coordinator,
            selector: #selector(Coordinator.contentSizeCategoryDidChange),
            name: UIContentSizeCategory.didChangeNotification,
            object: nil
        )
        context.coordinator.observedCollectionView = collectionView
    }

    /// Builds the grid model using the saved order from DashboardStore UI state.
    /// Adapts main-actor-isolated store values into a pure `Inputs` snapshot and
    /// delegates the mapping to `GoalStreakGridBuilder` (which is unit-testable).
    private func buildGridModelFromStoreState() -> MileStoneGridModel {
        let inputs = GoalStreakGridBuilder.Inputs(
            isEditMode: store.state.ui.isEditMode,
            hasLoadedProgressMetrics: store.state.ui.hasLoadedProgressMetrics,
            managerStreaks: store.streakManager.state.streakItems,
            streakItemsToShow: store.streakItemsToShow,
            goalCardPosition: store.state.ui.goalCardPosition,
            streakGridOrder: store.state.ui.streakGridOrder,
            isGoalCardRemoved: store.state.ui.isGoalCardRemoved,
            isStreakRemoved: { [gridEditingManager = store.gridEditingManager] label in
                gridEditingManager?.isStreakRemoved(label) ?? false
            },
            isTablet: DevicePlatform.isTablet
        )
        return GoalStreakGridBuilder.build(inputs: inputs)
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
        private var lastGoalCardSnapIndex: Int?

        // Cross-goalcard streak swap: custom-animation path that suppresses UIKit's native
        // cell shift so the GoalCard stays visually fixed while the opposite-adjacent streak
        // slides into the dragged streak's vacated slot. Only armed for the narrow case
        // where source is goalPos±1 and the user drags past the goalcard center.
        private var crossSwapSourceIndex: Int?
        private var crossSwapOppositeIndex: Int?
        private var crossSwapIsCrossed: Bool = false
        private weak var crossSwapOppositeCell: UICollectionViewCell?
        private var crossSwapSourceSlotCenter: CGPoint = .zero
        private var crossSwapOppositeSlotCenter: CGPoint = .zero

        // MARK: - Boundary Detection Properties
        public var boundaryDetector: GridBoundaryDetector

        // MARK: - Gesture Recognizer
        var longPressGestureRecognizer: UILongPressGestureRecognizer?

        // Held weakly so the Dynamic-Type notification handler can invalidate the layout
        weak var observedCollectionView: UICollectionView?

        deinit {
            NotificationCenter.default.removeObserver(self)
        }

        @objc func contentSizeCategoryDidChange() {
            guard let collectionView = observedCollectionView else { return }
            collectionView.collectionViewLayout.invalidateLayout()
        }

        // MARK: - Haptics (system-like: prepared + throttled)
        private let boundaryFeedback = UIImpactFeedbackGenerator(style: .light)
        private let dropFeedback = UIImpactFeedbackGenerator(style: .medium)

        // MARK: - Active vs Removed items

        /// Count of non-removed items; also the first index that is removed (drop-forbidden).
        private var firstRemovedIndex: Int {
            gridModel.mileStones.filter { widget in
                switch widget {
                case .goalCard: return !store.state.ui.isGoalCardRemoved
                case .streak(let item): return !store.gridEditingManager.isStreakRemoved(item.label)
                }
            }.count
        }

        private func isItemRemoved(at index: Int) -> Bool {
            guard index < gridModel.mileStones.count else { return false }
            switch gridModel.mileStones[index] {
            case .goalCard: return store.state.ui.isGoalCardRemoved
            case .streak(let item): return store.gridEditingManager.isStreakRemoved(item.label)
            }
        }

        private func isLastRowIncomplete(columns: Int) -> Bool {
            let active = gridModel.mileStones.reduce(into: 0) { count, widget in
                if case .streak(let item) = widget,
                   !store.gridEditingManager.isStreakRemoved(item.label) { count += 1 }
            }
            return (active % columns) != 0
        }

        init(store: DashboardStore, gridModel: MileStoneGridModel) {
            self.store = store
            self.gridModel = gridModel
            self.boundaryDetector = GridBoundaryDetector()
        }

        // MARK: - Gesture Sink

        @objc func consumeTap(_ sender: UITapGestureRecognizer) {
            guard store.state.ui.isEditMode,
                  let collectionView = sender.view as? UICollectionView else { return }
            let location = sender.location(in: collectionView)
            for cell in collectionView.visibleCells {
                if let goalCell = cell as? GoalCardCell {
                    let pointInCell = collectionView.convert(location, to: goalCell)
                    if goalCell.bounds.contains(pointInCell) { continue }
                    if goalCell.handleOverlayTapIfNeeded(at: pointInCell) { return }
                } else if let streakCell = cell as? StreakCardCell {
                    let pointInCell = collectionView.convert(location, to: streakCell)
                    if streakCell.bounds.contains(pointInCell) { continue }
                    if streakCell.handleOverlayTapIfNeeded(at: pointInCell) { return }
                }
            }
        }

        private func prepareHapticsForDrag() {
            boundaryFeedback.prepare()
            dropFeedback.prepare()
        }

        private func fireBoundaryHapticIfNeeded(now: Date = Date(), minInterval: TimeInterval = 0.25) {
            if let lastBoundaryHapticTime,
               now.timeIntervalSince(lastBoundaryHapticTime) <= minInterval { return }
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

        // MARK: - Cross-GoalCard Streak Swap (custom animation path)

        /// Arms the cross-goalcard swap when the dragged streak sits adjacent to the goal card
        /// and the opposite-adjacent slot is also a streak.
        private func armCrossGoalCardSwapIfEligible(source: IndexPath, in collectionView: UICollectionView) {
            resetCrossSwapState()
            guard case .streak = gridModel.mileStones[source.item],
                  let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }),
                  source.item == goalPos - 1 || source.item == goalPos + 1 else { return }

            let oppositeItem = 2 * goalPos - source.item
            guard oppositeItem >= 0, oppositeItem < gridModel.mileStones.count,
                  case .streak = gridModel.mileStones[oppositeItem] else { return }

            let oppositeIP = IndexPath(item: oppositeItem, section: source.section)
            guard let srcAttrs = collectionView.layoutAttributesForItem(at: source),
                  let oppAttrs = collectionView.layoutAttributesForItem(at: oppositeIP) else { return }

            crossSwapSourceIndex = source.item
            crossSwapOppositeIndex = oppositeItem
            crossSwapSourceSlotCenter = srcAttrs.center
            crossSwapOppositeSlotCenter = oppAttrs.center
            crossSwapOppositeCell = collectionView.cellForItem(at: oppositeIP)
        }

        /// Animates the opposite streak into the vacated slot (or back) when the finger crosses
        /// the goal card's center line. Fires only on the transition edge.
        private func updateCrossGoalCardSwapState(gestureY: CGFloat, in collectionView: UICollectionView) {
            guard let sourceIdx = crossSwapSourceIndex,
                  let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }),
                  let goalAttrs = collectionView.layoutAttributesForItem(at: IndexPath(item: goalPos, section: 0)) else { return }

            let shouldCross = sourceIdx < goalPos ? (gestureY > goalAttrs.center.y) : (gestureY < goalAttrs.center.y)
            guard shouldCross != crossSwapIsCrossed else { return }
            crossSwapIsCrossed = shouldCross

            let targetTransform: CGAffineTransform = shouldCross
                ? CGAffineTransform(
                    translationX: crossSwapSourceSlotCenter.x - crossSwapOppositeSlotCenter.x,
                    y: crossSwapSourceSlotCenter.y - crossSwapOppositeSlotCenter.y
                )
                : .identity

            UIView.animate(withDuration: 0.22, delay: 0, options: [.beginFromCurrentState, .curveEaseInOut]) {
                self.crossSwapOppositeCell?.transform = targetTransform
            }
        }

        /// Commits the cross-swap if the user crossed the goal card; returns true if the drop was
        /// handled here so the caller skips the standard `endInteractiveMovement` path.
        private func finishCrossGoalCardSwapIfArmed(in collectionView: UICollectionView) -> Bool {
            defer { resetCrossSwapState(clearCellTransform: true) }
            guard let sourceIdx = crossSwapSourceIndex,
                  let oppositeIdx = crossSwapOppositeIndex,
                  crossSwapIsCrossed else { return false }

            UIView.performWithoutAnimation { collectionView.endInteractiveMovement() }
            gridModel.moveWidget(from: sourceIdx, to: oppositeIdx)
            persistGridOrderToStore()
            UIView.performWithoutAnimation {
                collectionView.reloadData()
                collectionView.layoutIfNeeded()
            }
            return true
        }

        /// Rolls back the transform animation if the drag was cancelled mid-cross.
        private func rollbackCrossGoalCardSwapIfArmed() {
            if crossSwapIsCrossed, let cell = crossSwapOppositeCell {
                UIView.animate(withDuration: 0.18, delay: 0, options: [.beginFromCurrentState, .curveEaseOut]) {
                    cell.transform = .identity
                }
            }
            resetCrossSwapState(clearCellTransform: false)
        }

        private func resetCrossSwapState(clearCellTransform: Bool = true) {
            if clearCellTransform { crossSwapOppositeCell?.transform = .identity }
            crossSwapSourceIndex = nil
            crossSwapOppositeIndex = nil
            crossSwapIsCrossed = false
            crossSwapOppositeCell = nil
            crossSwapSourceSlotCenter = .zero
            crossSwapOppositeSlotCenter = .zero
        }

        private func animateGoalCardSnapIfNeeded(in collectionView: UICollectionView, to snappedIndex: Int) {
            guard lastGoalCardSnapIndex != snappedIndex else { return }
            lastGoalCardSnapIndex = snappedIndex

            UIView.animate(
                withDuration: 0.16,
                delay: 0,
                options: [.beginFromCurrentState, .allowUserInteraction, .curveEaseInOut],
                animations: {
                    collectionView.collectionViewLayout.invalidateLayout()
                    collectionView.layoutIfNeeded()
                }
            )
        }

        // MARK: - Interactive Movement (Strictly Clamped)

        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard let collectionView = gesture.view as? UICollectionView else { return }

            switch gesture.state {
            case .began:
                beginInteractiveDrag(gesture: gesture, in: collectionView)
            case .changed:
                updateInteractiveDrag(gesture: gesture, in: collectionView)
            case .ended:
                endInteractiveDrag(in: collectionView)
            default:
                cancelInteractiveDrag(in: collectionView)
            }
        }

        private func beginInteractiveDrag(gesture: UILongPressGestureRecognizer, in collectionView: UICollectionView) {
            if !store.state.ui.isEditMode {
                store.gridEditingManager.toggleEditMode()
            }
            prepareHapticsForDrag()

            let location = gesture.location(in: collectionView)
            guard let indexPath = collectionView.indexPathForItem(at: location),
                  indexPath.item < firstRemovedIndex else { return }

            configureBoundaryConstraints(for: collectionView)
            boundaryDetector.updateGridBounds(for: collectionView)
            interactiveMovingIndexPath = indexPath
            originalMovingIndex = indexPath.item
            lastGoalCardSnapIndex = nil
            captureMovingItemSize(for: indexPath, in: collectionView)

            switch gridModel.mileStones[indexPath.item] {
            case .goalCard:
                isDraggingGoalCard = true
                draggedStreakId = nil
            case .streak(let item):
                isDraggingGoalCard = false
                draggedStreakId = item.id.uuidString
            }
            armCrossGoalCardSwapIfEligible(source: indexPath, in: collectionView)
            collectionView.beginInteractiveMovementForItem(at: indexPath)
        }

        /// Prefers rendered cell bounds; falls back to layout attributes; then to model-derived size.
        private func captureMovingItemSize(for indexPath: IndexPath, in collectionView: UICollectionView) {
            if let cell = collectionView.cellForItem(at: indexPath) {
                interactiveMovingItemSize = cell.bounds.size
                (cell as? StreakCardCell)?.updateDragState(true)
                (cell as? GoalCardCell)?.updateDragState(true)
                return
            }
            if let attrs = collectionView.layoutAttributesForItem(at: indexPath) {
                interactiveMovingItemSize = attrs.bounds.size
                return
            }
            switch gridModel.mileStones[indexPath.item] {
            case .goalCard: interactiveMovingItemSize = goalCardCellSize(in: collectionView)
            case .streak:   interactiveMovingItemSize = streakCellSize(in: collectionView)
            }
        }

        private func updateInteractiveDrag(gesture: UILongPressGestureRecognizer, in collectionView: UICollectionView) {
            let location = gesture.location(in: collectionView)
            let clampedPoint = boundaryDetector.constrainDragLocation(location, in: collectionView)
            var targetCenter = clampedPoint
            if interactiveMovingItemSize != .zero {
                let frame = CGRect(
                    x: clampedPoint.x - interactiveMovingItemSize.width / 2,
                    y: clampedPoint.y - interactiveMovingItemSize.height / 2,
                    width: interactiveMovingItemSize.width,
                    height: interactiveMovingItemSize.height
                )
                let constrained = boundaryDetector.constrainDragFrame(frame, in: collectionView)
                targetCenter = CGPoint(x: constrained.midX, y: constrained.midY)
            }
            if abs(targetCenter.y - location.y) > 0.5 {
                fireBoundaryHapticIfNeeded()
            }
            collectionView.updateInteractiveMovementTargetPosition(targetCenter)
            updateCrossGoalCardSwapState(gestureY: targetCenter.y, in: collectionView)
        }

        private func endInteractiveDrag(in collectionView: UICollectionView) {
            let didCrossSwap = finishCrossGoalCardSwapIfArmed(in: collectionView)
            if !didCrossSwap {
                collectionView.endInteractiveMovement()
            }
            // Clear drag state before reloading so sizing uses default widget-based math —
            // moveItemAt can double-move the goal card, leaving interactiveMovingIndexPath stale.
            resetDragState()
            configureBoundaryConstraints(for: collectionView)
            boundaryDetector.updateGridBounds(for: collectionView)
            collectionView.collectionViewLayout.invalidateLayout()
            collectionView.layoutIfNeeded()
            if !didCrossSwap {
                UIView.performWithoutAnimation {
                    let visible = collectionView.indexPathsForVisibleItems
                    if !visible.isEmpty { collectionView.reloadItems(at: visible) }
                    collectionView.layoutIfNeeded()
                }
            }
            (collectionView as? CustomCollectionView)?.invalidateIntrinsicContentSize()
            clearDragHighlight(in: collectionView)
            fireDropConfirmationHaptic()
        }

        private func cancelInteractiveDrag(in collectionView: UICollectionView) {
            rollbackCrossGoalCardSwapIfArmed()
            collectionView.cancelInteractiveMovement()
            resetDragState()
            configureBoundaryConstraints(for: collectionView)
            boundaryDetector.updateGridBounds(for: collectionView)
            collectionView.collectionViewLayout.invalidateLayout()
            UIView.performWithoutAnimation {
                let visible = collectionView.indexPathsForVisibleItems
                if !visible.isEmpty { collectionView.reloadItems(at: visible) }
                collectionView.layoutIfNeeded()
            }
            clearDragHighlight(in: collectionView)
        }

        private func resetDragState() {
            interactiveMovingIndexPath = nil
            interactiveMovingItemSize = .zero
            isDraggingGoalCard = false
            draggedStreakId = nil
            originalMovingIndex = nil
            lastGoalCardSnapIndex = nil
        }

        private func clearDragHighlight(in collectionView: UICollectionView) {
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

        // MARK: - UICollectionViewDataSource

        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
            gridModel.mileStones.count
        }

        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let widget = gridModel.mileStones[indexPath.item]

            switch widget {
            case .goalCard:
                guard let cell = collectionView.dequeueReusableCell(
                    withReuseIdentifier: "GoalCardCell",
                    for: indexPath
                ) as? GoalCardCell else {
                    return UICollectionViewCell()
                }
                cell.configure(with: store)
                cell.isWiggling = store.ui.isEditMode
                cell.rowIndex = indexPath.item
                cell.isRemoved = store.ui.isGoalCardRemoved
                return cell
            case .streak(let item):
                guard let cell = collectionView.dequeueReusableCell(
                    withReuseIdentifier: "StreakCardCell",
                    for: indexPath
                ) as? StreakCardCell else {
                    return UICollectionViewCell()
                }
                cell.parentView = parentView
                cell.configure(
                    with: item,
                    store: store
                )
                cell.isWiggling = store.ui.isEditMode
                cell.rowIndex = indexPath.item
                cell.isRemoved = store.gridEditingManager.isStreakRemoved(item.label)
                return cell
            }
        }

        // MARK: - UICollectionViewDelegateFlowLayout

        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            if let size = sizeForMovingItem(at: indexPath, in: collectionView) {
                return size
            }
            switch gridModel.mileStones[indexPath.item] {
            case .goalCard:
                return goalCardCellSize(in: collectionView)
            case .streak:
                return streakCellSize(in: collectionView)
            }
        }

        /// Drag-time sizing: dragged cell keeps its captured size; a streak-drag keeps the
        /// real goal card full-width (tracked via the virtual order); all others are streak-sized.
        private func sizeForMovingItem(at indexPath: IndexPath, in collectionView: UICollectionView) -> CGSize? {
            guard interactiveMovingItemSize != .zero else { return nil }
            if interactiveMovingIndexPath == indexPath { return interactiveMovingItemSize }

            if !isDraggingGoalCard {
                let virtual = virtualMileStones()
                if indexPath.item >= 0, indexPath.item < virtual.count,
                   case .goalCard = virtual[indexPath.item] {
                    return goalCardCellSize(in: collectionView)
                }
            }
            return (isDraggingGoalCard || draggedStreakId != nil) ? streakCellSize(in: collectionView) : nil
        }

        /// Visual widget order during an in-flight interactive move (from → to).
        private func virtualMileStones() -> [MileStoneType] {
            var virtual = gridModel.mileStones
            guard let from = originalMovingIndex,
                  let to = interactiveMovingIndexPath?.item,
                  from >= 0, from < virtual.count,
                  to >= 0, to < virtual.count else { return virtual }
            virtual.insert(virtual.remove(at: from), at: to)
            return virtual
        }

        private func goalCardCellSize(in collectionView: UICollectionView) -> CGSize {
            CGSize(width: collectionView.bounds.width - 32, height: 120)
        }

        private func streakCellSize(in collectionView: UICollectionView) -> CGSize {
            let columns: CGFloat = DevicePlatform.isTablet ? 4 : 2
            let itemWidth = (collectionView.bounds.width - 32 - 16 * (columns - 1)) / columns
            return CGSize(width: itemWidth, height: 70)
        }

        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, insetForSectionAt section: Int) -> UIEdgeInsets {
            let topInset: CGFloat = store.ui.isGoalCardRemoved ? 32.0 : 16.0
            return UIEdgeInsets(top: topInset, left: 16.0, bottom: 32.0, right: 16.0)
        }

        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumLineSpacingForSectionAt section: Int) -> CGFloat {
            // Wider row gap on iPhone XS / SE / mini to keep streak rows from feeling cramped.
            if DevicePlatform.isSmallPhone || DevicePlatform.isMiniPhone { return 40 }
            return 32
        }

        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumInteritemSpacingForSectionAt section: Int) -> CGFloat {
            // Constant column gap — preserves column separation under Dynamic Type.
            return 16
        }

        // MARK: - Interactive Movement (Reordering)

        func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
            // Only allow moving items that are non-removed (active) items
            return store.ui.isEditMode && indexPath.item < firstRemovedIndex
        }

        func collectionView(_ collectionView: UICollectionView, targetIndexPathForMoveFromItemAt originalIndexPath: IndexPath, toProposedIndexPath proposedIndexPath: IndexPath) -> IndexPath {
            let maxValidIndex = firstRemovedIndex - 1

            // Cross-goalcard streak swap is only for the opposite-adjacent slot across the goal card.
            // Other moves that merely start next to the goal card should use UIKit's normal shift path.
            if let sourceIndex = crossSwapSourceIndex,
               let oppositeIndex = crossSwapOppositeIndex,
               let goalIndex = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                if proposedIndexPath.item == sourceIndex ||
                   proposedIndexPath.item == goalIndex ||
                   proposedIndexPath.item == oppositeIndex {
                    interactiveMovingIndexPath = originalIndexPath
                    return originalIndexPath
                }
                rollbackCrossGoalCardSwapIfArmed()
            }

            // Prevent any moves to/from removed item indices
            if originalIndexPath.item >= firstRemovedIndex || proposedIndexPath.item >= firstRemovedIndex {
                interactiveMovingIndexPath = originalIndexPath
                return originalIndexPath // Return original position to cancel the move
            }

            switch gridModel.mileStones[originalIndexPath.item] {
            case .goalCard:
                let snapped = snapGoalCardProposal(
                    originalIndexPath: originalIndexPath,
                    proposedIndexPath: proposedIndexPath,
                    maxValidIndex: maxValidIndex,
                    in: collectionView
                )
                interactiveMovingIndexPath = snapped
                return snapped

            case .streak:
                if let snapped = snapStreakProposal(
                    originalIndexPath: originalIndexPath,
                    proposedIndexPath: proposedIndexPath,
                    maxValidIndex: maxValidIndex
                ) {
                    interactiveMovingIndexPath = snapped
                    return snapped
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

        /// Removed-streaks → clamp to remaining range; full rows → snap to row-start (with
        /// adjacent-drop-forward); incomplete last row → keep exact.
        private func snapGoalCardProposal(
            originalIndexPath: IndexPath,
            proposedIndexPath: IndexPath,
            maxValidIndex: Int,
            in collectionView: UICollectionView
        ) -> IndexPath {
            if !store.state.ui.removedStreaks.isEmpty {
                let remainingStreakCount = gridModel.mileStones.reduce(into: 0) { count, widget in
                    if case .streak(let item) = widget,
                       !store.gridEditingManager.isStreakRemoved(item.label) { count += 1 }
                }
                return IndexPath(item: min(proposedIndexPath.item, remainingStreakCount), section: proposedIndexPath.section)
            }

            let columns: Int = DevicePlatform.isTablet ? 4 : 2
            if isLastRowIncomplete(columns: columns) {
                return IndexPath(item: min(proposedIndexPath.item, maxValidIndex), section: proposedIndexPath.section)
            }

            let sourceRowStart = (originalIndexPath.item / columns) * columns
            let endOfSourceRow = sourceRowStart + (columns - 1)
            // Adjacent-drop-forward: row-start → same-row end ⇒ next row-start.
            let snappedItem = (originalIndexPath.item == sourceRowStart && proposedIndexPath.item == endOfSourceRow)
                ? min(sourceRowStart + columns, maxValidIndex)
                : min((proposedIndexPath.item / columns) * columns, maxValidIndex)

            let snapped = IndexPath(item: snappedItem, section: proposedIndexPath.section)
            if snapped.item != proposedIndexPath.item {
                animateGoalCardSnapIfNeeded(in: collectionView, to: snapped.item)
            }
            return snapped
        }

        /// Avoids the goal-card slot and snaps generic non-cross-swap crossings to a safe row-start.
        private func snapStreakProposal(
            originalIndexPath: IndexPath,
            proposedIndexPath: IndexPath,
            maxValidIndex: Int
        ) -> IndexPath? {
            guard let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) else { return nil }

            if proposedIndexPath.item == goalPos {
                let item = originalIndexPath.item < goalPos
                    ? max(0, goalPos - 1)
                    : min(maxValidIndex, goalPos + 1)
                return IndexPath(item: item, section: proposedIndexPath.section)
            }

            guard crossSwapSourceIndex == nil else { return nil }
            if originalIndexPath.item < goalPos, proposedIndexPath.item > goalPos {
                return IndexPath(item: min(maxValidIndex, goalPos + 1), section: proposedIndexPath.section)
            }
            if originalIndexPath.item > goalPos, proposedIndexPath.item < goalPos {
                let columns: Int = DevicePlatform.isTablet ? 4 : 2
                return IndexPath(item: max(0, goalPos - columns), section: proposedIndexPath.section)
            }
            return nil
        }

        func collectionView(_ collectionView: UICollectionView, moveItemAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) {
            guard sourceIndexPath.item < firstRemovedIndex,
                  destinationIndexPath.item < firstRemovedIndex else { return }

            let sourceIndex = sourceIndexPath.item
            let destinationIndex = destinationIndexPath.item

            if performNeighborSwapAcrossGoalCard(sourceIndex: sourceIndex, destinationIndex: destinationIndex) {
                return
            }

            let movedWidget = gridModel.mileStones[sourceIndex]
            switch movedWidget {
            case .goalCard:
                applyGoalCardMove(sourceIndex: sourceIndex, destinationIndex: destinationIndex)
            case .streak:
                applyStreakMove(sourceIndex: sourceIndex, destinationIndex: destinationIndex)
            }
            // Realign interactiveMovingIndexPath with the widget's actual post-move slot.
            // Prevents UIKit's snapshot-back animation from querying `sizeForItemAt` on a stale
            // index — otherwise the goal card slot briefly renders at streak size on first drop.
            if let finalIndex = gridModel.mileStones.firstIndex(where: { $0 == movedWidget }) {
                interactiveMovingIndexPath = IndexPath(item: finalIndex, section: sourceIndexPath.section)
            }

            persistGridOrderToStore()
        }

        /// Immediate swap of streak neighbors straddling the goal card — keeps the goal card fixed.
        private func performNeighborSwapAcrossGoalCard(sourceIndex: Int, destinationIndex: Int) -> Bool {
            guard let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) else { return false }
            let isNeighborSwap = (sourceIndex == goalPos - 1 && destinationIndex == goalPos + 1)
                || (sourceIndex == goalPos + 1 && destinationIndex == goalPos - 1)
            guard isNeighborSwap else { return false }
            gridModel.moveWidget(from: sourceIndex, to: destinationIndex)
            persistGridOrderToStore()
            return true
        }

        private func applyGoalCardMove(sourceIndex: Int, destinationIndex: Int) {
            let hasRemovedStreaks = !store.state.ui.removedStreaks.isEmpty
            let columns: Int = DevicePlatform.isTablet ? 4 : 2

            if isAllStreaksPresent(), !hasRemovedStreaks {
                if isLastRowIncomplete(columns: columns) {
                    gridModel.moveWidget(from: sourceIndex, to: destinationIndex)
                    return
                }
                applyGoalCardMoveForFullRows(sourceIndex: sourceIndex, destinationIndex: destinationIndex, columns: columns)
                return
            }

            if hasRemovedStreaks {
                let remainingStreakCount = gridModel.mileStones.reduce(into: 0) { count, widget in
                    if case .streak(let item) = widget,
                       !store.gridEditingManager.isStreakRemoved(item.label) { count += 1 }
                }
                gridModel.moveWidget(from: sourceIndex, to: min(destinationIndex, remainingStreakCount))
                return
            }

            let rowStart = (destinationIndex / columns) * columns
            gridModel.moveWidget(from: sourceIndex, to: rowStart)
        }

        private func applyGoalCardMoveForFullRows(sourceIndex: Int, destinationIndex: Int, columns: Int) {
            let sourceRowStart = (sourceIndex / columns) * columns
            let endOfSourceRow = sourceRowStart + (columns - 1)
            let maxValidIndex = firstRemovedIndex - 1

            // Adjacent-drop-forward: from row-start to same-row end → push to next row-start.
            if sourceIndex == sourceRowStart, destinationIndex == endOfSourceRow {
                gridModel.moveWidget(from: sourceIndex, to: min(sourceRowStart + columns, maxValidIndex))
            } else {
                let rowStart = (destinationIndex / columns) * columns
                gridModel.moveWidget(from: sourceIndex, to: rowStart)
            }

            // Normalize if the goal card landed off row-start (e.g., after internal shifts).
            if let finalGoalIndex = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }),
               finalGoalIndex % columns != 0 {
                let finalRowStart = (finalGoalIndex / columns) * columns
                if finalRowStart != finalGoalIndex {
                    gridModel.moveWidget(from: finalGoalIndex, to: finalRowStart)
                }
            }
        }

        private func applyStreakMove(sourceIndex: Int, destinationIndex: Int) {
            var destination = destinationIndex
            // Snap crossings of the goal card so streaks don't morph mid-drag.
            if let goalPos = gridModel.mileStones.firstIndex(where: { $0 == .goalCard }) {
                let maxValidIndex = firstRemovedIndex - 1
                if sourceIndex < goalPos, destination > goalPos {
                    destination = min(maxValidIndex, goalPos + 1)
                } else if sourceIndex > goalPos, destination < goalPos {
                    let columns: Int = DevicePlatform.isTablet ? 4 : 2
                    destination = max(0, goalPos - columns)
                }
            }
            gridModel.moveWidget(from: sourceIndex, to: destination)
        }

        /// Saves the current grid order to DashboardStore UI state
        private func persistGridOrderToStore() {
            // Preserve user's exact positioning - no reordering
            var newStreakOrder: [MetricItem] = []
            var goalCardPosition: Int?

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

            // Save goal card position. Deliberately do NOT sync `lastGoalCardPosition` /
            // `lastStreakGridOrder` here: the follow-up `updateUIView` must rebuild via
            // `buildGridModelFromStoreState`, which normalizes the goal card position through
            // `effectiveGoalIndex` (row-start / even-slot snapping). Skipping that rebuild lets
            // the goal card settle at odd indices.
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
                    return store.gridEditingManager.isStreakRemoved(streakItem.label) ? nil : streakItem.label
                }
            }
            let streakCount = allStreakLabels.count
            let result = streakCount == 6

            // All streaks present means we have exactly 6 streak items
            return result
        }

    }
}
