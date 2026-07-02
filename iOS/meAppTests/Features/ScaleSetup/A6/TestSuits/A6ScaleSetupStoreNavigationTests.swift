import Foundation
@testable import meApp
import Testing

extension A6ScaleSetupStoreTests {
    @Suite("Navigation And Permissions")
    @MainActor
    struct NavigationAndPermissions {
        @Test("intro skips permissions when bluetooth permissions are already enabled")
        func introSkipsPermissionsWhenEnabled() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .wakeUp)
        }

        @Test("intro routes to permissions when bluetooth permissions are missing")
        func introRoutesToPermissionsWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(A6ScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .permissions)
            #expect(store.isNextEnabled == false)
        }

        @Test("permissions step requests missing bluetooth permission and enables next after both are granted")
        func permissionsStepRequestsMissingPermissionAndEnablesNext() async {
            let permissions = MockPermissionsService()
            permissions.handlePermissionResults[.bluetooth] = .ENABLED
            permissions.handlePermissionResults[.bluetoothSwitch] = .ENABLED
            permissions.setPermissions([
                .BLUETOOTH: .DISABLED,
                .BLUETOOTH_SWITCH: .DISABLED
            ])
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.currentStepIndex = A6ScaleSetupStep.permissions.index

            await A6ScaleSetupStoreTestFixtures.waitUntil {
                permissions.handlePermissionCalls.contains(.bluetooth)
            }

            let enabledHarness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let enabledStore = enabledHarness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(enabledStore)
            enabledStore.currentStepIndex = A6ScaleSetupStep.permissions.index
            #expect(enabledStore.isNextEnabled == true)
        }

        @Test("permissions next moves to wake up")
        func permissionsNextMovesToWakeUp() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.permissions.index
            store.moveToNextStep()

            #expect(store.currentStep == .wakeUp)
        }

        @Test("next from setup finished invokes dismiss action")
        func nextFromSetupFinishedInvokesDismiss() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = A6ScaleSetupStep.setupFinished.index

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }

        @Test("moveToPreviousStep from wake up returns to permissions or intro")
        func moveToPreviousFromWakeUp() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(A6ScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index

            store.moveToPreviousStep()

            #expect(store.currentStep == .permissions)
        }

        @Test("moveToPreviousStep from intro is no-op")
        func moveToPreviousFromIntroIsNoOp() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToPreviousStep()

            #expect(store.currentStep == .intro)
        }

        @Test("step views count matches steps count")
        func stepViewsMatchSteps() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("step views returns empty when no scale item configured")
        func stepViewsEmptyWithoutConfiguration() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            #expect(store.stepViews.isEmpty)
        }
    }
}
