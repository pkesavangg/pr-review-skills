import Foundation
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("Navigation")
    @MainActor
    struct Navigation {
        @Test("next button text reflects the current navigation state")
        func nextButtonTextReflectsCurrentNavigationState() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            #expect(store.nextButtonText == CommonStrings.next)

            store.scaleSetupError = .duplicatesFound
            store.navigateToStep(.gatheringNetwork)
            #expect(store.nextButtonText == CommonStrings.save)

            store.navigateToStep(.availableWifiList)
            store.handleNetworkSelection(WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -42))
            #expect(store.nextButtonText == CommonStrings.connect)

            store.navigateToStep(.viewSettings)
            #expect(store.nextButtonText == CommonStrings.save)

            store.navigateToStep(.measurement)
            #expect(store.nextButtonText == CommonStrings.next)

            store.scaleSetupError = .collectMeasurementFailed
            #expect(store.nextButtonText == CommonStrings.tryAgain)
            store.cancelMeasurementSubscription()

            store.navigateToStep(.scaleConnected)
            #expect(store.nextButtonText == CommonStrings.tryAgain)

            store.scaleSetupError = .none
            #expect(store.nextButtonText == CommonStrings.finish)
        }

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

        @Test("moveToNextStep respects permission skipping and exiting guards")
        func moveToNextStepRespectsPermissionSkippingAndExitingGuards() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.moveToNextStep()
            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .wakeup && harness.bluetooth.scanForPairingCalls == 1
            }

            #expect(store.currentStep == .wakeup)

            store.isExiting = true
            store.moveToNextStep()
            store.moveToNextStep()

            #expect(store.currentStep == .wakeup)
            #expect(harness.bluetooth.scanForPairingCalls == 1)
        }

        @Test("moveToNextStep stops on permissions when requirements are missing and dismisses at the end")
        func moveToNextStepStopsOnPermissionsAndDismissesAtEnd() {
            let harness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: MockNetworkMonitor(isConnected: false))
            let store = harness.store
            var dismissCalls = 0

            store.dismissAction = { dismissCalls += 1 }
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            store.moveToNextStep()
            #expect(store.currentStep == .permissions)

            store.isExiting = false
            store.navigateToStep(.scaleConnected)
            store.moveToNextStep()

            #expect(dismissCalls == 1)
            #expect(store.currentStep == .scaleConnected)
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

        @Test("direct discovery configuration chooses connecting bluetooth or permissions based on prerequisites")
        func directDiscoveryConfigurationUsesExpectedEntryStep() {
            let readyHarness = BtWifiStoreTestFixtures.makeSUT()
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            readyHarness.store.configure(
                with: SettingsConstants.defaultR4Sku,
                discoveredScale: discoveredScale,
                discoveryEvent: discoveryEvent,
                isWifiSetupOnly: false
            )

            #expect(readyHarness.store.currentStep == .connectingBluetooth)
            #expect(readyHarness.store.discoveryEvent == discoveryEvent)
            #expect(readyHarness.bluetooth.isSetupInProgress == true)

            let blockedHarness = BtWifiStoreTestFixtures.makeSUT(networkMonitor: MockNetworkMonitor(isConnected: false))
            blockedHarness.store.configure(
                with: SettingsConstants.defaultR4Sku,
                discoveredScale: discoveredScale,
                discoveryEvent: discoveryEvent,
                isWifiSetupOnly: false
            )

            #expect(blockedHarness.store.currentStep == .permissions)
            #expect(blockedHarness.store.discoveryEvent == discoveryEvent)
        }

        @Test("repeated configure transitions keep navigation mode consistent")
        func repeatedConfigureTransitionsRemainConsistent() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale(id: "saved-scale", token: "saved-token")
            let discoveredScale = BtWifiStoreTestFixtures.makeScale(id: "discovered-scale")
            let discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent(scale: discoveredScale)

            store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: savedScale,
                isWifiSetupOnly: true
            )
            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.isWifiSetupOnlyMode == true)

            store.configure(
                with: SettingsConstants.defaultR4Sku,
                discoveredScale: discoveredScale,
                discoveryEvent: discoveryEvent,
                isReconnect: true,
                isDuplicated: true,
                isWifiSetupOnly: false
            )

            #expect(store.currentStep == .connectingBluetooth)
            #expect(store.isWifiSetupOnlyMode == false)
            #expect(store.scaleSetupError == .none)
            #expect(store.discoveryEvent == discoveryEvent)
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

        @Test("moveToPreviousStep respects permission skipping, intro guard, settings exit, and exiting state")
        func moveToPreviousStepRespectsRulesAndStability() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wakeup)
            store.moveToPreviousStep()
            #expect(store.currentStep == .intro)

            store.moveToPreviousStep()
            store.moveToPreviousStep()
            #expect(store.currentStep == .intro)

            store.navigateToStep(.wakeup)
            let wakeupIndex = store.currentStepIndex
            store.isExiting = true
            store.moveToPreviousStep()
            #expect(store.currentStepIndex == wakeupIndex)
            store.isExiting = false

            let settingsHarness = BtWifiStoreTestFixtures.makeSUT()
            let settingsStore = settingsHarness.store
            settingsStore.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: BtWifiStoreTestFixtures.makeScale(),
                isWifiSetupOnly: true
            )
            settingsStore.navigateToStep(.availableWifiList)

            settingsStore.moveToPreviousStep()

            #expect(settingsStore.currentStep == .availableWifiList)
            #expect(settingsHarness.notification.showAlertCalls == 1)
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

        @Test("performExitCleanup from gathering network resets navigation state and resumes dependencies")
        func performExitCleanupFromGatheringNetworkResetsState() async {
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store
            let discoveredScale = BtWifiStoreTestFixtures.makeScale()
            var dismissCalls = 0

            store.dismissAction = { dismissCalls += 1 }
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.discoveredScale = discoveredScale
            store.scaleSetupError = .duplicatesFound
            store.connectionState = .failure
            store.fetchWifiNetworksTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
            harness.bluetooth.isSetupInProgress = true
            store.navigateToStep(.gatheringNetwork)

            store.performExitCleanup()

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_500_000_000) {
                dismissCalls == 1 &&
                    harness.bluetooth.resumeSmartScanCalls == 1 &&
                    harness.bluetoothSetupManager.disconnectIfNeededCalls == 1 &&
                    harness.bluetoothSetupManager.cancelWifiCalls == 1 &&
                    scaleService.updateAllScalesStatusCalls == 1 &&
                    harness.bluetooth.syncDevicesCalls == 1 &&
                    store.dismissAction == nil &&
                    store.isExiting == false
            }

            #expect(store.scaleSetupError == .none)
            #expect(store.connectionState == .success)
            #expect(store.fetchWifiNetworksTask == nil)
            #expect(harness.bluetoothSetupManager.lastDisconnectConsiderForSession == true)
            #expect(harness.bluetoothSetupManager.lastCancelledScale?.id == discoveredScale.id)
            #expect(harness.bluetooth.lastResumeClearOnlyPairing == false)
        }

        @Test("confirmExit handles scale connected, cancel, and confirm paths")
        func confirmExitHandlesAllPaths() async throws {
            let scaleConnectedHarness = BtWifiStoreTestFixtures.makeSUT()
            let scaleConnectedStore = scaleConnectedHarness.store
            var dismissCalls = 0

            scaleConnectedStore.dismissAction = { dismissCalls += 1 }
            scaleConnectedStore.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            scaleConnectedStore.discoveredScale = BtWifiStoreTestFixtures.makeScale()
            scaleConnectedStore.navigateToStep(.scaleConnected)

            let scaleConnectedResult = await scaleConnectedStore.confirmExit()
            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_500_000_000) {
                dismissCalls == 1 && scaleConnectedStore.dismissAction == nil
            }

            #expect(scaleConnectedResult == true)
            #expect(scaleConnectedHarness.notification.showAlertCalls == 0)

            let cancelHarness = BtWifiStoreTestFixtures.makeSUT()
            cancelHarness.store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            cancelHarness.store.navigateToStep(.wakeup)

            let cancelTask = Task { await cancelHarness.store.confirmExit() }
            await Task.yield()

            let cancelAlert = try #require(cancelHarness.notification.alertData)
            cancelAlert.buttons[1].action(nil)

            let cancelResult = await cancelTask.value
            #expect(cancelResult == false)
            #expect(cancelHarness.notification.alertData == nil)

            let confirmHarness = BtWifiStoreTestFixtures.makeSUT()
            let confirmStore = confirmHarness.store
            var confirmDismissCalls = 0

            confirmStore.dismissAction = { confirmDismissCalls += 1 }
            confirmStore.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            confirmStore.discoveredScale = BtWifiStoreTestFixtures.makeScale()
            confirmStore.navigateToStep(.wakeup)

            let confirmTask = Task { await confirmStore.confirmExit() }
            await Task.yield()

            let confirmAlert = try #require(confirmHarness.notification.alertData)
            confirmAlert.buttons[0].action(nil)

            let confirmResult = await confirmTask.value
            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_500_000_000) {
                confirmDismissCalls == 1 && confirmStore.dismissAction == nil
            }

            #expect(confirmResult == true)
            #expect(confirmHarness.bluetooth.resumeSmartScanCalls == 1)
        }

        @Test("showHelpModal and bluetooth turned off alert dispatch expected notification interactions")
        func helpModalAndBluetoothAlertDispatchExpectedInteractions() async throws {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .DISABLED,
                .BLUETOOTH_SWITCH: .ENABLED
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.showHelpModal()

            #expect(harness.notification.showModalCalls == 1)
            #expect(harness.notification.modalViewData.count == 1)

            harness.notification.modalViewData.last?.onDismiss?()
            #expect(harness.notification.dismissModalCalls == 1)
            #expect(harness.notification.modalViewData.isEmpty)

            store.showBluetoothTurnedOffAlert()
            let cancelAlert = try #require(harness.notification.alertData)
            cancelAlert.buttons[0].action(nil)
            #expect(permissions.handlePermissionCalls.isEmpty)

            store.showBluetoothTurnedOffAlert()
            let requestAlert = try #require(harness.notification.alertData)
            requestAlert.buttons[1].action(nil)

            await BtWifiStoreTestFixtures.waitUntil {
                permissions.handlePermissionCalls == [.bluetooth]
            }

            #expect(permissions.handlePermissionCalls == [.bluetooth])
        }

        @Test("footer and back button state follow navigation rules")
        func footerAndBackButtonStateFollowNavigationRules() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            #expect(store.shouldShowFooter() == true)
            #expect(store.shouldDisableBackButton() == true)

            store.scaleSetupError = .duplicatesFound
            store.navigateToStep(.gatheringNetwork)
            #expect(store.shouldShowFooter() == true)
            #expect(store.shouldDisableBackButton() == true)

            store.scaleSetupError = .none
            #expect(store.shouldShowFooter() == false)
            #expect(store.shouldDisableBackButton() == false)

            store.navigateToStep(.viewSettings)
            #expect(store.shouldShowFooter() == true)
            #expect(store.shouldDisableBackButton() == false)

            store.navigateToStep(.customizeSettings)
            #expect(store.shouldShowFooter() == true)
            #expect(store.shouldDisableBackButton() == true)

            store.navigateToStep(.availableWifiList)
            #expect(store.shouldDisableBackButton() == true)
        }
    }
}
