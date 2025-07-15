import Foundation
import SwiftUI

/// Manages all metric-related operations for the dashboard
@MainActor
class DashboardMetricsManager: ObservableObject, DashboardMetricsManaging {

    // MARK: - Dependencies
    @Injector private var accountService: AccountService
    @Injector private var logger: LoggerService
    @Injector private var entryService: EntryService

    // MARK: - Published Properties
    @Published var state: MetricsState

    // MARK: - Original Metrics Configuration
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
    }

    // MARK: - Metric Updates
    func updateMetrics(with entry: Entry) async throws {
      // Update individual metrics based on entry data
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
          let bmrValue = Double(bmr) / 10.0
          let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true)
          updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
      }

      if let metabolicAge = entry.scaleEntryMetric?.metabolicAge {
          let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true)
          updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
      }
    }

    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws {
      // Update metrics based on selected point data
      if let bmi = selectedPoint.bmi {
          let formattedValue = BodyMetricsConvertor.convert(bmi, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
      }

      if let bodyFat = selectedPoint.bodyFat {
          let formattedValue = BodyMetricsConvertor.convert(bodyFat, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
      }

      if let muscleMass = selectedPoint.muscleMass {
          let formattedValue = BodyMetricsConvertor.convert(muscleMass, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
      }

      if let water = selectedPoint.water {
          let formattedValue = BodyMetricsConvertor.convert(water, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.water, value: formattedValue)
      }

      if let pulse = selectedPoint.pulse {
          let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true)
          updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
      }

      if let boneMass = selectedPoint.boneMass {
          let formattedValue = BodyMetricsConvertor.convert(boneMass, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
      }

      if let visceralFat = selectedPoint.visceralFatLevel {
          let formattedValue = BodyMetricsConvertor.convert(visceralFat, shouldCompose: false, wholeNumber: true)
          updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
      }

      if let subFat = selectedPoint.subcutaneousFatPercent {
          let formattedValue = BodyMetricsConvertor.convert(subFat, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
      }

      if let protein = selectedPoint.proteinPercent {
          let formattedValue = BodyMetricsConvertor.convert(protein, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
      }

      if let skelMuscle = selectedPoint.skeletalMusclePercent {
          let formattedValue = BodyMetricsConvertor.convert(skelMuscle, shouldCompose: true, wholeNumber: false)
          updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
      }

      if let bmr = selectedPoint.bmr {
          let bmrValue = Double(bmr) / 10.0
          let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true)
          updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
      }

      if let metabolicAge = selectedPoint.metabolicAge {
          let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true)
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

    func loadMetricsFromAPI() async throws {
        do {
            guard let account = accountService.activeAccount else {
                throw DashboardError.noActiveAccount
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

    // MARK: - Metric Type Management
    
    /// Updates the metric type and adjusts activeMetricsCount accordingly
    func updateMetricType(_ metricType: DashboardMetricType) {
        state.metricType = metricType
        
        // Set activeMetricsCount based on metric type
        if metricType == .four {
            state.activeMetricsCount = 4
        } else {
            state.activeMetricsCount = 12
        }
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Updated metric type to \(metricType), activeMetricsCount: \(state.activeMetricsCount)")
    }

    // MARK: - Metric Management
    func resetMetricsToDefaults() async throws {
        setupInitialMetrics()
        
        // Set activeMetricsCount based on current metric type
        if state.metricType == .four {
            state.activeMetricsCount = 4
        } else {
            state.activeMetricsCount = originalMetrics.count
        }
        
        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reset metrics to defaults, activeMetricsCount: \(state.activeMetricsCount)")
    }

    func toggleMetricVisibility(at index: Int) async throws {
        guard index < state.metrics.count else {
            throw DashboardError.invalidMetricData("Invalid metric index: \(index)")
        }

        let metric = state.metrics[index]
        let isCurrentlyRemoved = index >= state.activeMetricsCount

        state.metrics.remove(at: index)

        if isCurrentlyRemoved {
            // Check if we can add more metrics based on metric type
            let maxAllowedMetrics = state.metricType == .four ? 4 : 12
            if state.activeMetricsCount < maxAllowedMetrics {
                state.metrics.insert(metric, at: state.activeMetricsCount)
                state.activeMetricsCount += 1
            } else {
                // If we can't add more, just append to the end
                state.metrics.append(metric)
            }
        } else {
            state.metrics.append(metric)
            state.activeMetricsCount -= 1
        }

        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Toggled metric visibility at index: \(index), activeMetricsCount: \(state.activeMetricsCount)")
    }

    func reorderMetrics(from source: IndexSet, to destination: Int) async throws {
        state.metrics.move(fromOffsets: source, toOffset: destination)
        
        // Ensure activeMetricsCount is still valid after reordering
        // The activeMetricsCount should represent the number of metrics that are currently visible
        // We need to recalculate this based on the current state and metric type limits
        let maxAllowedMetrics = state.metricType == .four ? 4 : 12
        let currentActiveCount = min(state.activeMetricsCount, state.metrics.count, maxAllowedMetrics)
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

    func getMetricsToShow(isEditMode: Bool, metricType: DashboardMetricType) -> [MetricItem] {
        if isEditMode {
            if metricType == .four {
                // For 4-metric mode in edit mode, show only the 4 basic metrics in correct order
                let fourLabels: [String] = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
                var orderedMetrics: [MetricItem] = []
                
                // Get metrics in the correct order
                for label in fourLabels {
                    if let metric = state.metrics.first(where: { $0.label == label }) {
                        orderedMetrics.append(metric)
                    }
                }
                
                return orderedMetrics
            } else {
                // For 12-metric mode in edit mode, show all metrics
                return state.metrics
            }
        } else {
            if metricType == .four {
                // For 4-metric mode in normal mode, show only the 4 basic metrics in correct order
                let fourLabels: [String] = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
                var orderedMetrics: [MetricItem] = []
                
                // Get metrics in the correct order from active metrics
                let activeMetrics = Array(state.metrics.prefix(state.activeMetricsCount))
                for label in fourLabels {
                    if let metric = activeMetrics.first(where: { $0.label == label }) {
                        orderedMetrics.append(metric)
                    }
                }
                
                return orderedMetrics
            } else {
                // For 12-metric mode in normal mode, show only active metrics (respect activeMetricsCount)
                let activeMetrics = Array(state.metrics.prefix(state.activeMetricsCount))
                return activeMetrics
            }
        }
    }

    /// Returns grid columns configuration based on metric type
    func getMetricGridColumns(for metricType: DashboardMetricType) -> [GridItem] {
        let columnCount = metricType == .four ? 
            DashboardConstants.UI.fourMetricGridColumns : 
            DashboardConstants.UI.twelveMetricGridColumns
        
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
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
        
        // Set activeMetricsCount based on metric type and API data
        if state.metricType == .four {
            // For 4-metric mode, cap at 4 regardless of API data
            state.activeMetricsCount = min(4, activeMetrics.count)
        } else {
            // For 12-metric mode, use the actual count from API
            state.activeMetricsCount = activeMetrics.count
        }

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
}
