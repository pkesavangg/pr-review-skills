//
//  DisplayMetricsViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/08/25.
//

import Foundation
import SwiftData
import SwiftUI

// MARK: - DisplayMetricsViewModel
@MainActor
final class DisplayMetricsViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var accountService: AccountServiceProtocol

    // Store the device ID for safe refetching from MainActor context
    private let scaleId: PersistentIdentifier
    private let scaleIdString: String

    // Cached scale for fallback when model not found in context
    private var cachedScale: Device?

    // Returns the cached scale - use refreshScale() to update from database
    var scale: Device {
        if let cached = cachedScale {
            return cached
        }
        logger.log(level: .error, tag: tag, message: "No cached scale available")
        return Device(id: "", accountId: "", deviceName: "Error", deviceType: "")
    }

    /// Refreshes the scale from the database. Call this before operations that need fresh data.
    func refreshScale() {
        // First try registeredModel for already-loaded models (fastest path)
        if let freshScale: Device = PersistenceController.shared.context.registeredModel(for: scaleId) {
            cachedScale = freshScale
            return
        }

        // If not in identity map, fetch from persistent store using FetchDescriptor
        let idToFind = scaleIdString
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { device in
                device.id == idToFind
            }
        )
        do {
            let results = try PersistenceController.shared.context.fetch(descriptor)
            if let freshScale = results.first {
                cachedScale = freshScale
                return
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch scale from store: \(error.localizedDescription)")
        }

        // Keep existing cached value if fetch failed
        if cachedScale != nil {
            logger.log(level: .debug, tag: tag, message: "Using existing cached scale after refresh failed")
        }
    }

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
        self.scaleId = scale.persistentModelID
        self.scaleIdString = scale.id
        self.cachedScale = scale
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
        // Refresh scale data from database
        refreshScale()

        // Load display metrics and update banner states
        loadDisplayMetrics()
        updateBannerStates()
        
        // Reset changes flag after loading data
        hasChanges = false
    }
    
    private func loadDisplayMetrics() {
        refreshScale()
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
// swiftlint:disable:next for_where
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
        let heartRateKey = "heartRate"
        
        // Disable and reorder heart rate in body metrics
        if let idx = metrics.firstIndex(where: { $0.key == heartRateKey }) {
            metrics[idx].isEnabled = false
            metrics = ScaleMetricSetting.reorderOnToggle(items: metrics, key: heartRateKey, isEnabled: false)
        }
        
        // Disable and reorder heart rate in progress metrics (if it exists there)
        if let idx = progressMetrics.firstIndex(where: { $0.key == heartRateKey }) {
            progressMetrics[idx].isEnabled = false
            progressMetrics = ScaleMetricSetting.reorderOnToggle(items: progressMetrics, key: heartRateKey, isEnabled: false)
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
        
        // Update toggle state immediately so SwiftUI can re-evaluate .moveDisabled()
        if let idx = metrics.firstIndex(where: { $0.key == key }) {
            metrics[idx].isEnabled = isEnabled
        }
        updateDisplayMetricsValue()
        hasChanges = true
        
        // Reorder on next run loop to ensure .moveDisabled() is updated first
        Task { @MainActor in
            withAnimation {
                self.metrics = ScaleMetricSetting.reorderOnToggle(items: self.metrics, key: key, isEnabled: isEnabled)
            }
        }
    }
    
    /// Same as `handleBodyMetricToggle` but for progress metrics list.
    func handleProgressMetricToggle(key: String, isEnabled: Bool) {
        // Prevent enabling heart rate when heart rate banner is shown (heart rate is off)
        if key == "heartRate" && isEnabled && showHeartRateBanner {
            logger.log(level: .info, tag: tag, message: "Heart rate cannot be enabled while heart rate banner is shown")
            return
        }
        
        // Update toggle state immediately so SwiftUI can re-evaluate .moveDisabled()
        if let idx = progressMetrics.firstIndex(where: { $0.key == key }) {
            progressMetrics[idx].isEnabled = isEnabled
        }
        updateDisplayMetricsValue()
        hasChanges = true
        
        // Reorder on next run loop to ensure .moveDisabled() is updated first
        Task { @MainActor in
            withAnimation {
                self.progressMetrics = ScaleMetricSetting.reorderOnToggle(items: self.progressMetrics, key: key, isEnabled: isEnabled)
            }
        }
    }
    
// swiftlint:disable:next function_body_length
    func saveDisplayMetrics() async {
        // Step 1: Read @Model synchronously on MainActor, extract to DTO
        refreshScale()
        guard let preference = scale.r4ScalePreference else { return }

        // Extract ALL data to DTO and local variables BEFORE any await
        var dto = preference.toDTO()
        let deviceId = scale.id
        let isConnected = scale.isConnected == true

        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))

        do {
            // Step 2: Apply mutations to DTO (synchronous)
            let shouldMeasureImpedance = dto.shouldMeasureImpedance

            if shouldMeasureImpedance {
                // Normal mode: Update display metrics from current UI state
                let bodyEnabledKeys = metrics.filter { $0.isEnabled }.map { $0.key }
                let progressEnabledKeys = progressMetrics.filter { $0.isEnabled }.map { $0.key }

                // Combine body and progress metrics while maintaining the order from the UI
                dto.displayMetrics = bodyEnabledKeys + progressEnabledKeys

            } else {
                // Weight-only mode: Only modify BMI, preserve all other metrics in their existing positions
                var updatedDisplayMetrics = dto.displayMetrics

                // Check if BMI is enabled in the UI
                let isBMIEnabled = metrics.first { $0.key == "bmi" }?.isEnabled ?? false

                if isBMIEnabled {
                    // Add BMI if not already present
                    if !updatedDisplayMetrics.contains("bmi") {
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

                dto.displayMetrics = updatedDisplayMetrics
            }

            // Step 3: Save to local database using DTO-based method
            try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
            await scaleService.pushLocalChangesToServer()

            // Step 4: Bluetooth update if connected
            if isConnected {
                // Refresh scale to ensure DB has our changes before bluetooth reads them
                refreshScale()
                guard let freshPreference = scale.r4ScalePreference else { return }
                let result = await bluetoothService.updateAccount(on: scale, preference: freshPreference)
                switch result {
                case .success:
                    logger.log(level: .info, tag: tag, message: "Scale metrics updated successfully via Bluetooth")
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to update scale via Bluetooth: \(error.localizedDescription)")
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
