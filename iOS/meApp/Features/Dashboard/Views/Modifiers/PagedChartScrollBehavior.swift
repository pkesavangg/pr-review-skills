import SwiftUI
import Charts

/// A ChartScrollTargetBehavior that provides robust paging behavior with date alignment.
/// - Pages horizontally with 1 page for normal swipes and 2 pages for bigger swipes
/// - Handles content smaller than viewport, start/end bounds, and near-edge snapping
/// - After paging, applies date-component alignment like `.valueAligned(matching:)`
/// references: https://fatbobman.com/en/posts/mastering-swiftui-scrolling-implementing-custom-paging/
struct PagedChartScrollBehavior: ChartScrollTargetBehavior {

    enum ScrollDirection { case left, right, none }

    /// Base threshold ratio for triggering paging
    var thresholdRatio: CGFloat = 1.0 / 3.0

    /// If drag exceeds threshold * twoPageMultiplier, jump 2 pages
    var twoPageMultiplier: CGFloat = 2.2

    /// If drag is below (threshold * freeScrollMultiplier), allow in-between landing
    /// Keep this < 1.0. Example: 0.75 means "below 75% of threshold => free"
    var freeScrollMultiplier: CGFloat = 0.75

    /// The underlying value-aligned behavior for date snapping (applied after paging)
    var valueAlignedBehavior: ValueAlignedChartScrollTargetBehavior?

    init(
        thresholdRatio: CGFloat = 1.0 / 3.0,
        twoPageMultiplier: CGFloat = 15,
        freeScrollMultiplier: CGFloat = 1,
        matching: DateComponents? = nil,
        majorAlignment: DateComponents? = nil
    ) {
        self.thresholdRatio = thresholdRatio
        self.twoPageMultiplier = twoPageMultiplier
        self.freeScrollMultiplier = freeScrollMultiplier

        // Create value-aligned behavior if date components are provided
        if let matching = matching, let majorAlignment = majorAlignment {
            self.valueAlignedBehavior = ValueAlignedChartScrollTargetBehavior(
                matching: matching,
                majorAlignment: .matching(majorAlignment)
            )
        }
    }

