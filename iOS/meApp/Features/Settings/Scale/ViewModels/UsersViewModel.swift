import Combine
//
//  UsersViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/08/25.
//
import Foundation
import SwiftData

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

    // Store the device ID for safe refetching from MainActor context
    private let scaleId: PersistentIdentifier
    private let scaleIdString: String

    // Cached scale for fallback when model not found in context
    private var cachedScale: Device?

    // Returns the cached scale - use refreshScale() to update from database
    private var scale: Device {
        if let cached = cachedScale {
            return cached
        }
        logger.log(level: .error, tag: tag, message: "No cached scale available")
        return Device(id: "", accountId: "", deviceName: "Error", deviceType: "")
    }

    /// Avoid replacing a live in-memory scale with a fetched copy that has lost transient
    /// connection/runtime fields still required by the current flow.
    private func resolvedScaleForCaching(_ freshScale: Device) -> Device {
        guard let cachedScale else { return freshScale }

        let freshMissingBroadcastId = (freshScale.broadcastIdString?.isEmpty ?? true) &&
            !(cachedScale.broadcastIdString?.isEmpty ?? true)
        let freshMissingToken = (freshScale.token?.isEmpty ?? true) &&
            !(cachedScale.token?.isEmpty ?? true)
        let freshLostConnectionState = cachedScale.isConnected == true && freshScale.isConnected != true

        if freshMissingBroadcastId || freshMissingToken || freshLostConnectionState {
            return cachedScale
        }

        return freshScale
    }

    /// Refreshes the scale from the database. Call this before operations that need fresh data.
    private func refreshScale() {
        // First try registeredModel for already-loaded models (fastest path)
        if let freshScale: Device = PersistenceController.shared.context.registeredModel(for: scaleId) {
            cachedScale = resolvedScaleForCaching(freshScale)
            return
        }

        // If not in identity map, fetch from persistent store using FetchDescriptor
        let idToFind = scaleIdString
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { device in
                device.id == idToFind
            }
        )
        do {
            let results = try PersistenceController.shared.context.fetch(descriptor)
            if let freshScale = results.first {
                cachedScale = resolvedScaleForCaching(freshScale)
                return
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch scale from store: \(error.localizedDescription)")
        }

        // Keep existing cached value if fetch failed
        if cachedScale != nil {
            logger.log(level: .debug, tag: tag, message: "Using existing cached scale after refresh failed")
        }
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
        self.scaleId = scale.persistentModelID
        self.scaleIdString = scale.id
        self.cachedScale = scale
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
        guard scale.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Scale is not connected, cannot load users")
            await MainActor.run {
                // Ensure loading state is reset even if device is not connected
                isLoadingUsers = false
                // If we don't have initial users, ensure we clear the state
                if initialUsersList.isEmpty {
                    self.deviceUsers = []
                    self.currentDeviceUser = nil
                }
            }
            return
        }
        
        await MainActor.run {
            isLoadingUsers = true
        }
        
        let result = await bluetoothService.getScaleUserList(broadcastId: scale.broadcastIdString ?? "")

        await MainActor.run {
            // Refresh scale before accessing relationships in findCurrentUser
            self.refreshScale()
            switch result {
            case .success(let users):
                self.applyLoadedUsers(users)
            case .failure(let error):
                logger.log(level: .error, tag: tag, message: "Failed to load users from scale: \(error.localizedDescription)")
                // Only clear users if we don't have initial users from navigation
                if initialUsersList.isEmpty {
                    self.deviceUsers = []
                    self.currentDeviceUser = nil
                }
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
            // Refresh scale to get latest data before accessing relationships
            refreshScale()
            // Update the current user's name via Bluetooth service
            if currentDeviceUser != nil,
               let preference = scale.r4ScalePreference {
                // Extract to DTO before async to avoid @Model mutation (R9)
                let deviceId = scale.id
                var dto = preference.toDTO()
                dto.displayName = newName
                try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                await scaleService.pushLocalChangesToServer()
                // Refresh scale before Bluetooth call to ensure fresh @Model data
                refreshScale()
                guard let freshPreference = scale.r4ScalePreference else { return }
                let result = await bluetoothService.updateAccount(broadcastId: scale.broadcastIdString ?? "")
                switch result {
                case .success:
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

    private func reloadUsersAfterDeletion(using device: Device) async {
        let result = await bluetoothService.getScaleUserList(broadcastId: device.broadcastIdString ?? "")

        await MainActor.run {
            switch result {
            case .success(let users):
                self.applyLoadedUsers(users)
            case .failure(let error):
                logger.log(level: .error, tag: tag, message: "Failed to reload users after deletion: \(error.localizedDescription)")
                if self.initialUsersList.isEmpty {
                    self.deviceUsers = []
                    self.currentDeviceUser = nil
                }
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

        await deleteUser(user)
        onDelete()
    }
    
    private func deleteUser(_ user: DeviceUser) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))

        let operationScale = cachedScale ?? scale

        // Extract primitives before async call — never mutate @Model to pass data
        guard let broadcastId = nonEmpty(operationScale.broadcastIdString) ?? nonEmpty(scale.broadcastIdString) else {
            logger.log(level: .error, tag: tag, message: "Missing broadcastId for scale")
            notificationService.dismissLoader()
            return
        }
        guard let userToken = user.token, !userToken.isEmpty else {
            logger.log(level: .error, tag: tag, message: "Missing token for user to delete")
            notificationService.dismissLoader()
            return
        }

        logger.log(level: .debug, tag: tag, message: "Deleting user: \(user.name) with token: \(userToken) on scale \(operationScale.id)")
        let result = await bluetoothService.deleteUserByToken(broadcastId: broadcastId, token: userToken, disconnect: false)
        
        switch result {
        case .success:
            logger.log(level: .info, tag: tag, message: "User deleted successfully, reloading user list", data: ["userName": user.name])
            
            // Add a small delay to ensure the scale has processed the deletion
            // This prevents race conditions where the user list might be fetched before the scale updates
            try? await Task.sleep(nanoseconds: userDeletionDelayNanoseconds)
            
            // Reload using the last known-good connected device instance so this flow
            // does not depend on a refetched SwiftData model preserving runtime fields.
            await reloadUsersAfterDeletion(using: operationScale)
            
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
