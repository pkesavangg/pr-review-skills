///
///  BpmSetupStore.swift
///  meApp
///

import Combine
import Foundation
import SwiftUI

/// Store responsible for orchestrating the BPM (Blood Pressure Monitor) multi-step setup flow.
@MainActor
final class BpmSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var permissionsService: PermissionsServiceProtocol
    @Injector private var bluetoothService: BluetoothServiceProtocol
    @Injector private var scaleService: ScaleServiceProtocol
    @Injector private var accountService: AccountServiceProtocol

    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private var deviceDiscoveryCancellable: AnyCancellable?
    private var scanTimerTask: Task<Void, Never>?
    private var bpmReadingSubscription: AnyCancellable?

    private var bpmItem: ScaleItemInfo?
    private var discoveredDevice: Device?
    private var discoveryEvent: DeviceDiscoveryEvent?
    private var isDeviceSaved: Bool = false

    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?

    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }

    @Published private(set) var currentStep: BpmSetupStep = .selectModel {
        didSet { handleStepChange() }
    }

    @Published private(set) var steps: [BpmSetupStep] = BpmSetupStep.allCases

    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true

    /// Connection state for the scanning/pairing UI.
    @Published var connectionState: ConnectionState = .loading

    /// Flag that indicates whether a BPM reading was received after pairing.
    @Published var isReadingSynced: Bool = false

    /// Selected BPM SKU from the model selection step.
    @Published var selectedSku: String? {
        didSet { updateNextEnabled() }
    }

    var isBackDisabled: Bool {
        switch currentStep {
        case .selectModel:
            return true
        case .success:
            return true
        default:
            return false
        }
    }

    private let tag = "BpmSetupStore"
    private let scanTimeoutNs: UInt64
    private let stepTransitionDelayNs: UInt64

    // MARK: - Init
    init(
        scanTimeoutNs: UInt64 = 15_000_000_000,
        stepTransitionDelayNs: UInt64 = 1_500_000_000
    ) {
        self.scanTimeoutNs = scanTimeoutNs
        self.stepTransitionDelayNs = stepTransitionDelayNs

        permissionsService.permissionsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
                self?.handlePermissionChange()
            }
            .store(in: &cancellables)
    }

    // MARK: - Navigation Helpers
    func moveToNextStep() {
        switch currentStep {
        case .btPermission:
            if let index = steps.firstIndex(of: .scanning) {
                currentStepIndex = index
            }
            return
        case .success:
            dismissAction?()
            return
        default:
            let candidate = currentStepIndex + 1
            let nextIndex = adjustedIndex(from: candidate, direction: 1)
            guard nextIndex < steps.count else { return }
            currentStepIndex = nextIndex
        }
    }

    func moveToPreviousStep() {
        let candidate = currentStepIndex - 1
        let previousIndex = adjustedIndex(from: candidate, direction: -1)
        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
        if currentStep == .scanning {
            resetDiscoveryState()
        }
    }

    // MARK: - Public Configuration
    func configure(with sku: String) {
        let resolved = BPMS.first { $0.sku == sku } ?? BPMS.first
        self.bpmItem = resolved
        self.selectedSku = sku
        resetDiscoveryState()
        bluetoothService.isSetupInProgress = true
    }

    // MARK: - Exit / Help
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(skuToNavigate: bpmItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }

    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        let alertLang = AlertStrings.ExitSetupAlert.self

        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { [weak self] _ in
                    guard let self = self else { return }
                    self.dismissAction?()
                },
                AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Step Transition Handling
    private func handleStepChange() {
        switch currentStep {
        case .scanning:
            startScanning()
        default:
            break
        }
    }

    // MARK: - Scanning Logic
    private func startScanning() {
        connectionState = .loading
        resetDiscoveryState()
        LoggerService.shared.log(level: .info, tag: tag, message: "BPM device scanning started")

        bluetoothService.scanForBpm()

        deviceDiscoveryCancellable = bluetoothService.deviceDiscoveredPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                self?.handleDeviceDiscovery(event)
            }

        scanTimerTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: scanTimeoutNs)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if self.discoveredDevice == nil && self.currentStep == .scanning {
                    self.setScanFailure()
                }
            }
        }
    }

    private func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        guard currentStep == .scanning else { return }
        guard event.deviceInfo.setupType == .bpm else { return }

        deviceDiscoveryCancellable?.cancel()
        scanTimerTask?.cancel()

        self.discoveredDevice = event.device
        self.discoveryEvent = event
        self.connectionState = .success

        LoggerService.shared.log(level: .info, tag: tag, message: "BPM device discovered")

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: stepTransitionDelayNs)
            if self.currentStep == .scanning {
                self.moveToNextStep()
            }
        }
    }

    // MARK: - Pairing Logic
    private func startPairing() async {
        guard let device = discoveredDevice else {
            LoggerService.shared.log(level: .error, tag: tag, message: "startPairing - no discovered device")
            connectionState = .failure
            return
        }

        connectionState = .loading
        let broadcastId = device.broadcastIdString ?? ""

        let result = await bluetoothService.connectBpm(broadcastId: broadcastId)
        switch result {
        case .success:
            connectionState = .success
            LoggerService.shared.log(level: .info, tag: tag, message: "BPM device paired successfully")
            await saveDevice()
            setupBpmReadingSubscription()

            Task { @MainActor in
                try? await Task.sleep(nanoseconds: stepTransitionDelayNs)
                if self.currentStep == .pairing {
                    self.moveToNextStep()
                }
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "BPM pairing failed: \(error.localizedDescription)")
            connectionState = .failure
        }
    }

    // MARK: - Device Persistence
    private func saveDevice() async {
        guard !isDeviceSaved else { return }
        guard let device = discoveredDevice, let bpmItem else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveDevice - missing device or bpmItem")
            return
        }
        guard let accountId = accountService.activeAccount?.accountId else {
            LoggerService.shared.log(level: .error, tag: tag, message: "No active account for BPM device creation")
            return
        }

        do {
            let deviceToSave = device
            deviceToSave.id = UUID().uuidString

            _ = try await scaleService.createBluetoothScale(
                device: deviceToSave,
                sku: bpmItem.sku,
                userNumber: "1",
                accountId: accountId,
                deviceMetadata: nil,
                skipDuplicateCheck: false
            )
            await scaleService.syncAllScalesWithRemote()
            isDeviceSaved = true
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            bluetoothService.isSetupInProgress = false
            LoggerService.shared.log(level: .info, tag: tag, message: "BPM device saved")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save BPM device: \(error.localizedDescription)")
            bluetoothService.isSetupInProgress = false
        }
    }

    // MARK: - BPM Reading Subscription
    private func setupBpmReadingSubscription() {
        bpmReadingSubscription?.cancel()

        bpmReadingSubscription = bluetoothService.newBpmReadingReceivedPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.isReadingSynced = true
                self.updateNextEnabled()
                self.bpmReadingSubscription?.cancel()
                self.bpmReadingSubscription = nil
            }
    }

    // MARK: - Helpers
    private func resetDiscoveryState() {
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        scanTimerTask?.cancel()
        bpmReadingSubscription?.cancel()
        bpmReadingSubscription = nil
        if !isDeviceSaved {
            discoveredDevice = nil
            discoveryEvent = nil
        }
    }

    private func isBluetoothPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }

    private func updateNextEnabled() {
        switch currentStep {
        case .selectModel:
            isNextEnabled = selectedSku != nil
        case .btPermission:
            let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
            let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED

            if !bluetoothEnabled {
                Task { await permissionsService.handlePermission(.bluetooth) }
            } else if !bluetoothSwitchEnabled {
                Task { await permissionsService.handlePermission(.bluetoothSwitch) }
            }

            isNextEnabled = bluetoothEnabled && bluetoothSwitchEnabled
        case .scanning:
            isNextEnabled = connectionState == .success
        case .pairing:
            isNextEnabled = connectionState == .success
        case .connecting:
            isNextEnabled = isReadingSynced
        default:
            isNextEnabled = true
        }
    }

    private func handlePermissionChange() {
        let permissionsOK = isBluetoothPermissionEnabled()

        if !permissionsOK {
            connectionState = .loading
            if ![.selectModel, .success].contains(currentStep) {
                resetDiscoveryState()
                if let permissionIndex = steps.firstIndex(of: .btPermission) {
                    currentStepIndex = permissionIndex
                }
            }
        }
    }

    /// Skips the permissions page when Bluetooth permissions are already granted.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .btPermission,
              isBluetoothPermissionEnabled() {
            idx += direction
        }
        return idx
    }

    private func setScanFailure() {
        connectionState = .failure
        resetDiscoveryState()
    }

    /// Cleans up all subscriptions and resources when the view disappears.
    func cleanUp() {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        scanTimerTask?.cancel()
        scanTimerTask = nil
        bpmReadingSubscription?.cancel()
        bpmReadingSubscription = nil
        resetDiscoveryState()
        bluetoothService.isSetupInProgress = false
        isDeviceSaved = false
    }
}

#if DEBUG
extension BpmSetupStore {
    @MainActor
    func testWarmInjectedDependencies() {
        let injectedDependencies = (
            notificationHelper: notificationService,
            permissions: permissionsService,
            bluetooth: bluetoothService,
            scale: scaleService,
            account: accountService
        )
        _ = injectedDependencies
    }

    @MainActor
    func testSetInternalState(
        discoveredDevice: Device? = nil,
        discoveryEvent: DeviceDiscoveryEvent? = nil,
        isDeviceSaved: Bool? = nil
    ) {
        self.discoveredDevice = discoveredDevice
        self.discoveryEvent = discoveryEvent
        if let isDeviceSaved {
            self.isDeviceSaved = isDeviceSaved
        }
    }

    @MainActor
    func testStartPairing() async {
        await startPairing()
    }

    @MainActor
    func testStartScanning() {
        startScanning()
    }
}
#endif
