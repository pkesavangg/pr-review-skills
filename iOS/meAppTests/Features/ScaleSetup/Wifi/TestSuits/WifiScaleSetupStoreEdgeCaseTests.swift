import Foundation
import Testing
import UIKit
@testable import meApp

private enum WifiScaleSetupStoreEdgeTestError: Error {
    case failure
}

extension WifiScaleSetupStoreTests {
    @Suite("Edge Cases")
    @MainActor
    struct EdgeCases {
        @Test("moveToNextStep from setup finish exits the flow")
        func moveToNextStepFromSetupFinishExitsFlow() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("moveToPreviousStep goes to permissions when permissions are missing")
        func moveToPreviousStepGoesToPermissionsWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(WifiScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index

            store.moveToPreviousStep()

            #expect(store.currentStep == .permissions)
        }

        @Test("next from intro clears get-mac flag and advances")
        func nextFromIntroClearsGetMacFlagAndAdvances() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.isForGetMac = true

            store.handleNextButtonClick()

            #expect(store.isForGetMac == false)
            #expect(store.currentStep == .wifiPassword)
        }

        @Test("next from permissions in get-mac flow goes to activate pairing mode")
        func nextFromPermissionsInGetMacFlowGoesToActivatePairingMode() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.isForGetMac = true
            store.currentStepIndex = WifiScaleSetupStep.permissions.index
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }

            store.handleNextButtonClick()

            #expect(store.currentStep == .activatePairingMode)
        }

        @Test("next from step-on advances to setup finish")
        func nextFromStepOnAdvancesToSetupFinish() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.stepOn.index

            store.handleNextButtonClick()

            #expect(store.currentStep == .setupFinish)
        }

        @Test("back from step-on without source falls back to previous step")
        func backFromStepOnWithoutSourceFallsBackToPreviousStep() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.stepOn.index

            store.handleBackButtonClick()

            #expect(store.currentStep == .copyMacAddress)
        }

        @Test("ap-mode get-mac flow captures bssid and navigates to copy-mac step")
        func apModeGetMacFlowCapturesMacAndNavigatesToCopyMac() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.isForGetMac = true
            store.currentStepIndex = WifiScaleSetupStep.apMode.index

            store.handleNextButtonClick()
            await WifiScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 6_000_000_000) {
                store.currentStep == .copyMacAddress
            }

            #expect(store.currentStep == .copyMacAddress)
            #expect(store.retrievedMacAddress == "AA:BB")
            #expect(harness.notification.showLoaderCalls == 1)
            #expect(harness.notification.dismissLoaderCalls == 1)
        }

        @Test("foreground event requests location switch permission when switch is disabled")
        func foregroundEventRequestsLocationSwitchPermissionWhenDisabled() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .LOCATION: .ENABLED,
                .LOCATION_SWITCH: .DISABLED,
                .WIFI_SWITCH: .ENABLED
            ])
            permissions.handlePermissionResults[.locationSwitch] = .ENABLED
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index

            NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
            await WifiScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_500_000_000) {
                harness.permissions.handlePermissionCalls.contains(.locationSwitch)
            }

            #expect(harness.permissions.handlePermissionCalls.contains(.locationSwitch))
        }

        @Test("foreground event requests location permission when switch is enabled but location is denied")
        func foregroundEventRequestsLocationPermissionWhenDenied() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .LOCATION: .DISABLED,
                .LOCATION_SWITCH: .ENABLED,
                .WIFI_SWITCH: .ENABLED
            ])
            permissions.handlePermissionResults[.location] = .ENABLED
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index

            NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
            await WifiScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 1_500_000_000) {
                harness.permissions.handlePermissionCalls.contains(.location)
            }

            #expect(harness.permissions.handlePermissionCalls.contains(.location))
        }

        @Test("setup finish with missing active account dismisses loader and skips create")
        func setupFinishWithMissingActiveAccountSkipsCreate() async {
            let accountService = MockAccountService()
            let activeAccount = WifiScaleSetupStoreTestFixtures.makeAccount()
            accountService.seedAccounts([activeAccount], active: activeAccount)
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(accountService: accountService)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.selectedUserNumber = 2
            accountService.activeAccount = nil
            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index

            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.handleNextButtonClick()
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                dismissCalls == 1
            }

            #expect(harness.notification.showLoaderCalls == 1)
            #expect(harness.notification.dismissLoaderCalls == 1)
            #expect(harness.scaleService.createDeviceCalls == 0)
        }

        @Test("setup finish create-device failure shows toast and clears setup-in-progress")
        func setupFinishCreateDeviceFailureShowsToastAndClearsSetupFlag() async {
            let scaleService = MockScaleService()
            scaleService.createDeviceError = WifiScaleSetupStoreEdgeTestError.failure
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.selectedUserNumber = 2
            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index

            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.handleNextButtonClick()
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                dismissCalls == 1 && harness.notification.showToastCalls == 1
            }

            #expect(harness.scaleService.createDeviceCalls == 1)
            #expect(harness.pushNotifications.setupPushNotificationsCalls == 0)
            #expect(harness.notification.showToastCalls == 1)
            #expect(harness.bluetoothService.isSetupInProgress == false)
        }

        @Test("cleanUp clears setup-in-progress flag")
        func cleanUpClearsSetupInProgressFlag() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            #expect(harness.bluetoothService.isSetupInProgress == true)

            store.cleanUp()

            #expect(harness.bluetoothService.isSetupInProgress == false)
        }

        @Test("scale token fetch is cached across network reconnect events")
        func scaleTokenFetchIsCachedAcrossReconnects() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }
            try? await Task.sleep(nanoseconds: 300_000_000)
            let baselineCalls = harness.wifiScaleService.getScaleTokenCalls
            harness.networkMonitor.isConnected = false
            harness.networkMonitor.isConnected = true
            try? await Task.sleep(nanoseconds: 200_000_000)

            #expect(harness.wifiScaleService.getScaleTokenCalls == baselineCalls)
            #expect(store.arePermissionsEnabled() == true)
        }

        @Test("showHelpModal presents the help view modal")
        func showHelpModalPresentsHelpViewModal() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.showHelpModal()

            #expect(harness.notification.showModalCalls == 1)
        }

        @Test("shouldDisableBackButton is true only on intro and setup finish")
        func shouldDisableBackButtonIsTrueOnlyForIntroAndSetupFinish() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.currentStepIndex = WifiScaleSetupStep.intro.index
            #expect(store.shouldDisableBackButton() == true)

            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index
            #expect(store.shouldDisableBackButton() == false)

            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index
            #expect(store.shouldDisableBackButton() == true)
        }

        @Test("next button text maps finish-capable steps to finish label")
        func nextButtonTextMapsFinishSteps() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.currentStepIndex = WifiScaleSetupStep.intro.index
            #expect(store.nextButtonText == CommonStrings.next)

            store.currentStepIndex = WifiScaleSetupStep.errorDetail.index
            #expect(store.nextButtonText == CommonStrings.finish)

            store.currentStepIndex = WifiScaleSetupStep.copyMacAddress.index
            #expect(store.nextButtonText == CommonStrings.finish)

            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index
            #expect(store.nextButtonText == CommonStrings.finish)
        }

        @Test("default initializer resolves dependencies from container")
        func defaultInitializerResolvesDependenciesFromContainer() {
            let originalDependencies = DependencyContainer.shared.dependencies
            defer { DependencyContainer.shared.dependencies = originalDependencies }
            DependencyContainer.shared.dependencies = [:]

            _ = WifiScaleSetupStoreTestFixtures.registerDefaultContainerDependencies()

            let store = WifiScaleSetupStore()
            store.configure(with: "0385")

            #expect(store.currentStep == .intro)
        }
    }
}
