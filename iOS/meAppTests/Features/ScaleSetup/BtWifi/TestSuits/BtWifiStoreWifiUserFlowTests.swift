import Foundation
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("WiFi And Users")
    @MainActor
    struct WifiAndUsers {
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

            store.savedScale = savedScale

            await store.fetchWifiNetworks(for: savedScale)

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

            store.savedScale = savedScale

            await store.fetchWifiNetworks(for: savedScale)

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

            store.savedScale = savedScale
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

            store.savedScale = savedScale
            store.networkForm.setSSID("Home WiFi")
            store.networkForm.setPassword("secret")

            await store.setupWifi()

            #expect(store.connectionState == .failure)
            #expect(store.errorCode == "104")
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

        @Test("saving duplicate user preserves edited name and returns to bluetooth connection")
        func savingDuplicateUserPreservesEditedName() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.initialDisplayNameSnapshot = "Lakshmi"
            store.userNameForm.updateUserList([ScaleUser(name: "Lakshmi", token: "dup")])
            store.userNameForm.setDisplayName("Lakshmi2")

            store.handleSaveDuplicateUser()

            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.duplicateUserName == "Lakshmi2")
            #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.userName.rawValue))
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
    }
}
