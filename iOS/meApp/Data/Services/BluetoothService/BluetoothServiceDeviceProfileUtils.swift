//
//  BluetoothServiceDeviceProfileUtils.swift
//  Device profile helpers: scale type, disconnect/skip, account-to-SDK profile conversion.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData

@MainActor
extension BluetoothService {
    // MARK: - Scale & Profile Helpers

    func getSafeScaleType(for device: Device) -> String? {
        guard let bathScale = device.bathScale else {
            return nil
        }
        return bathScale.scaleType
    }

    func disconnectDeletedScales(currentScales: [Device], newScales: [Device]) async {
        let accountId = activeAccount?.accountId ?? "unknown"

        let currentScalesForAccount = currentScales.filter { $0.accountId == accountId }
        let newScalesForAccount = newScales.filter { $0.accountId == accountId }

        let deletedScales = currentScalesForAccount.filter { currentScale in
            let found = newScalesForAccount.contains { newScale in
                currentScale.broadcastId == newScale.broadcastId
            }
            return !found
        }

        for scale in deletedScales {
            guard scale.accountId == accountId else {
                continue
            }

            if scale.isConnected ?? false {
                if let broadcastId = scale.broadcastIdString {
                    scale.isWeighOnlyModeEnabledByOthers = false
                    await scaleService.updateConnectedDeviceWeightOnlyMode(
                        broadcastId: broadcastId,
                        isWeightOnlyModeEnabledByOthers: false
                    )
                }
                let deleteResult = await deleteDevice(scale, disconnect: false)
                if case .failure(let error) = deleteResult {
                    logger.log(level: .error, tag: tag, message: "Failed to delete device: \(error.localizedDescription)")
                }

                guard let broadcastId = scale.broadcastIdString else { continue }
                let disconnectResult = await disconnectDevice(broadcastId: broadcastId)
                if case .failure(let error) = disconnectResult {
                    logger.log(level: .error, tag: tag, message: "Failed to disconnect device: \(error.localizedDescription)")
                }
            }
        }

        if !isWeightOnlyModeAlertDismissed {
            await checkCanShowWeightOnlyModeAlert()
        }
    }

    private func calculateAge(from dateString: String?) -> Int? {
        guard let dateString = dateString else { return nil }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        guard let birthDate = formatter.date(from: dateString) else {
            return nil
        }

        let today = Date()
        let calendar = Calendar.current

        var age = calendar.component(.year, from: today) - calendar.component(.year, from: birthDate)
        let monthDiff = calendar.component(.month, from: today) - calendar.component(.month, from: birthDate)
        let dayDiff = calendar.component(.day, from: today) - calendar.component(.day, from: birthDate)

        if monthDiff < 0 || (monthDiff == 0 && dayDiff < 0) {
            age -= 1
        }
        return age
    }

    func calculateHeightCm(height: String?) -> Int {
        let storedHeight: Int = {
            if let heightStr = height,
               let heightValue = Double(heightStr) {
                return Int(round(heightValue))
            }
            return 680
        }()
        return ConversionTools.convertStoredHeightToCm(storedHeight)
    }

    func createScanData(from account: Account?) -> ScanData? {
        guard let account = account else { return nil }
        let heightCm = calculateHeightCm(height: account.weightSettings?.height)
        let age = calculateAge(from: account.dob) ?? 30
        let isAthlete = account.weightSettings?.activityLevel?.rawValue == "athlete"
        let unit = account.weightSettings?.weightUnit?.rawValue ?? "kg"
        let sex = account.gender?.rawValue ?? "male"
        let goalWeight: Double? = {
            if let goalWeight = account.goalSettings?.goalWeight {
                return ConversionTools.convertStoredToDisplay(Int(goalWeight), isMetric: true)
            }
            return nil
        }()
        return ScanData(
            sex: sex,
            height: Double(heightCm),
            age: age,
            isAthlete: isAthlete,
            unit: unit,
            goalWeight: goalWeight,
            additionalInfo: nil
        )
    }

    func getProfileInfo(from account: Account) async -> GGBTUserProfile? {
        guard let scanData = createScanData(from: account) else {
            return nil
        }
        var currentWeight: Double?
        if let latest = try? await entryService.getLatestEntry(), let weight = latest.scaleEntry?.weight {
            currentWeight = ConversionTools.convertStoredToDisplay(weight, isMetric: true)
        }
        let name = account.firstName ?? "User"
        let goalType = account.goalSettings?.goalType?.rawValue
        return GGBTUserProfile(
            name: name,
            age: scanData.age,
            sex: scanData.sex,
            unit: scanData.unit,
            height: scanData.height,
            weight: currentWeight,
            goalWeight: scanData.goalWeight,
            isAthlete: scanData.isAthlete,
            goalType: goalType,
            metrics: nil
        )
    }

    func getWeightByProtocolType(protocolType: ProtocolType, weightInKg: Float, weight: Float) -> Int? {
        switch protocolType {
        case .A3:
            return Int(ConversionTools.convertBluetoothToStored(Double(weightInKg)) * 10)
        case .A6:
            return Int(ConversionTools.convertKgToStored(Double(weightInKg)))
        case .R4:
            return Int(ConversionTools.convertLbsToStored(Double(weight)))
        }
    }

    func disconnectDevice(broadcastId: String, considerForSession: Bool = true) async -> Result<Void, BluetoothServiceError> {
        if !skipDevices.contains(broadcastId) {
            skipDevices.append(broadcastId)
        }
        blockedBroadcastIds.insert(broadcastId)
        if let existing = unblockTasks[broadcastId] {
            existing.cancel()
        }
        let task = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(AppConstants.TimeoutsAndRetention.broadcastBlockDurationNs))
            await MainActor.run { [weak self] in
                _ = self?.blockedBroadcastIds.remove(broadcastId)
                self?.unblockTasks.removeValue(forKey: broadcastId)
            }
        }
        unblockTasks[broadcastId] = task
        setCanShowScaleDiscoveredModal(false)
        Task {
            try await Task.sleep(nanoseconds: UInt64(timeoutConstants.discoveredScaleModalTimeout))
            await MainActor.run {
                self.setCanShowScaleDiscoveredModal(true)
            }
        }
        ggBleSDK.skipDevice(broadcastId, considerForSession)
        return .success(())
    }

    func reapplySkipDevicesExcludingPaired() {
        let pairedIdsUpper = Set(bluetoothScales.compactMap { $0.broadcastIdString?.uppercased() })

        skipDevices = skipDevices.filter { !pairedIdsUpper.contains($0.uppercased()) }

        for id in skipDevices {
            ggBleSDK.skipDevice(id, true)
        }
    }
}
