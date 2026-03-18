import Foundation
import Testing
@testable import meApp

extension A6ScaleSetupStoreTests {
    @Suite("Edge Coverage And Cleanup")
    @MainActor
    struct EdgeCoverageAndCleanup {
        @Test("handleExit from non-finish step presents confirmation alert")
        func handleExitFromNonFinishStepShowsAlert() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.handleExit()

            #expect(harness.notification.showAlertCalls == 1)
        }

        @Test("handleExit from setupFinished dismisses without alert")
        func handleExitFromFinishStepDismisses() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = A6ScaleSetupStep.setupFinished.index

            store.handleExit()

            #expect(dismissCalls == 1)
            #expect(harness.notification.showAlertCalls == 0)
        }

        @Test("handleExit primary action dismisses flow")
        func handleExitPrimaryActionDismisses() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.handleExit()
            harness.notification.alertData?.buttons.first?.action(nil)

            #expect(dismissCalls == 1)
        }

        @Test("showHelpModal presents modal via notification service")
        func showHelpModalPresentsModal() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.showHelpModal()

            #expect(harness.notification.showModalCalls == 1)
        }

        @Test("cleanUp resets setup flags and cancels resources")
        func cleanUpResetsSetupFlags() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            harness.bluetooth.isSetupInProgress = true

            store.cleanUp()

            #expect(harness.bluetooth.isSetupInProgress == false)
            #expect(harness.bluetooth.reapplySkipDevicesExcludingPairedCalls >= 1)
        }

        @Test("configure sets setup in progress flag")
        func configureSetsSetupInProgress() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: "0022")

            #expect(harness.bluetooth.isSetupInProgress == true)
        }

        @Test("permission change to disabled during wakeUp navigates back to permissions")
        func permissionChangeToDisabledDuringWakeUpGoesBack() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions(A6ScaleSetupStoreTestFixtures.enabledPermissions())
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            permissions.setPermissions(A6ScaleSetupStoreTestFixtures.disabledPermissions())

            await A6ScaleSetupStoreTestFixtures.waitUntil {
                store.currentStep == .permissions
            }

            #expect(store.currentStep == .permissions)
        }

        @Test("permission change during non-wakeUp step does not navigate")
        func permissionChangeDuringNonWakeUpNoOp() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions(A6ScaleSetupStoreTestFixtures.enabledPermissions())
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.intro.index
            permissions.setPermissions(A6ScaleSetupStoreTestFixtures.disabledPermissions())

            try? await Task.sleep(nanoseconds: 50_000_000)
            #expect(store.currentStep == .intro)
        }

        @Test("isNextEnabled is true for non-permissions steps")
        func isNextEnabledForNonPermissionSteps() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.intro.index
            #expect(store.isNextEnabled == true)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            #expect(store.isNextEnabled == true)

            store.currentStepIndex = A6ScaleSetupStep.setupFinished.index
            #expect(store.isNextEnabled == true)
        }

        @Test("markA6ScalesUnsyncedForUnitUpdate marks A6 scale preferences as unsynced")
        func markA6ScalesUnsyncedForUnitUpdate() async {
            let scaleService = MockScaleService()
            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            device.protocolType = "A6"
            let pref = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: device.id),
                scaleId: device.id
            )
            pref.isSynced = true
            scaleService.scales = [device]
            scaleService.fetchAttachedPreferenceResult = pref

            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            await store.markA6ScalesUnsyncedForUnitUpdate()

            #expect(pref.isSynced == false)
            #expect(harness.bluetooth.syncDevicesCalls >= 1)
        }

        @Test("markA6ScalesUnsyncedForUnitUpdate with no A6 scales is no-op")
        func markA6ScalesUnsyncedNoScales() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            await store.markA6ScalesUnsyncedForUnitUpdate()

            #expect(harness.bluetooth.syncDevicesCalls == 0)
        }

        @Test("markA6ScalesUnsyncedForUnitUpdate handles updateScalePreference failure gracefully")
        func markA6ScalesUnsyncedHandlesFailure() async {
            let scaleService = MockScaleService()
            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            device.protocolType = "A6"
            let pref = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: device.id),
                scaleId: device.id
            )
            scaleService.scales = [device]
            scaleService.fetchAttachedPreferenceResult = pref
            scaleService.updateScalePreferenceError = NSError(domain: "test", code: 1)

            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            await store.markA6ScalesUnsyncedForUnitUpdate()

            #expect(harness.bluetooth.syncDevicesCalls >= 1)
        }
    }
}
