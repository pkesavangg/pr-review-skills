//
//  UsersViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/08/25.
//
import Foundation

@MainActor
final class UsersViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var bluetoothService: BluetoothService
    @Injector var scaleService: ScaleService
    @Injector var logger: LoggerService
    
    @Published var deviceUsers: [DeviceUser] = []
    @Published var currentDeviceUser: DeviceUser?
    @Published var isLoadingUsers: Bool = false
    
    private let scale: Device
    private let tag = "UsersViewModel"
    
    var otherDeviceUsersList: [DeviceUser] {
        return deviceUsers.filter { $0.token != currentDeviceUser?.token }
    }
    
    init(scale: Device, initialUsersList: [DeviceUser] = []) {
        self.scale = scale
        if !initialUsersList.isEmpty {
            self.deviceUsers = initialUsersList
            self.currentDeviceUser = initialUsersList.filter({$0.token == scale.token}).first
        } else {
            Task {
                await loadUsers()
            }            
        }
    }
    
    func loadUsers() async {
        // If users were already provided during initialization, don't fetch again
        guard deviceUsers.isEmpty else {
            logger.log(level: .info, tag: tag, message: "Users already loaded from initial list")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Scale is not connected, cannot load users")
            return
        }
        
        await MainActor.run {
            isLoadingUsers = true
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale)
        
        await MainActor.run {
            switch result {
            case .success(let users):
                self.deviceUsers = users
                // Find current user (typically the first one or the one that matches our account)
                self.currentDeviceUser = users.filter({$0.token == scale.token}).first
                logger.log(level: .info, tag: tag, message: "Successfully loaded \(users.count) users from scale")
            case .failure(let error):
                logger.log(level: .error, tag: tag, message: "Failed to load users from scale: \(error.localizedDescription)")
                self.deviceUsers = []
                self.currentDeviceUser = nil
            }
            isLoadingUsers = false
        }
    }
    
    func saveUsers(newName: String, onSuccess: (() -> Void)? = nil) async {
        guard !newName.isEmpty else {
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: "User name cannot be empty"))
            return
        }
        
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        
        do {
            // Update the current user's name via Bluetooth service
            if currentDeviceUser != nil,
               let preference = scale.r4ScalePreference {
                preference.displayName = newName
                try await scaleService.updateScalePreference(
                    scale.id,
                    preference
                )
                await scaleService.pushLocalChangesToServer()
                let result = await bluetoothService.updateAccount(on: scale, preference: preference)
                switch result {
                case .success(_):
                    currentDeviceUser?.name = newName
                    notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userNameUpdated))
                    logger.log(level: .info, tag: tag, message: "User name updated successfully", data: ["scaleId": scale.id, "newName": newName])
                    onSuccess?()
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to update user name: \(error.localizedDescription)")
                    notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
                }
            } else {
                logger.log(level: .error, tag: tag, message: "No current user or preference found for scale")
                notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save user name: \(error.localizedDescription)", data: error)
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
        }
        
        notificationService.dismissLoader()
    }
    
    func showDeleteUserAlert(for user: DeviceUser, onDelete: @escaping () -> Void) {
        let alert = AlertModel(
            title: AlertStrings.DeleteUserAlert.title(user.name),
            message: AlertStrings.DeleteUserAlert.message(user.name),
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteUserAlert.cancelButton, type: .secondary) { _ in },
                AlertButtonModel(title: AlertStrings.DeleteUserAlert.removeButton, type: .primary) { _ in
                    Task {
                        await self.deleteUser(user)
                        onDelete()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    private func deleteUser(_ user: DeviceUser) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        
        
        let result = await bluetoothService.deleteDevice(scale, disconnect: false)
        switch result {
        case .success(_):
            deviceUsers.removeAll { $0.token == user.token }
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userDeleted))
            logger.log(level: .info, tag: tag, message: "User deleted successfully", data: ["userName": user.name])
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to delete user: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorDeletingUser))
        }
        notificationService.dismissLoader()
    }
}
