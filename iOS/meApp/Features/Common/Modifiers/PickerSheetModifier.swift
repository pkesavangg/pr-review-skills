//
//  PickerSheetModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//

import SwiftUI

// MARK: - Picker Sheet Modifier
struct PickerSheetModifier<T: Hashable>: ViewModifier {
    @Binding var isPresented: Bool
    let selectedValues: [T]
    let options: [[T]]
    let displayValue: (T) -> String
    let onUpdate: ([T]) -> Void
    
    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $isPresented) {
                PickerView(
                    selectedValues: selectedValues,
                    options: options,
                    displayValue: displayValue,
                    updateValues: { newValues in
                        onUpdate(newValues)
                        isPresented = false
                    },
                    onCancel: {
                        isPresented = false
                    }
                )
                .presentationDetents([.height(300)])
                .presentationDragIndicator(.hidden)
                .interactiveDismissDisabled()
            }
    }
}
