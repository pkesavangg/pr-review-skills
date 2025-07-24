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
       
        if let r4Preference = scale.r4ScalePreference {
            let shouldMeasureImpedance = r4Preference.shouldMeasureImpedance
            let shouldMeasurePulse = r4Preference.shouldMeasurePulse
           
            state.originalModeValue = shouldMeasureImpedance ? ScaleModes.allBodyMetrics : ScaleModes.weightOnly
            state.originalHeartRateEnabled = shouldMeasurePulse
            state.modeValue = state.originalModeValue
            state.isHeartRateEnabled = state.originalHeartRateEnabled
        } else {
            
            // Create default R4ScalePreference if it doesn't exist
            let defaultPreference = R4ScalePreference(
                scaleId: scale.id,
                displayName: scale.nickname ?? scale.deviceName ?? "Unknown Device",
                displayMetrics: ScaleMetrics.defaultMetricsKeys,
                shouldFactoryReset: false,
                shouldMeasureImpedance: true,
                shouldMeasurePulse: true,
                timeFormat: "12",
                tzOffset: DateTimeTools.getTimeZoneInMinutes(),
                wifiFotaScheduleTime: Int(Date().timeIntervalSince1970),
                updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
            )
            scale.r4ScalePreference = defaultPreference
            
            state.originalModeValue = ScaleModes.allBodyMetrics
            state.originalHeartRateEnabled = true
            state.modeValue = state.originalModeValue
            state.isHeartRateEnabled = state.originalHeartRateEnabled
        }
        
        updateModeChangeTracking()
    }

    func saveScaleModePreferences(for scale: Device) async throws {
        let shouldMeasureImpedance = state.modeValue == ScaleModes.allBodyMetrics
        let shouldMeasurePulse = state.isHeartRateEnabled && shouldMeasureImpedance
        
        // Get existing display metrics or use defaults
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
        
        // Create or update R4ScalePreference with proper defaults
        let updatedPreference = R4ScalePreference(
            scaleId: scaleId,
            displayName: displayName,
            displayMetrics: existingDisplayMetrics,
            shouldFactoryReset: scale.r4ScalePreference?.shouldFactoryReset ?? false,
            shouldMeasureImpedance: shouldMeasureImpedance,
            shouldMeasurePulse: shouldMeasurePulse,
            timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
            tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
        )
        
        // Save to database
        try await scaleService.updateScalePreference(scaleId, updatedPreference)
        
        // Update the scale's R4ScalePreference in memory
        scale.r4ScalePreference = updatedPreference
        
        // Update on connected scale if available
        if scale.isConnected == true {
            let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
            switch result {
            case .success(let response):
                // Treat both creationCompleted and userSelectionInProgress as success
                // userSelectionInProgress means the scale is waiting for user selection, which is normal
                if response == .creationCompleted || response == .userSelectionInProgress {
                    logger.log(level: .info, tag: "ScaleModesManager", message: "Scale mode preferences updated on scale successfully (response: \(response))")
                } else {
                    logger.log(level: .info, tag: "ScaleModesManager", message: "Scale update returned unexpected response: \(response)")
                }
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleModesManager", message: "Failed to update scale: \(error)")
            }
        }
        
        // Update state tracking
        state.originalModeValue = state.modeValue
        state.originalHeartRateEnabled = state.isHeartRateEnabled
        updateModeChangeTracking()
        
        logger.log(level: .info, tag: "ScaleModesManager", message: "Scale mode preferences saved successfully")
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
        
        // Check if scale is in weight-only mode (set by other users)
        let isScaleInWeightOnlyMode = isWeightOnlyModeEnabledByOthers(for: scale)
        
        // Check if current user's mode is set to "All Body Metrics"
        let isCurrentUserInAllBodyMetricsMode = state.modeValue == ScaleModes.allBodyMetrics
        
        // Show banner only if scale is in weight-only mode AND current user wants all body metrics
        let shouldShow = isScaleInWeightOnlyMode && isCurrentUserInAllBodyMetricsMode
           
        return shouldShow
    }

    func shouldShowSetupIncompleteBanner(for scale: Device, connectedWifiSSID: String? = nil) -> Bool {
        let isR4Scale = ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4
        let isConnected = scale.isConnected == true
        let isWifiConfigured = scale.isWifiConfigured == true
        let hasConnectedWifi = connectedWifiSSID != nil && !connectedWifiSSID!.isEmpty
        let isInWeightOnlyMode = isWeightOnlyModeEnabledByOthers(for: scale)
        
        // Don't show banner if WiFi is configured OR if there's a connected WiFi network
        let wifiIsWorking = isWifiConfigured || hasConnectedWifi
        
        let shouldShow = isR4Scale && isConnected && !wifiIsWorking && !isInWeightOnlyMode
        
        return shouldShow
    }

    /// Refreshes the WiFi status for the given scale
    func refreshWifiStatus(for scale: Device) async {
        guard scale.isConnected == true else { return }
        
        do {
            let result = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
            switch result {
            case .success(let ssid):
                if !ssid.isEmpty {
                    logger.log(level: .info, tag: "ScaleModesManager", message: "Found connected WiFi SSID: \(ssid)")
                } else {
                    logger.log(level: .info, tag: "ScaleModesManager", message: "No connected WiFi SSID found")
                }
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleModesManager", message: "Failed to get connected WiFi SSID: \(error)")
            }
        }
    }

    func handleWeightOnlyBannerAction(for scale: Device) async {
        // This method is now called when the weight-only banner is tapped
        // The action should navigate to the scale modes screen where the user can change their mode
        logger.log(level: .info, tag: "ScaleModesManager", message: "Weight-only banner tapped - should navigate to scale modes screen")
        
        // Note: The actual navigation will be handled by the calling view/screen
        // This method is kept for logging and potential future functionality
    }

    // MARK: - Private Methods
    private func isWeightOnlyModeEnabledByOthers(for scale: Device) -> Bool {
        if let r4Preference = scale.r4ScalePreference {
            return !r4Preference.shouldMeasureImpedance
        }
        return false
    }
} 
