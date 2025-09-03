//
//  GridBoundaryDetector.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import UIKit

/// Shared utility for boundary detection across all grid components
/// Provides consistent boundary detection behavior for drag and drop operations
class GridBoundaryDetector {
    
    // MARK: - Properties
    
    private var gridBounds: CGRect = .zero
    private var isDragOutsideBounds: Bool = false
    private var boundaryFeedbackGenerator: UIImpactFeedbackGenerator?
    
    // MARK: - Initialization
    
    init() {
        self.boundaryFeedbackGenerator = UIImpactFeedbackGenerator(style: .medium)
    }
    
    // MARK: - Boundary Detection Methods
    
    /// Updates the grid bounds for boundary detection
    /// Uses precise grid dimensions: height = grid content height, width = screen width
    func updateGridBounds(for collectionView: UICollectionView) {
        // Get the actual content size of the collection view layout
        let contentSize = collectionView.collectionViewLayout.collectionViewContentSize
        let contentInsets = collectionView.contentInset
        
        // Calculate precise grid content height including insets
        let actualGridHeight = contentSize.height + contentInsets.top + contentInsets.bottom
        
        // Use screen width for horizontal boundaries
        let screenWidth = UIScreen.main.bounds.width
        
        // Get collection view's position in its superview for Y positioning
        let collectionViewFrame = collectionView.frame
        
        // Set precise boundaries:
        // - X: 0 to screen width (full screen width)
        // - Y: collection view's Y position to Y position + actual grid height
        gridBounds = CGRect(
            x: 0,
            y: collectionViewFrame.origin.y,
            width: screenWidth,
            height: actualGridHeight
        )
        
        // Debug logging for boundary testing
        #if DEBUG
        print("GridBoundaryDetector - Screen Width: \(screenWidth), Grid Height: \(actualGridHeight)")
        print("GridBoundaryDetector Bounds: \(gridBounds)")
        print("Collection View Frame: \(collectionViewFrame), Content Size: \(contentSize)")
        #endif
    }
    
    /// Checks if a drag location is within the precise grid boundaries
    /// Uses superview coordinates to check against exact grid dimensions
    func isDragLocationWithinBounds(_ location: CGPoint, in view: UIView) -> Bool {
        guard let collectionView = view as? UICollectionView else { return false }
        guard let superview = collectionView.superview else { return false }
        
        updateGridBounds(for: collectionView)
        
        // Convert drag location from collection view to superview coordinates
        let locationInSuperview = collectionView.convert(location, to: superview)
        
        // Check against precise grid boundaries in superview coordinate system
        let isWithinPreciseBounds = gridBounds.contains(locationInSuperview)
        
        return isWithinPreciseBounds
    }
    
    /// Provides haptic feedback when drag crosses boundary
    func provideBoundaryFeedback() {
        boundaryFeedbackGenerator?.prepare()
        boundaryFeedbackGenerator?.impactOccurred()
    }
    
    /// Updates drag state based on boundary detection
    /// - Parameters:
    ///   - isOutside: Whether the drag is currently outside bounds
    ///   - collectionView: The collection view being dragged in
    ///   - draggedItemId: The ID of the item being dragged
    ///   - updateCellBoundaryState: Closure to update the specific cell's boundary state
    func updateDragBoundaryState(
        _ isOutside: Bool,
        for collectionView: UICollectionView,
        draggedItemId: String?,
        updateCellBoundaryState: @escaping (String?, Bool) -> Void
    ) {
        guard isDragOutsideBounds != isOutside else { return }
        
        isDragOutsideBounds = isOutside
        
        if isOutside {
            // Provide haptic feedback when crossing boundary
            provideBoundaryFeedback()
            
            // Update visual feedback on dragged cell
            updateCellBoundaryState(draggedItemId, true)
        } else {
            // Restore normal drag appearance when back within bounds
            updateCellBoundaryState(draggedItemId, false)
        }
    }
    
    /// Updates the visual state of the dragged cell based on boundary status
    /// - Parameters:
    ///   - isOutsideBounds: Whether the drag is outside bounds
    ///   - collectionView: The collection view
    ///   - draggedItemId: The ID of the dragged item
    ///   - updateCellCallback: Closure to update the specific cell's boundary state
    func updateDraggedCellBoundaryState(
        isOutsideBounds: Bool,
        in collectionView: UICollectionView,
        draggedItemId: String?,
        updateCellCallback: @escaping (UICollectionViewCell, Bool) -> Void
    ) {
        guard draggedItemId != nil else { return }
        
        // Find the dragged cell and update its appearance
        for cell in collectionView.visibleCells {
            updateCellCallback(cell, isOutsideBounds)
        }
    }
    
    // MARK: - Boundary State Management
    
    /// Gets the current boundary state
    var isCurrentlyOutsideBounds: Bool {
        return isDragOutsideBounds
    }
    
    /// Resets the boundary state
    func resetBoundaryState() {
        isDragOutsideBounds = false
        gridBounds = .zero
    }
    
    /// Returns the current grid bounds
    func getGridBounds() -> CGRect {
        return gridBounds
    }
}


