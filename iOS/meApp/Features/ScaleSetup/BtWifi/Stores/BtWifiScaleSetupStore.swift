//
//  BtWifiScaleSetupStore.swift
//  meApp
//
//  Created by Cursor AI on 12/01/25.
//

import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the BtWifi scale setup multi-step flow.
@MainActor
final class BtWifiScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    /// Centralised permission handling service.
    @Injector private var permissionsService: PermissionsService
    /// Bluetooth service for device discovery
    @Injector private var bluetoothService: BluetoothService
    
    let networkMonitor = NetworkMonitor.shared
    
    // MARK: - Private
    @Published private var isNetworkConnected: Bool = true
    private var cancellables = Set<AnyCancellable>()
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: DismissAction?
    /// Discovered scale information
    private var discoveredScale: Device?
    /// Discovery event from Bluetooth service
    private var discoveryEvent: DeviceDiscoveryEvent?
    
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    
    @Published private(set) var currentStep: BtWifiScaleSetupStep = .intro {
        didSet { handleStepChange() }
    }
    
    /// All steps in the setup flow. Exposed as read-only so views can iterate.
    @Published private(set) var steps: [BtWifiScaleSetupStep] = BtWifiScaleSetupStep.allCases
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    private let tag = "BtWifiScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    
    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem))
            case .permissions:
                return AnyView(PermissionListView(setupType: .btWifi))
            default:
                // For now, other screens show the step name as text
                return AnyView(
                    VStack {
                        Text(stepName(for: step))
                            .font(.largeTitle)
                            .padding()
                        Text("Step: \(step.rawValue)")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                )
            }
        }
    }
    
    // MARK: - Lifecycle
    init() {
        // Observe permission updates so the footer button reacts instantly.
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)
        
        networkMonitor.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                self?.isNetworkConnected = isConnected
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Navigation Helpers
    func moveToNextStep() {
        let nextIndex = adjustedIndex(from: currentStepIndex + 1, direction: 1)
        guard nextIndex < steps.count else {
            dismissAction?()
            return
        }
        currentStepIndex = nextIndex
    }
    
    func moveToPreviousStep() {
        let previousIndex = adjustedIndex(from: currentStepIndex - 1, direction: -1)
        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }
    
    // MARK: - Configuration
    /// Configures the store for the given SKU, optionally injecting a previously-discovered
    /// scale and its discovery event (used when the flow originates from the *Scale Discovered* sheet).
    /// - Parameters:
    ///   - sku: The model/SKU (e.g. "0412").
    ///   - discoveredScale: The scale object discovered by Bluetooth (optional).
    ///   - discoveryEvent: The raw discovery event emitted by `BluetoothService` (optional).
    func configure(with sku: String,
                   discoveredScale: Device? = nil,
                   discoveryEvent: DeviceDiscoveryEvent? = nil) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        
        // Inject discovery context if provided.
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        
        // Set the starting step (defaults to intro, but may be connectingBluetooth for direct flow)
        let startStep: BtWifiScaleSetupStep = discoveredScale != nil && discoveryEvent != nil ? .connectingBluetooth : .intro
        if let idx = steps.firstIndex(of: startStep) {
            currentStepIndex = idx
        } else {
            currentStepIndex = 0
        }
        
        // Evaluate initial next-button state.
        updateNextEnabled()
    }
    
    // MARK: - Exit / Help
    
    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        /// For the last step, simply dismiss the setup.
        if currentStep == .scaleConnected {
            dismissAction?()
            return
        }
        
        let alertLang = AlertStrings.ExitSetupAlert.self
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Shows the generic Help modal used across the app.
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(skuToNavigate: scaleItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }
    
    // MARK: - Step Change Handling
    private func handleStepChange() {
        // Handle step-specific logic here
        // For now, just placeholder
    }
    
    // MARK: - Helper Methods
    private func stepName(for step: BtWifiScaleSetupStep) -> String {
        switch step {
        case .intro:
            return "Intro"
        case .permissions:
            return "Permissions"
        case .wakeup:
            return "Wake Up"
        case .connectingBluetooth:
            return "Connecting Bluetooth"
        case .gatheringNetwork:
            return "Gathering Network"
        case .duplicatesFound:
            return "Duplicates Found"
        case .availableWifiList:
            return "Available Wi-Fi List"
        case .wifiPassword:
            return "Wi-Fi Password"
        case .connectingWifi:
            return "Connecting Wi-Fi"
        case .customizeSettings:
            return "Customize Settings"
        case .viewSettings:
            return "View Settings"
        case .updateSettings:
            return "Update Settings"
        case .stepOn:
            return "Step On"
        case .measurement:
            return "Measurement"
        case .scaleConnected:
            return "Scale Connected"
        }
    }
    
    /// Evaluates whether the required permissions have already been granted.
    private func arePermissionsEnabled() -> Bool {
        // For BtWifi, we need both Bluetooth and Location permissions
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED && isNetworkConnected
    }
    
    /// Updates `isNextEnabled` depending on the current step and permission state.
    private func updateNextEnabled() {
        guard currentStep == .permissions else {
            isNextEnabled = true
            return
        }
        
        // Evaluate individual permissions
        let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
        let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        
        // Automatically request missing permissions
        if !bluetoothEnabled {
            Task { await permissionsService.handlePermission(.bluetooth) }
        } else if !bluetoothSwitchEnabled {
            Task { await permissionsService.handlePermission(.bluetoothSwitch) }
        }
        
        // Enable the Next button only when all permissions are granted
        isNextEnabled = bluetoothEnabled && bluetoothSwitchEnabled && isNetworkConnected
    }
    
    /// Returns an adjusted step index by skipping the permissions page when the
    /// permission requirements are already fulfilled.
    /// - Parameters:
    ///   - index: The candidate index to navigate to.
    ///   - direction: `+1` when moving forward; `-1` when moving backwards.
    /// - Returns: A new index that omits the permissions page if it can be skipped.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .permissions,
              arePermissionsEnabled() {
            idx += direction
        }
        return idx
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
} 
