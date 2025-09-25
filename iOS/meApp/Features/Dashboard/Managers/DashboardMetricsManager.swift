import Foundation
import SwiftUI

/// Manages all metric operations and configurations for the dashboard
@MainActor
class DashboardMetricsManager: ObservableObject, DashboardMetricsManaging {

    // MARK: - Dependencies
    @Injector private var accountService: AccountService
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: MetricsState

    // MARK: - Private Properties
    private let originalMetrics: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        (DashboardStrings.placeholder, DashboardStrings.bmi, nil, nil, AppAssets.bmiIcon),
        (DashboardStrings.placeholder, DashboardStrings.bodyFat, DashboardStrings.bodyFatUnit, nil, AppAssets.bodyFatIcon),
        (DashboardStrings.placeholder, DashboardStrings.muscle, DashboardStrings.muscleUnit, nil, AppAssets.muscleIcon),
        (DashboardStrings.placeholder, DashboardStrings.water, DashboardStrings.waterUnit, nil, AppAssets.waterIcon),
        (DashboardStrings.placeholder, DashboardStrings.heartBpm, DashboardStrings.heartBpmUnit, nil, AppAssets.heartIcon),
        (DashboardStrings.placeholder, DashboardStrings.bone, DashboardStrings.boneUnit, nil, AppAssets.boneIcon),
        (DashboardStrings.placeholder, DashboardStrings.visceralFat, nil, DashboardStrings.visceralFatPre, AppAssets.visceralFatIcon),
        (DashboardStrings.placeholder, DashboardStrings.subFat, DashboardStrings.subFatUnit, nil, AppAssets.subcutaneousFatIcon),
        (DashboardStrings.placeholder, DashboardStrings.protein, DashboardStrings.proteinUnit, nil, AppAssets.proteinIcon),
        (DashboardStrings.placeholder, DashboardStrings.skelMuscle, DashboardStrings.skelMuscleUnit, nil, AppAssets.skeletalMuscleIcon),
        (DashboardStrings.placeholder, DashboardStrings.bmrKcal, DashboardStrings.bmrKcalUnit, nil, AppAssets.bmrIcon),
        (DashboardStrings.placeholder, DashboardStrings.metAge, DashboardStrings.metAgeUnit, nil, AppAssets.ageIcon)
    ]

    // MARK: - Fallback Values Structure
    private struct FallbackValues {
        let bmi: Double?
        let bodyFat: Double?
        let muscleMass: Double?
        let water: Double?
        let pulse: Double?
        let boneMass: Double?
        let visceralFat: Double?
        let subFat: Double?
        let protein: Double?
        let skelMuscle: Double?
        let bmr: Double?
        let metabolicAge: Double?
    }

    // MARK: - Initialization
    init(initialState: MetricsState = MetricsState()) {
        self.state = initialState
        setupInitialMetrics()
    }

    // MARK: - Setup Methods
    private func setupInitialMetrics() {
        if state.dashboardType == .dashboard12 {
      
            state.metrics = originalMetrics.map {
                MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon)
            }
            state.activeMetricsCount = state.metrics.count
        } else {
            let basicMetrics = Array(originalMetrics.prefix(4))
            state.metrics = basicMetrics.map {
                MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon)
            }
            state.activeMetricsCount = state.metrics.count
        }
    }

    func resetOrderToDefault() {

        let desiredOrderLabels: [String]
        if state.dashboardType == .dashboard12 {
            desiredOrderLabels = originalMetrics.map { $0.label }
        } else {
            desiredOrderLabels = Array(originalMetrics.prefix(4)).map { $0.label }
        }

        let existingByLabel: [String: MetricItem] = Dictionary(
            uniqueKeysWithValues: state.metrics.map { ($0.label, $0) }
        )

        var newMetrics: [MetricItem] = []
        for label in desiredOrderLabels {
            if let existing = existingByLabel[label] {
                newMetrics.append(existing)
            } else if let original = originalMetrics.first(where: { $0.label == label }) {
                // Fallback to original placeholder item if not present
                newMetrics.append(MetricItem(value: original.value, label: original.label, unit: original.unit, preLabel: original.preLabel, icon: original.icon))
            }
        }

        let remaining = state.metrics.filter { !desiredOrderLabels.contains($0.label) }
        newMetrics.append(contentsOf: remaining)

        state.metrics = newMetrics
        state.activeMetricsCount = newMetrics.count
    }

    // MARK: - API Integration
    func loadMetricsFromAPI() async throws {
        do {
            guard let account = accountService.activeAccount else {
                throw DashboardError.noActiveAccount
            }
          
            
            // Fully rely on dashboardType parameter from active account
            let dashboardTypeString = account.dashboardSettings?.dashboardType
            let dashboardType: DashboardType
            switch dashboardTypeString {
            case "dashboard4":
                dashboardType = .dashboard4
            case "dashboard12":
                dashboardType = .dashboard12
            default:
                dashboardType = .dashboard12
            }
            updateDashboardType(dashboardType)
            
            if let dashboardMetrics = account.dashboardSettings?.dashboardMetrics {
                let metricArray = dashboardMetrics.split(separator: ",").map(String.init)
                updateMetricsOrder(from: metricArray)

            } else {
                setupInitialMetrics()
            }
        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to load metrics from API: \(error)")
            throw DashboardError.configurationLoadFailed(error)
        }
    }

    // MARK: - Dashboard Type Management
    func updateDashboardType(_ dashboardType: DashboardType) {
        state.dashboardType = dashboardType
        switch dashboardType {
        case .dashboard4:
            state.activeMetricsCount = 4
        case .dashboard12:
            state.activeMetricsCount = 12
        }
    }

    // MARK: - Metric Management
    func resetMetricsToDefaults() async throws {
        // Instead of just setting up initial metrics, reload from API to restore original order
        try await loadMetricsFromAPI()
        
        // Ensure all metrics are active after reset
        resetActiveMetricsCountToShowAll()
        
        // Clear any removal state to ensure all metrics are visible
        state.removedMetrics.removeAll()
        
    }
    
    /// Resets the active metrics count to show all metrics (useful for dashboard reset)
    /// For dashboard type 12: shows all 12 metrics
    /// For dashboard type 4: shows all configured metrics
    func resetActiveMetricsCountToShowAll() {
        if state.dashboardType == .dashboard12 {
            // For dashboard 12, show all 12 metrics
            state.activeMetricsCount = state.metrics.count
        } else {
            // For dashboard 4, all stored metrics are already active
        }
    }
    
    /// Returns the set of removed metric labels based on the current state
    /// For dashboard type 12: returns labels of metrics beyond activeMetricsCount (inactive ones)
    /// For dashboard type 4: returns empty set since all stored metrics are active
    func getRemovedMetricLabels() -> Set<String> {
        if state.dashboardType == .dashboard12 {
            let removedMetrics = Array(state.metrics.dropFirst(state.activeMetricsCount))
            let removedLabels = Set(removedMetrics.map { $0.label })
            
            return removedLabels
        } else {
            return []
        }
    }

    func toggleMetricVisibility(at index: Int) async throws {
        guard index < state.metrics.count else {
            throw DashboardError.invalidMetricData("Invalid metric index: \(index)")
        }
        
        if state.dashboardType == .dashboard12 {
            // For dashboard 12, allow toggling between active and inactive
            let metric = state.metrics[index]
            let isCurrentlyRemoved = index >= state.activeMetricsCount
            
            state.metrics.remove(at: index)
            if isCurrentlyRemoved {
                // Adding the metric back - insert at the end of active metrics
                state.metrics.insert(metric, at: state.activeMetricsCount)
                state.activeMetricsCount += 1
            } else {
                // Removing the metric - move to end (inactive section)
                state.metrics.append(metric)
                state.activeMetricsCount -= 1
            }
        } else {
            // For dashboard 4, all metrics are always active
        }
    }

    func reorderMetrics(from source: IndexSet, to destination: Int) async throws {
        state.metrics.move(fromOffsets: source, toOffset: destination)
        
        if state.dashboardType == .dashboard12 {
            // For dashboard 12, ensure activeMetricsCount remains correct after reordering
            // This prevents active and inactive metrics from getting mixed up
            let currentActiveCount = min(state.activeMetricsCount, state.metrics.count)
            state.activeMetricsCount = currentActiveCount
        } else {
            // For dashboard 4, activeMetricsCount remains the same since all metrics are active
        }
    }

    // MARK: - Utility Methods
    /// Sets placeholder values for all body metrics except weight (used when no exact data point is selected on the chart)
    func setPlaceholdersForAllMetrics() {
        let placeholder = DashboardStrings.placeholder
        state.metrics = state.metrics.enumerated().map { index, item in
            // Only set placeholder for body metrics, not weight
            let bodyMetric = getBodyMetric(for: item.label)
            if bodyMetric == .weight {
                // Keep weight value as is - don't set placeholder
                return item
            } else {
                // Set placeholder for all body metrics
                return MetricItem(
                    value: placeholder,
                    label: item.label,
                    unit: item.unit,
                    preLabel: item.preLabel,
                    icon: item.icon
                )
            }
        }
        logger.log(level: .debug, tag: "DashboardMetricsManager", message: "Set placeholders for body metrics (excluding weight) due to non-exact point selection")
    }
    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double? {
        switch label {
        case DashboardStrings.bmi: return summary.bmi
        case DashboardStrings.bodyFat: return summary.bodyFat
        case DashboardStrings.muscle: return summary.muscleMass
        case DashboardStrings.water: return summary.water
        case DashboardStrings.heartBpm: return summary.pulse.map { Double($0) }
        case DashboardStrings.bone: return summary.boneMass
        case DashboardStrings.visceralFat: return summary.visceralFatLevel.map { $0 / 10.0 }
        case DashboardStrings.subFat: return summary.subcutaneousFatPercent
        case DashboardStrings.protein: return summary.proteinPercent
        case DashboardStrings.skelMuscle: return summary.skeletalMusclePercent
        case DashboardStrings.bmrKcal: return summary.bmr.map { Double($0) / 10.0 }
        case DashboardStrings.metAge: return summary.metabolicAge.map { Double($0) }
        default: return nil
        }
    }

    func getMetricsToShow(isEditMode: Bool, dashboardType: DashboardType, removedMetrics: Set<String> = []) -> [MetricItem] {
        let allowedFour: Set<String> = [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.muscle,
            DashboardStrings.water
        ]

        if isEditMode {
            // In edit mode, show all metrics (both removed and non-removed) so users can manage them
            // Non-removed metrics first, then removed metrics
            let nonRemovedMetrics = state.metrics.filter { !removedMetrics.contains($0.label) }
            let removedMetricsArray = state.metrics.filter { removedMetrics.contains($0.label) }
            return nonRemovedMetrics + removedMetricsArray
        } else {
            // In non-edit mode, only show non-removed metrics
            let allUnremovedMetrics = state.metrics.filter { !removedMetrics.contains($0.label) }
            
            switch dashboardType {
            case .dashboard4:
                let result = Array(allUnremovedMetrics.filter { allowedFour.contains($0.label) }.prefix(4))
                return result
            case .dashboard12:
                let result = Array(allUnremovedMetrics.prefix(12))
                return result
            }
        }
    }

    func getMetricGridColumns(for dashboardType: DashboardType) -> [GridItem] {
        let columnCount = getMetricGridColumnCount(for: dashboardType)
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
    }

    /// Returns the number of columns for the metric grid based on the real device and dashboard type.
    /// - Uses `DevicePlatform.isTablet` (UIDevice.current.model) to detect iPad.
    /// - iPad: always 4 columns.
    /// - iPhone: 2 columns for 4-metric, 3 columns for 12-metric.
   func getMetricGridColumnCount(for dashboardType: DashboardType) -> Int {
        if DevicePlatform.isTablet { return 4 }
        switch dashboardType {
        case .dashboard4: return 2
        case .dashboard12: return 3
        }
    }

    // MARK: - Metric Updates
    func updateMetrics(with entry: Entry) async throws {
        // Only compute fallbacks if we actually need any
        let needsFallback = (entry.scaleEntry?.bmi == nil) ||
                            (entry.scaleEntry?.bodyFat == nil) ||
                            (entry.scaleEntry?.muscleMass == nil) ||
                            (entry.scaleEntry?.water == nil) ||
                            (entry.scaleEntryMetric?.pulse == nil) ||
                            (entry.scaleEntryMetric?.boneMass == nil) ||
                            (entry.scaleEntryMetric?.visceralFatLevel == nil) ||
                            (entry.scaleEntryMetric?.subcutaneousFatPercent == nil) ||
                            (entry.scaleEntryMetric?.proteinPercent == nil) ||
                            (entry.scaleEntryMetric?.skeletalMusclePercent == nil) ||
                            (entry.scaleEntryMetric?.bmr == nil) ||
                            (entry.scaleEntryMetric?.metabolicAge == nil)

        let fallbackValues: FallbackValues?
        if needsFallback {
            if let cached = cachedFallbackValues {
                fallbackValues = cached
            } else {
                fallbackValues = await getHistoricalFallbackValues()
            }
        } else {
            fallbackValues = cachedFallbackValues
        }
        if let bmi = entry.scaleEntry?.bmi {
            let formattedValue = BodyMetricsConvertor.convert(Double(bmi), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }

        if let bodyFat = entry.scaleEntry?.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(Double(bodyFat), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }

        if let muscleMass = entry.scaleEntry?.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(muscleMass), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }

        if let water = entry.scaleEntry?.water {
            let formattedValue = BodyMetricsConvertor.convert(Double(water), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }

        if let pulse = entry.scaleEntryMetric?.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }

        if let boneMass = entry.scaleEntryMetric?.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(boneMass), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }

        if let visceralFat = entry.scaleEntryMetric?.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(Double(visceralFat) / 10.0, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }

        if let subFat = entry.scaleEntryMetric?.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(subFat), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }

        if let protein = entry.scaleEntryMetric?.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(protein), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }

        if let skelMuscle = entry.scaleEntryMetric?.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(skelMuscle), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }

        if let bmr = entry.scaleEntryMetric?.bmr {
            let bmrValue = Double(bmr) / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }

        if let metabolicAge = entry.scaleEntryMetric?.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }

    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws {
        // Only compute fallbacks if we actually need any
        let needsFallback = (selectedPoint.bmi == nil) ||
                            (selectedPoint.bodyFat == nil) ||
                            (selectedPoint.muscleMass == nil) ||
                            (selectedPoint.water == nil) ||
                            (selectedPoint.pulse == nil) ||
                            (selectedPoint.boneMass == nil) ||
                            (selectedPoint.visceralFatLevel == nil) ||
                            (selectedPoint.subcutaneousFatPercent == nil) ||
                            (selectedPoint.proteinPercent == nil) ||
                            (selectedPoint.skeletalMusclePercent == nil) ||
                            (selectedPoint.bmr == nil) ||
                            (selectedPoint.metabolicAge == nil)

        let fallbackValues: FallbackValues? = nil
        
        if let bmi = selectedPoint.bmi {
            let formattedValue = BodyMetricsConvertor.convert(bmi, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }
        
        if let bodyFat = selectedPoint.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(bodyFat, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }
        
        if let muscleMass = selectedPoint.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(muscleMass, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }
        
        if let water = selectedPoint.water {
            let formattedValue = BodyMetricsConvertor.convert(water, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }
        
        if let pulse = selectedPoint.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }
        
        if let boneMass = selectedPoint.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(boneMass, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }
        
        if let visceralFat = selectedPoint.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(visceralFat / 10.0, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }
        
        if let subFat = selectedPoint.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(subFat, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }
        
        if let protein = selectedPoint.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(protein, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }
        
        if let skelMuscle = selectedPoint.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(skelMuscle, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues?.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }
        
        if let bmr = selectedPoint.bmr {
            let bmrValue = Double(bmr) / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }
        
        if let metabolicAge = selectedPoint.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues?.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }

    // MARK: - API Operations
    func saveMetricsToAPI(removedMetrics: Set<String> = []) async throws {
        do {
            guard accountService.activeAccount != nil else {
                throw DashboardError.noActiveAccount
            }
            
            // Get all metrics that are NOT removed and are within the active count
            let visibleMetrics = state.metrics
                .enumerated()
                .filter { index, metric in
                    // Include metric if it's not removed and within active count
                    !removedMetrics.contains(metric.label) && index < state.activeMetricsCount
                }
                .map { $0.element.label }
            
            let apiMetrics = visibleMetrics.compactMap { displayLabel -> String? in
                switch displayLabel {
                case DashboardStrings.bmi: return "bmi"
                case DashboardStrings.bodyFat: return "bodyFat"
                case DashboardStrings.muscle: return "muscleMass"
                case DashboardStrings.water: return "water"
                case DashboardStrings.heartBpm: return "pulse"
                case DashboardStrings.bone: return "boneMass"
                case DashboardStrings.visceralFat: return "visceralFatLevel"
                case DashboardStrings.subFat: return "subcutaneousFatPercent"
                case DashboardStrings.protein: return "proteinPercent"
                case DashboardStrings.skelMuscle: return "skeletalMusclePercent"
                case DashboardStrings.bmrKcal: return "bmr"
                case DashboardStrings.metAge: return "metabolicAge"
                default: return nil
                }
            }
            
            _ = try await accountService.updateDashboardMetrics(metrics: apiMetrics)
        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to save metrics to API: \(error)")
            throw DashboardError.metricsSaveFailed(error)
        }
    }

    func createEntryForMetricInfo(metricLabel: String? = nil) async -> Entry {
        let latestEntry = try? await entryService.getLatestEntry()
        if let latestEntry = latestEntry {
            return latestEntry
        } else {
            return createEntryForMetricInfoSync(metricLabel: metricLabel)
        }
    }

    func createEntryForMetricInfoSync(metricLabel: String? = nil, weight: Int = 0) -> Entry {
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )
        let bmiMetric = state.metrics.first(where: { $0.label == DashboardStrings.bmi })
        let bodyFatMetric = state.metrics.first(where: { $0.label == DashboardStrings.bodyFat })
        let muscleMetric = state.metrics.first(where: { $0.label == DashboardStrings.muscle })
        let waterMetric = state.metrics.first(where: { $0.label == DashboardStrings.water })
        let heartBpmMetric = state.metrics.first(where: { $0.label == DashboardStrings.heartBpm })
        let boneMetric = state.metrics.first(where: { $0.label == DashboardStrings.bone })
        let visceralFatMetric = state.metrics.first(where: { $0.label == DashboardStrings.visceralFat })
        let subFatMetric = state.metrics.first(where: { $0.label == DashboardStrings.subFat })
        let proteinMetric = state.metrics.first(where: { $0.label == DashboardStrings.protein })
        let skelMuscleMetric = state.metrics.first(where: { $0.label == DashboardStrings.skelMuscle })
        let bmrMetric = state.metrics.first(where: { $0.label == DashboardStrings.bmrKcal })
        let metAgeMetric = state.metrics.first(where: { $0.label == DashboardStrings.metAge })

        let bmiValue = Double(bmiMetric?.value ?? "").flatMap { Int($0) }
        let bodyFatValue = Double(bodyFatMetric?.value ?? "").flatMap { Int($0) }
        let muscleValue = Double(muscleMetric?.value ?? "").flatMap { Int($0) }
        let waterValue = Double(waterMetric?.value ?? "").flatMap { Int($0) }
        let heartBpmValue = Double(heartBpmMetric?.value ?? "").flatMap { Int($0) }
        let boneValue = Double(boneMetric?.value ?? "").flatMap { Int($0) }
        let visceralFatValue = Double(visceralFatMetric?.value ?? "").flatMap { Int($0) }
        let subFatValue = Double(subFatMetric?.value ?? "").flatMap { Int($0) }
        let proteinValue = Double(proteinMetric?.value ?? "").flatMap { Int($0) }
        let skelMuscleValue = Double(skelMuscleMetric?.value ?? "").flatMap { Int($0) }
        let bmrValue = Double(bmrMetric?.value ?? "").flatMap { Int($0) }
        let metAgeValue = Double(metAgeMetric?.value ?? "").flatMap { Int($0) }

        let latestWeight = weight
        
        entry.scaleEntry = BathScaleEntry(
            weight: latestWeight,
            bodyFat: bodyFatValue,
            muscleMass: muscleValue,
            water: waterValue,
            bmi: bmiValue,
            source: "dashboard"
        )

        entry.scaleEntryMetric = BathScaleMetric(
            bmr: bmrValue,
            metabolicAge: metAgeValue,
            proteinPercent: proteinValue,
            pulse: heartBpmValue,
            skeletalMusclePercent: skelMuscleValue,
            subcutaneousFatPercent: subFatValue,
            visceralFatLevel: visceralFatValue,
            boneMass: boneValue,
            impedance: nil,
            unit: nil
        )

        return entry
    }

    func getBodyMetric(for metricLabel: String) -> BodyMetric {
        switch metricLabel {
        case DashboardStrings.bmi: return .bmi
        case DashboardStrings.bodyFat: return .bodyFat
        case DashboardStrings.muscle: return .muscleMass
        case DashboardStrings.water: return .water
        case DashboardStrings.heartBpm: return .pulse
        case DashboardStrings.bone: return .boneMass
        case DashboardStrings.visceralFat: return .visceralFatLevel
        case DashboardStrings.subFat: return .subcutaneousFatPercent
        case DashboardStrings.protein: return .proteinPercent
        case DashboardStrings.skelMuscle: return .skeletalMusclePercent
        case DashboardStrings.bmrKcal: return .bmr
        case DashboardStrings.metAge: return .metabolicAge
        default: return .weight
        }
    }

    // MARK: - Private Methods
    private func updateMetricValue(for label: String, value: String) {
        if let index = state.metrics.firstIndex(where: { $0.label == label }) {
            state.metrics[index] = MetricItem(
                value: value,
                label: state.metrics[index].label,
                unit: state.metrics[index].unit,
                preLabel: state.metrics[index].preLabel,
                icon: state.metrics[index].icon
            )
        }
    }

    private func updateMetricsOrder(from apiMetrics: [String]) {

        let displayMetrics = apiMetrics.compactMap { apiMetric -> String? in
            switch apiMetric {
            case "bmi": return DashboardStrings.bmi
            case "bodyFat": return DashboardStrings.bodyFat
            case "muscleMass": return DashboardStrings.muscle
            case "water": return DashboardStrings.water
            case "pulse": return DashboardStrings.heartBpm
            case "boneMass": return DashboardStrings.bone
            case "visceralFatLevel": return DashboardStrings.visceralFat
            case "subcutaneousFatPercent": return DashboardStrings.subFat
            case "proteinPercent": return DashboardStrings.protein
            case "skeletalMusclePercent": return DashboardStrings.skelMuscle
            case "bmr": return DashboardStrings.bmrKcal
            case "metabolicAge": return DashboardStrings.metAge
            default: return nil
            }
        }

        var activeMetrics: [MetricItem] = []
        for displayMetric in displayMetrics {
            if let originalMetric = originalMetrics.first(where: { $0.label == displayMetric }) {
                let metricItem = MetricItem(
                    value: originalMetric.value,
                    label: originalMetric.label,
                    unit: originalMetric.unit,
                    preLabel: originalMetric.preLabel,
                    icon: originalMetric.icon
                )
                activeMetrics.append(metricItem)
            }
        }
        
        // For dashboard type 12, also include inactive metrics so they can be managed in edit mode
        var inactiveMetrics: [MetricItem] = []
        if state.dashboardType == .dashboard12 {
            // Add all metrics that are not in the API response as inactive
            for originalMetric in originalMetrics {
                if !displayMetrics.contains(originalMetric.label) {
                    let metricItem = MetricItem(
                        value: originalMetric.value,
                        label: originalMetric.label,
                        unit: originalMetric.unit,
                        preLabel: originalMetric.preLabel,
                        icon: originalMetric.icon
                    )
                    inactiveMetrics.append(metricItem)
                }
            }
        }

        state.metrics = activeMetrics + inactiveMetrics

        state.activeMetricsCount = activeMetrics.count

    }

    // MARK: - Metric Interaction Methods
    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) {
        let entry = createEntryForMetricInfoSync(metricLabel: metricLabel)
        selectedEntry.wrappedValue = entry
        selectedMetric.wrappedValue = getBodyMetric(for: metricLabel)
    }
    
    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async {
        guard let label = newValue else { return }
        let entry = await createEntryForMetricInfo(metricLabel: label)
        await MainActor.run {
            selectedEntry.wrappedValue = entry
            selectedMetric.wrappedValue = getBodyMetric(for: label)
        }
    }

    // MARK: - Entry Selection Methods
    func selectEntry(_ entry: BathScaleWeightSummary?, convertWeight: @escaping (Int) -> Double, triggerUpdate: @escaping () -> Void) {
        triggerUpdate()
    }

    func resetMetricsToLatestEntry(getLatestEntry: @escaping () async throws -> Entry?) async {
        do {
            guard let latestEntry = try await getLatestEntry() else {
                return
            }
            try await updateMetrics(with: latestEntry)
        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to reset metrics to latest entry: \(error)")
        }
    }

    func handleMetricLongPressWithUIState(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>, updateSelectedMetric: @escaping (String?) -> Void) {
        updateSelectedMetric(metricLabel)
        let entry = createEntryForMetricInfoSync(metricLabel: metricLabel)
        selectedEntry.wrappedValue = entry
        selectedMetric.wrappedValue = getBodyMetric(for: metricLabel)
    }

    // MARK: - Fallback Values
    private var cachedFallbackValues: FallbackValues?
    private var lastFallbackCacheTime: Date?
    private let fallbackCacheTimeout: TimeInterval = 60
    
    private func getHistoricalFallbackValues() async -> FallbackValues {
        if let cached = cachedFallbackValues,
           let lastCacheTime = lastFallbackCacheTime,
           Date().timeIntervalSince(lastCacheTime) < fallbackCacheTimeout {
            return cached
        }
        do {
            let allEntries = try await entryService.getAllEntries()
            // Offload reduction work to a background task to avoid any main-thread cost
            let computed = await Task.detached(priority: .utility) { () -> FallbackValues in
                @inline(__always) func isValid(_ value: Double) -> Bool {
                    guard !value.isNaN && !value.isInfinite else { return false }
                    if value >= 0 && value <= 100 { return true }
                    if value >= 0 && value <= 300 { return true }
                    if value >= 0 && value <= 50 { return true }
                    if value >= 0 && value <= 10000 { return true }
                    if value >= 0 && value <= 150 { return true }
                    return value >= 0
                }
                func latest(_ extractor: (Entry) -> Any?) -> (Double?, String?) {
                    var bestTs: String? = nil
                    var bestVal: Double? = nil
                    for e in allEntries {
                        guard let raw = extractor(e) else { continue }
                        let d = Double("\(raw)") ?? 0.0
                        if !isValid(d) { continue }
                        let ts = e.entryTimestamp
                        if bestTs == nil || ts > bestTs! { bestTs = ts; bestVal = d }
                    }
                    return (bestVal, bestTs)
                }
                let bmi = latest { $0.scaleEntry?.bmi }.0
                let bodyFat = latest { $0.scaleEntry?.bodyFat }.0
                let muscleMass = latest { $0.scaleEntry?.muscleMass }.0
                let water = latest { $0.scaleEntry?.water }.0
                let pulse = latest { $0.scaleEntryMetric?.pulse }.0
                let boneMass = latest { $0.scaleEntryMetric?.boneMass }.0
                let visceralFat = latest { $0.scaleEntryMetric?.visceralFatLevel }.0
                let subFat = latest { $0.scaleEntryMetric?.subcutaneousFatPercent }.0
                let protein = latest { $0.scaleEntryMetric?.proteinPercent }.0
                let skelMuscle = latest { $0.scaleEntryMetric?.skeletalMusclePercent }.0
                let bmrRaw = latest { $0.scaleEntryMetric?.bmr }.0
                let metabolicAge = latest { $0.scaleEntryMetric?.metabolicAge }.0
                return FallbackValues(
                    bmi: bmi,
                    bodyFat: bodyFat,
                    muscleMass: muscleMass,
                    water: water,
                    pulse: pulse,
                    boneMass: boneMass,
                    visceralFat: visceralFat.map { $0 / 10.0 },
                    subFat: subFat,
                    protein: protein,
                    skelMuscle: skelMuscle,
                    bmr: bmrRaw.map { $0 / 10.0 },
                    metabolicAge: metabolicAge
                )
            }.value
            cachedFallbackValues = computed
            lastFallbackCacheTime = Date()
            return computed
        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to get historical fallback values: \(error)")
            return FallbackValues(
                bmi: nil, bodyFat: nil, muscleMass: nil, water: nil, pulse: nil,
                boneMass: nil, visceralFat: nil, subFat: nil, protein: nil,
                skelMuscle: nil, bmr: nil, metabolicAge: nil
            )
        }
    }
    
    func clearFallbackCache() {
        cachedFallbackValues = nil
        lastFallbackCacheTime = nil
    }
    
    // MARK: - Visible Window Averages
    /// Updates dashboard metric tiles with averages computed from the currently visible operations.
    /// Falls back to latest historical values per metric when the visible window has no data for that metric.
    /// Shows placeholders ("--") when no visible operations are available.
    func updateMetricsForVisibleAverage(visibleOperations: [BathScaleWeightSummary]) async {
        guard !visibleOperations.isEmpty else { 
            // No visible operations - show placeholders for all metrics
            setPlaceholdersForAllMetrics()
            return 
        }

        clearFallbackCache()
        let fallbackValues = await getHistoricalFallbackValues()

        // Helper to compute average for a metric label from summaries
        func averageForMetric(_ label: String) -> Double? {
            let values: [Double] = visibleOperations.compactMap { summary in
                getMetricValue(for: label, from: summary)
            }
            guard !values.isEmpty else { return nil }
            return values.reduce(0, +) / Double(values.count)
        }
        // Define all metrics and their conversion params in an array
        let metrics: [(label: String, shouldCompose: Bool, wholeNumber: Bool, fallbackValue: Double?)] = [
            (DashboardStrings.bmi,           true,  false, fallbackValues.bmi),
            (DashboardStrings.bodyFat,       true,  false, fallbackValues.bodyFat),
            (DashboardStrings.muscle,        true,  false, fallbackValues.muscleMass),
            (DashboardStrings.water,         true,  false, fallbackValues.water),
            (DashboardStrings.heartBpm,      false, true,  fallbackValues.pulse),
            (DashboardStrings.bone,          true,  false, fallbackValues.boneMass),
            (DashboardStrings.visceralFat,   false, true,  fallbackValues.visceralFat),
            (DashboardStrings.subFat,        true,  false, fallbackValues.subFat),
            (DashboardStrings.protein,       true,  false, fallbackValues.protein),
            (DashboardStrings.skelMuscle,    true,  false, fallbackValues.skelMuscle),
            (DashboardStrings.bmrKcal,       false, true,  fallbackValues.bmr),
            (DashboardStrings.metAge,        false, true,  fallbackValues.metabolicAge)
        ]

        for metric in metrics {
            let avg = averageForMetric(metric.label)
            let formatted = BodyMetricsConvertor.convert(
                avg,
                shouldCompose: metric.shouldCompose,
                wholeNumber: metric.wholeNumber,
                fallbackValue: nil
            )
            updateMetricValue(for: metric.label, value: formatted)
        }
    }
    
    private func findLatestValidValue<T: Comparable>(for metricExtractor: (Entry) -> T?, in entries: [Entry]) -> Double? {
        // O(n) pass without sorting; compares ISO timestamps lexicographically
        var bestTs: String? = nil
        var best: Double? = nil
        for e in entries {
            guard let v = metricExtractor(e) else { continue }
            let d = Double("\(v)") ?? 0.0
            guard isValidMetricValue(d) else { continue }
            let ts = e.entryTimestamp
            if bestTs == nil || ts > bestTs! { bestTs = ts; best = d }
        }
        return best
    }
    
    private func isValidMetricValue(_ value: Double) -> Bool {
        guard !value.isNaN && !value.isInfinite else {
            return false
        }
        if value >= 0 && value <= 100 { return true }
        if value >= 0 && value <= 300 { return true }
        if value >= 0 && value <= 50 { return true }
        if value >= 0 && value <= 10000 { return true }
        if value >= 0 && value <= 150 { return true }
        return value >= 0
    }
}
