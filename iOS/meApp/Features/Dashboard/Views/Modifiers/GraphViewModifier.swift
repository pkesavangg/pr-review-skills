//
//  GraphViewModifier.swift
//  meApp
//
//  Created by Assistant on 22/08/25.
//

import SwiftUI

/// A ViewModifier that applies consistent styling for graph views
struct GraphViewModifier: ViewModifier {
    let canAddPadding: Bool
    
    func body(content: Content) -> some View {
        content
            .frame(height: 265) 
            .frame(maxWidth: .infinity, minHeight: 240)
            .padding(.leading, canAddPadding ? .spacingXS : 0)
            .padding(.trailing, 0)
    }
}

extension View {
    /// Applies consistent styling for graph views
    /// - Parameter canAddPadding: Whether to add padding to the leading edge
    /// - Returns: A styled view
    func graphViewStyle(canAddPadding: Bool) -> some View {
        self.modifier(GraphViewModifier(canAddPadding: canAddPadding))
    }
}
