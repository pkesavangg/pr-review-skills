import Foundation
import Testing
@testable import meApp

extension WifiScaleSetupStoreTests {
    @Suite("Navigation")
    @MainActor
    struct Navigation {
        @Test("configure initializes at intro and marks setup in progress")
        func configureInitializesIntroAndSetupState() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            #expect(store.currentStep == .intro)
            #expect(harness.bluetoothService.isSetupInProgress == true)
            #expect(store.stepViews.count == WifiScaleSetupStep.allCases.count)
        }

        @Test("moveToNextStep skips permissions when permissions are enabled")
        func moveToNextSkipsPermissionsWhenEnabled() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .wifiPassword)
        }

        @Test("moveToNextStep enters permissions when permissions are missing")
        func moveToNextGoesToPermissionsWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(WifiScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .permissions)
        }

        @Test("moveToPreviousStep skips permissions when stepping back with permissions enabled")
        func moveToPreviousSkipsPermissionsWhenEnabled() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index

            store.moveToPreviousStep()

            #expect(store.currentStep == .intro)
        }

        @Test("skip wifi on permissions marks permissions skipped and advances")
        func skipWifiOnPermissionsMarksSkippedAndAdvances() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions(WifiScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.permissions.index
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }

            store.handleSkipWifiStep()
            harness.notification.alertData?.buttons.last?.action(nil)

            #expect(store.permissionsSkipped == true)
            #expect(store.currentStep == .wifiPassword)
            #expect(store.networkForm.ssid.value.isEmpty)
        }

        @Test("back from activate pairing mode in get-mac flow goes to intro when permissions enabled")
        func backFromActivatePairingModeGetMacWithPermissionsEnabledGoesIntro() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.isForGetMac = true
            store.currentStepIndex = WifiScaleSetupStep.activatePairingMode.index

            store.handleBackButtonClick()

            #expect(store.currentStep == .intro)
        }

        @Test("back from activate pairing mode in get-mac flow goes to permissions when permissions missing")
        func backFromActivatePairingModeGetMacWithPermissionsMissingGoesPermissions() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(WifiScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.isForGetMac = true
            store.currentStepIndex = WifiScaleSetupStep.activatePairingMode.index

            store.handleBackButtonClick()

            #expect(store.currentStep == .permissions)
        }

        @Test("back from error select falls back to connection confirm without source")
        func backFromErrorSelectFallsBackToConnectionConfirm() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.errorSelect.index

            store.handleBackButtonClick()

            #expect(store.currentStep == .connectionConfirm)
        }

        @Test("back from copy mac address returns to ap mode")
        func backFromCopyMacAddressReturnsToApMode() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.copyMacAddress.index

            store.handleBackButtonClick()

            #expect(store.currentStep == .apMode)
        }

        @Test("back from step on returns to the source step")
        func backFromStepOnReturnsToSourceStep() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            store.selectedConnectionMode = .complete

            store.handleNextButtonClick()
            #expect(store.currentStep == .stepOn)

            store.handleBackButtonClick()
            #expect(store.currentStep == .connectionConfirm)
        }
    }
}
