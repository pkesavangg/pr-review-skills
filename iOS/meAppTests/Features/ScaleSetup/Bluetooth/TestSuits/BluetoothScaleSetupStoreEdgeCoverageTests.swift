import Foundation
@testable import meApp
import Testing

private enum BluetoothScaleSetupStoreEdgeError: Error {
    case forced
}

extension BluetoothScaleSetupStoreTests {
    @Suite("Edge Coverage")
    @MainActor
    struct EdgeCoverage {
        @Test("step views, back-disabled states, and help modal are covered")
        func stepViewsBackDisabledAndHelpModal() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            #expect(store.stepViews.count == store.steps.count)
            #expect(store.isBackDisabled == true) // intro

            store.currentStepIndex = BluetoothScaleSetupStep.permissions.index
            #expect(store.isBackDisabled == false)

            store.currentStepIndex = BluetoothScaleSetupStep.stepOn.index
            store.isEntrySynced = false
            #expect(store.isBackDisabled == false)
            store.isEntrySynced = true
            #expect(store.isBackDisabled == true)

            store.testSetInternalState(discoveredScale: BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale())
            store.currentStepIndex = BluetoothScaleSetupStep.setUser.index
            #expect(store.isBackDisabled == true)

            store.currentStepIndex = BluetoothScaleSetupStep.setupFinished.index
            #expect(store.isBackDisabled == true)

            store.showHelpModal()
            #expect(harness.notification.showModalCalls == 1)
        }

        @Test("permissions next without saved scale routes to select user")
        func permissionsNextWithoutSavedScaleRoutesToSelectUser() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = BluetoothScaleSetupStep.permissions.index

