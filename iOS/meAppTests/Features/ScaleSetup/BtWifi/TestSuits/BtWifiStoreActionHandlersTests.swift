import Combine
import Foundation
import Testing
@testable import meApp

extension BtWifiStoreTests {
    @Suite("Action Handlers")
    @MainActor
    struct ActionHandlers {
        @Test("restore account action presents confirmation and cancel leaves state unchanged")
        func restoreAccountActionCancelLeavesStateUnchanged() throws {
            let bluetooth = MockBluetoothService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            store.discoveredScale = BtWifiStoreTestFixtures.makeScale()
            store.currentUser = DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: true)

            store.handleRestoreAccount()

            let alert = try #require(harness.notification.alertData)
            #expect(harness.notification.showAlertCalls == 1)
            #expect(alert.buttons.count == 2)

            alert.buttons[0].action(nil)

            #expect(harness.notification.alertData == nil)
            #expect(bluetooth.getScaleUserListCalls == 0)
            #expect(bluetooth.deleteUserByTokenCalls == 0)
            #expect(store.scaleSetupError == .none)
        }

        @Test("restore account action recovers with duplicate error when downstream lookup fails")
        func restoreAccountActionFailureRestoresDuplicateRecovery() async throws {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Other", token: "other-1", lastActive: 1, isBodyMetricsEnabled: false)
            ])
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store

            store.discoveredScale = BtWifiStoreTestFixtures.makeScale()
            store.currentUser = DeviceUser(name: "Lakshmi", token: "dup-1", lastActive: 4, isBodyMetricsEnabled: true)

            store.handleRestoreAccount()
            let alert = try #require(harness.notification.alertData)
            alert.buttons[1].action(nil)

            await BtWifiStoreTestFixtures.waitUntil {
                store.scaleSetupError == .duplicatesFound
            }

            #expect(bluetooth.getScaleUserListCalls == 1)
            #expect(bluetooth.deleteUserByTokenCalls == 0)
            #expect(store.currentStep != .connectingBluetooth)
        }

        @Test("delete user action deletes and recovers to bluetooth connection even when deletion fails")
        func deleteUserActionFailureStillNavigatesToBluetoothRecovery() async throws {
            let bluetooth = MockBluetoothService()
            bluetooth.deleteUserByTokenResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let scale = BtWifiStoreTestFixtures.makeScale()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.discoveredScale = scale
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .maxUserReached

            store.handleDeleteUser(DeviceUser(name: "Existing", token: "dup-1", lastActive: 2, isBodyMetricsEnabled: false))
            let alert = try #require(harness.notification.alertData)
            alert.buttons[1].action(nil)

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
                store.currentStep == .connectingBluetooth && store.scaleSetupError == .none
            }

            #expect(bluetooth.deleteUserByTokenCalls == 1)
            #expect(bluetooth.lastDeleteUserBroadcastId == scale.broadcastIdString)
            #expect(bluetooth.lastDeleteUserToken == "dup-1")
        }

        @Test("skip wifi action cancel is a no-op and confirm cancels wifi then routes to customization")
        func skipWifiActionCancelAndConfirmBehaveCorrectly() async throws {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()

            store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: savedScale,
                isWifiSetupOnly: true
            )
            store.scaleSetupError = .noNetworkFound

            store.handleSkipWifiStep()
            let cancelAlert = try #require(harness.notification.alertData)
            cancelAlert.buttons[0].action(nil)

            #expect(harness.bluetoothSetupManager.cancelWifiCalls == 0)
            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .noNetworkFound)

            store.handleSkipWifiStep()
            let confirmAlert = try #require(harness.notification.alertData)
            confirmAlert.buttons[1].action(nil)
            #expect(store.scaleSetupError == .none)

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 800_000_000) {
                store.currentStep == .customizeSettings && harness.bluetoothSetupManager.cancelWifiCalls == 1
            }

            #expect(harness.bluetoothSetupManager.lastCancelledBroadcastId == savedScale.broadcastIdString)
        }

        @Test("view settings action dispatches repeated scale metric saves without duplicating selection state")
        func viewSettingsActionRepeatedSaveRemainsStable() async {
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            scaleService.fetchAttachedPreferenceResult = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved"),
                scaleId: scale.id
            )
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store
            let metricsKey = CustomizeSettingsItem.scaleMetrics.rawValue

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.navigateToStep(.customizeSettings)
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMetrics
            store.selectedDeviceMetrics = ["weight"]

            store.handleViewSettingsAction()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none &&
                    store.currentStep == .customizeSettings &&
                    store.savedDeviceMetricsSnapshot == ["weight"]
            }

            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMetrics
            store.selectedDeviceMetrics = ["weight", "heartRate"]

            store.handleViewSettingsAction()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none &&
                    store.currentStep == .customizeSettings &&
                    store.savedDeviceMetricsSnapshot == ["weight", "heartRate"]
            }

            #expect(store.selectedCustomizeItems == [metricsKey])
            #expect(store.hasSavedSettings == true)
        }

        @Test("settings wifi back action uses the special exit flow and confirm keeps the flow recoverable")
        func settingsWifiBackUsesSpecialExitFlow() async throws {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScale()
            var dismissCalls = 0

            store.dismissAction = { dismissCalls += 1 }
            store.configure(
                with: SettingsConstants.defaultR4Sku,
                saveScale: savedScale,
                isWifiSetupOnly: true
            )
            store.navigateToStep(.availableWifiList)
            store.connectionState = .failure
            store.scaleSetupError = .noNetworkFound

            store.handleBackButtonClick()

            #expect(store.currentStep == .availableWifiList)
            #expect(harness.notification.showAlertCalls == 1)

            let alert = try #require(harness.notification.alertData)
            alert.buttons[0].action(nil)
            #expect(store.scaleSetupError == .none)

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
                dismissCalls == 1 &&
                    harness.bluetoothSetupManager.cancelWifiCalls == 1 &&
                    store.connectionState == .success
            }

            #expect(harness.bluetoothSetupManager.lastCancelledBroadcastId == savedScale.broadcastIdString)
        }

        @Test("exit alert message changes for wifi-only, post-connection, and pre-connection contexts")
        func presentExitAlertUsesContextSpecificMessages() throws {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.isWifiSetupOnly = true
            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot(id: "wifi-only")
            store.presentExitAlert(onConfirm: {})
            let wifiOnlyAlert = try #require(harness.notification.alertData)
            #expect(wifiOnlyAlert.message == AlertStrings.ExitBtWifiSetupAlert.wifiExitMessage)

            store.isWifiSetupOnly = false
            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot(id: "saved")
            store.presentExitAlert(onConfirm: {})
            let postConnectionAlert = try #require(harness.notification.alertData)
            #expect(postConnectionAlert.message == AlertStrings.ExitBtWifiSetupAlert.postConnectionExitMessage)

            store.savedScale = nil
            store.presentExitAlert(onConfirm: {})
            let preConnectionAlert = try #require(harness.notification.alertData)
            #expect(preConnectionAlert.message == AlertStrings.ExitBtWifiSetupAlert.preConnectionExitMessage)
        }

        @Test("regular exit presents a confirmation alert and cancel resets exiting state")
        func handleExitCancelLeavesFlowActive() async throws {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.wakeup)
            store.fetchWifiNetworksTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
            store.stepTimerTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }

            store.handleExit()

            #expect(store.isExiting == true)
            #expect(store.fetchWifiNetworksTask == nil)
            #expect(store.stepTimerTask == nil)

            let alert = try #require(harness.notification.alertData)
            alert.buttons[1].action(nil)
            await Task.yield()

            #expect(store.isExiting == false)
            #expect(harness.notification.alertData == nil)
        }

        @Test("step-on exit stops measurement and dismisses even when the bluetooth stop operation fails")
        func handleExitFromStepOnStopsMeasurementAndDismisses() async {
            let bluetooth = MockBluetoothService()
            bluetooth.stopLiveMeasurementResult = .failure(.notImplemented)
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            var dismissCalls = 0

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            store.navigateToStep(.stepOn)
            store.liveMeasurementSubscription = PassthroughSubject<Int, Never>().sink { _ in }
            store.measurementTimeoutTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
            harness.bluetooth.isSetupInProgress = true
            store.dismissAction = { dismissCalls += 1 }

            store.handleExit()

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
                dismissCalls == 1 &&
                    harness.bluetooth.stopLiveMeasurementCalls == 1 &&
                    harness.bluetooth.isSetupInProgress == false
            }

            #expect(store.liveMeasurementSubscription == nil)
            #expect(store.measurementTimeoutTask == nil)
            #expect(store.scaleSetupError == .none)
            #expect(store.isExiting == false)
            #expect(store.isExitingFromStepOn == false)
        }

        @Test("next button guards invalid duplicate save requests and uses the connected-network shortcut")
        func nextButtonGuardsInvalidDuplicateSaveAndHandlesConnectedNetworkShortcut() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.gatheringNetwork)
            store.scaleSetupError = .duplicatesFound
            store.userNameForm.setDisplayName("")

            store.handleNextButtonClick()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .duplicatesFound)
            #expect(store.duplicateUserName.isEmpty)

            store.navigateToStep(.availableWifiList)
            store.savedScale = savedScale
            store.scaleSetupError = .none
            store.connectedWifiNetwork = WifiDetails(macAddress: "AA", ssid: "Home WiFi", rssi: -40)
            store.handleNextButtonClick()

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 800_000_000) {
                store.currentStep == .customizeSettings && harness.bluetoothSetupManager.cancelWifiCalls == 1
            }

            #expect(store.scaleSetupError == .none)
            #expect(harness.bluetoothSetupManager.lastCancelledBroadcastId == savedScale.broadcastIdString)
        }

        @Test("next button routes customization to update or step-on and finishing clears setup state")
        func nextButtonRoutesCustomizationAndFinishesFlow() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            var dismissCalls = 0
            var dashboardUpdateNotifications = 0

            let observer = NotificationCenter.default.addObserver(
                forName: .dashboardMetricsUpdated,
                object: nil,
                queue: nil
            ) { _ in
                dashboardUpdateNotifications += 1
            }
            defer { NotificationCenter.default.removeObserver(observer) }

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.customizeSettings)

            store.hasSavedSettings = false
            store.handleNextButtonClick()
            #expect(store.currentStep == .stepOn)

            store.navigateToStep(.customizeSettings)
            store.hasSavedSettings = true
            store.handleNextButtonClick()
            #expect(store.currentStep == .updateSettings)

            harness.bluetooth.isSetupInProgress = true
            store.dismissAction = { dismissCalls += 1 }
            store.navigateToStep(.scaleConnected)
            store.handleNextButtonClick()

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_200_000_000) {
                dismissCalls == 1 && harness.bluetooth.isSetupInProgress == false
            }

            #expect(dashboardUpdateNotifications == 1)
        }

        @Test("repeated try again from wifi failure remains stable and exiting blocks retries")
        func repeatedTryAgainRemainsStableAndExitGuardWins() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.availableWifiList)
            store.scaleSetupError = .wifiConnectionFailed
            store.connectionState = .failure
            store.fetchWifiNetworksTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }

            store.tryAgainButtonHandler()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .none)
            #expect(store.connectionState == .loading)
            #expect(store.fetchWifiNetworksTask == nil)

            store.tryAgainButtonHandler()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .none)
            #expect(store.connectionState == .loading)

            store.isExiting = true
            store.scaleSetupError = .wifiConnectionFailed
            store.connectionState = .failure

            store.tryAgainButtonHandler()

            #expect(store.currentStep == .gatheringNetwork)
            #expect(store.scaleSetupError == .wifiConnectionFailed)
            #expect(store.connectionState == .failure)
        }
    }
}
