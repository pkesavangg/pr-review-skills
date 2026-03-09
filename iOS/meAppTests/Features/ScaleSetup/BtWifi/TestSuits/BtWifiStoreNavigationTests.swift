import Foundation
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("Navigation")
    @MainActor
    struct Navigation {
        @Test("intro advances to wakeup when bluetooth permissions and network are available")
        func introAdvancesToWakeupWhenReady() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.handleNextButtonClick()
            await BtWifiStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForPairingCalls == 1
            }

            #expect(store.currentStep == .wakeup)
            #expect(harness.bluetooth.scanForPairingCalls == 1)
        }

        @Test("intro routes to permissions when bluetooth or network is unavailable")
        func introRoutesToPermissionsWhenUnavailable() {
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: MockNetworkMonitor(isConnected: false))
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.handleNextButtonClick()

            #expect(store.currentStep == .permissions)
            #expect(store.isNextEnabled == false)
        }

        @Test("wifi only configuration starts on gathering network with saved scale context")
        func wifiOnlyConfigurationStartsOnGatheringNetwork() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            harness.store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: savedScale,
                isWifiSetupOnly: true
            )

            #expect(harness.store.currentStep == .gatheringNetwork)
            #expect(harness.store.savedScale?.id == savedScale.id)
            #expect(harness.store.isWifiSetupOnlyMode == true)
        }

        @Test("network selection opens password step when prerequisites are satisfied")
        func networkSelectionOpensPasswordStep() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let network = WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -42)

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.availableWifiList)
            store.handleNetworkSelection(network)

            #expect(store.currentStep == .wifiPassword)
            #expect(store.selectedWifiNetwork == network)
            #expect(store.networkForm.ssid.value == "Home WiFi")
        }

        @Test("network selection falls back to gathering network when bluetooth permissions are missing")
        func networkSelectionFallsBackToGatheringNetworkWhenPermissionsMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .DISABLED,
                .BLUETOOTH_SWITCH: .ENABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.availableWifiList)
            store.handleNetworkSelection(WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -42))

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.connectionState == .loading)
        }

        @Test("back button from wifi password clears form state and returns to wifi list")
        func backButtonFromWifiPasswordClearsForm() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.availableWifiList)
            store.handleNetworkSelection(WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -42))
            store.networkForm.setPassword("super-secret")

            store.handleBackButtonClick()

            #expect(store.currentStep == .availableWifiList)
            #expect(store.networkForm.ssid.value.isEmpty)
            #expect(store.networkForm.password.value.isEmpty)
        }

        @Test("reconnect configuration loads scale users and shows max user recovery state")
        func reconnectConfigurationLoadsUsersAndShowsMaxUserRecoveryState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Existing 1", token: "other-1", lastActive: 1, isBodyMetricsEnabled: true),
                DeviceUser(name: "Existing 2", token: "other-2", lastActive: 2, isBodyMetricsEnabled: false)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let discoveredScale = BtWifiStoreTestFixtures.makeScale(token: "current-user")
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            harness.store.configure(
                with: SettingsConstants.defaultR4Sku,
                discoveredScale: discoveredScale,
                discoveryEvent: discoveryEvent,
                saveScale: discoveredScale,
                isReconnect: true,
                isWifiSetupOnly: false
            )

            await BtWifiStoreTestFixtures.waitUntil {
                harness.store.scaleSetupError == .maxUserReached && harness.store.userList.count == 2
            }

            #expect(harness.store.currentStep == .gatheringNetwork)
            #expect(harness.store.scaleSetupError == .maxUserReached)
            #expect(harness.store.userList.count == 2)
        }
    }
}
