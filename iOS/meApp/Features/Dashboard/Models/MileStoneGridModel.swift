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

    /// Move an item from one index to another (goalCard moves row-wise)
    mutating func moveWidget(from source: Int, to destination: Int) {
        guard source != destination, source >= 0, source < mileStones.count, destination >= 0, destination < mileStones.count else { return }
        let widget = mileStones.remove(at: source)
        mileStones.insert(widget, at: destination)
    }
    
    /// Reorders the grid so goal cards start on new rows when they wouldn't fit
    /// Similar to Android's reorderGrid function
    mutating func reorderGrid(spanCount: Int) {
        if mileStones.isEmpty { return }
        
        // Find the first goal card
        guard let goalCardIndex = mileStones.firstIndex(where: { $0 == .goalCard }) else { return }
        if goalCardIndex == mileStones.count - 1 { return }
        
        // Calculate how many columns are already used in the current row
        let usedBefore = (0..<goalCardIndex).reduce(0) { total, index in
            let widget = mileStones[index]
            let itemSpan = getItemSpan(for: widget, spanCount: spanCount)
            return (total + itemSpan) % spanCount
        }
        
        let targetSpan = getItemSpan(for: .goalCard, spanCount: spanCount)
        let remaining = spanCount - usedBefore
        
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
