//
//  GraphViewModifier.swift
//  meApp
//
//  Created by Assistant on 22/08/25.
//

import SwiftUI

/// A ViewModifier that applies consistent styling for graph views
struct GraphViewModifier: ViewModifier {
    let isAtLeftBoundary: Bool
    
    func body(content: Content) -> some View {
        content
            .frame(height: 265) 
            .frame(maxWidth: .infinity, minHeight: 240)
            .padding(.leading, 0)
            .padding(.trailing, .spacingXS)
    }
}

extension View {
    /// Applies consistent styling for graph views
    /// - Parameter isAtLeftBoundary: Whether the view is at the leftmost boundary
    /// - Returns: A styled view
    func graphViewStyle(isAtLeftBoundary: Bool) -> some View {
        self.modifier(GraphViewModifier(isAtLeftBoundary: isAtLeftBoundary))
    }
}
