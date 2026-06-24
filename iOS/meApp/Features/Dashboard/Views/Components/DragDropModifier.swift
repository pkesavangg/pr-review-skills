//
//  DragDropModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

// MARK: - Generic DropDelegate for drag-and-drop reordering
struct ReorderDropDelegate<T: Identifiable & Equatable>: DropDelegate {
    let item: T
    @Binding var items: [T]
    @Binding var draggingItem: T?
    let onDropTargetChanged: (Bool) -> Void
    let onDragEnd: (() -> Void)?

    func performDrop(info: DropInfo) -> Bool {
        // Reset drag state immediately for better responsiveness
        draggingItem = nil
        onDropTargetChanged(false)
        onDragEnd?()
        
        return true
    }

    func dropEntered(info: DropInfo) {
        guard let dragging = draggingItem, dragging != item,
              let from = items.firstIndex(of: dragging),
              let to = items.firstIndex(of: item) else { 
            onDropTargetChanged(true)
            return 
        }
        
        // Set drop target for visual feedback
        onDropTargetChanged(true)
        
        // Perform reordering immediately without animation delay
        // Calculate the correct destination index
        let newToIndex: Int
        if from < to {
            // Moving forward: insert after the target
            newToIndex = to + 1
        } else {
            // Moving backward: insert at the target position
            newToIndex = to
        }
        
        items.move(fromOffsets: IndexSet(integer: from), toOffset: newToIndex)
    }
    
    func dropExited(info: DropInfo) {
        onDropTargetChanged(false)
    }
    
    func dropUpdated(info: DropInfo) -> DropProposal? {
        return DropProposal(operation: .move)
    }
    
    func validateDrop(info: DropInfo) -> Bool {
        return true
    }
}
