//
//  LeadingAlignedFlowLayout.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import UIKit

/// Custom UICollectionViewFlowLayout that ensures items always align to the leading edge
/// This prevents items from being centered when there are fewer items than available space
/// Useful for grid layouts where items should always start from the left edge
class LeadingAlignedFlowLayout: UICollectionViewFlowLayout {
    
    override func layoutAttributesForElements(in rect: CGRect) -> [UICollectionViewLayoutAttributes]? {

        guard let attributes = super.layoutAttributesForElements(in: rect)?
            .compactMap({ $0.copy() as? UICollectionViewLayoutAttributes }) else {
            return nil
        }
        
        // Only adjust cell attributes; leave supplementary/decoration untouched
        let cellAttributes = attributes.filter { $0.representedElementCategory == .cell }
            .sorted { lhs, rhs in
                // Stable sort by row (y), then x to process in visual order
                if abs(lhs.frame.origin.y - rhs.frame.origin.y) > 1.0 {
                    return lhs.frame.origin.y < rhs.frame.origin.y
                } else {
                    return lhs.frame.origin.x < rhs.frame.origin.x
                }
            }
        
        let leftInset = sectionInset.left
        let interItem = minimumInteritemSpacing
        var currentRowY: CGFloat = -CGFloat.greatestFiniteMagnitude
        var leftMargin: CGFloat = leftInset
        
        for attr in cellAttributes {
            let y = attr.frame.origin.y
            // Detect new row using small epsilon to account for floating-point drift during reordering
            if abs(y - currentRowY) > 1.0 {
                currentRowY = y
                leftMargin = leftInset
            }
            var frame = attr.frame
            frame.origin.x = leftMargin
            attr.frame = frame
            leftMargin += frame.width + interItem
        }
        
        return attributes
    }

    override func shouldInvalidateLayout(forBoundsChange newBounds: CGRect) -> Bool {
        // Invalidate on bounds changes (e.g., during interactive movement) to keep alignment correct
        // Only invalidate layout if the width changes to improve performance
        return newBounds.width != collectionView?.bounds.width
    }
} 
