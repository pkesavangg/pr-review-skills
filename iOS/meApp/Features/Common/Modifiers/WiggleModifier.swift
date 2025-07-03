//
//  WiggleModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

struct WiggleModifier: ViewModifier {
    let shouldWiggle: Bool
    @State private var isWiggling = false

    func body(content: Content) -> some View {
        content
            .modifier(WiggleEffect(enabled: shouldWiggle, isWiggling: isWiggling))
            .onAppear {
                if shouldWiggle {
                    isWiggling = true
                }
            }
            .onChange(of: shouldWiggle) { _, newValue in
                isWiggling = newValue
            }
            .onDisappear {
                isWiggling = false
            }
    }
}

private struct WiggleEffect: ViewModifier {
    let enabled: Bool
    let isWiggling: Bool

    private var rotationAnimation: Animation {
        Animation.easeInOut(duration: Double.random(in: 0.1...0.15)).repeatForever(autoreverses: true)
    }

    private var bounceAnimation: Animation {
        Animation.easeInOut(duration: Double.random(in: 0.12...0.18)).repeatForever(autoreverses: true)
    }

    func body(content: Content) -> some View {
        if enabled {
            content
                .rotationEffect(.degrees(isWiggling ? 2.5 : -2))
                .offset(y: isWiggling ? 1 : -1)
                .animation(rotationAnimation, value: isWiggling)
                .animation(bounceAnimation, value: isWiggling)
        } else {
            content
        }
    }
}
