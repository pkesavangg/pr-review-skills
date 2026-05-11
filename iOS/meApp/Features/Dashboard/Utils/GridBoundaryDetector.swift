//
//  GridBoundaryDetector.swift
//  meApp
//
//  Created by Lakshmipriya on 02/07/25.
//

import UIKit

/// Shared utility for boundary detection across all grid components
/// Provides consistent boundary detection behavior for drag and drop operations
public class GridBoundaryDetector {
    
    // MARK: - Boundary Configuration
    
    public struct BoundaryConstraints: Hashable {
        public let maxWidth: CGFloat
        public let maxHeight: CGFloat
        public let minY: CGFloat
        public let excludeZones: [CGRect] // Areas where dragging is not allowed
        
        public init(maxWidth: CGFloat, maxHeight: CGFloat, minY: CGFloat, excludeZones: [CGRect]) {
            self.maxWidth = maxWidth
            self.maxHeight = maxHeight
            self.minY = minY
            self.excludeZones = excludeZones
        }
        
        public func hash(into hasher: inout Hasher) {
            hasher.combine(maxWidth)
            hasher.combine(maxHeight)
            hasher.combine(minY)
            hasher.combine(excludeZones.count)
            for zone in excludeZones {
                hasher.combine(zone.origin.x)
                hasher.combine(zone.origin.y)
                hasher.combine(zone.size.width)
                hasher.combine(zone.size.height)
            }
        }
        
        public static let metric = BoundaryConstraints(
            maxWidth: UIScreen.main.bounds.width,
            maxHeight: .greatestFiniteMagnitude,
            minY: 0,
            excludeZones: []
        )
        
        public static func goalStreak(gridHeight: CGFloat, dividerY: CGFloat) -> BoundaryConstraints {
            return BoundaryConstraints(
                maxWidth: UIScreen.main.bounds.width,
                maxHeight: gridHeight,
                minY: 0,
                excludeZones: [
                    // Keep a more compact no-go zone just above the divider (reduced height)
                    CGRect(x: 0, y: max(0, dividerY - 16), width: UIScreen.main.bounds.width, height: 16)
                ]
            )
        }
    }
    
    // MARK: - Properties
    
    private var gridBounds: CGRect = .zero
    private var isDragOutsideBounds: Bool = false
    private var boundaryFeedbackGenerator: UIImpactFeedbackGenerator?
    private var currentConstraints: BoundaryConstraints = .metric
    
    // Cache to prevent excessive recalculation and logging
    private var lastCollectionViewFrame: CGRect = .zero
    private var lastContentSize: CGSize = .zero
    private var lastConstraintsHash: Int = 0
    
    // MARK: - Initialization
    
    public init() {
        self.currentConstraints = .metric
        self.boundaryFeedbackGenerator = UIImpactFeedbackGenerator(style: .medium)
    }
    
    // MARK: - Configuration Methods
    
    /// Updates the boundary constraints for goal/streak grid
    /// - Parameters:
    ///   - gridHeight: Total height of the goal/streak grid
    ///   - dividerY: Y position of the divider that streak items should not touch
    public func updateGoalStreakConstraints(gridHeight: CGFloat, dividerY: CGFloat) {
        currentConstraints = .goalStreak(gridHeight: gridHeight, dividerY: dividerY)
    }
    
    // MARK: - Boundary Detection Methods
    
    /// Updates the grid bounds for boundary detection
    /// Uses precise grid dimensions: height = grid content height, width = screen width
    /// Uses caching to prevent excessive recalculation during drag operations
    public func updateGridBounds(for collectionView: UICollectionView) {
        // Get the actual content size of the collection view layout
        let contentSize = collectionView.collectionViewLayout.collectionViewContentSize
        let contentInsets = collectionView.contentInset
        let collectionViewFrame = collectionView.frame
        let constraintsHash = currentConstraints.hashValue
        
        // Check if we need to recalculate (cache optimization)
        if collectionViewFrame == lastCollectionViewFrame && 
           contentSize == lastContentSize && 
           constraintsHash == lastConstraintsHash {
            // No need to recalculate, use cached values
            return
        }
        
        // Update cache
        lastCollectionViewFrame = collectionViewFrame
        lastContentSize = contentSize
        lastConstraintsHash = constraintsHash
        
        // Calculate precise grid content height including insets
        let actualGridHeight = contentSize.height + contentInsets.top + contentInsets.bottom
        
        // Apply constraints based on grid type
        let constrainedWidth = min(currentConstraints.maxWidth, UIScreen.main.bounds.width)
        let constrainedHeight = min(currentConstraints.maxHeight, actualGridHeight)
        
        // Set precise boundaries:
        // - X: 0 to constrained width
        // - Y: collection view's Y position + minY to Y position + constrained height
        gridBounds = CGRect(
            x: 0,
            y: collectionViewFrame.origin.y + currentConstraints.minY,
            width: constrainedWidth,
            height: constrainedHeight
        )
    }
    
