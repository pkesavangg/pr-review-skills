//
//  BluetoothServiceEventAlerts.swift
//  Device event alerts: reconnect, duplicate user, find/delete user by token.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage

@MainActor
extension BluetoothService {
    // swiftlint:disable:next cyclomatic_complexity
    func handleDeviceEventAlert(_ deviceData: GGScanResponseData, isDuplicateUserError: Bool) async { // swiftlint:disable:this function_body_length

        guard let deviceDetails = deviceData as? GGDeviceDetails, !isSetupInProgress else {
            logger.log(level: .error, tag: tag, message: "Invalid device data for event alert")
            return
        }

        if skipDevices.contains(deviceDetails.broadcastIdString) || reconnectAlertSkippedDevices.contains(deviceDetails.broadcastIdString) {
            return
        }

        let scaleInfo = scaleInfoUtils.getDeviceInfo(byDeviceName: deviceDetails.deviceName)
        guard let discoveredScale = bluetoothScales.first(where: { $0.broadcastIdString == deviceDetails.broadcastIdString }) else {
            logger.log(level: .error, tag: tag, message: "Discovered scale not found in bluetoothScales")
            return
        }

        // The duplicate-user / reconnect + delete-user flow below is a weight-scale (R4) feature
        // — `findUserToDelete` only matches R4 scales, and `getScaleUserList` (called next) crashes
        // the SDK for BPM monitors. BPM monitors surface duplicate users via their own setup flow,
        // so bail out here rather than driving the scale flow against a monitor.
        if scaleInfo?.setupType == .bpm {
            logger.log(
                level: .info,
                tag: tag,
                message: "Ignoring scale user-event alert for BPM monitor \(deviceDetails.broadcastIdString)"
            )
            return
        }

        let userListResult = await getScaleUserList(broadcastId: discoveredScale.broadcastIdString ?? "", skipConnectionCheck: true)
        guard case .success(let userList) = userListResult else {
            logger.log(level: .error, tag: tag, message: "Failed to get scale user list for device event alert")
            return
        }

        let userToDelete = findUserToDelete(userList: userList, discoveredScale: discoveredScale)

        let openDeviceSetup: () -> Void = { [weak self] in
            guard let self = self else { return }

            Task { @MainActor in
                self.notificationService.dismissAllModals()

                if let userToDelete = userToDelete, let token = userToDelete.token, !token.isEmpty {
                    let response = await self.deleteScaleByBroadcastId(
                        broadcastId: discoveredScale.broadcastIdString ?? "",
                        token: token,
                        disconnect: false
                    )

                    switch response {
                    case .failure:
                        self.logger.log(level: .error, tag: self.tag, message: "Failed to delete user from scale during event alert")
                        return
                    default:
                        break
                    }
                }

                let deviceDiscoveryEvent = DeviceDiscoveryEvent(
                    device: discoveredScale,
                    deviceInfo: scaleInfo ?? DeviceItemInfo(
                        productName: deviceDetails.deviceName,
                        sku: "0412",
                        imgPath: AppAssets.meLogoDark,
                        setupType: .btWifiR4,
                        bodyComp: true
                    ),
                    protocolType: ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .R4,
                    isNew: true
                )

                self.onOpenDeviceSetup?(discoveredScale, deviceDiscoveryEvent, true, isDuplicateUserError)
            }
        }

        setCanShowScaleDiscoveredModal(false)

        let alertStrings = AlertStrings.self
        let alert: AlertModel

        if isDuplicateUserError {
            alert = AlertModel(
                title: alertStrings.DuplicateUserAlert.header,
                message: alertStrings.DuplicateUserAlert.message,
                buttons: [
                    AlertButtonModel(title: alertStrings.DuplicateUserAlert.cancelButton, type: .secondary) { _ in
                        Task {
                            if let broadcastId = discoveredScale.broadcastIdString {
                                self.reconnectAlertSkippedDevices.append(broadcastId)
                                _ = await self.disconnectDevice(broadcastId: broadcastId)
                            }
                        }
                    },
                    AlertButtonModel(title: alertStrings.DuplicateUserAlert.reconnectButton, type: .primary) { _ in
                        openDeviceSetup()
                    }
                ]
            )
        } else {
            alert = AlertModel(
                title: alertStrings.ReconnectDeviceAlert.header,
                message: alertStrings.ReconnectDeviceAlert.message,
                buttons: [
                    AlertButtonModel(title: alertStrings.ReconnectDeviceAlert.cancelButton, type: .secondary) { _ in
                        Task {
                            if let broadcastId = discoveredScale.broadcastIdString {
                                self.reconnectAlertSkippedDevices.append(broadcastId)
                                _ = await self.disconnectDevice(broadcastId: broadcastId)
                            }
                        }
                    },
                    AlertButtonModel(title: alertStrings.ReconnectDeviceAlert.reconnectButton, type: .primary) { _ in
                        openDeviceSetup()
                    }
                ]
            )
        }

        notificationService.showAlert(alert)
    }

    func findUserToDelete(userList: [DeviceUser], discoveredScale: DeviceSnapshot) -> DeviceUser? {
        return userList.first { user in
            bluetoothScales.contains { scale in
                let isR4Scale: Bool = {
                    guard let raw = scale.bathScale?.scaleType else { return false }
                    return DeviceSourceType(rawValue: raw) == .btWifiR4
                }()
                let namesMatch: Bool = {
                    if let pref = scale.r4ScalePreference {
                        if let fetched = fetchAttachedPreference(by: pref.id) { return user.name.lowercased() == fetched.displayName.lowercased() }
                        return user.name.lowercased() == pref.displayName.lowercased()
                    }
                    return false
                }()
                let idsMatch = discoveredScale.broadcastId == scale.broadcastId

                return isR4Scale && namesMatch && idsMatch
            }
        }
    }

    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        return await deleteScaleByBroadcastId(broadcastId: broadcastId, token: token, disconnect: disconnect)
    }

    func deleteScaleByBroadcastId(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        let ggDevice = mapToGGBTDevice(broadcastId)
        ggDevice.token = token
        do {
            let result = try await sdkOperationSerializer.execute(
                operationKey: "\(broadcastId):deleteUser"
            ) { @MainActor in
                try await self.withTimeout(seconds: 10) {
                    try await self.ggBleSDK.deleteUser(ggDevice, canDisconnect: disconnect)
                }
            }
            return .success(UserDeletionResponse(sdkType: result))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
}
