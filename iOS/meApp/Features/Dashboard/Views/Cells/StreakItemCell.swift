// MARK: - Enhanced StreakCardCell with Wiggle Animation

import UIKit
import SwiftUI

class StreakCardCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private var hostingController: UIHostingController<AnyView>?
    
    // MARK: - Wiggle Animation Properties
    
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
                configure(with: item, store: store)
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
                configure(with: item, store: store)
            }
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
    
    func restartWiggleAnimation() {
        if isWiggling && !isRemoved {
            contentView.stopWiggle()
            contentView.startWiggleWithRowIndex(rowIndex)
        }
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

    // MARK: - Drag Preview
    /// Creates a snapshot view for drag preview
    /// - Returns: A UIView snapshot of the cell's content
    func snapshotForPreview() -> UIView {
        if let hostingView = hostingController?.view {
            let snapshot = hostingView.snapshotView(afterScreenUpdates: true)
            snapshot?.frame = contentView.bounds
            snapshot?.layer.cornerRadius = 16
            snapshot?.layer.masksToBounds = true
            snapshot?.backgroundColor = .clear
            return snapshot ?? UIView(frame: contentView.bounds)
        } else {
            // Fallback to a simple rounded view if hosting controller is not yet set
            let fallbackView = UIView(frame: contentView.bounds)
            fallbackView.backgroundColor = UIColor.systemBackground
            fallbackView.layer.cornerRadius = 16
            fallbackView.layer.masksToBounds = true
            return fallbackView
        }
    }
} 
