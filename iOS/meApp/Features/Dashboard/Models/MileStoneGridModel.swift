//
//  MileStoneGridModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 07/08/25.
//

import Foundation

/// Model for grid arrangement
struct MileStoneGridModel {
    /// Ordered widgets; can be mutated for reordering
    var mileStones: [MileStoneType]

    /// Move an item from one index to another, with special handling for goal card and streak items
    mutating func moveWidget(from source: Int, to destination: Int) {
        guard source != destination, 
              source >= 0, source < mileStones.count, 
              destination >= 0, destination < mileStones.count else { return }
        
        let widget = mileStones[source]
        
        switch widget {
        case .goalCard:
            // Goal card moves normally - position validation is handled at UI level
            let goalCard = mileStones.remove(at: source)
            mileStones.insert(goalCard, at: destination)
            
        case .streak:
            // Find goal card position if it exists
            let goalCardIndex = mileStones.firstIndex(where: { $0 == .goalCard })
            
            if let goalPos = goalCardIndex {
                // Check if this is a swap between immediate neighbors of goal card
                let isImmediateNeighborSwap = (
                    // Moving from immediate before to immediate after goal card
                    (source == goalPos - 1 && destination == goalPos + 1) ||
                    // Moving from immediate after to immediate before goal card
                    (source == goalPos + 1 && destination == goalPos - 1)
                )
                
                if isImmediateNeighborSwap {
                    // Get the streak item at the destination
                    let targetWidget = mileStones[destination]
                    
                    // Only proceed if target is also a streak item
                    if case .streak = targetWidget {
                        // Swap the streak items while keeping goal card in place
                        let movingStreak = mileStones[source]
                        mileStones[source] = targetWidget
                        mileStones[destination] = movingStreak
                    } else {
                        // If target is not a streak item, do normal move
                        let streak = mileStones.remove(at: source)
                        mileStones.insert(streak, at: destination)
                    }
                } else {
                    // For non-immediate neighbors, do normal move
                    let streak = mileStones.remove(at: source)
                    mileStones.insert(streak, at: destination)
                }
            } else {
                // If no goal card, do normal move
                let streak = mileStones.remove(at: source)
                mileStones.insert(streak, at: destination)
            }
        }
    }
    
    /// Reorders the grid so goal cards start on new rows when they wouldn't fit
    /// Similar to Android's reorderGrid function
    mutating func reorderGrid(spanCount: Int, hasRemovedStreaks: Bool = false) {
        if mileStones.isEmpty { return }
        
        // Find the first goal card
        guard let goalCardIndex = mileStones.firstIndex(where: { $0 == .goalCard }) else { return }
        if goalCardIndex == mileStones.count - 1 { return }

        if hasRemovedStreaks {
            return // Skip automatic reordering when streaks are removed
        }
        
        // Count streak items before the goal card
        let streakCountBeforeGoal = (0..<goalCardIndex).reduce(0) { count, index in
            if case .streak = mileStones[index] { return count + 1 }
            return count
        }
        
        // Calculate how many columns are already used in the current row
        let usedBefore = (0..<goalCardIndex).reduce(0) { total, index in
            let widget = mileStones[index]
            let itemSpan = getItemSpan(for: widget, spanCount: spanCount)
            return (total + itemSpan) % spanCount
        }
        
        let targetSpan = getItemSpan(for: .goalCard, spanCount: spanCount)
        let remaining = spanCount - usedBefore
        
        // Special case: if we have odd number of streaks and goal card is at the end,
        // keep the current layout (last streak in single row, goal card below)
        // This applies when streaks are removed
        if hasRemovedStreaks && streakCountBeforeGoal % spanCount == 1 && goalCardIndex == streakCountBeforeGoal {
            return // Keep current layout for odd number case when streaks are removed
        }
        
        // If goal card fits in current row, keep order
        if usedBefore == 0 || targetSpan <= remaining { return }
        
        // Otherwise, move goal card to start of next row
        var moveBy = 0
        var filled = 0
        while goalCardIndex + 1 + moveBy < mileStones.count && filled < remaining {
            let nextIndex = goalCardIndex + 1 + moveBy
            let nextWidget = mileStones[nextIndex]
            filled += getItemSpan(for: nextWidget, spanCount: spanCount)
            moveBy += 1
        }
        
        let insertPos = min(goalCardIndex + moveBy, mileStones.count - 1)
        
        // Move the goal card
        let goalCard = mileStones.remove(at: goalCardIndex)
        mileStones.insert(goalCard, at: insertPos)
    }
    
    /// Get the span for a widget (goal card spans full width, others span 1)
    private func getItemSpan(for widget: MileStoneType, spanCount: Int) -> Int {
        switch widget {
        case .goalCard:
            return spanCount
        case .streak:
            return 1
        }
    }
}
