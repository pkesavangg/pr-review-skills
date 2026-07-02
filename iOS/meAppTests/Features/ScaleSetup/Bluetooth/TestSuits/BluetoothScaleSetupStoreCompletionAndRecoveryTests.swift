import Foundation
@testable import meApp
import Testing

extension BluetoothScaleSetupStoreTests {
    @Suite("Completion Duplicate And Recovery")
    @MainActor
    struct CompletionDuplicateAndRecovery {
        @Test("new entry event marks step on synced and advances from set user to step on")
        func newEntryEventMarksStepOnSyncedAndAdvances() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(
                BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo(serialNumber: "sync-serial")
            )
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.selectedUserNumber = 2
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            let reachedSetUser = await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                store.currentStep == .setUser && store.bluetoothConnectionState == .success
            }
            #expect(reachedSetUser == true)

            harness.bluetooth.newEntryReceivedSubject.send(
                BluetoothScaleSetupStoreTestFixtures.makeEntryNotification()
            )

            let reachedStepOn = await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                store.currentStep == .stepOn && store.isEntrySynced
            }
            #expect(reachedStepOn == true)

            #expect(store.isEntrySynced == true)
            #expect(store.currentStep == .stepOn)
            #expect(store.isNextEnabled == true)
        }

        @Test("step on next moves to setup finished and finish next dismisses")
        func stepOnNextMovesToSetupFinishedAndDismisses() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.isEntrySynced = true
            store.currentStepIndex = BluetoothScaleSetupStep.stepOn.index

            store.moveToNextStep()
            #expect(store.currentStep == .setupFinished)

            store.moveToNextStep()
            #expect(dismissCalls == 1)
        }

        @Test("duplicate scale return action with same user keeps nickname and saves then exits")
        func duplicateScaleReturnWithSameUserSavesAndExits() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(
                BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo(serialNumber: "dup-serial")
            )
            let scaleService = MockScaleService()
            let existingScale = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale(
                id: "existing-scale",
                userNumber: "2"
            )
            existingScale.peripheralIdentifier = "dup-serial"
            existingScale.nickname = "Guest Bathroom"
            scaleService.scales = [existingScale.toSnapshot()]

            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(
                bluetooth: bluetooth,
                scaleService: scaleService
            )
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.selectedUserNumber = 2
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls == 1
            }
            harness.notification.alertData?.buttons.first?.action(nil)

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.scaleService.createBluetoothScaleCalls == 1 && dismissCalls == 1
            }

            #expect(harness.scaleService.lastCreatedBluetoothScale?.nickname == "Guest Bathroom")
            #expect(dismissCalls == 1)
        }

        @Test("duplicate scale return action with different user exits without saving")
        func duplicateScaleReturnWithDifferentUserExitsWithoutSaving() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(
                BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo(serialNumber: "dup-other-serial")
            )
            let scaleService = MockScaleService()
            let existingScale = BluetoothScaleSetupStoreTestFixtures.makeBluetoothScale(
                id: "existing-scale-2",
                userNumber: "4"
            )
            existingScale.peripheralIdentifier = "dup-other-serial"
            scaleService.scales = [existingScale.toSnapshot()]

            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(
                bluetooth: bluetooth,
                scaleService: scaleService
            )
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }
            store.selectedUserNumber = 2
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls == 1
            }
            harness.notification.alertData?.buttons.first?.action(nil)

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                dismissCalls == 1
            }

            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
            #expect(dismissCalls == 1)
        }

        @Test("handleExit from non finish step presents confirmation alert")
        func handleExitFromNonFinishStepShowsAlert() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.handleExit()

            #expect(harness.notification.showAlertCalls == 1)
        }
    }
}
