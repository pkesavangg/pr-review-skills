import Foundation
@testable import meApp
import Testing

extension BtWifiStoreTests {
    @Suite("Recovery And Errors")
    @MainActor
    struct RecoveryAndErrors {
        @Test("try again from wifi failure resets state and returns to gathering network")
        func tryAgainFromWifiFailureResetsState() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.availableWifiList)
            store.scaleSetupError = .wifiConnectionFailed
            store.connectionState = .failure

            store.tryAgainButtonHandler()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .none)
            #expect(store.connectionState == .loading)
            #expect(store.isRefreshingWifiNetworks == false)
        }

        @Test("try again from bluetooth failure targets connecting bluetooth when a discovery context exists")
        func tryAgainFromBluetoothFailureTargetsConnectingBluetooth() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.navigateToStep(.connectingBluetooth)

            store.tryAgainButtonHandler(isFromBtConnection: true)

            #expect(store.currentStep == .connectingBluetooth)
        }

        @Test("try again from measurement timeout returns to step on")
        func tryAgainFromMeasurementTimeoutReturnsToStepOn() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.measurement)
            store.scaleSetupError = .collectMeasurementFailed

            store.tryAgainButtonHandler()

            #expect(store.currentStep == .stepOn)
        }

        @Test("duplicate user next button stays disabled until a valid changed name is entered")
        func duplicateUserNextButtonRequiresValidChangedName() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.initialDisplayNameSnapshot = "Lakshmi"
            store.userNameForm.updateUserList([DeviceUser(name: "Taken", token: "dup")])
            store.userNameForm.setDisplayName("Lakshmi")
            store.updateNextEnabled()
            #expect(store.isNextEnabled == false)

            store.userNameForm.setDisplayName("Lakshmi2")
            store.updateNextEnabled()
            #expect(store.isNextEnabled == true)
        }

        @Test("wifi password next button follows network validation rules")
        func wifiPasswordNextButtonFollowsValidationRules() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wifiPassword)
            store.networkForm.setSSID("Home WiFi")
            store.networkForm.setPassword("")
            store.updateNextEnabled()
            #expect(store.isNextEnabled == false)

            store.networkForm.setPassword("secret")
            store.updateNextEnabled()
            #expect(store.isNextEnabled == true)

            store.networkForm.networkHasNoPassword = true
            store.networkForm.setPassword("")
            store.updateNextEnabled()
            #expect(store.isNextEnabled == true)
        }

        @Test("network loss on wifi list recovers back to gathering network")
        func networkLossOnWifiListRecoversBackToGatheringNetwork() {
            let networkMonitor = MockNetworkMonitor(isConnected: true)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.availableWifiList)
            networkMonitor.isConnected = false
            store.handlePermissionChange()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .noNetworkFound)
            #expect(store.connectionState == .noNetworks)
        }

        @Test("permissions step requests the next missing bluetooth permission")
        func permissionsStepRequestsMissingBluetoothPermission() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .DISABLED,
                .BLUETOOTH_SWITCH: .DISABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)

            harness.store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            harness.store.navigateToStep(.permissions)
            harness.store.updateNextEnabled()
            await Task.yield()

            #expect(permissions.handlePermissionCalls.isEmpty == false)
            #expect(permissions.handlePermissionCalls.contains(.bluetooth))
        }
    }

}
