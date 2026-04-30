//
//  DragItemWrapper.swift
//  meApp
//
//  Created by Lakshmi Priya on 08/08/25.
//

import SwiftUI

class DragItemWrapper {
    enum ItemType {
        case metric
        case streak
        case goalStreak
    }
    
    let type: ItemType
    let item: Any // Changed from MetricItem to Any to support MileStoneType
    
    init(type: ItemType, item: Any) {
        self.type = type
        self.item = item
    }
}
