//
//  DashboardStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

// MARK: - Identifiable Data Structures
struct MetricItem: Identifiable, Equatable {
    let id = UUID()
    let value: String
    let label: String
    let unit: String?
    let preLabel: String?
    let icon: String?
    
    init(value: String, label: String, unit: String?, preLabel: String?, icon: String?) {
        self.value = value
        self.label = label
        self.unit = unit
        self.preLabel = preLabel
        self.icon = icon
    }
}

struct StreakItem: Identifiable, Equatable {
    let id = UUID()
    let icon: String?
    let value: String
    let label: String
    
    init(icon: String?, value: String, label: String) {
        self.icon = icon
        self.value = value
        self.label = label
    }
}

class DashboardStore: ObservableObject {
    let lang = DashboardStrings.self
    
    // MARK: - Edit Mode State
    @Published var isEditMode: Bool = false
    @Published var isGoalCardRemoved: Bool = false
    @Published var activeMetricsCount: Int = 12
    @Published var activeStreakItemsCount: Int = 6
    
    // MARK: - Drag and Drop State
    @Published var dropHoverId: String? = nil
    @Published var gridLayoutId = UUID()
    @Published var draggingMetric: MetricItem? = nil
    @Published var draggingStreak: StreakItem? = nil
    
    // MARK: - Selected Metric State
    @Published var selectedMetricLabel: String? = nil
    @Published var goalType: GoalType = .gain
    
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
    @Published var metrics: [MetricItem] = []
    @Published var streakItems: [StreakItem] = []
    
    // MARK: - Goal Card Data
    let goalDelta: Double = -13.2
    let goalStartWeight: Double = 154.3
    let goalWeight: Double = 132.3
    let goalUnit: WeightUnit = .lb
    var goalProgress: CGFloat {
        let total = abs(goalStartWeight - goalWeight)
        guard total > 0 else { return 1.0 }
        let achieved = abs(goalStartWeight - (goalStartWeight + goalDelta))
        return min(max(CGFloat(achieved / total), 0), 1)
    }
    
    // MARK: - Initialization
    init() {
        // Initialize with original data
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        streakItems = originalStreakItems.map { StreakItem(icon: $0.icon, value: $0.value, label: $0.label) }
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
    
    var metricsToShow: [MetricItem] {
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
            return metricsToProcess.compactMap { metric in
                if let metricType = metricMap[metric.label], fourMetrics.contains(metricType) {
                    return metric
                }
                return nil
            }
        } else {
            return metricsToProcess
        }
    }
    
