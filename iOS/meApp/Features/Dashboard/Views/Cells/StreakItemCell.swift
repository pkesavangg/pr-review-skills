// MARK: - Enhanced StreakCardCell with Wiggle Animation

import UIKit
import SwiftUI

class StreakCardCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private var hostingController: UIHostingController<AnyView>?
    
    // MARK: - Wiggle Animation Properties
    
    var isWiggling: Bool = false {
        didSet {
            if !isLongPressed && !isTapped {
                layoutSubviews()
            }
        }
    }
    
    var isRemoved: Bool = false {
        didSet {
            if !isLongPressed && !isTapped {
                layoutSubviews()
            }
        }
    }
    
    var rowIndex: Int = 0 {
        didSet {
            if isWiggling && !isRemoved && !isLongPressed && !isTapped {
                layoutSubviews()
            }
        }
    }
    
    // MARK: - Drag State Properties
    
    private var isLongPressed: Bool = false
    private var isTapped: Bool = false
    public var representedItem: MetricItem?
    private var currentStore: DashboardStore?
    
    // MARK: - Configuration
    
    func configure(with item: MetricItem, store: DashboardStore) {
        representedItem = item
        currentStore = store
        
        // Set the removal state
        isRemoved = store.isStreakRemoved(item.label)
        
        let streakCardView = StreakCardView(
            value: item.value,
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
        
        let viewWithOverlay = AnyView(
            streakCardView
                .editModeOverlay(
                    isEditMode: store.state.ui.isEditMode,
                    isRemoved: isRemoved,
                    onToggleRemoval: {
                        store.toggleStreakRemoval(item.label)
                    },
                    isBeingDragged: store.state.ui.draggingStreak?.id == item.id || isLongPressed || isTapped,
                    isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
                    rowIndex: rowIndex,
                    disableWiggle: false
                )
        )
        
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

        ensureProperSize()
        setNeedsLayout()
        layoutIfNeeded()

        if let hostingView = hostingController?.view {
            hostingView.frame = contentView.bounds
            hostingView.bounds = contentView.bounds
        }
    }
 
    func ensureProperSize() {
        contentView.transform = .identity
        hostingController?.view.transform = .identity

        contentView.frame = bounds
        hostingController?.view.frame = contentView.bounds

        if let hostingView = hostingController?.view {
            hostingView.frame = contentView.bounds
            hostingView.bounds = contentView.bounds
            hostingView.translatesAutoresizingMaskIntoConstraints = false
            hostingView.setNeedsLayout()
            hostingView.layoutIfNeeded()
        }
 
        contentView.setNeedsLayout()
        contentView.layoutIfNeeded()
    }
    
    // MARK: - Reuse
    
    override func prepareForReuse() {
        super.prepareForReuse()
        representedItem = nil
        currentStore = nil
        isLongPressed = false
        isTapped = false
        
        // Stop any ongoing wiggle animation
        contentView.stopWiggle()
        isWiggling = false
        isRemoved = false
        rowIndex = 0
        
        // Ensure proper size is maintained after reuse
        ensureProperSize()
        
        // Reset all transforms and frames to prevent size issues
        contentView.transform = .identity
        hostingController?.view.transform = .identity
        contentView.frame = bounds
        hostingController?.view.frame = contentView.bounds
        
        // Final size check to ensure no unexpected changes
        if let hostingView = hostingController?.view {
            hostingView.frame = contentView.bounds
            hostingView.bounds = contentView.bounds
        }
    }
    
    // MARK: - Drag State Handling

    func updateDragState(_ isBeingDragged: Bool) {
        isLongPressed = isBeingDragged
        isTapped = isBeingDragged

        if isBeingDragged {

            if let item = representedItem, let store = currentStore {
                let currentFrame = contentView.frame
                let currentBounds = contentView.bounds
                configure(with: item, store: store)
                contentView.frame = currentFrame
                contentView.bounds = currentBounds
                hostingController?.view.frame = currentFrame
                hostingController?.view.bounds = currentBounds
            }
            
            maintainSizeDuringDrag()

            contentView.setNeedsLayout()
            hostingController?.view.setNeedsLayout()
        } else {

            if let item = representedItem, let store = currentStore {
                configure(with: item, store: store)
            }
            
            ensureProperSize()
            
            // Allow normal layout after drag ends
            contentView.setNeedsLayout()
            hostingController?.view.setNeedsLayout()
        }
    }
    
    /// Ensures the cell maintains its size during drag operations
    func maintainSizeDuringDrag() {

        contentView.transform = .identity
        hostingController?.view.transform = .identity

        contentView.frame = bounds
        hostingController?.view.frame = contentView.bounds

        contentView.setNeedsLayout()
        hostingController?.view.setNeedsLayout()

        if let hostingView = hostingController?.view {
            hostingView.frame = contentView.bounds
            hostingView.bounds = contentView.bounds

            hostingView.translatesAutoresizingMaskIntoConstraints = false
            hostingView.setNeedsLayout()
            hostingView.layoutIfNeeded()
        }

        contentView.setNeedsLayout()
        contentView.layoutIfNeeded()
    }
    
    override func dragStateDidChange(_ dragState: UICollectionViewCell.DragState) {
        super.dragStateDidChange(dragState)
        
        switch dragState {
        case .none:
            // Restore full opacity when drag ends
            hostingController?.view.alpha = 1.0
            // Clear interaction states to show overlay
            isLongPressed = false
            isTapped = false
            // Reconfigure to show overlay after drag ends
            if let item = representedItem, let store = currentStore {
                configure(with: item, store: store)
            }
        case .lifting, .dragging:

            hostingController?.view.alpha = 1.0

            isLongPressed = true
            isTapped = true

            if let item = representedItem, let store = currentStore {
                let currentFrame = contentView.frame
                let currentBounds = contentView.bounds

                configure(with: item, store: store)
                
                contentView.frame = currentFrame
                contentView.bounds = currentBounds
                hostingController?.view.frame = currentFrame
                hostingController?.view.bounds = currentBounds
            }
            maintainSizeDuringDrag()
            contentView.setNeedsLayout()
            hostingController?.view.setNeedsLayout()
        @unknown default:
            break
        }
    }
    
    // MARK: - Wiggle Animation
    
    override func setNeedsLayout() {
        if !isLongPressed && !isTapped {
            super.setNeedsLayout()
        } else {
            maintainSizeDuringDrag()
        }
    }
    
    override func layoutIfNeeded() {
        if !isLongPressed && !isTapped {
            super.layoutIfNeeded()
        } else {
            maintainSizeDuringDrag()
        }
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        
        // Only wiggle if not removed and in wiggle mode
        if isWiggling && !isRemoved {
            contentView.startWiggleWithRowIndex(rowIndex)
        } else {
            contentView.stopWiggle()
        }

        if isLongPressed || isTapped {
            maintainSizeDuringDrag()
        } else {
            ensureProperSize()
        }
        if let hostingView = hostingController?.view {
            hostingView.frame = contentView.bounds
            hostingView.bounds = contentView.bounds
        }
    }
    
    func restartWiggleAnimation() {
        if isWiggling && !isRemoved {
            contentView.stopWiggle()
            contentView.startWiggleWithRowIndex(rowIndex)
            ensureProperSize()
            setNeedsLayout()
            layoutIfNeeded()
        }
    }

    func stopWiggleAnimation() {
        contentView.stopWiggle()
        ensureProperSize()
        setNeedsLayout()
        layoutIfNeeded()
    }

    override var isHighlighted: Bool {
        didSet {
            // Disable highlight visual but maintain size
            contentView.backgroundColor = .clear
            if isHighlighted {
                ensureProperSize()
                setNeedsLayout()
                layoutIfNeeded()
            }
        }
    }

    override var isSelected: Bool {
        didSet {
            // Disable selection visual but maintain size
            contentView.backgroundColor = .clear

            if isSelected {
                ensureProperSize()
                setNeedsLayout()
                layoutIfNeeded()
            }
        }
    }

    // MARK: - Drag Preview
    /// Creates a snapshot view for drag preview
    /// - Returns: A UIView snapshot of the cell's content
    func snapshotForPreview() -> UIView {
        guard let hostingController = hostingController else {
            let fallbackView = UIView(frame: contentView.bounds)
            fallbackView.backgroundColor = UIColor.systemBackground
            fallbackView.layer.cornerRadius = 16
            fallbackView.layer.masksToBounds = true
            return fallbackView
        }
        
        let snapshot = hostingController.view.snapshotView(afterScreenUpdates: true)
        snapshot?.frame = contentView.bounds
        snapshot?.layer.cornerRadius = 16
        snapshot?.layer.masksToBounds = true
        snapshot?.backgroundColor = .clear
        return snapshot ?? UIView(frame: contentView.bounds)
    }
} 
