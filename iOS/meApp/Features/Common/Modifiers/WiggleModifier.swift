//
//  WiggleModifier.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

// MARK: - Animation Constants

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
        // Calculate duration based on row index for alternating timing (matching movingGridsLearning exactly)
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
    /// Matches the movingGridsLearning implementation exactly
    func startWiggle() {
        let animation = createWiggleAnimation(
            duration: WiggleAnimationConstants.wiggleDurationEven,
            rotationAngle: WiggleAnimationConstants.wiggleRotationAngle // Already in radians
        )
        layer.add(animation, forKey: "wiggle")
    }
    
    /// Starts wiggle animation with alternating durations based on row index (app icons)
    /// - Parameter rowIndex: The row index to determine animation timing
    func startWiggleWithRowIndex(_ rowIndex: Int) {
        let duration = (Double(rowIndex).truncatingRemainder(dividingBy: 2)) == 0 
            ? WiggleAnimationConstants.wiggleDurationEven 
            : WiggleAnimationConstants.wiggleDurationOdd
        
        let animation = createWiggleAnimation(
            duration: duration,
            rotationAngle: WiggleAnimationConstants.wiggleRotationAngle // Already in radians
        )
        layer.add(animation, forKey: "wiggle")
    }
    
    /// Starts widget wiggle animation with alternating durations based on row index (widgets)
    /// - Parameter rowIndex: The row index to determine animation timing
    func startWidgetWiggleWithRowIndex(_ rowIndex: Int) {
        let duration = (Double(rowIndex).truncatingRemainder(dividingBy: 2)) == 0 
            ? WiggleAnimationConstants.widgetWiggleDurationEven 
            : WiggleAnimationConstants.widgetWiggleDurationOdd
        
        let animation = createWiggleAnimation(
            duration: duration,
            rotationAngle: WiggleAnimationConstants.widgetWiggleRotationAngle // Already in radians
        )
        layer.add(animation, forKey: "wiggle")
    }
    
    /// Stops the wiggle animation
    func stopWiggle() {
        layer.removeAnimation(forKey: "wiggle")
    }
    
    /// Creates a wiggle animation with specified parameters (matching movingGridsLearning exactly)
    /// - Parameters:
    ///   - duration: Animation duration
    ///   - rotationAngle: Rotation angle in radians
    /// - Returns: Configured CAKeyframeAnimation
    private func createWiggleAnimation(duration: Double, rotationAngle: Double) -> CAKeyframeAnimation {
        let transformAnim = CAKeyframeAnimation(keyPath: "transform")
        
        // Use the exact same values as movingGridsLearning for consistency
        transformAnim.values = [
            NSValue(caTransform3D: CATransform3DMakeRotation(rotationAngle, 0.0, 0.0, 1.0)),
            NSValue(caTransform3D: CATransform3DMakeRotation(-rotationAngle, 0.0, 0.0, 1.0))
        ]
        
        transformAnim.autoreverses = true
        transformAnim.duration = duration
        transformAnim.repeatCount = Float.infinity
        
        return transformAnim
    }
}
