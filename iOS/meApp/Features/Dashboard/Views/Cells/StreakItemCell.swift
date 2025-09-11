// MARK: - Enhanced StreakCardCell with Wiggle Animation

import UIKit
import SwiftUI
import ObjectiveC

class StreakCardCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private var hostingController: UIHostingController<AnyView>?
    
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
    
    // Parent context for rendering rules
    var parentView: DashboardMetricsParentView = .dashboard
    
    // MARK: - Configuration
    
    func configure(with item: MetricItem, store: DashboardStore, onMetricLongPress: ((String) -> Void)? = nil, onSelectMetric: ((String) -> Void)? = nil) {
        // Always reconfigure to ensure proper state synchronization
        let needsReconfiguration = true
        
        if !needsReconfiguration {
            return
        }
        
        representedItem = item
        currentStore = store
        
        // Set the removal state
        isRemoved = store.isStreakRemoved(item.label)
        
        let streakValue: String
        if parentView == .R4ScaleSetup {
            let lower = item.label.lowercased()
            if lower.contains("current streak") || lower.contains("longest streak") {
                streakValue = "0"
            } else {
                streakValue = "+/-"
            }
        } else {
            streakValue = item.value
        }

        let streakCardView = StreakCardView(
            value: streakValue,
            label: item.label,
            icon: item.icon,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: isRemoved,
            isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
            onToggleRemoval: {
                store.toggleStreakRemoval(item.label)
            },
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in }
        )
        
        // Apply EditModeOverlay to the StreakCardView only when appropriate
        let shouldShowOverlay = store.state.ui.isEditMode && 
                               !(currentIsBeingDragged || isLongPressed || suppressOverlay)
        
        let viewWithOverlay: AnyView
        if shouldShowOverlay {
            viewWithOverlay = AnyView(
                streakCardView
                    .editModeOverlay(
                        isEditMode: true, // Always true when we want to show overlay
                        isRemoved: isRemoved,
                        onToggleRemoval: {
                            store.toggleStreakRemoval(item.label)
                        },
                        isBeingDragged: currentIsBeingDragged || isLongPressed, // Use actual drag state
                        isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
                        rowIndex: rowIndex,
                        disableWiggle: isRemoved // removed items must not wiggle
                    )
            )
        } else {
            viewWithOverlay = AnyView(streakCardView)
        }

        let swiftUIView = viewWithOverlay
        
        if hostingController == nil {
            let hc = UIHostingController(rootView: swiftUIView)
            hc.view.backgroundColor = .clear
            contentView.addSubview(hc.view)
            hc.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                hc.view.topAnchor.constraint(equalTo: contentView.topAnchor),
                hc.view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
                hc.view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
                hc.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            ])
            hostingController = hc
        } else {
            hostingController?.rootView = swiftUIView
        }

        // Set up gesture recognizers based on edit mode
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        
        if store.state.ui.isEditMode {
            // In edit mode, rely on SwiftUI overlay buttons for add/remove
            // No custom gesture recognizers needed
        } else {
            let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleMetricLongPressForInfo(_:)))
            longPress.minimumPressDuration = 0.5
            self.addGestureRecognizer(longPress)

            let selectTap = UITapGestureRecognizer(target: self, action: #selector(handleNonEditSelectTap(_:)))
            selectTap.cancelsTouchesInView = true
            self.addGestureRecognizer(selectTap)

            self.tag = item.id.hashValue
            self.onMetricLongPressCallback = onMetricLongPress
            self.onSelectMetricCallback = onSelectMetric
        }

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
                onDropTargetChanged: { _ in }
            )
        )
        hostingController?.rootView = placeholderView
    }
    
    // MARK: - Drag State Handling

    /// Updates the drag state for this cell
    /// - Parameter isBeingDragged: Whether this cell is currently being dragged
    func updateDragState(_ isBeingDragged: Bool) {
        let oldState = currentIsBeingDragged
        currentIsBeingDragged = isBeingDragged
        

        
        if isBeingDragged {
            // Enable smooth animations during drag for beautiful cell movement
            layer.actions = [
                "position": NSNull(),
                "bounds": NSNull(),
                "transform": NSNull(),
                "opacity": NSNull()
            ]
            
            // Add subtle shadow and scale effect during drag
            layer.shadowOpacity = 0.3
            layer.shadowRadius = 8
            layer.shadowOffset = CGSize(width: 0, height: 4)
            
            // Smooth transform animation
            UIView.animate(withDuration: 0.2, delay: 0, options: [.allowUserInteraction, .curveEaseInOut], animations: {
                self.transform = CGAffineTransform(scaleX: 1.05, y: 1.05)
            })
        } else {
            // Restore normal behavior when not dragging
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
            
            // Completely remove all drag effects and shadows
            layer.shadowOpacity = 0.0
            layer.shadowRadius = 0
            layer.shadowOffset = .zero
            layer.shadowColor = nil
            layer.shadowPath = nil
            
            // Force immediate shadow removal
            layer.setNeedsDisplay()
            layer.displayIfNeeded()
            
            // Also call the dedicated shadow clearing method
            clearAllShadowEffects()
            
            // Smooth return to normal size
            UIView.animate(withDuration: 0.2, delay: 0, options: [.allowUserInteraction, .curveEaseInOut], animations: {
                self.transform = .identity
            })
        }
        
        // Always reconfigure to update overlay visibility when drag state changes
        if let item = representedItem, let store = currentStore {
            configure(with: item, store: store)
        }
    }
    
    func setOverlaySuppressed(_ suppressed: Bool) {
        suppressOverlay = suppressed
        if !suppressed {
            isLongPressed = false
            
            // When restoring overlays, add a small delay to ensure layout is fully settled
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
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
            // Restore full opacity when drag ends
            hostingController?.view.alpha = 1.0
            // Clear interaction states
            if !suppressOverlay {
                isLongPressed = false
            } else {
                isLongPressed = true
            }

            if let item = representedItem, let store = currentStore {
                configure(with: item, store: store)
            }
        case .lifting, .dragging:
            hostingController?.view.alpha = 1.0
            isLongPressed = true
            if let item = representedItem, let store = currentStore {
                configure(with: item, store: store)
            }
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
        static var metricLongPressCallback = "metricLongPressCallback"
        static var metricSelectCallback = "metricSelectCallback"
    }
    
    @objc private func handleMetricLongPressForInfo(_ gesture: UILongPressGestureRecognizer) {
        switch gesture.state {
        case .began:
            isLongPressed = true
            // Apply mild selection shadow to indicate selection like MetricCell
            applySelectionShadow()
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
            break
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
