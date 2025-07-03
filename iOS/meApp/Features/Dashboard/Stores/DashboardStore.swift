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
}

class DashboardStore: ObservableObject {
    // MARK: - Services
    @Injector private var notificationService: NotificationHelperService
    @Injector private var accountService: AccountService

    // MARK: - Loader State
    @Published var isLoading: Bool = false
    @Published var loaderOverride: LoaderModel? = nil
    @Published var alertData: AlertModel? = nil
    
    // MARK: - Common Strings/Labels as variables
    let lang = LoaderStrings.self

    // MARK: - Device-specific Metric Type
    @Published var metricType: DashboardMetricType = .twelve  // <--- Set explicitly, not by count!

    // MARK: - Edit Mode State
    @Published var isEditMode: Bool = false
    @Published var isGoalCardRemoved: Bool = false
    @Published var activeMetricsCount: Int = 12
    @Published var activeStreakItemsCount: Int = 6

    // MARK: - Drag and Drop State
    @Published var dropHoverId: String? = nil
    @Published var gridLayoutId = UUID()
    @Published var draggingMetric: MetricItem? = nil
    @Published var draggingStreak: MetricItem? = nil

    // MARK: - Selected Metric State
    @Published var selectedMetricLabel: String? = nil
    @Published var goalType: GoalType = .gain
    @Published var goalStartWeight: Double = 0.0
    @Published var goalWeight: Double = 0.0
    @Published var goalUnit: WeightUnit = .lb
    @Published var goalDelta: Double = 0.0
    @Published var goalProgress: CGFloat = 0.0
    
    private var latestWeightStored: Int = 0
    @Injector private var entryService: EntryService
    
    /// Loader binding for presentLoader
    var loaderData: Binding<LoaderModel?> {
        Binding(
            get: { self.loaderOverride ?? (self.isLoading ? LoaderModel(text: self.lang.saving) : nil) },
            set: { _ in }
        )
    }

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

