import Foundation
import Testing
@testable import meApp

private enum WifiScaleSetupStoreTestError: Error {
    case tokenUnavailable
}

extension WifiScaleSetupStoreTests {
    @Suite("Validation And Errors")
    @MainActor
    struct ValidationAndErrors {
        @Test("permissions step next button follows permission state")
        func permissionsStepNextButtonFollowsPermissionState() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions(WifiScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.permissions.index

            #expect(store.isNextEnabled == false)

            permissions.setPermissions(WifiScaleSetupStoreTestFixtures.enabledPermissions())
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                store.isNextEnabled
            }
            #expect(store.isNextEnabled == true)
        }

        @Test("wifi password step requires ssid and password for secured network")
        func wifiPasswordStepRequiresSsidAndPasswordForSecuredNetwork() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.networkForm.setSSID("")
            store.networkForm.setPassword("")
            store.networkForm.networkHasNoPassword = false
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index
            #expect(store.isNextEnabled == false)

            store.networkForm.setSSID("Home WiFi")
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index
            #expect(store.isNextEnabled == false)

            store.networkForm.setPassword("pass1234")
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index
            #expect(store.isNextEnabled == true)
        }

        @Test("wifi password step allows open network with ssid only")
        func wifiPasswordStepAllowsOpenNetworkWithSsidOnly() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.networkForm.networkHasNoPassword = true
            store.networkForm.setSSID("Open Network")
            store.currentStepIndex = WifiScaleSetupStep.wifiPassword.index

            #expect(store.isNextEnabled == true)
        }

        @Test("select user step enables next only when a user is selected")
        func selectUserStepEnablesNextOnlyWithSelection() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.selectedUserNumber = nil
            store.currentStepIndex = WifiScaleSetupStep.selectUser.index
            #expect(store.isNextEnabled == false)

            store.selectedUserNumber = 3
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.selectUser.index
            #expect(store.isNextEnabled == true)
        }

        @Test("connection confirm next button requires mode unless permissions were skipped")
        func connectionConfirmNextButtonRequiresModeUnlessSkipped() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.permissionsSkipped = false
            store.isForGetMac = false
            store.selectedConnectionMode = .none
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            #expect(store.isNextEnabled == false)

            store.selectedConnectionMode = .complete
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            #expect(store.isNextEnabled == true)

            store.selectedConnectionMode = .none
            store.permissionsSkipped = true
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            #expect(store.isNextEnabled == true)
        }

        @Test("ap mode next button requires AP SSID unless permissions were skipped")
        func apModeNextButtonRequiresApSsidUnlessSkipped() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.permissionsSkipped = false
            store.networkForm.setSSID("Home WiFi")
            store.currentStepIndex = WifiScaleSetupStep.apMode.index
            #expect(store.isNextEnabled == false)

            store.networkForm.setSSID("gg_SmartDeviceSetup_123")
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.apMode.index
            #expect(store.isNextEnabled == true)

            store.permissionsSkipped = true
            store.networkForm.setSSID("")
            store.currentStepIndex = WifiScaleSetupStep.intro.index
            store.currentStepIndex = WifiScaleSetupStep.apMode.index
            #expect(store.isNextEnabled == true)
        }

        @Test("handleExit from non-finish step shows exit confirmation alert")
        func handleExitFromNonFinishStepShowsAlert() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.handleExit()

            #expect(harness.notification.showAlertCalls == 1)
            harness.notification.alertData?.buttons.first?.action(nil)
            #expect(dismissCalls == 1)
        }

        @Test("handleExit from setup finish dismisses immediately")
        func handleExitFromSetupFinishDismisses() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.handleExit()

            #expect(dismissCalls == 1)
        }

        @Test("permissions next without token shows internet-required toast and stays on permissions")
        func permissionsNextWithoutTokenShowsToastAndStays() async {
            let wifiScaleService = MockWifiScaleService()
            wifiScaleService.getScaleTokenResult = .failure(WifiScaleSetupStoreTestError.tokenUnavailable)
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(wifiScaleService: wifiScaleService)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.permissions.index

            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }
            store.handleNextButtonClick()

            #expect(harness.notification.showToastCalls == 1)
            #expect(store.currentStep == .permissions)
        }

        @Test("skip wifi without token does not show alert and does not advance")
        func skipWifiWithoutTokenDoesNotShowAlertOrAdvance() async {
            let wifiScaleService = MockWifiScaleService()
            wifiScaleService.getScaleTokenResult = .failure(WifiScaleSetupStoreTestError.tokenUnavailable)
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT(wifiScaleService: wifiScaleService)
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.permissions.index

            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }
            store.handleSkipWifiStep()

            #expect(harness.notification.showAlertCalls == 0)
            #expect(store.currentStep == .permissions)
        }

        @Test("setup finish without selected user dismisses loader and does not create device")
        func setupFinishWithoutSelectedUserDismissesLoaderAndSkipsCreate() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.selectedUserNumber = nil
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
            #expect(harness.pushNotifications.setupPushNotificationsCalls == 0)
        }

        @Test("connection confirm starts smartConnect for standard wifi setup")
        func connectionConfirmStartsSmartConnectForStandardWifiSetup() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.permissionsSkipped = false
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.smartConnectCalls == 1
            }

            #expect(harness.wifiScaleService.smartConnectCalls == 1)
            #expect(harness.wifiScaleService.espSmartConnectCalls == 0)
        }

        @Test("connection confirm starts espSmartConnect for esp-touch setup")
        func connectionConfirmStartsEspSmartConnectForEspTouchSetup() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0384")

            store.permissionsSkipped = false
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.espSmartConnectCalls == 1
            }

            #expect(harness.wifiScaleService.espSmartConnectCalls == 1)
            #expect(harness.wifiScaleService.smartConnectCalls == 0)
        }

        @Test("connection confirm does not start smartConnect when permissions were skipped")
        func connectionConfirmDoesNotStartSmartConnectWhenPermissionsSkipped() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.permissionsSkipped = true
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            await WifiScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 200_000_000) {
                harness.wifiScaleService.stopCalls > 0 ||
                    harness.wifiScaleService.smartConnectCalls > 0 ||
                    harness.wifiScaleService.espSmartConnectCalls > 0
            }

            #expect(harness.wifiScaleService.stopCalls == 0)
            #expect(harness.wifiScaleService.smartConnectCalls == 0)
            #expect(harness.wifiScaleService.espSmartConnectCalls == 0)
        }

        @Test("ap mode confirm starts AP mode setup")
        func apModeConfirmStartsApModeSetup() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.connectedSsid = "Stored SSID"
            store.connectedBssid = "AA:BB:CC:DD:EE:FF"
            store.networkForm.setSSID("gg_SmartDeviceSetup_123")

            store.currentStepIndex = WifiScaleSetupStep.apModeConfirm.index
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.apModeCalls == 1
            }

            #expect(harness.wifiScaleService.apModeCalls == 1)
            #expect(harness.wifiScaleService.lastApModeInfo?.ssid == "Stored SSID")
        }

        @Test("entering AP mode sets skipCheckNetwork and reset clears it")
        func enteringApModeSetsSkipCheckNetworkAndResetClearsIt() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)

            store.currentStepIndex = WifiScaleSetupStep.apMode.index
            #expect(harness.httpClient.skipCheckNetwork == true)

            store.resetSkipCheckNetwork()
            #expect(harness.httpClient.skipCheckNetwork == false)
        }
    }
}
