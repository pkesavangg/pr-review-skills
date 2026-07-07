import Combine
import Foundation
@testable import meApp
import Testing

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
            store.testSetDeviceToDelete(existing.toSnapshot())

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
            harness.scaleService.scales = [existing.toSnapshot()]

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
            harness.scaleService.scales = [existing.toSnapshot()]

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

        // MARK: - A6 User Mismatch After "Already Paired" Continue

        @Test("A6 confirmUserAndPair Continue shows User Mismatch alert when monitor is on different user")
        func a6AlreadyPairedContinueShowsUserMismatchAlert() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(
                deviceName: "BPM",
                userNumber: 2  // monitor is set to User B
            ))
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 1  // app selected User A
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            // Populate lastRetrievedDeviceInfo with userNumber: 2 (monitor set to User B)
            await store.testUpdateDeviceFromPostConnectionInfo(device)

            // Simulate the "Already Paired" alert appearing
            store.testConfirmUserAndPair(isDifferentUser: false)
            let alertsAfterFirstAlert = harness.notification.showAlertCalls
            #expect(alertsAfterFirstAlert >= 1)

            // User taps Continue on "Already Paired" alert (second button)
            harness.notification.alertData?.buttons[1].action(nil)

            // Wait for the async Task inside the Continue handler
            let mismatchAlertShown = await BpmSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls > alertsAfterFirstAlert
            }
            #expect(mismatchAlertShown)
            #expect(harness.notification.alertData?.title == BpmSetupStrings.UserMismatchAlert.title)
            // Should NOT have advanced past scanning
            #expect(store.currentStep == .scanning)
        }

        @Test("A6 confirmUserAndPair Continue advances when no user mismatch")
        func a6AlreadyPairedContinueAdvancesWhenNoMismatch() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(
                deviceName: "BPM",
                userNumber: 1  // monitor matches selected user
            ))
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 1
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            await store.testUpdateDeviceFromPostConnectionInfo(device)

            store.testConfirmUserAndPair(isDifferentUser: false)
            let alertsBefore = harness.notification.showAlertCalls

            // Tap Continue — no mismatch, should advance
            harness.notification.alertData?.buttons[1].action(nil)

            let advanced = await BpmSetupStoreTestFixtures.waitUntil {
                store.currentStep != .scanning
            }
            #expect(advanced)
            // User Mismatch alert must NOT have appeared
            #expect(harness.notification.showAlertCalls == alertsBefore)
        }

        @Test("applyDeviceInfo writes userNumber from DeviceInfo onto the device")
        func applyDeviceInfoSetsUserNumber() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            let deviceInfo = DeviceInfo(deviceName: "BPM", userNumber: 2)

            store.testApplyDeviceInfo(deviceInfo, to: device, protocolType: "A6")

            #expect(device.userNumber == "2")
        }

        // MARK: - Pairing Auto-Retry (stale-session recovery)

        @Test("startPairing retries once when the first connect fails, then succeeds without an error alert")
        func startPairingAutoRetriesAndSucceeds() async {
            let bluetooth = MockBluetoothService()
            // First connect fails (stale session from the previously-paired user), second succeeds.
            bluetooth.connectBpmResults = [.failure(.timeout), .success(.creationCompleted)]
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 2
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            await store.testStartPairing()

            // Connect was attempted twice, and no "Unable to Connect" alert was surfaced.
            #expect(bluetooth.connectBpmCalls == 2)
            #expect(harness.notification.alertData?.title != BpmSetupStrings.ConnectionErrorAlert.title)
        }

        @Test("startPairing shows Unable to Connect only after the retry also fails")
        func startPairingShowsErrorAfterRetryFails() async {
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResults = [.failure(.timeout), .failure(.timeout)]
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 2
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            await store.testStartPairing()

            #expect(bluetooth.connectBpmCalls == 2)
            #expect(harness.notification.alertData?.title == BpmSetupStrings.ConnectionErrorAlert.title)
        }

        @Test("startPairing does not retry a non-recoverable failure (Bluetooth unavailable)")
        func startPairingNoRetryOnNonRecoverableFailure() async {
            let bluetooth = MockBluetoothService()
            // A hard failure can't be fixed by a second connect — it must fail fast, not retry.
            bluetooth.connectBpmResults = [.failure(.bluetoothUnavailable), .success(.creationCompleted)]
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 2
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            await store.testStartPairing()

            // Connected only once, and "Unable to Connect" surfaced immediately.
            #expect(bluetooth.connectBpmCalls == 1)
            #expect(harness.notification.alertData?.title == BpmSetupStrings.ConnectionErrorAlert.title)
        }

        @Test("startPairing does not retry when the first connect succeeds")
        func startPairingNoRetryOnFirstSuccess() async {
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResult = .success(.creationCompleted)
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 2
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            await store.testStartPairing()

            #expect(bluetooth.connectBpmCalls == 1)
        }

        @Test("startPairing does not retry a conflict response (different-user)")
        func startPairingNoRetryOnConflictResponse() async {
            let bluetooth = MockBluetoothService()
            // A conflict is a legitimate SDK response, not a connection failure — it must not retry.
            bluetooth.connectBpmResult = .success(.deviceExistsWithDifferentUser)
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            store.selectedUserNumber = 2
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device, protocolType: .A6)
            )

            await store.testStartPairing()

            #expect(bluetooth.connectBpmCalls == 1)
            // The different-user conflict alert is shown, not the connection-error alert.
            #expect(harness.notification.alertData?.title == BpmSetupStrings.DeviceConflictAlert.DifferentUser.title)
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
