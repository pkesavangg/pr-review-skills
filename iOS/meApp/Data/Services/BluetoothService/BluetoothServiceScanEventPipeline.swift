//
//  BluetoothServiceScanEventPipeline.swift
//  Smart scan, scan response handling, WiFi/permission events, new device, entry save/conversion, weight-only mode.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData
// swiftlint:disable cyclomatic_complexity file_length

@MainActor
extension BluetoothService {
    func startSmartScan() async throws {
        guard let activeAccount = activeAccount else {
            throw BluetoothServiceError.noActiveAccount
        }
        guard let accountData = await getProfileInfo(from: activeAccount) else {
            throw BluetoothServiceError.noProfileInfo
        }
        ggBleSDK.scan(.ME_HEALTH, accountData) { [weak self] result in
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
            ggBleSDK.skipDevice(bid, true)
            return
        }
        guard let responseType = data.type else { return }
        let scanData = data.data
        switch responseType {
        case .NEW_DEVICE:
            await handleNewDevice(scanData)
        case .SINGLE_ENTRY:
            logger.log(level: .info, tag: tag, message: "SINGLE_ENTRY called in handleSmartScaleData", data: scanData)
            await saveEntries(scanData)
        case .MULTI_ENTRIES:
            logger.log(level: .info, tag: tag, message: "MULTI_ENTRIES called in handleSmartScaleData", data: scanData)
            await saveEntries(scanData)
        case .KNOWN_DEVICE:
            break
        case .DEVICE_CONNECTED:
            logger.log(level: .info, tag: tag, message: "DEVICE_CONNECTED called in handleSmartScaleData", data: scanData)
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            if let deviceDetails = data.data as? GGDeviceDetails {
                // Weight-only mode only applies to adult weight scales, not baby scales
                let deviceType = resolveDeviceType(broadcastId: deviceDetails.broadcastIdString)
                if deviceType != .babyScale {
                    await updateWeightOnlyModeStatusFromDeviceDetails(deviceDetails)
                }
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
            logger.log(level: .info, tag: tag, message: "DEVICE_DISCONNECTED called in handleSmartScaleData", data: scanData)
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
        // Discovery creates a temporary device model. A3 BPMs may not have a stable
        // hex broadcast ID yet, so we preserve the raw discovery identifier for pairing
        // and patch in the stable post-connect fields later.
        let device = mapDeviceDetailsToDevice(deviceDetails, isA3Device: deviceDetails.protocolType == "A3")
        let protocolType = ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .A6

        let accountId = activeAccount?.accountId ?? ""
        let isKnown = bluetoothScales.contains { scale in
            scale.broadcastIdString == deviceDetails.broadcastId
            && scale.accountId == accountId
        }
        let isNew = !isKnown

        guard let scaleInfo else {
            logger.log(level: .error, tag: tag, message: "Scale info not found for discovered device")
            return
        }

        let category: DeviceCategory = scaleInfo.setupType == .bpm ? .bpm : .scale

        let discoveryEvent = DeviceDiscoveryEvent(
            device: device.toSnapshot(),
            deviceInfo: scaleInfo,
            protocolType: protocolType,
            isNew: isNew,
            deviceCategory: category
        )

        deviceDiscoveredSubject.send(discoveryEvent)
    }

    private func mapDeviceDetailsToDevice(_ deviceDetails: GGDeviceDetails, isA3Device: Bool = false) -> Device {
        let accountId = activeAccount?.accountId ?? ""
        let broadcastId = convertHexToInt(deviceDetails.broadcastId ?? "")
        return mapDeviceDetailsToDevice(deviceDetails, accountId: accountId, isA3Device: isA3Device, broadcastId: broadcastId)
    }

    private func saveEntries(_ entriesData: GGScanResponseData) async {
        // Handle BPM blood pressure measurements
        if let bpmData = entriesData as? GGBPMEntry {
            await saveSingleBpmEntry(bpmData)
            return
        }

        // Handle BPM blood pressure measurement history (multiple entries)
        if let bpmEntryList = entriesData as? GGBPMEntryList {
            await saveBpmEntryList(bpmEntryList)
            return
        }

        if let weightEntry = entriesData as? GGEntry {
            await saveSingleWeightEntry(weightEntry)
        } else if let entryList = entriesData as? GGEntryList {
            await saveWeightEntryList(entryList)
        }
    }

    private func saveSingleBpmEntry(_ bpmData: GGBPMEntry) async {
        handleBpmMeasurement(bpmData)
        let timestamp = bpmData.date.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) } ?? Date()
        logger.log(level: .info, tag: tag, message: "BP readings received from device: count=1")
        // Live single readings stage as pending — the toast handler will confirm/discard.
        await stagePendingBpmEntry(BpmMeasurement(
            systolic: bpmData.systolic ?? 0,
            diastolic: bpmData.diastolic ?? 0,
            pulse: bpmData.pulse ?? 0,
            timestamp: timestamp,
            broadcastId: bpmData.broadcastId
        ), batchCount: 1)
    }

