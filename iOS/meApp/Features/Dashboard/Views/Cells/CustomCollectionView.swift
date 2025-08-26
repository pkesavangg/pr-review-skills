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
        return CGSize(width: UIView.noIntrinsicMetric, height: contentSize.height)
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
    
    // MARK: - Animation Suppression Methods
    
    /// Performs batch updates with smooth animations for drag operations
    override public func performBatchUpdates(_ updates: (() -> Void)?, completion: ((Bool) -> Void)? = nil) {
        if isInDragOperation {
            // Use smooth animations during drag for beautiful cell movement
            super.performBatchUpdates(updates, completion: completion)
        } else {
            // Use instant updates for other operations to prevent jumps
            let animationsWereEnabled = UIView.areAnimationsEnabled
            UIView.setAnimationsEnabled(false)
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            CATransaction.setAnimationDuration(0)
            UIView.performWithoutAnimation {
                super.performBatchUpdates(updates, completion: { finished in
                    CATransaction.commit()
                    DispatchQueue.main.async {
                        UIView.setAnimationsEnabled(animationsWereEnabled)
                    }
                    completion?(finished)
                })
            }
        }
    }

    override public func reloadData() {
        let animationsWereEnabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(false)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        CATransaction.setAnimationDuration(0)
        UIView.performWithoutAnimation {
            super.reloadData()
        }
        CATransaction.commit()
        DispatchQueue.main.async {
            UIView.setAnimationsEnabled(animationsWereEnabled)
        }
    }

    override public func reloadItems(at indexPaths: [IndexPath]) {
        let animationsWereEnabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(false)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        CATransaction.setAnimationDuration(0)
        UIView.performWithoutAnimation {
            super.reloadItems(at: indexPaths)
        }
        CATransaction.commit()
        DispatchQueue.main.async {
            UIView.setAnimationsEnabled(animationsWereEnabled)
        }
    }
}

