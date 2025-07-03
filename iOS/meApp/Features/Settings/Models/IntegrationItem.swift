//
//  IntegrationItem.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

import Foundation

// MARK: - Integration Item Model
/// Represents a single integration provider row in the list.
struct IntegrationItem: Identifiable {
    let id = UUID()
    let type: IntegrationItemType
    var isSelected: Bool = false
    var isOutOfSync: Bool = false

    init(type: IntegrationItemType, isSelected: Bool = false, isOutOfSync: Bool = false) {
        self.type = type
        self.isSelected = isSelected
        self.isOutOfSync = isOutOfSync
    }
}