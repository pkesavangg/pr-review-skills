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
            handleViewSettingsBack()
        }
        moveToPreviousStep()
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
