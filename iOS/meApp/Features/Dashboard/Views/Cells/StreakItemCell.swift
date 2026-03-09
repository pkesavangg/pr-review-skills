// MARK: - Enhanced StreakCardCell with Wiggle Animation

// This UICollectionViewCell intentionally aggregates all cell configuration logic
// to maintain proper state synchronization between UIKit and SwiftUI components.
// Splitting would fragment the drag/drop, wiggle animation, and overlay management logic.

import ObjectiveC
import SwiftUI
import UIKit

class StreakCardCell: UICollectionViewCell {
    private static let overlayButtonSize: CGFloat = 48
    
    // MARK: - UI Components
    
    private var hostingController: UIHostingController<AnyView>?
    private let overlayTapButton = UIButton(type: .custom)
    
    // MARK: - Wiggle Animation Properties
    
    var isWiggling: Bool = false {
        didSet {
            // Only trigger layout if the wiggle state actually changed
            if oldValue != isWiggling {
                layoutSubviews()
            }
        }
    }
    
    var isRemoved: Bool = false {
        didSet {
            // Only trigger layout if the removal state actually changed
            if oldValue != isRemoved {
                layoutSubviews()
            }
        }
    }
    
    var rowIndex: Int = 0 {
        didSet {
            // Only trigger layout if the row index actually changed and wiggle is active
            if oldValue != rowIndex && isWiggling && !isRemoved {
                layoutSubviews()
            }
        }
    }
    
    // MARK: - Drag State Properties
    
    private var isLongPressed: Bool = false
    public var representedItem: MetricItem?
    private var currentStore: DashboardStore?
    private var currentIsBeingDragged: Bool = false
    private var suppressOverlay: Bool = false
    private var overlayButtonAction: (() -> Void)?
    private var overlayButtonVisible: Bool = false
    private var overlayButtonOffset = CGSize(width: 22, height: -32)
    
    // Parent context for rendering rules
    var parentView: DashboardMetricsParentView = .dashboard
    
    // MARK: - Configuration
    
    func configure(with item: MetricItem, store: DashboardStore, onMetricLongPress: ((String) -> Void)? = nil, onSelectMetric: ((String) -> Void)? = nil) {
        setupBasicState(item: item, store: store)
        let swiftUIView = createSwiftUIView(item: item, store: store)
        setupHostingController(with: swiftUIView)
        setupGestureRecognizers(store: store, item: item, onMetricLongPress: onMetricLongPress, onSelectMetric: onSelectMetric)
        setupOverlayButton()
        finalizeLayout()
    }
    
    // MARK: - Configuration Helpers
    
    private func setupBasicState(item: MetricItem, store: DashboardStore) {
        representedItem = item
        currentStore = store
        isRemoved = store.gridEditingManager.isStreakRemoved(item.label)
    }
    
    private func getStreakValue(for item: MetricItem) -> String {
        if parentView == .R4ScaleSetup {
            let lower = item.label.lowercased()
            if lower.contains("current streak") || lower.contains("longest streak") {
                return "0"
            } else {
                return "+/-"
            }
        } else {
            return item.value
        }
    }
    
    private func createStreakCardView(item: MetricItem, store: DashboardStore) -> StreakCardView {
        let streakValue = getStreakValue(for: item)
        return StreakCardView(
            value: streakValue,
            label: item.label,
            icon: item.icon,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: isRemoved,
            isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
            onToggleRemoval: {
                store.gridEditingManager.toggleStreakRemoval(item.label)
            },
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            parentView: parentView
        )
    }
    
    private func createSwiftUIView(item: MetricItem, store: DashboardStore) -> AnyView {
        let streakCardView = createStreakCardView(item: item, store: store)
        let shouldShowOverlay = store.state.ui.isEditMode && 
                               !(currentIsBeingDragged || isLongPressed || suppressOverlay)
        
        if shouldShowOverlay {
            let viewWithOverlay = AnyView(
                streakCardView
                    .editModeOverlay(
                        isEditMode: true,
                        isRemoved: isRemoved,
                        onToggleRemoval: {
                            store.gridEditingManager.toggleStreakRemoval(item.label)
                        },
                        isBeingDragged: currentIsBeingDragged || isLongPressed,
                        isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
                        rowIndex: rowIndex,
                        disableWiggle: isRemoved,
                        iconOffset: CGSize(width: 22, height: -32)
                    )
            )
            overlayButtonVisible = store.state.ui.dropHoverId != item.id.uuidString
            overlayButtonAction = { store.gridEditingManager.toggleStreakRemoval(item.label) }
            return viewWithOverlay
        } else {
            overlayButtonVisible = false
            overlayButtonAction = nil
            return AnyView(streakCardView)
        }
    }
    
