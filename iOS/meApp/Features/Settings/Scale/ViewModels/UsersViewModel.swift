import Combine
//
//  UsersViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/08/25.
//
import Foundation

@MainActor
final class UsersViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var permissionsService: PermissionsServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Published var userNameForm = UserNameForm()
    @Published var deviceUsers: [DeviceUser] = []
    @Published var currentDeviceUser: DeviceUser?
    @Published var isLoadingUsers: Bool = false

    private let scaleIdString: String

    /// Reads the current snapshot directly from the service — the single source of truth.
    private var deviceSnapshot: DeviceSnapshot? {
        scaleService.scales.first(where: { $0.id == scaleIdString })
    }

    private let tag = "UsersViewModel"
    private var cancellables = Set<AnyCancellable>()
    private let initialUsersList: [DeviceUser]

    // Delay after user deletion to ensure scale processes the deletion before reloading
    private let userDeletionDelayNanoseconds: UInt64

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

    init(
        scale: Device,
        initialUsersList: [DeviceUser] = [],
        userDeletionDelayNanoseconds: UInt64 = 500_000_000
    ) {
        self.scaleIdString = scale.id
        self.initialUsersList = initialUsersList
        self.userDeletionDelayNanoseconds = userDeletionDelayNanoseconds
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

        // Resolve DI-backed services up front so async work started from init
        // does not pick up a different dependency after another test mutates
        // the shared container.
        _ = notificationService
        _ = bluetoothService
        _ = permissionsService
        _ = scaleService
        _ = logger

        setupFormObservers()

        Task { @MainActor [weak self] in
            await self?.loadUsers()
        }
    }

    func loadUsers() async {
        // Check device connection status before attempting to load users
        guard deviceSnapshot?.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Scale is not connected, cannot load users")
            isLoadingUsers = false
            if initialUsersList.isEmpty {
                self.deviceUsers = []
                self.currentDeviceUser = nil
            }
            return
        }

        isLoadingUsers = true

        let broadcastId = deviceSnapshot?.broadcastIdString ?? ""
        let result = await bluetoothService.getScaleUserList(broadcastId: broadcastId)

        switch result {
        case .success(let users):
            applyLoadedUsers(users)
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to load users from scale: \(error.localizedDescription)")
            if initialUsersList.isEmpty {
                self.deviceUsers = []
                self.currentDeviceUser = nil
            }
        }
        isLoadingUsers = false
    }

    func saveUsers(newName: String, onSuccess: (() -> Void)? = nil) async {
        guard !newName.isEmpty else {
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: "User name cannot be empty"))
            return
        }

        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))

        do {
            if currentDeviceUser != nil,
               let preference = deviceSnapshot?.r4ScalePreference {
                let deviceId = scaleIdString
                let broadcastId = deviceSnapshot?.broadcastIdString ?? ""
                var dto = preference.toDTO()
                dto.displayName = newName
                try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                await scaleService.pushLocalChangesToServer()
                let result = await bluetoothService.updateAccount(broadcastId: broadcastId)
                switch result {
                case .success:
                    currentDeviceUser?.name = newName
                    notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userNameUpdated))
                    logger.log(level: .info, tag: tag, message: "User name updated successfully", data: ["scaleId": scaleIdString, "newName": newName])
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
        let snapshot = deviceSnapshot
        if let token = snapshot?.token, !token.isEmpty, let matchByToken = users.first(where: { $0.token == token }) {
            return matchByToken
        }
        if let prefName = snapshot?.r4ScalePreference?.displayName, !prefName.isEmpty,
           let matchByName = users.first(where: { $0.name.caseInsensitiveCompare(prefName) == .orderedSame }) {
            return matchByName
        }
        return nil
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    private func applyLoadedUsers(_ users: [DeviceUser]) {
        self.deviceUsers = users
        self.currentDeviceUser = self.findCurrentUser(in: users)
        logger.log(level: .info, tag: tag, message: "Successfully loaded \(users.count) users from scale")
        let currentName = currentDeviceUser?.name ?? ""
        userNameForm.setDisplayName(currentName)
        let scaleUsers = otherDeviceUsersList.map { deviceUser in
            ScaleUser(name: deviceUser.name, token: deviceUser.token)
        }
        userNameForm.updateUserList(scaleUsers)
    }

    private func reloadUsersAfterDeletion(broadcastId: String) async {
        let result = await bluetoothService.getScaleUserList(broadcastId: broadcastId)

        switch result {
        case .success(let users):
            applyLoadedUsers(users)
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to reload users after deletion: \(error.localizedDescription)")
            if initialUsersList.isEmpty {
                self.deviceUsers = []
                self.currentDeviceUser = nil
            }
        }
    }

    func showDeleteUserAlert(for user: DeviceUser, onDelete: @escaping () -> Void) {
        let alert = AlertModel(
            title: AlertStrings.DeleteR4ScaleUserAlert.title,
            message: AlertStrings.DeleteR4ScaleUserAlert.message(user.name),
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteR4ScaleUserAlert.cancelButton, type: .secondary) { _ in },
                AlertButtonModel(title: AlertStrings.DeleteR4ScaleUserAlert.deleteButton, type: .danger) { _ in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        await self.handleDeleteUserAction(for: user, onDelete: onDelete)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    @MainActor
    private func handleDeleteUserAction(for user: DeviceUser, onDelete: @escaping () -> Void) async {
        // Alert button actions are plain closures, so explicitly hop back to MainActor
        // before touching actor-isolated services and state.
        let isBluetoothAuthorized = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
        let isBluetoothOn = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        guard isBluetoothAuthorized && isBluetoothOn else {
            logger.log(
                level: .info,
                tag: tag,
                message: "Bluetooth permission or switch is OFF. Blocking user deletion and showing toast."
            )
            notificationService.showToast(
                ToastModel(
                    title: ToastStrings.bluetoothRequiredTitle,
                    message: ToastStrings.bluetoothRequiredMessage
                )
            )
            return
        }

        try? await scaleService.updateAllScalesStatus(nil)
        guard deviceSnapshot?.isConnected == true else {
            logger.log(
                level: .info,
                tag: tag,
                message: "Scale is not connected. Blocking user deletion and showing toast."
            )
            notificationService.showToast(
                ToastModel(
                    title: ToastStrings.error,
                    message: ToastStrings.errorDeletingUser
                )
            )
            return
        }

        await deleteUser(user)
        onDelete()
    }

    private func deleteUser(_ user: DeviceUser) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))

        guard let broadcastId = nonEmpty(deviceSnapshot?.broadcastIdString) else {
            logger.log(level: .error, tag: tag, message: "Missing broadcastId for scale")
            notificationService.dismissLoader()
            return
        }
        guard let userToken = user.token, !userToken.isEmpty else {
            logger.log(level: .error, tag: tag, message: "Missing token for user to delete")
            notificationService.dismissLoader()
            return
        }

        logger.log(level: .debug, tag: tag, message: "Deleting user: \(user.name) with token: \(userToken) on scale \(scaleIdString)")
        let result = await bluetoothService.deleteUserByToken(broadcastId: broadcastId, token: userToken, disconnect: false)

        switch result {
        case .success:
            logger.log(level: .info, tag: tag, message: "User deleted successfully, reloading user list", data: ["userName": user.name])

            // Give the scale a moment to process the deletion before fetching the refreshed list.
            try? await Task.sleep(nanoseconds: userDeletionDelayNanoseconds)

            await reloadUsersAfterDeletion(broadcastId: broadcastId)

            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userDeleted))
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
