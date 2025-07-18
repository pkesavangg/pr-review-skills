import Foundation
import SwiftUI

/// Manages all scale mode operations and preferences
@MainActor
class ScaleModesManager: ObservableObject {

    // MARK: - Dependencies
    @Injector private var scaleService: ScaleService
    @Injector private var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: ScaleModesState

    // MARK: - Initialization
    init(initialState: ScaleModesState = ScaleModesState()) {
        self.state = initialState
    }

    // MARK: - Mode Management
    func loadScaleModePreferences(for scale: Device) {
        guard let r4Preference = scale.r4ScalePreference else {
            state.originalModeValue = ScaleModes.weightOnly
            state.originalHeartRateEnabled = false
            state.modeValue = ScaleModes.weightOnly
            state.isHeartRateEnabled = false
            updateModeChangeTracking()
            return
        }
        
        state.originalModeValue = r4Preference.shouldMeasureImpedance ? ScaleModes.allBodyMetrics : ScaleModes.weightOnly
        state.originalHeartRateEnabled = r4Preference.shouldMeasurePulse
        state.modeValue = state.originalModeValue
        state.isHeartRateEnabled = state.originalHeartRateEnabled
        updateModeChangeTracking()
        
        logger.log(level: .debug, tag: "ScaleModesManager", message: "Loaded preferences - mode: \(state.modeValue.rawValue), heart rate: \(state.isHeartRateEnabled)")
    }

    func saveScaleModePreferences(for scale: Device) async throws {
        let shouldMeasureImpedance = state.modeValue == ScaleModes.allBodyMetrics
        let shouldMeasurePulse = state.isHeartRateEnabled && shouldMeasureImpedance
        
        var existingDisplayMetrics = scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys
        if existingDisplayMetrics.isEmpty {
            existingDisplayMetrics = ScaleMetrics.defaultMetricsKeys
        }
        
        let scaleId = scale.id
        guard !scaleId.isEmpty else {
            logger.log(level: .error, tag: "ScaleModesManager", message: "Invalid scale ID")
            return
        }
        
        var displayName = scale.nickname ?? scale.deviceName ?? "Unknown Device"
        if displayName.isEmpty {
            displayName = "Unknown Device"
        }
        
        let preference = R4ScalePreference(
            scaleId: scaleId,
            displayName: displayName,
            displayMetrics: existingDisplayMetrics,
            shouldFactoryReset: false,
            shouldMeasureImpedance: shouldMeasureImpedance,
            shouldMeasurePulse: shouldMeasurePulse,
            timeFormat: "12",
            tzOffset: DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: Int(Date().timeIntervalSince1970),
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
        )
        
        try await scaleService.updateScalePreference(scaleId, preference)
        
        if scale.isConnected == true {
            let result = await bluetoothService.updateWeightOnlyMode(on: scale)
            switch result {
            case .success:
                logger.log(level: .info, tag: "ScaleModesManager", message: "Updated scale preferences successfully")
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleModesManager", message: "Failed to update scale: \(error)")
            }
        }
        
        state.originalModeValue = state.modeValue
        state.originalHeartRateEnabled = state.isHeartRateEnabled
        updateModeChangeTracking()
    }

    func resetModeSettings() {
        state.modeValue = state.originalModeValue
        state.isHeartRateEnabled = state.originalHeartRateEnabled
        updateModeChangeTracking()
    }

    func updateModeChangeTracking() {
        state.hasModeChanges = (state.modeValue != state.originalModeValue) || (state.isHeartRateEnabled != state.originalHeartRateEnabled)
    }

    // MARK: - Banner Logic
    func shouldShowWeightOnlyBanner(for scale: Device) -> Bool {
        guard scale.isConnected == true else { return false }
        return isWeightOnlyModeEnabledByOthers(for: scale)
    }

    func shouldShowSetupIncompleteBanner(for scale: Device) -> Bool {
        let isR4Scale = ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4
        let isConnected = scale.isConnected == true
        let isWifiConfigured = scale.isWifiConfigured == true
        let isInWeightOnlyMode = isWeightOnlyModeEnabledByOthers(for: scale)
        
        return isR4Scale && isConnected && !isWifiConfigured && !isInWeightOnlyMode
    }

    func handleWeightOnlyBannerAction(for scale: Device) async {
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleModesManager", message: "Scale not connected, skipping weight-only mode update")
            return
        }
        
        do {
            let result = await bluetoothService.updateWeightOnlyMode(on: scale)
            switch result {
            case .success:
                logger.log(level: .info, tag: "ScaleModesManager", message: "Weight-only mode updated successfully")
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleModesManager", message: "Failed to update weight-only mode: \(error)")
            }
        }
    }

    // MARK: - Private Methods
    private func isWeightOnlyModeEnabledByOthers(for scale: Device) -> Bool {
        if let r4Preference = scale.r4ScalePreference {
            return !r4Preference.shouldMeasureImpedance
        }
        return false
    }
} 