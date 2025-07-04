//
//  DashboardStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

class DashboardStore: ObservableObject {
    @Injector private var notificationService: NotificationHelperService
    @Injector private var accountService: AccountService
    @Injector private var logger : LoggerService
    @Published var isLoading: Bool = false
    @Published var loaderOverride: LoaderModel? = nil
    @Published var alertData: AlertModel? = nil
    
    let lang = LoaderStrings.self

    @Published var metricType: DashboardMetricType = .twelve

    @Published var isEditMode: Bool = false
    @Published var isGoalCardRemoved: Bool = false
    @Published var activeMetricsCount: Int = 12
    @Published var activeStreakItemsCount: Int = 6

    @Published var dropHoverId: String? = nil
    @Published var gridLayoutId = UUID()
    @Published var draggingMetric: MetricItem? = nil
    @Published var draggingStreak: MetricItem? = nil

    @Published var selectedMetricLabel: String? = nil
    @Published var goalType: GoalType = .gain
    @Published var goalStartWeight: Double = 0.0
    @Published var goalWeight: Double = 0.0
    @Published var goalUnit: WeightUnit = .lb
    @Published var goalDelta: Double = 0.0
    @Published var goalProgress: CGFloat = 0.0
    
    private var latestWeightStored: Int = 0
    @Injector private var entryService: EntryService
    
    var loaderData: Binding<LoaderModel?> {
        Binding(
            get: { self.loaderOverride ?? (self.isLoading ? LoaderModel(text: self.lang.saving) : nil) },
            set: { _ in }
        )
    }

