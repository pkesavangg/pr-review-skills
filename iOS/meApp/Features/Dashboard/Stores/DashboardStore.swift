//
//  DashboardStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

class DashboardStore: ObservableObject {
    let lang = DashboardStrings.self

    // MARK: - Edit Mode State
    @Published var isEditMode: Bool = false
    @Published var isGoalCardRemoved: Bool = false
    @Published var activeMetricsCount: Int = 12
    @Published var activeStreakItemsCount: Int = 6

    // MARK: - Original Data (never modified)
    private let originalMetrics: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
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

    private let originalStreakItems: [(icon: String?, value: String, label: String)] = [
        (AppAssets.streak, "1 day", DashboardStrings.currentStreak),
        (AppAssets.longestStreak, "10 day", DashboardStrings.longestStreak),
        (nil, "-1", DashboardStrings.lbsWeek),
        (nil, "-10", DashboardStrings.lbsMonth),
        (nil, "-20", DashboardStrings.lbsYear),
        (nil, "-30", DashboardStrings.lbsTotal)
    ]

    // MARK: - Current Data (reordered based on removal state)
    @Published var metrics: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = []
    @Published var streakItems: [(icon: String?, value: String, label: String)] = []

    // MARK: - Goal Card Data
    let goalDelta: Double = -13.2
    let goalStartWeight: Double = 154.3
    let goalGoalWeight: Double = 132.3
    let goalUnit: WeightUnit = .lb

    // MARK: - Initialization
    init() {
        // Initialize with original data
        metrics = originalMetrics
        streakItems = originalStreakItems
    }

    // MARK: - Metric Grid Columns
    var metricType: DashboardMetricType { 
        // Always use the original count to determine metric type
        originalMetrics.count == 12 ? .twelve : .four 
    }

    var metricGridColumns: [GridItem] {
        metricType == .four ?
            Array(repeating: GridItem(.flexible(), spacing: 16), count: 2) :
            Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)
    }

    var metricsToShow: [(index: Int, value: String, label: String, unit: String?, preLabel: String?, icon: String?)] {
        // In edit mode, show all metrics (active + removed)
        // When not in edit mode, only show active metrics
        let metricsToProcess = isEditMode ? metrics : Array(metrics.prefix(activeMetricsCount))
        
        // Apply the four-metric filter if needed (based on original count, not current count)
        if metricType == .four {
            // Only show bmi, body fat, muscle, water
            let fourMetrics: [BodyMetric] = [.bmi, .bodyFat, .muscleMass, .water]
            let metricMap: [String: BodyMetric] = [
                DashboardStrings.bmi: .bmi,
                DashboardStrings.bodyFat: .bodyFat,
                DashboardStrings.muscle: .muscleMass,
                DashboardStrings.water: .water
            ]
            return metricsToProcess.enumerated().compactMap { index, metric in
                if let metricType = metricMap[metric.label], fourMetrics.contains(metricType) {
                    return (index: index, value: metric.value, label: metric.label, unit: metric.unit, preLabel: metric.preLabel, icon: metric.icon)
                }
                return nil
            }
        } else {
            return metricsToProcess.enumerated().map { index, metric in
                (index: index, value: metric.value, label: metric.label, unit: metric.unit, preLabel: metric.preLabel, icon: metric.icon)
            }
        }
    }

    // MARK: - Streak Grid Columns
    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)
    
    var streakItemsToShow: [(index: Int, icon: String?, value: String, label: String)] {
        // In edit mode, show all streak items (active + removed)
        // When not in edit mode, only show active streak items
        let streakItemsToProcess = isEditMode ? streakItems : Array(streakItems.prefix(activeStreakItemsCount))
        
        return streakItemsToProcess.enumerated().map { index, item in
            (index: index, icon: item.icon, value: item.value, label: item.label)
        }
    }

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

    // MARK: - Edit Mode Methods
    func toggleMetricRemoval(at index: Int) {
        guard index < metrics.count else { return }
        
        let metric = metrics[index]
        let isCurrentlyRemoved = isMetricRemoved(at: index)
        
        if isCurrentlyRemoved {
            // Restore the metric - move it back to the active section
            metrics.remove(at: index)
            metrics.insert(metric, at: activeMetricsCount)
            activeMetricsCount += 1
        } else {
            // Remove the metric - move it to the end
            metrics.remove(at: index)
            metrics.append(metric)
            activeMetricsCount -= 1
        }
    }
    
    func isMetricRemoved(at index: Int) -> Bool {
        guard index < metrics.count else { return false }
        return index >= activeMetricsCount
    }
    
    func toggleGoalCardRemoval() {
        isGoalCardRemoved.toggle()
    }
    
    func toggleStreakRemoval(at index: Int) {
        guard index < streakItems.count else { return }
        
        let item = streakItems[index]
        let isCurrentlyRemoved = isStreakRemoved(at: index)
        
        if isCurrentlyRemoved {
            // Restore the streak item - move it back to the active section
            streakItems.remove(at: index)
            streakItems.insert(item, at: activeStreakItemsCount)
            activeStreakItemsCount += 1
        } else {
            // Remove the streak item - move it to the end
            streakItems.remove(at: index)
            streakItems.append(item)
            activeStreakItemsCount -= 1
        }
    }
    
    func isStreakRemoved(at index: Int) -> Bool {
        guard index < streakItems.count else { return false }
        return index >= activeStreakItemsCount
    }
    
    func resetDashboard() {
        // Restore original order by moving all removed items back to their original positions
        restoreOriginalMetricOrder()
        restoreOriginalStreakOrder()
        activeMetricsCount = originalMetrics.count
        activeStreakItemsCount = originalStreakItems.count
        isGoalCardRemoved = false
        isEditMode = false
    }
    
    func saveChanges() {
        isEditMode = false
        // DO NOT permanently delete removed items - just exit edit mode
        // The arrays keep all items, but only active ones are shown in normal mode
    }
    
    // MARK: - Helper Methods
    private func restoreOriginalMetricOrder() {
        // Restore original order by moving all items back to their original positions
        metrics = originalMetrics
    }
    
    private func restoreOriginalStreakOrder() {
        // Restore original order by moving all items back to their original positions
        streakItems = originalStreakItems
    }

    // MARK: - Reordered Array Methods
    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return false }
        let originalIndex = metricsToShow[reorderedIndex].index
        return isMetricRemoved(at: originalIndex)
    }
    
    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return }
        let originalIndex = metricsToShow[reorderedIndex].index
        toggleMetricRemoval(at: originalIndex)
    }
    
    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let originalIndex = streakItemsToShow[reorderedIndex].index
        return isStreakRemoved(at: originalIndex)
    }
    
    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int) {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return }
        let originalIndex = streakItemsToShow[reorderedIndex].index
        toggleStreakRemoval(at: originalIndex)
    }

}
