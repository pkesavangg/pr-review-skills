// swiftlint:disable type_body_length
// This file intentionally aggregates WiFi scale setup orchestration logic.
// Breaking it into smaller files would fragment the multi-step flow management.

import Combine
import Foundation
import SwiftUI
import UIKit

/// Store responsible for orchestrating the WiFi scale setup multi-step flow.
@MainActor
final class WifiScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var permissionsService: PermissionsService
    @Injector private var wifiScaleService: WifiScaleService
    @Injector private var accountService: AccountService
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService
    @Injector private var pushNotificationService: PushNotificationService
    @Injector private var httpClient: HTTPClient
    @Injector private var bluetoothService: BluetoothService
    
    let networkMonitor = NetworkMonitor.shared
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private let tag = "WifiScaleSetupStore"
    private let ssidTempKey = "ssidTemp"
    // Strings
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let alertLang = AlertStrings.self
    private let commonLang = CommonStrings.self
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    
    /// Cached scale token to avoid repeated API calls
    private var scaleToken: String?
    
    /// Active subscription to the network form changes
    private var networkFormCancellable: AnyCancellable?
    
    /// Indicates if the user manually cleared the SSID field (prevents auto-fill).
    private var hasUserManuallyClearedSSID: Bool = false

    /// Stores the previous SSID to detect manual clearing.
    private var previousSSID: String = ""
    
    /// Tracks the step that presented `.errorSelect` so we can navigate back correctly.
    private var errorSelectSourceStep: WifiScaleSetupStep?
    /// Tracks the step that presented `.stepOn` so we can navigate back correctly.
    private var stepOnSourceStep: WifiScaleSetupStep?
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    
    @Published private(set) var currentStep: WifiScaleSetupStep = .intro {
        didSet { handleStepChange() }
    }
    
    /// All steps in the setup flow. Exposed as read-only so views can iterate.
    @Published private(set) var steps: [WifiScaleSetupStep] = WifiScaleSetupStep.allCases
    
    /// Indicates that the user initiated the dedicated *Get-MAC* flow from the intro screen.
    ///
    /// When `true` the wizard bypasses the normal password/user selection flow and forces
    /// AP-mode so that the scale’s MAC (BSSID) can be fetched and copied by the user.
    @Published var isForGetMac: Bool = false
    
    /// Live Wi-Fi information for the **phone** – cached in `KvStorageService` so we can
    /// still access the SSID/BSSID after the user navigates away from iOS Settings.
    /// Used for:
    /// • pre-filling the SSID text field
    /// • polling the BSSID during *Get-MAC* flow
    @Published var wifiStatus: WifiStatus?
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    @Published var selectedUserNumber: Int?
    @Published var selectedErrorCode: WifiErrorCode?
    @Published var selectedConnectionMode: WifiSetupOption = .none
    @Published var isApModeOnly: Bool = false
    
    /// Connected network identifiers captured right before switching the phone to the
    /// scale’s `gg_SmartScale_##` access point.  Required because iOS APIs stop returning
    /// the original SSID/BSSID once the phone moves to the AP.
    @Published var connectedSsid: String?
    @Published var connectedBssid: String?
    
    /// Captured MAC address once retrieved (Get-MAC flow).
    @Published var retrievedMacAddress: String?
    
    /// Set to *true* when the user taps **Skip** on the permissions page.  Smart-connect
    /// is skipped completely in this case and only AP-mode UI is shown.
    @Published var permissionsSkipped: Bool = false
    
    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    
    /// Controls whether to skip network connectivity checks during AP mode
    private var skipCheckNetwork: Bool = false
    
    // MARK: - Forms
    @Published var networkForm = NetworkForm()
    
    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem) {
                    /// For Get-MAC flow, we need to skip the permissions step and navigate to the activate pairing mode step
                    self.isForGetMac = true
                    self.permissionsSkipped = false
                    if self.arePermissionsEnabled() {
                        self.navigateToStep(.activatePairingMode)
                    } else {
                        self.moveToNextStep()
                    }
                })
            case .permissions:
                return AnyView(PermissionListView(setupType: .wifi))
            case .wifiPassword:
                return AnyView(WifiPasswordView(showWifiConnectionDetails: (scaleItem.setupType == .espTouchWifi && !permissionsSkipped)) {
                    self.openWifiSettings()
                })
            case .selectUser:
                return AnyView(UserNumberSelectionView(selectedNumber: selectedUserNumber) { number in
                    self.selectedUserNumber = number
                    self.updateNextEnabled()
                })
            case .activatePairingMode:
                return AnyView(ActivatePairingModeView(sku: scaleItem.sku))
            case .connectionConfirm:
                return AnyView(WifiConnectionConfirmView(
                    sku: scaleItem.sku,
                    userNumber: selectedUserNumber,
                    selectedOption: selectedConnectionMode,
                    mode: (permissionsSkipped || isForGetMac) ? .apModeOnly : .optionSelection
                ) { selectedMode in
                    self.selectedConnectionMode = selectedMode
                    self.updateNextEnabled()
                } onClickButton: {
                    self.selectedConnectionMode = .none
                    self.navigateToStep(.errorSelect)
                })
            case .apMode:
