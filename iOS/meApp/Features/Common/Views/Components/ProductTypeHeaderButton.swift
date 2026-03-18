//
//  ProductTypeHeaderButton.swift
//  meApp
//

import SwiftUI

/// Compact button showing the currently selected product type name
/// with a chevron.down icon. Tapping opens the product type selector sheet.
///
/// Only renders when there are multiple product types available.
/// When only one type exists, displays nothing (the screen's default title is used instead).
struct ProductTypeHeaderButton: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var store: ProductTypeStore
    @Binding var isSheetPresented: Bool

    var body: some View {
        if store.availableItems.count > 1 {
            Button {
                isSheetPresented = true
            } label: {
                HStack(spacing: 4) {
                    Text(store.selectedItem.displayName)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(theme.actionSecondary)
                        .lineLimit(1)
                    Image(systemName: "chevron.down")
                        .font(.caption2)
                        .foregroundColor(theme.actionSecondary)
                }
            }
        }
    }
}
