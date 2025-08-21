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
}

