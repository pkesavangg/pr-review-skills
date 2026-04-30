//
//  LongPressModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 04/07/25.
//

import SwiftUI

// MARK: - Long Press Modifier
struct LongPressModifier: ViewModifier {
    let isEditMode: Bool
    let action: () -> Void
    let minimumDuration: Double
    let maximumDistance: CGFloat
    
    init(
        isEditMode: Bool,
        minimumDuration: Double = 0.5,
        maximumDistance: CGFloat = 50,
        action: @escaping () -> Void
    ) {
        self.isEditMode = isEditMode
        self.minimumDuration = minimumDuration
        self.maximumDistance = maximumDistance
        self.action = action
    }
    
    func body(content: Content) -> some View {
        if !isEditMode {
            content
                .simultaneousGesture(
                    LongPressGesture(
                        minimumDuration: minimumDuration,
                        maximumDistance: maximumDistance
                    )
                    .onEnded { _ in
                        action()
                    }
                )
        } else {
            content
        }
    }
}

// MARK: - View Extension
extension View {
    func longPressGesture(
        isEditMode: Bool,
        minimumDuration: Double = 0.5,
        maximumDistance: CGFloat = 50,
        action: @escaping () -> Void
    ) -> some View {
        modifier(LongPressModifier(
            isEditMode: isEditMode,
            minimumDuration: minimumDuration,
            maximumDistance: maximumDistance,
            action: action
        ))
    }
}
