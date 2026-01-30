//
//  ShimmerModifier.swift
//  meApp
//
//  Created for skeleton loading animation
//

import SwiftUI

/// A view modifier that adds a shimmer/skeleton loading animation effect
/// Similar to Google sites and modern web applications
struct ShimmerModifier: ViewModifier {
    @Environment(\.appTheme) private var theme
    @State private var phase: CGFloat = 0
    private let duration: Double = 1.5
    
    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geometry in
                    LinearGradient(
                        gradient: Gradient(colors: [
                            theme.backgroundSecondary.opacity(0.3),
                            theme.backgroundSecondary.opacity(0.6),
                            theme.backgroundSecondary.opacity(0.3)
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: geometry.size.width * 2)
                    .offset(x: -geometry.size.width + (geometry.size.width * 2 * phase))
                    .blur(radius: 8)
                }
            )
            .onAppear {
                withAnimation(
                    Animation.linear(duration: duration)
                        .repeatForever(autoreverses: false)
                ) {
                    phase = 1
                }
            }
    }
}

extension View {
    /// Applies a shimmer loading animation effect to the view
    func shimmer() -> some View {
        modifier(ShimmerModifier())
    }
}
