import Foundation
import Testing
@testable import meApp

extension BpmSetupStoreTests {
    @Suite("Permissions And State")
    @MainActor
    struct PermissionsAndState {
        @Test("btPermission step disables next when bluetooth permissions are missing")
        func btPermissionDisablesNextWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BpmSetupStoreTestFixtures.disabledPermissions())
            let harness = BpmSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store

            store.currentStepIndex = BpmSetupStep.btPermission.index

            #expect(store.isNextEnabled == false)
        }

        @Test("btPermission step enables next when bluetooth permissions are granted")
        func btPermissionEnablesNextWhenGranted() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStep.btPermission.index

            #expect(store.isNextEnabled == true)
        }

        @Test("btPermission step disables next when A3 location permissions are missing")
        func btPermissionDisablesNextWhenA3LocationMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .ENABLED,
                .LOCATION: .DISABLED,
                .LOCATION_SWITCH: .DISABLED
            ])
            let harness = BpmSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStep.btPermission.index

            #expect(store.isNextEnabled == false)
        }

        @Test("selectModel step disables next when no SKU is selected")
        func selectModelDisablesNextWhenNoSku() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.selectedSku = nil
            store.currentStepIndex = BpmSetupStep.selectModel.index

            #expect(store.isNextEnabled == false)
        }

        @Test("selectModel step enables next when SKU is selected")
        func selectModelEnablesNextWhenSkuSelected() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.selectedSku = "0603"
            store.currentStepIndex = BpmSetupStep.selectModel.index

            #expect(store.isNextEnabled == true)
        }

        @Test("permission loss during scanning navigates back to btPermission")
        func permissionLossNavigatesToBtPermission() async {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BpmSetupStoreTestFixtures.enabledPermissions())
            let harness = BpmSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStep.scanning.index

            // Simulate permission loss
            permissions.setPermissions(BpmSetupStoreTestFixtures.disabledPermissions())

            let navigated = await BpmSetupStoreTestFixtures.waitUntil {
                store.currentStep == .btPermission
            }
            #expect(navigated)
        }

        @Test("configure sets bpm item and marks setup in progress")
        func configureSetsBpmItemAndSetupInProgress() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: "0603")

            #expect(store.selectedSku == "0603")
            #expect(harness.bluetooth.isSetupInProgress == true)
        }

        @Test("cleanUp resets state and clears setup in progress flag")
        func cleanUpResetsState() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.cleanUp()

            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        @Test("handleExit shows confirmation alert")
        func handleExitShowsAlert() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.handleExit()

            #expect(harness.notification.showAlertCalls == 1)
        }

        @Test("nickname disables next when empty")
        func nicknameDisablesNextWhenEmpty() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.deviceNickname = "   "
            store.currentStepIndex = BpmSetupStep.nickname.index

            #expect(store.isNextEnabled == false)
        }

        @Test("nickname enables next when non-empty")
        func nicknameEnablesNextWhenNonEmpty() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.deviceNickname = "My BPM"
            store.currentStepIndex = BpmSetupStep.nickname.index

            #expect(store.isNextEnabled == true)
        }

        @Test("BPM reading subscription marks isReadingSynced")
        func bpmReadingSubscriptionMarksSync() async {
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResult = .success(())
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStep.measureSetup.index

            // Simulate BPM reading received
            bluetooth.newBpmReadingReceivedSubject.send(
                BpmSetupStoreTestFixtures.makeBpmMeasurement()
            )

            let synced = await BpmSetupStoreTestFixtures.waitUntil {
                store.isReadingSynced == true
            }
            #expect(synced)
        }
    }
}