// swiftlint:disable:next line_length
                return AnyView(ApModeConnectionView(connectedSSID: networkForm.isValidApModeSSID() ? networkForm.ssid.value : "", permissionsSkipped: permissionsSkipped) {
                    self.openWifiSettings()
                })
            case .apModeConfirm:
                return AnyView(WifiConnectionConfirmView(
                    sku: scaleItem.sku,
                    userNumber: selectedUserNumber,
                    selectedOption: selectedConnectionMode,
                    mode: .apModeConfirmation
                ) { _ in
                    self.updateNextEnabled()
                } onClickButton: {
                    self.navigateToStep(.errorSelect)
                })
            case .errorSelect:
// swiftlint:disable:next multiline_arguments
                return AnyView(ErrorCodeSelectionView(sku: scaleItem.sku, selectedError: selectedErrorCode, onErrorSelected: { code in
                    self.selectedErrorCode = code
                }, onClickButton: {
                    self.moveToNextStep()
                }))
            case .errorDetail:
                return AnyView(WifiErrorCodeDetailView(errorCode: selectedErrorCode))
            case .copyMacAddress:
                return AnyView(CopyMacAddressView(macAddress: retrievedMacAddress ?? ""))
            case .stepOn:
                return AnyView(ScaleSetupStepOnView())
            case .setupFinish:
