//
//  WiggleModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

struct WiggleModifier: ViewModifier {
    let shouldWiggle: Bool
    @State private var rotation: Double = 0
    @State private var bounce: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .rotationEffect(.degrees(rotation))
            .offset(y: bounce)
            .onAppear {
                if shouldWiggle {
                    startWiggle()
                }
            }
            .onChange(of: shouldWiggle) { _, newValue in
                if newValue {
                    startWiggle()
                } else {
                    stopWiggle()
                }
            }
            .onDisappear {
                stopWiggle()
            }
    }

    private func startWiggle() {
        // Use the same animation for all items
        withAnimation(Animation.easeInOut(duration: 0.18).repeatForever(autoreverses: true)) {
            rotation = 2.0
        }
        withAnimation(Animation.easeInOut(duration: 0.14).repeatForever(autoreverses: true)) {
            bounce = 2.0
        }
    }

    private func stopWiggle() {
        withAnimation(.easeOut(duration: 0.1)) {
            rotation = 0
            bounce = 0
        }
    }
}
