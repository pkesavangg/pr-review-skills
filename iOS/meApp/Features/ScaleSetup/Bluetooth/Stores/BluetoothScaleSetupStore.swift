///
///  BluetoothScaleSetupStore.swift
///  meApp
///
///  Created by Cursor AI on 18/07/25.
///

import Combine
// This file intentionally aggregates Bluetooth scale setup orchestration logic.
// Breaking it into smaller files would fragment the multi-step flow management.
import Foundation
import SwiftUI

/// Store responsible for orchestrating the Bluetooth (A3) scale-setup multi-step flow.
@MainActor
final class BluetoothScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var permissionsService: PermissionsServiceProtocol
    @Injector private var bluetoothService: BluetoothServiceProtocol
    @Injector private var scaleService: ScaleServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private var deviceDiscoveryCancellable: AnyCancellable?
    private var stepTimerTask: Task<Void, Never>?
    private var newEntrySubscription: AnyCancellable?
    
    private var scaleItem: ScaleItemInfo?
    private var discoveredScale: Device?
    private var discoveryEvent: DeviceDiscoveryEvent?
    /// Flag to track if scale has been saved to prevent duplicate saves
    private var isScaleSaved: Bool = false
    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?
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
    
    /// Computed helper mirroring the `backDisabled()` logic
    var isBackDisabled: Bool {
        switch currentStep {
        case .intro:
            return true
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
    @Published var selectedUserNumber: Int? {
        didSet { updateNextEnabled() }
    }
    @Published var bluetoothConnectionState: ConnectionState = .loading
    @Published var scaleToDelete: Device?
    
    private let tag = "BluetoothScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let pairingTimeoutNs: UInt64
    private let stepTransitionDelayNs: UInt64
    
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
                    UserNumberSelectionView(selectedNumber: selectedUserNumber) { value in
                        self.selectedUserNumber = value
                    }
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
                    ScaleSetupStepOnView(isEntrySynced: isEntrySynced)
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
    init(
        pairingTimeoutNs: UInt64 = UInt64(AppConstants.TimeoutsAndRetention.bluetoothTimeoutNs),
        stepTransitionDelayNs: UInt64 = 1_500_000_000
    ) {
        self.pairingTimeoutNs = pairingTimeoutNs
        self.stepTransitionDelayNs = stepTransitionDelayNs
        // Observe permission updates so the footer button reacts instantly.
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
        // Special handling when moving from permissions step
        
        switch currentStep {
        case .permissions:
            // If scale is already saved and paired, navigate to stepOn
            // Otherwise, navigate to selectUser
            if isScaleSaved && bluetoothConnectionState == .success {
                if let index = steps.firstIndex(of: .stepOn) {
                    currentStepIndex = index
                }
            } else {
                if let index = steps.firstIndex(of: .selectUser) {
                    currentStepIndex = index
                }
            }
            return
        case .setupFinished:
            dismissAction?()
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
    func configure(with sku: String) {
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        // Pass original SKU to routes (not mapped), setup will save original SKU
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let resolved = SCALES.first { $0.sku == lookupSku } ?? SCALES.first
        self.scaleItem = resolved
        resetDiscoveryState()
        // Set setup in progress flag to prevent goal modals during setup
        bluetoothService.isSetupInProgress = true
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
        case .connectingBluetooth:
            pair()
        case .stepOn:
            // If scale is already saved, set up entry subscription
            // This handles the case where user navigates to stepOn after Bluetooth is turned back on
            if isScaleSaved, discoveredScale != nil {
                Task {
                    await syncNewScaleAndListenForEntries()
                }
            }
        default:
            break
        }
    }
    
    // MARK: - Pairing Logic
    private func pair() {
        bluetoothConnectionState = .loading
        resetDiscoveryState()
        LoggerService.shared.log(level: .info, tag: tag, message: "Bluetooth scale pairing started")
        
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
            try? await Task.sleep(nanoseconds: pairingTimeoutNs)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if self.discoveredScale == nil && self.currentStep == .connectingBluetooth {
                    self.setConnectionFailure()
                }
            }
        }
    }
    
    private func confirmPair() async { // swiftlint:disable:this function_body_length
        guard let scale = discoveredScale, discoveryEvent != nil else {
            LoggerService.shared.log(level: .error, tag: tag, message: "confirmPair - missing discovery event or scale")
            setConnectionFailure()
            return
        }
        
        guard let userNumber = self.selectedUserNumber else {
            LoggerService.shared.log(level: .error, tag: tag, message: "confirmPair - missing selected user number")
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
                let response = await self.bluetoothService.getDeviceInfo(for: scale, skipConnectionCheck: true)
                switch response {
                case .success(let deviceInfo):
                    discoveredScale?.broadcastId = bluetoothService.convertHexToInt(deviceInfo.broadcastId ?? "")
                    discoveredScale?.broadcastIdString = deviceInfo.broadcastId ?? ""
                    discoveredScale?.password = bluetoothService.convertHexToInt(deviceInfo.password ?? "")
                    discoveredScale?.peripheralIdentifier = deviceInfo.serialNumber
                    discoveredScale?.userNumber = "\(selectedUserNumber ?? 0)"
                    discoveredScale?.mac = deviceInfo.macAddress
                    let scaleToDelete = scaleService.scales.first {
                        $0.peripheralIdentifier == discoveredScale?.peripheralIdentifier
                    }
                    if scaleToDelete != nil {
                        self.scaleToDelete = scaleToDelete
                        handleDuplicateScale()
                    } else {
                        // Save scale immediately after pairing succeeds (similar to WiFi scales)
                        await saveDiscoveredScaleWithLoader(isExiting: false)
                        startEntrySyncing()
                    }
                    LoggerService.shared.log(level: .info, tag: tag, message: "Creation Completed \(response)")
                case .failure(let error):
                    setConnectionFailure()
                    LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
                }
            default:
                setConnectionFailure()
                LoggerService.shared.log(level: .error, tag: tag, message: "Unexpected pairing response: \(response)")
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
                    Task {
                        // Save scale immediately when user chooses to pair again
                        await self.saveDiscoveredScaleWithLoader(isExiting: false)
                        self.startEntrySyncing()
                    }
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
        // Check if user numbers match
        if Int(scaleToDelete.userNumber ?? "0") == selectedUserNumber {
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

        // Attach listener immediately so the first entry is never missed while UI advances.
        bluetoothService.resumeSmartScan(clearOnlyPairing: false)
        setupNewEntrySubscription()
        Task {
            await self.syncNewScale()
        }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: stepTransitionDelayNs)
            if self.isEntrySynced {
                self.promoteToStepOnIfPossible()
            } else if self.currentStep == .connectingBluetooth {
                self.moveToNextStep()
            }
        }
    }
    
    private func syncNewScaleAndListenForEntries() async {
        guard discoveredScale != nil else {
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
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "syncNewScale - missing discovered scale")
            return
        }
        
        // Create array with the single scale to sync
        let scalesToSync = [scale]
        bluetoothService.syncDevices(scalesToSync)
    }
    
    private func setupNewEntrySubscription() {
        // Cancel any existing subscription
        newEntrySubscription?.cancel()

        // Subscribe to new entry events (uses EntryNotification for safe cross-actor data passing)
        newEntrySubscription = bluetoothService.newEntryReceivedPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                Task { @MainActor in
                    // Mark entry as synced and update UI.
                    self.isEntrySynced = true
                    self.updateNextEnabled()
                    self.promoteToStepOnIfPossible()
                    self.cleanupEntrySubscription()
                }
            }
    }

    private func promoteToStepOnIfPossible() {
        guard let stepOnIndex = steps.firstIndex(of: .stepOn),
              currentStepIndex <= stepOnIndex else {
            return
        }
        currentStepIndex = stepOnIndex
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
    
    private func saveDiscoveredScaleWithLoader(isExiting: Bool) async { // swiftlint:disable:this function_body_length
        // Prevent duplicate saves
        if isScaleSaved {
            return
        }
        
        guard let discoveryEvent, let device = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveDiscoveredScale - missing discovery event or discovered scale")
            return
        }
        
        // Show appropriate loader message
        let loaderText = isExiting ? loaderLang.exiting : loaderLang.savingScale
        notificationService.showLoader(LoaderModel(text: loaderText))
        
        // Delete the existing scale if there's a duplicate
        if let scaleToDelete = scaleToDelete {
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
        
        // Get device metadata for Bluetooth scales (matching BluetoothService.addNewDevice logic)
        var deviceMetadata: DeviceMetaData?
        let deviceInfoResult = await bluetoothService.getDeviceInfo(for: deviceToSave, skipConnectionCheck: true)
        switch deviceInfoResult {
        case .success(let deviceInfo):
            let dto = ScaleMetaDataDTO(
                firmwareRevision: deviceInfo.firmwareRevision?.replacingOccurrences(of: "\0", with: ""),
                hardwareRevision: deviceInfo.hardwareRevision?.replacingOccurrences(of: "\0", with: ""),
                latestFirmwareVersion: nil,
                manufacturerName: deviceInfo.manufacturerName?.replacingOccurrences(of: "\0", with: ""),
                modelNumber: deviceInfo.modelNumber?.replacingOccurrences(of: "\0", with: ""),
                serialNumber: deviceInfo.serialNumber?.replacingOccurrences(of: "\0", with: ""),
                softwareRevision: deviceInfo.softwareRevision?.replacingOccurrences(of: "\0", with: ""),
                systemId: deviceInfo.systemID?.replacingOccurrences(of: "\0", with: ""),
                wifiMac: ""
            )
            deviceMetadata = DeviceMetaData(from: dto)
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
        }
        
        // Use the proper service method to create the Bluetooth scale with correct relationship handling
        do {
            // Get account ID from account service
            guard let accountId = accountService.activeAccount?.accountId else {
                LoggerService.shared.log(level: .error, tag: tag, message: "No active account found for scale creation")
                return
            }
            
            let savedScale = try await scaleService.createBluetoothScale(
                device: deviceToSave,
                sku: scaleItem?.sku ?? discoveryEvent.device.sku,
                userNumber: deviceToSave.userNumber ?? "1",
                accountId: accountId,
                deviceMetadata: deviceMetadata,
                skipDuplicateCheck: false
            )
            
            await self.scaleService.syncAllScalesWithRemote()
            
            isScaleSaved = true
            LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully with ID: \(savedScale.id)")
            
            // Post notification that scale was added
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            
            // Clear setup in progress flag after scale is saved
            bluetoothService.isSetupInProgress = false
            
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
            // Clear setup in progress flag even on error
            bluetoothService.isSetupInProgress = false
        }
        notificationService.dismissLoader()
        // Only dismiss the screen if we're exiting, not when saving immediately after pairing
        if isExiting {
            dismissAction?()
        }
    }
    
    // MARK: - Helpers
    private func resetDiscoveryState() {
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        cleanupEntrySubscription()
        // Preserve discoveredScale if scale is already saved, as we need it for entry subscription
        if !isScaleSaved {
            discoveredScale = nil
            discoveryEvent = nil
        }
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
        
        if !permissionsOK {
            // Reset connection state when permissions are lost to prevent incorrect navigation
            // This ensures that when permissions are restored, we don't incorrectly navigate to stepOn
            bluetoothConnectionState = .loading
            
            // If we are currently on the pairing step, navigate back so the user can
            // re-grant permissions.
            if ![.intro, .setupFinished].contains(currentStep) {
                resetDiscoveryState()
                if let permissionIndex = steps.firstIndex(of: .permissions) {
                    currentStepIndex = permissionIndex
                }
            }
        } else {
            // When permissions are restored, if scale is already saved, restore connection state
            // This allows proper navigation to stepOn when user taps NEXT from permissions
            if isScaleSaved && discoveredScale != nil {
                bluetoothConnectionState = .success
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
    
    /// Cleans up all subscriptions and resources when the view disappears
    func cleanUp() {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        stepTimerTask = nil
        cleanupEntrySubscription()
        resetDiscoveryState()
        // Re-apply skipped devices to BLE SDK, excluding paired scales
        bluetoothService.reapplySkipDevicesExcludingPaired()
        // Clear setup in progress flag when setup is dismissed
        bluetoothService.isSetupInProgress = false
        // Reset saved flag
        isScaleSaved = false
    }
} // swiftlint:disable:this file_length

#if DEBUG
extension BluetoothScaleSetupStore {
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
        discoveredScale: Device? = nil,
        discoveryEvent: DeviceDiscoveryEvent? = nil,
        isScaleSaved: Bool? = nil,
        scaleToDelete: Device? = nil
    ) {
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        if let isScaleSaved {
            self.isScaleSaved = isScaleSaved
        }
        self.scaleToDelete = scaleToDelete
    }

    @MainActor
    func testConfirmPair() async {
        await confirmPair()
    }

    @MainActor
    func testSyncNewScaleAndListenForEntries() async {
        await syncNewScaleAndListenForEntries()
    }

    @MainActor
    func testSyncNewScale() async {
        await syncNewScale()
    }

    @MainActor
    func testSaveDiscoveredScale() {
        saveDiscoveredScale()
    }

    @MainActor
    func testHandleDuplicateScaleReturn() async {
        await handleDuplicateScaleReturn()
    }
}
#endif