    private func setupHostingController(with swiftUIView: AnyView) {
        if hostingController == nil {
            let hc = UIHostingController(rootView: swiftUIView)
            hc.view.backgroundColor = .clear
            hc.view.clipsToBounds = false
            contentView.clipsToBounds = false
            clipsToBounds = false
            contentView.addSubview(hc.view)
            hc.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                hc.view.topAnchor.constraint(equalTo: contentView.topAnchor),
                hc.view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
                hc.view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
                hc.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
            ])
            hostingController = hc
        } else {
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            hostingController?.rootView = swiftUIView
            hostingController?.view.setNeedsLayout()
            CATransaction.commit()
        }
    }
    
    private func setupGestureRecognizers(store: DashboardStore, item: MetricItem, onMetricLongPress: ((String) -> Void)?, onSelectMetric: ((String) -> Void)?) {
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        
        if store.state.ui.isEditMode {
            // In edit mode, rely on SwiftUI overlay buttons for add/remove
            return
        }
        
        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleMetricLongPressForInfo(_:)))
        longPress.minimumPressDuration = 0.5
        longPress.cancelsTouchesInView = false
        longPress.delaysTouchesBegan = false
        self.addGestureRecognizer(longPress)

        let selectTap = UITapGestureRecognizer(target: self, action: #selector(handleNonEditSelectTap(_:)))
        selectTap.cancelsTouchesInView = true
        self.addGestureRecognizer(selectTap)

        self.tag = item.id.hashValue
        self.onMetricLongPressCallback = onMetricLongPress
        self.onSelectMetricCallback = onSelectMetric
    }
    
    private func setupOverlayButton() {
        if overlayTapButton.superview == nil {
            overlayTapButton.backgroundColor = .clear
            overlayTapButton.isHidden = true
            overlayTapButton.addTarget(self, action: #selector(handleOverlayTap), for: .touchUpInside)
            addSubview(overlayTapButton)
        }
    }
    
    private func finalizeLayout() {
        setNeedsLayout()
        layoutIfNeeded()

        if let hostingView = hostingController?.view {
            hostingView.frame = contentView.bounds
            hostingView.bounds = contentView.bounds
        }
    }
 
    override func prepareForReuse() {
        super.prepareForReuse()
        representedItem = nil
        currentStore = nil
        currentIsBeingDragged = false
        isLongPressed = false
        
        // Stop any ongoing wiggle animation
        contentView.stopWiggle()
        isWiggling = false
        isRemoved = false
        rowIndex = 0
        
        // Clear any drag effects
        layer.shadowOpacity = 0.0
        layer.shadowRadius = 0
        layer.shadowOffset = .zero
        layer.shadowColor = nil
        layer.shadowPath = nil
        transform = .identity
        
        // Clear callbacks
        onMetricLongPressCallback = nil
        onSelectMetricCallback = nil
        overlayButtonVisible = false
        overlayButtonAction = nil
        overlayTapButton.isHidden = true
        
        // Remove gesture recognizers
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        
        // Reset to placeholder view
        let placeholderView = AnyView(
            StreakCardView(
                value: "0",
                label: "Placeholder",
                icon: "placeholder",
                isEditMode: false,
                isRemoved: false,
                isDropTarget: false,
                onToggleRemoval: {},
                onDrop: { _, _ in false },
                onDropTargetChanged: { _ in },
                parentView: parentView
            )
        )
        // Update with animation disabled to prevent visual glitches during cell reuse
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        hostingController?.rootView = placeholderView
        hostingController?.view.setNeedsLayout()
        CATransaction.commit()
    }
    
    // MARK: - Drag State Handling

    /// Updates the drag state for this cell
    /// - Parameter isBeingDragged: Whether this cell is currently being dragged
    func updateDragState(_ isBeingDragged: Bool) {
        let currentState = currentIsBeingDragged
        if currentState == isBeingDragged { return }
        currentIsBeingDragged = isBeingDragged
        
        if isBeingDragged {
            layer.actions = [
                "position": NSNull(),
                "bounds": NSNull(),
                "transform": NSNull(),
                "opacity": NSNull()
            ]
            layer.shadowOpacity = 0.3
            layer.shadowRadius = 8
            layer.shadowOffset = CGSize(width: 0, height: 4)
            UIView.animate(withDuration: 0.2, delay: 0, options: [.allowUserInteraction, .curveEaseInOut]) {
                self.transform = CGAffineTransform(scaleX: 1.05, y: 1.05)
            }
            // Set suppressOverlay immediately without reconfigure
            suppressOverlay = true
            isLongPressed = true
        } else {
            layer.actions = [
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
            layer.shadowOpacity = 0.0
            layer.shadowRadius = 0
            layer.shadowOffset = .zero
            layer.shadowColor = nil
            layer.shadowPath = nil
            layer.setNeedsDisplay()
            layer.displayIfNeeded()
            clearAllShadowEffects()
            UIView.animate(withDuration: 0.2, delay: 0, options: [.allowUserInteraction, .curveEaseInOut]) {
                self.transform = .identity
            }
            // Clear suppressOverlay immediately without reconfigure
            suppressOverlay = false
            isLongPressed = false
        }
        
        // NO reconfigure during drag to prevent blinking
    }
    
    func setOverlaySuppressed(_ suppressed: Bool) {
        suppressOverlay = suppressed
        if !suppressed {
            isLongPressed = false
            
            // When restoring overlays, add a small delay to ensure layout is fully settled
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 200_000_000)
                guard let self = self,
                      let item = self.representedItem,
                      let store = self.currentStore else { return }
                
                // Force a complete reconfiguration to ensure overlay is properly positioned
                self.configure(with: item, store: store)
                
                // Force layout update to ensure overlay is fully visible
                self.setNeedsLayout()
                self.layoutIfNeeded()
                
                // Ensure the hosting controller view is properly positioned and sized
                if let hostingView = self.hostingController?.view {
                    hostingView.frame = self.contentView.bounds
                    hostingView.bounds = self.contentView.bounds
                    hostingView.setNeedsLayout()
                    hostingView.layoutIfNeeded()
                }
            }
        } else {
            isLongPressed = true
        }
    }
    
    /// Force clear all shadow effects - call this when items are dropped
    func clearAllShadowEffects() {
        layer.shadowOpacity = 0.0
        layer.shadowRadius = 0
        layer.shadowOffset = .zero
        layer.shadowColor = nil
        layer.shadowPath = nil
        layer.setNeedsDisplay()
        layer.displayIfNeeded()
    }

    private func applySelectionShadow() {
        layer.shadowOpacity = 0.3
        layer.shadowRadius = 8
        layer.shadowOffset = CGSize(width: 0, height: 4)
    }

    override func dragStateDidChange(_ dragState: UICollectionViewCell.DragState) {
        super.dragStateDidChange(dragState)
        
        switch dragState {
        case .none:
            hostingController?.view.alpha = 1.0
            suppressOverlay = false
            isLongPressed = false
            // NO reconfigure to prevent blinking
        case .lifting, .dragging:
            hostingController?.view.alpha = 1.0
            suppressOverlay = true
            isLongPressed = true
            // NO reconfigure to prevent blinking
        @unknown default:
            break
        }
    }
    
    // MARK: - Wiggle Animation
    
    override func layoutSubviews() {
        super.layoutSubviews()
        // Wiggle only in edit mode and only if not removed
        if let store = currentStore, store.state.ui.isEditMode, isWiggling && !isRemoved {
            contentView.startWiggleWithRowIndex(rowIndex)
        } else {
            contentView.stopWiggle()
        }

        let size = StreakCardCell.overlayButtonSize
        overlayTapButton.frame = CGRect(
            x: bounds.width - size + overlayButtonOffset.width,
            y: 0 + overlayButtonOffset.height,
            width: size,
            height: size
        )
        overlayTapButton.isHidden = !overlayButtonVisible
    }
    
    func restartWiggleAnimation() {
        if isWiggling && !isRemoved {
            contentView.stopWiggle()
            contentView.startWiggleWithRowIndex(rowIndex)
        }
    }
    
    func stopWiggleAnimation() {
        contentView.stopWiggle()
    }

    // MARK: - Long Press Handling
    
    private var onMetricLongPressCallback: ((String) -> Void)? {
        get { objc_getAssociatedObject(self, &AssociatedKeys.metricLongPressCallback) as? ((String) -> Void) }
        set { objc_setAssociatedObject(self, &AssociatedKeys.metricLongPressCallback, newValue, .OBJC_ASSOCIATION_COPY_NONATOMIC) }
    }
    private var onSelectMetricCallback: ((String) -> Void)? {
        get { objc_getAssociatedObject(self, &AssociatedKeys.metricSelectCallback) as? ((String) -> Void) }
        set { objc_setAssociatedObject(self, &AssociatedKeys.metricSelectCallback, newValue, .OBJC_ASSOCIATION_COPY_NONATOMIC) }
    }
    private struct AssociatedKeys {
        static var metricLongPressCallback: UInt8 = 0
        static var metricSelectCallback: UInt8 = 0
    }
    
    @objc private func handleMetricLongPressForInfo(_ gesture: UILongPressGestureRecognizer) {
        switch gesture.state {
        case .began:
            isLongPressed = true
            // Apply mild selection shadow to indicate selection like MetricCell
            applySelectionShadow()
            // Enter edit mode on long press if not already in edit mode
            if let store = currentStore, !store.state.ui.isEditMode {
                store.gridEditingManager.toggleEditMode()
            }
            // Reconfigure to hide overlay during long press
            if let item = representedItem, let store = currentStore {
                configure(with: item, store: store)
            }
            guard let item = representedItem,
                  let callback = onMetricLongPressCallback else { return }
            // In non-edit mode, always select the item and open info sheet
            if let selectCallback = onSelectMetricCallback, !isSelected {
                selectCallback(item.label)
            }
            callback(item.label)
        case .ended, .cancelled:
            isLongPressed = false
            clearAllShadowEffects()
            // Reconfigure to show overlay after long press ends (if in edit mode)
            if let item = representedItem, let store = currentStore {
                configure(with: item, store: store)
            }
        default:
            break
        }
    }
    
    @objc private func handleNonEditSelectTap(_ gesture: UITapGestureRecognizer) {
        guard gesture.state == .ended, let item = representedItem else { return }
        // Toggle selection: deselect if same, otherwise select tapped
        if currentStore?.state.ui.selectedMetricLabel == item.label {
            onSelectMetricCallback?("")
        } else {
            onSelectMetricCallback?(item.label)
        }
    }

    @objc private func handleOverlayTap() {
        overlayButtonAction?()
    }

    func handleOverlayTapIfNeeded(at pointInCell: CGPoint) -> Bool {
        guard overlayButtonVisible else { return false }
        if overlayTapButton.frame.contains(pointInCell) {
            overlayButtonAction?()
            return true
        }
        return false
    }

    override var isHighlighted: Bool {
        didSet {
            contentView.alpha = 1.0
            backgroundView?.alpha = 1.0
            layer.shadowOpacity = 0.0
        }
    }

    override var isSelected: Bool {
        didSet {
            contentView.alpha = 1.0
            backgroundView?.alpha = 1.0
            layer.shadowOpacity = 0.0
        }
    }

    // MARK: - Drag Preview
    /// Creates a snapshot view for drag preview
    /// - Returns: A UIView snapshot of the cell's content
    func snapshotForPreview() -> UIView {
        guard let hostingController = hostingController else {
            let fallbackView = UIView(frame: contentView.bounds)
            fallbackView.backgroundColor = UIColor.systemBackground
            fallbackView.layer.cornerRadius = .radiusSM
            fallbackView.layer.masksToBounds = true
            return fallbackView
        }
        
        let snapshot = hostingController.view.snapshotView(afterScreenUpdates: true)
        snapshot?.frame = contentView.bounds
        snapshot?.layer.cornerRadius = .radiusSM
        snapshot?.layer.masksToBounds = true
        snapshot?.backgroundColor = .clear
        return snapshot ?? UIView(frame: contentView.bounds)
    }
} 
