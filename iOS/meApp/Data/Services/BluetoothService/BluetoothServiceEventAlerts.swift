//
//  BluetoothServiceEventAlerts.swift
//  Device event alerts: reconnect, duplicate user, find/delete user by token.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData

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

        let scaleInfo = scaleInfoUtils.getScaleInfo(byScaleName: deviceDetails.deviceName)
        guard let discoveredScale = bluetoothScales.first(where: { $0.broadcastIdString == deviceDetails.broadcastIdString }) else {
            logger.log(level: .error, tag: tag, message: "Discovered scale not found in bluetoothScales")
            return
        }

        let userListResult = await getScaleUserList(for: discoveredScale, skipConnectionCheck: true)
        guard case .success(let userList) = userListResult else {
            logger.log(level: .error, tag: tag, message: "Failed to get scale user list for device event alert")
            return
        }

        let userToDelete = findUserToDelete(userList: userList, discoveredScale: discoveredScale)

        let openScaleSetup: () -> Void = { [weak self] in
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
                    deviceInfo: scaleInfo ?? ScaleItemInfo(
                        productName: deviceDetails.deviceName,
                        sku: "0412",
                        imgPath: AppAssets.meLogoDark,
                        setupType: .btWifiR4,
                        bodyComp: true
                    ),
                    protocolType: ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .R4,
                    isNew: true
                )

                self.onOpenScaleSetup?(discoveredScale, deviceDiscoveryEvent, true, isDuplicateUserError)
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
                        openScaleSetup()
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
                        openScaleSetup()
                    }
                ]
            )
        }

        notificationService.showAlert(alert)
    }

    func findUserToDelete(userList: [DeviceUser], discoveredScale: Device) -> DeviceUser? {
        return userList.first { user in
            bluetoothScales.contains { scale in
                let isR4Scale: Bool = {
                    if let raw = getSafeScaleType(for: scale) { return ScaleSourceType(rawValue: raw) == .btWifiR4 }
                    return false
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
        let tempDevice = Device(
            id: UUID().uuidString,
            accountId: activeAccount?.accountId ?? "",
            mac: nil,
            deviceName: nil,
            broadcastId: nil,
            broadcastIdString: broadcastId,
            isConnected: false
        )
        tempDevice.token = token

        return await deleteDevice(tempDevice, disconnect: disconnect)
    }
}
