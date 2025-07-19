///
///  BluetoothScaleSetupStore.swift
///  meApp
///
///  Created by Cursor AI on 18/07/25.
///

import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the Bluetooth (A3) scale-setup multi-step flow.
@MainActor
final class BluetoothScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var permissionsService: PermissionsService
    @Injector private var bluetoothService: BluetoothService

    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private var deviceDiscoveryCancellable: AnyCancellable? = nil
    private var stepTimerTask: Task<Void, Never>? = nil

    private var scaleItem: ScaleItemInfo?
    private var discoveredScale: Device?
    private var discoveryEvent: DeviceDiscoveryEvent?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: DismissAction?
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }

    @Published private(set) var currentStep: BluetoothScaleSetupStep = .intro {
        didSet { handleStepChange() }
    }

    @Published private(set) var steps: [BluetoothScaleSetupStep] = BluetoothScaleSetupStep.allCases

    /// Connection status shown in the BluetoothConnectionView.
    @Published var connectionState: ConnectionState = .loading

    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true

    /// Flag that indicates if the scale is currently syncing the first entry.
    @Published var syncing: Bool = true

    /// Computed helper mirroring the `backDisabled()` logic from the legacy Angular flow.
    var isBackDisabled: Bool {
        switch currentStep {
        case .intro:
            return true // Same as scaleInfo in Angular
        case .stepOn:
            return syncing
        case .setupFinished:
            return true
        case .findUser:
            return discoveredScale != nil
        default:
            return false
        }
    }

    /// Selected user number (1-8) captured from SelectUser step.
    @Published var selectedUserNumber: Int? = nil {
        didSet { updateNextEnabled() }
    }

    private let tag = "BluetoothScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let timeoutConstants = AppConstants.TimeoutsAndRetention.self

    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }

        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem))
            case .permissions:
                return AnyView(PermissionListView(setupType: .bluetooth))
            case .selectUser:
                // Dummy view – UI to select user number will be implemented later.
                return AnyView(
                    UserNumberSelectionView(selectedNumber: selectedUserNumber, onNumberSelected: { value in
                        self.selectedUserNumber = value
                    })
                )
            case .connectingBluetooth:
                return AnyView(
                    Text("connectingBluetooth (Placeholder)")
                        .fontOpenSans(.body1)
                )
            case .findUser:
                return AnyView(
                    Text("findUser (Placeholder)")
                        .fontOpenSans(.body1)
                )
            case .stepOn:
                return AnyView(
                    Text("stepOn (Placeholder)")
                        .fontOpenSans(.body1)
                )
            case .setupFinished:
                let lang = scaleSetupStrings.FinishViewStrings.self
                return AnyView(
                    ScaleSetupFinishView(title: lang.title, description: lang.description)
                        .environmentObject(Theme.shared)
                )
            }
        }
    }

    // MARK: - Init
    init() {
        // Observe permission updates so the footer button reacts instantly.
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
                self?.handlePermissionChange()
            }
            .store(in: &cancellables)
    }

    // MARK: - Navigation Helpers
    func moveToNextStep() {
        let candidate = currentStepIndex + 1
        let nextIndex = adjustedIndex(from: candidate, direction: 1)
        guard nextIndex < steps.count else { return }
        currentStepIndex = nextIndex
    }

    func moveToPreviousStep() {
        let candidate = currentStepIndex - 1
        let previousIndex = adjustedIndex(from: candidate, direction: -1)
        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }

    /// Returns an adjusted step index by skipping the permissions page when Bluetooth permissions are already granted.
    /// - Parameters:
    ///   - index: Proposed index.
    ///   - direction: +1 when moving forward, -1 when moving backwards.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .permissions,
              isBluetoothPermissionEnabled() {
            idx += direction
        }
        return idx
    }

    // MARK: - Public Configuration
    func configure(with sku: String,) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved

        resetDiscoveryState()
    }

    // MARK: - Exit / Help
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(skuToNavigate: scaleItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }
    
    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        /// For the last step, simply dismiss the setup.
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

    // MARK: - Step Transition Handling
    private func handleStepChange() {
        switch currentStep {
        case .connectingBluetooth:
            connectionState = .loading
            pair()
        case .findUser:
            // Simulate quick progression to step-on
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 2_000_000_000) // 2s
                self.moveToNextStep()
                // Start syncing simulation
                self.syncing = true
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 3_000_000_000) // 3s fake sync
                    self.syncing = false
                    self.updateNextEnabled()
                }
            }
        default:
            break
        }
    }

    // MARK: - Pairing Logic
    private func pair() {
        resetDiscoveryState()

        // Begin scan for pairing-mode devices.
        bluetoothService.scanForPairing()

        // Listen for discovery events.
        deviceDiscoveryCancellable = bluetoothService.deviceDiscoveredPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                self?.handleDeviceDiscovery(event)
            }

        // Timeout after N seconds if nothing is found
        stepTimerTask = Task { [weak self] in
            guard let self else { return }
            let ns = UInt64(timeoutConstants.bluetoothTimeoutNs)
            try? await Task.sleep(nanoseconds: ns)
            await MainActor.run {
                if self.discoveredScale == nil && self.currentStep == .connectingBluetooth {
                    self.connectionState = .failure
                }
            }
        }
    }

    private func retryPairing() {
        if let connectingIndex = steps.firstIndex(of: .connectingBluetooth) {
            currentStepIndex = connectingIndex
        }
    }

    private func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        // Only handle during connecting step & for bluetooth scales
        guard currentStep == .connectingBluetooth else { return }
        guard event.deviceInfo.setupType == .bluetooth else { return }

        deviceDiscoveryCancellable?.cancel()
        stepTimerTask?.cancel()

        self.discoveredScale = event.device
        self.discoveryEvent = event

        Task { await self.saveDiscoveredScale() }
    }

    private func saveDiscoveredScale() async {
        guard let discoveryEvent, var device = discoveredScale else { return }
        device.sku = scaleItem?.sku ?? discoveryEvent.device.sku
        device.deviceType = DeviceType.scale.rawValue
        device.bathScale = device.bathScale ?? BathScale(scaleType: ScaleSourceType.bluetooth.rawValue, bodyComp: device.bathScale?.bodyComp ?? false)

        let result = await bluetoothService.addNewDevice(device, metaData: nil)
        switch result {
        case .success:
            connectionState = .success
            // Advance to findUser after small delay to show success state.
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if let findIndex = self.steps.firstIndex(of: .findUser) {
                    self.currentStepIndex = findIndex
                }
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
            connectionState = .failure
        }
    }

    // MARK: - Helpers
    private func resetDiscoveryState() {
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        discoveredScale = nil
        discoveryEvent = nil
    }

    private func isBluetoothPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }

    private func updateNextEnabled() {
        // When we are on the permissions step we only enable "Next" once the
        // required Bluetooth permissions are granted. For any other step the
        // button stays enabled so the user can progress or go back.
        guard currentStep == .permissions else {
            isNextEnabled = true
            return
        }

        // Evaluate individual Bluetooth-related permissions
        let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
        let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED

        // Automatically prompt for the missing permission, prioritising the core
        // Bluetooth authorisation before the hardware switch.
        if !bluetoothEnabled {
            Task { await permissionsService.handlePermission(.bluetooth) }
        } else if !bluetoothSwitchEnabled {
            Task { await permissionsService.handlePermission(.bluetoothSwitch) }
        }

        // Enable the Next button only when both permissions are satisfied
        isNextEnabled = bluetoothEnabled && bluetoothSwitchEnabled
    }

    /// Reacts to permission changes that occur while the wizard is running. If
    /// Bluetooth permissions become invalid during the pairing step we abort
    /// the process, reset discovery state, and return the user to the
    /// Permissions screen.
    private func handlePermissionChange() {
        let permissionsOK = isBluetoothPermissionEnabled()
        guard !permissionsOK else { return }

        // If we are currently on the pairing step, navigate back so the user can
        // re-grant permissions.
        if currentStep == .connectingBluetooth {
            resetDiscoveryState()
            if let permissionIndex = steps.firstIndex(of: .permissions) {
                currentStepIndex = permissionIndex
            }
        }
    }
} 
