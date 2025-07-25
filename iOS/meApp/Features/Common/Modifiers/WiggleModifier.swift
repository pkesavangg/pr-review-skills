//
//  WiggleModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

// MARK: - Animation Constants

private struct WiggleAnimationConstants {
    /// Wiggle animation duration for even rows (perfect middle ground)
    static let wiggleDurationEven: Double = 0.095
    
    /// Wiggle animation duration for odd rows (perfect middle ground)
    static let wiggleDurationOdd: Double = 0.085
    
    /// Wiggle rotation angle in degrees (perfect middle ground)
    static let wiggleRotationAngle: Double = 3.7 // Perfect balance between 3.0 and 4.2
}

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
            .rotationEffect(.degrees(currentRotation))
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
        // Calculate duration based on row index for alternating timing
        let finalEvenRowDuration = evenRowDuration ?? WiggleAnimationConstants.wiggleDurationEven
        let finalOddRowDuration = oddRowDuration ?? WiggleAnimationConstants.wiggleDurationOdd
        let animationDuration = (Double(rowIndex).truncatingRemainder(dividingBy: 2)) == 0 
            ? finalEvenRowDuration 
            : finalOddRowDuration
        
        let targetRotationAngle = wiggleAngle ?? WiggleAnimationConstants.wiggleRotationAngle
        
        withAnimation(
            Animation
                .easeInOut(duration: animationDuration)
                .repeatForever(autoreverses: true)
                .speed(1.0) // Balanced speed
        ) {
            currentRotation = targetRotationAngle
        }
    }

    private func stopWiggleAnimation() {
        withAnimation(.easeOut(duration: 0.095)) { // Perfect middle ground stop animation
            currentRotation = 0
        }
    }
}

// MARK: - UIView Extension for Direct Layer Animation (for UIKit components)

extension UIView {
    /// Starts the iOS home screen-style wiggle animation using CAKeyframeAnimation
    func startWiggle() {
        let animation = createWiggleAnimation(
            duration: WiggleAnimationConstants.wiggleDurationEven,
            rotationAngle: WiggleAnimationConstants.wiggleRotationAngle * .pi / 180 // Convert to radians
        )
        layer.add(animation, forKey: "wiggle")
    }
    
    /// Starts wiggle animation with alternating durations based on row index
    /// - Parameter rowIndex: The row index to determine animation timing
    func startWiggleWithRowIndex(_ rowIndex: Int) {
        let duration = (Double(rowIndex).truncatingRemainder(dividingBy: 2)) == 0 
            ? WiggleAnimationConstants.wiggleDurationEven 
            : WiggleAnimationConstants.wiggleDurationOdd
        
        let animation = createWiggleAnimation(
            duration: duration,
            rotationAngle: WiggleAnimationConstants.wiggleRotationAngle * .pi / 180 // Convert to radians
        )
        layer.add(animation, forKey: "wiggle")
    }
    
    /// Stops the wiggle animation
    func stopWiggle() {
        layer.removeAnimation(forKey: "wiggle")
    }
    
    /// Creates a wiggle animation with specified parameters (optimized for tight spacing)
    /// - Parameters:
    ///   - duration: Animation duration
    ///   - rotationAngle: Rotation angle in radians
    /// - Returns: Configured CAKeyframeAnimation
    private func createWiggleAnimation(duration: Double, rotationAngle: Double) -> CAKeyframeAnimation {
        let transformAnim = CAKeyframeAnimation(keyPath: "transform")
        
        transformAnim.values = [
            NSValue(caTransform3D: CATransform3DMakeRotation(rotationAngle, 0.0, 0.0, 1.0)),
            NSValue(caTransform3D: CATransform3DMakeRotation(-rotationAngle, 0.0, 0.0, 1.0))
        ]
        
        // Use more pronounced keyframes for better visibility in tight spacing
//        transformAnim.values = [
//            NSValue(caTransform3D: CATransform3DMakeRotation(0, 0.0, 0.0, 1.0)),
//            NSValue(caTransform3D: CATransform3DMakeRotation(rotationAngle * 0.8, 0.0, 0.0, 1.0)),
//            NSValue(caTransform3D: CATransform3DMakeRotation(rotationAngle, 0.0, 0.0, 1.0)),
//            NSValue(caTransform3D: CATransform3DMakeRotation(0, 0.0, 0.0, 1.0)),
//            NSValue(caTransform3D: CATransform3DMakeRotation(-rotationAngle * 0.8, 0.0, 0.0, 1.0)),
//            NSValue(caTransform3D: CATransform3DMakeRotation(-rotationAngle, 0.0, 0.0, 1.0)),
//            NSValue(caTransform3D: CATransform3DMakeRotation(0, 0.0, 0.0, 1.0))
//        ]
//        
//        // Use more detailed keyTimes for smoother animation
//        transformAnim.keyTimes = [0, 0.15, 0.3, 0.5, 0.7, 0.85, 1.0]
        
        transformAnim.autoreverses = false // We handle the full cycle manually
        transformAnim.duration = duration
        transformAnim.repeatCount = Float.infinity
        
        // Use easeInEaseOut timing function for smooth but pronounced animation
        transformAnim.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        
        return transformAnim
    }
}
