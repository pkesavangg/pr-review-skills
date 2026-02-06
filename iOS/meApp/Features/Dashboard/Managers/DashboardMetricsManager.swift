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
        (DashboardStrings.placeholder, DashboardStrings.bodyFat, DashboardStrings.percentageUnitSymbol, nil, AppAssets.bodyFatIcon),
        (DashboardStrings.placeholder, DashboardStrings.muscle, DashboardStrings.percentageUnitSymbol, nil, AppAssets.muscleIcon),
        (DashboardStrings.placeholder, DashboardStrings.water, DashboardStrings.percentageUnitSymbol, nil, AppAssets.waterIcon),
        (DashboardStrings.placeholder, DashboardStrings.heartBpm, DashboardStrings.bpmUnitSymbol, nil, AppAssets.heartIcon),
        (DashboardStrings.placeholder, DashboardStrings.bone, DashboardStrings.percentageUnitSymbol, nil, AppAssets.boneIcon),
        (DashboardStrings.placeholder, DashboardStrings.visceralFat, nil, DashboardStrings.visceralFatPre, AppAssets.visceralFatIcon),
        (DashboardStrings.placeholder, DashboardStrings.subFat, DashboardStrings.percentageUnitSymbol, nil, AppAssets.subcutaneousFatIcon),
        (DashboardStrings.placeholder, DashboardStrings.protein, DashboardStrings.percentageUnitSymbol, nil, AppAssets.proteinIcon),
        (DashboardStrings.placeholder, DashboardStrings.skelMuscle, DashboardStrings.percentageUnitSymbol, nil, AppAssets.skeletalMuscleIcon),
        (DashboardStrings.placeholder, DashboardStrings.bmrKcal, DashboardStrings.kcalUnitSymbol, nil, AppAssets.bmrIcon),
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
    init(initialState: MetricsState = MetricsState(), skipInitialSetup: Bool = false) {
        self.state = initialState
        if !skipInitialSetup {
            setupInitialMetrics()
        }
    }

    // MARK: - Setup Methods
    public func setupInitialMetrics(forceShowAll: Bool = false) {
        if state.dashboardType == .dashboard12 || forceShowAll {
            // In dashboard12 mode or if forced (e.g. during R4 setup), show all 12
            state.metrics = originalMetrics.map {
                MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon)
            }
            state.activeMetricsCount = state.metrics.count
        } else {
            // Regular dashboard4
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
        
        // Ensure activeMetricsCount doesn't exceed metrics count
        let maxMetricsCount = newMetrics.count
        switch state.dashboardType {
        case .dashboard4:
            state.activeMetricsCount = min(4, maxMetricsCount)
        case .dashboard12:
            state.activeMetricsCount = min(12, maxMetricsCount)
        }
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
            
            // If metrics exist locally, update from API only when data has changed
           // Preserve local state during in-flow navigation
            if !state.metrics.isEmpty {
                if let dashboardMetrics = account.dashboardSettings?.dashboardMetrics {
                    let apiMetricArray = dashboardMetrics.split(separator: ",").map(String.init)
                    let localMetricLabels = state.metrics.prefix(state.activeMetricsCount).map { $0.label }
                    
                    // Map local labels to API format for comparison
                    let localApiMetrics = localMetricLabels.compactMap { displayLabel -> String? in
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
                    
                    // If API metrics differ from local, update from API (changes were saved)
                    if localApiMetrics != apiMetricArray {
                        logger.log(level: .info, tag: "DashboardMetricsManager", message: "API metrics differ from local, updating from API after save")
                        updateMetricsOrder(from: apiMetricArray)
                        return
                    }
                }
                return
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
        
        // Only set activeMetricsCount if metrics haven't been loaded from API yet (i.e., if the metrics array is empty).
        // Once metrics are loaded from API, activeMetricsCount should come from API data.
        if state.metrics.isEmpty {
            switch dashboardType {
            case .dashboard4:
                state.activeMetricsCount = 4
            case .dashboard12:
                state.activeMetricsCount = 12
            }
        } else {
            // Metrics have been loaded, don't override activeMetricsCount.
            // Just ensure it doesn't exceed the maximum allowed for this dashboard type.
            let maxAllowedForType = dashboardType == .dashboard4 ? 4 : 12
            state.activeMetricsCount = min(state.activeMetricsCount, maxAllowedForType)
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
    func resetActiveMetricsCountToShowAll() {
        state.activeMetricsCount = state.metrics.count
    }
    
    /// Returns the set of removed metric labels based on the current state
    /// Returns labels of metrics beyond activeMetricsCount (inactive ones)
    func getRemovedMetricLabels() -> Set<String> {
        let removedMetrics = Array(state.metrics.dropFirst(state.activeMetricsCount))
        let removedLabels = Set(removedMetrics.map { $0.label })
        return removedLabels
    }
    
    func toggleMetricVisibility(at index: Int) async throws {
        guard index < state.metrics.count else {
            throw DashboardError.invalidMetricData("Invalid metric index: \(index)")
        }
        
        let metric = state.metrics[index]
        let isCurrentlyRemoved = index >= state.activeMetricsCount
        
        state.metrics.remove(at: index)
        
        if isCurrentlyRemoved {
            // Add back to active section
            state.metrics.insert(metric, at: state.activeMetricsCount)
            state.activeMetricsCount += 1
        } else {
            
            state.metrics.append(metric)
            state.activeMetricsCount -= 1
        }
    }
    
    /// Synchronous version for immediate UI updates (used when already on MainActor)
    func toggleMetricVisibilitySync(at index: Int) throws {
        guard index < state.metrics.count else {
            throw DashboardError.invalidMetricData("Invalid metric index: \(index)")
        }
        
        let metric = state.metrics[index]
        let isCurrentlyRemoved = index >= state.activeMetricsCount
        
        state.metrics.remove(at: index)
        
        if isCurrentlyRemoved {
            // Add back to active section
            state.metrics.insert(metric, at: state.activeMetricsCount)
            state.activeMetricsCount += 1
        } else {
            state.metrics.append(metric)
            state.activeMetricsCount -= 1
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
            let effectiveRemoved = removedMetrics.isEmpty ? getRemovedMetricLabels() : removedMetrics
            // In edit mode, show all metrics (both removed and non-removed) so users can manage them
            // Non-removed metrics first, then removed metrics
            let nonRemovedMetrics = state.metrics.filter { !effectiveRemoved.contains($0.label) }
            let removedMetricsArray = state.metrics.filter { effectiveRemoved.contains($0.label) }
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

    // MARK: - Metric Definitions (DRY helpers)
    private struct MetricDefinition {
        let label: String
        let shouldCompose: Bool
        let wholeNumber: Bool
        let valueFromEntry: (Entry) -> Double?
        let valueFromSummary: (BathScaleWeightSummary) -> Double?
    }

    private var metricDefinitions: [MetricDefinition] {
        return [
            MetricDefinition(
                label: DashboardStrings.bmi,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { entry in entry.scaleEntry?.bmi.map { Double($0) } },
                valueFromSummary: { $0.bmi }
            ),
            MetricDefinition(
                label: DashboardStrings.bodyFat,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntry?.bodyFat.map { Double($0) } },
                valueFromSummary: { $0.bodyFat }
            ),
            MetricDefinition(
                label: DashboardStrings.muscle,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntry?.muscleMass.map { Double($0) } },
                valueFromSummary: { $0.muscleMass }
            ),
            MetricDefinition(
                label: DashboardStrings.water,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntry?.water.map { Double($0) } },
                valueFromSummary: { $0.water }
            ),
            MetricDefinition(
                label: DashboardStrings.heartBpm,
                shouldCompose: false,
                wholeNumber: true,
                valueFromEntry: { $0.scaleEntryMetric?.pulse.map { Double($0) } },
                valueFromSummary: { $0.pulse.map { Double($0) } }
            ),
            MetricDefinition(
                label: DashboardStrings.bone,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntryMetric?.boneMass.map { Double($0) } },
                valueFromSummary: { $0.boneMass }
            ),
            MetricDefinition(
                label: DashboardStrings.visceralFat,
                shouldCompose: false,
                wholeNumber: true,
                valueFromEntry: { $0.scaleEntryMetric?.visceralFatLevel.map { Double($0) / 10.0 } },
                valueFromSummary: { $0.visceralFatLevel.map { $0 / 10.0 } }
            ),
            MetricDefinition(
                label: DashboardStrings.subFat,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntryMetric?.subcutaneousFatPercent.map { Double($0) } },
                valueFromSummary: { $0.subcutaneousFatPercent }
            ),
            MetricDefinition(
                label: DashboardStrings.protein,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntryMetric?.proteinPercent.map { Double($0) } },
                valueFromSummary: { $0.proteinPercent }
            ),
            MetricDefinition(
                label: DashboardStrings.skelMuscle,
                shouldCompose: true,
                wholeNumber: false,
                valueFromEntry: { $0.scaleEntryMetric?.skeletalMusclePercent.map { Double($0) } },
                valueFromSummary: { $0.skeletalMusclePercent }
            ),
            MetricDefinition(
                label: DashboardStrings.bmrKcal,
                shouldCompose: false,
                wholeNumber: true,
                valueFromEntry: { $0.scaleEntryMetric?.bmr.map { Double($0) / 10.0 } },
                valueFromSummary: { $0.bmr.map { $0 / 10.0 } }
            ),
            MetricDefinition(
                label: DashboardStrings.metAge,
                shouldCompose: false,
                wholeNumber: true,
                valueFromEntry: { $0.scaleEntryMetric?.metabolicAge.map { Double($0) } },
                valueFromSummary: { $0.metabolicAge.map { Double($0) } }
            )
        ]
    }

    private func fallbackValue(for label: String, from f: FallbackValues?) -> Double? {
        guard let f = f else { return nil }
        switch label {
        case DashboardStrings.bmi: return f.bmi
        case DashboardStrings.bodyFat: return f.bodyFat
        case DashboardStrings.muscle: return f.muscleMass
        case DashboardStrings.water: return f.water
        case DashboardStrings.heartBpm: return f.pulse
        case DashboardStrings.bone: return f.boneMass
        case DashboardStrings.visceralFat: return f.visceralFat
        case DashboardStrings.subFat: return f.subFat
        case DashboardStrings.protein: return f.protein
        case DashboardStrings.skelMuscle: return f.skelMuscle
        case DashboardStrings.bmrKcal: return f.bmr
        case DashboardStrings.metAge: return f.metabolicAge
        default: return nil
        }
    }

    private func updateMetricsUsing(valuesProvider: (MetricDefinition) -> Double?, fallbackProvider: (String) -> Double?) {
        for def in metricDefinitions {
            let val = valuesProvider(def)
            let formatted = BodyMetricsConvertor.convert(
                val,
                shouldCompose: def.shouldCompose,
                wholeNumber: def.wholeNumber,
                fallbackValue: fallbackProvider(def.label)
            )
            updateMetricValue(for: def.label, value: formatted)
        }
    }

    // MARK: - Metric Updates
    func updateMetrics(with entry: Entry) async throws {
        // Compute fallbacks only if any metric is missing
        let anyMissing = metricDefinitions.contains { def in
            def.valueFromEntry(entry) == nil
        }
        let fallbackValues: FallbackValues?
        if anyMissing {
            if let cached = cachedFallbackValues {
                fallbackValues = cached
            } else {
                fallbackValues = await getHistoricalFallbackValues()
            }
        } else {
            fallbackValues = cachedFallbackValues
        }

        updateMetricsUsing(
            valuesProvider: { def in def.valueFromEntry(entry) },
            fallbackProvider: { label in self.fallbackValue(for: label, from: fallbackValues) }
        )
    }

    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws {
        // For exact selection, don't use fallbacks – reflect data as-is
        updateMetricsUsing(
            valuesProvider: { def in def.valueFromSummary(selectedPoint) },
            fallbackProvider: { _ in nil }
        )
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
        // Safely extract weight from latest entry by refetching on main actor if needed
        let latestEntry = try? await entryService.getLatestEntry()
        guard let entryId = latestEntry?.id else {
            return buildEntryFromCurrentMetrics(storedWeight: nil)
        }
        
        // Refetch entry on main actor to safely access SwiftData properties
        let repository = EntryRepository()
        let refetched = try? await repository.refetchEntriesOnMainActor(entryIds: [entryId])
        
        // Access SwiftData properties synchronously on main actor
        let latestWeight: Int? = await MainActor.run {
            return refetched?[entryId]?.scaleEntry?.weight
        }
        
        return buildEntryFromCurrentMetrics(storedWeight: latestWeight)
    }
    
    private func parseDisplayInt(_ displayValue: String?, scaleByTen: Bool = false) -> Int? {
        guard let value = displayValue, value != DashboardStrings.placeholder else { return nil }
        let numericScalars = value.unicodeScalars.filter { CharacterSet.decimalDigits.contains($0) || $0 == "." }
        let numericString = String(String.UnicodeScalarView(numericScalars))
        guard let doubleValue = Double(numericString) else { return nil }
        if doubleValue == 0 { return nil }
        let scaled = scaleByTen ? (doubleValue * 10.0) : doubleValue
        return Int(scaled)
    }

    func createEntryForMetricInfoSync(metricLabel: String? = nil, weight: Int = 0) -> Entry {
        return buildEntryFromCurrentMetrics(storedWeight: weight)
    }

    private func buildEntryFromCurrentMetrics(storedWeight: Int?) -> Entry {
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

        // Convert display values back to stored values
        let bmiValue = parseDisplayInt(bmiMetric?.value)
        let bodyFatValue = parseDisplayInt(bodyFatMetric?.value)
        let muscleValue = parseDisplayInt(muscleMetric?.value)
        let waterValue = parseDisplayInt(waterMetric?.value)
        let heartBpmValue = parseDisplayInt(heartBpmMetric?.value)
        let boneValue = parseDisplayInt(boneMetric?.value)
        let visceralFatValue = parseDisplayInt(visceralFatMetric?.value, scaleByTen: true)
        let subFatValue = parseDisplayInt(subFatMetric?.value)
        let proteinValue = parseDisplayInt(proteinMetric?.value)
        let skelMuscleValue = parseDisplayInt(skelMuscleMetric?.value)
        let bmrValue = parseDisplayInt(bmrMetric?.value, scaleByTen: true)
        let metAgeValue = parseDisplayInt(metAgeMetric?.value)

        entry.scaleEntry = BathScaleEntry(
            weight: storedWeight ?? 0,
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

    func updateMetricsOrder(from apiMetrics: [String]) {

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
        
        // Include inactive metrics so they can be managed in edit mode
        let metricsToCheck = state.dashboardType == .dashboard12
        ? originalMetrics
        : Array(originalMetrics.prefix(4))
        
        let inactiveMetrics: [MetricItem] = metricsToCheck
            .filter { !displayMetrics.contains($0.label) }
            .map {
                MetricItem(
                    value: $0.value,
                    label: $0.label,
                    unit: $0.unit,
                    preLabel: $0.preLabel,
                    icon: $0.icon
                )
            }
        
        state.metrics = activeMetrics + inactiveMetrics
        
        // CRITICAL: Set activeMetricsCount to the number of active metrics from API
        // This ensures removed metrics are correctly identified
        // activeMetrics contains only the metrics that are enabled/visible in the API
        state.activeMetricsCount = activeMetrics.count
        
        logger.log(level: .debug, tag: "DashboardMetricsManager", 
                  message: "Loaded metrics from API: \(activeMetrics.count) active, \(inactiveMetrics.count) inactive, total: \(state.metrics.count)")
        
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
            // Refetch entry on main actor to safely access SwiftData properties
            let entryId = latestEntry.id
            let repository = EntryRepository()
            let refetched = try await repository.refetchEntriesOnMainActor(entryIds: [entryId])
            guard let refetchedEntry = refetched[entryId] else {
                logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to refetch latest entry")
                return
            }
            try await updateMetrics(with: refetchedEntry)
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
            // Extract entry IDs and refetch on main actor to safely access SwiftData properties
            let entryIds = allEntries.map { $0.id }
            let repository = EntryRepository()
            let refetchedEntries = try await repository.refetchEntriesOnMainActor(entryIds: entryIds)
            
            // Extract all SwiftData values on main actor before doing computation
            let extractedData = await MainActor.run {
                refetchedEntries.values.map { entry -> (timestamp: String, values: [String: Double?]) in
                    // Use toOperationDTO() to centralize extraction logic and avoid duplication
                    let dto = entry.toOperationDTO()
                    let values: [String: Double?] = [
                        "bmi": dto.bmi,
                        "bodyFat": dto.bodyFat,
                        "muscleMass": dto.muscleMass,
                        "water": dto.water,
                        "pulse": dto.pulse,
                        "boneMass": dto.boneMass,
                        "visceralFat": dto.visceralFatLevel,
                        "subFat": dto.subcutaneousFatPercent,
                        "protein": dto.proteinPercent,
                        "skelMuscle": dto.skeletalMusclePercent,
                        "bmr": dto.bmr,
                        "metabolicAge": dto.metabolicAge
                    ]
                    return (timestamp: entry.entryTimestamp, values: values)
                }
            }
            
            // Now compute fallback values in background task using extracted plain data
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
                func latest(_ metricKey: String) -> (Double?, String?) {
                    var bestTs: String? = nil
                    var bestVal: Double? = nil
                    for data in extractedData {
                        guard let value = data.values[metricKey] else { continue }
                        guard let doubleValue = value else { continue }
                        if !isValid(doubleValue) { continue }
                        let ts = data.timestamp
                        if bestTs == nil || ts > bestTs! { bestTs = ts; bestVal = doubleValue }
                    }
                    return (bestVal, bestTs)
                }
                let bmi = latest("bmi").0
                let bodyFat = latest("bodyFat").0
                let muscleMass = latest("muscleMass").0
                let water = latest("water").0
                let pulse = latest("pulse").0
                let boneMass = latest("boneMass").0
                let visceralFat = latest("visceralFat").0
                let subFat = latest("subFat").0
                let protein = latest("protein").0
                let skelMuscle = latest("skelMuscle").0
                let bmrRaw = latest("bmr").0
                let metabolicAge = latest("metabolicAge").0
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
            // For all metrics, only include values > 0 (exclude zero values)
            let values: [Double] = visibleOperations.compactMap { summary in
                getMetricValue(for: label, from: summary)
            }.filter { value in
                value > 0
            }
            
            guard !values.isEmpty else { return nil }
            
            // Calculate raw average
            let rawAverage = values.reduce(0, +) / Double(values.count)
            
            // Apply same robust rounding logic as weight calculations
            let roundedAverage = (rawAverage * 100).rounded(.toNearestOrAwayFromZero) / 100
            
            return roundedAverage
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
