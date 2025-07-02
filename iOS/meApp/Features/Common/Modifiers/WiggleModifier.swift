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

    /// Produces a randomized interval for the wiggle animation, adding slight variance to the timing.
    /// - Parameters:
    ///   - interval: Base interval.
    ///   - variance: Amount of variance to add/subtract.
    /// - Returns: A `TimeInterval` with variance applied.
    private static func randomize(interval: TimeInterval, withVariance variance: Double) -> TimeInterval {
        let random = (Double(arc4random_uniform(1000)) - 500.0) / 500.0
        return interval + variance * random
    }
    
    /// Animation for the rotation (wiggle) effect.
    private let rotateAnimation = Animation
        .easeInOut(
            duration: WiggleModifier.randomize(
                interval: 0.14,
                withVariance: 0.025
            )
        )
        .repeatForever(autoreverses: true)
    
    /// Animation for the vertical bounce effect.
    private let bounceAnimation = Animation
        .easeInOut(
            duration: WiggleModifier.randomize(
                interval: 0.18,
                withVariance: 0.025
            )
        )
        .repeatForever(autoreverses: true)
    
    func body(content: Content) -> some View {
        if shouldWiggle {
            content
                .rotationEffect(.degrees(isWiggling ? 2.0 : 0))
                .animation(rotateAnimation, value: isWiggling)
                .offset(x: 0, y: isWiggling ? 2.0 : 0)
                .animation(bounceAnimation, value: isWiggling)
                .onAppear { isWiggling = true }
                .onDisappear { isWiggling = false }
        } else {
            content
        }
    }
}
