import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the WiFi scale setup multi-step flow.
@MainActor
final class WifiScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var permissionsService: PermissionsService
    @Injector private var wifiScaleService: WifiScaleService
    @Injector private var accountService: AccountService
    
    let networkMonitor = NetworkMonitor.shared
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private let tag = "WifiScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let alertLang = AlertStrings.self
    private let commonLang = CommonStrings.self
    
    /// Active subscription to the network form changes
    private var networkFormCancellable: AnyCancellable? = nil
    
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
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    @Published var selectedUserNumber: Int?
    @Published var selectedErrorCode: String?
    @Published var selectedConnectionMode: WifiSetupOption = .none
    @Published var isApModeOnly: Bool = false
    
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
                return AnyView(ScaleSetupIntroView(scale: scaleItem))
            case .permissions:
                return AnyView(PermissionListView(setupType: .wifi))
            case .wifiPassword:
                return AnyView(WifiPasswordView(allowEditSsid: scaleItem.setupType == .espTouchWifi) {
                    // TODO: Implement on network change logic
                })
            case .selectUser:
                return AnyView(UserNumberSelectionView(selectedNumber: selectedUserNumber) { number in
                    self.selectedUserNumber = number
                })
            case .activatePairingMode:
                return AnyView(ActivatePairingModeView(sku: scaleItem.sku))
            case .connectionConfirm:
                return AnyView(WifiConnectionConfirmView(
                    sku: scaleItem.sku,
                    userNumber: selectedUserNumber,
                    selectedOption: selectedConnectionMode,
                    isApModeAlone: isApModeOnly,
                    
                ) { selectedMode in
                    self.selectedConnectionMode = selectedMode
                } onClickButton: {
                    // TODO: Implement on see something else button click
                })
                
            case .errorSelect:
                return AnyView(ErrorCodeSelectionView(selectedError: selectedErrorCode) { code in
                    self.selectedErrorCode = code
                })
            case .stepOn:
                return AnyView(BtSetupStepOnView())
            case .setupFinish:
                return AnyView(ScaleSetupFinishView(title: scaleSetupStrings.FinishViewStrings.title, description: scaleSetupStrings.FinishViewStrings.description))
            default:
                // Empty view placeholder for other steps
                return AnyView(EmptyView())
            }
        }
    }
    
    var nextButtonText: String {
        switch currentStep {
        case .setupFinish:
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
            }
            .store(in: &cancellables)
        
        networkMonitor.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)
        
        subscribeToNetworkForm()
    }
    
    // MARK: - Configuration
    func configure(with sku: String) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        
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
        moveToNextStep()
    }
    
    func handleBackButtonClick() {
        moveToPreviousStep()
    }
    
    private func handleStepChange() {
        // Handle any step-specific logic here
    }
    
    /// Handles the skip WiFi step action
    func handleSkipWifiStep() {
        let alertStrings = alertLang.SkipPermissionsAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.skipButton, type: .primary) { [weak self] _ in
                    // TODO: Implement skip logic
                }
            ]
        )
        notificationService.showAlert(alert)
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
        if let stepIndex = steps.firstIndex(of: step) {
            self.currentStepIndex = stepIndex
        }
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
        default:
            isNextEnabled = true
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
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
