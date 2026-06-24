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
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.moveToNextStep()
            #expect(store.currentStep == .selectUser)
        }

        @Test("selectModel routes to btPermission when bluetooth permissions are missing")
        func selectModelRoutesToBtPermissionWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BpmSetupStoreTestFixtures.disabledPermissions())
            let harness = BpmSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.moveToNextStep()

            #expect(store.currentStep == .btPermission)
            #expect(store.isNextEnabled == false)
        }

        @Test("btPermission next navigates to selectUser")
        func btPermissionNextNavigatesToSelectUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.btPermission, in: store)
            store.moveToNextStep()

            #expect(store.currentStep == .selectUser)
        }

        @Test("back from selectUser skips btPermission and returns to intro")
        func backFromSelectUserSkipsBtPermissionAndReturnsToIntro() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.selectUser, in: store)
            store.moveToPreviousStep()

            #expect(store.currentStep == .intro)
        }

        @Test("back is disabled on selectModel")
        func backIsDisabledOnSelectModel() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            #expect(store.isBackDisabled == true)
        }

        @Test("back is disabled on complete")
        func backIsDisabledOnComplete() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.complete, in: store)
            #expect(store.isBackDisabled == true)
        }

        @Test("next from paired invokes dismiss action")
        func nextFromPairedInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.paired, in: store)

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("learn how to measure opens measurement setup")
        func learnHowToMeasureOpensMeasurementSetup() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.paired, in: store)
            store.moveToMeasurementTutorial()

            #expect(store.currentStep == .measureSetup)
        }

        @Test("next from complete invokes dismiss action")
        func nextFromCompleteInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.complete, in: store)

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("selectUser disables next when no user selected")
        func selectUserDisablesNextWhenNoUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.selectedUserNumber = nil
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.selectUser, in: store)

            #expect(store.isNextEnabled == false)
        }

        @Test("selectUser enables next when user selected")
        func selectUserEnablesNextWhenUserSelected() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.selectedUserNumber = 1
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.selectUser, in: store)

            #expect(store.isNextEnabled == true)
        }

        @Test("nickname next saves device locally and advances to paired")
        func nicknameNextSavesDeviceLocallyAndAdvancesToPaired() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.deviceNickname = "Living Room BPM"
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.nickname, in: store)
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

        // MARK: - A6 Navigation (same steps as A3)

        @Test("A6 configure uses preSelectedSteps")
        func a6ConfigureUsesPreSelectedSteps() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            #expect(store.steps == BpmSetupStep.preSelectedSteps)
            #expect(store.isA6Flow == true)
        }

        @Test("A6 and A3 share the same step sequence when pre-selected")
        func a6AndA3ShareSameSteps() {
            let a3Harness = BpmSetupStoreTestFixtures.makeSUT()
            BpmSetupStoreTestFixtures.configureA3Bpm(a3Harness.store)

            let a6Harness = BpmSetupStoreTestFixtures.makeSUT()
            BpmSetupStoreTestFixtures.configureA6Bpm(a6Harness.store)

            #expect(a3Harness.store.steps == a6Harness.store.steps)
        }

        @Test("A6 next from paired invokes dismiss (same as A3)")
        func a6NextFromPairedInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.paired, in: store)

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("A6 learn how to measure opens measurement setup")
        func a6LearnHowToMeasureOpensMeasurementSetup() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.paired, in: store)
            store.moveToMeasurementTutorial()

            #expect(store.currentStep == .measureSetup)
        }

        @Test("A6 next from complete invokes dismiss")
        func a6NextFromCompleteInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.complete, in: store)

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("A6 measurement tutorial advances through measureSetup -> measureStart -> complete")
        func a6MeasurementTutorialFlow() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            store.moveToMeasurementTutorial()
            #expect(store.currentStep == .measureSetup)

            store.moveToNextStep()
            #expect(store.currentStep == .measureStart)

            store.moveToNextStep()
            #expect(store.currentStep == .complete)
        }
    }
}
