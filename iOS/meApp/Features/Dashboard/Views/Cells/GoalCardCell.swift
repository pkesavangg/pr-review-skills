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
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        // Create a placeholder view that will be configured later
        let placeholderView = AnyView(
            GoalProgressCardView(
                delta: 0.0,
                startWeight: 0.0,
                goalWeight: 0.0,
                unit: "lbs",
                isRemoved: false,
                progress: 0.0,
                goalType: .gain,
                isWeightlessMode: false
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
    
    /// Configures the cell with dashboard store data
    /// - Parameter store: The dashboard store
    func configure(with store: DashboardStore) {
        // Set the removal state
        isRemoved = store.state.ui.isGoalCardRemoved
        
        let goalCardView = GoalProgressCardView(
            delta: store.state.goal.goalDelta,
            startWeight: store.state.goal.goalStartWeight,
            goalWeight: store.state.goal.goalWeight,
            unit: store.currentUnitText,
            isRemoved: store.state.ui.isGoalCardRemoved,
            progress: store.state.goal.goalProgress,
            goalType: store.state.goal.goalType,
            isWeightlessMode: store.isWeightlessModeEnabled
        )
        
        // Apply EditModeOverlay to the GoalProgressCardView
        let viewWithOverlay = AnyView(
            goalCardView
                .editModeOverlay(
                    isEditMode: store.state.ui.isEditMode,
                    isRemoved: store.state.ui.isGoalCardRemoved,
                    onToggleRemoval: {
                        store.toggleGoalCardRemoval()
                    },
                    isBeingDragged: store.state.ui.isGoalCardBeingDragged,
                    isDropTarget: store.state.ui.dropHoverId == "goalCard",
                    rowIndex: rowIndex,
                    disableWiggle: false
                )
        )
        
        hostingController?.rootView = viewWithOverlay
    }
    
    // MARK: - Reuse
    
    override func prepareForReuse() {
        super.prepareForReuse()
        // Reset to placeholder view
        let placeholderView = AnyView(
            GoalProgressCardView(
                delta: 0.0,
                startWeight: 0.0,
                goalWeight: 0.0,
                unit: "lbs",
                isRemoved: false,
                progress: 0.0,
                goalType: .gain,
                isWeightlessMode: false
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
        case .lifting, .dragging:
            // Don't reduce opacity during drag - let EditModeOverlay handle visibility
            // This prevents items from appearing "removed" during drag operations
            hostingController?.view.alpha = 1.0
        @unknown default:
            break
        }
    }
    
    // MARK: - Wiggle Animation
    
    override func layoutSubviews() {
        super.layoutSubviews()
        // Only wiggle if not removed and in wiggle mode
        if isWiggling && !isRemoved {
            contentView.startWiggleWithRowIndex(rowIndex)
        } else {
            contentView.stopWiggle()
        }
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
    
    // MARK: - Drag Preview
    
    /// Creates a snapshot view for drag preview
    /// - Returns: A UIView snapshot of the cell's content
    func snapshotForPreview() -> UIView {
        guard let hostingController = hostingController else {
            // Fallback to a simple colored view if hosting controller is not available
            let fallbackView = UIView(frame: contentView.bounds)
            fallbackView.backgroundColor = UIColor.systemBackground
            fallbackView.layer.cornerRadius = 16
            fallbackView.layer.masksToBounds = true
            return fallbackView
        }
        
        // Create a snapshot of the hosting controller's view
        let snapshot = hostingController.view.snapshotView(afterScreenUpdates: true)
        snapshot?.layer.cornerRadius = 16
        snapshot?.layer.masksToBounds = true
        snapshot?.backgroundColor = .clear
        
        return snapshot ?? UIView()
    }
} 
