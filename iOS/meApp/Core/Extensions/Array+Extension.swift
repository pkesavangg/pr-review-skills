//
//  Array+Extension.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Foundation

// MARK: - Array Extension
/// This extension provides a method to truncate an array to a specific index.
extension Array {
    mutating func truncate(to index: Int) {
        guard index < self.count && index >= 0 else {
            return
        }
        self = Array(self[..<Swift.min(index + 1, self.count)])
    }
}
