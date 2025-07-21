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
    @Injector private var scaleService: ScaleService
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private var deviceDiscoveryCancellable: AnyCancellable? = nil
    private var stepTimerTask: Task<Void, Never>? = nil
    private var newEntrySubscription: AnyCancellable? = nil
    
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
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    /// Flag that indicates if the scale is currently synced the first entry.
    @Published var isEntrySynced: Bool = false
    
    /// Computed helper mirroring the `backDisabled()` logic from the legacy Angular flow.
    var isBackDisabled: Bool {
        switch currentStep {
        case .intro:
            return true // Same as scaleInfo in Angular
        case .stepOn:
            return isEntrySynced
        case .setupFinished:
            return true
        case .setUser:
            return discoveredScale != nil
        default:
            return false
        }
    }
    
    /// Selected user number (1-8) captured from SelectUser step.
    @Published var selectedUserNumber: Int? = nil {
        didSet { updateNextEnabled() }
    }
    @Published var bluetoothConnectionState: ConnectionState = .loading
    @Published var scaleToDelete: Device? = nil
    
    private let tag = "BluetoothScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let alertLang = AlertStrings.self
    private let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    private let loaderLang = LoaderStrings.self
    
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
                    ConnectingBluetoothView(sku: scaleItem.sku, connectionState: bluetoothConnectionState) {
                        self.pair()
                    }
                )
            case .setUser:
                return AnyView(
                    SetUserNumberView(sku: scaleItem.sku, userNumber: selectedUserNumber ?? 0)
                )
            case .stepOn:
                return AnyView(
                    BtSetupStepOnView(isEntrySynced: isEntrySynced)
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
    // MARK: - Navigation Helpers
    func moveToNextStep() {
        // Special handling when moving from permissions step
        
        switch currentStep {
        case .permissions:
            let targetStep: BluetoothScaleSetupStep = (bluetoothConnectionState == .success) ? .stepOn : .selectUser
            if let index = steps.firstIndex(of: targetStep) {
                currentStepIndex = index
            }
            return
        case .setupFinished:
            saveDiscoveredScale()
            return
        default:
            // Default behavior for other steps
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
        if currentStep == .connectingBluetooth {
            resetDiscoveryState()
        }
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
            pair()
        default:
            break
        }
    }
    
    // MARK: - Pairing Logic
    private func pair() {
        bluetoothConnectionState = .loading
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
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if self.discoveredScale == nil && self.currentStep == .connectingBluetooth {
                    self.setConnectionFailure()
                }
            }
        }
    }
    
    private func confirmPair() async {
        guard let scale = discoveredScale, discoveryEvent != nil else {
            LoggerService.shared.log(level: .error, tag: tag, message: "confirmPair - missing discovery event or scale")
            setConnectionFailure()
            return
        }
        
        guard let userNumber = self.selectedUserNumber else {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to obtain scale token")
            bluetoothConnectionState = .failure
            resetDiscoveryState()
            return
        }
        // Call confirmSmartPair
        let pairResult = await bluetoothService.confirmSmartPair(
            device: scale,
            token: "",
            displayName: "",
            userNumber: userNumber
        )
        switch pairResult {
        case .success(let response):
            switch response {
            case .creationCompleted:
                let response = await self.bluetoothService.getDeviceInfo(for: scale)
                switch response {
                case .success(let deviceInfo):
                    discoveredScale?.broadcastId = bluetoothService.convertHexToInt(deviceInfo.broadcastId ?? "")
                    discoveredScale?.broadcastIdString = deviceInfo.broadcastId ?? ""
                    discoveredScale?.password = bluetoothService.convertHexToInt(deviceInfo.password ?? "")
                    discoveredScale?.peripheralIdentifier = deviceInfo.serialNumber
                    discoveredScale?.userNumber = "\(selectedUserNumber ?? 0)"
                    discoveredScale?.mac = deviceInfo.macAddress
                    let scaleToDelete = scaleService.scales.first(where: { $0.peripheralIdentifier == discoveredScale?.peripheralIdentifier })
                    if scaleToDelete != nil {
                        self.scaleToDelete = scaleToDelete
                        handleDuplicateScale()
                    } else {
                        startEntrySyncing()
                    }
                    LoggerService.shared.log(level: .info, tag: tag, message: "Creation Completed \(response)")
                    break
                case .failure(let error):
                    setConnectionFailure()
                    LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
                }
                break
            default:
                setConnectionFailure()
                LoggerService.shared.log(level: .error, tag: tag, message: "Unexpected pairing response: \(response)")
                break
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to pair scale: \(error.localizedDescription)")
            setConnectionFailure()
        }
    }
    
    private func handleDuplicateScale() {
        let lang = alertLang.DeviceAlreadyPairedAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message(scaleItem?.sku ?? ""),
            buttons: [
                AlertButtonModel(title: lang.returnButton, type: .secondary) { _ in
                    Task {
                        await self.handleDuplicateScaleReturn()
                    }
                },
                AlertButtonModel(title: lang.pairButton, type: .primary) { _ in
                    self.startEntrySyncing()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    private func handleDuplicateScaleReturn() async {
        guard let scaleToDelete = scaleToDelete,
              let discoveredScale = discoveredScale,
              let selectedUserNumber = selectedUserNumber else {
            dismissAction?()
            return
        }
        // Check if user numbers match (same as TypeScript logic)
        if Int(scaleToDelete.userNumber ?? "0") == selectedUserNumber {
            LoggerService.shared.log(level: .info, tag: tag, message: "User numbers match, assigning old nickname and saving scale")
            // Assign the old scale's nickname to the discovered scale
            discoveredScale.nickname = scaleToDelete.nickname
            
            // Save the scale with conditional loader message
            await saveDiscoveredScaleWithLoader(isExiting: true)
        } else {
            // Different user numbers, just exit
            dismissAction?()
        }
    }
    
    private func startEntrySyncing() {
        self.bluetoothConnectionState = .success
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            self.moveToNextStep()
            // Sync the newly paired scale and start listening for entries
            Task {
                await self.syncNewScaleAndListenForEntries()
            }
        }
    }
    
    private func syncNewScaleAndListenForEntries() async {
        guard discoveredScale != nil else {
            LoggerService.shared.log(level: .error, tag: tag, message: "No discovered scale to sync")
            return
        }
        
        // Resume smart scan
        bluetoothService.resumeSmartScan(clearOnlyPairing: false)
        
        // Sync the newly paired device with BluetoothService
        await syncNewScale()
        
        // Set up subscription to listen for new entries
        setupNewEntrySubscription()
    }
    
    private func syncNewScale() async {
        guard let scale = discoveredScale else { return }
        
        // Create array with the single scale to sync
        let scalesToSync = [scale]
        bluetoothService.syncDevices(scalesToSync)
        
        LoggerService.shared.log(level: .info, tag: tag, message: "Synced new scale for entry receiving")
    }
    
    private func setupNewEntrySubscription() {
        // Cancel any existing subscription
        newEntrySubscription?.cancel()
        
        // Subscribe to new entry events
        newEntrySubscription = bluetoothService.newEntryReceivedPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] entry in
                guard let self = self else { return }
                LoggerService.shared.log(level: .info, tag: self.tag, message: "New entry received, marking as synced")
                
                // Mark entry as synced and update UI
                self.isEntrySynced = true
                self.updateNextEnabled()
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    self.moveToNextStep()
                }
                self.cleanupEntrySubscription()
            }
    }
    
    private func cleanupEntrySubscription() {
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
    }
    
    private func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        // Only handle during connecting step & for bluetooth scales
        guard currentStep == .connectingBluetooth else { return }
        guard event.deviceInfo.setupType == .bluetooth else { return }
        
        deviceDiscoveryCancellable?.cancel()
        stepTimerTask?.cancel()
        
        self.discoveredScale = event.device
        self.discoveryEvent = event
        guard discoveredScale != nil && discoveryEvent != nil else {
            return
        }
        Task {
            await confirmPair()
        }
    }
    
    private func saveDiscoveredScale() {
        Task {
            await saveDiscoveredScaleWithLoader(isExiting: false)
        }
    }
    
    private func saveDiscoveredScaleWithLoader(isExiting: Bool) async {
        guard let discoveryEvent, let device = discoveredScale else { return }
        
        // Show appropriate loader message
        let loaderText = isExiting ? loaderLang.exiting : loaderLang.savingScale
        notificationService.showLoader(LoaderModel(text: loaderText))
        
        // Delete the existing scale if there's a duplicate (matching the Angular logic)
        if let scaleToDelete = scaleToDelete {
            LoggerService.shared.log(level: .info, tag: tag, message: "Deleting existing scale with ID: \(scaleToDelete.id)")
            do {
                let existingDevices = try await self.scaleService.getDevices()
                if let oldDevice = existingDevices.first(where: { $0.id == scaleToDelete.id }) {
                    try await self.scaleService.deleteDevice(oldDevice.id, showToast: false)
                    // Explicitly refresh the scales list after deletion to ensure consistency
                    _ = try await self.scaleService.getDevices()
                    await self.scaleService.syncAllScalesWithRemote()
                }
            } catch {
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to delete existing scale: \(error.localizedDescription)")
            }
        }
        // Prepare the device for saving
        let deviceToSave = device
        deviceToSave.id = UUID().uuidString
        deviceToSave.sku = scaleItem?.sku ?? discoveryEvent.device.sku
        deviceToSave.deviceType = DeviceType.scale.rawValue
        deviceToSave.bathScale = deviceToSave.bathScale ?? BathScale(
            scaleType: ScaleSourceType.bluetooth.rawValue,
            bodyComp: deviceToSave.bathScale?.bodyComp ?? false
        )
        // Save the new scale
        let result = await bluetoothService.addNewDevice(deviceToSave, metaData: nil)
        await self.scaleService.syncAllScalesWithRemote()
        switch result {
        case .success(let savedScale):
            LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully with ID: \(savedScale.id)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
        }
        notificationService.dismissLoader()
        dismissAction?()
    }
    
    // MARK: - Helpers
    private func resetDiscoveryState() {
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        cleanupEntrySubscription()
        discoveredScale = nil
        discoveryEvent = nil
        scaleToDelete = nil
    }
    
    private func isBluetoothPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }
    
    private func updateNextEnabled() {
        switch currentStep {
        case .permissions:
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
        case .selectUser:
            // Enable next button only when user number is selected
            isNextEnabled = selectedUserNumber != nil
        case .connectingBluetooth:
            // Enable next button only when connection is successful (paired)
            isNextEnabled = bluetoothConnectionState == .success
        case .stepOn:
            // Enable next button only when entry sync is complete
            isNextEnabled = isEntrySynced
            
        default:
            // For all other steps, enable the next button
            isNextEnabled = true
        }
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
        if ![.intro, .setupFinished].contains(currentStep) {
            resetDiscoveryState()
            if let permissionIndex = steps.firstIndex(of: .permissions) {
                currentStepIndex = permissionIndex
            }
        }
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
    
    /// Sets the connection state to failure and resets discovery state.
    private func setConnectionFailure() {
        self.bluetoothConnectionState = .failure
        self.resetDiscoveryState()
    }
}
