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
    let notificationService: NotificationHelperServiceProtocol
    let scaleService: PairedDeviceServiceProtocol
    let bluetoothService: BluetoothServiceProtocol
    let logger: LoggerServiceProtocol
    let accountService: AccountServiceProtocol

    private let scaleIdString: String

    /// Reads the current snapshot directly from the service — the single source of truth.
    private var deviceSnapshot: DeviceSnapshot? {
        scaleService.scales.first(where: { $0.id == scaleIdString })
    }

    @Published var metrics: [DeviceMetricSetting] = []
    @Published var progressMetrics: [DeviceMetricSetting] = []
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

    init(
        scale: Device,
        isWeighOnlyModeEnabledByOthers: Bool = false,
        notificationService: NotificationHelperServiceProtocol? = nil,
        scaleService: PairedDeviceServiceProtocol? = nil,
        bluetoothService: BluetoothServiceProtocol? = nil,
        logger: LoggerServiceProtocol? = nil,
        accountService: AccountServiceProtocol? = nil
    ) {
        self.scaleIdString = scale.id
        self.isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers
        self.notificationService = notificationService ?? Self.resolveDependency(NotificationHelperServiceProtocol.self)
        self.scaleService = scaleService ?? Self.resolveDependency(PairedDeviceServiceProtocol.self)
        self.bluetoothService = bluetoothService ?? Self.resolveDependency(BluetoothServiceProtocol.self)
        self.logger = logger ?? Self.resolveDependency(LoggerServiceProtocol.self)
        self.accountService = accountService ?? Self.resolveDependency(AccountServiceProtocol.self)
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
        loadDisplayMetrics()
        updateBannerStates()
        hasChanges = false
    }

    private func loadDisplayMetrics() {
        guard let preference = deviceSnapshot?.r4ScalePreference else {
            // Default metrics if no preference
            metrics = DeviceMetrics.bodyMetrics
            progressMetrics = DeviceMetrics.progressMetrics
            updateDisplayMetricsValue()
            return
        }
        
        let shouldMeasureImpedance = preference.shouldMeasureImpedance
        let displayMetricsKeys = preference.displayMetrics
        
        // Get available body metrics based on impedance setting
        let availableBodyMetrics = shouldMeasureImpedance ? 
            DeviceMetrics.bodyMetrics : 
            DeviceMetrics.bodyMetrics.filter { $0.key == "bmi" }
        
        // Order body metrics: enabled first (in displayMetrics order), then disabled (in DeviceMetrics order)
        metrics = orderMetrics(
            availableMetrics: availableBodyMetrics,
            displayMetricsKeys: displayMetricsKeys
        )
        
        // Order progress metrics the same way
        progressMetrics = orderMetrics(
            availableMetrics: DeviceMetrics.progressMetrics,
            displayMetricsKeys: displayMetricsKeys
        )
        
        updateDisplayMetricsValue()
    }
    
    private func orderMetrics(
        availableMetrics: [DeviceMetricSetting],
        displayMetricsKeys: [String]
    ) -> [DeviceMetricSetting] {
        var orderedMetrics: [DeviceMetricSetting] = []
        
        // First, add enabled metrics in the order they appear in displayMetrics
        for key in displayMetricsKeys {
            if let metric = availableMetrics.first(where: { $0.key == key }) {
                var enabledMetric = metric
                enabledMetric.isEnabled = true
                orderedMetrics.append(enabledMetric)
            }
        }
        
        // Then, add disabled metrics in their original DeviceMetrics order
        for metric in availableMetrics where !displayMetricsKeys.contains(metric.key) {
            var disabledMetric = metric
            disabledMetric.isEnabled = false
            orderedMetrics.append(disabledMetric)
        }
        
        return orderedMetrics
    }
    
    private func updateBannerStates() {
        guard let preference = deviceSnapshot?.r4ScalePreference else {
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
            metrics = DeviceMetricSetting.reorderOnToggle(items: metrics, key: heartRateKey, isEnabled: false)
        }
        
        // Disable and reorder heart rate in progress metrics (if it exists there)
        if let idx = progressMetrics.firstIndex(where: { $0.key == heartRateKey }) {
            progressMetrics[idx].isEnabled = false
            progressMetrics = DeviceMetricSetting.reorderOnToggle(items: progressMetrics, key: heartRateKey, isEnabled: false)
        }
        
        // Update display metrics value to reflect the change
        updateDisplayMetricsValue()
    }
    
    func updateMetrics(_ newMetrics: [DeviceMetricSetting]) {
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
    
    func updateProgressMetrics(_ newMetrics: [DeviceMetricSetting]) {
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
                self.metrics = DeviceMetricSetting.reorderOnToggle(items: self.metrics, key: key, isEnabled: isEnabled)
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
                self.progressMetrics = DeviceMetricSetting.reorderOnToggle(items: self.progressMetrics, key: key, isEnabled: isEnabled)
            }
        }
    }
    
// swiftlint:disable:next function_body_length
    func saveDisplayMetrics() async {
        guard let snapshot = deviceSnapshot, let preference = snapshot.r4ScalePreference else { return }

        var dto = preference.toDTO()
        let deviceId = scaleIdString
        let isConnected = snapshot.isConnected
        let broadcastId = snapshot.broadcastIdString ?? ""

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
                        let progressMetricsKeys = DeviceMetrics.progressMetrics.map { $0.key }
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
                let progressMetricsKeys = DeviceMetrics.progressMetrics.map { $0.key }
                updatedDisplayMetrics.removeAll { progressMetricsKeys.contains($0) }
                updatedDisplayMetrics.append(contentsOf: progressEnabledKeys)

                dto.displayMetrics = updatedDisplayMetrics
            }

            // Step 3: Save to local database using DTO-based method
            try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
            await scaleService.pushLocalChangesToServer()

            // Step 4: Bluetooth update if connected
            if isConnected {
                let result = await bluetoothService.updateAccount(broadcastId: broadcastId)
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

    /// Presents a confirm-discard alert; returns true if the user confirms exit.
    func confirmDiscardChanges() async -> Bool {
        let alertLang = AlertStrings.EditProfileExitAlert.self
        return await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: alertLang.title,
                message: alertLang.message,
                buttons: [
                    AlertButtonModel(title: alertLang.exitButton, type: .primary) { _ in
                        continuation.resume(returning: true)
                    },
                    AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in
                        continuation.resume(returning: false)
                    }
                ]
            )
            notificationService.showAlert(alert)
        }
    }

    /// Allows exit when no changes are pending; otherwise prompts for confirmation.
    func allowExit() async -> Bool {
        if !hasChanges { return true }
        return await confirmDiscardChanges()
    }
}

private extension DisplayMetricsViewModel {
    static func resolveDependency<T>(_ type: T.Type) -> T {
        guard let dependency = DependencyContainer.shared.resolve(type) else {
            let keys = DependencyContainer.shared.dependencies.keys.sorted().joined(separator: ", ")
            fatalError("Dependency \(type) is not registered in DependencyContainer. Registered keys: [\(keys)]")
        }
        return dependency
    }
}
