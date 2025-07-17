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
        state.metrics = originalMetrics.map {
            MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon)
        }
        
        // Set activeMetricsCount based on dashboard type
        switch state.dashboardType {
        case .dashboard4:
            state.activeMetricsCount = 4
        case .dashboard12:
            state.activeMetricsCount = 12
        }
    }

    // MARK: - API Integration
    func loadMetricsFromAPI() async throws {
        do {
            guard let account = accountService.activeAccount else {
                throw DashboardError.noActiveAccount
            }

            // Load dashboard type from account
            if let dashboardTypeString = account.dashboardSettings?.dashboardType,
               let dashboardType = DashboardType(rawValue: dashboardTypeString) {
                updateDashboardType(dashboardType)
                logger.log(level: .info, tag: "DashboardMetricsManager", message: "Loaded dashboard type from API: \(dashboardType.rawValue)")
            }

            // Load dashboard metrics from account
            if let dashboardMetrics = account.dashboardSettings?.dashboardMetrics {
                let metricArray = dashboardMetrics.split(separator: ",").map(String.init)
                updateMetricsOrder(from: metricArray)
                logger.log(level: .info, tag: "DashboardMetricsManager", message: "Loaded metrics from API: \(metricArray)")
            }

        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to load metrics from API: \(error)")
            throw DashboardError.configurationLoadFailed(error)
        }
    }

    // MARK: - Dashboard Type Management
    
    /// Updates the dashboard type
    func updateDashboardType(_ dashboardType: DashboardType) {
        state.dashboardType = dashboardType
        
        // Set activeMetricsCount based on dashboard type
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
        setupInitialMetrics()
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reset metrics to defaults")
    }

    func toggleMetricVisibility(at index: Int) async throws {
        guard index < state.metrics.count else {
            throw DashboardError.invalidMetricData("Invalid metric index: \(index)")
        }

        let metric = state.metrics[index]
        let isCurrentlyRemoved = index >= state.activeMetricsCount

        state.metrics.remove(at: index)

        if isCurrentlyRemoved {
            // Add the metric back to active metrics
            state.metrics.insert(metric, at: state.activeMetricsCount)
            state.activeMetricsCount += 1
        } else {
            // Move the metric to inactive metrics
            state.metrics.append(metric)
            state.activeMetricsCount -= 1
        }

        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Toggled metric visibility at index: \(index), activeMetricsCount: \(state.activeMetricsCount)")
    }

    func reorderMetrics(from source: IndexSet, to destination: Int) async throws {
        state.metrics.move(fromOffsets: source, toOffset: destination)
        
        // Ensure activeMetricsCount is still valid after reordering
        let currentActiveCount = min(state.activeMetricsCount, state.metrics.count)
        state.activeMetricsCount = currentActiveCount
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reordered metrics from \(source) to \(destination), activeMetricsCount: \(state.activeMetricsCount)")
    }

    // MARK: - Utility Methods
    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double? {
        switch label {
        case DashboardStrings.bmi:
            return summary.bmi
        case DashboardStrings.bodyFat:
            return summary.bodyFat
        case DashboardStrings.muscle:
            return summary.muscleMass
        case DashboardStrings.water:
            return summary.water
        case DashboardStrings.heartBpm:
            return summary.pulse.map { Double($0) }
        case DashboardStrings.bone:
            return summary.boneMass
        case DashboardStrings.visceralFat:
            return summary.visceralFatLevel
        case DashboardStrings.subFat:
            return summary.subcutaneousFatPercent
        case DashboardStrings.protein:
            return summary.proteinPercent
        case DashboardStrings.skelMuscle:
            return summary.skeletalMusclePercent
        case DashboardStrings.bmrKcal:
            return summary.bmr.map { Double($0) / 10.0 }
        case DashboardStrings.metAge:
            return summary.metabolicAge.map { Double($0) }
        default:
            return nil
        }
    }

    func getMetricsToShow(isEditMode: Bool, dashboardType: DashboardType) -> [MetricItem] {
        if isEditMode {
            // In edit mode, show all metrics regardless of type
            return state.metrics
        } else {
            // In normal mode, show only active metrics based on activeMetricsCount
            return Array(state.metrics.prefix(state.activeMetricsCount))
        }
    }

    /// Returns grid columns configuration based on dashboard type
    func getMetricGridColumns(for dashboardType: DashboardType) -> [GridItem] {
        let columnCount = dashboardType == .dashboard4 ? 
            DashboardConstants.UI.fourMetricGridColumns : 
            DashboardConstants.UI.twelveMetricGridColumns
        
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
    }

    // MARK: - Metric Updates
    func updateMetrics(with entry: Entry) async throws {
        print("hello: updateMetrics called with entry")
        
        // Clear fallback cache to ensure fresh data
        clearFallbackCache()
        
        // Get historical fallback values for each metric
        let fallbackValues = await getHistoricalFallbackValues()
        print("hello: fallbackValues - bmi: \(fallbackValues.bmi ?? -999), bodyFat: \(fallbackValues.bodyFat ?? -999), muscleMass: \(fallbackValues.muscleMass ?? -999)")
        
        // Update individual metrics based on entry data with fallback support
        if let bmi = entry.scaleEntry?.bmi {
            print("hello: entry has bmi: \(bmi)")
            let formattedValue = BodyMetricsConvertor.convert(Double(bmi), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bmi)
            print("hello: bmi formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        } else {
            print("hello: entry has NO bmi, using fallback: \(fallbackValues.bmi ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bmi)
            print("hello: bmi fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }

        if let bodyFat = entry.scaleEntry?.bodyFat {
            print("hello: entry has bodyFat: \(bodyFat)")
            let formattedValue = BodyMetricsConvertor.convert(Double(bodyFat), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bodyFat)
            print("hello: bodyFat formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        } else {
            print("hello: entry has NO bodyFat, using fallback: \(fallbackValues.bodyFat ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.bodyFat)
            print("hello: bodyFat fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }

        if let muscleMass = entry.scaleEntry?.muscleMass {
            print("hello: entry has muscleMass: \(muscleMass)")
            let formattedValue = BodyMetricsConvertor.convert(Double(muscleMass), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.muscleMass)
            print("hello: muscleMass formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        } else {
            print("hello: entry has NO muscleMass, using fallback: \(fallbackValues.muscleMass ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.muscleMass)
            print("hello: muscleMass fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }

        if let water = entry.scaleEntry?.water {
            print("hello: entry has water: \(water)")
            let formattedValue = BodyMetricsConvertor.convert(Double(water), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.water)
            print("hello: water formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        } else {
            print("hello: entry has NO water, using fallback: \(fallbackValues.water ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.water)
            print("hello: water fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }

        if let pulse = entry.scaleEntryMetric?.pulse {
            print("hello: entry has pulse: \(pulse)")
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.pulse)
            print("hello: pulse formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        } else {
            print("hello: entry has NO pulse, using fallback: \(fallbackValues.pulse ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.pulse)
            print("hello: pulse fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }

        if let boneMass = entry.scaleEntryMetric?.boneMass {
            print("hello: entry has boneMass: \(boneMass)")
            let formattedValue = BodyMetricsConvertor.convert(Double(boneMass), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.boneMass)
            print("hello: boneMass formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        } else {
            print("hello: entry has NO boneMass, using fallback: \(fallbackValues.boneMass ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.boneMass)
            print("hello: boneMass fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }

        if let visceralFat = entry.scaleEntryMetric?.visceralFatLevel {
            print("hello: entry has visceralFat: \(visceralFat)")
            let formattedValue = BodyMetricsConvertor.convert(Double(visceralFat), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.visceralFat)
            print("hello: visceralFat formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        } else {
            print("hello: entry has NO visceralFat, using fallback: \(fallbackValues.visceralFat ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.visceralFat)
            print("hello: visceralFat fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }

        if let subFat = entry.scaleEntryMetric?.subcutaneousFatPercent {
            print("hello: entry has subFat: \(subFat)")
            let formattedValue = BodyMetricsConvertor.convert(Double(subFat), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.subFat)
            print("hello: subFat formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        } else {
            print("hello: entry has NO subFat, using fallback: \(fallbackValues.subFat ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.subFat)
            print("hello: subFat fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }

        if let protein = entry.scaleEntryMetric?.proteinPercent {
            print("hello: entry has protein: \(protein)")
            let formattedValue = BodyMetricsConvertor.convert(Double(protein), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.protein)
            print("hello: protein formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        } else {
            print("hello: entry has NO protein, using fallback: \(fallbackValues.protein ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.protein)
            print("hello: protein fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }

        if let skelMuscle = entry.scaleEntryMetric?.skeletalMusclePercent {
            print("hello: entry has skelMuscle: \(skelMuscle)")
            let formattedValue = BodyMetricsConvertor.convert(Double(skelMuscle), shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.skelMuscle)
            print("hello: skelMuscle formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        } else {
            print("hello: entry has NO skelMuscle, using fallback: \(fallbackValues.skelMuscle ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: true, wholeNumber: false, fallbackValue: fallbackValues.skelMuscle)
            print("hello: skelMuscle fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }

        if let bmr = entry.scaleEntryMetric?.bmr {
            print("hello: entry has bmr: \(bmr)")
            let bmrValue = Double(bmr) / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.bmr)
            print("hello: bmr formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        } else {
            print("hello: entry has NO bmr, using fallback: \(fallbackValues.bmr ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.bmr)
            print("hello: bmr fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }

        if let metabolicAge = entry.scaleEntryMetric?.metabolicAge {
            print("hello: entry has metabolicAge: \(metabolicAge)")
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.metabolicAge)
            print("hello: metabolicAge formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        } else {
            print("hello: entry has NO metabolicAge, using fallback: \(fallbackValues.metabolicAge ?? -999)")
            let formattedValue = BodyMetricsConvertor.convert(nil, shouldCompose: false, wholeNumber: true, fallbackValue: fallbackValues.metabolicAge)
            print("hello: metabolicAge fallback formattedValue: \(formattedValue)")
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }

    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws {
        // Clear fallback cache to ensure fresh data
        clearFallbackCache()
        
        // Get historical fallback values for each metric
        let fallbackValues = await getHistoricalFallbackValues()
        
        // Update metrics based on selected point data with fallback support
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
    func saveMetricsToAPI() async throws {
        do {
            guard accountService.activeAccount != nil else {
                throw DashboardError.noActiveAccount
            }

            // Get current visible body metrics in order
            let visibleMetrics = Array(state.metrics.prefix(state.activeMetricsCount)).map { $0.label }

            // Convert display labels to API format
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

            // Save to API
            _ = try await accountService.updateDashboardMetrics(metrics: apiMetrics)

            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Saved metrics to API: \(apiMetrics)")

        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to save metrics to API: \(error)")
            throw DashboardError.metricsSaveFailed(error)
        }
    }

    func createEntryForMetricInfo(metricLabel: String? = nil) async -> Entry {
        // Get the latest entry to use its actual values
        let latestEntry = try? await entryService.getLatestEntry()
        
        if let latestEntry = latestEntry {
            // Use the latest entry with current values
            return latestEntry
        } else {
            // Fallback to creating a new entry with current metric values
            return createEntryForMetricInfoSync(metricLabel: metricLabel)
        }
    }

    // Synchronous version for UI operations
    func createEntryForMetricInfoSync(metricLabel: String? = nil, weight: Int = 0) -> Entry {
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )

        // Create entry with current metric values from state
        // Use the actual current values from the metrics state
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

        // Convert string values to appropriate types
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

        // Get the latest weight from data manager state
        let latestWeight = weight
        
        entry.scaleEntry = BathScaleEntry(
            weight: latestWeight,
            bodyFat: bodyFatValue,
            muscleMass: muscleValue,
            water: waterValue,
            bmi: bmiValue,
            source: "dashboard"
        )

        // Add metric entry with all the values
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
        // Convert API metrics to display labels
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

        // Reorder metrics based on API order
        var activeMetrics: [MetricItem] = []
        var inactiveMetrics: [MetricItem] = []

        // Add active metrics in API order
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

        // Add inactive metrics
        for metric in originalMetrics {
            if !displayMetrics.contains(metric.label) {
                let metricItem = MetricItem(
                    value: metric.value,
                    label: metric.label,
                    unit: metric.unit,
                    preLabel: metric.preLabel,
                    icon: metric.icon
                )
                inactiveMetrics.append(metricItem)
            }
        }

        // Update state
        state.metrics = activeMetrics + inactiveMetrics
        
        // Set activeMetricsCount based on the actual API data
        state.activeMetricsCount = activeMetrics.count

        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Updated metrics order from API: \(displayMetrics), activeMetricsCount: \(state.activeMetricsCount)")
    }

    // MARK: - Metric Interaction Methods
    
    /// Handle metric long press interaction
    /// Creates an entry for metric info display and updates selection state
    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) {
        // Update selection state if needed
        // Note: This would need to be called from the store since it manages UI state
        
        // Create entry with the latest data and open metric info sheet
        let entry = createEntryForMetricInfoSync(metricLabel: metricLabel)
        selectedEntry.wrappedValue = entry
        selectedMetric.wrappedValue = getBodyMetric(for: metricLabel)
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Handled metric long press for: \(metricLabel)")
    }
    
    /// Handle selected metric info change
    /// Creates an entry for the selected metric and updates UI state
    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async {
        guard let label = newValue else { return }
        
        // Create entry with the latest data
        let entry = await createEntryForMetricInfo(metricLabel: label)
        
        await MainActor.run {
            selectedEntry.wrappedValue = entry
            selectedMetric.wrappedValue = getBodyMetric(for: label)
        }
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Handled selected metric info change for: \(label)")
    }

    // MARK: - Entry Selection Methods (moved from DashboardStore)
    
    /// Selects an entry for the chart
    func selectEntry(_ entry: BathScaleWeightSummary?, convertWeight: @escaping (Int) -> Double, triggerUpdate: @escaping () -> Void) {
        if let entry = entry {
            // Note: This would need to update the graph state
            // For now, we'll just trigger an update
            triggerUpdate()
        } else {
            // Clear selection
            triggerUpdate()
        }
    }

    /// Reset metrics to latest entry values
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

    /// Handle metric long press interaction with UI state management
    func handleMetricLongPressWithUIState(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>, updateSelectedMetric: @escaping (String?) -> Void) {
        // Update selection state if needed
        updateSelectedMetric(metricLabel)
        
        // Create entry with the latest data and open metric info sheet
        let entry = createEntryForMetricInfoSync(metricLabel: metricLabel)
        selectedEntry.wrappedValue = entry
        selectedMetric.wrappedValue = getBodyMetric(for: metricLabel)
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Handled metric long press with UI state for: \(metricLabel)")
    }

    // MARK: - Fallback Values
    private var cachedFallbackValues: FallbackValues?
    private var lastFallbackCacheTime: Date?
    private let fallbackCacheTimeout: TimeInterval = 60 // Cache for 1 minute
    
    private func getHistoricalFallbackValues() async -> FallbackValues {
        // Check if we have valid cached values
        if let cached = cachedFallbackValues,
           let lastCacheTime = lastFallbackCacheTime,
           Date().timeIntervalSince(lastCacheTime) < fallbackCacheTimeout {
            return cached
        }
        
        do {
            // Get all entries to search through history
            let allEntries = try await entryService.getAllEntries()
            
            // Find the most recent valid value for each metric
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
            
            // Cache the results
            cachedFallbackValues = fallbackValues
            lastFallbackCacheTime = Date()
            
            return fallbackValues
        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to get historical fallback values: \(error)")
            // Return nil values if we can't fetch history
            return FallbackValues(
                bmi: nil, bodyFat: nil, muscleMass: nil, water: nil, pulse: nil,
                boneMass: nil, visceralFat: nil, subFat: nil, protein: nil,
                skelMuscle: nil, bmr: nil, metabolicAge: nil
            )
        }
    }
    
    /// Clears the fallback cache when new data is available
    func clearFallbackCache() {
        cachedFallbackValues = nil
        lastFallbackCacheTime = nil
    }
    
    /// Finds the most recent valid value for a specific metric in the entry history
    private func findLatestValidValue<T: Comparable>(for metricExtractor: (Entry) -> T?, in entries: [Entry]) -> Double? {
        print("hello: findLatestValidValue called with \(entries.count) entries")
        
        // Sort entries by timestamp (newest first) and find the first valid value
        let sortedEntries = entries.sorted { $0.entryTimestamp > $1.entryTimestamp }
        print("hello: Sorted entries, checking from newest to oldest")
        
        for (index, entry) in sortedEntries.enumerated() {
            print("hello: Checking entry \(index + 1)/\(sortedEntries.count) from \(entry.entryTimestamp)")
            
            if let value = metricExtractor(entry) {
                print("hello: Found value: \(value)")
                // Convert to Double and check if it's valid based on metric type
                let doubleValue = Double("\(value)") ?? 0.0
                print("hello: Converted to double: \(doubleValue)")
                
                if isValidMetricValue(doubleValue) {
                    print("hello: Value is valid, returning: \(doubleValue)")
                    return doubleValue
                } else {
                    print("hello: Value is invalid according to isValidMetricValue")
                }
            } else {
                print("hello: No value found for this entry")
            }
        }
        
        print("hello: No valid value found in any entry")
        return nil
    }
    
    /// Determines if a metric value is valid based on its type and reasonable ranges
    private func isValidMetricValue(_ value: Double) -> Bool {
        print("hello: isValidMetricValue called with value: \(value)")
        
        // Check for NaN or infinite values
        guard !value.isNaN && !value.isInfinite else { 
            print("hello: Value is NaN or infinite, returning false")
            return false 
        }
        
        // For body metrics, we need to be more permissive than just > 0.01
        // Many metrics can legitimately be 0 or very small values
        
        // BMI can be 0-100 (though typically 10-50)
        if value >= 0 && value <= 100 { 
            print("hello: Value is in BMI range (0-100), returning true")
            return true 
        }
        
        // Body fat, muscle, water percentages can be 0-100
        if value >= 0 && value <= 100 { 
            print("hello: Value is in percentage range (0-100), returning true")
            return true 
        }
        
        // Heart rate can be 0-300 (though typically 40-200)
        if value >= 0 && value <= 300 { 
            print("hello: Value is in heart rate range (0-300), returning true")
            return true 
        }
        
        // Bone mass can be 0-50
        if value >= 0 && value <= 50 { 
            print("hello: Value is in bone mass range (0-50), returning true")
            return true 
        }
        
        // Visceral fat level can be 0-50
        if value >= 0 && value <= 50 { 
            print("hello: Value is in visceral fat range (0-50), returning true")
            return true 
        }
        
        // BMR can be 0-10000 (though typically 1000-4000)
        if value >= 0 && value <= 10000 { 
            print("hello: Value is in BMR range (0-10000), returning true")
            return true 
        }
        
        // Metabolic age can be 0-150
        if value >= 0 && value <= 150 { 
            print("hello: Value is in metabolic age range (0-150), returning true")
            return true 
        }
        
        // If none of the above ranges match, use a more permissive check
        print("hello: Value didn't match any specific range, checking if >= 0")
        return value >= 0
    }
}
