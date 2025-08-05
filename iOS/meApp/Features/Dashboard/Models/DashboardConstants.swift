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

public struct WiggleAnimationConstants {
    static let wiggleBounceY: CGFloat = 3.0
    static let wiggleBounceDuration: Double = 0.14
    static let wiggleBounceDurationVariance: Double = 0.025
    static let wiggleRotateAngle: Double = 0.03
    static let wiggleRotateDuration: Double = 0.12
    static let wiggleRotateDurationVariance: Double = 0.025
}
