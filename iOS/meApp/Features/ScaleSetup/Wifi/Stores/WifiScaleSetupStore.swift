import Foundation
import SwiftUI
import Combine
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
    
    let networkMonitor = NetworkMonitor.shared
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private let tag = "WifiScaleSetupStore"
    // Strings
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let alertLang = AlertStrings.self
    private let commonLang = CommonStrings.self
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    
    /// Cached scale token to avoid repeated API calls
    private var scaleToken: String?
    
    /// Active subscription to the network form changes
    private var networkFormCancellable: AnyCancellable? = nil
    
    /// Tracks the step that presented `.errorSelect` so we can navigate back correctly.
    private var errorSelectSourceStep: WifiScaleSetupStep? = nil
    /// Tracks the step that presented `.stepOn` so we can navigate back correctly.
    private var stepOnSourceStep: WifiScaleSetupStep? = nil
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
    
    @Published var isForGetMac: Bool = false
    @Published var wifiStatus: WifiStatus?
    @Published var WifiSetupType: WifiSetupType?
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    @Published var selectedUserNumber: Int?
    @Published var selectedErrorCode: WifiErrorCode?
    @Published var selectedConnectionMode: WifiSetupOption = .none
    @Published var isApModeOnly: Bool = false
    
    /// Captured MAC address once retrieved (Get-MAC flow).
    @Published var retrievedMacAddress: String? = nil
    
    /// Flag indicating that the permissions step was skipped by the user. Used
    /// to short-circuit smart-connect if prerequisites are missing
    @Published var permissionsSkipped: Bool = false
    
    /// Callback used by the screen to dismiss itself.
    var dismissAction: DismissAction?
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    
    // MARK: - Forms
    @Published var networkForm = NetworkForm()
    
    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem) {
                    self.isForGetMac = true
                    if self.arePermissionsEnabled() {
                        self.navigateToStep(.activatePairingMode)
                    } else {
                        self.moveToNextStep()
                    }
                })
            case .permissions:
                return AnyView(PermissionListView(setupType: .wifi))
            case .wifiPassword:
                return AnyView(WifiPasswordView(allowEditSsid: scaleItem.setupType != .espTouchWifi) {
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
                return AnyView(ApModeConnectionView(connectedSSID: networkForm.isValidApModeSSID() ? networkForm.ssid.value : "", permissionsSkipped: permissionsSkipped) {
                    self.openWifiSettings()
                })
            case .apModeConfirm:
                return AnyView(WifiConnectionConfirmView(
                    sku: scaleItem.sku,
                    userNumber: selectedUserNumber,
                    selectedOption: selectedConnectionMode,
                    mode: .apModeConfirmation
                ) { selectedMode in
                    self.updateNextEnabled()
                } onClickButton: {
                    self.navigateToStep(.errorSelect)
                })
            case .errorSelect:
                return AnyView(ErrorCodeSelectionView(selectedError: selectedErrorCode, onErrorSelected: { code in
                    self.selectedErrorCode = code
                }, onClickButton: {
                    self.moveToNextStep()
                }))
            case .errorDetail:
                return AnyView(WifiErrorCodeDetailView(errorCode: selectedErrorCode))
            case  .copyMacAddress:
                return AnyView(CopyMacAddressView(macAddress: retrievedMacAddress ?? ""))
            case .stepOn:
                return AnyView(ScaleSetupStepOnView())
            case .setupFinish:
                return AnyView(ScaleSetupFinishView(title: scaleSetupStrings.FinishViewStrings.title, description: scaleSetupStrings.FinishViewStrings.description))
            }
        }
    }
    
    var nextButtonText: String {
        switch currentStep {
        case .setupFinish,.errorDetail,.copyMacAddress:
            return commonLang.finish
        default:
            return commonLang.next
        }
    }
    
    // MARK: - Lifecycle
    init() {
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
        // Observe app coming to foreground to refresh Wi-Fi status
        NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    self?.getWifiStatus()
                }
            }
            .store(in: &cancellables)
        getWifiStatus()
        subscribeToNetworkForm()
        fetchWifiScaleToken()
    }
    
    // MARK: - Configuration
    func configure(with sku: String) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        self.WifiSetupType = self.scaleItem?.setupType == .espTouchWifi ? .espTouchWifi : .first
        // Start at intro
        currentStepIndex = 0
        
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
        return currentStep == .intro
    }
    
    func handleNextButtonClick() {
        switch currentStep {
        case .intro:
            isForGetMac = false
            moveToNextStep()
            break
        case .permissions:
            if checkScaleToken() == nil {
                return
            }
            if isForGetMac {
                navigateToStep(.activatePairingMode)
            } else {
                moveToNextStep()
            }
        case .connectionConfirm:
            if permissionsSkipped || isForGetMac {
                selectedConnectionMode = .apMode
            }
            if selectedConnectionMode == .complete {
                self.navigateToStep(.stepOn)
            } else {
                self.navigateToStep(.apMode)
            }
            break
        case .apMode:
            if isForGetMac {
                Task {
                    await self.getMacAddress()
                    if retrievedMacAddress != nil {
                        self.navigateToStep(.copyMacAddress)
                    }
                }
            } else {
                moveToNextStep()
            }
            break
        case .apModeConfirm:
            self.navigateToStep(.stepOn)
        case .errorDetail:
            exitSetup()
            break
        case .stepOn:
            saveScale()
        default:
            moveToNextStep()
            break
        }
    }
    
    func handleBackButtonClick() {
        switch currentStep {
        case .activatePairingMode:
            if isForGetMac {
                arePermissionsEnabled() ? navigateToStep(.intro) : navigateToStep(.permissions)
            } else {
                moveToPreviousStep()
            }
            break
        case .errorSelect:
            if let origin = errorSelectSourceStep {
                navigateToStep(origin)
            } else {
                navigateToStep(.connectionConfirm)
            }
        case .copyMacAddress:
            navigateToStep(.apMode)
            break
        case .stepOn:
            if let origin = stepOnSourceStep {
                navigateToStep(origin)
            } else {
                moveToPreviousStep()
            }
        default:
            moveToPreviousStep()
            break
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
                    // User chose to skip – flag this so smart-connect can bail.
                    self?.permissionsSkipped = true
                    // Continue to next step.
                    self?.moveToNextStep()
                }
            ]
        )
        notificationService.showAlert(alert)
        // Note: `permissionsSkipped` is set inside the alert action above.
    }
    
    /// Starts observing the network form changes to update the next button state.
    private func subscribeToNetworkForm() {
        // Cancel previous subscription to avoid redundant updates
        networkFormCancellable?.cancel()
        
        networkFormCancellable = networkForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
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
            let tempKey = "ssidTemp"
            
            let status = await wifiScaleService.getConnectedWifiInfo()
            
            if let ssid = status.ssid, !ssid.isEmpty {
                let localStatus = kvStorage.getCodable(forKey: tempKey, as: WifiStatus.self)
                if let wifiStatus = localStatus {
                    if ssid != wifiStatus.ssid {
                        kvStorage.setCodable(status, forKey: tempKey)
                    }
                } else {
                    kvStorage.setCodable(status, forKey: tempKey)
                }
            }
            
            let wifiStatus = kvStorage.getCodable(forKey: tempKey, as: WifiStatus.self)
            self.wifiStatus = wifiStatus
            self.networkForm.setSSID(self.wifiStatus?.ssid ?? "")
        }
    }
    
    private func handleStepChange() {
        switch currentStep {
        case .connectionConfirm:
            Task { await startSmartConnect() }
        case .apModeConfirm:
            Task { await startApMode() }
        default:
            break
        }
    }
    
    private func openWifiSettings() {
        permissionsService.navigateToWifiSettings()
    }
    
    private func arePermissionsEnabled() -> Bool {
        // For WiFi setup, we need Location permission and switches enabled
        return permissionsService.getPermissionState(.LOCATION) == .ENABLED &&
        permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED &&
        permissionsService.getPermissionState(.WIFI_SWITCH) == .ENABLED
    }
    
    private func updateNextEnabled() {
        switch currentStep {
        case .permissions:
            // Evaluate location permissions
            let locationEnabled = permissionsService.getPermissionState(.LOCATION) == .ENABLED
            let locationSwitchEnabled = permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED
            let wifiSwitchEnabled = permissionsService.getPermissionState(.WIFI_SWITCH) == .ENABLED
            
            // Automatically request missing permissions
            if !locationEnabled {
                Task { await permissionsService.handlePermission(.location) }
            } else if !locationSwitchEnabled {
                Task { await permissionsService.handlePermission(.locationSwitch) }
            }
            
            // Enable Next button only when all permissions are granted
            isNextEnabled = locationEnabled && locationSwitchEnabled && wifiSwitchEnabled
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
                let scaleTokenResponse = try await wifiScaleService.getScaleToken(r: "4")
                self.scaleToken = scaleTokenResponse.token
                LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi scale token: \(scaleTokenResponse.token)")
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
                await self.scaleService.pushLocalChangesToServer()
                Task {
                    await self.pushNotificationService.setupPushNotifications(isFromScaleSetup: true)
                }
                moveToNextStep()
                logger.log(level: .info, tag: tag, message: "Scale saved successfully with ID: \(response.id) \(scaleItem.sku)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
                self.notificationService.showToast(ToastModel(message: ToastStrings.saveScaleError))
            }
        }
    }
    
    // MARK: - Smart-Connect
    /// Initiates the Wi-Fi smart-connect flow once the user confirms the
    /// connection screen.
    private func startSmartConnect() async {
        LoggerService.shared.log(level: .info, tag: tag, message: "startSmartConnect initiated – setupType: \(String(describing: self.WifiSetupType))")
        
        // If permissions were skipped, do NOT try to configure the scale.
        if permissionsSkipped { return }
        
        guard let setupType = self.WifiSetupType else {
            LoggerService.shared.log(level: .error, tag: tag, message: "startSmartConnect aborted – WifiSetupType not set")
            return
        }
        
        // Build the setup payload.
        let setupInfo = getSetupInfo(for: setupType)
        
        do {
            // Ensure any previous smart-connect sessions are stopped.
            await wifiScaleService.stop()
            
            // Cache SSID / BSSID for later use if required.
            // self.connectedSsid = setupInfo.ssid // This line was removed from the original file, so it's removed here.
            // self.connectedBssid = setupInfo.bssid // This line was removed from the original file, so it's removed here.
            if scaleItem?.setupType == .espTouchWifi {
                // TODO: Enable once ESP-Touch support lands.
                try await wifiScaleService.espSmartConnect(setupInfo, setupType)
            } else {
                try await wifiScaleService.smartConnect(setupInfo, setupType)
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "startSmartConnect error: \(error.localizedDescription)")
        }
    }
    
    /// Constructs the `WifiSetupInfo` payload depending on the selected
    /// `WifiSetupType`.
    private func getSetupInfo(for setupType: WifiSetupType) -> WifiSetupInfo {
        let hasPassword = !networkForm.networkHasNoPassword
        
        switch setupType {
        case .join:
            return WifiSetupInfo(ssid: nil,
                                 bssid: nil,
                                 password: nil,
                                 userNumber: selectedUserNumber,
                                 token: scaleToken)
            
        case .change:
            return WifiSetupInfo(ssid: networkForm.ssid.value,
                                 bssid: nil,
                                 password: hasPassword ? networkForm.password.value : nil,
                                 userNumber: nil,
                                 token: nil)
            
        default:
            return WifiSetupInfo(ssid: networkForm.ssid.value,
                                 bssid: wifiStatus?.bssid,
                                 password: hasPassword ? networkForm.password.value : nil,
                                 userNumber: selectedUserNumber,
                                 token: scaleToken)
        }
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
        
        // If the permission step was skipped we bail out, same as the original.
        if permissionsSkipped { return }
        
        guard let setupType = self.WifiSetupType else {
            logger.log(level: .error, tag: tag, message: "startApMode aborted – WifiSetupType not set")
            return
        }
        
        // Prepare the payload (mutating SSID/BSSID when available).
        let baseInfo = getSetupInfo(for: setupType)
        let info = WifiSetupInfo(
            ssid: wifiStatus?.ssid ?? baseInfo.ssid,
            bssid: wifiStatus?.bssid ?? baseInfo.bssid,
            password: baseInfo.password,
            userNumber: baseInfo.userNumber,
            token: baseInfo.token
        )
        
        do {
            // Stop any previous sessions before starting AP-mode.
            await wifiScaleService.stop()
            try await wifiScaleService.apMode(info, setupType)
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
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
