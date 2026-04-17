import Combine
import Foundation
import SwiftUI

@MainActor
extension BtWifiScaleSetupStore {
    func handleBackButtonClick() {
        if handleSettingsWifiSetupExit() {
            return
        }

        if currentStep == .wifiPassword {
            resetNetworkForm()
        } else if currentStep == .viewSettings {
            performViewSettingsBack()
        }
        moveToPreviousStep()
    }

    /// Handles the save action from the view settings screen
    func handleViewSettingsAction() {
        performViewSettingsSave()
    }

    /// Handles the back action from the view settings screen
    func handleViewSettingsBack() {
        performViewSettingsBack()
    }

    /// Handles the restore account action from the duplicate user screen
    func handleRestoreAccount() {
        let alertStrings = alertLang.ConfirmRestoreAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.backButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.restoreButton, type: .primary) { [weak self] _ in
                    Task {
                        await self?.performRestoreAccount()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Handles the delete user action from the max user count exceeded screen
    func handleDeleteUser(_ user: DeviceUser) {
        let alertStrings = alertLang.ConfirmDeleteUserAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message(user.name),
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.deleteButton, type: .danger) { [weak self] _ in
                    Task {
                        await self?.deleteUserFromScale(user)
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 250_000_000)
                            self?.scaleSetupError = .none
                        }
                        self?.navigateToStep(.connectingBluetooth)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Handles the skip WiFi step action
    func handleSkipWifiStep() {
        let alertStrings = alertLang.SkipWifiStepAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.skipButton, type: .primary) { [weak self] _ in
                    self?.cancelWifi()
                    self?.scaleSetupError = .none
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 100_000_000)
                        self?.navigateToStep(.customizeSettings)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Handles the WiFi password connect action
    func handleWifiPasswordConnect() {
        guard networkForm.ssid.isValid else { return }
        if !networkForm.networkHasNoPassword {
            guard networkForm.password.isValid else { return }
        }

        connectionState = .loading
        navigateToStep(.connectingWifi)
    }

    /// Handles the network selection from the WiFi list
    func handleNetworkSelection(_ network: WifiDetails) {
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected

        if missingPermissions || noNetwork {
            connectionState = .noNetworks
            navigateToStep(.gatheringNetwork)
            return
        }

        selectedWifiNetwork = network
        networkForm.setSSID(selectedWifiNetwork?.ssid ?? "")
        navigateToStep(.wifiPassword)
        updateNextEnabled()
    }

    // MARK: - Exit & Navigation Actions

    /// Called by the ✕ button.
    func handleExit() {
        if currentStep == .stepOn {
            isExitingFromStepOn = true
            cancelStepOnTimeout()
            cancelMeasurementSubscription()
            if let savedScale = savedScale {
                Task.detached(priority: .userInitiated) { [weak self] in
                    guard let self else { return }
                    _ = await self.bluetoothService.stopLiveMeasurement(broadcastId: savedScale.broadcastIdString ?? "")
                }
            }
            scaleSetupError = .none
            isExiting = true
            dismissAction?()
            Task { @MainActor [weak self] in
                self?.performExitCleanup()
            }
            return
        }

        isExiting = true
        cancelNetworkScanTimeout()
        fetchWifiNetworksTask?.cancel()
        fetchWifiNetworksTask = nil

        if handleSettingsWifiSetupExit() {
            return
        }

        guard currentStep != .scaleConnected else {
            performExitCleanup()
            return
        }
        presentExitAlert(
            onConfirm: { [weak self] in
                self?.performExitCleanup()
            },
            onCancel: { [weak self] in
                guard let self else { return }
                self.isExiting = false
            }
        )
    }

    /// Presents the standard exit-alert.
    func presentExitAlert(
        onConfirm: @escaping () -> Void,
        onCancel: @escaping () -> Void = {}
    ) {
        let lang = AlertStrings.ExitBtWifiSetupAlert.self
        let message: String = {
            switch (isWifiSetupOnly, savedScale != nil) {
            case (true, _):  return lang.wifiExitMessage
            case (false, true):  return lang.postConnectionExitMessage
            default:  return lang.preConnectionExitMessage
            }
        }()

        let alert = AlertModel(
            title: lang.title,
            message: message,
            buttons: [
                AlertButtonModel(title: lang.exitButton, type: .primary) { _ in onConfirm() },
                AlertButtonModel(title: lang.goBackButton, type: .secondary) { _ in onCancel() }
            ])
        notificationService.showAlert(alert)
    }

    /// Handles exit from Settings WiFi setup when on available WiFi list. Returns true if exit was handled.
    func handleSettingsWifiSetupExit() -> Bool {
        guard isSettingsWifiSetup && currentStep == .availableWifiList else {
            return false
        }

        presentExitAlert(
            onConfirm: { [weak self] in
                self?.cancelWifi()
                self?.scaleSetupError = .none
                self?.dismissAction?()
                Task { @MainActor [weak self] in
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    self?.connectionState = .success
                }
            },
            onCancel: {}
        )
        return true
    }

    /// Handles the next button click based on the current step.
    func handleNextButtonClick() { // swiftlint:disable:this cyclomatic_complexity function_body_length
        switch currentStep {
        case .intro:
            let hasPermissions = hasAllBtPermissions()
            let hasNetwork = networkMonitor.isConnected
            if !hasPermissions || !hasNetwork {
                navigateToStep(.permissions)
            } else {
                navigateToStep(.wakeup)
            }
        case .gatheringNetwork:
            if scaleSetupError == .duplicatesFound {
                handleSaveDuplicateUser()
            } else {
                moveToNextStep()
            }
        case .availableWifiList:
            if connectedWifiNetwork != nil {
                cancelWifi()
                scaleSetupError = .none
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 100_000_000)
                    self.navigateToStep(.customizeSettings)
                }
            } else {
                moveToNextStep()
            }
        case .wifiPassword:
            handleWifiPasswordConnect()
        case .viewSettings:
            handleViewSettingsAction()
        case .customizeSettings:
            persistDashboardMetricsIfNeeded()
            if hasSavedSettings {
                navigateToStep(.updateSettings)
            } else {
                navigateToStep(.stepOn)
            }
        case .scaleConnected:
            Task { @MainActor in
                NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 500_000_000)
                    self.dismissAction?()
                    self.checkGoalModalAfterSetup()
                    self.bluetoothService.isSetupInProgress = false
                }
            }
        case .permissions:
            moveToNextStep()
        case .wakeup:
            moveToNextStep()
        case .connectingBluetooth:
            moveToNextStep()
        default:
            moveToNextStep()
        }
    }

    /// Invoked from the *Try Again* button of `BluetoothConnectionView` and `WifiConnectionView` failure state.
    func tryAgainButtonHandler(isFromBtConnection: Bool = false) {
        guard !isExiting else { return }

        let targetStep: BtWifiScaleSetupStep
        if isFromBtConnection {
            if discoveredScale != nil {
                targetStep = .connectingBluetooth
            } else {
                targetStep = .wakeup
            }
        } else if scaleSetupError == .collectMeasurementFailed {
            targetStep = .stepOn
        } else if scaleSetupError == .updateSettingsFailed {
            targetStep = .customizeSettings
        } else {
            targetStep = .gatheringNetwork

            fetchWifiNetworksTask?.cancel()
            fetchWifiNetworksTask = nil

            isRefreshingWifiNetworks = true
            scaleSetupError = .none
            connectionState = .loading
        }

        if scaleSetupError != .collectMeasurementFailed && targetStep != .gatheringNetwork {
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 250_000_000)
                self.scaleSetupError = .none
                self.connectionState = .loading
            }
        }

        navigateToStep(targetStep)
    }
}
