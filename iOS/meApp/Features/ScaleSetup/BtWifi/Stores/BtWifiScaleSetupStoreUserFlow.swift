import Combine
import Foundation
import SwiftUI

@MainActor
extension BtWifiScaleSetupStore {
    /// Gets the account name to restore, using the original name that exists on the scale
    /// (not the edited duplicateUserName, since restore should use the original account name)
    func getAccountNameForRestore() -> String {
        if let originalName = currentUser?.name, !originalName.isEmpty {
            return originalName.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return (firstName ?? "User").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Finds a matching user on the scale by comparing account name (handles name truncation)
    func findMatchingUserOnScale(scale: Device, accountName: String) async -> DeviceUser? {
        let userListResult = await bluetoothService.getScaleUserList(
            broadcastId: scale.broadcastIdString ?? "",
            skipConnectionCheck: true,
            sku: scale.sku ?? discoveryEvent?.deviceInfo.sku ?? scaleItem?.sku
        )
        guard case .success(let allUsers) = userListResult else {
            return nil
        }

        let normalizedAccountName = accountName.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let maxScaleNameLength = 20
        let truncatedAccountName = String(normalizedAccountName.prefix(maxScaleNameLength))

        return allUsers.first { $0.name.lowercased() == normalizedAccountName }
            ?? allUsers.first { $0.name.lowercased() == truncatedAccountName }
    }

    /// Deletes a matching user from the scale during restore account flow
    func deleteMatchingUserFromScale(scale: Device, user: DeviceUser) async -> Bool {
        guard let userToken = user.token, !userToken.isEmpty else {
            return false
        }
        guard let broadcastId = scale.broadcastIdString, !broadcastId.isEmpty else {
            return false
        }

        let deleteResult = await bluetoothService.deleteUserByToken(
            broadcastId: broadcastId,
            token: userToken,
            disconnect: false
        )

        switch deleteResult {
        case .success:
            return true
        case .failure:
            return false
        }
    }

    /// Determines which username value should be preserved when restarting the connection.
    func resolveUsernameToPreserve(from preservedUsername: String) -> String {
        if !preservedUsername.isEmpty {
            return preservedUsername
        }
        if !duplicateUserName.isEmpty {
            return duplicateUserName
        }
        return firstName ?? "User"
    }

    /// Performs the restore account operation by finding and deleting the matching user on the scale
    func performRestoreAccount() async {
        guard hasAllBtPermissions() else {
            notificationService.dismissAlert()
            resetDiscoveryState()
            navigateToStep(.permissions)
            return
        }
        guard let scale = discoveredScale else {
            scaleSetupError = .duplicatesFound
            return
        }

        let accountName = getAccountNameForRestore()
        userNameForm.setDisplayName(accountName)
        guard !accountName.isEmpty else {
            scaleSetupError = .duplicatesFound
            return
        }

        guard let matchingUser = await findMatchingUserOnScale(scale: scale, accountName: accountName) else {
            scaleSetupError = .duplicatesFound
            return
        }

        guard await deleteMatchingUserFromScale(scale: scale, user: matchingUser) else {
            scaleSetupError = .duplicatesFound
            return
        }

        await restartConnectionAndNavigate()
    }

    /// Restarts the connection and navigates to the connecting step
    func restartConnectionAndNavigate() async {
        let preservedUsername = userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines)
        let usernameToPreserve = resolveUsernameToPreserve(from: preservedUsername)

        try? await Task.sleep(nanoseconds: 500_000_000)
        scaleSetupError = .none
        await restartConnection()

        userNameForm.setDisplayName(usernameToPreserve)
        duplicateUserName = usernameToPreserve

        navigateToStep(.connectingBluetooth)
    }

    /// Handles the save action from the duplicate user screen
    func handleSaveDuplicateUser() {
        guard userNameForm.displayName.isValid else { return }

        duplicateUserName = removeWhiteSpace(userNameForm.displayName.value)
        selectedCustomizeItems.insert(CustomizeSettingsItem.userName.rawValue)

        scaleSetupError = .none
        connectionState = .loading
        navigateToStep(.connectingBluetooth)
    }

    /// Deletes a specific user from the scale
    func deleteUserFromScale(_ user: DeviceUser) async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - no discovered scale")
            return
        }

        guard let broadcastId = scale.broadcastIdString, !broadcastId.isEmpty else {
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - no broadcastId")
            return
        }
        let userToken = user.token ?? ""
        let result = await bluetoothService.deleteUserByToken(broadcastId: broadcastId, token: userToken, disconnect: false)

        switch result {
        case .success:
            LoggerService.shared.log(level: .info, tag: tag, message: "deleteUserFromScale - deleted user: \(user.name)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - error deleting user: \(error.localizedDescription)")
        }
    }

    /// Restarts the connection process after deleting users
    func restartConnection() async {
        self.userList = []
        self.currentUser = nil
        self.duplicateList = []
        self.duplicateUserLastActiveAt = nil

        self.userNameForm.reset()

        guard discoveredScale != nil, discoveryEvent != nil else {
            return
        }

        connectionState = .loading
    }

    func getUserList() async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - no discovered scale")
            return
        }

        let result = await bluetoothService.getScaleUserList(
            broadcastId: scale.broadcastIdString ?? "",
            skipConnectionCheck: true,
            sku: scale.sku ?? discoveryEvent?.deviceInfo.sku ?? scaleItem?.sku
        )
        switch result {
        case .success(let users):
            self.userList = users.filter { user in
                user.token != scale.token
            }
            LoggerService.shared.log(level: .info, tag: tag, message: "getUserList - retrieved \(self.userList.count) users")

            if currentCustomizeSetting == .scaleUsername {
                await MainActor.run {
                    self.userNameForm.updateUserList(self.userList)
                    if self.userNameForm.currentUserName == nil {
                        let currentName = self.initialDisplayNameSnapshot ?? self.firstName ?? "User"
                        self.userNameForm.setCurrentUserName(currentName)
                    }
                    self.userNameForm.displayName.markAsPristine()
                    self.userNameForm.displayName.markAsUntouched()
                    self.updateNextEnabled()
                }
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - error getting scale users: \(error.localizedDescription)")
        }
    }

    /// Checks for duplicate users in the user list
    func checkDuplicateUserList() {
        self.currentUser = userList.first { user in
            user.name.lowercased() == (self.firstName?.lowercased() ?? "")
        }

        if let currentUser = self.currentUser {
            self.duplicateList = userList.filter { user in
                user.name == currentUser.name
            }
        }
        duplicateUserLastActiveAt = Int64(duplicateList.first?.lastActive ?? 0)
        LoggerService.shared.log(level: .info, tag: tag, message: "checkDuplicateUserList - found \(self.duplicateList.count) duplicate users")
    }

    /// Deletes duplicate users from the scale
    func deleteUsers() async {
        guard let scale = discoveredScale else {
            return
        }

        guard let broadcastId = scale.broadcastIdString, !broadcastId.isEmpty else { return }
        for user in duplicateList {
            guard let userToken = user.token, !userToken.isEmpty else {
                continue
            }

            _ = await bluetoothService.deleteUserByToken(broadcastId: broadcastId, token: userToken, disconnect: false)
        }

        duplicateUserName = firstName ?? "User"

        userNameForm.reset()
        if let firstName = self.firstName {
            userNameForm.setDisplayName(firstName)
        }
    }
}
