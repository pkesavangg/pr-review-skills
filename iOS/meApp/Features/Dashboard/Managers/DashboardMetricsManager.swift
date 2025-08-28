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
          
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Loading metrics from API - Current state: \(state.metrics.count) metrics, active: \(state.activeMetricsCount)")
            
            // Fully rely on dashboardType parameter from active account
            if let dashboardTypeString = account.dashboardSettings?.dashboardType,
               let dashboardType = DashboardType(rawValue: dashboardTypeString) {
                updateDashboardType(dashboardType)
                logger.log(level: .info, tag: "DashboardMetricsManager", message: "Loaded dashboard type from API: \(dashboardType.rawValue)")
            } else {
                // If no dashboardType is set, use default dashboard12
                updateDashboardType(.dashboard12)
            }
            
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
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Updated dashboard type to \(dashboardType.rawValue) with activeMetricsCount: \(state.activeMetricsCount)")
    }

    // MARK: - Metric Management
    func resetMetricsToDefaults() async throws {
        // Instead of just setting up initial metrics, reload from API to restore original order
        try await loadMetricsFromAPI()
        
        // Ensure all metrics are active after reset
        resetActiveMetricsCountToShowAll()
        
        // Clear any removal state to ensure all metrics are visible
        state.removedMetrics.removeAll()
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reset metrics to defaults by reloading from API and restoring all metrics")
    }
    
    /// Resets the active metrics count to show all metrics (useful for dashboard reset)
    /// For dashboard type 12: shows all 12 metrics
    /// For dashboard type 4: shows all configured metrics
    func resetActiveMetricsCountToShowAll() {
        if state.dashboardType == .dashboard12 {
            // For dashboard 12, show all 12 metrics
            state.activeMetricsCount = state.metrics.count
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reset active metrics count for dashboard 12: all \(state.activeMetricsCount) metrics are now active")
        } else {
            // For dashboard 4, all stored metrics are already active
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reset active metrics count called for dashboard 4 - all \(state.metrics.count) metrics are already active")
        }
    }
    
    /// Returns the set of removed metric labels based on the current state
    /// For dashboard type 12: returns labels of metrics beyond activeMetricsCount (inactive ones)
    /// For dashboard type 4: returns empty set since all stored metrics are active
    func getRemovedMetricLabels() -> Set<String> {
        if state.dashboardType == .dashboard12 {
            let removedMetrics = Array(state.metrics.dropFirst(state.activeMetricsCount))
            let removedLabels = Set(removedMetrics.map { $0.label })
            
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Dashboard 12: \(removedLabels.count) inactive metrics out of \(state.metrics.count) total")
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
                logger.log(level: .info, tag: "DashboardMetricsManager", message: "Added metric back: \(metric.label), activeMetricsCount: \(state.activeMetricsCount)")
            } else {
                // Removing the metric - move to end (inactive section)
                state.metrics.append(metric)
                state.activeMetricsCount -= 1
                logger.log(level: .info, tag: "DashboardMetricsManager", message: "Removed metric: \(metric.label), activeMetricsCount: \(state.activeMetricsCount)")
            }
        } else {
            // For dashboard 4, all metrics are always active
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Toggle metric visibility called but not needed for dashboard 4 - all \(state.metrics.count) metrics are always active")
        }
    }

    func reorderMetrics(from source: IndexSet, to destination: Int) async throws {
        state.metrics.move(fromOffsets: source, toOffset: destination)
        
        if state.dashboardType == .dashboard12 {
            // For dashboard 12, ensure activeMetricsCount remains correct after reordering
            // This prevents active and inactive metrics from getting mixed up
            let currentActiveCount = min(state.activeMetricsCount, state.metrics.count)
            state.activeMetricsCount = currentActiveCount
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reordered metrics for dashboard 12, activeMetricsCount: \(state.activeMetricsCount)")
        } else {
            // For dashboard 4, activeMetricsCount remains the same since all metrics are active
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reordered metrics for dashboard 4, activeMetricsCount: \(state.activeMetricsCount)")
        }
    }

    // MARK: - Utility Methods
    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double? {
        switch label {
        case DashboardStrings.bmi: return summary.bmi
        case DashboardStrings.bodyFat: return summary.bodyFat
        case DashboardStrings.muscle: return summary.muscleMass
        case DashboardStrings.water: return summary.water
        case DashboardStrings.heartBpm: return summary.pulse.map { Double($0) }
        case DashboardStrings.bone: return summary.boneMass
        case DashboardStrings.visceralFat: return summary.visceralFatLevel
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
            return state.metrics
        } else {
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
        clearFallbackCache()
        let fallbackValues = await getHistoricalFallbackValues()
        if let bmi = entry.scaleEntry?.bmi {
            let formattedValue = BodyMetricsConvertor.convert(Double(bmi), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }

        if let bodyFat = entry.scaleEntry?.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(Double(bodyFat), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }

        if let muscleMass = entry.scaleEntry?.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(muscleMass), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }

        if let water = entry.scaleEntry?.water {
            let formattedValue = BodyMetricsConvertor.convert(Double(water), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }

        if let pulse = entry.scaleEntryMetric?.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }

        if let boneMass = entry.scaleEntryMetric?.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(boneMass), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }

        if let visceralFat = entry.scaleEntryMetric?.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(Double(visceralFat), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }

        if let subFat = entry.scaleEntryMetric?.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(subFat), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }

        if let protein = entry.scaleEntryMetric?.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(protein), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }

        if let skelMuscle = entry.scaleEntryMetric?.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(skelMuscle), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }

        if let bmr = entry.scaleEntryMetric?.bmr {
            let bmrValue = Double(bmr) / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }

        if let metabolicAge = entry.scaleEntryMetric?.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        } else {
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }

    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws {
        clearFallbackCache()
        let fallbackValues = await getHistoricalFallbackValues()
        if let bmi = selectedPoint.bmi {
            let formattedValue = BodyMetricsConvertor.convert(bmi, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bmi)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }
        if let bodyFat = selectedPoint.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(bodyFat, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bodyFat)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }
        if let muscleMass = selectedPoint.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(muscleMass, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.muscleMass)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }
        if let water = selectedPoint.water {
            let formattedValue = BodyMetricsConvertor.convert(water, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.water)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }
        if let pulse = selectedPoint.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.pulse)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }
        if let boneMass = selectedPoint.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(boneMass, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.boneMass)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }
        if let visceralFat = selectedPoint.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(visceralFat, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.visceralFat)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }
        if let subFat = selectedPoint.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(subFat, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.subFat)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }
        if let protein = selectedPoint.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(protein, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.protein)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }
        if let skelMuscle = selectedPoint.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(skelMuscle, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.skelMuscle)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }
        if let bmr = selectedPoint.bmr {
            let bmrValue = Double(bmr) / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.bmr)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }
        if let metabolicAge = selectedPoint.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.metabolicAge)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Updated metrics with selected point data")
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
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Saved metrics to API: \(apiMetrics)")
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
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Handled metric long press for: \(metricLabel)")
    }
    
    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async {
        guard let label = newValue else { return }
        let entry = await createEntryForMetricInfo(metricLabel: label)
        await MainActor.run {
            selectedEntry.wrappedValue = entry
            selectedMetric.wrappedValue = getBodyMetric(for: label)
        }
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Handled selected metric info change for: \(label)")
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
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Handled metric long press with UI state for: \(metricLabel)")
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
            let bmi = findLatestValidValue(for: { $0.scaleEntry?.bmi }, in: allEntries)
            let bodyFat = findLatestValidValue(for: { $0.scaleEntry?.bodyFat }, in: allEntries)
            let muscleMass = findLatestValidValue(for: { $0.scaleEntry?.muscleMass }, in: allEntries)
            let water = findLatestValidValue(for: { $0.scaleEntry?.water }, in: allEntries)
            let pulse = findLatestValidValue(for: { $0.scaleEntryMetric?.pulse }, in: allEntries)
            let boneMass = findLatestValidValue(for: { $0.scaleEntryMetric?.boneMass }, in: allEntries)
            let visceralFat = findLatestValidValue(for: { $0.scaleEntryMetric?.visceralFatLevel }, in: allEntries)
            let subFat = findLatestValidValue(for: { $0.scaleEntryMetric?.subcutaneousFatPercent }, in: allEntries)
            let protein = findLatestValidValue(for: { $0.scaleEntryMetric?.proteinPercent }, in: allEntries)
            let skelMuscle = findLatestValidValue(for: { $0.scaleEntryMetric?.skeletalMusclePercent }, in: allEntries)
            let bmr = findLatestValidValue(for: { $0.scaleEntryMetric?.bmr }, in: allEntries).map { Double($0) / 10.0 }
            let metabolicAge = findLatestValidValue(for: { $0.scaleEntryMetric?.metabolicAge }, in: allEntries)
            let fallbackValues = FallbackValues(
                bmi: bmi,
                bodyFat: bodyFat,
                muscleMass: muscleMass,
                water: water,
                pulse: pulse,
                boneMass: boneMass,
                visceralFat: visceralFat,
                subFat: subFat,
                protein: protein,
                skelMuscle: skelMuscle,
                bmr: bmr,
                metabolicAge: metabolicAge
            )
            cachedFallbackValues = fallbackValues
            lastFallbackCacheTime = Date()
            return fallbackValues
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
    
    private func findLatestValidValue<T: Comparable>(for metricExtractor: (Entry) -> T?, in entries: [Entry]) -> Double? {
        let sortedEntries = entries.sorted { $0.entryTimestamp > $1.entryTimestamp }
        for entry in sortedEntries {
            if let value = metricExtractor(entry) {
                let doubleValue = Double("\(value)") ?? 0.0
                if isValidMetricValue(doubleValue) {
                    return doubleValue
                }
            }
        }
        return nil
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
