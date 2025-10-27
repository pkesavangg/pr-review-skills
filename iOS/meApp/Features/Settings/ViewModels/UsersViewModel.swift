//
//  UsersViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/08/25.
//
import Foundation
import Combine

@MainActor
final class UsersViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var bluetoothService: BluetoothService
    @Injector var scaleService: ScaleService
    @Injector var logger: LoggerService
    @Published var userNameForm = UserNameForm()
    @Published var deviceUsers: [DeviceUser] = []
    @Published var currentDeviceUser: DeviceUser?
    @Published var isLoadingUsers: Bool = false
    
    private let scale: Device
    private let tag = "UsersViewModel"
    private var cancellables = Set<AnyCancellable>()
    
    var otherDeviceUsersList: [DeviceUser] {
        return deviceUsers.filter { user in
            if let currentToken = currentDeviceUser?.token, !currentToken.isEmpty,
               let token = user.token, !token.isEmpty {
                return token != currentToken
            } else if let currentName = currentDeviceUser?.name, !currentName.isEmpty {
                return user.name.caseInsensitiveCompare(currentName) != .orderedSame
            }
            return false
        }
    }
    
    init(scale: Device, initialUsersList: [DeviceUser] = []) {
        self.scale = scale
        if !initialUsersList.isEmpty {
            self.deviceUsers = initialUsersList
            self.currentDeviceUser = findCurrentUser(in: initialUsersList)
            // Pre-populate the form with the current user's name and user list
            let currentName = self.currentDeviceUser?.name ?? ""
            userNameForm.setDisplayName(currentName)
            let scaleUsers = otherDeviceUsersList.map { deviceUser in
                ScaleUser(name: deviceUser.name, token: deviceUser.token)
            }
            userNameForm.updateUserList(scaleUsers)
        }
        
        setupFormObservers()
        
        Task {
            await loadUsers()
        }
    }
    
    func loadUsers() async {
        // If users were already provided during initialization, don't fetch again
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
                // Find current user using unified matching logic
                self.currentDeviceUser = self.findCurrentUser(in: users)
                logger.log(level: .info, tag: tag, message: "Successfully loaded \(users.count) users from scale")
                let currentName = currentDeviceUser?.name ?? ""
                userNameForm.setDisplayName(currentName)
                let scaleUsers = otherDeviceUsersList.map { deviceUser in
                    ScaleUser(name: deviceUser.name, token: deviceUser.token)
                }
                userNameForm.updateUserList(scaleUsers)
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
    
    // MARK: - Helpers
    private func findCurrentUser(in users: [DeviceUser]) -> DeviceUser? {
        if let token = scale.token, !token.isEmpty, let matchByToken = users.first(where: { $0.token == token }) {
            return matchByToken
        }
        if let prefName = scale.r4ScalePreference?.displayName, !prefName.isEmpty,
           let matchByName = users.first(where: { $0.name.caseInsensitiveCompare(prefName) == .orderedSame }) {
            return matchByName
        }
        return nil
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
        
        // Preserve the original token so we can restore it after the deletion call
        let originalToken = scale.token
        // Temporarily set the token to the user's token we want to delete
        scale.token = user.token
        logger.log(level: .debug, tag: tag, message: "Deleting user: \(user.name) with token: \(user.token ?? "nil") on scale \(scale.id)")
        let result = await bluetoothService.deleteDevice(scale, disconnect: false)
        // Restore the original token to avoid side-effects elsewhere in the app
        scale.token = originalToken
        switch result {
        case .success(_):
            deviceUsers.removeAll { $0.token == user.token }
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userDeleted))
            logger.log(level: .info, tag: tag, message: "User deleted successfully", data: ["userName": user.name])
            Task {
                await loadUsers()
            }
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to delete user: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorDeletingUser))
        }
        notificationService.dismissLoader()
    }
    
    // MARK: - Form Validation & Observers
    
    /// Setup form observers to trigger UI updates when form changes
    private func setupFormObservers() {
        userNameForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Form Helper Methods
    
    /// Get error message for the display name field
    var displayNameError: String? {
        userNameForm.getError(for: userNameForm.displayName)
    }
    
    /// Check if form is valid
    var isFormValid: Bool {
        userNameForm.isValid
    }
    
    /// Mark display name field as touched/dirty
    func setDisplayNameTouched() {
        userNameForm.displayName.markAsDirty()
        objectWillChange.send()
    }
}