    private let originalStreakItems: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        ("1 day", DashboardStrings.currentStreak, nil, nil, AppAssets.streak),
        ("10 day", DashboardStrings.longestStreak, nil, nil, AppAssets.longestStreak),
        ("-1", DashboardStrings.lbsWeek, nil, nil, nil),
        ("-10", DashboardStrings.lbsMonth, nil, nil, nil),
        ("-20", DashboardStrings.lbsYear, nil, nil, nil),
        ("-30", DashboardStrings.lbsTotal, nil, nil, nil)
    ]

    // MARK: - Current Data (reordered based on removal state)
    @Published var metrics: [MetricItem] = []
    @Published var streakItems: [MetricItem] = []

    // MARK: - Initialization
    init() {
        // Initialize with original data
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        // metricType can be set externally after init depending on device type
    }

    // MARK: - Metric Grid Columns
    var metricGridColumns: [GridItem] {
        metricType == .four ?
            Array(repeating: GridItem(.flexible(), spacing: 16), count: 2) :
            Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)
    }

    var metricsToShow: [MetricItem] {
        let metricsToProcess = isEditMode ? metrics : Array(metrics.prefix(activeMetricsCount))
        if metricType == .four {
            let fourLabels: Set<String> = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
            return metricsToProcess.filter { fourLabels.contains($0.label) }
        } else {
            return metricsToProcess
        }
    }

    // MARK: - Streak Grid Columns
    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)

    var streakItemsToShow: [MetricItem] {
        isEditMode ? streakItems : Array(streakItems.prefix(activeStreakItemsCount))
    }

    // MARK: - Entry Creation Helper
    func createEntryForMetricInfo() -> Entry {
        let bmiStr = metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
        let bodyFatStr = metrics.first(where: { $0.label == DashboardStrings.bodyFat })?.value
        let muscleStr = metrics.first(where: { $0.label == DashboardStrings.muscle })?.value
        let waterStr = metrics.first(where: { $0.label == DashboardStrings.water })?.value

        let bmi = bmiStr.flatMap { Double($0) }.flatMap { Int($0) }
        let bodyFat = bodyFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let muscleMass = muscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let water = waterStr.flatMap { Double($0) }.flatMap { Int($0) }
        let mockWeight = bmi.map { Int(Double($0) * 2.5) }

        let bmrStr = metrics.first(where: { $0.label == DashboardStrings.bmrKcal })?.value
        let metabolicAgeStr = metrics.first(where: { $0.label == DashboardStrings.metAge })?.value
        let pulseStr = metrics.first(where: { $0.label == DashboardStrings.heartBpm })?.value
        let skeletalMuscleStr = metrics.first(where: { $0.label == DashboardStrings.skelMuscle })?.value
        let subFatStr = metrics.first(where: { $0.label == DashboardStrings.subFat })?.value
        let visceralFatStr = metrics.first(where: { $0.label == DashboardStrings.visceralFat })?.value
        let boneStr = metrics.first(where: { $0.label == DashboardStrings.bone })?.value
        let proteinStr = metrics.first(where: { $0.label == DashboardStrings.protein })?.value

        let bmr = bmrStr.flatMap { Double($0) }.flatMap { Int($0) }
        let metabolicAge = metabolicAgeStr.flatMap { Double($0) }.flatMap { Int($0) }
        let pulse = pulseStr.flatMap { Double($0) }.flatMap { Int($0) }
        let skeletalMusclePercent = skeletalMuscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let subcutaneousFatPercent = subFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let visceralFatLevel = visceralFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let _ = boneStr.flatMap { Double($0) }.flatMap { Int($0) }
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
            boneMass: boneStr.flatMap { Double($0) }.flatMap { Int($0) },
            impedance: nil,
            unit: nil
        )
        return entry
    }
    
    func createEntryForMetricInfo(metricLabel: String) -> Entry {
        // Create the same entry as the default method, but we can customize based on metricLabel if needed
        return createEntryForMetricInfo()
    }

    func getBodyMetric(for metricLabel: String) -> BodyMetric {
        switch metricLabel {
        case DashboardStrings.bmi:
            return .bmi
        case DashboardStrings.bodyFat:
            return .bodyFat
        case DashboardStrings.muscle:
            return .muscleMass
        case DashboardStrings.water:
            return .water
        default:
            return .weight // Default fallback
        }
    }

    // MARK: - Edit Mode Methods
    func toggleMetricRemoval(at index: Int) {
        guard index < metrics.count else { return }
        let metric = metrics[index]
        let isCurrentlyRemoved = isMetricRemoved(at: index)
        metrics.remove(at: index)
        if isCurrentlyRemoved {
            metrics.insert(metric, at: activeMetricsCount)
            activeMetricsCount += 1
        } else {
            metrics.append(metric)
            activeMetricsCount -= 1
        }
        
        // Reset drag state and force UI refresh
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
        streakItems.remove(at: index)
        if isCurrentlyRemoved {
            streakItems.insert(item, at: activeStreakItemsCount)
            activeStreakItemsCount += 1
        } else {
            streakItems.append(item)
            activeStreakItemsCount -= 1
        }
        
        // Reset drag state and force UI refresh
        resetDragState()
    }

    func isStreakRemoved(at index: Int) -> Bool {
        guard index < streakItems.count else { return false }
        return index >= activeStreakItemsCount
    }

    func resetDashboard() {
        isLoading = true
        loaderOverride = LoaderModel(text: lang.saving)
        
        // Clear all state immediately to prevent unwanted behavior
        selectedMetricLabel = nil
        isEditMode = false
        resetDragState()
        
        // Simulate reset operation with a delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.restoreOriginalMetricOrder()
                self.restoreOriginalStreakOrder()
                self.activeMetricsCount = self.originalMetrics.count
                self.activeStreakItemsCount = self.originalStreakItems.count
                self.isGoalCardRemoved = false
                
                // Ensure all state is properly reset
                self.selectedMetricLabel = nil
                self.isEditMode = false
                self.resetDragState()
                
                // Update grid layout ID to force UI refresh
                self.gridLayoutId = UUID()
            }
        }
    }

    @MainActor
    func showResetDashboardAlert() {
        let alertLang = AlertStrings.ResetDashboardAlert.self
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.cancelButton, type: .secondary) { _ in
                    // Do nothing, just dismiss alert
                },
                AlertButtonModel(title: alertLang.resetButton, type: .primary) { _ in
                    self.resetDashboard()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func saveChanges() {
        isLoading = true
        loaderOverride = LoaderModel(text: lang.saving)
        
        // Clear selection and drag state immediately
        selectedMetricLabel = nil
        resetDragState()
        
        // Simulate save operation with a delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.isEditMode = false
                self.resetDragState()
            }
        }
    }

    // MARK: - Helper Methods
    private func restoreOriginalMetricOrder() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }
    private func restoreOriginalStreakOrder() {
        streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
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

    // MARK: - Selection Methods
    func selectMetric(_ label: String) {
        // If the same metric is tapped again, deselect it
        if selectedMetricLabel == label {
            selectedMetricLabel = nil
        } else {
            // Otherwise, select the new metric
            selectedMetricLabel = label
        }
    }

    // MARK: - Drag State Management
    func resetDragState() {
        // Reset all drag state immediately without animation to prevent delays
        draggingMetric = nil
        draggingStreak = nil
        dropHoverId = nil
        
        // Force UI refresh by updating grid layout ID
        gridLayoutId = UUID()
    }

    var isAnyItemBeingDragged: Bool {
        draggingMetric != nil || draggingStreak != nil
    }

    // MARK: - Computed Properties for View
    @MainActor
    var allContentRemoved: Bool {
        metricsToShow.isEmpty && (!isEditMode && isGoalCardRemoved) && (!shouldShowStreakGrid)
    }

    // MARK: - Streak Visibility
    @MainActor
    var isStreakEnabled: Bool {
        accountService.activeAccount?.streaksSettings?.isStreakOn ?? false
    }

    @MainActor
    var shouldShowStreakGrid: Bool {
        isStreakEnabled && !streakItemsToShow.isEmpty
    }

    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }

    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = selectedMetricLabel else { return .weight }
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
            return .weight
        }
    }
    
    // MARK: - Goal Card Data
    @MainActor
    private func displayWeight(fromStored stored: Int) -> Double {
        let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        if unit == .kg {
            return ConversionTools.convertStoredToKg(stored)
        } else {
            return ConversionTools.convertStoredToLbs(stored)
        }
    }

    @MainActor
    func loadGoalCardData() {
        Task { [weak self] in
            guard let self = self else { return }
            do {
                // Get latest entry for current weight
                let latestEntry = try await self.entryService.getLatestEntry()
                var currentWeightStored: Int = 0
                if let latestWeight = latestEntry?.scaleEntry?.weight {
                    currentWeightStored = latestWeight
                }
                
                guard let account = self.accountService.activeAccount,
                      let goalSettings = account.goalSettings else { 
                    print("Dashboard: No account or goal settings found")
                    return 
                }
                
                // Set goal type from account settings
                self.goalType = goalSettings.goalType ?? .gain
                self.goalUnit = account.weightSettings?.weightUnit ?? .lb
                
                // Get initial weight from goal settings (this is the starting weight when goal was set)
                let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
                self.goalStartWeight = self.displayWeight(fromStored: initialWeightStored)
                
                // Get goal weight from goal settings
                if let goalW = goalSettings.goalWeight {
                    self.goalWeight = self.displayWeight(fromStored: Int(goalW))
                }
                
                // Calculate current weight
                let currentWeight = self.displayWeight(fromStored: currentWeightStored)
                
                // Calculate delta (current weight - initial weight)
                self.goalDelta = currentWeight - self.goalStartWeight
                
                // Debug prints
                print("Dashboard Goal Data:")
                print("  Initial Weight (stored): \(initialWeightStored)")
                print("  Initial Weight (display): \(self.goalStartWeight)")
                print("  Current Weight (stored): \(currentWeightStored)")
                print("  Current Weight (display): \(currentWeight)")
                print("  Goal Weight (stored): \(goalSettings.goalWeight ?? 0)")
                print("  Goal Weight (display): \(self.goalWeight)")
                print("  Delta: \(self.goalDelta)")
                print("  Goal Type: \(self.goalType)")
                print("  Unit: \(self.goalUnit)")
                
                // Calculate progress
                let totalDistance = abs(self.goalWeight - self.goalStartWeight)
                let achievedDistance = abs(currentWeight - self.goalStartWeight)
                
                if totalDistance > 0 {
                    // For gain goals: progress increases as weight increases
                    // For lose goals: progress increases as weight decreases
                    let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                    self.goalProgress = progress
                    print("  Progress: \(progress)")
                } else {
                    self.goalProgress = 1.0
                    print("  Progress: 1.0 (no distance to goal)")
                }
                
            } catch {
                // Handle error if needed
                print("Error loading goal card data: \(error)")
            }
        }
    }
}
