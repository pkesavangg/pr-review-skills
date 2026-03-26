//
//  BabyScaleSetupStorePairingFlow.swift
//  meApp
//

import Combine
import Foundation

@MainActor
extension BabyScaleSetupStore {

    // MARK: - Step Change Handler

    func handleStepChange() {
        guard !isExiting else { return }

        switch currentStep {
        case .intro, .permissions, .scaleName, .paired, .babyProfile, .babyAdded:
            break
        case .wakeup:
            // TODO: Re-enable BLE scanning when API is ready
            // startBluetoothScan()
            simulateScanAndPair()
        case .connectingBluetooth:
            // TODO: Re-enable BLE pairing when API is ready
            // connectionState = .loading
            // Task {
            //     if discoveredScale != nil && discoveryEvent != nil {
            //         await confirmPair()
            //     } else {
            //         connectionState = .failure
            //     }
            // }
            break
        }
    }

    // MARK: - UI-Only Simulation (API not working)

    /// Simulates scan + pair flow: shows scanning UI briefly, then skips straight to scale name.
    private func simulateScanAndPair() {
        Task { @MainActor in
            // Show scanning animation for 2 seconds
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !isExiting else { return }
            // Skip connectingBluetooth and go directly to scaleName
            isScaleSaved = true
            navigateToStep(.scaleName)
        }
    }

    // MARK: - Bluetooth Scanning (disabled — API not working)

    /*
    func startBluetoothScan() {
        resetDiscoveryState()
        Task { bluetoothService.scanForPairing() }

        if deviceDiscoveryCancellable == nil {
            deviceDiscoveryCancellable = bluetoothService.deviceDiscoveredPublisher
                .receive(on: DispatchQueue.main)
                .sink { [weak self] discoveryEvent in
                    self?.handleDeviceDiscovery(discoveryEvent)
                }
        }

        // Timeout: 60 seconds
        scanTimeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 60_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self else { return }
                if self.discoveredScale == nil && self.currentStep == .wakeup {
                    self.navigateToStep(.connectingBluetooth)
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 250_000_000)
                        self.connectionState = .failure
                    }
                }
            }
        }
    }

    // MARK: - Device Discovery

    func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        guard currentStep == .wakeup else { return }
        guard event.deviceInfo.setupType == .babyScale else { return }

        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        scanTimeoutTask?.cancel()

        self.discoveryEvent = event
        self.discoveredScale = event.device

        if event.isNew {
            moveToNextStep()
        } else {
            showKnownScaleAlert()
        }
    }

    // MARK: - Pairing

    func confirmPair() async {
        guard let scale = discoveredScale, discoveryEvent != nil else {
            LoggerService.shared.log(level: .error, tag: tag, message: "confirmPair - missing discovery event or scale")
            connectionState = .failure
            return
        }

        let displayName = accountService.activeAccount?.firstName ?? "User"

        let pairResult = await bluetoothService.confirmSmartPair(
            device: scale,
            token: "",
            displayName: displayName,
            userNumber: nil
        )

        switch pairResult {
        case .success(let response):
            switch response {
            case .creationCompleted:
                LoggerService.shared.log(level: .info, tag: tag, message: "Baby scale pairing completed")
                await saveScale()
                connectionState = .success
                scaleSetupError = .none
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    self.navigateToStep(.scaleName)
                }
            default:
                LoggerService.shared.log(level: .error, tag: tag, message: "Baby scale pairing response: \(response)")
                connectionState = .failure
                scaleSetupError = .pairingFailed
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Baby scale pairing failed: \(error)")
            connectionState = .failure
            scaleSetupError = .connectionFailed
        }
    }

    // MARK: - Save Scale

    func saveScale() async {
        guard !isScaleSaved, let scale = discoveredScale, let event = discoveryEvent else { return }
        isScaleSaved = true

        let accountId = accountService.activeAccount?.accountId ?? ""
        let scaleID = String(DateTimeTools.getCurrentTimestampMillis())
        let displayName = accountService.activeAccount?.firstName ?? "User"

        do {
            var deviceMetadata: DeviceMetaData?
            let deviceInfoResult = await bluetoothService.getDeviceInfo(for: scale, skipConnectionCheck: true)
            if case .success(let deviceInfo) = deviceInfoResult {
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
            }

            let device = try await scaleService.createR4Scale(
                scaleId: scaleID,
                accountId: accountId,
                displayName: displayName,
                token: "",
                mac: scale.mac,
                broadcastIdString: scale.broadcastIdString,
                broadcastId: scale.broadcastId,
                sku: scaleItem?.sku ?? event.device.sku,
                deviceName: event.deviceInfo.productName,
                wifiMac: nil,
                deviceMetadata: deviceMetadata,
                isWifiConfigured: false,
                isConnected: true,
                skipDuplicateCheck: false
            )
            self.savedScale = device
            await scaleService.syncAllScalesWithRemote()
            LoggerService.shared.log(level: .info, tag: tag, message: "Baby scale saved: \(device.id)")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save baby scale: \(error)")
            isScaleSaved = false
        }
    }
    */

    // MARK: - Helpers

    func resetDiscoveryState() {
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        discoveredScale = nil
        discoveryEvent = nil
        scanTimeoutTask?.cancel()
    }

    private func showKnownScaleAlert() {
        let alert = AlertModel(
            title: "Scale Already Paired",
            message: "This scale is already paired to your account. Would you like to set it up again?",
            buttons: [
                AlertButtonModel(title: commonLang.cancel, type: .secondary, action: { [weak self] _ in
                    self?.navigateToStep(.intro)
                }),
                AlertButtonModel(title: "Continue", type: .primary, action: { [weak self] _ in
                    self?.moveToNextStep()
                })
            ]
        )
        notificationService.showAlert(alert)
    }
}
