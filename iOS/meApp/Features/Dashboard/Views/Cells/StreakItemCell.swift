//
//  StreakItemCell.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import UIKit
import SwiftUI

/// Custom UICollectionViewCell that represents a streak item as a medium widget
/// Features include wiggle animation and iOS home screen-like behavior
/// Uses StreakCardView SwiftUI component for UI with EditModeOverlay
class StreakItemCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private let hostingController: UIHostingController<AnyView>?
    
    // MARK: - Public Accessors
    
    var representedItem: MetricItem?
    var onDeleteTapped: (() -> Void)?
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        // Create a placeholder view that will be configured later
        let placeholderView = AnyView(
            StreakCardView(
                value: "0",
                label: "Placeholder",
                icon: nil,
                isEditMode: false,
                isRemoved: false,
                isDropTarget: false,
                onToggleRemoval: {},
                onDrop: { _, _ in false },
                onDropTargetChanged: { _ in }
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
    ///   - store: The dashboard store for formatting
    func configure(with item: MetricItem, store: DashboardStore) {
        representedItem = item
        
        // Determine if this item is removed
        let itemIsRemoved = store.isStreakRemovedInReorderedArray(at: store.streakItemsToShow.firstIndex(where: { $0.id == item.id }) ?? 0)
        isRemoved = itemIsRemoved
        
        let streakCardView = StreakCardView(
            value: item.value,
            label: item.label,
            icon: item.icon,
            isEditMode: store.state.ui.isEditMode,
            isRemoved: itemIsRemoved,
            isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
            onToggleRemoval: {
                if let index = store.streakItemsToShow.firstIndex(where: { $0.id == item.id }) {
                    store.toggleStreakRemovalInReorderedArray(at: index)
                }
            },
            onDrop: { _, _ in false }, // Drag and drop handled by UIKit
            onDropTargetChanged: { _ in }
        )
        
        // Apply EditModeOverlay to the StreakCardView
        let viewWithOverlay = AnyView(
            streakCardView
                .editModeOverlay(
                    isEditMode: store.state.ui.isEditMode,
                    isRemoved: itemIsRemoved,
                    onToggleRemoval: {
                        if let index = store.streakItemsToShow.firstIndex(where: { $0.id == item.id }) {
                            store.toggleStreakRemovalInReorderedArray(at: index)
                        }
                    },
                    isBeingDragged: store.state.ui.draggingStreak?.id == item.id,
                    isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
                    rowIndex: rowIndex,
                    disableWiggle: false
                )
        )
        
        hostingController?.rootView = viewWithOverlay
    }
    
    // MARK: - Reuse
    
    override func prepareForReuse() {
        super.prepareForReuse()
        representedItem = nil
        
        // Reset to placeholder view
        let placeholderView = AnyView(
            StreakCardView(
                value: "0",
                label: "Placeholder",
                icon: nil,
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
