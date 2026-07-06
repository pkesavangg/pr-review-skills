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
    @Injector private var deviceService: PairedDeviceServiceProtocol
    @Injector private var accountService: AccountServiceProtocol

    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private var deviceDiscoveryCancellable: AnyCancellable?
    private var scanTimerTask: Task<Void, Never>?
    private var bpmReadingSubscription: AnyCancellable?

    private(set) var bpmItem: DeviceItemInfo?
    private var discoveredDevice: Device?
    private var discoveryEvent: DeviceDiscoveryEvent?
    private var isDeviceSaved: Bool = false
    private var isSaving: Bool = false
    private var deviceToDelete: DeviceSnapshot?
    private var lastRetrievedDeviceInfo: DeviceInfo?
    private var canReplaceUser: Bool = false

    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?

    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            guard currentStepIndex >= 0, currentStepIndex < steps.count else {
                currentStepIndex = max(0, steps.count - 1)
                return
            }
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
        didSet {
            updateNextEnabled()
            // When user selects a model from the grid, reconfigure steps for that SKU.
            if let sku = selectedSku, oldValue != sku {
                reconfigureStepsForSku(sku)
            }
        }
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

    /// The BLE protocol type expected for the selected SKU.
    private var expectedProtocolType: ProtocolType? {
        guard let sku = selectedSku else { return nil }
        if a6BpmSkus.contains(sku) { return .A6 }
        if a3BpmSkus.contains(sku) { return .A3 }
        return nil
    }

    private let tag = "BpmSetupStore"
    private let scanTimeoutNs: UInt64
    private let stepTransitionDelayNs: UInt64
    private let pairingRetryDelayNs: UInt64

    // MARK: - Step Views

    var stepViews: [AnyView] {
        guard let bpmItem else { return [] }
        return steps.map { buildStepView($0, bpmItem: bpmItem) }
    }

    // swiftlint:disable:next cyclomatic_complexity
    private func buildStepView(_ step: BpmSetupStep, bpmItem: DeviceItemInfo) -> AnyView {
        switch step {
        case .selectModel: return buildSelectModelView()
        case .intro: return buildIntroView(bpmItem: bpmItem)
        case .btPermission: return buildBtPermissionView()
        case .selectUser: return buildSelectUserView(bpmItem: bpmItem)
        case .powerSwitch: return buildPowerSwitchView(bpmItem: bpmItem)
        case .setUser: return buildSetUserView(bpmItem: bpmItem)
        case .confirmUser: return buildConfirmUserView(bpmItem: bpmItem)
        case .prePairing: return buildPrePairingView(bpmItem: bpmItem)
        case .scanning: return buildScanningView(bpmItem: bpmItem)
        case .nickname: return buildNicknameView()
        case .paired: return buildPairedView()
        case .measureSetup: return buildMeasureSetupView(bpmItem: bpmItem)
        case .measureStart: return buildMeasureStartView(bpmItem: bpmItem)
        case .complete: return buildCompleteView()
        }
    }

    private func buildSelectModelView() -> AnyView {
        AnyView(
            A3BpmModelSelectionView(
                models: BPMS,
                selectedSku: selectedSku
            ) { [weak self] sku in
                self?.selectedSku = sku
            }
        )
    }

    private func buildIntroView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(ScaleSetupIntroView(scale: bpmItem, troubleText: BpmSetupStrings.troubleSettingUp))
    }

    private func buildBtPermissionView() -> AnyView {
        AnyView(PermissionListView(setupType: .bpm))
    }

    private func buildSelectUserView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
            A3BpmUserSelectionView(
                bpmItem: bpmItem,
                selectedUser: Binding(
                    get: { [weak self] in self?.selectedUserNumber },
                    set: { [weak self] in self?.selectedUserNumber = $0 }
                )
            ) { [weak self] user in
                self?.selectedUserNumber = user
            }
        )
    }

    private func buildPowerSwitchView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
            A3BpmInstructionView(
                title: BpmSetupStrings.PowerSwitch.title,
                description: BpmSetupStrings.PowerSwitch.description,
                imagePath: bpmItem.imgPath,
                resourceImageName: BpmA3MonitorSetupAssets.perSkuResourceName(sku: bpmItem.sku, BpmA3MonitorSetupAssets.ImageFile.powerSwitch),
                resourceImageSubdirectory: BpmA3MonitorSetupAssets.userGifBundleSubdirectory(for: bpmItem.sku),
                mediaLayout: .top,
                mediaHorizontalPadding: 0
            )
        )
    }

    private func buildSetUserView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
            A3BpmInstructionView(
                title: BpmSetupStrings.SetUser.title(userLabel(for: selectedUserNumber ?? 1)),
                description: BpmSetupStrings.SetUser.description(for: bpmItem),
                imagePath: bpmItem.imgPath,
                gifName: userGifName(for: bpmItem.sku, selectedUserNumber: selectedUserNumber),
                gifSubdirectory: userGifSubdirectory(for: bpmItem.sku),
                resourceImageName: imageName(for: .setUser, sku: bpmItem.sku),
                resourceImageSubdirectory: gifSubdirectory(for: bpmItem.sku),
                mediaLayout: .bottom,
                mediaHorizontalPadding: 0
            )
        )
    }

    private func buildConfirmUserView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
            A3BpmInstructionView(
                title: BpmSetupStrings.ConfirmUser.title,
                description: BpmSetupStrings.ConfirmUser.description(for: bpmItem.sku),
                imagePath: bpmItem.imgPath,
                gifName: gifName(for: .confirmUser, sku: bpmItem.sku),
                gifSubdirectory: confirmUserGifSubdirectory(for: bpmItem.sku),
                resourceImageName: imageName(for: .confirmUser, sku: bpmItem.sku),
                resourceImageSubdirectory: gifSubdirectory(for: bpmItem.sku),
                mediaLayout: .top,
                mediaHorizontalPadding: 0
            )
        )
    }

    private func buildPrePairingView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
            A3BpmInstructionView(
                title: BpmSetupStrings.PrePairing.title,
                description: BpmSetupStrings.PrePairing.description,
                imagePath: bpmItem.imgPath,
                gifName: gifName(for: .prePairing, sku: bpmItem.sku),
                gifSubdirectory: confirmUserGifSubdirectory(for: bpmItem.sku),
                mediaLayout: .top,
                mediaHorizontalPadding: 0
            )
        )
    }

    private func buildScanningView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
            A3BpmScanningView(
                connectionState: connectionState,
                bpmItem: bpmItem,
                onTryAgain: { [weak self] in self?.retryScanning() },
                onSupport: { [weak self] in self?.showHelpModal() }
            )
        )
    }

    private func buildNicknameView() -> AnyView {
        AnyView(
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
    }

    private func buildPairedView() -> AnyView {
        AnyView(
            A3BpmPairedView { [weak self] in
                self?.moveToMeasurementTutorial()
            }
        )
    }

    private func buildMeasureSetupView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
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
    }

    private func buildMeasureStartView(bpmItem: DeviceItemInfo) -> AnyView {
        AnyView(
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
    }

    private func buildCompleteView() -> AnyView {
        AnyView(
            ScaleSetupFinishView(
                title: BpmSetupStrings.Complete.title,
                description: BpmSetupStrings.Complete.description
            )
        )
    }

    // MARK: - Init
    init(
        scanTimeoutNs: UInt64 = 15_000_000_000,
        stepTransitionDelayNs: UInt64 = 1_500_000_000,
        pairingRetryDelayNs: UInt64 = 1_000_000_000
    ) {
        self.scanTimeoutNs = scanTimeoutNs
        self.stepTransitionDelayNs = stepTransitionDelayNs
        self.pairingRetryDelayNs = pairingRetryDelayNs

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
            // Mark setup as finished here so deferred post-setup flows don't depend
            // solely on the view's onDisappear timing.
            bluetoothService.isSetupInProgress = false
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
    func configure(with sku: String,
                   discoveredScale: Device? = nil,
                   discoveryEvent: DeviceDiscoveryEvent? = nil) {
        let primarySku = primaryBpmSetupSku(for: sku)
        let resolved = BPMS.first { $0.sku == primarySku } ?? BPMS.first
        self.bpmItem = resolved
        self.selectedSku = primarySku
        self.deviceNickname = resolved?.productName ?? BpmSetupStrings.Nickname.defaultName
        resetDiscoveryState()
        bluetoothService.isSetupInProgress = true

        // Inject pre-discovered device context (from discovery toast → Connect).
        if let scale = discoveredScale, let event = discoveryEvent {
            self.discoveredDevice = scale
            self.discoveryEvent = event
            scale.protocolType = event.protocolType.rawValue
        }

        let preSelected = bpmSkus.contains(sku)
        self.steps = BpmSetupStep.steps(for: primarySku, preSelected: preSelected)

        // If a device was already discovered, skip directly to user selection.
        if discoveredScale != nil, discoveryEvent != nil,
           let userIndex = steps.firstIndex(of: .selectUser) {
            self.currentStepIndex = userIndex
        } else {
            self.currentStepIndex = 0
        }
    }

    /// Re-builds the step array when the user picks a different model from the grid.
    /// Preserves the current step index (model selection) so the UI doesn't jump.
    private func reconfigureStepsForSku(_ sku: String) {
        let primarySku = primaryBpmSetupSku(for: sku)
        let resolved = BPMS.first { $0.sku == primarySku } ?? BPMS.first
        self.bpmItem = resolved
        self.deviceNickname = resolved?.productName ?? BpmSetupStrings.Nickname.defaultName

        let isPreSelected = steps.first == .intro
        let newSteps = BpmSetupStep.steps(for: primarySku, preSelected: isPreSelected)

        // Only update if the steps actually changed (avoids unnecessary re-renders).
        if newSteps != steps {
            let currentStep = self.currentStep
            self.steps = newSteps
            // Stay on the same logical step after reconfiguration.
            if let idx = newSteps.firstIndex(of: currentStep) {
                self.currentStepIndex = idx
            }
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
                    self?.bluetoothService.isSetupInProgress = false
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

        // Only accept devices whose protocol matches the selected SKU (A3 vs A6).
        // Mismatched devices are silently ignored so scanning continues.
        guard event.protocolType == expectedProtocolType else {
            LoggerService.shared.log(
                level: .info,
                tag: tag,
                message: "Ignoring BPM device — protocol \(event.protocolType) does not match expected \(String(describing: expectedProtocolType))"
            )
            return
        }

        deviceDiscoveryCancellable?.cancel()
        scanTimerTask?.cancel()

        let device = event.device.toDevice()
        device.protocolType = event.protocolType.rawValue
        self.discoveredDevice = device
        self.discoveryEvent = event

        LoggerService.shared.log(level: .info, tag: tag, message: "BPM device discovered, checking for existing pairing")

        Task { @MainActor in
            await self.checkForPrePairingDuplicate()
        }
    }

    /// Checks whether the discovered device's MAC matches an already-paired device
    /// BEFORE initiating BLE pairing. Same-user duplicates show "User Already Paired";
    /// different-user duplicates on the same physical device proceed silently.
    private func checkForPrePairingDuplicate() async {
        guard let device = discoveredDevice else {
            showConnectionErrorAlert()
            return
        }

        let discoveredMac = device.mac ?? ""

        if !discoveredMac.isEmpty {
            let existingDevices = (try? await deviceService.getDevices()) ?? []

            if let existing = existingDevices.first(where: {
                guard let existingMac = $0.mac, !existingMac.isEmpty else { return false }
                return existingMac.lowercased() == discoveredMac.lowercased()
            }) {
                let isSameUser = existing.userNumber == "\(selectedUserNumber ?? 1)"
                if isSameUser {
                    LoggerService.shared.log(
                        level: .info,
                        tag: tag,
                        message: "Pre-pairing check: same-user duplicate found (MAC: \(discoveredMac)). Marking for deletion; continuing to pair."
                    )
                    // Mark for deletion — checkForDuplicateAndAdvance will show the
                    // "Already Paired" alert post-connection if pairing succeeds.
                    // Do NOT return here: fall through to the user mismatch check and
                    // startPairing() so a monitor with a different active user is caught.
                    deviceToDelete = existing
                } else {
                    LoggerService.shared.log(
                        level: .info,
                        tag: tag,
                        message: "Pre-pairing check: different-user on same device (MAC: \(discoveredMac)). Allowing multi-user pairing."
                    )
                    // Different user slot on same physical device — allowed. Fall through to pairing.
                }
            }
        }

        // Check user mismatch before connecting — if the discovered device reports
        // a different user than what the app selected, show alert without pairing.
        let deviceUserNumberStr = discoveredDevice?.userNumber
        let deviceUserNumber = deviceUserNumberStr.flatMap { Int($0) }
        if let deviceUser = deviceUserNumber,
           let expected = selectedUserNumber,
           deviceUser != expected {
            LoggerService.shared.log(
                level: .info,
                tag: tag,
                message: "User mismatch before pairing: app selected \(expected), device reports \(deviceUser)"
            )
            showUserMismatchAlert()
            return
        }

        connectionState = .success
        await startPairing()
    }

    // MARK: - Pairing Logic
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func startPairing() async {
        guard let device = discoveredDevice else {
            LoggerService.shared.log(level: .error, tag: tag, message: "startPairing - no discovered device")
            showConnectionErrorAlert()
            return
        }

        let broadcastId = device.broadcastIdString ?? ""
        let pairedSKUMonitors = deviceService.scales.filter { $0.sku == selectedSku }

        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Starting BPM pairing: \(broadcastId), user: \(String(describing: selectedUserNumber)), replaceUser: \(canReplaceUser)"
        )

        var result = await bluetoothService.connectBpm(
            broadcastId: broadcastId,
            userNumber: selectedUserNumber ?? 1,
            replaceUser: canReplaceUser,
            pairedSKUMonitors: pairedSKUMonitors
        )

        // Re-pairing the same physical monitor under a newly-selected user can fail on the
        // first attempt: the SDK still holds the previously-paired user's session, so the
        // initial connect times out / errors. That failed attempt tears the stale session
        // down, which is why a manual "Try Again" then succeeds. Do that recovery
        // automatically — retry the connect once before surfacing "Unable to Connect", so
        // the user isn't forced to reconnect. Only `.failure` (timeout / pair error) is
        // retried; the SDK's conflict responses below are legitimate and handled as-is.
        if case .failure(let error) = result {
            LoggerService.shared.log(
                level: .info,
                tag: tag,
                message: "First BPM connect attempt failed (\(error.localizedDescription)); "
                    + "retrying once after stale-session teardown for \(broadcastId)"
            )
            try? await Task.sleep(nanoseconds: pairingRetryDelayNs)
            result = await bluetoothService.connectBpm(
                broadcastId: broadcastId,
                userNumber: selectedUserNumber ?? 1,
                replaceUser: canReplaceUser,
                pairedSKUMonitors: pairedSKUMonitors
            )
        }

        switch result {
        case .success(let response):
            switch response {
            case .differentUser:
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "SDK reports different user for BPM \(broadcastId)"
                )
                if !broadcastId.isEmpty {
                    _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId, considerForSession: false)
                }
                showUserMismatchAlert()
                return

            case .deviceExistsWithSameUser:
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "SDK reports device exists with same user for BPM \(broadcastId)"
                )
                showSameUserConflictAlert()
                return

            case .deviceExistsWithDifferentUser:
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "SDK reports device exists with different user for BPM \(broadcastId)"
                )
                // Look up the existing device with same SKU but different user number,
                // so we can delete it if the user chooses to replace.
                if deviceToDelete == nil {
                    let existingDevices = (try? await deviceService.getDevices()) ?? []
                    let selectedUser = "\(selectedUserNumber ?? 1)"
                    deviceToDelete = existingDevices.first { existing in
                        existing.sku == selectedSku && existing.userNumber != selectedUser
                    }
                }
                showDifferentUserConflictAlert()
                return

            case .creationCompleted:
                connectionState = .success
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "creationCompleted for BPM \(broadcastId)"
                )

                // Fetch fresh device info after connection (matching Ionic pattern).
                // The post-connection broadcastId may differ from the discovery broadcastId
                // for A3 BPM devices, so update the device with stable identifiers.
                await updateDeviceFromPostConnectionInfo(device)

                // Fallback: check if the monitor's active user matches the app selection.
                if let deviceInfo = lastRetrievedDeviceInfo,
                   await checkForUserMismatch(deviceInfo) {
                    return
                }

                await checkForDuplicateAndAdvance(device)

            default:
                LoggerService.shared.log(
                    level: .error,
                    tag: tag,
                    message: "BPM pairing returned unexpected status: \(response.rawValue)"
                )
                showConnectionErrorAlert()
            }

        case .failure(let error):
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "BPM pairing failed: \(error.localizedDescription)"
            )
            showConnectionErrorAlert()
        }
    }

    // MARK: - Device Conflict Detection

    /// After a successful pair, checks whether a device with the same peripheralIdentifier
    /// already exists. Same-user duplicates show "User Already Paired";
    /// different-user duplicates proceed silently so both users appear in the list.
    private func checkForDuplicateAndAdvance(_ device: Device) async {
        // If pre-pairing check already identified a duplicate, use it directly.
        if let existing = deviceToDelete {
            let isSameUser = existing.userNumber == "\(selectedUserNumber ?? 1)"
            confirmUserAndPair(isDifferentUser: !isSameUser)
            return
        }

        let existingDevices = (try? await deviceService.getDevices()) ?? []
        let peripheralId = device.peripheralIdentifier ?? ""

        if !peripheralId.isEmpty,
           let existing = existingDevices.first(where: { $0.peripheralIdentifier == peripheralId }) {
            let isSameUser = existing.userNumber == "\(selectedUserNumber ?? 1)"
            if isSameUser && canReplaceUser {
                // User already confirmed the same-user conflict via showSameUserConflictAlert.
                // Showing the alert again here would be a duplicate — just mark for deletion and advance.
                deviceToDelete = existing
                advanceFromScanning()
            } else if isSameUser {
                deviceToDelete = existing
                confirmUserAndPair(isDifferentUser: false)
            } else if canReplaceUser {
                // User explicitly chose "Replace User" — mark old entry for deletion
                // so saveDevice removes it, leaving only the new user's entry.
                deviceToDelete = existing
                advanceFromScanning()
            } else {
                // Different user on same physical device — both coexist in the list.
                advanceFromScanning()
            }
        } else {
            advanceFromScanning()
        }
    }

    /// Shows a confirmation alert when the discovered device conflicts with an existing pairing.
    /// Same user → "Caution: User Already Paired" with Continue.
    /// Different user → proceeds silently (both users coexist in the monitor list).
    private func confirmUserAndPair(isDifferentUser: Bool) {
        if isDifferentUser {
            advanceFromScanning()
            return
        } else {
            let lang = BpmSetupStrings.DeviceConflictAlert.SameUser.self
            let alert = AlertModel(
                title: lang.title,
                message: lang.message,
                buttons: [
                    AlertButtonModel(title: CommonStrings.cancel, type: .secondary) { [weak self] _ in
                        self?.deviceToDelete = nil
                        self?.dismissAction?()
                    },
                    AlertButtonModel(title: lang.continueButton, type: .primary) { [weak self] _ in
                        guard let self else { return }
                        Task { @MainActor in
                            if let deviceInfo = self.lastRetrievedDeviceInfo,
                               await self.checkForUserMismatch(deviceInfo) {
                                return
                            }
                            self.advanceFromScanning()
                        }
                    }
                ]
            )
            notificationService.showAlert(alert)
        }
    }

    /// Returns the display label ("A"/"B" or "1"/"2") for the existing user in a conflict alert.
    private func userLabelForConflict() -> String {
        guard let existing = deviceToDelete,
              let userNumber = existing.userNumber else { return "1" }
        // Only 0603 (hasNumericUsers) shows "1"/"2"; all others show "A"/"B".
        let useNumeric = bpmItem?.hasNumericUsers ?? false
        if useNumeric { return userNumber }
        return userNumber == "1" ? "A" : "B"
    }

    /// Post-connection fallback: checks whether the monitor's active user matches the app selection.
    /// Returns `true` if there is a mismatch (caller should abort pairing).
    private func checkForUserMismatch(_ deviceInfo: DeviceInfo) async -> Bool {
        guard let expected = selectedUserNumber,
              let actual = deviceInfo.userNumber,
              actual != expected else {
            return false
        }

        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Post-connection user mismatch: app selected \(expected), monitor reports \(actual)"
        )

        let broadcastId = discoveredDevice?.broadcastIdString ?? ""
        if !broadcastId.isEmpty {
            _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId, considerForSession: false)
        }

        showUserMismatchAlert()
        return true
    }

    /// Displays the "User Mismatch" alert when app user and monitor user don't match.
    private func showUserMismatchAlert() {
        let lang = BpmSetupStrings.UserMismatchAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.cancelSetupButton, type: .secondary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: lang.reviewButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.resetDiscoveryState()
                    if let userIndex = self.steps.firstIndex(of: .selectUser) {
                        self.currentStepIndex = userIndex
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Shows an alert when the SDK reports the device is already paired to the same user.
    /// Offers Cancel (close setup) or Continue (retry with replaceUser=true).
    private func showSameUserConflictAlert() {
        let lang = BpmSetupStrings.DeviceConflictAlert.SameUser.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: CommonStrings.cancel, type: .secondary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: lang.continueButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.canReplaceUser = true
                    // Clear the pre-pairing duplicate marker so checkForDuplicateAndAdvance
                    // doesn't re-show this same alert if the second pairing attempt succeeds.
                    self.deviceToDelete = nil
                    Task { @MainActor in await self.startPairing() }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Shows an alert when the SDK reports the device is paired to a different user.
    /// Offers Cancel (close setup) or Replace User (retry with replaceUser=true).
    private func showDifferentUserConflictAlert() {
        let lang = BpmSetupStrings.DeviceConflictAlert.DifferentUser.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message(userLabelForConflict()),
            buttons: [
                AlertButtonModel(title: CommonStrings.cancel, type: .secondary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: lang.replaceButton, type: .primary) { [weak self] _ in
                    guard let self else { return }
                    self.canReplaceUser = true
                    Task { @MainActor in await self.startPairing() }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Advances from scanning to the nickname step after a successful pair.
    private func advanceFromScanning() {
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: stepTransitionDelayNs)
            if self.currentStep == .scanning {
                self.moveToNextStep()
            }
        }
    }

    // MARK: - Device Persistence
    // swiftlint:disable:next function_body_length
    private func saveDevice() async -> Bool {
        guard !isDeviceSaved else { return true }
        guard let bpmItem else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveDevice - missing bpmItem")
            return false
        }
        guard let accountId = accountService.activeAccount?.accountId else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveDevice failed: no active account")
            return false
        }

        // If a previous device entry was marked for replacement (same-user re-pair or
        // different-user replace), delete it before saving the new entry.
        // This mirrors the Ionic pattern: deleteMonitor(deviceId) removes only that
        // specific user's entry — other users' entries for the same physical device remain.
        if let existingDevice = deviceToDelete, !existingDevice.id.isEmpty {
            LoggerService.shared.log(level: .info, tag: tag, message: "Deleting previous device entry (id: \(existingDevice.id)) before saving new pairing")
            try? await deviceService.deleteSingleDeviceEntry(existingDevice.id)
            deviceToDelete = nil
        }
        canReplaceUser = false

        // Check for existing device with same broadcastId or peripheralIdentifier AND userNumber.
        // If a duplicate is found, delete the old entry before saving the new one so the list
        // only ever shows one monitor per user number for a given physical device.
        let selectedUser = "\(selectedUserNumber ?? 1)"
        let existingDevices = (try? await deviceService.getDevices()) ?? []
        let broadcastId = discoveredDevice?.broadcastIdString ?? ""
        let peripheralId = discoveredDevice?.peripheralIdentifier ?? ""

        let duplicate = existingDevices.first { existing in
            let matchesBroadcast = !broadcastId.isEmpty && existing.broadcastIdString == broadcastId
            let matchesPeripheral = !peripheralId.isEmpty && existing.peripheralIdentifier == peripheralId
            return (matchesBroadcast || matchesPeripheral) && existing.userNumber == selectedUser
        }

        if let duplicate, !duplicate.id.isEmpty {
            LoggerService.shared.log(level: .info, tag: tag, message: "Removing duplicate BPM entry (id: \(duplicate.id)) before saving updated pairing")
            try? await deviceService.deleteSingleDeviceEntry(duplicate.id)
        }

        let deviceToSave: Device
        if let device = discoveredDevice {
            deviceToSave = device
            if deviceToSave.id.isEmpty { deviceToSave.id = UUID().uuidString }
        } else {
            // No real BLE device discovered (UI-only mode) — create a local placeholder.
            deviceToSave = Device(
                id: UUID().uuidString,
                accountId: accountId,
                sku: bpmItem.sku,
                deviceType: DeviceType.scale.rawValue,
                createdAt: DateTimeTools.getCurrentDatetimeIsoString(),
                isSynced: false,
                hasServerID: false
            )
        }
        deviceToSave.nickname = deviceNickname.isEmpty ? nil : deviceNickname

        // Ensure protocolType is set (defensive fallback from discovery event)
        if deviceToSave.protocolType == nil || deviceToSave.protocolType?.isEmpty == true,
           let eventProtocol = discoveryEvent?.protocolType {
            deviceToSave.protocolType = eventProtocol.rawValue
        }

        do {
            _ = try await deviceService.createBluetoothScale(
                device: deviceToSave,
                sku: bpmItem.sku,
                userNumber: "\(selectedUserNumber ?? 1)",
                accountId: accountId,
                deviceMetadata: nil,
                skipDuplicateCheck: discoveredDevice == nil,
                deviceType: .bpm
            )
            isDeviceSaved = true
            // createBluetoothScale already triggers syncDevices → syncAllScalesWithRemote
            // internally, so an additional sync here is redundant and risks a SwiftData
            // "This store went missing?" crash from rapid delete-then-save cycles.
            ProductTypeStore.shared.selectLastAdded(.myBloodPressure)
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            LoggerService.shared.log(level: .info, tag: tag, message: "BPM device saved")
            return true
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "saveDevice failed: \(error.localizedDescription)"
            )
            return false
        }
    }

    private func saveAndAdvanceFromNickname() async {
        guard currentStep == .nickname, !isSaving else { return }
        isSaving = true
        updateNextEnabled()
        defer {
            isSaving = false
            updateNextEnabled()
        }
        let saved = await saveDevice()
        guard saved else {
            LoggerService.shared.log(level: .error, tag: tag, message: "BPM device save failed, cannot advance from nickname")
            return
        }

        // Navigate explicitly to .paired to ensure "Your monitor is paired!" screen is shown,
        // avoiding any index drift from async save operations.
        if let pairedIndex = steps.firstIndex(of: .paired) {
            currentStepIndex = pairedIndex
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

    // MARK: - Post-Connection Device Info

    /// Updates the discovered device with stable identifiers from the connected device info.
    /// Matching the Ionic Balance Health app pattern:
    /// - `broadcastIdString` is set to the real hex broadcastId (used for plugin sync)
    /// - `peripheralIdentifier` stores the serialNumber (used for DB dedup)
    /// - `password` is captured from the connected device (critical for A3 BPM)
    ///
    /// On iOS, A3 BPMs report a CoreBluetooth UUID during discovery. The real broadcastId
    /// only becomes available after connection via DEVICE_CONNECTED. We first try getDeviceInfo,
    /// then fall back to lastConnectedDeviceDetails from the DEVICE_CONNECTED event.
    private func updateDeviceFromPostConnectionInfo(_ device: Device) async {
        let protocolType = device.protocolType ?? "A3"

        // Try getDeviceInfo first (works for A6/R4 where broadcastId is valid)
        let deviceInfoResult = await bluetoothService.getDeviceInfo(broadcastId: device.broadcastIdString ?? "", skipConnectionCheck: true)

        switch deviceInfoResult {
        case .success(let deviceInfo):
            lastRetrievedDeviceInfo = deviceInfo
            applyDeviceInfo(deviceInfo, to: device, protocolType: protocolType)

        case .failure(let error):
            // For A3 BPMs, getDeviceInfo fails because the SDK can't find the device
            // by its CoreBluetooth UUID. Fall back to deviceInfoUpdatedPublisher which
            // fires on DEVICE_INFO_UPDATE scan events with real device info post-connection.
            LoggerService.shared.log(
                level: .info,
                tag: tag,
                message: "getDeviceInfo failed for \(protocolType) BPM (\(error.localizedDescription)), waiting for deviceInfoUpdatedPublisher…"
            )
            do {
                let deviceInfo = try await awaitDeviceInfoUpdate(timeoutSeconds: 5)
                lastRetrievedDeviceInfo = deviceInfo
                applyDeviceInfo(deviceInfo, to: device, protocolType: protocolType)
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "Applied device info from publisher for \(protocolType) BPM"
                )
            } catch {
                LoggerService.shared.log(
                    level: .error,
                    tag: tag,
                    message: "No device info received from publisher within timeout: \(error.localizedDescription)"
                )
            }
        }
    }

    /// Waits for the next `deviceInfoUpdatedPublisher` emission, with a timeout.
    private func awaitDeviceInfoUpdate(timeoutSeconds: Int) async throws -> DeviceInfo {
        try await withCheckedThrowingContinuation { continuation in
            var resumed = false
            var cancellable: AnyCancellable?
            cancellable = bluetoothService.deviceInfoUpdatedPublisher
                .timeout(.seconds(timeoutSeconds), scheduler: DispatchQueue.main)
                .first()
                .sink(
                    receiveCompletion: { completion in
                        guard !resumed else { return }
                        resumed = true
                        cancellable?.cancel()
                        if case .failure = completion {
                            continuation.resume(throwing: BluetoothServiceError.timeout)
                        } else {
                            // .finished without a value means timeout elapsed
                            continuation.resume(throwing: BluetoothServiceError.timeout)
                        }
                    },
                    receiveValue: { info in
                        guard !resumed else { return }
                        resumed = true
                        cancellable?.cancel()
                        continuation.resume(returning: info)
                    }
                )
        }
    }

    /// Applies device info fields to the discovered device (matching Ionic setLastPairedDevice pattern).
    private func applyDeviceInfo(_ deviceInfo: DeviceInfo, to device: Device, protocolType: String) {
        let serialNumber = deviceInfo.serialNumber?.replacingOccurrences(of: "\0", with: "")

        // Update broadcastIdString to the real hex value from the connected device.
        // During A3 discovery, broadcastIdString was a CoreBluetooth UUID — now replace
        // it with the real hex broadcastId that the SDK uses for sync.
        let realBroadcastId = deviceInfo.broadcastIdString
        if !realBroadcastId.isEmpty {
            let broadcastIdInt = bluetoothService.convertHexToInt(realBroadcastId)
            if broadcastIdInt > 0 {
                device.broadcastIdString = realBroadcastId
                device.broadcastId = broadcastIdInt
            }
        }

        // For A3, store serialNumber as peripheralIdentifier (matching Ionic pattern).
        // In Ionic: peripheralIdentifier = deviceInfo.serialNumber (for A3)
        if protocolType == "A3", let serial = serialNumber, !serial.isEmpty {
            device.peripheralIdentifier = serial
        }

        // Capture password (A3 BPMs need this for sync).
        // In Ionic: deviceData.password = monitor.password (hex string from plugin)
        if let password = deviceInfo.password, !password.isEmpty {
            let passwordInt = bluetoothService.convertHexToInt(password)
            if passwordInt > 0 {
                device.password = passwordInt
            }
        }

        // Capture MAC address if not already set
        if !deviceInfo.macAddress.isEmpty, device.mac == nil || device.mac?.isEmpty == true {
            device.mac = deviceInfo.macAddress
        }

        // Capture the monitor's active user slot so post-connection user-mismatch checks
        // have a value to compare against (critical for A6 multi-user pairing).
        if let userNum = deviceInfo.userNumber {
            device.userNumber = "\(userNum)"
        }

        if device.protocolType == nil || device.protocolType?.isEmpty == true {
            device.protocolType = protocolType
        }
    }

    // MARK: - Helpers

    /// Returns "1"/"2" for monitors with numeric users (0603), "A"/"B" for all others.
    private func userLabel(for userNumber: Int) -> String {
        guard let bpmItem else { return "\(userNumber)" }
        if bpmItem.hasNumericUsers {
            return "\(userNumber)"
        }
        return userNumber == 1 ? "A" : "B"
    }

    private func resetDiscoveryState() {
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        scanTimerTask?.cancel()
        bpmReadingSubscription?.cancel()
        bpmReadingSubscription = nil
        lastRetrievedDeviceInfo = nil
        canReplaceUser = false
        if !isDeviceSaved {
            discoveredDevice = nil
            discoveryEvent = nil
        }
    }

    private func isBluetoothPermissionEnabled() -> Bool {
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }

    /// Subdirectory for `.confirmUser` and `.prePairing` steps that may have per-SKU GIFs.
    private func confirmUserGifSubdirectory(for sku: String) -> String? {
        if a3BpmSkus.contains(sku) {
            // SKUs with their own Pulse GIF use the per-SKU folder.
            if sku == "0634" || sku == "0636" {
                return BpmA3MonitorSetupAssets.userGifBundleSubdirectory(for: sku)
            }
            return BpmA3MonitorSetupAssets.gifBundleSubdirectory(for: sku)
        }
        if a6BpmSkus.contains(sku) {
            return BpmA6MonitorSetupAssets.gifBundleSubdirectory(for: sku)
        }
        return nil
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

    private func userGifSubdirectory(for sku: String) -> String? {
        if a3BpmSkus.contains(sku) {
            return BpmA3MonitorSetupAssets.userGifBundleSubdirectory(for: sku)
        }
        if a6BpmSkus.contains(sku) {
            return BpmA6MonitorSetupAssets.gifBundleSubdirectory(for: sku)
        }
        return nil
    }

    // swiftlint:disable:next cyclomatic_complexity
    private func gifName(for step: BpmSetupStep, sku: String) -> String? {
        if a3BpmSkus.contains(sku) {
            let resolvedSku = BpmA3MonitorSetupAssets.resolvedAssetSku(sku)
            // SKUs 0634 and 0636 have their own GIF sets; all others share the 0603 folder.
            let usePerSku = resolvedSku != "0603"

            switch step {
            case .prePairing:
                if usePerSku {
                    return BpmA3MonitorSetupAssets.perSkuResourceName(sku: sku, BpmA3MonitorSetupAssets.ImageFile.pulse)
                }
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.memButton)
            case .confirmUser:
                if sku == "0636" {
                    return BpmA3MonitorSetupAssets.perSkuResourceName(sku: sku, BpmA3MonitorSetupAssets.ImageFile.pulse)
                }
                return nil
            case .measureSetup:
                if usePerSku {
                    return BpmA3MonitorSetupAssets.perSkuResourceName(sku: sku, BpmA3MonitorSetupAssets.ImageFile.cuff)
                }
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.cuff)
            case .measureStart:
                if usePerSku {
                    return BpmA3MonitorSetupAssets.perSkuResourceName(sku: sku, BpmA3MonitorSetupAssets.ImageFile.start)
                }
                return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.start)
            default:
                return nil
            }
        }

        if a6BpmSkus.contains(sku) {
            switch step {
            case .prePairing:
                return BpmA6MonitorSetupAssets.resolvedResourceName(sku: sku, BpmA6MonitorSetupAssets.ImageFile.pulse)
            case .measureSetup:
                return BpmA6MonitorSetupAssets.resolvedResourceName(sku: sku, BpmA6MonitorSetupAssets.ImageFile.cuff)
            case .measureStart:
                return BpmA6MonitorSetupAssets.resolvedResourceName(sku: sku, BpmA6MonitorSetupAssets.ImageFile.start)
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
                if sku == "0634" {
                    return BpmA3MonitorSetupAssets.perSkuResourceName(sku: sku, BpmA3MonitorSetupAssets.ImageFile.monitorOff)
                }
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
            return BpmA6MonitorSetupAssets.userGifName(sku: sku, slot: selectedUserNumber)
        }
        return nil
    }

    private func isPermissionStepSatisfied() -> Bool {
        isBluetoothPermissionEnabled()
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

            isNextEnabled = isPermissionStepSatisfied()
        case .selectUser:
            isNextEnabled = selectedUserNumber != nil
        case .scanning:
            isNextEnabled = connectionState == .success
        case .nickname:
            isNextEnabled = !deviceNickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving
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

    /// Skips steps that should be bypassed during normal forward navigation:
    /// - `.btPermission` when permissions are already granted (both directions)
    /// - `.measureSetup`, `.measureStart` in forward direction — only reachable via "Learn How to Measure"
    ///   Once the user is already in the measurement tutorial, these steps are NOT skipped.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        let measurementSteps: Set<BpmSetupStep> = [.measureSetup, .measureStart]
        let isInMeasurementTutorial = measurementSteps.contains(currentStep)
        var idx = index
        while idx >= 0 && idx < steps.count {
            let step = steps[idx]
            if step == .btPermission && isPermissionStepSatisfied() {
                idx += direction
            } else if direction == 1 && measurementSteps.contains(step) && !isInMeasurementTutorial {
                idx += direction
            } else {
                break
            }
        }
        return idx
    }

    private func setScanFailure() {
        resetDiscoveryState()
        showConnectionErrorAlert()
    }

    private func showConnectionErrorAlert() {
        let lang = BpmSetupStrings.ConnectionErrorAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.dismissButton, type: .secondary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: lang.tryAgainButton, type: .primary) { [weak self] _ in
                    self?.retryScanning()
                }
            ]
        )
        notificationService.showAlert(alert)
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
            scale: deviceService,
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

    @MainActor
    func testSaveAndAdvanceFromNickname() async {
        await saveAndAdvanceFromNickname()
    }

    @MainActor
    func testSetDeviceToDelete(_ device: DeviceSnapshot) {
        self.deviceToDelete = device
    }

    @MainActor
    func testUpdateDeviceFromPostConnectionInfo(_ device: Device) async {
        await updateDeviceFromPostConnectionInfo(device)
    }

    @MainActor
    func testCheckForPrePairingDuplicate() async {
        await checkForPrePairingDuplicate()
    }

    @MainActor
    func testConfirmUserAndPair(isDifferentUser: Bool) {
        confirmUserAndPair(isDifferentUser: isDifferentUser)
    }

    @MainActor
    func testCheckForUserMismatch(_ deviceInfo: DeviceInfo) async -> Bool {
        await checkForUserMismatch(deviceInfo)
    }

    @MainActor
    func testUserLabel(for userNumber: Int) -> String {
        userLabel(for: userNumber)
    }

    @MainActor
    func testUserLabelForConflict() -> String {
        userLabelForConflict()
    }

    @MainActor
    func testGifName(for step: BpmSetupStep, sku: String) -> String? {
        gifName(for: step, sku: sku)
    }

    @MainActor
    func testImageName(for step: BpmSetupStep, sku: String) -> String? {
        imageName(for: step, sku: sku)
    }

    @MainActor
    func testGifSubdirectory(for sku: String) -> String? {
        gifSubdirectory(for: sku)
    }

    @MainActor
    func testConfirmUserGifSubdirectory(for sku: String) -> String? {
        confirmUserGifSubdirectory(for: sku)
    }

    @MainActor
    func testUserGifSubdirectory(for sku: String) -> String? {
        userGifSubdirectory(for: sku)
    }

    @MainActor
    func testUserGifName(for sku: String, selectedUserNumber: Int?) -> String? {
        userGifName(for: sku, selectedUserNumber: selectedUserNumber)
    }

    @MainActor
    func testApplyDeviceInfo(_ deviceInfo: DeviceInfo, to device: Device, protocolType: String) {
        applyDeviceInfo(deviceInfo, to: device, protocolType: protocolType)
    }

    @MainActor
    func testCheckForDuplicateAndAdvance(_ device: Device) async {
        await checkForDuplicateAndAdvance(device)
    }

    @MainActor
    func testRetryScanning() {
        retryScanning()
    }
}
#endif
// swiftlint:enable file_length
