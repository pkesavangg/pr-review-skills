import Foundation
import SwiftUI

/// Manages all user-related operations for scales
@MainActor
class ScaleUsersManager: ObservableObject {

    // MARK: - Dependencies
    @Injector private var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: ScaleUsersState

    // MARK: - Initialization
    init(initialState: ScaleUsersState = ScaleUsersState()) {
        self.state = initialState
    }

    // MARK: - User Management
    func fetchUserList(for scale: Device) async {
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleUsersManager", message: "Scale not connected, skipping user list fetch")
            return
        }
        
        do {
            let result = await bluetoothService.getScaleUserList(for: scale)
            switch result {
            case .success(let users):
                state.deviceUsers = users
                updateCurrentUserFromDeviceUsers(users, scale: scale)
                logger.log(level: .debug, tag: "ScaleUsersManager", message: "Fetched \(users.count) users")
            case .failure(let error):
                state.deviceUsers = []
                state.currentDeviceUser = nil
                logger.log(level: .error, tag: "ScaleUsersManager", message: "Failed to fetch users: \(error)")
            }
        }
    }

    func refreshUserList(for scale: Device) async {
        await fetchUserList(for: scale)
    }

    func deleteUser(_ user: DeviceUser, from scale: Device) async throws {
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleUsersManager", message: "Scale not connected, skipping user deletion")
            return
        }
        
        let deviceForDeletion = Device(
            id: scale.id,
            accountId: scale.accountId,
            peripheralIdentifier: nil,
            nickname: scale.nickname,
            sku: scale.sku,
            mac: scale.mac,
            password: scale.password,
            isDeleted: nil,
            deviceName: user.name,
            deviceType: scale.deviceType,
            broadcastId: scale.broadcastId,
            broadcastIdString: scale.broadcastIdString,
            userNumber: scale.userNumber,
            protocolType: scale.protocolType,
            createdAt: scale.createdAt,
            lastModified: nil,
            isSynced: nil,
            hasServerID: false,
            isConnected: scale.isConnected,
            wifiMac: scale.wifiMac,
            isWifiConfigured: nil,
            token: user.token,
            bathScale: scale.bathScale,
            r4ScalePreference: scale.r4ScalePreference,
            metaData: scale.metaData
        )
        
        let result = await bluetoothService.deleteDevice(deviceForDeletion, disconnect: false)
        
        switch result {
        case .success(let response):
            if response == .success {
                state.deviceUsers.removeAll { $0.token == user.token }
                logger.log(level: .info, tag: "ScaleUsersManager", message: "Deleted user: \(user.name)")
            } else {
                logger.log(level: .error, tag: "ScaleUsersManager", message: "Failed to delete user: \(response)")
                throw ScaleUserError.userDeletionFailed
            }
        case .failure(let error):
            logger.log(level: .error, tag: "ScaleUsersManager", message: "Failed to delete user: \(error)")
            throw ScaleUserError.userDeletionFailed
        }
    }

    func updateCurrentUserName(_ newName: String, for scale: Device) async throws {
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleUsersManager", message: "Scale not connected, skipping user name update")
            return
        }
        
        let updatedPreference = R4ScalePreference(
            scaleId: scale.id,
            displayName: newName,
            displayMetrics: scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys,
            shouldFactoryReset: false,
            shouldMeasureImpedance: scale.r4ScalePreference?.shouldMeasureImpedance ?? true,
            shouldMeasurePulse: scale.r4ScalePreference?.shouldMeasurePulse ?? false,
            timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
            tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
        )
        
        let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
        switch result {
        case .success(let response):
            if response == .creationCompleted {
                state.currentDeviceUser = DeviceUser(
                    name: newName,
                    token: state.currentDeviceUser?.token,
                    lastActive: state.currentDeviceUser?.lastActive ?? Int(Date().timeIntervalSince1970),
                    isBodyMetricsEnabled: state.currentDeviceUser?.isBodyMetricsEnabled ?? true
                )
                logger.log(level: .info, tag: "ScaleUsersManager", message: "Updated user name to: \(newName)")
            } else {
                logger.log(level: .error, tag: "ScaleUsersManager", message: "Failed to update user name: \(response)")
                throw ScaleUserError.userUpdateFailed
            }
        case .failure(let error):
            logger.log(level: .error, tag: "ScaleUsersManager", message: "Failed to update user name: \(error)")
            throw ScaleUserError.userUpdateFailed
        }
    }

    // MARK: - Computed Properties
    var otherDeviceUsersList: [DeviceUser] {
        guard let currentUser = state.currentDeviceUser else { return state.deviceUsers }
        return state.deviceUsers.filter { $0.token != currentUser.token }
    }

    var usersValue: String {
        guard let currentUser = state.currentDeviceUser else { return "" }
        return currentUser.name
    }

    // MARK: - Private Methods
    private func updateCurrentUserFromDeviceUsers(_ users: [DeviceUser], scale: Device) {
        guard let deviceToken = scale.token else {
            state.currentDeviceUser = nil
            return
        }
        
        if let matchingUser = users.first(where: { $0.token == deviceToken }) {
            state.currentDeviceUser = matchingUser
        } else if let displayName = scale.r4ScalePreference?.displayName {
            state.currentDeviceUser = DeviceUser(
                name: displayName,
                token: deviceToken,
                lastActive: Int(Date().timeIntervalSince1970),
                isBodyMetricsEnabled: scale.r4ScalePreference?.shouldMeasureImpedance ?? true
            )
        }
    }
}

// MARK: - Scale Errors
enum ScaleUserError: Error {
    case userDeletionFailed
    case userUpdateFailed
} 