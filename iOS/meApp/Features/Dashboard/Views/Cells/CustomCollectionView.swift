//
//  CustomCollectionView.swift
//  meApp
//
//  Created by Lakshmi Priya on 08/08/25.
//

import UIKit

// MARK: - Custom Collection View

public class CustomCollectionView: UICollectionView {
    private var contentSizeObserver: NSKeyValueObservation?
    private var lastBoundsSize: CGSize = .zero
    public var hideDragPlatter: Bool = false
    public var suspendIntrinsicInvalidation: Bool = false
    public var isInDragOperation: Bool = false
    
    // Boundary detection support (used by goal/streak grid for strict boundaries)
    public var boundaryDetector: GridBoundaryDetector?
    
    public override init(frame: CGRect, collectionViewLayout layout: UICollectionViewLayout) {
        super.init(frame: frame, collectionViewLayout: layout)
        setupIntrinsicSizeObserver()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupIntrinsicSizeObserver()
    }
    
    override public func didAddSubview(_ subview: UIView) {
        super.didAddSubview(subview)
        if hideDragPlatter {
            let className = String(describing: type(of: subview))
            if className.contains("Platter") || className.contains("Preview") || className.contains("Drag") || className.contains("Drop") {
                subview.alpha = 0
                subview.layer.removeAllAnimations()
            }
        }
    }
    
    // Override intrinsic content size to ensure proper sizing
    override public var intrinsicContentSize: CGSize {
        let contentSize = self.collectionViewLayout.collectionViewContentSize
        
        let height = contentSize.height > 0 ? contentSize.height : calculateEstimatedHeight()
        
        return CGSize(width: UIView.noIntrinsicMetric, height: height)
    }
    
    public override func layoutSubviews() {
        let previousSize = lastBoundsSize
        super.layoutSubviews()
        // If bounds changed (e.g., after tab switch), invalidate layout and intrinsic size
        if bounds.size != previousSize {
            self.collectionViewLayout.invalidateLayout()
            self.invalidateIntrinsicContentSize()
            lastBoundsSize = bounds.size
        }
    }
    
    private func setupIntrinsicSizeObserver() {
        // Observe contentSize changes to update intrinsic size immediately when data/layout changes
        contentSizeObserver = observe(\.contentSize, options: [.new]) { [weak self] _, _ in
            guard let self = self else { return }
            if self.suspendIntrinsicInvalidation { return }
            self.invalidateIntrinsicContentSize()
        }
    }
    
    /// Calculates estimated height when collection view content size is not yet available
    /// This prevents layout jumping during initial load
    private func calculateEstimatedHeight() -> CGFloat {
        // Get data source item count
        guard let dataSource = self.dataSource else { return 100 }
        let itemCount = dataSource.collectionView(self, numberOfItemsInSection: 0)
        
        // Estimate based on typical cell sizes and layout
        if let flowLayout = collectionViewLayout as? UICollectionViewFlowLayout {
            // Use flow layout spacing and insets for calculation
            let availableWidth = bounds.width - flowLayout.sectionInset.left - flowLayout.sectionInset.right
            
            // Estimate columns based on available width and typical item size
            let estimatedItemWidth: CGFloat = 120 // Typical metric card width
            let estimatedColumns = max(1, Int(availableWidth / (estimatedItemWidth + flowLayout.minimumInteritemSpacing)))
            let estimatedRows = Int(ceil(Double(itemCount) / Double(estimatedColumns)))
            
            // Calculate estimated height with increased bottom padding for last row
            let estimatedItemHeight: CGFloat = 70 // Typical metric card height
            let totalSpacing = CGFloat(max(0, estimatedRows - 1)) * flowLayout.minimumLineSpacing
            let totalInsets = flowLayout.sectionInset.top + flowLayout.sectionInset.bottom
            
            // Add optimal bottom padding for last row items during drag operations
            let extraBottomPadding: CGFloat = 20 // Fine-tuned additional space for last row
            return CGFloat(estimatedRows) * estimatedItemHeight + totalSpacing + totalInsets + extraBottomPadding
        }
        
        // Fallback estimation for other layout types with extra bottom padding
        let estimatedRowHeight: CGFloat = 102 // Item height + spacing
        let estimatedColumns = bounds.width > 500 ? 4 : (itemCount > 4 ? 3 : 2) // Device-based estimation
        let estimatedRows = Int(ceil(Double(itemCount) / Double(estimatedColumns)))
        
        // Add optimal bottom padding for last row items
        let extraBottomPadding: CGFloat = 20
        return max(100, CGFloat(estimatedRows) * estimatedRowHeight + 40 + extraBottomPadding) 
    }
    
    // MARK: - Animation Suppression Methods
    
    /// Performs batch updates with smooth animations for drag operations
    override public func performBatchUpdates(_ updates: (() -> Void)?, completion: ((Bool) -> Void)? = nil) {
        if isInDragOperation {
            // Use smooth animations during drag for beautiful cell movement
            super.performBatchUpdates(updates, completion: completion)
        } else {
            // Scope animation disabling to only this collection view
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            
            // Only disable animations on this specific layer, not globally
            let originalActions = self.layer.actions
            self.layer.actions = ["position": NSNull(), "bounds": NSNull(), "transform": NSNull()]
            
            super.performBatchUpdates(updates, completion: nil)
            
            // Restore this collection view's layer actions and commit transaction
            self.layer.actions = originalActions
            CATransaction.commit()
            
            // Call the original completion block if provided
            completion?(true)
        }
    }

    override public func reloadData() {
        // Scope animation disabling to only this collection view
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        CATransaction.setAnimationDuration(0)
        
        // Only disable animations on this specific layer, not globally
        let originalActions = self.layer.actions
        self.layer.actions = ["position": NSNull(), "bounds": NSNull(), "transform": NSNull()]
        
        super.reloadData()
        
        // Restore this collection view's layer actions
        self.layer.actions = originalActions
        CATransaction.commit()
    }

    override public func reloadItems(at indexPaths: [IndexPath]) {
        // Scope animation disabling to only this collection view
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        CATransaction.setAnimationDuration(0)
        
        // Only disable animations on this specific layer, not globally
        let originalActions = self.layer.actions
        self.layer.actions = ["position": NSNull(), "bounds": NSNull(), "transform": NSNull()]
        
        super.reloadItems(at: indexPaths)
        
        // Restore this collection view's layer actions
        self.layer.actions = originalActions
        CATransaction.commit()
    }
}