// swiftlint:disable:next line_length
                return AnyView(ScaleSetupFinishView(title: scaleSetupStrings.FinishViewStrings.title, description: scaleSetupStrings.FinishViewStrings.description))
            }
        }
    }
    
    var nextButtonText: String {
        switch currentStep {
        case .setupFinish, .errorDetail, .copyMacAddress:
            return commonLang.finish
        default:
            return commonLang.next
        }
    }
    
    // MARK: - Lifecycle
    init() {
        // Initialize HTTPClient skipCheckNetwork to false
        httpClient.skipCheckNetwork = false
        
        // Observe permission updates
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
                self?.getWifiStatus()
            }
            .store(in: &cancellables)
        
        networkMonitor.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                self?.updateNextEnabled()
                self?.getWifiStatus()
                if isConnected {
                    self?.fetchWifiScaleToken()
                }
            }
            .store(in: &cancellables)
        // Observe app coming to foreground to refresh Wi-Fi status and check if location permissions are revoked
        NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in
                guard let self else { return }
                // Refresh Wi-Fi status after slight delay so underlying services are ready.
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    self.getWifiStatus()
                }
                /// Check if location permissions are revoked and show alert if they are
                /// If the user has revoked location permissions, this method will show an alert
                /// prompting the user to enable location permissions
                if self.currentStepIndex > 1 && !self.permissionsSkipped {
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 300_000_000)
                        await self.showPermissionRevokedAlert()
                    }
                }
            }
            .store(in: &cancellables)
        getWifiStatus()
        subscribeToNetworkForm()
        fetchWifiScaleToken()
    }
    
    // MARK: - Configuration
    func configure(with sku: String) {
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        // Pass original SKU to routes (not mapped), setup will save original SKU
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let resolved = SCALES.first { $0.sku == lookupSku } ?? SCALES.first
        self.scaleItem = resolved
        // Start at intro
        currentStepIndex = 0
        
        // Set setup in progress flag to prevent goal modals during setup
        bluetoothService.isSetupInProgress = true
        
        // Evaluate initial next-button state
        updateNextEnabled()
    }
    
    // MARK: - Navigation
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
    
    // MARK: - Exit / Help
    func handleExit() {
        if currentStep == .setupFinish {
            // Save scale before exiting from the last step
            saveScale()
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
    
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(skuToNavigate: scaleItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }
    
    func shouldDisableBackButton() -> Bool {
        return currentStep == .intro || currentStep == .setupFinish
    }
    
// swiftlint:disable:next cyclomatic_complexity
    func handleNextButtonClick() {
        switch currentStep {
        case .intro:
            // User is following the normal wizard path – clear any previously-set "Get-MAC" flag.
            isForGetMac = false
            moveToNextStep()
        case .permissions:
            // A valid scale token is mandatory beyond this point; bail out (and show a toast) if we don't have one yet.
            if checkScaleToken() == nil {
                return
            }
            permissionsSkipped = false
            // When the user launched the dedicated "Get-MAC" flow we bypass the regular password & user-selection steps.
            if isForGetMac {
                navigateToStep(.activatePairingMode)
            } else {
                moveToNextStep()
            }
// swiftlint:disable:next switch_case_alignment
            case .connectionConfirm:
            // If permissions were skipped or we're in the Get-MAC flow we must force AP-mode – smart-connect isn't possible.
            if permissionsSkipped || isForGetMac {
                selectedConnectionMode = .apMode
            }
            // Branch based on the user's choice: go straight to Step-On for a full smart-connect, otherwise continue to AP-mode.
            if selectedConnectionMode == .complete {
                self.navigateToStep(.stepOn)
            } else {
                self.navigateToStep(.apMode)
            }
// swiftlint:disable:next switch_case_alignment
            case .apMode:
            // In Get-MAC flow we poll the scale's AP for its MAC address, otherwise we just proceed to the next wizard step.
            if isForGetMac {
                Task {
                    await self.getMacAddress()
                    if retrievedMacAddress != nil {
                        // MAC obtained – show copy screen.
                        self.navigateToStep(.copyMacAddress)
                    }
                }
            } else {
                moveToNextStep()
            }
        case .apModeConfirm:
            // User confirmed AP-mode connection; advance to the scale calibration (Step-On) stage.
            self.navigateToStep(.stepOn)
// swiftlint:disable:next switch_case_alignment
            case .errorDetail, .copyMacAddress:
            // When the user taps "Finish" on the error detail screen/copy mac address screen, we exit the setup entirely.
            exitSetup()
        case .stepOn:
            // Move to next step without saving
            moveToNextStep()
        case .setupFinish:
            // Save scale before finishing setup
            saveScale()
            moveToNextStep()
// swiftlint:disable:next switch_case_alignment
            default:
            moveToNextStep()
        }
    }
    
    func handleBackButtonClick() {
        switch currentStep {
        case .activatePairingMode:
            // "Back" from pairing mode behaves differently when in the Get-MAC flow:
            //   • If required permissions are now granted we can return to the intro.
            //   • Otherwise we must guide the user back to the permissions screen.
            if isForGetMac {
                // Ternary chooses the appropriate destination based on current permission state.
// swiftlint:disable:next void_function_in_ternary
                arePermissionsEnabled() ? navigateToStep(.intro) : navigateToStep(.permissions)
            } else {
                moveToPreviousStep()
            }
        case .errorSelect:
            // Return to whichever screen originally presented the error selection list.
            if let origin = errorSelectSourceStep {
                navigateToStep(origin)
            } else {
                navigateToStep(.connectionConfirm)
            }
// swiftlint:disable:next switch_case_alignment
            case .copyMacAddress:
            // Simply rewind to the AP-mode instructions when the user taps "Back" from the copy screen.
            navigateToStep(.apMode)
        case .stepOn:
            // Allow the user to return to the screen that led to Step-On (e.g. Connection Confirm) when possible.
            if let origin = stepOnSourceStep {
                navigateToStep(origin)
            } else {
                moveToPreviousStep()
            }
// swiftlint:disable:next switch_case_alignment
            default:
            moveToPreviousStep()
        }
    }
    
    /// Handles the skip WiFi step action
    func handleSkipWifiStep() {
        if checkScaleToken() == nil {
            return
        }
        let alertStrings = alertLang.SkipPermissionsAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.skipButton, type: .primary) { [weak self] _ in
                    guard let self = self else { return }
                    // User chose to skip – flag this so smart-connect can bail.
                    self.permissionsSkipped = true
                    // Clear the network form SSID when permissions are skipped and mark as pristine to avoid validation errors
                    self.networkForm.clearSSIDAndMarkPristine()
                    self.wifiStatus = nil
                    // Reset the manual clear flag and sync previousSSID since this is a programmatic clear, not user-initiated
                    self.hasUserManuallyClearedSSID = false
                    self.previousSSID = ""
                    logger.log(level: .info, tag: tag, message: "Permissions skipped - cleared WiFi password form SSID and marked as pristine")
                    // Continue to next step.
                    self.moveToNextStep()
                }
            ]
        )
        notificationService.showAlert(alert)
        // Note: `permissionsSkipped` is set inside the alert action above.
    }
    
    func cleanUp() {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        // Clear setup in progress flag when setup is dismissed
        bluetoothService.isSetupInProgress = false
    }
    
    /// Observes network form changes and updates Next button state.
    private func subscribeToNetworkForm() {
        // Cancel existing subscription
        networkFormCancellable?.cancel()
        
        // Set initial SSID
        previousSSID = networkForm.ssid.value
        
        networkFormCancellable = networkForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                guard let self else { return }
                
                self.updateNextEnabled()
                
                let currentSSID = self.networkForm.ssid.value
                
                // Track manual SSID clear: once user clears a non-empty SSID,
                // remember this for the rest of the session.
                if !self.previousSSID.isEmpty && currentSSID.isEmpty {
                    self.hasUserManuallyClearedSSID = true
                }
                
                self.previousSSID = currentSSID
            }
    }
    
    private func navigateToStep(_ step: WifiScaleSetupStep, delay: TimeInterval = 0) {
        // Track source steps for back-navigation.
        if step == .errorSelect {
            errorSelectSourceStep = currentStep
        } else if step == .stepOn {
            stepOnSourceStep = currentStep
        }
        if let stepIndex = steps.firstIndex(of: step) {
            self.currentStepIndex = stepIndex
        }
    }
    
    private func getWifiStatus() {
        Task { @MainActor in
            let kvStorage = KvStorageService.shared
            let status = await wifiScaleService.getConnectedWifiInfo()
            
            /// Auto-populate SSID only when permissions allow or are currently enabled
            let arePermissionsCurrentlyEnabled = arePermissionsEnabled()
            let shouldAutoPopulate = !permissionsSkipped || isForGetMac || arePermissionsCurrentlyEnabled
            
            if !shouldAutoPopulate {
                // Permissions were skipped and not in Get-MAC mode - clear SSID and mark as pristine to avoid validation errors
                self.networkForm.clearSSIDAndMarkPristine()
                self.wifiStatus = nil
                // Reset the manual clear flag and sync previousSSID since this is a programmatic clear, not user-initiated
                self.hasUserManuallyClearedSSID = false
                self.previousSSID = ""
                logger.log(level: .info, tag: tag, message: "Wi-Fi permissions skipped: SSID cleared and will not be populated.")
            } else {
                // Normal flow: update WiFi status and populate SSID
                if let ssid = status.ssid, !ssid.isEmpty {
                    let localStatus = kvStorage.getCodable(forKey: ssidTempKey, as: WifiStatus.self)
                    if let wifiStatus = localStatus {
                        if ssid != wifiStatus.ssid {
                            kvStorage.setCodable(status, forKey: ssidTempKey)
                        }
                    } else {
                        kvStorage.setCodable(status, forKey: ssidTempKey)
                    }
                    self.wifiStatus = kvStorage.getCodable(forKey: ssidTempKey, as: WifiStatus.self)

                    // Only update form when we have a definitive read from the system.
                    // When status.ssid is empty, do NOT call setSSID - iOS returns empty
                    // intermittently during network transitions. Overwriting would both clear
                    // valid data and trigger hasUserManuallyClearedSSID in formDidChange.
                    if !hasUserManuallyClearedSSID {
                        self.networkForm.setSSID(ssid)
                        self.previousSSID = ssid
                    }
                } else {
                    // status.ssid empty: keep cache for other consumers, but don't touch form
                    self.wifiStatus = kvStorage.getCodable(forKey: ssidTempKey, as: WifiStatus.self)
                }
            }
        }
    }
    
    private func handleStepChange() {
        switch currentStep {
        case .connectionConfirm:
            Task { await startSmartConnect() }
        case .apModeConfirm:
            Task { await startApMode() }
        case .apMode:
            // Set skipCheckNetwork to true when entering AP mode
            setSkipCheckNetwork(true)
// swiftlint:disable:next switch_case_alignment
            default:
            // Reset skipCheckNetwork to false for other steps
            setSkipCheckNetwork(false)
        }
    }
    
    private func openWifiSettings() {
        permissionsService.navigateToWifiSettings()
    }
    
    /// Controls the skipCheckNetwork flag on HTTPClient
    /// - Parameter skip: Whether to skip network connectivity checks
    private func setSkipCheckNetwork(_ skip: Bool) {
        skipCheckNetwork = skip
        httpClient.skipCheckNetwork = skip
        logger.log(level: .debug, tag: tag, message: "skipCheckNetwork set to: \(skip)")
    }
    
    /// Resets skipCheckNetwork to false (called when view disappears)
    func resetSkipCheckNetwork() {
        setSkipCheckNetwork(false)
    }
    
    func arePermissionsEnabled() -> Bool {
        // For WiFi setup, we need Location permission and switches enabled
        return permissionsService.getPermissionState(.LOCATION) == .ENABLED &&
        permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED &&
        permissionsService.getPermissionState(.WIFI_SWITCH) == .ENABLED
    }
    
    private func updateNextEnabled() {
        switch currentStep {
        case .permissions:
            isNextEnabled = arePermissionsEnabled()
        case .wifiPassword:
            // Enable connect button only when password is valid (unless no password required)
            if networkForm.networkHasNoPassword {
                isNextEnabled = networkForm.ssid.isValid
            } else {
                isNextEnabled = networkForm.ssid.isValid && networkForm.password.isValid
            }
        case .selectUser:
            isNextEnabled = selectedUserNumber != nil
        case .connectionConfirm:
            isNextEnabled = (permissionsSkipped || isForGetMac) ? true : selectedConnectionMode != .none
        case .apMode:
            isNextEnabled = permissionsSkipped ? true : networkForm.isValidApModeSSID()
        default:
            isNextEnabled = true
        }
    }
    
    /// Fetches the WiFi scale token for setup operations.
    /// This demonstrates how to use the WiFi scale service from other services.
    private func fetchWifiScaleToken() {
        if scaleToken != nil {
            return
        }
        Task {
            do {
                let scaleTokenResponse = try await wifiScaleService.getScaleToken(request: "4")
                self.scaleToken = scaleTokenResponse.token
                LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi scale token")
            } catch {
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi scale token: \(error.localizedDescription)")
            }
        }
    }
    
    /// Returns the cached Wi-Fi scale token or shows a toast if none is available.
    private func checkScaleToken() -> String? {
        if scaleToken == nil {
            notificationService.showToast(
                ToastModel(
                    title: toastLang.internetRequiredTitle,
                    message: toastLang.internetRequiredMessage
                )
            )
        }
        return scaleToken
    }
    
    private func saveScale() {
        if checkScaleToken() == nil {
            return
        }
        
        // Reset skipCheckNetwork to false when saving (similar to Angular code)
        setSkipCheckNetwork(false)
        
        notificationService.showLoader(LoaderModel(text: loaderLang.saving))
        
        guard let scaleItem, let userNumber = selectedUserNumber else {
            notificationService.dismissLoader()
            return
        }
        
        guard let accountId = self.accountService.activeAccount?.accountId else {
            return
        }
        
        Task {
            defer { self.notificationService.dismissLoader() }
            do {
                let newDevice = Device(
                    id: UUID().uuidString,
                    accountId: accountId,
                    sku: scaleItem.sku,
                    deviceName: scaleItem.productName,
                    deviceType: DeviceType.scale.rawValue,
                    userNumber: "\(userNumber)",
                    token: self.scaleToken ?? "",
                    bathScale: BathScale(scaleType: ScaleSourceType.wifi.rawValue, bodyComp: scaleItem.bodyComp)
                )
                let response = try await self.scaleService.createDevice(newDevice)
                await self.scaleService.syncAllScalesWithRemote()
                Task {
                    await self.pushNotificationService.setupPushNotifications(isFromScaleSetup: true)
                }
                
                // Clear setup in progress flag after scale is saved
                bluetoothService.isSetupInProgress = false
                
                logger.log(level: .info, tag: tag, message: "Scale saved successfully with ID: \(response.id) \(scaleItem.sku)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
                self.notificationService.showToast(ToastModel(message: ToastStrings.saveScaleError))
                // Clear setup in progress flag even on error
                bluetoothService.isSetupInProgress = false
            }
        }
    }
    
    // MARK: - Smart-Connect
    /// Initiates the Wi-Fi smart-connect flow once the user confirms the connection screen.
    ///
    /// Behaviour:
    /// 1. Early-returns when `permissionsSkipped` is `true` – we cannot obtain the data
    ///    required for smart-connect without Location permission.
    /// 2. Builds a `WifiSetupInfo` payload.
    /// 3. Stops any previous session, caches `connectedSsid/Bssid`, and invokes either
    ///    `espSmartConnect` or `smartConnect` on `WifiScaleService`.
    ///
    /// On error the method simply logs via `LoggerService`; UI feedback is handled by
    /// observers of `LoggerService` elsewhere in the app.
    private func startSmartConnect() async {
// swiftlint:disable:next line_length
        LoggerService.shared.log(level: .info, tag: tag, message: "startSmartConnect initiated – setupType: \(String(describing: scaleItem?.setupType))")
        
        // If permissions were skipped, do NOT try to configure the scale.
        if permissionsSkipped { return }
        
        // Build the setup payload.
        let setupInfo = getSetupInfo()
        
        do {
            // Ensure any previous smart-connect sessions are stopped.
            await wifiScaleService.stop()
            
            // Cache SSID / BSSID for later use if required.
            // self.connectedSsid = setupInfo.ssid // This line was removed from the original file, so it's removed here.
            // self.connectedBssid = setupInfo.bssid // This line was removed from the original file, so it's removed here.
            self.connectedSsid = setupInfo.ssid
            self.connectedBssid = setupInfo.bssid
            if scaleItem?.setupType == .espTouchWifi {
                try await wifiScaleService.espSmartConnect(setupInfo)
            } else {
                try await wifiScaleService.smartConnect(setupInfo)
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "startSmartConnect error: \(error.localizedDescription)")
        }
    }
    
    /// Constructs the `WifiSetupInfo` payload
    private func getSetupInfo() -> WifiSetupInfo {
        let hasPassword = !networkForm.networkHasNoPassword
        
        return WifiSetupInfo(ssid: networkForm.ssid.value,
                             bssid: wifiStatus?.bssid,
                             password: hasPassword ? networkForm.password.value : nil,
                             userNumber: selectedUserNumber,
                             token: scaleToken)
    }
    
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .permissions,
              arePermissionsEnabled() {
            idx += direction
        }
        return idx
    }
    
    private func exitSetup() {
        // Reset skipCheckNetwork when exiting setup
        setSkipCheckNetwork(false)
        
        Task {
            await self.wifiScaleService.stop()
            dismissAction?()
        }
    }
    
    // MARK: - AP-Mode
    /// Initiates the AP-Mode Wi-Fi configuration once the user reaches the
    /// confirmation step
    ///
    /// Retries up to five times when the operation throws, waiting five seconds
    /// between attempts – matching the behaviour in the legacy TS code.
    private func startApMode(retryCount: Int = 0) async {
        logger.log(level: .info, tag: tag, message: "startApMode triggered – attempt #\(retryCount)")
        
        // Prepare the payload (mutating SSID/BSSID when available).
        let baseInfo = getSetupInfo()
        let info = WifiSetupInfo(
            ssid: self.connectedSsid ?? wifiStatus?.ssid ?? baseInfo.ssid,
            bssid: self.connectedBssid ?? wifiStatus?.bssid ?? baseInfo.bssid,
            password: baseInfo.password,
            userNumber: baseInfo.userNumber,
            token: baseInfo.token
        )
        
        do {
            // Stop any previous sessions before starting AP-mode.
            await wifiScaleService.stop()
            try await wifiScaleService.apMode(info)
        } catch {
            logger.log(level: .error, tag: tag, message: "startApMode error: \(error.localizedDescription)")
            
            // Retry up to 5 times, matching the JS logic.
            if retryCount < 5 {
                try? await Task.sleep(nanoseconds: 5_000_000_000) // 5 seconds
                await startApMode(retryCount: retryCount + 1)
            }
        }
    }
    
    // MARK: - MAC Address Retrieval
    /// Attempts to retrieve the currently-connected Wi-Fi BSSID (MAC address)
    /// within ~30 seconds, polling every second
    /// - Returns: `true` if a MAC address was obtained, `false` on timeout.
    @discardableResult
    private func getMacAddress() async -> Bool {
        self.retrievedMacAddress = nil
        notificationService.showLoader(LoaderModel(text: LoaderStrings.gettingMacAddress))
        defer { notificationService.dismissLoader() }
        
        let timeout: TimeInterval = 29.5
        let startDate = Date()
        
        do {
            // Initial delay to allow the Wi-Fi service to stabilize.
            try await Task.sleep(nanoseconds: 3 * 1_000_000_000) // Initial delay of 3 seconds
        } catch {}
        
        while Date().timeIntervalSince(startDate) < timeout {
            if let bssid = self.wifiStatus?.bssid, !bssid.isEmpty {
                // Normalize segments to two-character hex values.
                let formatted = bssid
                    .split(separator: ":")
                    .map { segment -> String in
                        segment.count == 1 ? "0\(segment)" : String(segment)
                    }
                    .joined(separator: ":")
                
                self.retrievedMacAddress = formatted
                self.logger.log(level: .info, tag: tag, message: "MAC address retrieved: \(formatted)")
                return true
            }
            
            // Wait 1 second before next attempt.
            try? await Task.sleep(nanoseconds: 1_000_000_000)
        }
        
        // Timed-out – show alert
        let alert = AlertModel(
            title: ToastStrings.genericError,
            buttons: [
                AlertButtonModel(title: CommonStrings.ok, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
        return false
    }
    
    /// Shows an alert if the user has revoked location permissions.
    /// If the user has revoked location permissions, this method will show an alert
    /// prompting the user to enable location permissions
    private func showPermissionRevokedAlert() async {
        let isLocationSwitchEnabled = permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED
        let isLocationAuthorized = permissionsService.getPermissionState(.LOCATION) == .ENABLED
        if !isLocationSwitchEnabled {
            _ = await permissionsService.handlePermission(.locationSwitch)
        } else if !isLocationAuthorized {
            _ = await permissionsService.handlePermission(.location)
        }
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
// swiftlint:disable:next file_length
// swiftlint:enable type_body_length