    /// Checks if a drag location is within the precise grid boundaries
    /// Uses superview coordinates to check against exact grid dimensions
    public func isDragLocationWithinBounds(_ location: CGPoint, in view: UIView) -> Bool {
        guard let collectionView = view as? UICollectionView else { return false }
        guard let superview = collectionView.superview else { return false }
        
        updateGridBounds(for: collectionView)
        
        // Convert drag location from collection view to superview coordinates
        let locationInSuperview = collectionView.convert(location, to: superview)
        
        // Check against precise grid boundaries in superview coordinate system
        let isWithinPreciseBounds = gridBounds.contains(locationInSuperview)
        
        // If within basic bounds, check exclude zones
        if isWithinPreciseBounds {
            // Check if location is in any exclude zone
            for excludeZone in currentConstraints.excludeZones where excludeZone.contains(locationInSuperview) {
                return false // Location is in an excluded area
            }
            return true // Within bounds and not in any exclude zone
        }
        
        return false // Outside basic bounds
    }
    
    /// Checks if a drag can be allowed at the given location
    /// Returns false if the drag should be blocked (prevents drag operation entirely)
    public func canDragAtLocation(_ location: CGPoint, in view: UIView) -> Bool {
        return isDragLocationWithinBounds(location, in: view)
    }
    
    /// Constrains a drag location to stay within allowed boundaries
    /// Returns the constrained location that respects boundary limits
    /// Implements strict boundary enforcement similar to SwiftUI drag limiting
    public func constrainDragLocation(_ location: CGPoint, in view: UIView) -> CGPoint {
        guard let collectionView = view as? UICollectionView else { return location }
        guard let superview = collectionView.superview else { return location }
        
        updateGridBounds(for: collectionView)
        
        // Convert drag location from collection view to superview coordinates
        let locationInSuperview = collectionView.convert(location, to: superview)
        
        // Apply strict constraints - similar to SwiftUI drag limiting
        var constrainedLocation = locationInSuperview
        
        // First, constrain to basic grid bounds
        constrainedLocation.x = max(gridBounds.minX, min(gridBounds.maxX, locationInSuperview.x))
        constrainedLocation.y = max(gridBounds.minY, min(gridBounds.maxY, locationInSuperview.y))
        
        // Apply STRICT exclude zone constraints - prevent entering forbidden areas entirely
        for excludeZone in currentConstraints.excludeZones {
            // Use a more compact buffer so last row has maximum usable space
            let strictBufferZone = excludeZone.insetBy(dx: -4, dy: -4)
            
            if strictBufferZone.contains(constrainedLocation) {
                // For STRICT boundaries, always push to the top edge (safest direction away from divider)
                // This ensures items can NEVER get close to the divider area
                constrainedLocation.y = strictBufferZone.minY - 1 // Minimal safety margin
                
                // Ensure we don't go outside horizontal bounds
                constrainedLocation.x = max(gridBounds.minX, min(gridBounds.maxX, constrainedLocation.x))
                
                // Re-constrain to ensure we're still within grid bounds
                constrainedLocation.y = max(gridBounds.minY, constrainedLocation.y)
            }
        }
        
        // Convert back to collection view coordinates
        let finalPoint = superview.convert(constrainedLocation, to: collectionView)
        return finalPoint
    }
    
