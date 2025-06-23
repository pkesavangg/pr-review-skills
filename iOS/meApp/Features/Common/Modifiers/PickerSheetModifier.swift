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
    let pickerType: PickerType
    let onUpdate: ([T]) -> Void
    var title: String? = nil
    var showCancel: Bool = false
    var allowTapOutside: Bool = true
    
    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $isPresented) {
                PickerView(
                    selectedValues: selectedValues,
                    options: options,
                    displayValue: displayValue,
                    pickerType: pickerType,
                    title: title,
                    showCancel: showCancel,
                    updateValues: { newValues in
                        onUpdate(newValues)
                        isPresented = false
                    },
                    onCancel: showCancel ? { isPresented = false } : nil
                )
                .presentationDetents([.height(280)])
                .presentationDragIndicator(.hidden)
                .modifier(InteractiveDismissModifier(disabled: !allowTapOutside))
            }
    }
}

// Helper modifier
private struct InteractiveDismissModifier: ViewModifier {
    let disabled: Bool
    func body(content: Content) -> some View {
        content
            .interactiveDismissDisabled(disabled)
    }
}
