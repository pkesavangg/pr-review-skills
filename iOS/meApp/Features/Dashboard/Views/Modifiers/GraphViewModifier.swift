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
    let canAddTrailingPadding: Bool
    
    func body(content: Content) -> some View {
        content
            .frame(height: 265) 
            .frame(maxWidth: .infinity, minHeight: 240)
            .padding(.leading, canAddPadding ? .spacingXS : 0)
            .padding(.trailing, canAddTrailingPadding ? .spacingXS : 0)
    }
}

extension View {
    /// Applies consistent styling for graph views
    /// - Parameter canAddPadding: Whether to add padding to the leading edge
    /// - Parameter canAddTrailingPadding: Whether to add padding to the trailing edge (defaults to true)
    /// - Returns: A styled view
    func graphViewStyle(canAddPadding: Bool, canAddTrailingPadding: Bool = true) -> some View {
        self.modifier(GraphViewModifier(canAddPadding: canAddPadding, canAddTrailingPadding: canAddTrailingPadding))
    }
}