    // MARK: - Streak Grid Columns
    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)
    
    var streakItemsToShow: [StreakItem] {
        // In edit mode, show all streak items (active + removed)
        // When not in edit mode, only show active streak items
        let streakItemsToProcess = isEditMode ? streakItems : Array(streakItems.prefix(activeStreakItemsCount))
        
        return streakItemsToProcess
    }
    
    // MARK: - Entry Creation Helper
    func createEntryForMetricInfo() -> Entry {
        // Get values from the metrics array using the correct labels
        let bmiStr = metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
        let bodyFatStr = metrics.first(where: { $0.label == DashboardStrings.bodyFat })?.value
        let muscleStr = metrics.first(where: { $0.label == DashboardStrings.muscle })?.value
        let waterStr = metrics.first(where: { $0.label == DashboardStrings.water })?.value
        
        // Convert string values to appropriate types (Int for whole numbers, Double for decimals)
        let bmi = bmiStr.flatMap { Double($0) }.flatMap { Int($0) }
        let bodyFat = bodyFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let muscleMass = muscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let water = waterStr.flatMap { Double($0) }.flatMap { Int($0) }
        
        // For demo purposes, calculate a mock weight from BMI (assuming average height)
        // In a real app, you would get the actual weight from the latest entry
        let mockWeight = bmi.map { Int(Double($0) * 2.5) } // Rough calculation for demo
        
        let bmrStr = metrics.first(where: { $0.label == DashboardStrings.bmrKcal })?.value
        let metabolicAgeStr = metrics.first(where: { $0.label == DashboardStrings.metAge })?.value
        let pulseStr = metrics.first(where: { $0.label == DashboardStrings.heartBpm })?.value
        let skeletalMuscleStr = metrics.first(where: { $0.label == DashboardStrings.skelMuscle })?.value
        let subFatStr = metrics.first(where: { $0.label == DashboardStrings.subFat })?.value
        let visceralFatStr = metrics.first(where: { $0.label == DashboardStrings.visceralFat })?.value
        let boneStr = metrics.first(where: { $0.label == DashboardStrings.bone })?.value
        let proteinStr = metrics.first(where: { $0.label == DashboardStrings.protein })?.value
        
        // Convert to appropriate types - some need to be Int, others Double
        let bmr = bmrStr.flatMap { Double($0) }.flatMap { Int($0) }
        let metabolicAge = metabolicAgeStr.flatMap { Double($0) }.flatMap { Int($0) }
        let pulse = pulseStr.flatMap { Double($0) }.flatMap { Int($0) }
        let skeletalMusclePercent = skeletalMuscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let subcutaneousFatPercent = subFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let visceralFatLevel = visceralFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let boneMass = boneStr.flatMap { Double($0) }.flatMap { Int($0) }
        let proteinPercent = proteinStr.flatMap { Double($0) }.flatMap { Int($0) }
        
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: "create",
            deviceType: "scale",
            isSynced: true
        )
        entry.scaleEntry = BathScaleEntry(
            weight: mockWeight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: "dashboard"
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
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
        
        // Reset drag state to ensure circle icons appear immediately
        resetDragState()
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
        
        // Reset drag state to ensure circle icons appear immediately
        resetDragState()
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
        resetDragState()
    }
    
    func saveChanges() {
        isEditMode = false
        resetDragState()
        // DO NOT permanently delete removed items - just exit edit mode
    }
    
    // MARK: - Helper Methods
    private func restoreOriginalMetricOrder() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }
    
    private func restoreOriginalStreakOrder() {
        streakItems = originalStreakItems.map { StreakItem(icon: $0.icon, value: $0.value, label: $0.label) }
    }
    
    // MARK: - Reordered Array Methods
    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return false }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metrics.firstIndex(where: { $0.id == metric.id }) else { return false }
        return isMetricRemoved(at: originalIndex)
    }
    
    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metrics.firstIndex(where: { $0.id == metric.id }) else { return }
        toggleMetricRemoval(at: originalIndex)
    }
    
    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = streakItems.firstIndex(where: { $0.id == streak.id }) else { return false }
        return isStreakRemoved(at: originalIndex)
    }
    
    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int) {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = streakItems.firstIndex(where: { $0.id == streak.id }) else { return }
        toggleStreakRemoval(at: originalIndex)
    }
    
    // MARK: - Helper Methods for Views
    func getMetricId(for index: Int) -> String {
        guard index < metrics.count else { return "" }
        return metrics[index].label
    }
    
    func getStreakId(for index: Int) -> String {
        guard index < streakItems.count else { return "" }
        return streakItems[index].label
    }
    
    // MARK: - Selection Methods
    func selectMetric(_ label: String) {
        // Toggle selection: if the same metric is selected again, deselect it
        if selectedMetricLabel == label {
            selectedMetricLabel = nil
        } else {
            selectedMetricLabel = label
        }
    }
    
    // MARK: - Drag State Management
    func resetDragState() {
        // Immediately reset all drag state to prevent flickering
        withAnimation(.easeInOut(duration: 0.1)) {
            draggingMetric = nil
            draggingStreak = nil
            dropHoverId = nil
        }
    }
    
    // MARK: - Computed Properties for Drag State
    var isAnyItemBeingDragged: Bool {
        draggingMetric != nil || draggingStreak != nil
    }
    
    // MARK: - Drag State Timeout Management
    func startDragTimeout() {
        // Reset drag state after 5 seconds to prevent stuck state
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
            if self.isAnyItemBeingDragged {
                self.resetDragState()
            }
        }
    }
    
    // MARK: - Computed Properties for View
    /// Returns true if all dashboard content is removed (for hiding sections in the view)
    var allContentRemoved: Bool {
        metricsToShow.isEmpty && (!isEditMode && isGoalCardRemoved) && streakItemsToShow.isEmpty
    }
    
    /// Returns the formatted value for a metric (handles preLabel)
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }
    
    /// Returns the BodyMetric enum for the selected metric label
    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = selectedMetricLabel else { return .bmi }
        
        switch selectedLabel {
        case DashboardStrings.bmi:
            return .bmi
        case DashboardStrings.bodyFat:
            return .bodyFat
        case DashboardStrings.muscle:
            return .muscleMass
        case DashboardStrings.water:
            return .water
        case DashboardStrings.heartBpm:
            return .pulse
        case DashboardStrings.bone:
            return .boneMass
        case DashboardStrings.visceralFat:
            return .visceralFatLevel
        case DashboardStrings.subFat:
            return .subcutaneousFatPercent
        case DashboardStrings.protein:
            return .proteinPercent
        case DashboardStrings.skelMuscle:
            return .skeletalMusclePercent
        case DashboardStrings.bmrKcal:
            return .bmr
        case DashboardStrings.metAge:
            return .metabolicAge
        default:
            return .bmi
        }
    }
}
