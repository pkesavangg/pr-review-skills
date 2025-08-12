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
    }
    
    let type: ItemType
    let item: MetricItem
    
    init(type: ItemType, item: MetricItem) {
        self.type = type
        self.item = item
    }
}
