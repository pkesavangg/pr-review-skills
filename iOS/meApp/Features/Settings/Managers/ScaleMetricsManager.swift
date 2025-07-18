import Foundation
import SwiftUI

/// Manages all display metrics operations for scales
@MainActor
class ScaleMetricsManager: ObservableObject {

    // MARK: - Dependencies
    @Injector private var scaleService: ScaleService
    @Injector private var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: ScaleMetricsState

    // MARK: - Initialization
    init(initialState: ScaleMetricsState = ScaleMetricsState()) {
        self.state = initialState
    }

    // MARK: - Metrics Management
    func loadDisplayMetrics(for scale: Device) {
        let displayMetrics = scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys
        
        let bodyMetricsConfig = ScaleMetrics.bodyMetrics
        let bodyMetricsWithState = bodyMetricsConfig.map { config in
            ScaleMetricSetting(
                name: config.name,
                key: config.key,
                imagePath: config.imagePath,
                isEnabled: displayMetrics.contains(config.key),
                isProgressMetrics: false
            )
        }
        
        state.metrics = sortMetrics(bodyMetricsWithState, displayMetrics: displayMetrics, originalConfig: bodyMetricsConfig)
        
        let progressMetricsConfig = ScaleMetrics.progressMetrics
        let progressMetricsWithState = progressMetricsConfig.map { config in
            ScaleMetricSetting(
                name: config.name,
                key: config.key,
                imagePath: config.imagePath,
                isEnabled: displayMetrics.contains(config.key),
                isProgressMetrics: true
            )
        }
        
        state.progressMetrics = sortMetrics(progressMetricsWithState, displayMetrics: displayMetrics, originalConfig: progressMetricsConfig)
        
        updateDisplayMetricsValue()
        
        logger.log(level: .debug, tag: "ScaleMetricsManager", message: "Loaded display metrics: \(displayMetrics)")
    }

    func saveDisplayMetrics(for scale: Device) async throws {
        let bodyMetrics = extractDisplayMetrics(from: state.metrics)
        let progressMetricsKeys = extractDisplayMetrics(from: state.progressMetrics)
        let displayMetrics = bodyMetrics + progressMetricsKeys
        
        let updatedPreference = R4ScalePreference(
            scaleId: scale.id,
            displayName: scale.r4ScalePreference?.displayName ?? scale.nickname ?? scale.deviceName ?? "Unknown Device",
            displayMetrics: displayMetrics.isEmpty ? ScaleMetrics.defaultMetricsKeys : displayMetrics,
            shouldFactoryReset: scale.r4ScalePreference?.shouldFactoryReset ?? false,
            shouldMeasureImpedance: scale.r4ScalePreference?.shouldMeasureImpedance ?? true,
            shouldMeasurePulse: scale.r4ScalePreference?.shouldMeasurePulse ?? true,
            timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
            tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
        )
        
        try await scaleService.updateScalePreference(scale.id, updatedPreference)
        
        if scale.isConnected == true {
            let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
            switch result {
            case .success(let response):
                if response == .creationCompleted {
                    logger.log(level: .info, tag: "ScaleMetricsManager", message: "Display metrics updated on scale successfully")
                } else {
                    logger.log(level: .error, tag: "ScaleMetricsManager", message: "Scale update returned: \(response)")
                }
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleMetricsManager", message: "Failed to update scale: \(error)")
            }
        }
        
        updateDisplayMetricsValue()
        
        logger.log(level: .info, tag: "ScaleMetricsManager", message: "Display metrics saved successfully")
    }

    func handleMetricsReorder(indices: IndexSet, newOffset: Int, isProgressMetrics: Bool) {
        if isProgressMetrics {
            state.progressMetrics.move(fromOffsets: indices, toOffset: newOffset)
            reorderMetricsToKeepDisabledAtEnd(isProgressMetrics: true)
        } else {
            state.metrics.move(fromOffsets: indices, toOffset: newOffset)
            reorderMetricsToKeepDisabledAtEnd(isProgressMetrics: false)
        }
        
        updateDisplayMetricsValue()
    }

    // MARK: - Private Methods
    private func extractDisplayMetrics(from metrics: [ScaleMetricSetting]) -> [String] {
        return metrics.filter { $0.isEnabled }.map { $0.key }
    }

    private func sortMetrics(_ metrics: [ScaleMetricSetting], displayMetrics: [String], originalConfig: [ScaleMetricSetting]) -> [ScaleMetricSetting] {
        return metrics.sorted { first, second in
            if first.isEnabled == second.isEnabled {
                if first.isEnabled {
                    let firstApiIndex = displayMetrics.firstIndex(of: first.key) ?? Int.max
                    let secondApiIndex = displayMetrics.firstIndex(of: second.key) ?? Int.max
                    return firstApiIndex < secondApiIndex
                } else {
                    let firstIndex = originalConfig.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = originalConfig.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
            }
            return first.isEnabled && !second.isEnabled
        }
    }

    private func reorderMetricsToKeepDisabledAtEnd(isProgressMetrics: Bool) {
        if isProgressMetrics {
            state.progressMetrics.sort { first, second in
                if first.isEnabled == second.isEnabled {
                    let firstIndex = state.progressMetrics.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = state.progressMetrics.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
                return first.isEnabled && !second.isEnabled
            }
        } else {
            state.metrics.sort { first, second in
                if first.isEnabled == second.isEnabled {
                    let firstIndex = state.metrics.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = state.metrics.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
                return first.isEnabled && !second.isEnabled
            }
        }
    }

    func updateDisplayMetricsValue() {
        // Always set to empty string as requested
        state.displayMetricsValue = ""
    }
} 