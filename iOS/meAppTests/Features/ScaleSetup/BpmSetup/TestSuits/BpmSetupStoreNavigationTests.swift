import Foundation
import Testing
@testable import meApp

extension BpmSetupStoreTests {
    @Suite("Navigation")
    @MainActor
    struct Navigation {
        @Test("initial step is selectModel")
        func initialStepIsSelectModel() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            #expect(store.currentStep == .selectModel)
            #expect(store.currentStepIndex == 0)
        }

        @Test("selectModel skips btPermission when bluetooth permissions are enabled")
        func selectModelSkipsBtPermissionWhenEnabled() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            // Move from selectModel -> should skip btPermission -> selectUser
            store.moveToNextStep()
            #expect(store.currentStep == .selectUser)
        }

        @Test("selectModel routes to btPermission when bluetooth permissions are missing")
        func selectModelRoutesToBtPermissionWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BpmSetupStoreTestFixtures.disabledPermissions())
            let harness = BpmSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.moveToNextStep() // selectModel -> btPermission

            #expect(store.currentStep == .btPermission)
            #expect(store.isNextEnabled == false)
        }

        @Test("btPermission next navigates to selectUser")
        func btPermissionNextNavigatesToSelectUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.currentStepIndex = BpmSetupStep.btPermission.index
            store.moveToNextStep()

            #expect(store.currentStep == .selectUser)
        }

        @Test("back from selectUser skips btPermission and returns to selectModel")
        func backFromSelectUserSkipsBtPermissionAndReturnsToSelectModel() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.currentStepIndex = BpmSetupStep.selectUser.index
            store.moveToPreviousStep()

            #expect(store.currentStep == .selectModel)
        }

        @Test("back is disabled on selectModel")
        func backIsDisabledOnSelectModel() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            #expect(store.isBackDisabled == true)
        }

        @Test("back is disabled on paired")
        func backIsDisabledOnPaired() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.currentStepIndex = BpmSetupStep.paired.index
            #expect(store.isBackDisabled == true)
        }

        @Test("back is disabled on complete")
        func backIsDisabledOnComplete() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.currentStepIndex = BpmSetupStep.complete.index
            #expect(store.isBackDisabled == true)
        }

        @Test("next from paired invokes dismiss action")
        func nextFromPairedInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStep.paired.index

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("learn how to measure opens measurement setup")
        func learnHowToMeasureOpensMeasurementSetup() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.currentStepIndex = BpmSetupStep.paired.index
            store.moveToMeasurementTutorial()

            #expect(store.currentStep == .measureSetup)
        }

        @Test("next from complete invokes dismiss action")
        func nextFromCompleteInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStep.complete.index

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("selectUser disables next when no user selected")
        func selectUserDisablesNextWhenNoUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.selectedUserNumber = nil
            store.currentStepIndex = BpmSetupStep.selectUser.index

            #expect(store.isNextEnabled == false)
        }

        @Test("selectUser enables next when user selected")
        func selectUserEnablesNextWhenUserSelected() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.selectedUserNumber = 1
            store.currentStepIndex = BpmSetupStep.selectUser.index

            #expect(store.isNextEnabled == true)
        }

        @Test("nickname next saves device locally and advances to paired")
        func nicknameNextSavesDeviceLocallyAndAdvancesToPaired() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.deviceNickname = "Living Room BPM"
            store.currentStepIndex = BpmSetupStep.nickname.index
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            store.moveToNextStep()

            let advanced = await BpmSetupStoreTestFixtures.waitUntil {
                harness.scaleService.createBluetoothScaleCalls == 1 && store.currentStep == .paired
            }

            #expect(advanced)
            #expect(harness.scaleService.lastCreatedBluetoothScale?.nickname == "Living Room BPM")
        }
    }
}
