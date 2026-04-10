import Combine
import Foundation
import Testing
@testable import meApp

extension BpmSetupStoreTests {
    @Suite("Device Flow")
    @MainActor
    struct DeviceFlow {

        // MARK: - Save Device (via nickname advance)

        @Test("saveAndAdvanceFromNickname saves device and navigates to paired")
        func saveAndAdvanceFromNicknameSuccess() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.deviceNickname = "My BPM"
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.nickname, in: store)
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testSaveAndAdvanceFromNickname()

            #expect(store.currentStep == .paired)
            #expect(harness.scaleService.createBluetoothScaleCalls == 1)
            #expect(harness.scaleService.lastCreatedBluetoothScale?.nickname == "My BPM")
        }

        @Test("saveAndAdvanceFromNickname does nothing when not on nickname step")
        func saveAndAdvanceFromNicknameWrongStep() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            // Stay on selectUser step (not nickname)
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.selectUser, in: store)

            await store.testSaveAndAdvanceFromNickname()

            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }

        @Test("saveAndAdvanceFromNickname does not advance when save fails")
        func saveAndAdvanceFromNicknameFailure() async {
            let scaleService = MockScaleService()
            scaleService.createDeviceError = NSError(domain: "test", code: 1)
            let harness = BpmSetupStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.deviceNickname = "My BPM"
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.nickname, in: store)
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testSaveAndAdvanceFromNickname()

            #expect(store.currentStep == .nickname)
        }

        @Test("save device skips when already saved")
        func saveDeviceSkipsWhenAlreadySaved() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.deviceNickname = "Test"
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.nickname, in: store)
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device),
                isDeviceSaved: true
            )

            await store.testSaveAndAdvanceFromNickname()

            // Should advance (returns true immediately) but NOT call createBluetoothScale again
            #expect(store.currentStep == .paired)
            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }

        @Test("save device deletes previous device entry when deviceToDelete is set")
        func saveDeviceDeletesPreviousEntry() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "old-bpm")
            let device = BpmSetupStoreTestFixtures.makeBpmDevice(id: "new-bpm")
            store.deviceNickname = "Updated"
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.nickname, in: store)
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )
            store.testSetDeviceToDelete(existing)

            await store.testSaveAndAdvanceFromNickname()

            #expect(harness.scaleService.deleteSingleDeviceEntryCalls >= 1)
            #expect(harness.scaleService.createBluetoothScaleCalls == 1)
            #expect(store.currentStep == .paired)
        }

        // MARK: - updateDeviceFromPostConnectionInfo

        @Test("updateDeviceFromPostConnectionInfo applies device info on success")
        func updateDeviceInfoAppliesOnSuccess() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(
                serialNumber: "SN-12345",
                deviceName: "BPM",
                broadcastIdString: "FF01",
                password: "AB",
                macAddress: "AA:BB:CC:DD:EE:FF"
            ))
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.protocolType = "A3"
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testUpdateDeviceFromPostConnectionInfo(device)

            // A3 stores serialNumber as peripheralIdentifier
            #expect(device.peripheralIdentifier == "SN-12345")
            #expect(device.mac == "AA:BB:CC:DD:EE:FF")
        }

        @Test("updateDeviceFromPostConnectionInfo handles failure gracefully")
        func updateDeviceInfoHandlesFailure() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .failure(.notImplemented)
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testUpdateDeviceFromPostConnectionInfo(device)

            // Should not crash; device unchanged
            #expect(device.peripheralIdentifier == nil)
        }

        // MARK: - BPM Reading Subscription

        @Test("BPM reading subscription sets isReadingSynced and auto-cancels")
        func bpmReadingSubscriptionSetsSync() async {
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResult = .success(.creationCompleted)
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.measureSetup, in: store)

            bluetooth.newBpmReadingReceivedSubject.send(
                BpmSetupStoreTestFixtures.makeBpmMeasurement()
            )

            let synced = await BpmSetupStoreTestFixtures.waitUntil {
                store.isReadingSynced == true
            }
            #expect(synced)

            // Send second reading — should be ignored (subscription auto-cancelled)
            store.isReadingSynced = false
            bluetooth.newBpmReadingReceivedSubject.send(
                BpmSetupStoreTestFixtures.makeBpmMeasurement(systolic: 130)
            )
            try? await Task.sleep(nanoseconds: 50_000_000)
            #expect(store.isReadingSynced == false)
        }

        // MARK: - Pre-pairing Duplicate Detection

        @Test("checkForPrePairingDuplicate with same-user MAC match shows alert")
        func prePairingDuplicateSameUserShowsAlert() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "existing-bpm")
            existing.mac = "AA:BB:CC"
            existing.userNumber = "1"
            harness.scaleService.scales = [existing]

            let discovered = BpmSetupStoreTestFixtures.makeBpmDevice(id: "discovered")
            discovered.mac = "AA:BB:CC"
            store.testSetInternalState(
                discoveredDevice: discovered,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: discovered)
            )

            await store.testCheckForPrePairingDuplicate()

            // Same-user duplicate → shows conflict alert
            #expect(harness.notification.showAlertCalls >= 1)
        }

        @Test("checkForPrePairingDuplicate with different-user MAC match does not show alert")
        func prePairingDifferentUserNoAlert() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let alertsBefore = harness.notification.showAlertCalls

            // Existing device has same MAC but different user
            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "existing-bpm")
            existing.mac = "AA:BB:CC"
            existing.userNumber = "2"
            harness.scaleService.scales = [existing]

            let discovered = BpmSetupStoreTestFixtures.makeBpmDevice()
            discovered.mac = "AA:BB:CC"
            discovered.broadcastIdString = "ABCD"
            discovered.userNumber = "1" // matches selectedUserNumber to avoid user-mismatch alert
            store.testSetInternalState(
                discoveredDevice: discovered,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: discovered)
            )

            await store.testCheckForPrePairingDuplicate()

            // Different user on same device → no conflict alert (unlike same-user which shows one)
            #expect(harness.notification.showAlertCalls == alertsBefore)
        }

        // MARK: - cleanUp

        @Test("cleanUp resets setup in progress and isDeviceSaved")
        func cleanUpResetsState() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            harness.bluetooth.isSetupInProgress = true
            store.testSetInternalState(isDeviceSaved: true)

            store.cleanUp()

            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        // MARK: - handleExit from non-terminal steps

        @Test("handleExit shows alert and exit button dismisses setup")
        func handleExitShowsAlertAndDismisses() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            harness.bluetooth.isSetupInProgress = true

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)
            store.handleExit()

            #expect(harness.notification.showAlertCalls >= 1)

            // Tap exit button (first button)
            let alert = harness.notification.alertData
            alert?.buttons[0].action(nil)

            #expect(dismissCalls == 1)
            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        @Test("handleExit return button does not dismiss")
        func handleExitReturnButtonDoesNotDismiss() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.handleExit()

            let alert = harness.notification.alertData
            // Tap return button (second button)
            alert?.buttons[1].action(nil)

            #expect(dismissCalls == 0)
        }
    }
}
