import Foundation
import SwiftUI

/// Protocol defining metrics management operations
protocol DashboardMetricsManaging {
    func updateMetrics(with entry: Entry) async throws
    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws
    func saveMetricsToAPI() async throws
    func loadMetricsFromAPI() async throws
    func resetMetricsToDefaults() async throws
    func toggleMetricVisibility(at index: Int) async throws
    func reorderMetrics(from source: IndexSet, to destination: Int) async throws
    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double?
    func createEntryForMetricInfo(metricLabel: String?) -> Entry
    func getBodyMetric(for metricLabel: String) -> BodyMetric
}

/// Manages all metric-related operations for the dashboard
@MainActor
class DashboardMetricsManager: ObservableObject, DashboardMetricsManaging {

    // MARK: - Dependencies
    @Injector private var accountService: AccountService
    @Injector private var logger: LoggerService

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
        do {
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
                let bmrValue = bmr / 10.0
                let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true)
                updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
            }

            if let metabolicAge = selectedPoint.metabolicAge {
                let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true)
                updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
            }

            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Updated metrics with selected point data")

        } catch {
            logger.log(level: .error, tag: "DashboardMetricsManager", message: "Failed to update metrics with selected point: \(error)")
            throw DashboardError.invalidMetricData("Failed to update metrics with selected point data")
        }
    }

    // MARK: - API Operations
    func saveMetricsToAPI() async throws {
        do {
            guard let account = accountService.activeAccount else {
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

    // MARK: - Metric Management
    func resetMetricsToDefaults() async throws {
        setupInitialMetrics()
        state.activeMetricsCount = originalMetrics.count
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
            state.metrics.insert(metric, at: state.activeMetricsCount)
            state.activeMetricsCount += 1
        } else {
            state.metrics.append(metric)
            state.activeMetricsCount -= 1
        }

        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Toggled metric visibility at index: \(index)")
    }

    func reorderMetrics(from source: IndexSet, to destination: Int) async throws {
        do {
            state.metrics.move(fromOffsets: source, toOffset: destination)
            logger.log(level: .info, tag: "DashboardMetricsManager", message: "Reordered metrics from \(source) to \(destination)")
        } catch {
            throw DashboardError.invalidMetricData("Failed to reorder metrics")
        }
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

    func createEntryForMetricInfo(metricLabel: String?) -> Entry {
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )

        // Create entry with current metric values
        let bmiStr = state.metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
        let bodyFatStr = state.metrics.first(where: { $0.label == DashboardStrings.bodyFat })?.value
        let muscleStr = state.metrics.first(where: { $0.label == DashboardStrings.muscle })?.value
        let waterStr = state.metrics.first(where: { $0.label == DashboardStrings.water })?.value

        entry.scaleEntry = BathScaleEntry(
            weight: 0, // This should be provided by the caller
            bodyFat: bodyFatStr.flatMap { Double($0) }.flatMap { Int($0) },
            muscleMass: muscleStr.flatMap { Double($0) }.flatMap { Int($0) },
            water: waterStr.flatMap { Double($0) }.flatMap { Int($0) },
            bmi: bmiStr.flatMap { Double($0) }.flatMap { Int($0) },
            source: "dashboard"
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
        state.activeMetricsCount = activeMetrics.count

        logger.log(level: .info, tag: "DashboardMetricsManager", message: "Updated metrics order from API: \(displayMetrics)")
    }
}
