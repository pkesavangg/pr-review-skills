//
//  BluetoothServiceScanEventPipeline.swift
//  Smart scan, scan response handling, WiFi/permission events, new device, entry save/conversion, weight-only mode.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData
// swiftlint:disable cyclomatic_complexity

@MainActor
extension BluetoothService {
    func startSmartScan() async throws {
        guard let activeAccount = activeAccount else {
            throw BluetoothServiceError.noActiveAccount
        }
        guard let accountData = await getProfileInfo(from: activeAccount) else {
            throw BluetoothServiceError.noProfileInfo
        }

        ggBleSDK.scan(.WEIGHT_GURUS, accountData) { [weak self] result in
            Task { @MainActor in
                switch result {
                case .success(let scanResponse):
                    await self?.handleSmartScaleData(scanResponse)
                case .failure(let error):
                    self?.logger.log(
                        level: .error,
                        tag: self?.tag ?? "BluetoothService",
                        message: BluetoothServiceError.scanFailed(error).localizedDescription
                    )
                }
            }
        }
        isSmartScanStarted = true
    }

    // swiftlint:disable:next function_body_length
    private func handleSmartScaleData(_ data: GGScanResponse) async {
        var bid: String?
        if let details = data.data as? GGDeviceDetails {
            bid = details.broadcastIdString
        } else if let entry = data.data as? GGWeightEntry {
            bid = entry.broadcastIdString
        }
        if let bid = bid, blockedBroadcastIds.contains(bid) {
            ggBleSDK.skipDevice(bid)
            return
        }
        guard let responseType = data.type else { return }
        let scanData = data.data
        switch responseType {
        case .NEW_DEVICE:
            await handleNewDevice(scanData)
        case .SINGLE_ENTRY:
            await saveEntries(scanData)
        case .MULTI_ENTRIES:
            await saveEntries(scanData)
        case .KNOWN_DEVICE:
            break
        case .DEVICE_CONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            if let deviceDetails = data.data as? GGDeviceDetails {
                await updateWeightOnlyModeStatusFromDeviceDetails(deviceDetails)
            }
            await checkCanShowWeightOnlyModeAlert()
        case .DEVICE_DISCONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: false)
            if let deviceDetails = data.data as? GGDeviceDetails {
                await clearWeightOnlyModeStatusOnDisconnect(deviceDetails)
            }
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .DEVICE_MEMORY_FULL:
            await handleDeviceEventAlert(scanData, isDuplicateUserError: false)
        case .DEVICE_DUPLICATE_USER:
            await handleDeviceEventAlert(scanData, isDuplicateUserError: true)
        case .WIFI_STATUS_UPDATE:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            if let deviceDetails = data.data as? GGDeviceDetails {
                await updateWeightOnlyModeStatusFromDeviceDetails(deviceDetails)
            }
            await handleWifiStatusUpdate(scanData)
        case .DEVICE_INFO_UPDATE:
            await scaleService.updateConnectedDevices(device: scanData, isConnected: true)

            guard let deviceDetails = data.data as? GGDeviceDetails else {
                logger.log(level: .error, tag: tag, message: "DEVICE_INFO_UPDATE: Failed to cast data to GGDeviceDetails")
                return
            }
            let deviceInfo = DeviceInfo(sdk: deviceDetails)
            await updateWeightOnlyModeStatus(deviceDetails: deviceDetails, deviceInfo: deviceInfo)

            deviceInfoUpdatedSubject.send(deviceInfo)
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .PERMISSION_STATUS:
            await handlePermissionStatus(scanData)
        case .DEVICE_WAKE_UP:
            break
        case .LIVE_MEASUREMENT:
            if let liveData = data.data as? GGWeightEntry {
                liveMeasurementSubject.send(liveData)
            } else {
                logger.log(level: .error, tag: tag, message: "Failed to get live measurement data")
            }
        }
    }

    private func handleWifiStatusUpdate(_ deviceData: GGScanResponseData) async {
        guard let wifiStatus = parseWifiStatus(deviceData) else { return }
        await scaleService.updateConnectedDeviceWifiStatus(
            broadcastId: wifiStatus.broadcastId,
            isConfigured: wifiStatus.isConfigured
        )
    }

    private func handlePermissionStatus(_ permissionData: GGScanResponseData) async {
        guard let permissionResponse = parsePermissionStatus(permissionData) else { return }
        let permissionStatus = permissionResponse.permissions
        PermissionsService.shared.setPermissions(permissionStatus)
        logger.log(level: .debug, tag: tag, message: "Permission status updated: \(permissionStatus)")
    }

    private func handleNewDevice(_ deviceData: GGScanResponseData) async {
        guard let deviceDetails = deviceData as? GGDeviceDetails else { return }

        let scaleInfo = ScaleInfoUtils.shared.getScaleInfo(byScaleName: deviceDetails.deviceName)
        let device = mapDeviceDetailsToDevice(deviceDetails, isA3Device: deviceDetails.protocolType == "A3")
        let protocolType = ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .A6

        let isKnown = bluetoothScales.contains { scale in
            scale.broadcastIdString == deviceDetails.broadcastId
        }
        let isNew = !isKnown

        guard let scaleInfo else {
            logger.log(level: .error, tag: tag, message: "Scale info not found for discovered device")
            return
        }

        let discoveryEvent = DeviceDiscoveryEvent(
            device: device,
            deviceInfo: scaleInfo,
            protocolType: protocolType,
            isNew: isNew
        )

        deviceDiscoveredSubject.send(discoveryEvent)
    }

    private func mapDeviceDetailsToDevice(_ deviceDetails: GGDeviceDetails, isA3Device: Bool = false) -> Device {
        let accountId = activeAccount?.accountId ?? ""
        let broadcastId = convertHexToInt(deviceDetails.broadcastId ?? "")
        return mapDeviceDetailsToDevice(deviceDetails, accountId: accountId, isA3Device: isA3Device, broadcastId: broadcastId)
    }

    private func saveEntries(_ entriesData: GGScanResponseData) async {
        if let weightEntry = entriesData as? GGEntry {
            let entry = convertGGEntry(weightEntry)
            guard let entry = entry else {
                return
            }
            try? await entryService.saveNewEntry(entry)
            let notification = EntryNotification(from: entry)
            newEntryReceivedSubject.send(notification)
        } else if let entryList = entriesData as? GGEntryList {
            let entries = entryList.list.compactMap { convertGGEntry($0) }
            if entries.isEmpty {
                logger.log(level: .info, tag: tag, message: "No valid entries to save")
                return
            }
            for entry in entries {
                try? await entryService.saveNewEntry(entry)
            }
            if !entries.isEmpty {
                let notification = EntryNotification(from: entries[0])
                newEntryReceivedSubject.send(notification)
            }
        }
    }

    private func convertGGEntry(_ ggEntry: GGEntry) -> Entry? {
        guard let activeAccount = activeAccount else {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.noActiveAccount.localizedDescription)
            return nil
        }
        let entryDate = if let epoch = ggEntry.date {
            Date(timeIntervalSince1970: TimeInterval(epoch) / 1000)
        } else {
            Date()
        }
        let timestamp = ISO8601DateFormatter().string(from: entryDate)

        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.scale.rawValue,
            isSynced: false
        )
        let protocolType = ProtocolType(rawValue: ggEntry.protocolType ?? "") ?? .A6
        var sourceType = ScaleSourceType.bluetoothScale
        if protocolType == .R4 {
            sourceType = .btWifiR4
        }

        let scaleEntry = BathScaleEntry(
            weight: getWeightByProtocolType(protocolType: protocolType, weightInKg: ggEntry.weightInKg, weight: ggEntry.weight),
            bodyFat: roundMetric(ggEntry.bodyFat),
            muscleMass: roundMetric(ggEntry.muscleMass),
            water: roundMetric(ggEntry.water),
            bmi: ggEntry.bmi > 0
                ? roundMetric(ggEntry.bmi)
                : ConversionTools.calculateBMI(
                    weight: Double(ggEntry.weightInKg),
                    height: calculateHeightCm(height: activeAccount.weightSettings?.height)
                ),
            source: sourceType.rawValue
        )
        let scaleMetric = BathScaleMetric(
            bmr: ggEntry.bmr * 10,
            metabolicAge: ggEntry.metabolicAge,
            proteinPercent: roundMetric(ggEntry.proteinPercent),
            pulse: ggEntry.pulse,
            skeletalMusclePercent: roundMetric(ggEntry.skeletalMusclePercent),
            subcutaneousFatPercent: roundMetric(ggEntry.subcutaneousFatPercent),
            visceralFatLevel: ggEntry.visceralFatLevel * 10,
            boneMass: roundMetric(ggEntry.boneMass),
            impedance: roundMetric(ggEntry.impedance),
            unit: ggEntry.unit.lowercased()
        )
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric
        return entry
    }

    // MARK: - Weight-only mode (alert, preference sync, status on connect/disconnect)
    func checkCanShowWeightOnlyModeAlert() async {
        weightOnlyModeAlertDebounceTask?.cancel()

        let debounceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)

            guard !Task.isCancelled, let self = self else { return }

            let connectedScales = self.bluetoothScales.filter { scale in (scale.isConnected ?? false) }

            var hasWeightOnlyModeEnabledByOthers = false

            for scale in connectedScales {
                if let isWeightOnlyEnabled = scale.isWeighOnlyModeEnabledByOthers, isWeightOnlyEnabled {
                    hasWeightOnlyModeEnabledByOthers = true
                    break
                }
            }

            if hasWeightOnlyModeEnabledByOthers && !self.isWeightOnlyModeAlertDismissed {
                self.showWeightOnlyModeAlertSubject.send(true)
            } else {
                self.showWeightOnlyModeAlertSubject.send(false)
            }
        }

        weightOnlyModeAlertDebounceTask = debounceTask
        await debounceTask.value
    }

    func handleWeightOnlyModeAlertDismissed() {
        isWeightOnlyModeAlertDismissed = true
        showWeightOnlyModeAlertSubject.send(false)
    }

    private func syncPreferencesIfNeeded(for scale: Device, deviceInfo: DeviceInfo) async {
        guard !isSyncingPreferences else {
            return
        }
        isSyncingPreferences = true
        defer { isSyncingPreferences = false }

        guard scale.isConnected == true,
              let preference = fetchAttachedPreference(by: scale.id)
        else {
            return
        }

        let impedanceSwitchState = deviceInfo.impedanceSwitchState ?? false
        let hasMismatch = preference.shouldMeasureImpedance != impedanceSwitchState

        guard hasMismatch else {
            return
        }
        let broadcastId = scale.broadcastIdString ?? "unknown"
        switch await updateAccount(on: scale, preference: preference) {
        case .success:
            logger.log(level: .info, tag: tag, message: "Synced preference settings to scale \(broadcastId)")
            preference.isSynced = true
            await Task { @MainActor in
                do {
                    try await scaleService.updateScalePreference(scale.id, preference)
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to update preference sync status: \(error)")
                }
            }.value
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to sync preference settings to scale \(broadcastId): \(error)")
        }
    }

    private func updateWeightOnlyModeStatus(deviceDetails: GGDeviceDetails, deviceInfo: DeviceInfo) async {
        guard let broadcastId = deviceDetails.broadcastId else {
            logger.log(level: .error, tag: tag, message: "Cannot update weight-only mode status: missing broadcast ID")
            return
        }

        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }) else {
            logger.log(level: .error, tag: tag, message: "Scale not found for broadcast ID: \(broadcastId)")
            return
        }

        try? await Task.sleep(nanoseconds: 200_000_000)

        let shouldMeasureImpedance: Bool = {
            if let pref = scale.r4ScalePreference {
                if let fetched = fetchAttachedPreference(by: pref.id) { return fetched.shouldMeasureImpedance }
                return pref.shouldMeasureImpedance
            }
            return false
        }()

        let updatedDeviceInfoResult = await getDeviceInfo(for: scale, skipConnectionCheck: true)
        let finalImpedanceSwitchState: Bool
        if case .success(let updatedInfo) = updatedDeviceInfoResult {
            finalImpedanceSwitchState = updatedInfo.impedanceSwitchState ?? false
        } else {
            finalImpedanceSwitchState = shouldMeasureImpedance
        }

        let isWeightOnlyModeEnabledByOthers = !finalImpedanceSwitchState && shouldMeasureImpedance

        scale.isWeighOnlyModeEnabledByOthers = isWeightOnlyModeEnabledByOthers

        await scaleService.updateConnectedDeviceWeightOnlyMode(
            broadcastId: broadcastId,
            isWeightOnlyModeEnabledByOthers: isWeightOnlyModeEnabledByOthers
        )

        logger.log(level: .debug, tag: tag, message: "Updated weight-only mode status for scale \(broadcastId): \(isWeightOnlyModeEnabledByOthers)")
    }

    private func updateWeightOnlyModeStatusFromDeviceDetails(_ deviceDetails: GGDeviceDetails) async {
        guard let broadcastId = deviceDetails.broadcastId else {
            logger.log(level: .error, tag: tag, message: "Cannot update weight-only mode status: missing broadcast ID")
            return
        }

        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }) else {
            logger.log(level: .error, tag: tag, message: "Scale not found for broadcast ID: \(broadcastId)")
            return
        }

        let deviceInfoResult = await getDeviceInfo(for: scale, skipConnectionCheck: true)
        switch deviceInfoResult {
        case .success(let deviceInfo):
            await syncPreferencesIfNeeded(for: scale, deviceInfo: deviceInfo)
            await updateWeightOnlyModeStatus(deviceDetails: deviceDetails, deviceInfo: deviceInfo)
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to get device info for weight-only mode calculation: \(error)")
        }
    }

    private func clearWeightOnlyModeStatusOnDisconnect(_ deviceDetails: GGDeviceDetails) async {
        guard let broadcastId = deviceDetails.broadcastId else {
            return
        }

        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }) else {
            return
        }

        scale.isWeighOnlyModeEnabledByOthers = false

        await scaleService.updateConnectedDeviceWeightOnlyMode(
            broadcastId: broadcastId,
            isWeightOnlyModeEnabledByOthers: false
        )

        logger.log(level: .debug, tag: tag, message: "Cleared weight-only mode status for disconnected scale \(broadcastId)")
    }
}
// swiftlint:enable cyclomatic_complexity
