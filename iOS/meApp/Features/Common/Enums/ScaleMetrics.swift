//
//  ScaleMetrics.swift
//  meApp
//
//  Created by AI Assistant on 29/01/25.
//

import Foundation

/// Represents a scale metric setting configuration
struct ScaleMetricSetting: Identifiable {
    let id = UUID()
    let name: String
    let key: String
    let imagePath: String?
    var isEnabled: Bool
    let isProgressMetrics: Bool
    
    init(
        name: String,
        key: String,
        imagePath: String? = nil,
        isEnabled: Bool = false,
        isProgressMetrics: Bool = false
    ) {
        self.name = name
        self.key = key
        self.imagePath = imagePath
        self.isEnabled = isEnabled
        self.isProgressMetrics = isProgressMetrics
    }
}

/// Central configuration for all scale metric settings
enum ScaleMetrics {
    static let config: [ScaleMetricSetting] = [
        // Body Composition Metrics
        ScaleMetricSetting(
            name: "Body Mass Index",
            key: "bmi",
            imagePath: "bmi",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Body Fat",
            key: "bodyFatPercent",
            imagePath: "bodyFat",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Muscle Mass",
            key: "musclePercent",
            imagePath: "muscle",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Body Water",
            key: "bodyWaterPercent",
            imagePath: "bodyWater",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Heart Rate",
            key: "heartRate",
            imagePath: "heartRate",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Bone Mass",
            key: "bonePercent",
            imagePath: "boneMass",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Visceral Fat",
            key: "visceralFatLevel",
            imagePath: "visceralFat",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Subcutaneous Fat",
            key: "subcutaneousFatPercent",
            imagePath: "subcutaneousFat",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Protein",
            key: "proteinPercent",
            imagePath: "protein",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Skeletal Muscles",
            key: "skeletalMusclePercent",
            imagePath: "skeletalMuscle",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Basal Metabolic Rate",
            key: "bmr",
            imagePath: "bmr",
            isEnabled: false,
            isProgressMetrics: false
        ),
        ScaleMetricSetting(
            name: "Metabolic Age",
            key: "metabolicAge",
            imagePath: "metabolicAge",
            isEnabled: false,
            isProgressMetrics: false
        ),
        
        // Progress Metrics
        ScaleMetricSetting(
            name: "Goal Progress",
            key: "goalProgress",
            isEnabled: false,
            isProgressMetrics: true
        ),
        ScaleMetricSetting(
            name: "Daily Average",
            key: "dailyAverage",
            isEnabled: false,
            isProgressMetrics: true
        ),
        ScaleMetricSetting(
            name: "Weekly Average",
            key: "weeklyAverage",
            isEnabled: false,
            isProgressMetrics: true
        ),
        ScaleMetricSetting(
            name: "Monthly Average",
            key: "monthlyAverage",
            isEnabled: false,
            isProgressMetrics: true
        )
    ]
    
    /// Returns all metrics filtered by progress metrics flag
    static var bodyMetrics: [ScaleMetricSetting] {
        return config.filter { !$0.isProgressMetrics }
    }
    
    /// Returns all progress metrics
    static var progressMetrics: [ScaleMetricSetting] {
        return config.filter { $0.isProgressMetrics }
    }
    
    /// Returns all metrics
    static var defaultMetricsKeys: [String] {
        return config.map { $0.key }
    }
}
