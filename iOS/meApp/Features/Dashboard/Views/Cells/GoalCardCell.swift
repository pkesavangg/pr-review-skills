//
//  GoalCardCell.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import UIKit
import SwiftUI

/// Custom UICollectionViewCell that represents the goal card as a large widget
/// Features include wiggle animation and iOS home screen-like behavior
/// Uses GoalProgressCardView SwiftUI component for UI with EditModeOverlay
class GoalCardCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private let hostingController: UIHostingController<AnyView>?
    
    // MARK: - Public Accessors
    
    var onDeleteTapped: (() -> Void)?
    
    // MARK: - Private Properties for Configuration
    
    private var currentStore: DashboardStore?
    private var isLongPressed: Bool = false
    private var suppressOverlay: Bool = false
    private var currentIsBeingDragged: Bool = false
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        // Create a placeholder view that will be configured later
        let placeholderView = AnyView(
            GoalProgressView()
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
        setupGestureRecognizers()
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
    
    /// Sets up gesture recognizers for the cell
    private func setupGestureRecognizers() {
        // Gesture recognizers will be set up in configure method based on edit mode
    }
    
    /// Sets up gesture recognizers based on edit mode
    private func setupGestureRecognizersForEditMode(_ isEditMode: Bool) {
        // Remove existing gesture recognizers
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        
        if !isEditMode {
            // In non-edit mode, add long press gesture recognizer
            let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
            longPress.minimumPressDuration = 0.5
            self.addGestureRecognizer(longPress)
        }
        // In edit mode, rely on SwiftUI overlay buttons for add/remove
    }
    
    /// Handles long press gesture on the cell
    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        switch gesture.state {
        case .began:
            isLongPressed = true
            applySelectionShadow()
            // Reconfigure to hide overlay during long press
            if let store = currentStore {
                configure(with: store)
            }
        case .ended, .cancelled:
            isLongPressed = false
            clearAllShadowEffects()
            // Reconfigure to show overlay after long press ends (if in edit mode)
            if let store = currentStore {
                configure(with: store)
            }
        default:
            break
        }
    }
    
    // MARK: - Configuration
    
    /// Configures the cell with dashboard store data
    /// - Parameter store: The dashboard store
    func configure(with store: DashboardStore) {
        currentStore = store
        
        // Set the removal state
        isRemoved = store.state.ui.isGoalCardRemoved
        
        let goalCardView = GoalProgressView(isSetGoalButtonDisabled: store.state.ui.isEditMode)

        let viewWithOverlay: AnyView
        if store.state.ui.isEditMode {
            let isDragging = store.state.ui.isGoalCardBeingDragged || isLongPressed || currentIsBeingDragged || suppressOverlay
            
            viewWithOverlay = AnyView(
                goalCardView
                    .editModeOverlay(
                        isEditMode: true,
                        isRemoved: store.state.ui.isGoalCardRemoved,
                        onToggleRemoval: {
                            store.toggleGoalCardRemoval()
                        },
                        isBeingDragged: isDragging, // Let overlay handle icon visibility during drag
                        isDropTarget: store.state.ui.dropHoverId == "goalCard",
                        rowIndex: rowIndex,
                        disableWiggle: store.state.ui.isGoalCardRemoved,
                        iconOffset: CGSize(width: 5, height: -5),
                        dimWhenRemoved: true
                    )
            )
        } else {
            viewWithOverlay = AnyView(goalCardView)
        }
        
        hostingController?.rootView = viewWithOverlay
        
        // Set up gesture recognizers based on edit mode
        setupGestureRecognizersForEditMode(store.state.ui.isEditMode)
    }
    
    // MARK: - Reuse
    
    override func prepareForReuse() {
        super.prepareForReuse()
        currentStore = nil
        isLongPressed = false
        currentIsBeingDragged = false
        
        // Remove gesture recognizers
        gestureRecognizers?.forEach { self.removeGestureRecognizer($0) }
        
        // Reset to placeholder view
        let placeholderView = AnyView(
            GoalProgressView()
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
            if !suppressOverlay {
                isLongPressed = false
            } else {
                isLongPressed = true
            }
            // Do NOT reconfigure here to avoid blinking; overlay restoration handled by coordinator
        case .lifting, .dragging:
            // Don't reduce opacity during drag - let EditModeOverlay handle visibility
            // This prevents items from appearing "removed" during drag operations
            hostingController?.view.alpha = 1.0
            // Set interaction states to hide overlay during drag
            isLongPressed = true
            // Do NOT reconfigure during drag to avoid blinking
        @unknown default:
            break
        }
    }
    
    // MARK: - Wiggle Animation
    
    override func layoutSubviews() {
        super.layoutSubviews()
        // Only wiggle if not removed and in wiggle mode
        if isWiggling && !isRemoved {
            startWiggleAnimation()
        } else {
            stopWiggleAnimation()
        }
    }
    
    /// Starts the widget wiggle animation (same behavior as reference WidgetCell)
    private func startWiggleAnimation() {
        // Remove any existing animations before starting
        layer.removeAllAnimations()
        contentView.startWiggleWithRowIndex(rowIndex)
    }
    
    /// Stops the widget wiggle animation (same behavior as reference WidgetCell)
    private func stopWiggleAnimation() {
        contentView.stopWiggle()
    }
    
    /// Controls whether the cell is in wiggle mode
    var isWiggling: Bool = false {
        didSet {
            layoutSubviews()
        }
    }
    
    /// Controls whether the cell represents a removed item
    var isRemoved: Bool = false {
        didSet {
            layoutSubviews()
        }
    }
    
    /// Row index used for alternating wiggle animation timing
    var rowIndex: Int = 0 {
        didSet {
            if isWiggling && !isRemoved {
                layoutSubviews()
            }
        }
    }
    
    // MARK: - EditModeOverlay Management
    
    /// Hides the EditModeOverlay delete button specifically for drag operations
    func hideDeleteButtonForDrag() {
        // The EditModeOverlay will automatically hide during drag operations
        // based on the isBeingDragged parameter
    }
    
    /// Shows the EditModeOverlay delete button if the cell is in wiggle mode
    func showDeleteButtonIfNeeded() {
        // The EditModeOverlay will automatically show/hide based on edit mode
        // No manual intervention needed
    }
    
    // MARK: - Drag State Management
    
    /// Updates the drag state for this cell
    /// - Parameter isBeingDragged: Whether this cell is currently being dragged
    func updateDragState(_ isBeingDragged: Bool) {
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
            
            // Avoid scaling to prevent perceived resizing during drag
            self.transform = .identity
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
            
            // Keep identity transform
            self.transform = .identity
        }
        
        // Do NOT reconfigure here to avoid blinking; overlay visibility handled by coordinator using setOverlaySuppressed
    }
    
    func setOverlaySuppressed(_ suppressed: Bool) {
        suppressOverlay = suppressed
        if !suppressed {
            isLongPressed = false
            
            // When restoring overlays, add a small delay to ensure layout is fully settled
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
                guard let self = self,
                      let store = self.currentStore else { return }
                
                // Force a complete reconfiguration to ensure overlay is properly positioned
                self.configure(with: store)
                
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
    
    // MARK: - Drag Preview
    
    /// Creates a snapshot view for drag preview
    /// - Returns: A UIView snapshot of the cell's content
    func snapshotForPreview() -> UIView {
        guard let hostingController = hostingController else {
            // Fallback to a simple colored view if hosting controller is not available
            let fallbackView = UIView(frame: contentView.bounds)
            fallbackView.backgroundColor = UIColor.systemBackground
            fallbackView.layer.cornerRadius = .radiusSM
            fallbackView.layer.masksToBounds = true
            return fallbackView
        }
        
        // Create a snapshot of the hosting controller's view
        let snapshot = hostingController.view.snapshotView(afterScreenUpdates: true)
        snapshot?.layer.cornerRadius = .radiusSM
        snapshot?.layer.masksToBounds = true
        snapshot?.backgroundColor = .clear
        
        return snapshot ?? UIView()
    }

    override var isHighlighted: Bool {
        didSet {
            // Disable highlight visual
            contentView.backgroundColor = .clear
        }
    }

    override var isSelected: Bool {
        didSet {
            // Disable selection visual
            contentView.backgroundColor = .clear
        }
    }
} 
