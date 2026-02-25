import Combine
import Foundation
import SwiftUI

// swiftlint:disable cyclomatic_complexity function_body_length

@MainActor
extension BtWifiScaleSetupStore {
    func fetchWifiNetworks(for scale: Device) async {
        guard !isExiting, !Task.isCancelled else { return }

        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected

        if missingPermissions || noNetwork {
            LoggerService.shared.log(level: .error, tag: tag, message: "Cannot fetch WiFi networks: permissions missing or network unavailable")
            await MainActor.run {
                guard !self.isExiting, !Task.isCancelled else { return }
                self.setConnectionState(.noNetworks, allowNetworkErrors: false)
            }
            return
        }

        await MainActor.run {
            guard !self.isExiting, !Task.isCancelled else { return }
            self.connectionState = .loading
        }

        do {
            guard !isExiting, !Task.isCancelled else { return }

            let connectedSSIDResult = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")

            guard !isExiting, !Task.isCancelled else { return }

            var connectedSSID: String?
            switch connectedSSIDResult {
            case .success(let ssid):
                connectedSSID = ssid.isEmpty ? nil : ssid
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get connected WiFi SSID: \(error.localizedDescription)")
                connectedSSID = nil
            }

            guard !isExiting, !Task.isCancelled else { return }

            let wifiListResult = await bluetoothService.getWifiList(for: scale)

            guard !isExiting, !Task.isCancelled else { return }

            var networks: [WifiDetails] = []
            switch wifiListResult {
            case .success(let wifiList):
                networks = wifiList
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get WiFi networks: \(error.localizedDescription)")
                throw error
            }

            await MainActor.run {
                guard !self.isExiting, !Task.isCancelled else { return }

                let noNetwork = !self.networkMonitor.isConnected
                if noNetwork {
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                    self.scaleSetupError = .noNetworkFound
                    return
                }

                self.stepTimerTask?.cancel()
                self.stepTimerTask = nil

                self.wifiNetworks = networks

                if let connectedSSID = connectedSSID {
                    self.connectedWifiNetwork = WifiDetails(macAddress: "", ssid: connectedSSID, rssi: 0)
                } else {
                    self.connectedWifiNetwork = nil
                }

                guard !self.isExiting, !Task.isCancelled else { return }

                if networks.isEmpty {
                    self.scaleSetupError = .noNetworkFound
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                } else {
                    self.scaleSetupError = .none
                    self.connectionState = .success
                }

                guard !self.isExiting, !Task.isCancelled else { return }

                self.navigateToStep(.availableWifiList)
            }

            LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi networks: \(networks.count) networks found")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi networks: \(error.localizedDescription)")
            await MainActor.run {
                guard !self.isExiting, !Task.isCancelled else { return }

                let missingPermissions = !self.hasAllBtPermissions()
                let noNetwork = !self.networkMonitor.isConnected

                if missingPermissions || noNetwork {
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                } else {
                    self.setConnectionState(.failure, allowNetworkErrors: false)
                    self.scaleSetupError = .noNetworkFound
                }
            }
        }
    }

    /// Sets up WiFi on the scale
    func setupWifi() async {
        guard let scale = savedScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "setupWifi - no saved scale")
            connectionState = .failure
            return
        }

        let networkConfig = networkForm.getRawValue()

        LoggerService.shared.log(level: .info, tag: tag, message: "WiFi setup started for SSID: \(networkConfig.ssid)")
        let wifiSetupResult = await bluetoothService.setupWifi(on: scale, config: networkConfig)
        switch wifiSetupResult {
        case .success(let response):
            switch response.wifiState {
            case "GG_WIFI_STATE_CONNECTED":
                LoggerService.shared.log(level: .info, tag: tag, message: "WiFi connected for: \(networkConfig.ssid)")
                self.scaleSetupError = .none
                self.connectionState = .success
                self.errorCode = nil

                if let broadcastId = scale.broadcastIdString {
                    await scaleService.updateConnectedDeviceWifiStatus(broadcastId: broadcastId, isConfigured: true)
// swiftlint:disable:next line_length
                    LoggerService.shared.log(level: .info, tag: tag, message: "Updated WiFi configuration status to true for broadcast ID: \(broadcastId)")
                }

                let delay: TimeInterval = 2.0
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                    if self.isWifiSetupOnly {
                        self.dismissAction?()
                    } else {
                        self.navigateToStep(.customizeSettings)
                    }
                }
// swiftlint:disable:next switch_case_alignment
                default:
                LoggerService.shared.log(level: .error, tag: tag, message: "WiFi connection failed: \(response)")
                self.connectionState = .failure
                if let errorCode = response.errorCode {
                    self.errorCode = errorCode
                }
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "WiFi connection failed: \(error.localizedDescription)")
            self.connectionState = .failure
            self.errorCode = nil
        }
        self.resetNetworkForm()
    }

    /// Checks device info and WiFi configuration after WiFi setup for scale SKU 0412
    func checkDeviceInfoAfterWifiSetup(scale: Device) async -> Bool {
        var isWifiConfigured = false
        let result = await bluetoothService.getDeviceInfo(for: scale, skipConnectionCheck: true)
        switch result {
        case .success(let deviceInfo):
            isWifiConfigured = deviceInfo.isWifiConfigured ?? false
            LoggerService.shared.log(level: .info, tag: tag, message: "Device info after WiFi setup - WiFi configured: \(isWifiConfigured)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info after WiFi setup: \(error)")
        }
        return isWifiConfigured
    }

    /// Starts observing the network form changes to update the next button state.
    func subscribeToNetworkForm() {
        networkFormCancellable?.cancel()

        networkFormCancellable = networkForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
            }
    }

    /// Resets the network form to its initial state.
    func resetNetworkForm() {
        networkForm.reset()
        networkForm = NetworkForm()
        subscribeToNetworkForm()
    }

    /// Cancels Wi-Fi to hide connecting to wifi screen on 0412 scale.
    func cancelWifi() {
        let scaleToCancel = discoveredScale ?? savedScale
        if let scaleToCancel = scaleToCancel {
            Task {
                await bluetoothSetupManager.cancelWifi(on: scaleToCancel, bluetoothService: bluetoothService)
            }
        }
    }
}
// swiftlint:enable cyclomatic_complexity function_body_length
