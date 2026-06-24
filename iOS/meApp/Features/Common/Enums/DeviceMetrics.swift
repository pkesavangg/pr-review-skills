//
//  DeviceMetrics.swift
//  meApp
//
//  Created by AI Assistant on 29/01/25.
//

import Foundation

/// Represents a scale metric setting configuration
struct DeviceMetricSetting: Identifiable, Equatable {
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
    
    /// Core reordering routine used when toggling metrics on/off.
    /// Moves the toggled metric to the end of its enabled/disabled group.
    /// Caller should update `isEnabled` on the target item before invoking this function.
    static func reorderOnToggle(items: [DeviceMetricSetting], key: String, isEnabled: Bool) -> [DeviceMetricSetting] {
        var current = items
        guard let idx = current.firstIndex(where: { $0.key == key }) else { return items }
        let changed = current.remove(at: idx)
        
        if !isEnabled {
            // Moving to disabled: append to end of disabled metrics
            let enabled = current.filter { $0.isEnabled }
            var disabled = current.filter { !$0.isEnabled }
            disabled.append(changed)
            return enabled + disabled
        } else {
            // Moving to enabled: append to end of enabled metrics
            var enabled = current.filter { $0.isEnabled }
            let disabled = current.filter { !$0.isEnabled }
            enabled.append(changed)
            return enabled + disabled
        }
    }
}

/// Central configuration for all scale metric settings
enum DeviceMetrics {
    static let config: [DeviceMetricSetting] = [
        // Body Composition Metrics
        DeviceMetricSetting(
            name: "Body Mass Index",
            key: "bmi",
            imagePath: "bmi",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Body Fat",
            key: "bodyFatPercent",
            imagePath: "bodyFat",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Muscle Mass",
            key: "musclePercent",
            imagePath: "muscle",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Body Water",
            key: "bodyWaterPercent",
            imagePath: "bodyWater",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Heart Rate",
            key: "heartRate",
            imagePath: "heartRate",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Bone Mass",
            key: "bonePercent",
            imagePath: "boneMass",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Visceral Fat",
            key: "visceralFatLevel",
            imagePath: "visceralFat",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Subcutaneous Fat",
            key: "subcutaneousFatPercent",
            imagePath: "subcutaneousFat",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Protein",
            key: "proteinPercent",
            imagePath: "protein",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Skeletal Muscles",
            key: "skeletalMusclePercent",
            imagePath: "skeletalMuscle",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Basal Metabolic Rate",
            key: "bmr",
            imagePath: "bmr",
            isEnabled: false,
            isProgressMetrics: false
        ),
        DeviceMetricSetting(
            name: "Metabolic Age",
            key: "metabolicAge",
            imagePath: "metabolicAge",
            isEnabled: false,
            isProgressMetrics: false
        ),
        
        // Progress Metrics
        DeviceMetricSetting(
            name: "Goal Progress",
            key: "goalProgress",
            isEnabled: false,
            isProgressMetrics: true
        ),
        DeviceMetricSetting(
            name: "Daily Average",
            key: "dailyAverage",
            isEnabled: false,
            isProgressMetrics: true
        ),
        DeviceMetricSetting(
            name: "Weekly Average",
            key: "weeklyAverage",
            isEnabled: false,
            isProgressMetrics: true
        ),
        DeviceMetricSetting(
            name: "Monthly Average",
            key: "monthlyAverage",
            isEnabled: false,
            isProgressMetrics: true
        )
    ]
    
    /// Returns all metrics filtered by progress metrics flag
    static var bodyMetrics: [DeviceMetricSetting] {
        return config.filter { !$0.isProgressMetrics }
    }
    
    /// Returns all progress metrics
    static var progressMetrics: [DeviceMetricSetting] {
        return config.filter { $0.isProgressMetrics }
    }
    
    /// Returns all metrics
    static var defaultMetricsKeys: [String] {
        return config.map { $0.key }
    }
}