            // permissions → completeProfile (always presented, MOB-1388) → selectUser once
            // the Complete Profile step is passed, since no scale has been saved yet.
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)
            store.moveToNextStep()

            #expect(store.currentStep == .selectUser)
        }

        @Test("back from set user returns to connecting bluetooth")
        func backFromSetUserReturnsToConnectingBluetooth() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.currentStepIndex = BluetoothScaleSetupStep.setUser.index

            store.moveToPreviousStep()

            #expect(store.currentStep == .connectingBluetooth)
        }

        @Test("handleExit primary action dismisses")
        func handleExitPrimaryActionDismisses() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.handleExit()
            harness.notification.alertData?.buttons.first?.action(nil)

            #expect(dismissCalls == 1)
        }

        @Test("confirmPair missing scale context sets failure")
        func confirmPairMissingScaleContextSetsFailure() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.testSetInternalState(discoveredScale: nil, discoveryEvent: nil)
            await store.testConfirmPair()

            #expect(store.bluetoothConnectionState == .failure)
        }

        @Test("confirmPair without selected user fails before pairing")
        func confirmPairWithoutSelectedUserFailsBeforePairing() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event)
            store.selectedUserNumber = nil

            await store.testConfirmPair()

            #expect(store.bluetoothConnectionState == .failure)
            #expect(harness.bluetooth.confirmSmartPairCalls == 0)
        }

        @Test("confirmPair device info failure sets failure")
        func confirmPairDeviceInfoFailureSetsFailure() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .failure(.notImplemented)
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event)
            store.selectedUserNumber = 1

            await store.testConfirmPair()

            #expect(store.bluetoothConnectionState == .failure)
        }

        @Test("confirmPair unexpected creation response sets failure")
        func confirmPairUnexpectedResponseSetsFailure() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationFailed)
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event)
            store.selectedUserNumber = 1

            await store.testConfirmPair()

            #expect(store.bluetoothConnectionState == .failure)
        }

        @Test("duplicate alert pair button path saves and starts sync")
        func duplicateAlertPairButtonPathSavesAndStartsSync() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(
                BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo(serialNumber: "dup-pair-button")
            )
            let scaleService = MockScaleService()
            let existingScale = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale(id: "old-scale")
            existingScale.peripheralIdentifier = "dup-pair-button"
            scaleService.scales = [existingScale.toSnapshot()]

            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.selectedUserNumber = 3
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls == 1
            }
            harness.notification.alertData?.buttons.last?.action(nil)

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.scaleService.createBluetoothScaleCalls == 1 &&
                store.bluetoothConnectionState == .success
            }

            #expect(harness.scaleService.createBluetoothScaleCalls == 1)
        }

        @Test("duplicate return guard without context dismisses")
        func duplicateReturnGuardWithoutContextDismisses() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.testSetInternalState(discoveredScale: nil, discoveryEvent: nil, scaleToDelete: nil)

            await store.testHandleDuplicateScaleReturn()

            #expect(dismissCalls == 1)
        }

        @Test("sync guards without discovered scale return early")
        func syncGuardsWithoutDiscoveredScaleReturnEarly() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.testSetInternalState(discoveredScale: nil, discoveryEvent: nil)

            await store.testSyncNewScaleAndListenForEntries()
            await store.testSyncNewScale()

            #expect(harness.bluetooth.confirmSmartPairCalls == 0)
        }

        @Test("saveDiscoveredScale wrapper returns when context missing")
        func saveDiscoveredScaleWrapperReturnsWhenContextMissing() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.testSetInternalState(discoveredScale: nil, discoveryEvent: nil, isScaleSaved: false)

            store.testSaveDiscoveredScale()
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showLoaderCalls == 0
            }

            #expect(harness.notification.showLoaderCalls == 0)
        }

        @Test("saveDiscoveredScale returns early when already saved")
        func saveDiscoveredScaleReturnsEarlyWhenAlreadySaved() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event, isScaleSaved: true)

            store.testSaveDiscoveredScale()
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showLoaderCalls == 0
            }

            #expect(harness.notification.showLoaderCalls == 0)
        }

        @Test("saveDiscoveredScale handles duplicate deletion error")
        func saveDiscoveredScaleHandlesDuplicateDeletionError() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo())
            let scaleService = MockScaleService()
            scaleService.deleteDeviceError = BluetoothScaleSetupStoreEdgeError.forced
            let old = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale(id: "old-dup")
            scaleService.scales = [old.toSnapshot()]
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store

            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale(id: "new-scale")
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(
                discoveredScale: discovered,
                discoveryEvent: event,
                isScaleSaved: false,
                scaleToDelete: old.toSnapshot()
            )
            store.selectedUserNumber = 1

            store.testSaveDiscoveredScale()
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.scaleService.createBluetoothScaleCalls == 1)
        }

        @Test("saveDiscoveredScale exits when no active account")
        func saveDiscoveredScaleExitsWhenNoActiveAccount() async {
            let account = MockAccountService()
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo())
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth, account: account)
            let store = harness.store
            // makeSUT seeds a default active account; clear it explicitly for this edge case.
            harness.account.activeAccount = nil
            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event, isScaleSaved: false)

            store.testSaveDiscoveredScale()
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showLoaderCalls == 1
            }

            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }

        @Test("saveDiscoveredScale create failure clears setup flag")
        func saveDiscoveredScaleCreateFailureClearsSetupFlag() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo())
            bluetooth.isSetupInProgress = true
            let scaleService = MockScaleService()
            scaleService.createDeviceError = BluetoothScaleSetupStoreEdgeError.forced
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event, isScaleSaved: false)

            store.testSaveDiscoveredScale()
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.dismissLoaderCalls == 1
            }

            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        @Test("permission restore sets connection success when scale already saved")
        func permissionRestoreSetsConnectionSuccessWhenScaleSaved() async {
            let permissions = MockPermissionsService()
            permissions.handlePermissionResults[.bluetooth] = .ENABLED
            permissions.handlePermissionResults[.bluetoothSwitch] = .ENABLED
            permissions.setPermissions(BluetoothScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.currentStepIndex = BluetoothScaleSetupStep.permissions.index
            store.bluetoothConnectionState = .loading

            let discovered = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale()
            let event = BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: discovered)
            store.testSetInternalState(discoveredScale: discovered, discoveryEvent: event, isScaleSaved: true)

            permissions.setPermissions(BluetoothScaleSetupStoreTestFixtures.enabledPermissions())
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                store.bluetoothConnectionState == .success
            }

            #expect(store.bluetoothConnectionState == .success)
        }

        @Test("cleanup resets setup flags and resources")
        func cleanupResetsSetupFlagsAndResources() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            harness.bluetooth.isSetupInProgress = true

            store.cleanUp()

            #expect(harness.bluetooth.isSetupInProgress == false)
        }
    }
}
