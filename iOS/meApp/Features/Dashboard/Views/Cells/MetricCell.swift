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
    private var isLongPressed: Bool = false
    private var isTapped: Bool = false
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        // Create a placeholder view that will be configured later
        let placeholderView = AnyView(
            MetricCardView(
                value: "0",
                label: "Placeholder",
                dashboardType: .dashboard12,
                isEditMode: false,
                isRemoved: false,
                isSelected: false,
                onToggleRemoval: {},
                onTap: {},
                isDropTarget: false,
                onDrop: { _, _ in false },
                onDropTargetChanged: { _ in },
                verticalPadding: MetricCardView.twelveCardVerticalPadding
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
    func configure(with item: MetricItem, dashboardType: DashboardType, store: DashboardStore, isBeingDragged: Bool = false, onMetricLongPress: ((String) -> Void)? = nil, onSelectMetric: ((String) -> Void)? = nil) {
        representedItem = item
        currentStore = store
        currentDashboardType = dashboardType
        currentIsBeingDragged = isBeingDragged
        
        // Determine if this item is removed
        let itemIsRemoved = store.isMetricRemovedInReorderedArray(at: store.metricsToShow.firstIndex(where: { $0.id == item.id }) ?? 0)
        isRemoved = itemIsRemoved
        
        let metricCardView = MetricCardView(
            value: store.formattedMetricValue(for: (item.preLabel, item.value)),
            label: item.label,
            dashboardType: dashboardType,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: itemIsRemoved,
            isSelected: store.state.ui.selectedMetricLabel == item.label,
            onToggleRemoval: {
                if let index = store.metricsToShow.firstIndex(where: { $0.id == item.id }) {
                    store.toggleMetricRemovalInReorderedArray(at: index)
                }
            },
            onTap: {
                // Only allow selection if not in edit mode
                if !store.state.ui.isEditMode {
                    if store.state.ui.selectedMetricLabel == item.label {
                        // Deselect if already selected
                        onSelectMetric?("")
                    } else {
                        // Select if not selected
                        onSelectMetric?(item.label)
                    }
                }
            },
            isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
            onDrop: { _, _ in false }, // Drag and drop handled by UIKit
            onDropTargetChanged: { _ in },
            verticalPadding: dashboardType == .dashboard12 
                ? MetricCardView.twelveCardVerticalPadding 
                : MetricCardView.fourCardVerticalPadding
        )
        
        // Reintroduce the EditModeOverlay modifier to the metricCardView
        let viewWithOverlay = AnyView(
            metricCardView
                .editModeOverlay(
                    isEditMode: store.state.ui.isEditMode,
                    isRemoved: itemIsRemoved,
                    onToggleRemoval: {
                        if let index = store.metricsToShow.firstIndex(where: { $0.id == item.id }) {
                            store.toggleMetricRemovalInReorderedArray(at: index)
                        }
                    },
                    isBeingDragged: store.state.ui.draggingMetric?.id == item.id || isLongPressed || isTapped, // Include interaction states
                    isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
                    rowIndex: rowIndex,
                    disableWiggle: false
                )
        )
        
        hostingController?.rootView = viewWithOverlay
        // Remove previous gesture recognizers
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        if store.state.ui.isEditMode {
            // Add tap gesture for edit mode
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleMetricTap(_:)))
            self.addGestureRecognizer(tapGesture)
            // Add drag-and-drop gesture in edit mode (handled by UIKit grid)
            // No-op here, handled by parent
        } else {
            // Add long-press for info sheet only in non-edit mode
            let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleMetricLongPressForInfo(_:)))
            longPress.minimumPressDuration = 0.5
            self.addGestureRecognizer(longPress)
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
        
        // Reset to placeholder view
        let placeholderView = AnyView(
            MetricCardView(
                value: "0",
                label: "Placeholder",
                dashboardType: .dashboard12,
                isEditMode: false,
                isRemoved: false,
                isSelected: false,
                onToggleRemoval: {},
                onTap: {},
                isDropTarget: false,
                onDrop: { _, _ in false },
                onDropTargetChanged: { _ in },
                verticalPadding: MetricCardView.twelveCardVerticalPadding
            )
        )
        hostingController?.rootView = placeholderView
    }
    
    // MARK: - Drag State Handling
    
    override func dragStateDidChange(_ dragState: UICollectionViewCell.DragState) {
        super.dragStateDidChange(dragState)
        
        switch dragState {
        case .none:
            // Restore full opacity when drag ends
            hostingController?.view.alpha = 1.0
            // Clear interaction states
            isLongPressed = false
            isTapped = false
            // Reconfigure to show overlay after drag ends
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: false)
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
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: true)
            }
        @unknown default:
            break
        }
    }
    
    // MARK: - Wiggle Animation
    
    override func layoutSubviews() {
        super.layoutSubviews()
        if isWiggling && !isRemoved {
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
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: true)
        }
    }
    
    /// Shows the EditModeOverlay delete button if the cell is in wiggle mode
    /// Similar to AppIconCell.showDeleteButtonIfNeeded() in movingGridsLearning
    func showDeleteButtonIfNeeded() {
        // Update the drag state without full reconfiguration
        currentIsBeingDragged = false
        
        // Reconfigure the cell with isBeingDragged = false to show the overlay
        if let item = representedItem, let store = currentStore {
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: false)
        }
    }
    
    /// Updates the drag state for this cell
    /// - Parameter isBeingDragged: Whether this cell is currently being dragged
    func updateDragState(_ isBeingDragged: Bool) {
        let oldState = currentIsBeingDragged
        currentIsBeingDragged = isBeingDragged
        
        // Reconfigure the cell with the new drag state
        if let item = representedItem, let store = currentStore {
            configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: isBeingDragged)
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
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: currentIsBeingDragged)
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
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: currentIsBeingDragged)
            }
            break
        default:
            break
        }
    }
    
    @objc private func handleMetricTap(_ gesture: UITapGestureRecognizer) {
        switch gesture.state {
        case .began:
            isTapped = true
            // Reconfigure to hide overlay during tap
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: currentIsBeingDragged)
            }
        case .ended, .cancelled:
            isTapped = false
            // Reconfigure to show overlay after tap ends
            if let item = representedItem, let store = currentStore {
                configure(with: item, dashboardType: currentDashboardType, store: store, isBeingDragged: currentIsBeingDragged)
            }
        default:
            break
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
}
