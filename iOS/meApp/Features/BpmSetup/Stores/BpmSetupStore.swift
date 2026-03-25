// swiftlint:disable file_length
///
///  BpmSetupStore.swift
///  meApp
///

import Combine
import Foundation
import SwiftUI

/// Store responsible for orchestrating the BPM (Blood Pressure Monitor) multi-step setup flow.
@MainActor
// swiftlint:disable:next type_body_length
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

    private(set) var bpmItem: ScaleItemInfo?
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

    @Published private(set) var steps: [BpmSetupStep] = BpmSetupStep.defaultSteps

    @Published var isNextEnabled: Bool = true
    @Published var connectionState: ConnectionState = .loading
    @Published var isReadingSynced: Bool = false

    @Published var selectedSku: String? {
        didSet { updateNextEnabled() }
    }

    @Published var selectedUserNumber: Int? {
        didSet { updateNextEnabled() }
    }

    @Published var deviceNickname: String = BpmSetupStrings.Nickname.defaultName
    @Published var focusedField: FocusField?

    var isBackDisabled: Bool {
        currentStepIndex == 0 || currentStep == .complete
    }

    /// Whether the current SKU belongs to an A6 monitor (affects only asset resolution).
    var isA6Flow: Bool {
        guard let sku = bpmItem?.sku ?? selectedSku else { return false }
        return a6BpmSkus.contains(sku)
    }

    private let tag = "BpmSetupStore"
    private let scanTimeoutNs: UInt64
    private let stepTransitionDelayNs: UInt64

    // MARK: - Step Views
    var stepViews: [AnyView] {
        guard let bpmItem else { return [] }

        return steps.map { step in
            switch step {
            case .selectModel:
                return AnyView(
                    A3BpmModelSelectionView(
                        models: BPMS,
                        selectedSku: selectedSku,
                        onSelect: { [weak self] sku in
                            self?.selectedSku = sku
                        }
                    )
                )

            case .intro:
                return AnyView(ScaleSetupIntroView(scale: bpmItem))

            case .btPermission:
                return AnyView(PermissionListView(setupType: .bpm))

            case .selectUser:
                return AnyView(
                    A3BpmUserSelectionView(
                        sku: bpmItem.sku,
                        selectedUser: Binding(
                            get: { [weak self] in self?.selectedUserNumber },
                            set: { [weak self] in self?.selectedUserNumber = $0 }
                        ),
                        onSelect: { [weak self] user in
                            self?.selectedUserNumber = user
                        }
                    )
                )

            case .setUser:
                return AnyView(
                    A3BpmInstructionView(
                        title: BpmSetupStrings.SetUser.title(selectedUserNumber ?? 1),
                        description: BpmSetupStrings.SetUser.description,
                        imagePath: bpmItem.imgPath,
                        gifName: userGifName(for: bpmItem.sku, selectedUserNumber: selectedUserNumber),
                        gifSubdirectory: gifSubdirectory(for: bpmItem.sku),
                        resourceImageName: imageName(for: .setUser, sku: bpmItem.sku),
                        resourceImageSubdirectory: gifSubdirectory(for: bpmItem.sku),
                        mediaLayout: .bottom,
                        mediaHorizontalPadding: 0
                    )
                )
            case .confirmUser:
                return AnyView(
                    A3BpmInstructionView(
                        title: BpmSetupStrings.ConfirmUser.title,
                        description: BpmSetupStrings.ConfirmUser.description,
                        imagePath: bpmItem.imgPath,
                        resourceImageName: imageName(for: .confirmUser, sku: bpmItem.sku),
                        resourceImageSubdirectory: gifSubdirectory(for: bpmItem.sku),
                        mediaLayout: .top,
                        mediaHorizontalPadding: 0
                    )
                )
            case .prePairing:
                return AnyView(
                    A3BpmInstructionView(
                        title: BpmSetupStrings.PrePairing.title,
                        description: BpmSetupStrings.PrePairing.description,
                        imagePath: bpmItem.imgPath,
                        gifName: gifName(for: .prePairing, sku: bpmItem.sku),
                        gifSubdirectory: gifSubdirectory(for: bpmItem.sku),
                        mediaLayout: .top,
                        mediaHorizontalPadding: 0
                    )
                )
            case .scanning:
                return AnyView(
                    A3BpmScanningView(
                        connectionState: connectionState,
                        bpmItem: bpmItem,
                        onTryAgain: { [weak self] in self?.retryScanning() },
                        onSupport: { [weak self] in self?.showHelpModal() }
                    )
                )

            case .nickname:
                return AnyView(
                    A3BpmNicknameView(
                        nickname: Binding(
                            get: { [weak self] in self?.deviceNickname ?? "" },
                            set: { [weak self] in self?.deviceNickname = $0 }
                        ),
                        focusedField: Binding(
                            get: { [weak self] in self?.focusedField },
                            set: { [weak self] in self?.focusedField = $0 }
                        )
                    )
                )

            case .paired:
                return AnyView(
                    A3BpmPairedView(onLearnHowToMeasure: { [weak self] in
                        self?.moveToMeasurementTutorial()
                    })
                )

            case .measureSetup:
                return AnyView(
                    A3BpmInstructionView(
                        title: BpmSetupStrings.MeasureSetup.title,
                        description: BpmSetupStrings.MeasureSetup.description,
                        imagePath: bpmItem.imgPath,
                        gifName: gifName(for: .measureSetup, sku: bpmItem.sku),
                        gifSubdirectory: gifSubdirectory(for: bpmItem.sku),
                        contentHorizontalPadding: 0,
                        mediaHorizontalPadding: 0,
                        wrapsMediaInCard: false
                    )
                )
            case .measureStart:
                return AnyView(
                    A3BpmInstructionView(
                        title: BpmSetupStrings.MeasureStart.title,
                        description: BpmSetupStrings.MeasureStart.description,
                        imagePath: bpmItem.imgPath,
                        gifName: gifName(for: .measureStart, sku: bpmItem.sku),
                        gifSubdirectory: gifSubdirectory(for: bpmItem.sku),
                        contentHorizontalPadding: 0,
                        mediaHorizontalPadding: 0,
                        wrapsMediaInCard: false
                    )
                )

            case .complete:
                return AnyView(
                    ScaleSetupFinishView(
                        title: BpmSetupStrings.Complete.title,
                        description: BpmSetupStrings.Complete.description
                    )
                )
            }
        }
    }

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
            if let index = steps.firstIndex(of: .selectUser) {
                currentStepIndex = index
            }
            return
        case .nickname:
            Task { @MainActor in
                await saveAndAdvanceFromNickname()
            }
            return
        case .paired, .complete:
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
        let primarySku = primaryBpmSetupSku(for: sku)
        let resolved = BPMS.first { $0.sku == primarySku } ?? BPMS.first
        self.bpmItem = resolved
        self.selectedSku = primarySku
        self.deviceNickname = BpmSetupStrings.Nickname.defaultName
        resetDiscoveryState()
        bluetoothService.isSetupInProgress = true

        if bpmSkus.contains(sku) {
            self.steps = BpmSetupStep.preSelectedSteps
            self.currentStepIndex = 0
        }
    }

    // MARK: - Public Retry
    func retryScanning() {
        startScanning()
    }

    func moveToMeasurementTutorial() {
        guard let measureSetupIndex = steps.firstIndex(of: .measureSetup) else { return }
        currentStepIndex = measureSetupIndex
    }

    // MARK: - Exit / Help
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(skuToNavigate: bpmItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }

    func handleExit() {
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
        case .scanning:
            startScanning()
        case .measureSetup:
            setupBpmReadingSubscription()
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

        LoggerService.shared.log(level: .info, tag: tag, message: "BPM device discovered, starting pairing")

        Task { @MainActor in
            await self.startPairing()
        }
    }

    // MARK: - Pairing Logic
    private func startPairing() async {
        guard let device = discoveredDevice else {
            LoggerService.shared.log(level: .error, tag: tag, message: "startPairing - no discovered device")
            connectionState = .failure
            return
        }

        let broadcastId = device.broadcastIdString ?? ""

        let result = await bluetoothService.connectBpm(broadcastId: broadcastId)
        switch result {
        case .success:
            connectionState = .success
            LoggerService.shared.log(level: .info, tag: tag, message: "BPM device paired successfully")

            Task { @MainActor in
                try? await Task.sleep(nanoseconds: stepTransitionDelayNs)
                if self.currentStep == .scanning {
                    self.moveToNextStep()
                }
            }
        case .failure(let error):
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "BPM pairing failed: \(error.localizedDescription)"
            )
            connectionState = .failure
        }
    }

    // MARK: - Device Persistence
    private func saveDevice() async -> Bool {
        guard !isDeviceSaved else { return true }
        guard let device = discoveredDevice, let bpmItem else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveDevice - missing device or bpmItem")
            return false
        }
        guard let accountId = accountService.activeAccount?.accountId else {
            LoggerService.shared.log(level: .error, tag: tag, message: "No active account for BPM device creation")
            return false
        }

        do {
            let deviceToSave = device
            deviceToSave.id = UUID().uuidString
            deviceToSave.nickname = deviceNickname.isEmpty ? nil : deviceNickname

            _ = try await scaleService.createBluetoothScale(
                device: deviceToSave,
                sku: bpmItem.sku,
                userNumber: "\(selectedUserNumber ?? 1)",
                accountId: accountId,
                deviceMetadata: nil,
                skipDuplicateCheck: false
            )
            await scaleService.syncAllScalesWithRemote()
            isDeviceSaved = true
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            bluetoothService.isSetupInProgress = false
            LoggerService.shared.log(level: .info, tag: tag, message: "BPM device saved")
            return true
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "Failed to save BPM device: \(error.localizedDescription)"
            )
            bluetoothService.isSetupInProgress = false
            return false
        }
    }

    private func saveAndAdvanceFromNickname() async {
        // Temporary workaround:
        // skip local BPM device saving from the nickname step for now because
        // the pairing flow is not consistently carrying discovered device/bpmItem
        // context into this point, which causes repeated `saveDevice` errors.
        // Re-enable the `saveDevice()` call here once the device context issue is fixed.

        guard currentStep == .nickname else { return }
        let candidate = currentStepIndex + 1
        let nextIndex = adjustedIndex(from: candidate, direction: 1)
        guard nextIndex < steps.count else { return }
        currentStepIndex = nextIndex
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

    private func gifSubdirectory(for sku: String) -> String? {
        if a3BpmSkus.contains(sku) {
            return BpmA3MonitorSetupAssets.gifBundleSubdirectory(for: sku)
        }
        if a6BpmSkus.contains(sku) {
            return BpmA6MonitorSetupAssets.gifBundleSubdirectory(for: sku)
        }
        return nil
    }

    private func gifName(for step: BpmSetupStep, sku: String) -> String? {
        if a3BpmSkus.contains(sku) {
            switch step {
            case .prePairing:
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.memButton)
            case .measureSetup:
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.cuff)
            case .measureStart:
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.start)
            default:
                return nil
            }
        }

        if a6BpmSkus.contains(sku) {
            switch step {
            case .prePairing:
                return BpmA6MonitorSetupAssets.resourceName(BpmA6MonitorSetupAssets.ImageFile.pulse)
            case .measureSetup:
                return BpmA6MonitorSetupAssets.resourceName(BpmA6MonitorSetupAssets.ImageFile.cuff)
            case .measureStart:
                return BpmA6MonitorSetupAssets.resourceName(BpmA6MonitorSetupAssets.ImageFile.start)
            default:
                return nil
            }
        }

        return nil
    }

    private func imageName(for step: BpmSetupStep, sku: String) -> String? {
        if a3BpmSkus.contains(sku) {
            switch step {
            case .setUser:
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.setUser)
            case .confirmUser:
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.monitorStartStop)
            default:
                return nil
            }
        }
        if a6BpmSkus.contains(sku) {
            switch step {
            case .setUser:
                return BpmA6MonitorSetupAssets.resourceName(BpmA6MonitorSetupAssets.ImageFile.setUser)
            case .confirmUser:
                return BpmA6MonitorSetupAssets.resourceName(BpmA6MonitorSetupAssets.ImageFile.monitorStartStop)
            default:
                return nil
            }
        }
        return nil
    }

    private func userGifName(for sku: String, selectedUserNumber: Int?) -> String? {
        guard let selectedUserNumber else { return nil }
        if a3BpmSkus.contains(sku) {
            return BpmA3MonitorSetupAssets.userGifName(sku: sku, slot: selectedUserNumber)
        }
        if a6BpmSkus.contains(sku) {
            return BpmA6MonitorSetupAssets.userGifName(slot: selectedUserNumber)
        }
        return nil
    }

    private func isLocationPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.LOCATION) == .ENABLED &&
        permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED
    }

    private func isPermissionStepSatisfied() -> Bool {
        isBluetoothPermissionEnabled() && isLocationPermissionEnabled()
    }

    // swiftlint:disable:next cyclomatic_complexity
    private func updateNextEnabled() {
        switch currentStep {
        case .selectModel:
            isNextEnabled = selectedSku != nil
        case .btPermission:
            let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
            let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
            let locationEnabled = permissionsService.getPermissionState(.LOCATION) == .ENABLED
            let locationSwitchEnabled = permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED

            if !bluetoothEnabled {
                Task { await permissionsService.handlePermission(.bluetooth) }
            } else if !bluetoothSwitchEnabled {
                Task { await permissionsService.handlePermission(.bluetoothSwitch) }
            } else if !locationEnabled {
                Task { await permissionsService.handlePermission(.location) }
            } else if !locationSwitchEnabled {
                Task { await permissionsService.handlePermission(.locationSwitch) }
            }

            isNextEnabled = isPermissionStepSatisfied()
        case .selectUser:
            isNextEnabled = selectedUserNumber != nil
        case .scanning:
            // Temporary UI-review override:
            // isNextEnabled = connectionState == .success
            isNextEnabled = true
        case .nickname:
            isNextEnabled = !deviceNickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .measureStart:
            // Temporary UI-review override:
            // isNextEnabled = isReadingSynced
            isNextEnabled = true
        default:
            isNextEnabled = true
        }
    }

    private func handlePermissionChange() {
        let permissionsOK = isPermissionStepSatisfied()

        if !permissionsOK {
            connectionState = .loading
            let skipSteps: Set<BpmSetupStep> = [.selectModel, .intro, .paired, .complete]
            if !skipSteps.contains(currentStep) {
                resetDiscoveryState()
                if let permissionIndex = steps.firstIndex(of: .btPermission) {
                    currentStepIndex = permissionIndex
                }
            }
        }
    }

    /// Skips the permissions page when all required setup permissions are already granted.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .btPermission,
              isPermissionStepSatisfied() {
            idx += direction
        }
        return idx
    }

    private func setScanFailure() {
        connectionState = .failure
        resetDiscoveryState()
    }

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
// swiftlint:enable file_length
