import Combine
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

        @Test("known discovery without a broadcast id still shows alert without disconnecting")
        func knownDiscoveryWithoutBroadcastIdSkipsDisconnect() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let discoveredScale = ScaleTestFixtures.makeDevice(broadcastIdString: "", broadcastId: nil)
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale, isNew: false)

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wakeup)

            store.handleDeviceDiscovery(discoveryEvent)
            await Task.yield()

            #expect(harness.notification.showAlertCalls == 1)
            #expect(harness.bluetoothSetupManager.disconnectIfNeededCalls == 0)
            #expect(store.discoveredScale?.id == discoveredScale.id)
        }

        @Test("known scale alert action triggers exit cleanup")
        func knownScaleAlertPrimaryActionTriggersExitCleanup() throws {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            var dismissCalls = 0

            store.dismissAction = { dismissCalls += 1 }
            harness.bluetooth.isSetupInProgress = true

            store.showKnownScaleAlert()
            let alert = try #require(harness.notification.alertData)
            alert.buttons[0].action(nil)

            #expect(store.isExiting == true)
            #expect(dismissCalls == 1)
            #expect(harness.bluetooth.isSetupInProgress == false)
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

        @Test("confirmPair success progresses to gathering network after persistence completes")
        func confirmPairSuccessProgressesToGatheringNetwork() async {
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
            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 3_000_000_000) {
                store.currentStep == .gatheringNetwork
            }

            #expect(store.currentStep == .gatheringNetwork)
        }

        @Test("confirmPair uses the edited duplicate username when retrying pairing")
        func confirmPairUsesDuplicateUserNameWhenPresent() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.scaleToken = "setup-token"
            store.duplicateUserName = "Recovered Name"

            await store.confirmPair()

            #expect(bluetooth.lastConfirmedPairDisplayName == "Recovered Name")
            #expect(bluetooth.lastConfirmedPairToken == "setup-token")
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

        @Test("confirmPair token fetch failure aborts before bluetooth confirmation")
        func confirmPairTokenFetchFailureSkipsBluetoothConfirmation() async {
            let bluetooth = MockBluetoothService()
            let wifiScaleService = MockWifiScaleService()
            wifiScaleService.getScaleTokenResult = .failure(ScaleTestError.localFailure)
            let harness = BtWifiStoreTestFixtures.makeSUT(
                bluetooth: bluetooth,
                wifiScaleService: wifiScaleService
            )
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            await store.confirmPair()

            #expect(store.scaleToken == nil)
            #expect(store.connectionState == .failure)
            #expect(wifiScaleService.getScaleTokenCalls == 1)
            #expect(bluetooth.confirmSmartPairCalls == 0)
        }

        @Test("confirmPair input data error leaves bluetooth connection in failure state")
        func confirmPairInputDataErrorLeavesConnectionInFailureState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.inputDataError)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.scaleToken = "setup-token"

            await store.confirmPair()

            #expect(store.connectionState == .failure)
            #expect(harness.scaleService.createR4ScaleCalls == 0)
            #expect(store.currentStep != .gatheringNetwork)
        }

        @Test("confirmPair unexpected response leaves pairing in failure state")
        func confirmPairUnexpectedResponseLeavesConnectionFailure() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationFailed)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.scaleToken = "setup-token"

            await store.confirmPair()

            #expect(store.connectionState == .failure)
            #expect(store.currentStep != .gatheringNetwork)
            #expect(harness.scaleService.createR4ScaleCalls == 0)
        }

        @Test("connecting bluetooth without discovery context fails fast")
        func connectingBluetoothWithoutDiscoveryContextFailsFast() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.connectingBluetooth)

            await BtWifiStoreTestFixtures.waitUntil {
                store.connectionState == .failure
            }

            #expect(store.connectionState == .failure)
            #expect(harness.bluetooth.confirmSmartPairCalls == 0)
        }

        @Test("confirmPair without discovery data fails without invoking bluetooth pairing")
        func confirmPairWithoutDiscoveryDataFailsFast() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()

            await harness.store.confirmPair()

            #expect(harness.store.connectionState == .failure)
            #expect(harness.bluetooth.confirmSmartPairCalls == 0)
        }

        @Test("fetchWifiScaleToken caches the first successful token and skips repeated fetches")
        func fetchWifiScaleTokenCachesFirstSuccess() async {
            let wifiScaleService = MockWifiScaleService()
            wifiScaleService.getScaleTokenResult = .success(WifiScaleTokenResponse(token: "cached-token"))
            let harness = BtWifiStoreTestFixtures.makeSUT(wifiScaleService: wifiScaleService)
            let store = harness.store

            await store.fetchWifiScaleToken()
            await store.fetchWifiScaleToken()

            #expect(store.scaleToken == "cached-token")
            #expect(wifiScaleService.getScaleTokenCalls == 1)
            #expect(wifiScaleService.lastScaleTokenRequest == "4")
        }

        @Test("fetchWifiScaleToken failure leaves pairing in a recoverable failure state")
        func fetchWifiScaleTokenFailureSetsConnectionFailure() async {
            let wifiScaleService = MockWifiScaleService()
            wifiScaleService.getScaleTokenResult = .failure(ScaleTestError.localFailure)
            let harness = BtWifiStoreTestFixtures.makeSUT(wifiScaleService: wifiScaleService)

            await harness.store.fetchWifiScaleToken()

            #expect(harness.store.scaleToken == nil)
            #expect(harness.store.connectionState == .failure)
            #expect(wifiScaleService.getScaleTokenCalls == 1)
        }

        @Test("saveScale missing required context does not create a device")
        func saveScaleMissingContextDoesNotCreateDevice() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()

            await harness.store.saveScale()

            #expect(harness.scaleService.createR4ScaleCalls == 0)
            #expect(harness.store.savedScale == nil)
        }

        @Test("saveScale create failure leaves bluetooth connection in failure state")
        func saveScaleCreateFailureLeavesConnectionInFailureState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(deviceName: "R4"))
            bluetooth.getWifiMacAddressResult = .success("AA:BB:CC:DD:EE:FF")
            let scaleService = MockScaleService()
            scaleService.createR4ScaleError = ScaleTestError.localFailure
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            store.scaleItem = discoveryEvent.deviceInfo
            store.discoveredScale = discoveredScale
            store.discoveryEvent = discoveryEvent
            store.scaleToken = "setup-token"

            await store.saveScale()

            #expect(scaleService.createR4ScaleCalls == 1)
            #expect(store.savedScale == nil)
            #expect(store.connectionState == .failure)
            #expect(harness.pushNotifications.setupPushNotificationsCalls == 0)
        }

        @Test("saveScale still persists when metadata lookups and status refresh fail")
        func saveScaleSucceedsDespiteMetadataAndStatusFailures() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .failure(.notImplemented)
            bluetooth.getWifiMacAddressResult = .failure(.notImplemented)
            let scaleService = MockScaleService()
            scaleService.updateAllScalesStatusError = ScaleTestError.localFailure
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            store.scaleItem = discoveryEvent.deviceInfo
            store.discoveredScale = discoveredScale
            store.discoveryEvent = discoveryEvent
            store.scaleToken = "setup-token"

            await store.saveScale()
            await BtWifiStoreTestFixtures.waitUntil {
                harness.pushNotifications.setupPushNotificationsCalls == 1
            }

            #expect(scaleService.createR4ScaleCalls == 1)
            #expect(scaleService.lastCreatedR4Scale?.metaData == nil)
            #expect(scaleService.lastCreatedR4Scale?.wifiMac == discoveredScale.wifiMac)
            #expect(scaleService.updateAllScalesStatusCalls == 1)
            #expect(store.savedScale?.token == "setup-token")
        }

        @Test("saveScale preserves duplicate username and reconnect duplicate-check bypass")
        func saveScalePreservesDuplicateRecoveryContext() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(deviceName: "R4"))
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale(displayName: "Stale Name")
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            store.scaleItem = discoveryEvent.deviceInfo
            store.discoveredScale = discoveredScale
            store.discoveryEvent = discoveryEvent
            store.scaleToken = "setup-token"
            store.duplicateUserName = "Recovered Name"
            store.isReconnect = true

            await store.saveScale()

            #expect(scaleService.lastCreatedR4Scale?.r4ScalePreference?.displayName == "Recovered Name")
            #expect(scaleService.lastCreateR4ScaleSkipDuplicateCheck == true)
        }

        @Test("resetDiscoveryState clears stale pairing context and cancels transient work")
        func resetDiscoveryStateClearsPairingContext() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let staleScale = BtWifiStoreTestFixtures.makeScale(id: "stale-scale")

            store.deviceDiscoveryCancellable = PassthroughSubject<Int, Never>().sink { _ in }
            store.stepTimerTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
            let timerTask = store.stepTimerTask
            store.discoveredScale = staleScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: staleScale)
            store.scaleSetupError = .duplicatesFound

            store.resetDiscoveryState()

            #expect(store.deviceDiscoveryCancellable == nil)
            #expect(timerTask?.isCancelled == true)
            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
            #expect(store.scaleSetupError == .none)
        }

        @Test("repeated pair attempts clear stale discovery state before subscribing again")
        func repeatedPairAttemptsClearStaleState() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let staleScale = BtWifiStoreTestFixtures.makeScale(id: "stale-scale")

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.discoveredScale = staleScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: staleScale)
            store.scaleSetupError = .duplicatesFound

            store.pair()
            await Task.yield()

            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
            #expect(store.scaleSetupError == .none)
            #expect(harness.bluetooth.scanForPairingCalls >= 1)
            #expect(store.deviceDiscoveryCancellable != nil)

            store.navigateToStep(.wakeup)
            store.discoveredScale = staleScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: staleScale)
            store.scaleSetupError = .maxUserReached

            store.pair()
            await Task.yield()

            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
            #expect(store.scaleSetupError == .none)
            #expect(harness.bluetooth.scanForPairingCalls >= 2)
        }
    }
}