    /// Calculates a strictly constrained frame for a dragged item
    /// Similar to SwiftUI's drag constraint approach
    public func constrainDragFrame(_ frame: CGRect, in view: UIView) -> CGRect {
        guard let collectionView = view as? UICollectionView else { return frame }
        guard let superview = collectionView.superview else { return frame }
        
        updateGridBounds(for: collectionView)
        
        // Convert frame to superview coordinates
        let frameInSuperview = collectionView.convert(frame, to: superview)
        var constrainedFrame = frameInSuperview
        
        // Constrain the entire frame to stay within grid bounds
        // Ensure the frame doesn't go outside the allowed area
        constrainedFrame.origin.x = max(gridBounds.minX, min(gridBounds.maxX - constrainedFrame.width, frameInSuperview.origin.x))
        constrainedFrame.origin.y = max(gridBounds.minY, min(gridBounds.maxY - constrainedFrame.height, frameInSuperview.origin.y))
        
        // Check against exclude zones - ensure the entire frame doesn't intersect
        for excludeZone in currentConstraints.excludeZones {
            let bufferZone = excludeZone.insetBy(dx: -6, dy: -6) // Reduced 6pt buffer for more space
            
            if constrainedFrame.intersects(bufferZone) {
                // Move the frame to avoid intersection
                let frameBottom = constrainedFrame.maxY
                let frameTop = constrainedFrame.minY
                let frameRight = constrainedFrame.maxX
                let frameLeft = constrainedFrame.minX
                
                // Calculate distances to move frame outside buffer zone
                let moveUp = bufferZone.minY - frameBottom
                let moveDown = frameTop - bufferZone.maxY
                let moveLeft = bufferZone.minX - frameRight
                let moveRight = frameLeft - bufferZone.maxX
                
                // Choose the smallest movement that avoids intersection
                // Only consider movements that actually resolve the intersection (directionally valid)
                let movements = [
                    (abs(moveUp), CGPoint(x: 0, y: moveUp), moveUp < 0),      // move up if frame is below buffer
                    (abs(moveDown), CGPoint(x: 0, y: moveDown), moveDown > 0), // move down if frame is above buffer
                    (abs(moveLeft), CGPoint(x: moveLeft, y: 0), moveLeft < 0), // move left if frame is to the right of buffer
                    (abs(moveRight), CGPoint(x: moveRight, y: 0), moveRight > 0) // move right if frame is to the left of buffer
                ].filter { $0.2 } // Only valid movements that resolve intersection
                
                if let minMovement = movements.min(by: { $0.0 < $1.0 }) {
                    constrainedFrame.origin.x += minMovement.1.x
                    constrainedFrame.origin.y += minMovement.1.y
                }
                
                // Re-constrain to grid bounds after adjustment
                constrainedFrame.origin.x = max(gridBounds.minX, min(gridBounds.maxX - constrainedFrame.width, constrainedFrame.origin.x))
                constrainedFrame.origin.y = max(gridBounds.minY, min(gridBounds.maxY - constrainedFrame.height, constrainedFrame.origin.y))
            }
        }
        
        // Convert back to collection view coordinates
        let finalFrame = superview.convert(constrainedFrame, to: collectionView)
        return finalFrame
    }
    
    /// Provides haptic feedback when drag crosses boundary
    public func provideBoundaryFeedback() {
        boundaryFeedbackGenerator?.prepare()
        boundaryFeedbackGenerator?.impactOccurred()
    }
    
    /// Updates drag state based on boundary detection
    /// - Parameters:
    ///   - isOutside: Whether the drag is currently outside bounds
    ///   - collectionView: The collection view being dragged in
    ///   - draggedItemId: The ID of the item being dragged
    ///   - updateCellBoundaryState: Closure to update the specific cell's boundary state (deprecated)
    public func updateDragBoundaryState(
        _ isOutside: Bool,
        for collectionView: UICollectionView,
        draggedItemId: String?,
        updateCellBoundaryState: @escaping (String?, Bool) -> Void = { _, _ in }
    ) {
        guard isDragOutsideBounds != isOutside else { return }
        
        isDragOutsideBounds = isOutside
        
        if isOutside {
            // Provide haptic feedback when crossing boundary
            provideBoundaryFeedback()
            
            // Note: Visual feedback removed - drag operations are now blocked at boundaries
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
    public var isCurrentlyOutsideBounds: Bool {
        return isDragOutsideBounds
    }
    
    /// Resets the boundary state
    public func resetBoundaryState() {
        isDragOutsideBounds = false
        gridBounds = .zero
    }
    
    /// Returns the current grid bounds
    public func getGridBounds() -> CGRect {
        return gridBounds
    }
}
