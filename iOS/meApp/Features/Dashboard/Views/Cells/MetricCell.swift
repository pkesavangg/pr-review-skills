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
    func configure(with item: MetricItem, dashboardType: DashboardType, store: DashboardStore) {
        representedItem = item
        
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
                store.selectMetric(item.label)
            },
            isDropTarget: store.state.ui.dropHoverId == item.id.uuidString,
            onDrop: { _, _ in false }, // Drag and drop handled by UIKit
            onDropTargetChanged: { _ in },
            verticalPadding: dashboardType == .dashboard12 
                ? MetricCardView.twelveCardVerticalPadding 
                : MetricCardView.fourCardVerticalPadding
        )
        
        // Apply EditModeOverlay to the MetricCardView
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
                    isBeingDragged: store.state.ui.draggingMetric?.id == item.id,
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
        
        // Manage visual feedback during drag operations
        switch dragState {
        case .none:
            // Restore normal appearance
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
}

// MARK: - Wiggle Animation Extension

extension UIView {
    /// Creates a wiggle animation with specified parameters (matching movingGridsLearning exactly)
    /// - Parameters:
    ///   - duration: Animation duration
    ///   - rotationAngle: Rotation angle in radians
    /// - Returns: Configured CAKeyframeAnimation
    private func createWiggleAnimation(duration: Double, rotationAngle: Double) -> CAKeyframeAnimation {
        let transformAnim = CAKeyframeAnimation(keyPath: "transform")
        
        // Use the exact same values as movingGridsLearning for consistency
        transformAnim.values = [
            NSValue(caTransform3D: CATransform3DMakeRotation(rotationAngle, 0.0, 0.0, 1.0)),
            NSValue(caTransform3D: CATransform3DMakeRotation(-rotationAngle, 0.0, 0.0, 1.0))
        ]
        
        transformAnim.autoreverses = true
        transformAnim.duration = duration
        transformAnim.repeatCount = Float.infinity
        
        return transformAnim
    }
} 
