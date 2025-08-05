//
//  WiggleModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

struct WiggleModifier: ViewModifier {
    let shouldWiggle: Bool
    let rowIndex: Int
    let evenRowDuration: Double?
    let oddRowDuration: Double?
    let wiggleAngle: Double?
    @State private var currentRotation: Double = 0
    
    init(
        shouldWiggle: Bool, 
        rowIndex: Int = 0,
        evenRowDuration: Double? = nil,
        oddRowDuration: Double? = nil,
        wiggleAngle: Double? = nil
    ) {
        self.shouldWiggle = shouldWiggle
        self.rowIndex = rowIndex
        self.evenRowDuration = evenRowDuration
        self.oddRowDuration = oddRowDuration
        self.wiggleAngle = wiggleAngle
    }

    func body(content: Content) -> some View {
        content
            .rotationEffect(.radians(currentRotation))
            .onAppear {
                if shouldWiggle {
                    startWiggleAnimation()
                }
            }
            .onChange(of: shouldWiggle) { _, newValue in
                if newValue {
                    startWiggleAnimation()
                } else {
                    stopWiggleAnimation()
                }
            }
            .onDisappear {
                stopWiggleAnimation()
            }
    }

    private func startWiggleAnimation() {
        stopWiggleAnimation()
        
        let finalEvenRowDuration = evenRowDuration ?? WiggleAnimationConstants.wiggleRotateDuration
        let finalOddRowDuration = oddRowDuration ?? WiggleAnimationConstants.wiggleRotateDuration
        let animationDuration = (Double(rowIndex).truncatingRemainder(dividingBy: 2)) == 0 
            ? finalEvenRowDuration 
            : finalOddRowDuration
        
        let targetRotationAngle = wiggleAngle ?? WiggleAnimationConstants.wiggleRotateAngle
        
        withAnimation(
            Animation
                .easeInOut(duration: animationDuration)
                .repeatForever(autoreverses: true)
                .speed(1.0)
        ) {
            currentRotation = targetRotationAngle
        }
    }

    private func stopWiggleAnimation() {
        withAnimation(.easeOut(duration: 0.095)) {
            currentRotation = 0
        }
    }
}

extension UIView {
    func startWiggle() {
        layer.removeAnimation(forKey: "wiggle")
        layer.removeAnimation(forKey: "rotation")
        layer.removeAnimation(forKey: "bounce")
        
        layer.add(createRotationAnimation(), forKey: "rotation")
        layer.add(createBounceAnimation(), forKey: "bounce")
    }
    
    func startWiggleWithRowIndex(_ rowIndex: Int) {
        layer.removeAnimation(forKey: "wiggle")
        layer.removeAnimation(forKey: "rotation")
        layer.removeAnimation(forKey: "bounce")
        
        layer.add(createRotationAnimationWithRowIndex(rowIndex), forKey: "rotation")
        layer.add(createBounceAnimationWithRowIndex(rowIndex), forKey: "bounce")
    }
    
    func stopWiggle() {
        layer.removeAnimation(forKey: "wiggle")
        layer.removeAnimation(forKey: "rotation")
        layer.removeAnimation(forKey: "bounce")
    }
    
    private func createRotationAnimationWithRowIndex(_ rowIndex: Int) -> CAKeyframeAnimation {
        let animation = CAKeyframeAnimation(keyPath: "transform.rotation.z")
        
        animation.values = [
            NSNumber(value: -WiggleAnimationConstants.wiggleRotateAngle),
            NSNumber(value: WiggleAnimationConstants.wiggleRotateAngle)
        ]
        
        animation.autoreverses = true
        animation.duration = randomizeInterval(
            WiggleAnimationConstants.wiggleRotateDuration,
            withVariance: WiggleAnimationConstants.wiggleRotateDurationVariance
        )
        animation.repeatCount = Float.infinity
        
        return animation
    }
    
    private func createBounceAnimationWithRowIndex(_ rowIndex: Int) -> CAKeyframeAnimation {
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.y")
        
        animation.values = [
            NSNumber(value: WiggleAnimationConstants.wiggleBounceY),
            NSNumber(value: 0.0)
        ]
        
        animation.autoreverses = true
        animation.duration = randomizeInterval(
            WiggleAnimationConstants.wiggleBounceDuration,
            withVariance: WiggleAnimationConstants.wiggleBounceDurationVariance
        )
        animation.repeatCount = Float.infinity
        
        return animation
    }
    
    private func createRotationAnimation() -> CAKeyframeAnimation {
        let animation = CAKeyframeAnimation(keyPath: "transform.rotation.z")
        
        animation.values = [
            NSNumber(value: -WiggleAnimationConstants.wiggleRotateAngle),
            NSNumber(value: WiggleAnimationConstants.wiggleRotateAngle)
        ]
        
        animation.autoreverses = true
        animation.duration = randomizeInterval(
            WiggleAnimationConstants.wiggleRotateDuration,
            withVariance: WiggleAnimationConstants.wiggleRotateDurationVariance
        )
        animation.repeatCount = Float.infinity
        
        return animation
    }
    
    private func createBounceAnimation() -> CAKeyframeAnimation {
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.y")
        
        animation.values = [
            NSNumber(value: WiggleAnimationConstants.wiggleBounceY),
            NSNumber(value: 0.0)
        ]
        
        animation.autoreverses = true
        animation.duration = randomizeInterval(
            WiggleAnimationConstants.wiggleBounceDuration,
            withVariance: WiggleAnimationConstants.wiggleBounceDurationVariance
        )
        animation.repeatCount = Float.infinity
        
        return animation
    }
    
    private func randomizeInterval(_ baseInterval: Double, withVariance variance: Double) -> Double {
        let randomFactor = Double.random(in: -1.0...1.0)
        return baseInterval + (randomFactor * variance)
    }
}
