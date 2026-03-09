import Foundation
import Testing
@testable import meApp

extension BluetoothScaleSetupStoreTests {
    @Suite("Navigation And Selection")
    @MainActor
    struct NavigationAndSelection {
        @Test("intro skips permissions when bluetooth permissions are already enabled")
        func introSkipsPermissionsWhenEnabled() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .selectUser)
        }

        @Test("intro routes to permissions when bluetooth permissions are missing")
        func introRoutesToPermissionsWhenMissing() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BluetoothScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

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
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(permissions: permissions)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.currentStepIndex = BluetoothScaleSetupStep.permissions.index

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                permissions.handlePermissionCalls.contains(.bluetooth)
            }

            let enabledHarness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let enabledStore = enabledHarness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(enabledStore)
            enabledStore.currentStepIndex = BluetoothScaleSetupStep.permissions.index
            #expect(enabledStore.isNextEnabled == true)
        }

        @Test("select user step enables next only when a user number is selected")
        func selectUserStepRequiresSelection() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.selectedUserNumber = nil
            store.currentStepIndex = BluetoothScaleSetupStep.selectUser.index
            #expect(store.isNextEnabled == false)

            store.selectedUserNumber = 3
            #expect(store.isNextEnabled == true)
        }

        @Test("permissions next after successful save resumes at step on")
        func permissionsNextAfterSuccessfulSaveResumesAtStepOn() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(
                BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo(serialNumber: "serial-success")
            )
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.selectedUserNumber = 2
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.scaleService.createBluetoothScaleCalls == 1 &&
                store.bluetoothConnectionState == .success
            }

            store.currentStepIndex = BluetoothScaleSetupStep.permissions.index

            store.moveToNextStep()

            #expect(store.currentStep == .stepOn)
        }

        @Test("next from setup finished invokes dismiss action")
        func nextFromSetupFinishedInvokesDismiss() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.currentStepIndex = BluetoothScaleSetupStep.setupFinished.index

            store.moveToNextStep()

            #expect(dismissCalls == 1)
        }
    }
}
