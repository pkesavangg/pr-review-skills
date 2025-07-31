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
        let attributes = super.layoutAttributesForElements(in: rect)
        
        // Ensure items align to the leading edge
        var leftMargin = sectionInset.left
        var maxY: CGFloat = -1.0
        
        attributes?.forEach { layoutAttributes in
            let indexPath = layoutAttributes.indexPath
            
            // If this is a new row, reset the left margin
            if layoutAttributes.frame.origin.y >= maxY {
                leftMargin = sectionInset.left
            }
            
            // Set the x position to align to the leading edge
            layoutAttributes.frame.origin.x = leftMargin
            
            // Update left margin for next item
            leftMargin += layoutAttributes.frame.width + minimumInteritemSpacing
            
            // Update maxY for row detection
            maxY = max(maxY, layoutAttributes.frame.maxY)
        }
        
        return attributes
    }
} 