    func updateTarget(_ target: inout ScrollTarget, context: ChartScrollTargetBehaviorContext) {
        let viewportW = context.containerSize.width
        let contentW = context.contentSize.width

        guard contentW > viewportW else {
            target.rect.origin.x = 0
            return
        }

        let maxOffset = contentW - viewportW

        // Clamp original position to valid bounds (handles rubber band state)
        let rawOriginalX = context.originalTarget.rect.minX
        let originalX = min(max(rawOriginalX, 0), maxOffset)
        let proposedX = target.rect.minX

        // Determine direction
        let direction: ScrollDirection =
            proposedX > originalX ? .left :
            (proposedX < originalX ? .right : .none)

        guard direction != .none else {
            target.rect.origin.x = originalX
            return
        }

        // Check if starting from an edge - use simpler paging from edges
        let isAtRightEdge = originalX >= maxOffset - 1
        let isAtLeftEdge = originalX <= 1

        // Remaining content used to scale threshold near edges
        let remaining: CGFloat
        if isAtRightEdge && direction == .right {
            // At right edge, scrolling left - use full viewport as threshold base
            remaining = viewportW
        } else if isAtLeftEdge && direction == .left {
            // At left edge, scrolling right - use full viewport as threshold base
            remaining = viewportW
        } else {
            remaining = (direction == .left)
                ? (contentW - context.originalTarget.rect.maxX)
                : context.originalTarget.rect.minX
        }

        let thresholdBase = min(max(remaining, viewportW * 0.1), viewportW)
        let threshold = thresholdBase * thresholdRatio

        // Drag distance
        let dragDistance = originalX - proposedX
        let absDrag = abs(dragDistance)

        // --- 1) FREE SCROLL REGION (in-between pages) ---
        // Always route through the same final alignment phase so small drags and
        // larger scroll gestures resolve to identical snapped positions.
        var destination = min(max(proposedX, 0), maxOffset)
        let freeLimit = threshold * freeScrollMultiplier
        if absDrag < freeLimit {
            destination = min(max(proposedX, 0), maxOffset)
        } else {
            // --- 2) PAGING REGION (1 or 2 pages) ---
            // When scrolling toward an edge and proposedX hits the boundary,
            // the drag distance is artificially limited. Scale threshold accordingly.
            let isProposedAtEdge = (direction == .left && proposedX >= maxOffset - 1) ||
                                   (direction == .right && proposedX <= 1)
            let effectiveTwoPageMultiplier = isProposedAtEdge ? twoPageMultiplier * 0.5 : twoPageMultiplier
            let twoPageThreshold = threshold * effectiveTwoPageMultiplier

            let pagesToJump: CGFloat
            let shouldApplyPageJump: Bool
            if absDrag > twoPageThreshold {
                pagesToJump = 2
                shouldApplyPageJump = true
            } else if absDrag > threshold {
                pagesToJump = 1
                shouldApplyPageJump = true
            } else {
                // Between freeLimit and threshold: soft snap to nearest page
                let bounded = min(max(proposedX, 0), maxOffset)
                let nearestPage = round(bounded / viewportW) * viewportW
                destination = min(max(nearestPage, 0), maxOffset)
                pagesToJump = 0
                shouldApplyPageJump = false
            }

            if shouldApplyPageJump {
                destination = (dragDistance > 0)
                    ? (originalX - viewportW * pagesToJump)  // scrolling to see earlier content
                    : (originalX + viewportW * pagesToJump)  // scrolling to see later content
            }

            // --- 3) BOUNDARY HANDLING ---
            // First clamp to valid range
            destination = min(max(destination, 0), maxOffset)

            // Edge snapping - ONLY when moving TOWARD the edge, not away from it
            // direction == .right means scrolling to see earlier content (moving away from right edge)
            // direction == .left means scrolling to see later content (moving toward right edge)
            // Use viewport-based distance (not percentage of total content) for edge detection
            let distanceFromRightEdge = maxOffset - destination
            let distanceFromLeftEdge = destination
            let edgeSnapThreshold = viewportW * 0.15  // Snap when within 15% of viewport from edge

            let shouldSnapToMaxOffset = distanceFromRightEdge <= edgeSnapThreshold && direction == .left
            let shouldSnapToZero = distanceFromLeftEdge <= edgeSnapThreshold && direction == .right

            if shouldSnapToMaxOffset {
                destination = maxOffset
            } else if shouldSnapToZero {
                destination = 0
            } else {
                // Align to page boundaries
                if direction == .right {
                    let offsetFromRight = maxOffset - destination
                    let pageFromRight = round(offsetFromRight / viewportW)
                    destination = maxOffset - (pageFromRight * viewportW)
                } else {
                    let pageNumber = round(destination / viewportW)
                    destination = min(pageNumber * viewportW, maxOffset)
                }
            }
        }

        // Final bounds check to prevent any rubber banding
        target.rect.origin.x = min(max(destination, 0), maxOffset)

        // Phase 2: Apply date-component alignment (if configured)
        // Limit adjustment to prevent valueAligned from adding extra page movement
        let preAlignedX = target.rect.origin.x

        if let valueAligned = valueAlignedBehavior {
            valueAligned.updateTarget(&target, context: context)

            // Limit valueAligned adjustment to half a viewport to prevent extra page jumps
            let maxAdjustment = viewportW * 0.80
            let adjustment = abs(target.rect.origin.x - preAlignedX)
            if adjustment > maxAdjustment {
                target.rect.origin.x = preAlignedX
            }

            // Re-clamp after alignment in case it pushed outside bounds
            target.rect.origin.x = min(max(target.rect.origin.x, 0), maxOffset)
        }
    }
}
