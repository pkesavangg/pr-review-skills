//
//  DragDropModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

// MARK: - Generic Reorder Extension
extension View {
    func draggableReorder<T: Identifiable & Equatable>(
        item: T,
        draggingItem: Binding<T?>,
        items: Binding<[T]>,
        isDraggable: Bool = true,
        onDropTargetChanged: @escaping (Bool) -> Void
    ) -> some View {
        Group {
            if isDraggable {
                self
                    .onDrag {
                        draggingItem.wrappedValue = item
                        return NSItemProvider(object: "\(item.id)" as NSString)
                    }
                    .onDrop(
                        of: [.text],
                        delegate: ReorderDropDelegate(
                            item: item,
                            items: items,
                            draggingItem: draggingItem,
                            onDropTargetChanged: onDropTargetChanged
                        )
                    )
            } else {
                self
            }
        }
    }
}

// MARK: - Generic DropDelegate for drag-and-drop reordering
struct ReorderDropDelegate<T: Identifiable & Equatable>: DropDelegate {
    let item: T
    @Binding var items: [T]
    @Binding var draggingItem: T?
    let onDropTargetChanged: (Bool) -> Void

    func performDrop(info: DropInfo) -> Bool {
        // Immediately reset drag state to prevent time delay
        draggingItem = nil
        onDropTargetChanged(false)
        return true
    }

    func dropEntered(info: DropInfo) {
        guard let dragging = draggingItem, dragging != item,
              let from = items.firstIndex(of: dragging),
              let to = items.firstIndex(of: item) else { 
            onDropTargetChanged(true)
            return 
        }

        onDropTargetChanged(true)
        withAnimation(.easeInOut(duration: 0.2)) {
            items.move(fromOffsets: IndexSet(integer: from), toOffset: to > from ? to + 1 : to)
        }
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

// MARK: - Legacy DragDropModifier (for backward compatibility)
struct DragDropModifier: ViewModifier {
    let isEditMode: Bool
    let label: String
    let onDrop: (String, String) -> Bool
    let onDropTargetChanged: (Bool) -> Void

    func body(content: Content) -> some View {
        if isEditMode {
            content
                .draggable(label)
                .dropDestination(for: String.self) { droppedIds, _ in
                    guard let draggedId = droppedIds.first else { return false }
                    let result = onDrop(draggedId, label)
                    onDropTargetChanged(false)
                    return result
                } isTargeted: { isTargeted in
                    onDropTargetChanged(isTargeted)
                }
        } else {
            content
        }
    }
}
