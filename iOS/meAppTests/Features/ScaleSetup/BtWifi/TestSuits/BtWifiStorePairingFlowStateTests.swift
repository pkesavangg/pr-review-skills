import Combine
import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("Pairing Flow State")
    @MainActor
    struct PairingFlowState {
        @Test("gathering network skips fetch while showing no network error")
        func gatheringNetworkWithNoNetworkErrorSkipsFetch() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale()
            store.scaleSetupError = .noNetworkFound
            store.connectionState = .failure

            store.navigateToStep(.gatheringNetwork)
            await Task.yield()

            #expect(store.connectionState == .failure)
            #expect(store.fetchWifiNetworksTask == nil)
            #expect(bluetooth.getWifiListCalls == 0)
        }

        @Test("settings wifi gathering network skips refetch when returning from wifi list")
        func gatheringNetworkInSettingsFlowSkipsFetchWhenReturningFromWifiList() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: savedScale,
                isWifiSetupOnly: true
            )
            store.fetchWifiNetworksTask = nil
            store.navigateToStep(.availableWifiList)

            store.navigateToStep(.gatheringNetwork)
            await Task.yield()

            #expect(store.connectionState == .loading)
            #expect(store.fetchWifiNetworksTask == nil)
            #expect(store.isRefreshingWifiNetworks == false)
            #expect(bluetooth.getWifiListCalls == 0)
        }

        @Test("settings wifi gathering network refresh cancels stale work and refetches networks")
        func gatheringNetworkRefreshRefetchesNetworks() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getConnectedWifiSSIDResult = .success("Home WiFi")
            bluetooth.getWifiListResult = .success([
                WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -35)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: savedScale,
                isWifiSetupOnly: true
            )
            store.navigateToStep(.availableWifiList)

            let staleFetchTask = Task<Void, Never> {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
            store.fetchWifiNetworksTask = staleFetchTask
            store.isRefreshingWifiNetworks = true

            store.navigateToStep(.gatheringNetwork)

            await BtWifiStoreTestFixtures.waitUntil {
                bluetooth.getWifiListCalls == 1 &&
                    store.currentStep == .availableWifiList &&
                    store.connectedWifiNetwork?.ssid == "Home WiFi"
            }

            #expect(staleFetchTask.isCancelled == true)
            #expect(store.isRefreshingWifiNetworks == false)
            #expect(store.connectionState == .loading)
        }

        @Test("settings wifi list step clears transient error state on entry")
        func availableWifiListInSettingsFlowClearsTransientErrorState() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: BtWifiStoreTestFixtures.makeScale(),
                isWifiSetupOnly: true
            )
            store.scaleSetupError = .noNetworkFound
            store.connectionState = .failure

            store.navigateToStep(.availableWifiList)

            #expect(store.scaleSetupError == .none)
            #expect(store.connectionState == .loading)
        }

        @Test("view settings username step fetches users when list is empty")
        func viewSettingsUsernameStepFetchesUsersWhenNeeded() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Other", token: "token-2", lastActive: 2, isBodyMetricsEnabled: false)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.discoveredScale = BtWifiStoreTestFixtures.makeScale(token: "token-1")
            store.currentCustomizeSetting = .scaleUsername

            store.navigateToStep(.viewSettings)

            await BtWifiStoreTestFixtures.waitUntil {
                bluetooth.getScaleUserListCalls == 1 && store.userList.count == 1
            }

            #expect(store.userList.map(\.name) == ["Other"])
        }

        @Test("view settings username step does not refetch users when the list is already loaded")
        func viewSettingsUsernameStepUsesExistingUserList() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.userList = [
                DeviceUser(name: "Loaded", token: "token-1", lastActive: 1, isBodyMetricsEnabled: false)
            ]
            store.currentCustomizeSetting = .scaleUsername

            store.navigateToStep(.viewSettings)
            await Task.yield()

            #expect(bluetooth.getScaleUserListCalls == 0)
            #expect(store.userList.map(\.name) == ["Loaded"])
        }

        @Test("connecting wifi step only calls setup when no blocking error is present")
        func connectingWifiStepCallsSetupOnlyWhenErrorIsClear() async {
            let bluetooth = MockBluetoothService()
            bluetooth.setupWifiResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = savedScale
            store.networkForm.setSSID("Home WiFi")
            store.networkForm.setPassword("secret")

            store.scaleSetupError = .none
            store.navigateToStep(.connectingWifi)
            await BtWifiStoreTestFixtures.waitUntil {
                bluetooth.setupWifiCalls == 1
            }

            store.navigateToStep(.wifiPassword)
            store.scaleSetupError = .wifiConnectionFailed
            store.navigateToStep(.connectingWifi)
            await Task.yield()

            #expect(bluetooth.setupWifiCalls == 1)
        }

        @Test("setConnectionState blocks network errors during bluetooth pairing unless explicitly allowed")
        func setConnectionStateBlocksNetworkErrorsWhilePairing() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.navigateToStep(.connectingBluetooth)
            store.connectionState = .loading

            store.setConnectionState(.noNetworks, allowNetworkErrors: false)
            #expect(store.connectionState == .loading)

            store.setConnectionState(.failure, allowNetworkErrors: false)
            #expect(store.connectionState == .loading)

            store.setConnectionState(.failure, allowNetworkErrors: true)
            #expect(store.connectionState == .failure)
        }

        @Test("device discovery ignores events outside wakeup and for unsupported setup types")
        func deviceDiscoveryGuardsInvalidEvents() throws {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let scale = BtWifiStoreTestFixtures.makeScale()
            let nonBtWifiScaleInfo = try #require(SCALES.first { $0.setupType != .btWifiR4 })
            let nonBtWifiEvent = DeviceDiscoveryEvent(
                device: scale,
                deviceInfo: nonBtWifiScaleInfo,
                protocolType: .R4,
                isNew: true
            )

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.handleDeviceDiscovery(BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: scale))

            #expect(store.currentStep == .intro)
            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)

            store.navigateToStep(.wakeup)
            store.handleDeviceDiscovery(nonBtWifiEvent)

            #expect(store.currentStep == .wakeup)
            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
        }

        @Test("step on entry starts live measurement and prepares timeout state")
        func stepOnEntryStartsMeasurementFlow() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            defer { HTTPClient.shared.skipCheckNetwork = false }

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = savedScale

            store.navigateToStep(.stepOn)

            await BtWifiStoreTestFixtures.waitUntil {
                bluetooth.startLiveMeasurementCalls == 1 &&
                    store.liveMeasurementSubscription != nil &&
                    store.stepOnTimeoutTask != nil
            }

            #expect(HTTPClient.shared.skipCheckNetwork == true)
            #expect(bluetooth.lastStartLiveMeasurementDevice?.id == savedScale.id)
        }

        @Test("step on live measurement success stops measurement and advances")
        func stepOnLiveMeasurementSuccessAdvancesToMeasurement() async throws {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            defer { HTTPClient.shared.skipCheckNetwork = false }

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = savedScale
            store.navigateToStep(.stepOn)

            await BtWifiStoreTestFixtures.waitUntil {
                store.liveMeasurementSubscription != nil && store.stepOnTimeoutTask != nil
            }

            harness.bluetooth.liveMeasurementSubject.send(
                try makeLiveMeasurementEntry(
                    broadcastId: savedScale.broadcastIdString ?? "",
                    displayWeight: 72.4
                )
            )

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .measurement &&
                    harness.bluetooth.stopLiveMeasurementCalls == 1 &&
                    store.liveMeasurementSubscription == nil &&
                    store.stepOnTimeoutTask == nil
            }

            #expect(store.scaleSetupError == .none)
            #expect(HTTPClient.shared.skipCheckNetwork == false)
        }

        @Test("measurement entry subscribes for entries and advances on first received entry")
        func measurementEntryAdvancesOnNewEntry() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.measurement)

            await BtWifiStoreTestFixtures.waitUntil {
                store.newEntrySubscription != nil && store.measurementTimeoutTask != nil
            }

            harness.bluetooth.newEntryReceivedSubject.send(
                BluetoothScaleSetupStoreTestFixtures.makeEntryNotification()
            )

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .scaleConnected &&
                    store.newEntrySubscription == nil &&
                    store.measurementTimeoutTask == nil
            }

            #expect(store.scaleSetupError == .none)
        }

        @Test("leaving step on cancels the timeout and restores network checks")
        func leavingStepOnCancelsTimeoutAndRestoresNetworkChecks() async {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            defer { HTTPClient.shared.skipCheckNetwork = false }

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale()
            store.navigateToStep(.stepOn)

            await BtWifiStoreTestFixtures.waitUntil {
                store.stepOnTimeoutTask != nil && HTTPClient.shared.skipCheckNetwork == true
            }

            store.navigateToStep(.measurement)
            await BtWifiStoreTestFixtures.waitUntil {
                store.stepOnTimeoutTask == nil
            }

            #expect(HTTPClient.shared.skipCheckNetwork == false)
        }

        @Test("permission loss on wakeup clears discovery context and routes to permissions")
        func permissionLossOnWakeupRoutesToPermissions() {
            let networkMonitor = MockNetworkMonitor(isConnected: true)
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .ENABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(
                permissions: permissions,
                networkMonitor: networkMonitor
            )
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wakeup)
            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            networkMonitor.isConnected = false

            store.handlePermissionChange()

            #expect(store.currentStep == .permissions)
            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
        }

        @Test("permission change during bluetooth pairing suppresses network errors")
        func permissionChangeWhileConnectingBluetoothKeepsLoadingState() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.connectingBluetooth)
            store.connectionState = .noNetworks

            store.handlePermissionChange()

            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.connectionState == .loading)
        }

        @Test("permission changes are ignored once the flow is exiting")
        func permissionChangeWhileExitingDoesNothing() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale()
            store.navigateToStep(.wifiPassword)
            store.isExiting = true

            store.handlePermissionChange()

            #expect(store.currentStep == .wifiPassword)
            #expect(store.scaleSetupError == .none)
        }

        @Test("duplicate recovery ignores permission changes while gathering network")
        func permissionChangeDuringDuplicateRecoveryIsIgnored() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.connectionState = .success

            store.handlePermissionChange()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .duplicatesFound)
            #expect(store.connectionState == .success)
        }

        @Test("gathering network without a saved scale routes back to permissions on network loss")
        func gatheringNetworkWithoutSavedScaleRoutesToPermissions() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.navigateToStep(.gatheringNetwork)

            store.handlePermissionChange()

            #expect(store.currentStep == .permissions)
            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
        }

        @Test("network loss during wifi password routes back to wifi selection recovery")
        func networkLossDuringWifiPasswordRoutesToWifiRecovery() {
            let networkMonitor = MockNetworkMonitor(isConnected: true)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale()
            store.navigateToStep(.wifiPassword)
            networkMonitor.isConnected = false

            store.handlePermissionChange()

            #expect(store.currentStep == .availableWifiList)
            #expect(store.scaleSetupError == .wifiConnectionFailed)
        }

        @Test("successful wifi setup does not navigate backward on permission change")
        func permissionChangeDuringSuccessfulConnectingWifiDoesNotNavigateBack() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale()
            store.scaleSetupError = .wifiConnectionFailed
            store.navigateToStep(.connectingWifi)
            store.connectionState = .success

            store.handlePermissionChange()

            #expect(store.currentStep == .connectingWifi)
            #expect(store.connectionState == .success)
        }

        @Test("wifi permission recovery without a saved scale falls back to permissions")
        func networkLossDuringWifiPasswordWithoutSavedScaleRoutesToPermissions() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: networkMonitor)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.discoveredScale = discoveredScale
            store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)
            store.navigateToStep(.wifiPassword)

            store.handlePermissionChange()

            #expect(store.currentStep == .permissions)
            #expect(store.discoveredScale == nil)
            #expect(store.discoveryEvent == nil)
        }

        @Test("step on permission loss cancels live measurement state and shows alert")
        func permissionChangeOnStepOnCancelsMeasurementSetup() {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .DISABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store

            defer { HTTPClient.shared.skipCheckNetwork = false }

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.stepOn)
            store.liveMeasurementSubscription = PassthroughSubject<Int, Never>().sink { _ in }
            store.measurementTimeoutTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
            store.stepOnTimeoutTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }

            store.handlePermissionChange()

            #expect(store.liveMeasurementSubscription == nil)
            #expect(store.measurementTimeoutTask == nil)
            #expect(store.stepOnTimeoutTask == nil)
            #expect(harness.notification.showAlertCalls == 1)
        }

        @Test("step on ignores permission change when update settings failed is already showing")
        func permissionChangeOnStepOnWithUpdateSettingsFailureDoesNothing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .DISABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.stepOn)
            store.scaleSetupError = .updateSettingsFailed
            store.liveMeasurementSubscription = PassthroughSubject<Int, Never>().sink { _ in }
            store.stepOnTimeoutTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }

            store.handlePermissionChange()

            #expect(store.liveMeasurementSubscription != nil)
            #expect(store.stepOnTimeoutTask != nil)
            #expect(harness.notification.showAlertCalls == 0)
        }

        @Test("measurement permission loss cancels subscriptions but keeps timeout running")
        func permissionChangeOnMeasurementKeepsTimeoutForRecovery() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .DISABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.measurement)
            store.liveMeasurementSubscription = PassthroughSubject<Int, Never>().sink { _ in }

            await BtWifiStoreTestFixtures.waitUntil {
                store.newEntrySubscription != nil && store.measurementTimeoutTask != nil
            }

            store.handlePermissionChange()

            #expect(store.newEntrySubscription == nil)
            #expect(store.liveMeasurementSubscription == nil)
            #expect(store.measurementTimeoutTask != nil)
            #expect(harness.notification.showAlertCalls == 1)
        }

        @Test("update settings step without a saved scale fails immediately")
        func updateSettingsWithoutSavedScaleFailsImmediately() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.updateSettings)

            await BtWifiStoreTestFixtures.waitUntil {
                store.scaleSetupError == .updateSettingsFailed
            }

            #expect(store.scaleSetupError == .updateSettingsFailed)
        }

        @Test("permission change during update settings refreshes and resyncs the saved scale")
        func permissionChangeOnUpdateSettingsRefreshesSavedScale() async {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(
                scaleService: scaleService,
                networkMonitor: networkMonitor
            )
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale(id: "saved-scale")
            let refreshedScale = BtWifiStoreTestFixtures.makeScale(id: "saved-scale", displayName: "Refreshed")

            scaleService.scales = [refreshedScale]
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = savedScale
            store.navigateToStep(BtWifiScaleSetupStep.updateSettings)

            store.handlePermissionChange()

            await BtWifiStoreTestFixtures.waitUntil {
                scaleService.updateAllScalesStatusCalls == 1 &&
                    harness.bluetooth.syncDevicesCalls == 1 &&
                    store.savedScale?.id == refreshedScale.id
            }

            #expect(harness.bluetooth.lastSyncedDevices.map { $0.id } == ["saved-scale"])
        }

        @Test("permission change during update settings stops when status refresh fails")
        func permissionChangeOnUpdateSettingsStopsWhenStatusRefreshFails() async {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let scaleService = MockScaleService()
            scaleService.updateAllScalesStatusError = ScaleTestError.localFailure
            let harness = BtWifiStoreTestFixtures.makeSUT(
                scaleService: scaleService,
                networkMonitor: networkMonitor
            )
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale(id: "saved-scale")
            store.navigateToStep(BtWifiScaleSetupStep.updateSettings)

            store.handlePermissionChange()
            await Task.yield()

            #expect(scaleService.updateAllScalesStatusCalls == 1)
            #expect(harness.bluetooth.syncDevicesCalls == 0)
        }

        @Test("permission change during update settings does nothing when bluetooth is switched off")
        func permissionChangeOnUpdateSettingsWithBluetoothOffSkipsRefresh() {
            let networkMonitor = MockNetworkMonitor(isConnected: false)
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .DISABLED
            ])
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(
                permissions: permissions,
                scaleService: scaleService,
                networkMonitor: networkMonitor
            )
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScale(id: "saved-scale")
            store.navigateToStep(.updateSettings)

            store.handlePermissionChange()

            #expect(scaleService.updateAllScalesStatusCalls == 0)
            #expect(harness.bluetooth.syncDevicesCalls == 0)
            #expect(store.savedScale?.id == "saved-scale")
        }

        private func makeLiveMeasurementEntry(
            broadcastId: String,
            displayWeight: Float
        ) throws -> GGWeightEntry {
            let payload = """
            {
              "broadcastId": "\(broadcastId)",
              "broadcastIdString": "\(broadcastId)",
              "protocolType": "R4",
              "date": 1,
              "displayWeight": \(displayWeight),
              "unit": "kg"
            }
            """.data(using: .utf8)!

            return try JSONDecoder().decode(GGWeightEntry.self, from: payload)
        }
    }
}
