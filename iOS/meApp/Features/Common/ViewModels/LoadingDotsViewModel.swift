//
//  LoadingDotsViewModel.swift
//  meApp
//
//  Created for animated three-dot loading effect ViewModel
//

import SwiftUI
import Combine

/// ViewModel for managing the animated three-dot loading effect
@MainActor
class LoadingDotsViewModel: ObservableObject {
    // MARK: - Configuration Properties
    let dotCount: Int = 3
    var jumpUpHeight: CGFloat = 6
    var jumpDownHeight: CGFloat = 2.5
    var dotSize: CGFloat = .spacingXS / 2
    var spacing: CGFloat = 4
    var animationDuration: Double = 1.5
    var color: Color = .primary
    
    // MARK: - Initialization
    init(
        jumpUpHeight: CGFloat = 6,
        jumpDownHeight: CGFloat = 2.5,
        dotSize: CGFloat = .spacingXS / 2,
        spacing: CGFloat = 4,
        animationDuration: Double = 1.5,
        color: Color = .primary
    ) {
        self.jumpUpHeight = jumpUpHeight
        self.jumpDownHeight = jumpDownHeight
        self.dotSize = dotSize
        self.spacing = spacing
        self.animationDuration = animationDuration
        self.color = color
    }
    
    // MARK: - Animation Methods
    /// Calculates the delay for a specific dot index
    /// - Parameter index: The index of the dot (0, 1, or 2)
    /// - Returns: The delay value for the dot
    func delayFor(index: Int) -> Double {
        switch index {
        case 0: return 0.2 / animationDuration
        case 1: return 0.4 / animationDuration
        case 2: return 0.55 / animationDuration
        default: return 0
        }
    }
    
    /// Calculates the animation phase for a specific dot
    /// - Parameters:
    ///   - index: The index of the dot
    ///   - currentTime: The current time interval
    /// - Returns: The animation phase (0.0 to 1.0)
    func phaseFor(index: Int, currentTime: TimeInterval) -> Double {
        let delay = delayFor(index: index)
        let progress = currentTime.truncatingRemainder(dividingBy: animationDuration) / animationDuration
        return (progress - delay + 1).truncatingRemainder(dividingBy: 1.0)
    }
    
    /// Calculates the interpolated animation state for a given phase
    /// - Parameter phase: The animation phase (0.0 to 1.0)
    /// - Returns: A tuple containing (offsetY, scale) values
    func interpolatedAnimationState(for phase: Double) -> (CGFloat, CGFloat) {
        // Match keyframe intervals and interpolate between values
        switch phase {
        case 0..<0.4:
            return (0, 0.8)
            
        case 0.4..<0.58:
            // Interpolate from 0, 0.8 to -jumpUpHeight, 1.0
            let interpolationFactor = normalized(phase, in: 0.4...0.58)
            return (
                lerp(from: 0, to: -jumpUpHeight, interpolationFactor: interpolationFactor),
                lerp(from: 0.8, to: 1.0, interpolationFactor: interpolationFactor)
            )
            
        case 0.58..<0.62:
            // Interpolate to -jumpUpHeight * 0.95, 0.95
            let interpolationFactor = normalized(phase, in: 0.58...0.62)
            return (
                lerp(from: -jumpUpHeight, to: -jumpUpHeight * 0.95, interpolationFactor: interpolationFactor),
                lerp(from: 1.0, to: 0.95, interpolationFactor: interpolationFactor)
            )
            
        case 0.62..<0.75:
            // Interpolate to jumpDownHeight, 0.85
            let interpolationFactor = normalized(phase, in: 0.62...0.75)
            return (
                lerp(from: -jumpUpHeight * 0.95, to: jumpDownHeight, interpolationFactor: interpolationFactor),
                lerp(from: 0.95, to: 0.85, interpolationFactor: interpolationFactor)
            )
            
        case 0.75..<0.9:
            // Interpolate to 0, 0.8
            let interpolationFactor = normalized(phase, in: 0.75...0.9)
            return (
                lerp(from: jumpDownHeight, to: 0, interpolationFactor: interpolationFactor),
                lerp(from: 0.85, to: 0.8, interpolationFactor: interpolationFactor)
            )
            
        default:
            return (0, 0.8)
        }
    }
    
    // MARK: - Helper Methods
    /// Linear interpolation between two values
    /// - Parameters:
    ///   - from: Starting value
    ///   - to: Ending value
    ///   - interpolationFactor: Interpolation factor (0.0 to 1.0)
    /// - Returns: Interpolated value
    private func lerp(from: CGFloat, to: CGFloat, interpolationFactor: CGFloat) -> CGFloat {
        from + (to - from) * interpolationFactor
    }
    
    /// Normalizes a value within a given range
    /// - Parameters:
    ///   - value: The value to normalize
    ///   - range: The range to normalize within
    /// - Returns: Normalized value as CGFloat
    private func normalized(_ value: Double, in range: ClosedRange<Double>) -> CGFloat {
        guard range.upperBound != range.lowerBound else { return 0 }
        return CGFloat((value - range.lowerBound) / (range.upperBound - range.lowerBound))
    }
} 