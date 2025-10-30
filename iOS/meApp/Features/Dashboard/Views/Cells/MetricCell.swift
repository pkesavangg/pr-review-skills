//
//  MetricCell.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import UIKit
import SwiftUI

/// Custom UICollectionViewCell that represents a metric item with drag-and-drop support
/// Features include wiggle animation and iOS home screen-like behavior
/// Uses MetricCardView SwiftUI component for UI with EditModeOverlay
class MetricCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private let hostingController: UIHostingController<AnyView>?
    
    // MARK: - Public Accessors
    
    var representedItem: MetricItem?
    var onDeleteTapped: (() -> Void)?
    
    // MARK: - Private Properties for Configuration
    
    private var currentStore: DashboardStore?
    private var currentDashboardType: DashboardType = .dashboard12
    private var currentIsBeingDragged: Bool = false
    private var currentParentView: DashboardMetricsParentView = .dashboard
    private var isLongPressed: Bool = false
    private var isTapped: Bool = false
    private var suppressOverlay: Bool = false
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        // Create a placeholder view that will be configured later
        let placeholderView = AnyView(
            MetricCardView(
                value: "0",
                label: "Placeholder",
                icon: nil,
                dashboardType: .dashboard12,
                isEditMode: false,
                isRemoved: false,
                isSelected: false,
                onToggleRemoval: {},
                onTap: {},
                isDropTarget: false,
                onDrop: { _, _ in false },
                onDropTargetChanged: { _ in },
                verticalPadding: MetricCardView.twelveCardVerticalPadding,
                parentView: .dashboard
            )
        )
        
        self.hostingController = UIHostingController(rootView: placeholderView)
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - UI Setup
    
    /// Sets up all UI components and constraints
    private func setupUI() {
        setupHostingController()
        setupConstraints()
    }
    
    /// Configures the hosting controller for SwiftUI view
    private func setupHostingController() {
        guard let hostingController = hostingController else { return }
        
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        hostingController.view.backgroundColor = .clear
        contentView.addSubview(hostingController.view)
    }
    
    /// Sets up Auto Layout constraints for all UI components
    private func setupConstraints() {
        guard let hostingController = hostingController else { return }
        
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: contentView.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }
    
    // MARK: - Configuration
    
    /// Configures the cell with a MetricItem
    /// - Parameters:
    ///   - item: The MetricItem to display
    ///   - dashboardType: The dashboard type for styling
    ///   - store: The dashboard store for formatting
    ///   - isBeingDragged: Whether this cell is currently being dragged
    ///   - parentView: The context in which this cell is displayed (dashboard vs R4ScaleSetup)
    func configure(with item: MetricItem, dashboardType: DashboardType, store: DashboardStore, isBeingDragged: Bool = false, parentView: DashboardMetricsParentView, onMetricLongPress: ((String) -> Void)? = nil, onSelectMetric: ((String) -> Void)? = nil) {
        representedItem = item
        currentStore = store
        currentDashboardType = dashboardType
        currentIsBeingDragged = isBeingDragged
        currentParentView = parentView
        
        // Determine if this item is removed using the new removal state
        let itemIsRemoved = store.isMetricRemoved(item.label)
        isRemoved = itemIsRemoved
        
        let displayValue = store.formattedMetricValue(for: (item.preLabel, item.value))

        let metricCardView = MetricCardView(
            value: displayValue,
            label: item.label,
            icon: item.icon,
            dashboardType: dashboardType,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: itemIsRemoved,
            isSelected: store.state.ui.selectedMetricLabel == item.label,
            onToggleRemoval: {
                store.toggleMetricRemoval(item.label)
            },
            onTap: {
                // Only allow selection if not in edit mode
                if !store.state.ui.isEditMode {
                    if store.state.ui.selectedMetricLabel == item.label {
                        // Deselect if already selected
                        store.state.ui.selectedMetricLabel = nil
                        onSelectMetric?("")
                    } else {
                        // Select if not selected
                        store.state.ui.selectedMetricLabel = item.label
                        onSelectMetric?(item.label)
                    }
                }
            },
            isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
            onDrop: { _, _ in false }, // Drag and drop handled by UIKit
            onDropTargetChanged: { _ in },
            verticalPadding: dashboardType == .dashboard12 
                ? MetricCardView.twelveCardVerticalPadding 
                : MetricCardView.fourCardVerticalPadding,
            parentView: parentView
        )
        
        // Only apply EditModeOverlay when in edit mode
        let finalView = store.state.ui.isEditMode ? AnyView(
            metricCardView
                .editModeOverlay(
                    isEditMode: store.state.ui.isEditMode,
                    isRemoved: itemIsRemoved,
                    onToggleRemoval: {
                        store.toggleMetricRemoval(item.label)
                    },
                    isBeingDragged: store.state.ui.draggingMetric?.id == item.id || isLongPressed || isTapped,
                    isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
                    rowIndex: rowIndex,
                    disableWiggle: itemIsRemoved // removed items must not wiggle
                )
        ) : AnyView(metricCardView)
        
        // Update root view synchronously to prevent visual glitches during cell configuration
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        hostingController?.rootView = finalView
        hostingController?.view.setNeedsLayout()
        hostingController?.view.layoutIfNeeded()
        CATransaction.commit()
        // Remove previous gesture recognizers
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        if store.state.ui.isEditMode {
            // In edit mode, rely on SwiftUI overlay buttons for add/remove; avoid intercepting taps here
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
    }
    
    // MARK: - Reuse
    
    override func prepareForReuse() {
        super.prepareForReuse()
        representedItem = nil
        currentStore = nil
        currentDashboardType = .dashboard12
        currentIsBeingDragged = false
        isLongPressed = false
        isTapped = false
        
        // Stop any ongoing wiggle animation
        contentView.stopWiggle()
        isWiggling = false
        isRemoved = false
        rowIndex = 0
        
        // Reset callbacks
        onMetricLongPressCallback = nil
        onSelectMetricCallback = nil
        
        // Reset to placeholder view with immediate UI update to prevent visual glitches
        let placeholderView = AnyView(
            MetricCardView(
                value: DashboardStrings.placeholder,
                label: "Placeholder",
                icon: nil,
                dashboardType: .dashboard12,
                isEditMode: false,
                isRemoved: false,
                isSelected: false,
                onToggleRemoval: {},
                onTap: {},
                isDropTarget: false,
                onDrop: { _, _ in false },
                onDropTargetChanged: { _ in },
                verticalPadding: MetricCardView.twelveCardVerticalPadding,
                parentView: .dashboard
            )
        )
        // Update synchronously to prevent visual glitches during cell reuse
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        hostingController?.rootView = placeholderView
        hostingController?.view.setNeedsLayout()
        hostingController?.view.layoutIfNeeded()
        CATransaction.commit()
    }
    
    // MARK: - Drag State Handling
    
    override func dragStateDidChange(_ dragState: UICollectionViewCell.DragState) {
        super.dragStateDidChange(dragState)
        
        switch dragState {
        case .none:
            // Restore full opacity when drag ends
            hostingController?.view.alpha = 1.0
            // Clear interaction states
            if !suppressOverlay {
                isLongPressed = false
                isTapped = false
            } else {
                isLongPressed = true
            }
            // Reconfigure to show overlay after drag ends
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: suppressOverlay, parentView: currentParentView)
            }
        case .lifting, .dragging:
            // Don't reduce opacity during drag - let EditModeOverlay handle visibility
            // This prevents items from appearing "removed" during drag operations
            hostingController?.view.alpha = 1.0
            // Set interaction states to hide overlay during drag
            isLongPressed = true
            isTapped = true
            // Reconfigure to hide overlay during drag
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: true, parentView: currentParentView)
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
    
    var isWiggling: Bool = false {
        didSet {
            layoutSubviews()
        }
    }
    
    var isRemoved: Bool = false {
        didSet {
            layoutSubviews()
        }
    }
    
    var rowIndex: Int = 0 {
        didSet {
            if isWiggling && !isRemoved {
                layoutSubviews()
            }
        }
    }
    
    func restartWiggleAnimation() {
        if isWiggling && !isRemoved {
            contentView.stopWiggle()
            contentView.startWiggleWithRowIndex(rowIndex)
        }
    }
    
    // MARK: - EditModeOverlay Management
    
    /// Hides the EditModeOverlay delete button specifically for drag operations
    /// Similar to AppIconCell.hideDeleteButtonForDrag() in movingGridsLearning
    func hideDeleteButtonForDrag() {
        // Update the drag state without full reconfiguration
        currentIsBeingDragged = true
        
        // Reconfigure the cell with isBeingDragged = true to hide the overlay
        if let item = representedItem, let store = currentStore {
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: true, parentView: currentParentView)
        }
    }
    
    /// Shows the EditModeOverlay delete button if the cell is in wiggle mode
    /// Similar to AppIconCell.showDeleteButtonIfNeeded() in movingGridsLearning
    func showDeleteButtonIfNeeded() {
        // Update the drag state without full reconfiguration
        currentIsBeingDragged = false
        
        // Reconfigure the cell with isBeingDragged = false to show the overlay
        if let item = representedItem, let store = currentStore {
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: false, parentView: currentParentView)
        }
    }
    
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
        
        // Reconfigure the cell with the new drag state
        if let item = representedItem, let store = currentStore {
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: isBeingDragged, parentView: currentParentView)
        }
    }

    func setOverlaySuppressed(_ suppressed: Bool) {
        suppressOverlay = suppressed
        if !suppressed {
            isLongPressed = false
            isTapped = false
        } else {
            isLongPressed = true
        }
        if let item = representedItem, let store = currentStore {
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: suppressed, parentView: currentParentView)
        }
    }
    
    /// Updates the cell's appearance when dragging outside boundaries
    /// - Parameter isOutsideBounds: Whether the drag is currently outside allowed boundaries
    func updateBoundaryState(_ isOutsideBounds: Bool) {
        if isOutsideBounds {
            // Add visual feedback for out-of-bounds drag
            layer.borderWidth = 2.0
            layer.borderColor = UIColor.systemRed.cgColor
            alpha = 0.6
            
            // Add a subtle shake animation to indicate invalid drop zone
            let shake = CAKeyframeAnimation(keyPath: "transform.translation.x")
            shake.timingFunction = CAMediaTimingFunction(name: .linear)
            shake.duration = 0.6
            shake.values = [-2.0, 2.0, -2.0, 2.0, -1.0, 1.0, -0.5, 0.5, 0.0]
            layer.add(shake, forKey: "shake")
        } else {
            // Restore normal drag appearance
            layer.borderWidth = 0.0
            layer.borderColor = UIColor.clear.cgColor
            alpha = 1.0
            
            // Remove shake animation
            layer.removeAnimation(forKey: "shake")
        }
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
            // Reconfigure to hide overlay during long press
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: currentIsBeingDragged, parentView: currentParentView)
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
            // Reconfigure to show overlay after long press ends
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: currentIsBeingDragged, parentView: currentParentView)
            }
            break
        default:
            break
        }
    }
    
    // Removed edit-mode tap handler to avoid swallowing SwiftUI overlay button taps

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

}
