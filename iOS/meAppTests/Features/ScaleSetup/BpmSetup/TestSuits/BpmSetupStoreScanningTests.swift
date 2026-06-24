import Foundation
import Testing
@testable import meApp

extension BpmSetupStoreTests {
    @Suite("Scanning And Pairing")
    @MainActor
    struct ScanningAndPairing {
        @Test("scanning step triggers BPM scan on bluetooth service")
        func scanningStepTriggersBpmScan() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let scanned = await BpmSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForBpmCalls > 0
            }
            #expect(scanned)
        }

        @Test("device discovery during scanning updates connection state to success")
        func deviceDiscoveryUpdatesConnectionState() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            await BpmSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForBpmCalls > 0
            }

            harness.bluetooth.deviceDiscoveredSubject.send(
                BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent()
            )

            let success = await BpmSetupStoreTestFixtures.waitUntil {
                store.connectionState == .success
            }
            #expect(success)
        }

        @Test("scan timeout sets connection state to failure")
        func scanTimeoutSetsFailure() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT(scanTimeoutNs: 10_000_000) // 10ms
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let failed = await BpmSetupStoreTestFixtures.waitUntil {
                store.connectionState == .failure
            }
            #expect(failed)
        }

        @Test("non-BPM discovery events are ignored during scanning")
        func nonBpmDiscoveryEventsAreIgnored() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT(scanTimeoutNs: 500_000_000)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            await BpmSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForBpmCalls > 0
            }

            // Send a bluetooth (non-BPM) discovery event
            let bluetoothEvent = BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(setupType: .bluetooth)
            harness.bluetooth.deviceDiscoveredSubject.send(bluetoothEvent)

            // Short wait to verify state doesn't change
            try? await Task.sleep(nanoseconds: 50_000_000)
            #expect(store.connectionState == .loading)
        }

        @Test("discovery events outside scanning step are ignored")
        func discoveryEventsOutsideScanningAreIgnored() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            // Stay on selectModel step
            harness.bluetooth.deviceDiscoveredSubject.send(
                BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent()
            )

            try? await Task.sleep(nanoseconds: 50_000_000)
            #expect(store.connectionState == .loading)
        }

        @Test("pairing success does not save the device before nickname confirmation")
        func pairingSuccessDoesNotSaveTheDeviceBeforeNicknameConfirmation() async {
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResult = .success(.creationCompleted)
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.broadcastIdString = "ABCD"
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testStartPairing()

            #expect(store.connectionState == .success)
            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }

        @Test("pairing failure sets connection state to failure")
        func pairingFailureSetsFailure() async {
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResult = .failure(.pairFailed(NSError(domain: "test", code: -1)))
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.broadcastIdString = "ABCD"
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testStartPairing()

            #expect(store.connectionState == .failure)
            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }

        @Test("pairing without discovered device sets failure")
        func pairingWithoutDeviceSetsFailure() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            await store.testStartPairing()

            #expect(store.connectionState == .failure)
        }

        @Test("Already Paired alert shown only once when Continue leads to successful second pairing")
        func sameUserConflictAlertShownOnlyOnce() async {
            // SDK returns deviceExistsWithSameUser first, then creationCompleted on retry.
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResults = [
                .success(.deviceExistsWithSameUser),
                .success(.creationCompleted)
            ]
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            // Simulate a pre-pairing duplicate detected (same MAC, same user) —
            // this is what sets deviceToDelete before the SDK call.
            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "existing-bpm")
            existing.mac = "AA:BB:CC"
            existing.userNumber = "1"
            let device = BpmSetupStoreTestFixtures.makeBpmDevice(id: "new-bpm")
            device.mac = "AA:BB:CC"
            device.broadcastIdString = "ABCD"
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )
            store.testSetDeviceToDelete(existing.toSnapshot())

            // First pairing call — SDK says deviceExistsWithSameUser → shows alert
            await store.testStartPairing()

            let firstAlertCount = harness.notification.showAlertCalls
            #expect(firstAlertCount == 1)
            #expect(harness.notification.alertData?.title == BpmSetupStrings.DeviceConflictAlert.SameUser.title)

            // User taps Continue — second pairing attempt succeeds with creationCompleted
            harness.notification.alertData?.buttons[1].action(nil)

            // After Continue, the alert must NOT appear again
            let advanced = await BpmSetupStoreTestFixtures.waitUntil {
                store.currentStep != .scanning
            }
            #expect(advanced)
            #expect(harness.notification.showAlertCalls == firstAlertCount)
        }

        @Test("Already Paired alert shown only once when peripheralId match found after successful second pairing")
        func sameUserConflictAlertShownOnlyOnceViaPeripheralId() async {
            // SDK returns deviceExistsWithSameUser first, then creationCompleted on retry.
            // No MAC match pre-pairing — duplicate is detected post-connection via peripheralIdentifier.
            let bluetooth = MockBluetoothService()
            bluetooth.connectBpmResults = [
                .success(.deviceExistsWithSameUser),
                .success(.creationCompleted)
            ]
            // getDeviceInfo succeeds so peripheralIdentifier gets set on the device
            bluetooth.getDeviceInfoResult = .success(DeviceInfo(
                serialNumber: "SN-ABC",
                deviceName: "BPM",
                userNumber: 1
            ))
            let harness = BpmSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1
            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            // Existing DB entry shares the same peripheralIdentifier (serialNumber) and same user
            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "existing-bpm")
            existing.peripheralIdentifier = "SN-ABC"
            existing.userNumber = "1"
            harness.scaleService.scales = [existing]

            let device = BpmSetupStoreTestFixtures.makeBpmDevice(id: "new-bpm")
            device.broadcastIdString = "ABCD"
            device.protocolType = "A3"
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            // First pairing call — SDK says deviceExistsWithSameUser → shows alert
            await store.testStartPairing()

            let firstAlertCount = harness.notification.showAlertCalls
            #expect(firstAlertCount == 1)
            #expect(harness.notification.alertData?.title == BpmSetupStrings.DeviceConflictAlert.SameUser.title)

            // User taps Continue — second pairing succeeds, peripheralId match found in DB
            harness.notification.alertData?.buttons[1].action(nil)

            // Must advance without showing the alert a second time
            let advanced = await BpmSetupStoreTestFixtures.waitUntil {
                store.currentStep != .scanning
            }
            #expect(advanced)
            #expect(harness.notification.showAlertCalls == firstAlertCount)
        }
    }
}
