//
//  DashboardConstants.swift
//  meApp
//
//  Created by Lakshmi Priya on 04/07/25.
//

import Foundation
import UIKit

/// Constants used throughout the Dashboard feature
enum DashboardConstants {

    // MARK: - Time Intervals
    enum TimeInterval {
        static let day: Foundation.TimeInterval = 24 * 60 * 60
        static let week: Foundation.TimeInterval = 7 * 24 * 60 * 60
        static let month: Foundation.TimeInterval = 30 * 24 * 60 * 60
        static let year: Foundation.TimeInterval = 365 * 24 * 60 * 60
        static let quarter: Foundation.TimeInterval = 90 * 24 * 60 * 60
        static let total: Foundation.TimeInterval = 5 * 365 * 24 * 60 * 60
    }

    // MARK: - Metric Configurations
    enum MetricType {
        static let fourScaleMetrics = ["bmi", "bodyFat", "muscleMass", "water"]
        static let allMetrics = ["bmi", "bodyFat", "muscleMass", "water", "pulse", "boneMass",
                                "visceralFatLevel", "subcutaneousFatPercent", "proteinPercent",
                                "skeletalMusclePercent", "bmr", "metabolicAge"]
    }

    // MARK: - UI Constants
    enum UI {
        static let gridSpacing: CGFloat = 16
        static let minimumTickSpacing: CGFloat = 20
        static let chartAnimationDuration: Double = 0.3
        static let scrollEndDebounceDelay: Double = 0.3
        static let loaderDelay: Double = 1.5
        
        // MARK: - Grid Layout Constants
        static let fourMetricGridColumns: Int = 2
        static let twelveMetricGridColumns: Int = 3
        static let streakGridColumns: Int = 2
    }

    // MARK: - Thresholds
    enum Thresholds {
        static let weekRepeatThreshold = 7
        static let monthRepeatThreshold = 20
        static let yearRepeatThreshold = 12
    }

    // MARK: - Metric Ranges (for normalization)
    enum MetricRanges {
        static let bmi = 18.0...35.0
        static let percentage = 0.0...100.0
        static let heartRate = 40.0...200.0
        static let visceralFat = 1.0...30.0
        static let bmr = 1000.0...3000.0
        static let metabolicAge = 15.0...80.0
    }
}

// MARK: - Dashboard Animation Constants

struct DashboardAnimationConstants {
    /// Wiggle animation duration for even rows (matching movingGridsLearning exactly)
    static let wiggleDurationEven: Double = 0.135
    
    /// Wiggle animation duration for odd rows (matching movingGridsLearning exactly)
    static let wiggleDurationOdd: Double = 0.125
    
    /// Wiggle rotation angle in radians (matching movingGridsLearning exactly)
    static let wiggleRotationAngle: Double = 0.04 // Radians, not degrees
    
    /// Delete button show animation duration
    static let deleteButtonShowDuration: Double = 0.3
    
    /// Delete button hide animation duration
    static let deleteButtonHideDuration: Double = 0.2
}

// MARK: - Dashboard Layout Constants

struct DashboardLayoutConstants {
    /// Number of columns in the metric grid
    static let metricGridColumns: Int = 2
    
    /// Number of columns in the streak grid
    static let streakGridColumns: Int = 2
    
    /// Standard spacing between grid items
    static let gridSpacing: CGFloat = 16
    
    /// Standard corner radius for cards
    static let cardCornerRadius: CGFloat = 8
}

// MARK: - Dashboard Animation Helpers

struct DashboardAnimationHelpers {
    /// Creates a wiggle animation with specified parameters (matching movingGridsLearning exactly)
    /// - Parameters:
    ///   - duration: Animation duration
    ///   - rotationAngle: Rotation angle in radians
    /// - Returns: Configured CAKeyframeAnimation
    static func createWiggleAnimation(duration: Double, rotationAngle: Double) -> CAKeyframeAnimation {
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
    
    /// Calculates wiggle duration based on row index for alternating timing
    /// - Parameter rowIndex: The row index to determine animation timing
    /// - Returns: Duration for the row (even or odd)
    static func getWiggleDuration(for rowIndex: Int) -> Double {
        return (Double(rowIndex).truncatingRemainder(dividingBy: 2)) == 0 
            ? DashboardAnimationConstants.wiggleDurationEven 
            : DashboardAnimationConstants.wiggleDurationOdd
    }
    
    /// Gets the wiggle rotation angle in radians (matching movingGridsLearning exactly)
    /// - Returns: Rotation angle in radians
    static func getWiggleRotationAngle() -> Double {
        return DashboardAnimationConstants.wiggleRotationAngle
    }
}
