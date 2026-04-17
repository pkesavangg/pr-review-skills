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
        case .intro, .permissions, .scaleName, .paired, .babyProfile, .babyAdded, .connectionError:
            break
        case .wakeup:
            startBluetoothScan()
        case .connectingBluetooth:
            // Loading state — pairing is triggered directly from device discovery.
            break
        }
    }

    // MARK: - Bluetooth Scanning

    func startBluetoothScan() {
        // If a pre-discovered scale was injected via configure(), skip scanning
        // and proceed directly to pairing.
        if let event = discoveryEvent, discoveredScale != nil {
            handleDeviceDiscovery(event)
            return
        }

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
                    self.navigateToStep(.connectionError)
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
        self.discoveredScale = event.device.toDevice()

        if event.isNew {
            Task {
                connectionState = .loading
                await confirmPair()
            }
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
                navigateToStep(.scaleName)
            default:
                LoggerService.shared.log(level: .error, tag: tag, message: "Baby scale pairing response: \(response)")
                connectionState = .failure
                scaleSetupError = .pairingFailed
                navigateToStep(.connectionError)
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Baby scale pairing failed: \(error)")
            connectionState = .failure
            scaleSetupError = .connectionFailed
            navigateToStep(.connectionError)
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
            let deviceInfoResult = await bluetoothService.getDeviceInfo(broadcastId: scale.broadcastIdString ?? "", skipConnectionCheck: true)
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
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            LoggerService.shared.log(level: .info, tag: tag, message: "Baby scale saved: \(device.id)")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save baby scale: \(error)")
            isScaleSaved = false
        }
    }

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
                AlertButtonModel(title: commonLang.cancel, type: .secondary) { [weak self] _ in
                    self?.navigateToStep(.intro)
                },
                AlertButtonModel(title: "Continue", type: .primary) { [weak self] _ in
                    self?.moveToNextStep()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
}
