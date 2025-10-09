//
//  DisplayMetricsViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/08/25.
//

import Foundation
import SwiftUI

// MARK: - DisplayMetricsViewModel
@MainActor
final class DisplayMetricsViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var bluetoothService: BluetoothService
    @Injector var logger: LoggerService
    @Injector var accountService: AccountService
    
    @Published var scale: Device
    @Published var metrics: [ScaleMetricSetting] = []
    @Published var progressMetrics: [ScaleMetricSetting] = []
    @Published var displayMetricsValue: String = ""
    
    // Banner states
    @Published var showWeightOnlyBanner: Bool = false
    @Published var showWeightOnlyInfo: Bool = false
    @Published var showHeartRateBanner: Bool = false
    @Published var isWeightOnlyModeOn: Bool = false
    @Published var isHeartRateOn: Bool = false
    @Published var isHeartRateEnabled: Bool = false
    
    // Track if changes have been made
    @Published var hasChanges: Bool = false
    
    private let isWeighOnlyModeEnabledByOthers: Bool
    private let tag = "DisplayMetricsViewModel"
    
    init(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false) {
        self.scale = scale
        self.isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers
        setupInitialValues()
    }
    
    private func setupInitialValues() {
        // Load initial metrics
        loadDisplayMetrics()
        
        // Setup banner states based on scale preferences
        updateBannerStates()
        
        // Reset changes flag after initial setup
        hasChanges = false
    }
    
    func loadDisplayMetricsData() async {
        // Refresh scale data from service
        do {
            if let updatedScale = try await scaleService.getDevices().first(where: { $0.id == scale.id }) {
                scale = updatedScale
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load scale data: \(error)")
        }
        
        // Load display metrics and update banner states
        loadDisplayMetrics()
        updateBannerStates()
        
        // Reset changes flag after loading data
        hasChanges = false
    }
    
    private func loadDisplayMetrics() {
        guard let preference = scale.r4ScalePreference else {
            // Default metrics if no preference
            metrics = ScaleMetrics.bodyMetrics
            progressMetrics = ScaleMetrics.progressMetrics
            updateDisplayMetricsValue()
            return
        }
        
        let shouldMeasureImpedance = preference.shouldMeasureImpedance
        let displayMetricsKeys = preference.displayMetrics
        
        // Get available body metrics based on impedance setting
        let availableBodyMetrics = shouldMeasureImpedance ? 
            ScaleMetrics.bodyMetrics : 
            ScaleMetrics.bodyMetrics.filter { $0.key == "bmi" }
        
        // Order body metrics: enabled first (in displayMetrics order), then disabled (in ScaleMetrics order)
        metrics = orderMetrics(
            availableMetrics: availableBodyMetrics,
            displayMetricsKeys: displayMetricsKeys
        )
        
        // Order progress metrics the same way
        progressMetrics = orderMetrics(
            availableMetrics: ScaleMetrics.progressMetrics,
            displayMetricsKeys: displayMetricsKeys
        )
        
        updateDisplayMetricsValue()
    }
    
    private func orderMetrics(
        availableMetrics: [ScaleMetricSetting],
        displayMetricsKeys: [String]
    ) -> [ScaleMetricSetting] {
        var orderedMetrics: [ScaleMetricSetting] = []
        
        // First, add enabled metrics in the order they appear in displayMetrics
        for key in displayMetricsKeys {
            if let metric = availableMetrics.first(where: { $0.key == key }) {
                var enabledMetric = metric
                enabledMetric.isEnabled = true
                orderedMetrics.append(enabledMetric)
            }
        }
        
        // Then, add disabled metrics in their original ScaleMetrics order
        for metric in availableMetrics {
            if !displayMetricsKeys.contains(metric.key) {
                var disabledMetric = metric
                disabledMetric.isEnabled = false
                orderedMetrics.append(disabledMetric)
            }
        }
        
        return orderedMetrics
    }
    
    private func updateBannerStates() {
        guard let preference = scale.r4ScalePreference else {
            showWeightOnlyBanner = false
            showWeightOnlyInfo = false
            showHeartRateBanner = false
            isWeightOnlyModeOn = false
            isHeartRateOn = false
            isHeartRateEnabled = false
            return
        }
        
        let shouldMeasureImpedance = preference.shouldMeasureImpedance
        let shouldMeasurePulse = preference.shouldMeasurePulse
        
        // Weight-only banner logic
        showWeightOnlyBanner = !shouldMeasureImpedance // Current user is in weight-only mode
        showWeightOnlyInfo = isWeighOnlyModeEnabledByOthers // Show info when weight-only is enabled by others
        
        // Heart rate banner logic
        showHeartRateBanner = !shouldMeasurePulse && shouldMeasureImpedance // Heart rate is off but body metrics are on
        
        // State values
        isWeightOnlyModeOn = !shouldMeasureImpedance
        isHeartRateOn = shouldMeasurePulse
        isHeartRateEnabled = shouldMeasurePulse
        
        // Ensure heart rate metric is disabled when heart rate banner is shown
        if showHeartRateBanner {
            disableHeartRateMetric()
        }
    }
    
    /// Disables the heart rate metric in the metrics list when heart rate is off
    private func disableHeartRateMetric() {
        let heartRateKey = ScaleMetrics.heartRateKey
        
        // Disable and reorder heart rate in body metrics
        if let heartRateIndex = metrics.firstIndex(where: { $0.key == heartRateKey }) {
            metrics = reorderMetricsOnToggle(items: metrics, key: heartRateKey, isEnabled: false)
        }
        
        // Disable and reorder heart rate in progress metrics (if it exists there)
        if let heartRateIndex = progressMetrics.firstIndex(where: { $0.key == heartRateKey }) {
            progressMetrics = reorderMetricsOnToggle(items: progressMetrics, key: heartRateKey, isEnabled: false)
        }
        
        // Update display metrics value to reflect the change
        updateDisplayMetricsValue()
    }
    
    func updateMetrics(_ newMetrics: [ScaleMetricSetting]) {
        // Check if this is a toggle operation (enabled state changed) or a reorder operation
        let oldEnabledKeys = Set(metrics.filter { $0.isEnabled }.map { $0.key })
        let newEnabledKeys = Set(newMetrics.filter { $0.isEnabled }.map { $0.key })
        
        let isToggleOperation = oldEnabledKeys != newEnabledKeys
        let isReorderOperation = !isToggleOperation
        
        if isReorderOperation {
            // For reorder operations, just update the metrics directly without re-ordering
            metrics = newMetrics
            updateDisplayMetricsValue()
            hasChanges = true
            return
        }
        
        // For toggle operations, just update the enabled state without changing order
        metrics = newMetrics
        updateDisplayMetricsValue()
        hasChanges = true
    }
    
    func updateProgressMetrics(_ newMetrics: [ScaleMetricSetting]) {
        // Check if this is a toggle operation (enabled state changed) or a reorder operation
        let oldEnabledKeys = Set(progressMetrics.filter { $0.isEnabled }.map { $0.key })
        let newEnabledKeys = Set(newMetrics.filter { $0.isEnabled }.map { $0.key })
        
        let isToggleOperation = oldEnabledKeys != newEnabledKeys
        let isReorderOperation = !isToggleOperation
        
        if isReorderOperation {
            // For reorder operations, just update the metrics directly without re-ordering
            progressMetrics = newMetrics
            updateDisplayMetricsValue()
            hasChanges = true
            return
        }
        
        // For toggle operations, just update the enabled state without changing order
        progressMetrics = newMetrics
        updateDisplayMetricsValue()
        hasChanges = true
    }
    
    func updateDisplayMetricsValue() {
        let allMetrics = metrics + progressMetrics
        let enabledMetrics = allMetrics.filter { $0.isEnabled }
        displayMetricsValue = enabledMetrics.map { $0.key }.joined(separator: ",")
    }
    
    // MARK: - Toggle Reorder Helpers
    /// Reorders body metrics when a toggle changes, preserving the order of already-disabled items
    /// and appending the newly disabled item to the very end of the disabled group (or enabled group when turning on).
    func handleBodyMetricToggle(key: String, isEnabled: Bool) {
        // Prevent enabling heart rate when heart rate banner is shown (heart rate is off)
        if key == "heartRate" && isEnabled && showHeartRateBanner {
            logger.log(level: .info, tag: tag, message: "Heart rate cannot be enabled while heart rate banner is shown")
            return
        }
        
        withAnimation {
            metrics = reorderMetricsOnToggle(items: metrics, key: key, isEnabled: isEnabled)
            updateDisplayMetricsValue()
            hasChanges = true
        }
    }
    
    /// Same as `handleBodyMetricToggle` but for progress metrics list.
    func handleProgressMetricToggle(key: String, isEnabled: Bool) {
        // Prevent enabling heart rate when heart rate banner is shown (heart rate is off)
        if key == "heartRate" && isEnabled && showHeartRateBanner {
            logger.log(level: .info, tag: tag, message: "Heart rate cannot be enabled while heart rate banner is shown")
            return
        }
        
        withAnimation {
            progressMetrics = reorderMetricsOnToggle(items: progressMetrics, key: key, isEnabled: isEnabled)
            updateDisplayMetricsValue()
            hasChanges = true
        }
    }

    /// Core reordering routine used by both body and progress metric toggles.
    private func reorderMetricsOnToggle(items: [ScaleMetricSetting], key: String, isEnabled: Bool) -> [ScaleMetricSetting] {
        var current = items
        guard let idx = current.firstIndex(where: { $0.key == key }) else { return items }
        var changed = current.remove(at: idx)
        changed.isEnabled = isEnabled
        if !isEnabled {
            let enabled = current.filter { $0.isEnabled }
            var disabled = current.filter { !$0.isEnabled }
            disabled.append(changed)
            return enabled + disabled
        } else {
            var enabled = current.filter { $0.isEnabled }
            let disabled = current.filter { !$0.isEnabled }
            enabled.append(changed)
            return enabled + disabled
        }
    }
    
    func saveDisplayMetrics() async {
        guard let preference = scale.r4ScalePreference else { return }
        
        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
        
        do {
            let shouldMeasureImpedance = preference.shouldMeasureImpedance
            
            if shouldMeasureImpedance {
                // Normal mode: Update display metrics from current UI state
                let bodyEnabledKeys = metrics.filter { $0.isEnabled }.map { $0.key }
                let progressEnabledKeys = progressMetrics.filter { $0.isEnabled }.map { $0.key }
                
                // Combine body and progress metrics while maintaining the order from the UI
                preference.displayMetrics = bodyEnabledKeys + progressEnabledKeys
                
            } else {
                // Weight-only mode: Only modify BMI, preserve all other metrics in their existing positions
                var updatedDisplayMetrics = preference.displayMetrics
                
                // Check if BMI is enabled in the UI
                let isBMIEnabled = metrics.first(where: { $0.key == "bmi" })?.isEnabled ?? false
                
                if isBMIEnabled {
                    // Add BMI if not already present
                    if !updatedDisplayMetrics.contains("bmi") {
                        // Find a good position to insert BMI (at the beginning of body metrics)
                        let progressMetricsKeys = ScaleMetrics.progressMetrics.map { $0.key }
                        if let firstProgressIndex = updatedDisplayMetrics.firstIndex(where: { progressMetricsKeys.contains($0) }) {
                            updatedDisplayMetrics.insert("bmi", at: firstProgressIndex)
                        } else {
                            updatedDisplayMetrics.append("bmi")
                        }
                    }
                } else {
                    // Remove BMI if present
                    updatedDisplayMetrics.removeAll { $0 == "bmi" }
                }
                
                // Update progress metrics normally
                let progressEnabledKeys = progressMetrics.filter { $0.isEnabled }.map { $0.key }
                
                // Remove old progress metrics and add new ones in order
                let progressMetricsKeys = ScaleMetrics.progressMetrics.map { $0.key }
                updatedDisplayMetrics.removeAll { progressMetricsKeys.contains($0) }
                updatedDisplayMetrics.append(contentsOf: progressEnabledKeys)
                
                preference.displayMetrics = updatedDisplayMetrics
            }
            
            // Save to local database
            try await scaleService.updateScalePreference(scale.id, preference)
            await scaleService.pushLocalChangesToServer()
            
            // Update the scale via Bluetooth if connected
            if scale.isConnected == true {
                let result = await bluetoothService.updateAccount(on: scale, preference: preference)
                switch result {
                case .success(_):
                    logger.log(level: .info, tag: tag, message: "Scale metrics updated successfully via Bluetooth")
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to update scale via Bluetooth: \(error.localizedDescription)")
                    // Rethrow to trigger retry logic
                    throw error
                }
            }
            
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.displayMetricsSaved))
            
            // Reset changes flag after successful save
            hasChanges = false
            
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save display metrics: \(error.localizedDescription)", data: error)
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorSavingDisplayMetrics))
        }
    }
}
