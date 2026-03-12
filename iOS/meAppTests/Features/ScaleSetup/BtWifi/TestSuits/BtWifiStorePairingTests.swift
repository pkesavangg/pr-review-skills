import Foundation
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("Pairing Flow")
    @MainActor
    struct PairingFlow {
        @Test("new discovery event captures device context and advances to bluetooth connection")
        func newDiscoveryAdvancesToBluetoothConnection() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale, isNew: true)

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wakeup)

            store.handleDeviceDiscovery(discoveryEvent)

            #expect(store.discoveredScale?.id == discoveredScale.id)
            #expect(store.discoveryEvent == discoveryEvent)
            #expect(store.currentStep == .connectingBluetooth)
        }

        @Test("known discovery event triggers disconnect handling and shows known scale alert")
        func knownDiscoveryShowsAlert() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale, isNew: false)

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wakeup)

            store.handleDeviceDiscovery(discoveryEvent)
            await Task.yield()

            #expect(harness.notification.showAlertCalls == 1)
            #expect(harness.bluetoothSetupManager.disconnectIfNeededCalls == 1)
            #expect(harness.bluetoothSetupManager.lastDisconnectedBroadcastId == discoveredScale.broadcastIdString)
        }

        @Test("confirmPair success saves the scale and marks bluetooth connection successful")
        func confirmPairSuccessSavesScale() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(deviceName: "R4"))
            bluetooth.getWifiMacAddressResult = .success("AA:BB:CC:DD:EE:FF")
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            store.scaleItem = discoveryEvent.deviceInfo
            store.discoveredScale = discoveredScale
            store.discoveryEvent = discoveryEvent
            store.scaleToken = "setup-token"

            await store.confirmPair()
            await BtWifiStoreTestFixtures.waitUntil {
                harness.pushNotifications.lastIsFromScaleSetup == true
            }

            #expect(bluetooth.confirmSmartPairCalls == 1)
            #expect(harness.scaleService.createR4ScaleCalls == 1)
            #expect(store.savedScale?.token == "setup-token")
            #expect(store.connectionState == .success)
            #expect(harness.pushNotifications.setupPushNotificationsCalls == 1)
            #expect(harness.pushNotifications.lastIsFromScaleSetup == true)
        }

        @Test("confirmPair duplicate user response loads duplicate recovery state")
        func confirmPairDuplicateUserResponseLoadsRecoveryState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.duplicateUserError)
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 10, isBodyMetricsEnabled: true),
                DeviceUser(name: "Lakshmi", token: "dup-2", lastActive: 12, isBodyMetricsEnabled: false),
                DeviceUser(name: "Other", token: "other", lastActive: 1, isBodyMetricsEnabled: false)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.scaleToken = "setup-token"

            await store.confirmPair()

            #expect(store.scaleSetupError == .duplicatesFound)
            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.currentUser?.name == "Lakshmi")
            #expect(store.duplicateList.count == 2)
            #expect(store.userNameForm.displayName.value == "Lakshmi")
        }

        @Test("confirmPair memory full response loads max user recovery state")
        func confirmPairMemoryFullLoadsMaxUserState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.memoryFull)
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "User 1", token: "1", lastActive: 1, isBodyMetricsEnabled: true)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.scaleToken = "setup-token"

            await store.confirmPair()

            #expect(store.scaleSetupError == .maxUserReached)
            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.userList.count == 1)
        }

        @Test("confirmPair failure leaves bluetooth connection in failure state")
        func confirmPairFailureLeavesConnectionInFailureState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.scaleToken = "setup-token"

            await store.confirmPair()

            #expect(store.connectionState == .failure)
            #expect(harness.scaleService.createR4ScaleCalls == 0)
        }
    }
}
