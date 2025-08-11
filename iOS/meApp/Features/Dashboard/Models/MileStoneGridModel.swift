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
}
