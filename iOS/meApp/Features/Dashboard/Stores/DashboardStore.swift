//
//  DashboardStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

class DashboardStore: ObservableObject {
    let lang = DashboardStrings.self

    // MARK: - Metrics Data
    let metrics: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        ("24.5", DashboardStrings.bmi, nil, nil, nil),
        ("18.3", DashboardStrings.bodyFat, DashboardStrings.bodyFatUnit, nil, nil),
        ("41.6", DashboardStrings.muscle, DashboardStrings.muscleUnit, nil, nil),
        ("59.1", DashboardStrings.water, DashboardStrings.waterUnit, nil, nil),
        ("80", DashboardStrings.heartBpm, DashboardStrings.heartBpmUnit, nil, nil),
        ("4.4", DashboardStrings.bone, DashboardStrings.boneUnit, nil, nil),
        ("8", DashboardStrings.visceralFat, nil, DashboardStrings.visceralFatPre, nil),
        ("10.3", DashboardStrings.subFat, DashboardStrings.subFatUnit, nil, nil),
        ("18.6", DashboardStrings.protein, DashboardStrings.proteinUnit, nil, nil),
        ("52.7", DashboardStrings.skelMuscle, DashboardStrings.skelMuscleUnit, nil, nil),
        ("1862", DashboardStrings.bmrKcal, DashboardStrings.bmrKcalUnit, nil, nil),
        ("28", DashboardStrings.metAge, DashboardStrings.metAgeUnit, nil, nil)
    ]

    // MARK: - Streak/Loss Items
    let streakItems: [(icon: String?, value: String, label: String)] = [
        (AppAssets.streak, "1 day", DashboardStrings.currentStreak),
        (AppAssets.longestStreak, "10 day", DashboardStrings.longestStreak),
        (nil, "-1", DashboardStrings.lbsWeek),
        (nil, "-10", DashboardStrings.lbsMonth),
        (nil, "-20", DashboardStrings.lbsYear),
        (nil, "-30", DashboardStrings.lbsTotal)
    ]

    // MARK: - Goal Card Data
    let goalDelta: Double = -13.2
    let goalStartWeight: Double = 154.3
    let goalGoalWeight: Double = 132.3
    let goalUnit: WeightUnit = .lb

    // MARK: - Metric Grid Columns
    var metricType: DashboardMetricType { metrics.count == 12 ? .twelve : .four }

    var metricGridColumns: [GridItem] {
        metricType == .four ?
            Array(repeating: GridItem(.flexible(), spacing: 16), count: 2) :
            Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)
    }

    var metricsToShow: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] {
        if metricType == .four {
            // Only show bmi, body fat, muscle, water
            let fourMetrics: [BodyMetric] = [.bmi, .bodyFat, .muscleMass, .water]
            let metricMap: [String: BodyMetric] = [
                DashboardStrings.bmi: .bmi,
                DashboardStrings.bodyFat: .bodyFat,
                DashboardStrings.muscle: .muscleMass,
                DashboardStrings.water: .water
            ]
            return metrics.compactMap { item in
                if let metric = metricMap[item.label], fourMetrics.contains(metric) {
                    return item
                }
                return nil
            }
        } else {
            return metrics
        }
    }

    // MARK: - Streak Grid Columns
    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)

    // MARK: - Entry Creation Helper
    func createEntryForMetricInfo() -> Entry {
        let weightStr = metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
        let bodyFatStr = metrics.first(where: { $0.label == DashboardStrings.bodyFat })?.value
        let muscleStr = metrics.first(where: { $0.label == DashboardStrings.muscle })?.value
        let waterStr = metrics.first(where: { $0.label == DashboardStrings.water })?.value
        let weight = weightStr.flatMap { Int($0) }
        let bodyFat = bodyFatStr.flatMap { Int($0) }
        let muscleMass = muscleStr.flatMap { Int($0) }
        let water = waterStr.flatMap { Int($0) }

        let bmrStr = metrics.first(where: { $0.label == DashboardStrings.bmrKcal })?.value
        let metabolicAgeStr = metrics.first(where: { $0.label == DashboardStrings.metAge })?.value
        let pulseStr = metrics.first(where: { $0.label == DashboardStrings.heartBpm })?.value
        let skeletalMuscleStr = metrics.first(where: { $0.label == DashboardStrings.skelMuscle })?.value
        let subFatStr = metrics.first(where: { $0.label == DashboardStrings.subFat })?.value
        let visceralFatStr = metrics.first(where: { $0.label == DashboardStrings.visceralFat })?.value
        let boneStr = metrics.first(where: { $0.label == DashboardStrings.bone })?.value
        let bmr = bmrStr.flatMap { Int($0) }
        let metabolicAge = metabolicAgeStr.flatMap { Int($0) }
        let pulse = pulseStr.flatMap { Int($0) }
        let skeletalMusclePercent = skeletalMuscleStr.flatMap { Int($0) }
        let subcutaneousFatPercent = subFatStr.flatMap { Int($0) }
        let visceralFatLevel = visceralFatStr.flatMap { Int($0) }
        let boneMass = boneStr.flatMap { Int($0) }

        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: "create",
            deviceType: "scale",
            isSynced: true
        )
        entry.scaleEntry = BathScaleEntry(
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: bmr,
            metabolicAge: metabolicAge,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass,
            impedance: nil,
            unit: nil
        )
        return entry
    }
}
