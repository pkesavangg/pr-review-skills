//
//  BodyMetrics.swift
//  meApp
//
//  Created by Barath Chittibabu on 19/06/25.
//

import Foundation

/// Central lookup for all metric definitions used in the app.
/// Usage: `BodyMetrics.config[.bmi]?.label` etc.
///
/// This enum provides a static dictionary that maps each `BodyMetric` enum case to its corresponding `MetricData` configuration.
/// Each `MetricData` instance contains the necessary information for rendering a metric item, including:
/// - `unit`: The unit of measurement (e.g., "kg", "%", "bpm", etc.)
enum BodyMetrics {
    static let config: [BodyMetric: MetricData] = [
        .weight: MetricData(
            unit: "",
            label: MetricStrings.weight,
            bodyCompositionRelated: true,
            icon: AppAssets.bmiIcon
        ),
        .bmi: MetricData(
            unit: "",
            label: MetricStrings.bmi,
            expandedLabel: "Body Mass Index",
            bodyCompositionRelated: true,
            icon: AppAssets.bmiIcon
        ),
        .bodyFat: MetricData(
            unit: "%",
            label: MetricStrings.bodyFat,
            bodyCompositionRelated: true,
            icon: AppAssets.bodyFatIcon
        ),
        .muscleMass: MetricData(
            unit: "%",
            label: MetricStrings.muscleMass,
            bodyCompositionRelated: true,
            icon: AppAssets.muscleIcon
        ),
        .water: MetricData(
            unit: "%",
            label: MetricStrings.bodyWater,
            bodyCompositionRelated: true,
            icon: AppAssets.waterIcon
        ),
        .pulse: MetricData(
            unit: "bpm",
            label: MetricStrings.heartRate,
            bodyCompositionRelated: false,
            icon: AppAssets.heartIcon,
            min: 0,
            max: 200,
            isWholeNumber: true
        ),
        .boneMass: MetricData(
            unit: "%",
            label: MetricStrings.boneMass,
            bodyCompositionRelated: true,
            icon: AppAssets.boneIcon
        ),
        .visceralFatLevel: MetricData(
            unit: "",
            label: MetricStrings.visceralFat,
            bodyCompositionRelated: false,
            icon: AppAssets.visceralFatIcon,
            isWholeNumber: true,
            preLabel: "Lv."
        ),
        .subcutaneousFatPercent: MetricData(
            unit: "%",
            label: MetricStrings.subcutaneousFat,
            bodyCompositionRelated: true,
            icon: AppAssets.subcutaneousFatIcon
        ),
        .proteinPercent: MetricData(
            unit: "%",
            label: MetricStrings.protein,
            bodyCompositionRelated: true,
            icon: AppAssets.proteinIcon
        ),
        .skeletalMusclePercent: MetricData(
            unit: "%",
            label: MetricStrings.skeletalMuscles,
            bodyCompositionRelated: true,
            icon: AppAssets.skeletalMuscleIcon
        ),
        .bmr: MetricData(
            unit: "kcal",
            label: MetricStrings.basalMetabolicRate,
            expandedLabel: "Basal Metabolic Rate",
            bodyCompositionRelated: true,
            icon: AppAssets.bmrIcon,
            min: 0,
            max: 10000,
            isWholeNumber: true
        ),
        .metabolicAge: MetricData(
            unit: "yrs",
            label: MetricStrings.metabolicAge,
            bodyCompositionRelated: false,
            icon: AppAssets.ageIcon,
            min: 0,
            max: 150,
            isWholeNumber: true
        )
    ]
}