    private let originalMetrics: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        (DashboardStrings.placeholder, DashboardStrings.bmi, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.bodyFat, DashboardStrings.bodyFatUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.muscle, DashboardStrings.muscleUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.water, DashboardStrings.waterUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.heartBpm, DashboardStrings.heartBpmUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.bone, DashboardStrings.boneUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.visceralFat, nil, DashboardStrings.visceralFatPre, nil),
        (DashboardStrings.placeholder, DashboardStrings.subFat, DashboardStrings.subFatUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.protein, DashboardStrings.proteinUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.skelMuscle, DashboardStrings.skelMuscleUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.bmrKcal, DashboardStrings.bmrKcalUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.metAge, DashboardStrings.metAgeUnit, nil, nil)
    ]

    private let originalStreakItems: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        (DashboardStrings.placeholder, DashboardStrings.currentStreak, nil, nil, AppAssets.streak),
        (DashboardStrings.placeholder, DashboardStrings.longestStreak, nil, nil, AppAssets.longestStreak),
        (DashboardStrings.placeholder, DashboardStrings.lbsWeek, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsMonth, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsYear, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsTotal, nil, nil, nil)
    ]

    @Published var metrics: [MetricItem] = []
    @Published var streakItems: [MetricItem] = []

    init() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        Task {
            await loadLatestEntryData()
        }
    }
    
    @MainActor
    func loadLatestEntryData() {
        Task {
            do {
                guard let latestEntry = try await entryService.getLatestEntry() else {
                    return
                }
                if let weight = latestEntry.scaleEntry?.weight {
                    latestWeightStored = weight
                }
                updateMetricsWithEntry(latestEntry)
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to load latest entry data: \(error)")
            }
        }
    }
    
    private func updateMetricsWithEntry(_ entry: Entry) {
        if let bmi = entry.scaleEntry?.bmi {
            let formattedValue = BodyMetricsConvertor.convert(Double(bmi), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }
        
        if let bodyFat = entry.scaleEntry?.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(Double(bodyFat), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }
        
        if let muscleMass = entry.scaleEntry?.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(muscleMass), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }
        
        if let water = entry.scaleEntry?.water {
            let formattedValue = BodyMetricsConvertor.convert(Double(water), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }
        
        if let pulse = entry.scaleEntryMetric?.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }
        
        if let boneMass = entry.scaleEntryMetric?.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(boneMass), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }
        
        if let visceralFat = entry.scaleEntryMetric?.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(Double(visceralFat), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }
        
        if let subFat = entry.scaleEntryMetric?.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(subFat), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }
        
        if let protein = entry.scaleEntryMetric?.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(protein), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }
        
        if let skelMuscle = entry.scaleEntryMetric?.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(skelMuscle), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }
        
        if let bmr = entry.scaleEntryMetric?.bmr {
            let formattedValue = BodyMetricsConvertor.convert(Double(bmr), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }
        
        if let metabolicAge = entry.scaleEntryMetric?.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }
    
    private func updateMetricValue(for label: String, value: String) {
        if let index = metrics.firstIndex(where: { $0.label == label }) {
            metrics[index] = MetricItem(
                value: value,
                label: metrics[index].label,
                unit: metrics[index].unit,
                preLabel: metrics[index].preLabel,
                icon: metrics[index].icon
            )
        }
    }

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

    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)

    var streakItemsToShow: [MetricItem] {
        isEditMode ? streakItems : Array(streakItems.prefix(activeStreakItemsCount))
    }

    func createEntryForMetricInfo() -> Entry {
        let bmiStr = metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
        let bodyFatStr = metrics.first(where: { $0.label == DashboardStrings.bodyFat })?.value
        let muscleStr = metrics.first(where: { $0.label == DashboardStrings.muscle })?.value
        let waterStr = metrics.first(where: { $0.label == DashboardStrings.water })?.value

        let bmi = bmiStr.flatMap { Double($0) }.flatMap { Int($0) }
        let bodyFat = bodyFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let muscleMass = muscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let water = waterStr.flatMap { Double($0) }.flatMap { Int($0) }
        let actualWeight = latestWeightStored

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
            weight: actualWeight,
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
            return .weight
        }
    }

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
        
        resetDragState()
    }

    func isStreakRemoved(at index: Int) -> Bool {
        guard index < streakItems.count else { return false }
        return index >= activeStreakItemsCount
    }

    func resetDashboard() {
        isLoading = true
        loaderOverride = LoaderModel(text: lang.saving)
        
        selectedMetricLabel = nil
        isEditMode = false
        resetDragState()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.restoreOriginalMetricOrder()
                self.restoreOriginalStreakOrder()
                self.activeMetricsCount = self.originalMetrics.count
                self.activeStreakItemsCount = self.originalStreakItems.count
                self.isGoalCardRemoved = false
                
                self.selectedMetricLabel = nil
                self.isEditMode = false
                self.resetDragState()
                
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
        
        selectedMetricLabel = nil
        resetDragState()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.isEditMode = false
                self.resetDragState()
            }
        }
    }

    private func restoreOriginalMetricOrder() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }
    private func restoreOriginalStreakOrder() {
        streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }

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

    func selectMetric(_ label: String) {
        if selectedMetricLabel == label {
            selectedMetricLabel = nil
        } else {
            selectedMetricLabel = label
        }
    }

    func resetDragState() {
        draggingMetric = nil
        draggingStreak = nil
        dropHoverId = nil
        
        gridLayoutId = UUID()
    }

    var isAnyItemBeingDragged: Bool {
        draggingMetric != nil || draggingStreak != nil
    }

    @MainActor
    var allContentRemoved: Bool {
        metricsToShow.isEmpty && (!isEditMode && isGoalCardRemoved) && (!shouldShowStreakGrid)
    }

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
                let latestEntry = try await self.entryService.getLatestEntry()
                var currentWeightStored: Int = 0
                if let latestWeight = latestEntry?.scaleEntry?.weight {
                    currentWeightStored = latestWeight
                }
                
                guard let account = self.accountService.activeAccount,
                      let goalSettings = account.goalSettings else { 
                    logger.log(level: .error, tag: "DashboardStore", message: "No account or goal settings found")
                    return 
                }
                
                self.goalType = goalSettings.goalType ?? .gain
                self.goalUnit = account.weightSettings?.weightUnit ?? .lb
                
                let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
                self.goalStartWeight = self.displayWeight(fromStored: initialWeightStored)
                if let goalW = goalSettings.goalWeight {
                    self.goalWeight = self.displayWeight(fromStored: Int(goalW))
                }
                let currentWeight = self.displayWeight(fromStored: currentWeightStored)
                self.goalDelta = currentWeight - self.goalStartWeight

                let totalDistance = abs(self.goalWeight - self.goalStartWeight)
                let achievedDistance = abs(currentWeight - self.goalStartWeight)
                
                if totalDistance > 0 {
                    let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                    self.goalProgress = progress
                } else {
                    self.goalProgress = 1.0
                }
                
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Error loading goal card data: \(error)")
            }
        }
    }
}