    private func saveBpmEntryList(_ bpmEntryList: GGBPMEntryList) async {
        if bpmEntryList.list.isEmpty {
            logger.log(level: .info, tag: tag, message: "No valid BPM entries to save")
            return
        }
        let batchCount = bpmEntryList.list.count
        logger.log(level: .info, tag: tag, message: "BP readings received from device: count=\(batchCount)")
        // Mirror the weight-list pattern: persist historical entries silently and surface
        // only the most recent reading as a pending confirmation toast.
        let measurements = bpmEntryList.list.map { bpmData -> BpmMeasurement in
            let timestamp = bpmData.date.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) } ?? Date()
            return BpmMeasurement(
                systolic: bpmData.systolic ?? 0,
                diastolic: bpmData.diastolic ?? 0,
                pulse: bpmData.pulse ?? 0,
                timestamp: timestamp,
                broadcastId: bpmData.broadcastId
            )
        }
        for bpmData in bpmEntryList.list {
            handleBpmMeasurement(bpmData)
        }
        let historical = measurements.dropFirst()
        for measurement in historical {
            await persistBpmEntry(measurement)
        }
        if let latest = measurements.first {
            await stagePendingBpmEntry(latest, batchCount: batchCount)
        }
    }

    private func saveSingleWeightEntry(_ weightEntry: GGEntry) async {
        guard let entry = convertGGEntry(weightEntry) else { return }

        if entry.entryType == EntryType.baby.rawValue {
            // Baby entries are saved immediately; the assign/discard toast is driven by newEntryReceivedSubject.
            do {
                try await entryService.saveNewEntry(entry)
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to save baby entry. entryId=\(entry.id.uuidString)",
                    data: error.localizedDescription
                )
                return
            }
            newEntryReceivedSubject.send(EntryNotification(from: entry))
            return
        }

        logger.log(level: .info, tag: tag, message: "Weight readings received from device: count=1")
        // Weight entries: hold pending user confirmation via the toast.
        // If a previous entry is still awaiting confirmation, save it automatically
        // before overwriting — the user only sees one toast at a time.
        if let existing = pendingScaleEntry {
            do {
                try await entryService.saveNewEntry(existing)
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Auto-saved displaced pending entry. entryId=\(existing.id.uuidString)"
                )
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to auto-save displaced pending entry. entryId=\(existing.id.uuidString)",
                    data: error.localizedDescription
                )
            }
        }
        pendingScaleEntry = entry
        pendingScaleEntrySubject.send(EntryNotification(from: entry, batchCount: 1))
    }

    private func saveWeightEntryList(_ entryList: GGEntryList) async { // swiftlint:disable:this function_body_length
        let entries = entryList.list.compactMap { convertGGEntry($0) }
        if entries.isEmpty {
            logger.log(level: .info, tag: tag, message: "No valid entries to save")
            return
        }
        logger.log(level: .info, tag: tag, message: "Weight readings received from device: count=\(entries.count)")

        // Baby-scale batches: all entries are saved immediately and the most recent fires
        // newEntryReceivedSubject for the assign/discard card. Mirror saveSingleWeightEntry logic.
        if let firstEntry = entries.first, firstEntry.entryType == EntryType.baby.rawValue {
            let historicalEntries = entries.dropFirst()
            for entry in historicalEntries {
                do {
                    try await entryService.saveNewEntry(entry)
                } catch {
                    logger.log(
                        level: .error,
                        tag: tag,
                        message: "Failed to save historical baby entry. entryId=\(entry.id.uuidString)",
                        data: error.localizedDescription
                    )
                }
            }
            do {
                try await entryService.saveNewEntry(firstEntry)
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to save baby entry. entryId=\(firstEntry.id.uuidString)",
                    data: error.localizedDescription
                )
                return
            }
            newEntryReceivedSubject.send(EntryNotification(from: firstEntry))
            return
        }

        // Weight/BPM batches: save historical entries immediately; hold the most recent
        // (first in the list) pending user confirmation via the toast.
        let historicalEntries = entries.dropFirst()
        for entry in historicalEntries {
            do {
                try await entryService.saveNewEntry(entry)
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to save historical entry. entryId=\(entry.id.uuidString)",
                    data: error.localizedDescription
                )
            }
        }
        if let latestEntry = entries.first {
            // Auto-save any displaced pending entry before replacing it.
            if let existing = pendingScaleEntry {
                do {
                    try await entryService.saveNewEntry(existing)
                    logger.log(
                        level: .info,
                        tag: tag,
                        message: "Auto-saved displaced pending entry. entryId=\(existing.id.uuidString)"
                    )
                } catch {
                    logger.log(
                        level: .error,
                        tag: tag,
                        message: "Failed to auto-save displaced pending entry. entryId=\(existing.id.uuidString)",
                        data: error.localizedDescription
                    )
                }
            }
            pendingScaleEntry = latestEntry
            let notification = EntryNotification(from: latestEntry, batchCount: entries.count)
            pendingScaleEntrySubject.send(notification)
        }
    }

    private func convertGGEntry(_ ggEntry: GGEntry) -> Entry? {
        guard let activeAccount = activeAccount else {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.noActiveAccount.localizedDescription)
            return nil
        }
        let deviceType = resolveDeviceType(broadcastId: ggEntry.broadcastIdString)
        let entryDate = resolveEntryDate(ggEntry: ggEntry, deviceType: deviceType)
        let timestamp = ISO8601DateFormatter().string(from: entryDate)

        // Baby scale entries get a BabyEntry relationship instead of BathScaleEntry
        if deviceType == .babyScale {
            return convertBabyScaleEntry(ggEntry: ggEntry, activeAccount: activeAccount, timestamp: timestamp)
        }

        let resolvedEntryType: EntryType = deviceType == .bpm ? .bpm : .scale
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            entryType: resolvedEntryType.rawValue,
            isSynced: false
        )
        let protocolType = ProtocolType(rawValue: ggEntry.protocolType ?? "") ?? .A6
        let scaleEntry = createBathScaleEntry(ggEntry: ggEntry, protocolType: protocolType, activeAccount: activeAccount)
        let scaleMetric = createBathScaleMetric(ggEntry: ggEntry)
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric
        return entry
    }

    private func resolveEntryDate(ggEntry: GGEntry, deviceType: DeviceType) -> Date {
        if deviceType == .babyScale {
            return Date()
        } else if let epoch = ggEntry.date {
            return Date(timeIntervalSince1970: TimeInterval(epoch) / 1000)
        } else {
            return Date()
        }
    }

    private func createBathScaleEntry(ggEntry: GGEntry, protocolType: ProtocolType, activeAccount: AccountSnapshot) -> BathScaleEntry {
        var sourceType = ScaleSourceType.bluetoothScale
        if protocolType == .R4 {
            sourceType = .btWifiR4
        }
        return BathScaleEntry(
            weight: getWeightByProtocolType(protocolType: protocolType, weightInKg: ggEntry.weightInKg, weight: ggEntry.weight),
            bodyFat: roundMetric(ggEntry.bodyFat),
            muscleMass: roundMetric(ggEntry.muscleMass),
            water: roundMetric(ggEntry.water),
            bmi: ggEntry.bmi > 0
            ? roundMetric(ggEntry.bmi)
            : ConversionTools.calculateBMI(
                weight: Double(ggEntry.weightInKg),
                height: calculateHeightCm(height: activeAccount.weightHeight)
            ),
            source: sourceType.rawValue
        )
    }

    private func createBathScaleMetric(ggEntry: GGEntry) -> BathScaleMetric {
        return BathScaleMetric(
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
    }

    private func resolveDeviceType(broadcastId: String?) -> DeviceType {
        guard let broadcastId = broadcastId else { return .scale }
        if let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }),
           let sku = scale.sku {
            let scaleInfo = ScaleInfoUtils.shared.getScaleInfo(bySku: sku)
            if scaleInfo?.setupType == .babyScale {
                return .babyScale
            }
            if scaleInfo?.setupType == .bpm {
                return .bpm
            }
        }
        return .scale
    }

    /// Resolves the DeviceSnapshot for the given broadcast ID from the paired bluetooth scales.
    private func resolveDevice(forBroadcastId broadcastId: String?) -> DeviceSnapshot? {
        guard let broadcastId = broadcastId else { return nil }
        return bluetoothScales.first { $0.broadcastIdString == broadcastId }
    }

    /// Finds the Baby linked to the device broadcasting with the given ID.
    /// Chain: broadcastId → Device (bluetoothScales) → Device.id → Baby.deviceId → Baby
    private func resolveBaby(forBroadcastId broadcastId: String?) -> Baby? {
        guard let device = resolveDevice(forBroadcastId: broadcastId) else { return nil }
        return babyService.currentBabies.first { $0.deviceId == device.id }
    }

    /// Resolves the scale SKU for the device broadcasting with the given ID.
    private func resolveScaleSku(forBroadcastId broadcastId: String?) -> String? {
        return resolveDevice(forBroadcastId: broadcastId)?.sku
    }

    /// Converts a BLE baby scale measurement into a baby Entry with a BabyEntry relationship.
    /// If no baby profile is linked to the scale the entry is still created with an empty babyId
    /// so the reading-arrival toast can surface it — the user can then ASSIGN or DON'T ASSIGN.
    private func convertBabyScaleEntry(ggEntry: GGEntry, activeAccount: AccountSnapshot, timestamp: String) -> Entry? {
        let baby = resolveBaby(forBroadcastId: ggEntry.broadcastIdString)
        if baby == nil {
            logger.log(
                level: .info,
                tag: tag,
                // swiftlint:disable:next line_length
                message: "Baby scale entry received but no baby linked for broadcastId: \(ggEntry.broadcastIdString ?? "nil") — creating unassigned entry"
            )
        }
        let weightDecigrams = ConversionTools.convertBabyKgToDecigrams(Double(ggEntry.weightInKg))
        let scaleSku = resolveScaleSku(forBroadcastId: ggEntry.broadcastIdString)
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: false
        )
        entry.babyEntry = BabyEntry(babyId: baby?.id ?? "", length: 0, weight: weightDecigrams, source: scaleSku)
        return entry
    }

    // MARK: - Weight-only mode (alert, preference sync, status on connect/disconnect)
    func checkCanShowWeightOnlyModeAlert() async {
        weightOnlyModeAlertDebounceTask?.cancel()

        let debounceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)

            guard !Task.isCancelled, let self = self else { return }

            let connectedScales = self.bluetoothScales.filter { scale in
                guard scale.isConnected else { return false }
                // Exclude baby scales — weight-only mode only applies to adult weight scales
                if let sku = scale.sku,
                   ScaleInfoUtils.shared.getScaleInfo(bySku: sku)?.setupType == .babyScale {
                    return false
                }
                return true
            }

            var hasWeightOnlyModeEnabledByOthers = false

            for scale in connectedScales where scale.isWeighOnlyModeEnabledByOthers {
                hasWeightOnlyModeEnabledByOthers = true
                break
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

    private func syncPreferencesIfNeeded(for scale: DeviceSnapshot, deviceInfo: DeviceInfo) async {
        guard !isSyncingPreferences else {
            return
        }
        isSyncingPreferences = true
        defer { isSyncingPreferences = false }

        guard scale.isConnected,
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
        switch await updateAccount(broadcastId: broadcastId) {
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
        guard let resolvedScale = resolveScaleForWeightOnlyMode(deviceDetails) else {
            return
        }
        let scale = resolvedScale.scale
        let broadcastId = resolvedScale.broadcastId

        try? await Task.sleep(nanoseconds: 200_000_000)

        let shouldMeasureImpedance: Bool = {
            if let pref = scale.r4ScalePreference {
                if let fetched = fetchAttachedPreference(by: pref.id) { return fetched.shouldMeasureImpedance }
                return pref.shouldMeasureImpedance
            }
            return false
        }()

        let updatedDeviceInfoResult = await getDeviceInfo(broadcastId: broadcastId, skipConnectionCheck: true)
        let finalImpedanceSwitchState: Bool
        if case .success(let updatedInfo) = updatedDeviceInfoResult {
            finalImpedanceSwitchState = updatedInfo.impedanceSwitchState ?? false
        } else {
            finalImpedanceSwitchState = shouldMeasureImpedance
        }

        let isWeightOnlyModeEnabledByOthers = !finalImpedanceSwitchState && shouldMeasureImpedance

        await scaleService.updateConnectedDeviceWeightOnlyMode(
            broadcastId: broadcastId,
            isWeightOnlyModeEnabledByOthers: isWeightOnlyModeEnabledByOthers
        )

        logger.log(level: .debug, tag: tag, message: "Updated weight-only mode status for scale \(broadcastId): \(isWeightOnlyModeEnabledByOthers)")
    }

    private func updateWeightOnlyModeStatusFromDeviceDetails(_ deviceDetails: GGDeviceDetails) async {
        guard let resolvedScale = resolveScaleForWeightOnlyMode(deviceDetails) else {
            return
        }
        let scale = resolvedScale.scale
        let broadcastId = resolvedScale.broadcastId

        let deviceInfoResult = await getDeviceInfo(broadcastId: broadcastId, skipConnectionCheck: true)
        switch deviceInfoResult {
        case .success(let deviceInfo):
            await syncPreferencesIfNeeded(for: scale, deviceInfo: deviceInfo)
            await updateWeightOnlyModeStatus(deviceDetails: deviceDetails, deviceInfo: deviceInfo)
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to get device info for weight-only mode calculation: \(error)")
        }
    }

    private func clearWeightOnlyModeStatusOnDisconnect(_ deviceDetails: GGDeviceDetails) async {
        guard let resolvedScale = resolveScaleForWeightOnlyMode(deviceDetails) else {
            return
        }
        let broadcastId = resolvedScale.broadcastId

        await scaleService.updateConnectedDeviceWeightOnlyMode(
            broadcastId: broadcastId,
            isWeightOnlyModeEnabledByOthers: false
        )

        logger.log(level: .debug, tag: tag, message: "Cleared weight-only mode status for disconnected scale \(broadcastId)")
    }

    private func resolveScaleForWeightOnlyMode(_ deviceDetails: GGDeviceDetails) -> (scale: DeviceSnapshot, broadcastId: String)? {
        let candidateIds = [deviceDetails.broadcastIdString, deviceDetails.broadcastId]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        guard !candidateIds.isEmpty else {
            logger.log(level: .error, tag: tag, message: "Cannot update weight-only mode status: missing broadcast ID")
            return nil
        }

        guard let scale = bluetoothScales.first(where: { scale in
            guard let storedBroadcastId = scale.broadcastIdString else {
                return false
            }
            return candidateIds.contains(storedBroadcastId)
        }) else {
            logger.log(level: .error, tag: tag, message: "Scale not found for broadcast ID candidates: \(candidateIds.joined(separator: ", "))")
            return nil
        }

        return (scale, scale.broadcastIdString ?? candidateIds[0])
    }
}
// swiftlint:enable cyclomatic_complexity file_length
