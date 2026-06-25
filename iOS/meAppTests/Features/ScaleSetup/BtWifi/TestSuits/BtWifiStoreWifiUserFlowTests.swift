import Foundation
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("WiFi And Users")
    @MainActor
    struct WifiAndUsers {
        @Test("restore account helper names preserve original user then fall back to trimmed account state")
        func restoreAccountNameHelpersPreferExpectedValues() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.currentUser = DeviceUser(name: "  Existing User  ", token: "dup", lastActive: 4, isBodyMetricsEnabled: true)
            #expect(store.getAccountNameForRestore() == "Existing User")

            store.currentUser = nil
            store.firstName = "  Lakshmi  "
            #expect(store.getAccountNameForRestore() == "Lakshmi")

            #expect(store.resolveUsernameToPreserve(from: "  Kept Name  ") == "  Kept Name  ")
            store.duplicateUserName = "Retry Name"
            #expect(store.resolveUsernameToPreserve(from: "") == "Retry Name")

            store.duplicateUserName = ""
            store.firstName = "Fallback"
            #expect(store.resolveUsernameToPreserve(from: "") == "Fallback")
        }

        @Test("fetchWifiNetworks success populates available networks and connected ssid")
        func fetchWifiNetworksSuccessPopulatesNetworks() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getConnectedWifiSSIDResult = .success("Home WiFi")
            bluetooth.getWifiListResult = .success([
                WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -35),
                WifiDetails(macAddress: "BB", ssid: "Office", rssi: -48)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.savedScale = savedScale.toSnapshot()

            await store.fetchWifiNetworks(for: savedScale.broadcastIdString ?? "")

            #expect(store.currentStep == .availableWifiList)
            #expect(store.connectionState == .success)
            #expect(store.wifiNetworks.count == 2)
            #expect(store.connectedWifiNetwork?.ssid == "Home WiFi")
        }

        @Test("fetchWifiNetworks failure sets no network error state")
        func fetchWifiNetworksFailureSetsErrorState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getConnectedWifiSSIDResult = .success("")
            bluetooth.getWifiListResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.savedScale = savedScale.toSnapshot()

            await store.fetchWifiNetworks(for: savedScale.broadcastIdString ?? "")

            #expect(store.connectionState == .failure)
            #expect(store.scaleSetupError == .noNetworkFound)
        }

        @Test("wifi password connect requires valid password unless the network is open")
        func wifiPasswordConnectRequiresValidPasswordUnlessOpenNetwork() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wifiPassword)
            store.networkForm.setSSID("Home WiFi")

            store.handleWifiPasswordConnect()
            #expect(store.currentStep == .wifiPassword)

            store.networkForm.networkHasNoPassword = true
            store.handleWifiPasswordConnect()
            #expect(store.currentStep == .connectingWifi)
        }

        @Test("setupWifi success updates wifi status and clears network form")
        func setupWifiSuccessUpdatesWifiStatus() async {
            let bluetooth = MockBluetoothService()
            bluetooth.setupWifiResult = .success(WifiSetupResponse(wifiState: "GG_WIFI_STATE_CONNECTED"))
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.savedScale = savedScale.toSnapshot()
            store.networkForm.setSSID("Home WiFi")
            store.networkForm.setPassword("secret")

            await store.setupWifi()

            #expect(store.connectionState == .success)
            #expect(scaleService.updateConnectedDeviceWifiStatusCalls == 1)
            #expect(store.networkForm.ssid.value.isEmpty)
            #expect(store.networkForm.password.value.isEmpty)
        }

        @Test("setupWifi failure keeps failure state and error code")
        func setupWifiFailureKeepsFailureState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.setupWifiResult = .success(WifiSetupResponse(wifiState: "GG_WIFI_STATE_FAILED", errorCode: "104"))
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.savedScale = savedScale.toSnapshot()
            store.networkForm.setSSID("Home WiFi")
            store.networkForm.setPassword("secret")

            await store.setupWifi()

            #expect(store.connectionState == .failure)
            #expect(store.errorCode == "104")
        }

        @Test("findMatchingUserOnScale supports exact matches, truncated matches, and lookup failures")
        func findMatchingUserOnScaleSupportsExactAndTruncatedMatches() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let scale = BtWifiStoreTestFixtures.makeScale()

            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "lakshmi", token: "exact", lastActive: 1, isBodyMetricsEnabled: false)
            ])
            let exact = await store.findMatchingUserOnScale(scale: scale, accountName: " Lakshmi ")
            #expect(exact?.token == "exact")

            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "averyveryverylongnam", token: "truncated", lastActive: 1, isBodyMetricsEnabled: false)
            ])
            let truncated = await store.findMatchingUserOnScale(
                scale: scale,
                accountName: "AveryVeryVeryLongNameMore"
            )
            #expect(truncated?.token == "truncated")

            bluetooth.getScaleUserListResult = .failure(.notImplemented)
            let failure = await store.findMatchingUserOnScale(scale: scale, accountName: "Lakshmi")
            #expect(failure == nil)
        }

        @Test("deleteMatchingUserFromScale validates token and broadcast id before deleting")
        func deleteMatchingUserFromScaleValidatesContext() async {
            let bluetooth = MockBluetoothService()
            bluetooth.deleteUserByTokenResult = .success(.success)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            let missingToken = await store.deleteMatchingUserFromScale(
                scale: BtWifiStoreTestFixtures.makeScale(),
                user: DeviceUser(name: "User", token: nil, lastActive: 1, isBodyMetricsEnabled: false)
            )
            #expect(missingToken == false)

            let scaleWithoutBroadcast = ScaleTestFixtures.makeDevice(broadcastIdString: "", broadcastId: nil)
            let missingBroadcast = await store.deleteMatchingUserFromScale(
                scale: scaleWithoutBroadcast,
                user: DeviceUser(name: "User", token: "dup", lastActive: 1, isBodyMetricsEnabled: false)
            )
            #expect(missingBroadcast == false)

            let success = await store.deleteMatchingUserFromScale(
                scale: BtWifiStoreTestFixtures.makeScale(),
                user: DeviceUser(name: "User", token: "dup", lastActive: 1, isBodyMetricsEnabled: false)
            )
            #expect(success == true)
            #expect(bluetooth.deleteUserByTokenCalls == 1)

            bluetooth.deleteUserByTokenResult = .failure(.notImplemented)
            let failure = await store.deleteMatchingUserFromScale(
                scale: BtWifiStoreTestFixtures.makeScale(),
                user: DeviceUser(name: "User", token: "dup-2", lastActive: 1, isBodyMetricsEnabled: false)
            )
            #expect(failure == false)
            #expect(bluetooth.deleteUserByTokenCalls == 2)
        }

        @Test("performRestoreAccount success deletes the matching scale user and reconnects")
        func performRestoreAccountSuccessReconnects() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: true),
                DeviceUser(name: "Other", token: "other", lastActive: 1, isBodyMetricsEnabled: false)
            ])
            bluetooth.deleteUserByTokenResult = .success(.success)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.currentUser = DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: true)

            await store.performRestoreAccount()
            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
                store.currentStep == .connectingBluetooth
            }

            #expect(bluetooth.deleteUserByTokenCalls == 1)
            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.duplicateUserName == "Lakshmi")
        }

        @Test("performRestoreAccount without bluetooth permissions routes to permissions")
        func performRestoreAccountWithoutPermissionsRoutesToPermissions() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .DISABLED,
                .BLUETOOTH_SWITCH: .ENABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            await store.performRestoreAccount()

            #expect(store.currentStep == .permissions)
        }

        @Test("performRestoreAccount failure paths keep duplicate recovery coherent")
        func performRestoreAccountFailurePathsKeepDuplicateRecoveryCoherent() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            await store.performRestoreAccount()
            #expect(store.scaleSetupError == .duplicatesFound)

            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.firstName = ""

            await store.performRestoreAccount()
            #expect(store.scaleSetupError == .duplicatesFound)
            #expect(store.userNameForm.displayName.value.isEmpty)

            store.firstName = "Lakshmi"
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Other", token: "other", lastActive: 1, isBodyMetricsEnabled: false)
            ])

            await store.performRestoreAccount()
            #expect(store.scaleSetupError == .duplicatesFound)
            #expect(bluetooth.deleteUserByTokenCalls == 0)

            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: true)
            ])
            bluetooth.deleteUserByTokenResult = .failure(.notImplemented)

            await store.performRestoreAccount()
            #expect(store.scaleSetupError == .duplicatesFound)
            #expect(bluetooth.deleteUserByTokenCalls == 1)
            #expect(store.currentStep != .connectingBluetooth)
        }

        @Test("restartConnectionAndNavigate preserves repeated input and latest retry name")
        func restartConnectionAndNavigatePreservesRepeatedInput() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.connectionState = .failure
            store.userList = [
                DeviceUser(name: "Old", token: "old", lastActive: 1, isBodyMetricsEnabled: false)
            ]
            store.currentUser = DeviceUser(name: "Lakshmi", token: "dup", lastActive: 2, isBodyMetricsEnabled: false)
            store.duplicateList = [store.currentUser!] // swiftlint:disable:this force_unwrapping
            store.duplicateUserLastActiveAt = 2
            store.duplicateUserName = "Previous Retry"
            store.userNameForm.setDisplayName(" Latest Retry ")

            await store.restartConnectionAndNavigate()
            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
                store.currentStep == .connectingBluetooth
            }

            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.scaleSetupError == .none)
            #expect(store.connectionState == .loading)
            #expect(store.userList.isEmpty)
            #expect(store.currentUser == nil)
            #expect(store.duplicateList.isEmpty)
            #expect(store.duplicateUserLastActiveAt == nil)
            #expect(store.duplicateUserName == "Latest Retry")
            #expect(store.userNameForm.displayName.value == "Latest Retry")

            store.duplicateUserName = "Recovered Name"
            store.userNameForm.setDisplayName("   ")
            await store.restartConnectionAndNavigate()
            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
                store.currentStep == .connectingBluetooth
            }

            #expect(store.duplicateUserName == "Recovered Name")
            #expect(store.userNameForm.displayName.value == "Recovered Name")
        }

        @Test("saving duplicate user preserves edited name and returns to bluetooth connection")
        func savingDuplicateUserPreservesEditedName() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.initialDisplayNameSnapshot = "Lakshmi"
            store.userNameForm.updateUserList([DeviceUser(name: "Lakshmi", token: "dup")])
            store.userNameForm.setDisplayName("Lakshmi2")

            store.handleSaveDuplicateUser()

            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.duplicateUserName == "Lakshmi2")
            #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.userName.rawValue))
        }

        @Test("saving duplicate user rejects invalid input and remains stable across repeated saves")
        func savingDuplicateUserRejectsInvalidInputAndRemainsStable() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.userNameForm.updateUserList([DeviceUser(name: "Taken", token: "dup")])
            store.userNameForm.setDisplayName("Taken")

            store.handleSaveDuplicateUser()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.duplicateUserName.isEmpty)
            #expect(store.selectedCustomizeItems.isEmpty)

            store.userNameForm.setDisplayName("Taken2")
            store.handleSaveDuplicateUser()
            store.handleSaveDuplicateUser()

            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.duplicateUserName == "Taken2")
            #expect(store.selectedCustomizeItems == [CustomizeSettingsItem.userName.rawValue])
        }

        @Test("getUserList filters out the current scale token")
        func getUserListFiltersOutCurrentScaleToken() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Current", token: "token-1", lastActive: 1, isBodyMetricsEnabled: true),
                DeviceUser(name: "Other", token: "token-2", lastActive: 2, isBodyMetricsEnabled: false)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale(token: "token-1")

            store.discoveredScale = discoveredScale

            await store.getUserList()

            #expect(store.userList.map(\.name) == ["Other"])
        }

        @Test("getUserList updates username customization state and preserves existing current user name")
        func getUserListUpdatesUsernameCustomizationState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Other", token: "token-2", lastActive: 2, isBodyMetricsEnabled: false),
                DeviceUser(name: "Another", token: "token-3", lastActive: 3, isBodyMetricsEnabled: false)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale(token: "token-1")

            store.discoveredScale = discoveredScale
            store.currentCustomizeSetting = .scaleUsername
            store.initialDisplayNameSnapshot = "Snapshot Name"

            await store.getUserList()

            #expect(store.userList.map(\.name) == ["Other", "Another"])
            #expect(store.userNameForm.userList.map(\.name) == ["Other", "Another"])
            #expect(store.userNameForm.currentUserName == "Snapshot Name")

            store.userNameForm.setCurrentUserName("Existing Current")
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Fresh", token: "token-4", lastActive: 4, isBodyMetricsEnabled: false)
            ])

            await store.getUserList()

            #expect(store.userList.map(\.name) == ["Fresh"])
            #expect(store.userNameForm.currentUserName == "Existing Current")
            #expect(bluetooth.getScaleUserListCalls == 2)
        }

        @Test("getUserList and restartConnection guard missing discovery context without mutating state")
        func getUserListAndRestartConnectionGuardMissingDiscoveryContext() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            store.userList = [
                DeviceUser(name: "Preserved", token: "keep", lastActive: 1, isBodyMetricsEnabled: false)
            ]
            await store.getUserList()

            #expect(store.userList.map(\.name) == ["Preserved"])
            #expect(bluetooth.getScaleUserListCalls == 0)

            store.connectionState = .failure
            await store.restartConnection()
            #expect(store.connectionState == .failure)
            #expect(store.userList.isEmpty)
        }

        @Test("checkDuplicateUserList and deleteUser helpers keep repeated recovery state coherent")
        func duplicateListChecksAndDeleteHelpersRemainCoherent() async {
            let bluetooth = MockBluetoothService()
            bluetooth.deleteUserByTokenResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.firstName = "Lakshmi"
            store.userList = [
                DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: false),
                DeviceUser(name: "Lakshmi", token: "dup-2", lastActive: 7, isBodyMetricsEnabled: true),
                DeviceUser(name: "Other", token: "other", lastActive: 1, isBodyMetricsEnabled: false)
            ]

            store.checkDuplicateUserList()
            #expect(store.currentUser?.token == "dup-1")
            #expect(store.duplicateList.map { $0.token ?? "" } == ["dup-1", "dup-2"])
            #expect(store.duplicateUserLastActiveAt == 4)

            await store.deleteUserFromScale(DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: false))
            #expect(bluetooth.deleteUserByTokenCalls == 0)

            store.discoveredScale = ScaleTestFixtures.makeDevice(broadcastIdString: "", broadcastId: nil)
            await store.deleteUserFromScale(DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: false))
            #expect(bluetooth.deleteUserByTokenCalls == 0)

            store.discoveredScale = discoveredScale
            await store.deleteUserFromScale(DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: false))
            #expect(bluetooth.deleteUserByTokenCalls == 1)
            #expect(bluetooth.lastDeleteUserBroadcastId == discoveredScale.broadcastIdString)
            #expect(bluetooth.lastDeleteUserToken == "dup-1")
        }

        @Test("deleteUsers skips invalid entries and resets duplicate form state for backtracking")
        func deleteUsersSkipsInvalidEntriesAndResetsFormState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.deleteUserByTokenResult = .success(.success)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            await store.deleteUsers()
            #expect(bluetooth.deleteUserByTokenCalls == 0)

            store.discoveredScale = ScaleTestFixtures.makeDevice(broadcastIdString: "", broadcastId: nil)
            store.duplicateList = [
                DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 1, isBodyMetricsEnabled: false)
            ]
            await store.deleteUsers()
            #expect(bluetooth.deleteUserByTokenCalls == 0)

            store.discoveredScale = discoveredScale
            store.firstName = "Lakshmi"
            store.duplicateUserName = "Retry Name"
            store.userNameForm.setDisplayName("Retry Name")
            store.duplicateList = [
                DeviceUser(name: "Lakshmi", token: nil, lastActive: 1, isBodyMetricsEnabled: false),
                DeviceUser(name: "Lakshmi", token: "", lastActive: 2, isBodyMetricsEnabled: false),
                DeviceUser(name: "Lakshmi", token: "dup-2", lastActive: 3, isBodyMetricsEnabled: false)
            ]

            await store.deleteUsers()

            #expect(bluetooth.deleteUserByTokenCalls == 1)
            #expect(bluetooth.lastDeleteUserToken == "dup-2")
            #expect(store.duplicateUserName == "Lakshmi")
            #expect(store.userNameForm.displayName.value == "Lakshmi")
        }
    }
}
