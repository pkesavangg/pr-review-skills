import Foundation
@testable import meApp
import Testing

extension WifiScaleSetupStoreTests {
    @Suite("Modes And Progression")
    @MainActor
    struct ModesAndProgression {
        @Test("connection confirm with complete mode moves to step on")
        func connectionConfirmCompleteMovesToStepOn() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            store.selectedConnectionMode = .complete

            store.handleNextButtonClick()

            #expect(store.currentStep == .stepOn)
        }

        @Test("connection confirm with ap mode selection moves to ap mode")
        func connectionConfirmApModeMovesToApMode() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            store.selectedConnectionMode = .apMode

            store.handleNextButtonClick()

            #expect(store.currentStep == .apMode)
        }

        @Test("connection confirm forces ap mode when permissions were skipped")
        func connectionConfirmForcesApModeWhenPermissionsSkipped() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            store.permissionsSkipped = true
            store.selectedConnectionMode = .complete

            store.handleNextButtonClick()

            #expect(store.selectedConnectionMode == .apMode)
            #expect(store.currentStep == .apMode)
        }

        @Test("connection confirm forces ap mode in get-mac flow")
        func connectionConfirmForcesApModeInGetMacFlow() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.connectionConfirm.index
            store.isForGetMac = true
            store.selectedConnectionMode = .complete

            store.handleNextButtonClick()

            #expect(store.selectedConnectionMode == .apMode)
            #expect(store.currentStep == .apMode)
        }

        @Test("ap mode next advances to ap mode confirm in standard flow")
        func apModeNextAdvancesToApModeConfirmInStandardFlow() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.isForGetMac = false
            store.currentStepIndex = WifiScaleSetupStep.apMode.index

            store.handleNextButtonClick()

            #expect(store.currentStep == .apModeConfirm)
        }

        @Test("ap mode confirm next advances to step on")
        func apModeConfirmNextAdvancesToStepOn() {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.currentStepIndex = WifiScaleSetupStep.apModeConfirm.index

            store.handleNextButtonClick()

            #expect(store.currentStep == .stepOn)
        }

        @Test("error detail finish exits setup and stops wifi setup client")
        func errorDetailFinishExitsSetupAndStopsWifi() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = WifiScaleSetupStep.errorDetail.index

            store.handleNextButtonClick()
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                dismissCalls == 1
            }

            #expect(dismissCalls == 1)
            #expect(harness.wifiScaleService.stopCalls > 0)
            #expect(harness.httpClient.skipCheckNetwork == false)
        }

        @Test("copy mac address finish exits setup")
        func copyMacAddressFinishExitsSetup() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = WifiScaleSetupStep.copyMacAddress.index

            store.handleNextButtonClick()
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                dismissCalls == 1
            }

            #expect(dismissCalls == 1)
        }

        @Test("setup finish saves scale and sets up push notifications before exit")
        func setupFinishSavesScaleAndSetsUpPushBeforeExit() async {
            let harness = WifiScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            WifiScaleSetupStoreTestFixtures.configureDefaultWifiScale(store)
            store.selectedUserNumber = 2
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.wifiScaleService.getScaleTokenCalls > 0
            }

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = WifiScaleSetupStep.setupFinish.index

            store.handleNextButtonClick()
            await WifiScaleSetupStoreTestFixtures.waitUntil {
                harness.scaleService.createDeviceCalls == 1 &&
                    harness.pushNotifications.setupPushNotificationsCalls == 1 &&
                    dismissCalls == 1
            }

            #expect(harness.scaleService.createDeviceCalls == 1)
            #expect(harness.pushNotifications.setupPushNotificationsCalls == 1)
            #expect(harness.pushNotifications.lastIsFromScaleSetup == true)
            #expect(dismissCalls == 1)
            #expect(harness.bluetoothService.isSetupInProgress == false)
        }
    }
}
