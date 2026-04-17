import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct UsersViewModelTests {
    @Test("init with initial users pre-populates current user and form state")
    func initWithInitialUsersPrePopulatesCurrentUserAndForm() async {
        let scale = makeScale(isConnected: false, token: "owner-token")
        let initialUsers = [
            makeUser(name: "Owner", token: "owner-token"),
            makeUser(name: "Guest", token: "guest-token")
        ]
        let (store, _, _, _, _) = makeSUT(scale: scale, initialUsersList: initialUsers)

        _ = await waitUntil {
            store.isLoadingUsers == false &&
            store.deviceUsers.count == 2 &&
            store.currentDeviceUser?.name == "Owner"
        }

        #expect(store.deviceUsers.map { $0.name } == ["Owner", "Guest"])
        #expect(store.currentDeviceUser?.name == "Owner")
        #expect(store.userNameForm.displayName.value == "Owner")
        #expect(store.userNameForm.userList.map { $0.name } == ["Guest"])
    }

    @Test("otherDeviceUsersList falls back to name comparison when token is missing")
    func otherDeviceUsersListFallsBackToNameComparison() async {
        let scale = makeScale(isConnected: false, token: nil, displayName: "owner")
        let users = [
            makeUser(name: "Owner", token: nil),
            makeUser(name: "Guest", token: nil)
        ]
        let (store, _, _, _, _) = makeSUT(scale: scale, initialUsersList: users)

        _ = await waitUntil {
            store.currentDeviceUser?.name == "Owner"
        }

        #expect(store.otherDeviceUsersList.map { $0.name } == ["Guest"])
    }

    @Test("loadUsers disconnected with no initial users clears state")
    func loadUsersDisconnectedWithoutInitialUsersClearsState() async {
        let scale = makeScale(isConnected: false)
        let (store, _, _, _, _) = makeSUT(scale: scale)
        store.deviceUsers = [makeUser(name: "Stale", token: "stale")]
        store.currentDeviceUser = makeUser(name: "Stale", token: "stale")

        await store.loadUsers()

        #expect(store.isLoadingUsers == false)
        #expect(store.deviceUsers.isEmpty)
        #expect(store.currentDeviceUser == nil)
    }

    @Test("loadUsers disconnected with initial users retains cached list")
    func loadUsersDisconnectedWithInitialUsersRetainsCachedState() async {
        let scale = makeScale(isConnected: false, token: "owner-token")
        let initialUsers = [
            makeUser(name: "Owner", token: "owner-token"),
            makeUser(name: "Guest", token: "guest-token")
        ]
        let (store, _, _, _, _) = makeSUT(scale: scale, initialUsersList: initialUsers)

        await store.loadUsers()

        #expect(store.deviceUsers.map { $0.name } == ["Owner", "Guest"])
        #expect(store.currentDeviceUser?.name == "Owner")
    }

    @Test("loadUsers success matches current user by token and updates form user list")
    func loadUsersSuccessMatchesCurrentUserByToken() async {
        let scale = makeScale(isConnected: true, token: "owner-token")
        let bluetooth = MockBluetoothService()
        bluetooth.getScaleUserListResult = .success([
            makeUser(name: "Owner", token: "owner-token"),
            makeUser(name: "Guest", token: "guest-token")
        ])
        let (store, _, _, _, _) = makeSUT(scale: scale, bluetooth: bluetooth)

        await store.loadUsers()

        #expect(store.deviceUsers.count == 2)
        #expect(store.currentDeviceUser?.token == "owner-token")
        #expect(store.userNameForm.displayName.value == "Owner")
        #expect(store.otherDeviceUsersList.map { $0.name } == ["Guest"])
    }

    @Test("loadUsers success falls back to preference displayName match when token does not match")
    func loadUsersSuccessMatchesByDisplayNameWhenTokenDoesNotMatch() async {
        let scale = makeScale(isConnected: true, token: "no-match-token", displayName: "owner")
        let bluetooth = MockBluetoothService()
        bluetooth.getScaleUserListResult = .success([
            makeUser(name: "Owner", token: "owner-token"),
            makeUser(name: "Guest", token: "guest-token")
        ])
        let (store, _, _, _, _) = makeSUT(scale: scale, bluetooth: bluetooth)

        await store.loadUsers()

        #expect(store.currentDeviceUser?.name == "Owner")
        #expect(store.currentDeviceUser?.token == "owner-token")
    }

    @Test("loadUsers failure with no initial users clears list and current user")
    func loadUsersFailureWithoutInitialUsersClearsState() async {
        let scale = makeScale(isConnected: true)
        let bluetooth = MockBluetoothService()
        bluetooth.getScaleUserListResult = .failure(.notImplemented)
        let (store, _, _, _, _) = makeSUT(scale: scale, bluetooth: bluetooth)
        store.deviceUsers = [makeUser(name: "Old", token: "old")]
        store.currentDeviceUser = makeUser(name: "Old", token: "old")

        await store.loadUsers()

        #expect(store.deviceUsers.isEmpty)
        #expect(store.currentDeviceUser == nil)
        #expect(store.isLoadingUsers == false)
    }

    @Test("loadUsers failure with initial users retains previously provided list")
    func loadUsersFailureWithInitialUsersRetainsState() async {
        let scale = makeScale(isConnected: true, token: "owner-token")
        let bluetooth = MockBluetoothService()
        bluetooth.getScaleUserListResult = .failure(.notImplemented)
        let initialUsers = [
            makeUser(name: "Owner", token: "owner-token"),
            makeUser(name: "Guest", token: "guest-token")
        ]
        let (store, _, _, _, _) = makeSUT(scale: scale, initialUsersList: initialUsers, bluetooth: bluetooth)

        await store.loadUsers()

        #expect(store.deviceUsers.map { $0.name } == ["Owner", "Guest"])
        #expect(store.currentDeviceUser?.name == "Owner")
    }

    @Test("saveUsers with empty name shows validation toast and skips loader")
    func saveUsersEmptyNameShowsValidationToast() async {
        let (store, _, _, notification, _) = makeSUT(scale: makeScale(isConnected: true))

        await store.saveUsers(newName: "")

        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.message == "User name cannot be empty")
        #expect(notification.showLoaderCalls == 0)
        #expect(notification.dismissLoaderCalls == 0)
    }

    @Test("saveUsers without current user shows update failure toast")
    func saveUsersWithoutCurrentUserShowsErrorToast() async {
        let scale = makeScale(isConnected: true, token: "owner-token")
        let (store, bluetooth, _, notification, _) = makeSUT(scale: scale)
        store.currentDeviceUser = nil

        await store.saveUsers(newName: "Renamed")

        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.toastData?.message == ToastStrings.errorUpdatingUserName)
        #expect(bluetooth.updateAccountCalls == 0)
    }

    @Test("saveUsers updateScalePreference failure shows error toast and does not call updateAccount")
    func saveUsersScalePreferenceFailureShowsErrorToast() async {
        let scale = makeScale(isConnected: true, token: "owner-token")
        let initialUsers = [
            makeUser(name: "Owner", token: "owner-token")
        ]
        let scaleService = MockScaleService()
        scaleService.updateScalePreferenceError = UsersViewModelTestsError.updatePreferenceFailed
        let (store, bluetooth, _, notification, _) = makeSUT(
            scale: scale,
            initialUsersList: initialUsers,
            scaleService: scaleService
        )

        await store.saveUsers(newName: "Renamed")

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(scaleService.pushLocalChangesToServerCalls == 0)
        #expect(bluetooth.updateAccountCalls == 0)
        #expect(notification.toastData?.message == ToastStrings.errorUpdatingUserName)
        #expect(notification.dismissLoaderCalls == 1)
    }

    @Test("saveUsers updateAccount failure shows error toast")
    func saveUsersUpdateAccountFailureShowsErrorToast() async {
        let scale = makeScale(isConnected: true, token: "owner-token")
        let initialUsers = [makeUser(name: "Owner", token: "owner-token")]
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .failure(.notImplemented)
        let (store, _, scaleService, notification, _) = makeSUT(
            scale: scale,
            initialUsersList: initialUsers,
            bluetooth: bluetooth
        )

        await store.saveUsers(newName: "Renamed")

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(scaleService.pushLocalChangesToServerCalls == 1)
        #expect(bluetooth.updateAccountCalls == 1)
        #expect(notification.toastData?.message == ToastStrings.errorUpdatingUserName)
    }

    @Test("saveUsers success updates current user name and executes callback")
    func saveUsersSuccessUpdatesCurrentUserAndRunsCallback() async {
        let scale = makeScale(isConnected: true, token: "owner-token")
        let initialUsers = [makeUser(name: "Owner", token: "owner-token")]
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .success(.creationCompleted)
        let (store, _, scaleService, notification, _) = makeSUT(
            scale: scale,
            initialUsersList: initialUsers,
            bluetooth: bluetooth
        )
        var callbackCalls = 0

        await store.saveUsers(newName: "Renamed") {
            callbackCalls += 1
        }

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(scaleService.pushLocalChangesToServerCalls == 1)
        #expect(bluetooth.updateAccountCalls == 1)
        #expect(store.currentDeviceUser?.name == "Renamed")
        #expect(notification.toastData?.message == ToastStrings.userNameUpdated)
        #expect(callbackCalls == 1)
    }

    @Test("showDeleteUserAlert blocks deletion when bluetooth permission or switch is disabled")
    func showDeleteUserAlertBlocksDeletionWhenBluetoothDisabled() async {
        let permissions = MockPermissionsService()
        permissions.setPermissions([.BLUETOOTH: .DISABLED, .BLUETOOTH_SWITCH: .DISABLED])
        let (store, bluetooth, _, notification, _) = makeSUT(
            scale: makeScale(isConnected: true, broadcastIdString: "ABC"),
            permissions: permissions
        )

        store.showDeleteUserAlert(for: makeUser(name: "Guest", token: "guest")) {}
        notification.alertData?.buttons.last?.action(nil as String?)
        await Task.yield()

        #expect(bluetooth.deleteUserByTokenCalls == 0)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.bluetoothRequiredTitle)
    }

    @Test("showDeleteUserAlert success deletes user reloads list and executes callback")
    func showDeleteUserAlertDeleteSuccessFlow() async {
        let scale = makeScale(isConnected: true, token: "owner-token", broadcastIdString: "ABC")
        let bluetooth = MockBluetoothService()
        bluetooth.deleteUserByTokenResult = .success(.success)
        bluetooth.getScaleUserListResult = .success([
            makeUser(name: "Owner", token: "owner-token"),
            makeUser(name: "AfterDelete", token: "after-token")
        ])
        let (store, _, _, notification, _) = makeSUT(scale: scale, bluetooth: bluetooth)
        var onDeleteCalls = 0

        await store.loadUsers()

        #expect(store.isLoadingUsers == false)
        #expect(bluetooth.getScaleUserListCalls >= 1)
        #expect(store.deviceUsers.map(\.name) == ["Owner", "AfterDelete"])

        store.showDeleteUserAlert(for: makeUser(name: "Guest", token: "guest-token")) {
            onDeleteCalls += 1
        }
        notification.alertData?.buttons.last?.action(nil as String?)
        await Task.yield()

        let completed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            bluetooth.deleteUserByTokenCalls == 1 &&
            bluetooth.getScaleUserListCalls >= 2 &&
            notification.dismissLoaderCalls >= 1 &&
            notification.toastData?.message == ToastStrings.userDeleted &&
            onDeleteCalls == 1
        }

        #expect(completed == true)
        #expect(notification.toastData?.message == ToastStrings.userDeleted)
    }

    @Test("showDeleteUserAlert delete failure shows error toast and still dismisses loader")
    func showDeleteUserAlertDeleteFailureFlow() async {
        let scale = makeScale(isConnected: true, broadcastIdString: "ABC")
        let bluetooth = MockBluetoothService()
        bluetooth.deleteUserByTokenResult = .failure(.notImplemented)
        let (store, _, _, notification, _) = makeSUT(scale: scale, bluetooth: bluetooth)

        store.showDeleteUserAlert(for: makeUser(name: "Guest", token: "guest-token")) {}
        notification.alertData?.buttons.last?.action(nil as String?)
        await Task.yield()

        let completed = await waitUntil {
            bluetooth.deleteUserByTokenCalls == 1 &&
            notification.dismissLoaderCalls == 1
        }

        #expect(completed == true)
        #expect(notification.toastData?.message == ToastStrings.errorDeletingUser)
    }

    @Test("showDeleteUserAlert with missing broadcastId exits early and does not call delete API")
    func showDeleteUserAlertMissingBroadcastIdEarlyExit() async {
        let scale = makeScale(isConnected: true, broadcastIdString: nil)
        let (store, bluetooth, _, notification, _) = makeSUT(scale: scale)
        var onDeleteCalls = 0

        store.showDeleteUserAlert(for: makeUser(name: "Guest", token: "guest-token")) {
            onDeleteCalls += 1
        }
        notification.alertData?.buttons.last?.action(nil as String?)
        await Task.yield()

        let completed = await waitUntil {
            notification.dismissLoaderCalls == 1 && onDeleteCalls == 1
        }

        #expect(completed == true)
        #expect(bluetooth.deleteUserByTokenCalls == 0)
    }

    @Test("showDeleteUserAlert with missing token exits early and does not call delete API")
    func showDeleteUserAlertMissingTokenEarlyExit() async {
        let scale = makeScale(isConnected: true, broadcastIdString: "ABC")
        let (store, bluetooth, _, notification, _) = makeSUT(scale: scale)
        var onDeleteCalls = 0

        store.showDeleteUserAlert(for: makeUser(name: "Guest", token: nil)) {
            onDeleteCalls += 1
        }
        notification.alertData?.buttons.last?.action(nil as String?)
        await Task.yield()

        let completed = await waitUntil {
            notification.dismissLoaderCalls == 1 && onDeleteCalls == 1
        }

        #expect(completed == true)
        #expect(bluetooth.deleteUserByTokenCalls == 0)
    }

    @Test("form helper properties expose validation and touched state")
    func formHelperPropertiesExposeValidationAndTouchedState() async {
        let (store, _, _, _, _) = makeSUT(scale: makeScale(isConnected: false))

        #expect(store.isFormValid == false)
        store.setDisplayNameTouched()
        #expect(store.userNameForm.displayName.isDirty == true)
        #expect(store.displayNameError != nil)

        store.userNameForm.setDisplayName("User123")
        #expect(store.displayNameError == nil)
    }

    private func makeSUT(
        scale: Device? = nil,
        initialUsersList: [DeviceUser] = [],
        bluetooth: MockBluetoothService? = nil,
        scaleService: MockScaleService? = nil,
        notification: MockNotificationHelperService? = nil,
        permissions: MockPermissionsService? = nil,
        logger: MockLoggerService? = nil
    // swiftlint:disable:next large_tuple
    ) -> (UsersViewModel, MockBluetoothService, MockScaleService, MockNotificationHelperService, MockPermissionsService) {
        let bluetooth = bluetooth ?? MockBluetoothService()
        let scaleService = scaleService ?? MockScaleService()
        let notification = notification ?? MockNotificationHelperService()
        let permissions = permissions ?? MockPermissionsService()
        let logger = logger ?? MockLoggerService()
        let scale = scale ?? makeScale()

        if permissions.permissions == nil {
            permissions.setPermissions([.BLUETOOTH: .ENABLED, .BLUETOOTH_SWITCH: .ENABLED])
        }

        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        // Publish the scale as a DeviceSnapshot so the ViewModel can resolve it via ScaleService.
        scaleService.scales = [scale.toSnapshot(isConnected: scale.isConnected ?? false)]

        let store = UsersViewModel(scale: scale, initialUsersList: initialUsersList, userDeletionDelayNanoseconds: 0)
        // Pin dependencies on the instance to avoid cross-suite DI races from global container mutation.
        store.notificationService = notification
        store.bluetoothService = bluetooth
        store.permissionsService = permissions
        store.scaleService = scaleService
        store.logger = logger
        return (store, bluetooth, scaleService, notification, permissions)
    }

    private func makeScale(
        id: String = "scale-1",
        isConnected: Bool = true,
        token: String? = "owner-token",
        displayName: String = "Owner",
        broadcastIdString: String? = "ABCDEF"
    ) -> Device {
        let scale = ScaleTestFixtures.makeDevice(
            id: id,
            accountId: "acct-1",
            displayName: displayName
        )
        scale.token = token
        scale.isConnected = isConnected
        scale.broadcastIdString = broadcastIdString
        if let preference = scale.r4ScalePreference {
            preference.displayName = displayName
        } else {
            scale.r4ScalePreference = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: id, displayName: displayName),
                scaleId: id
            )
        }
        return scale
    }

    private func makeUser(name: String, token: String?) -> DeviceUser {
        DeviceUser(name: name, token: token, lastActive: 1, isBodyMetricsEnabled: true)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }
}

private enum UsersViewModelTestsError: Error {
    case updatePreferenceFailed
}
