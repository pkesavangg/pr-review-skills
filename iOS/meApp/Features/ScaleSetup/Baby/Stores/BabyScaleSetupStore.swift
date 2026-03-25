//
//  BabyScaleSetupStore.swift
//  meApp
//

import Combine
import Foundation
import SwiftUI

/// Store responsible for orchestrating the Baby scale setup multi-step flow.
@MainActor
final class BabyScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var permissionsService: PermissionsServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var accountService: AccountServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var babyService: BabyServiceProtocol

    let lang = BabyScaleSetupStrings.self
    let commonLang = CommonStrings.self
    let tag = "BabyScaleSetupStore"

    /// Resolved scale metadata used across the setup flow.
    var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?
    /// Discovered scale information.
    var discoveredScale: Device?
    /// Discovery event from Bluetooth service.
    var discoveryEvent: DeviceDiscoveryEvent?

    // MARK: - Private
    var cancellables = Set<AnyCancellable>()
    var deviceDiscoveryCancellable: AnyCancellable?
    /// Task handling Bluetooth scan timeout.
    var scanTimeoutTask: Task<Void, Never>?
    /// Flag to track if scale has been saved (prevents duplicates).
    var isScaleSaved: Bool = false

    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            guard !isRevertingStepIndex else { return }
            if isExiting {
                if oldValue != currentStepIndex {
                    let previousIndex = oldValue
                    isRevertingStepIndex = true
                    Task { @MainActor [weak self] in
                        guard let self, self.isExiting else {
                            self?.isRevertingStepIndex = false
                            return
                        }
                        self.currentStepIndex = previousIndex
                        self.isRevertingStepIndex = false
                    }
                }
                return
            }
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }

    @Published private(set) var currentStep: BabyScaleSetupStep = .intro {
        didSet {
            guard !isExiting else { return }
            previousStep = oldValue
            handleStepChange()
        }
    }

    var previousStep: BabyScaleSetupStep = .intro
    @Published var isExiting: Bool = false
    var isRevertingStepIndex: Bool = false

    @Published var connectionState: ConnectionState = .loading
    @Published var savedScale: Device?
    @Published var scaleSetupError: BabyScaleSetupError = .none
    @Published private(set) var steps: [BabyScaleSetupStep] = BabyScaleSetupStep.allCases
    @Published var isNextEnabled: Bool = true

    // MARK: - Forms
    @Published var scaleNicknameForm = ScaleNicknameForm()
    @Published var babyProfileForm = BabyProfileSetupForm()

    // MARK: - Baby Profile State
    @Published var savedBabies: [Baby] = []
    @Published var editingBaby: Baby?

    // MARK: - Computed Properties

    /// View builders for each step, consumed by SwiperView.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(BabyScaleIntroView(scale: scaleItem))
            case .permissions:
                return AnyView(PermissionListView(setupType: .bluetooth))
            case .wakeup:
                return AnyView(BabyScaleScanningView())
            case .connectingBluetooth:
                return AnyView(
                    BluetoothConnectionView(
                        state: connectionState,
                        setupType: .babyScale,
                        onTryAgain: { [weak self] in
                            self?.tryAgainButtonHandler()
                        },
                        onSupport: { [weak self] in
                            self?.showHelpModal()
                        }
                    )
                )
            case .scaleName:
                return AnyView(BabyScaleNameView())
            case .paired:
                return AnyView(BabyPairedSuccessView())
            case .babyProfile:
                return AnyView(BabyProfileFormView())
            case .babyAdded:
                return AnyView(BabyAddedListView())
            }
        }
    }

    // MARK: - Helpers

    /// Updates the enabled state of the footer "Next" button based on the current step.
    func updateNextEnabled() {
        switch currentStep {
        case .intro, .wakeup, .connectingBluetooth, .paired, .babyAdded:
            isNextEnabled = true
        case .permissions:
            isNextEnabled = arePermissionsEnabled()
        case .scaleName:
            isNextEnabled = scaleNicknameForm.isValid
        case .babyProfile:
            isNextEnabled = babyProfileForm.isProfileValid
        }
    }

    /// Shows the help alert.
    func showHelpModal() {
        notificationService.showAlert(AlertModel(
            title: "Need Help?",
            message: lang.Intro.troubleSettingUp,
            buttons: [AlertButtonModel(title: commonLang.ok, type: .primary, action: { _ in })]
        ))
    }
}
