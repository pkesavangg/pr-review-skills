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

            // Move from selectModel -> preparing
            store.moveToNextStep()
            #expect(store.currentStep == .preparing)

            // Move from preparing -> should skip btPermission -> scanning
            store.moveToNextStep()
            #expect(store.currentStep == .scanning)
        }

        @Test("preparing routes to btPermission when bluetooth permissions are missing")
        func preparingRoutesToBtPermissionWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BpmSetupStoreTestFixtures.disabledPermissions())
            let harness = BpmSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.moveToNextStep() // selectModel -> preparing
            store.moveToNextStep() // preparing -> btPermission

            #expect(store.currentStep == .btPermission)
            #expect(store.isNextEnabled == false)
        }

        @Test("btPermission next navigates to scanning")
        func btPermissionNextNavigatesToScanning() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.currentStepIndex = BpmSetupStep.btPermission.index
            store.moveToNextStep()

            #expect(store.currentStep == .scanning)
        }

        @Test("back from preparing returns to selectModel")
        func backFromPreparingReturnsToSelectModel() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.currentStepIndex = BpmSetupStep.preparing.index
            store.moveToPreviousStep()

            #expect(store.currentStep == .selectModel)
        }

        @Test("back is disabled on selectModel")
        func backIsDisabledOnSelectModel() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            #expect(store.isBackDisabled == true)
        }

        @Test("back is disabled on success")
        func backIsDisabledOnSuccess() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.currentStepIndex = BpmSetupStep.success.index
            #expect(store.isBackDisabled == true)
        }

        @Test("next from success invokes dismiss action")
        func nextFromSuccessInvokesDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BpmSetupStep.success.index

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("back skips btPermission when bluetooth permissions are enabled")
        func backSkipsBtPermissionWhenEnabled() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureDefaultBpm(store)

            store.currentStepIndex = BpmSetupStep.scanning.index
            store.moveToPreviousStep()

            // Should skip btPermission and go to preparing
            #expect(store.currentStep == .preparing)
        }
    }
}
