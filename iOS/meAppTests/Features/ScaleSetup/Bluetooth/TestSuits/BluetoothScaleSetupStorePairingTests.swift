import Foundation
import Testing
@testable import meApp

extension BluetoothScaleSetupStoreTests {
    @Suite("Pairing Start Retry And Errors")
    @MainActor
    struct PairingStartRetryAndErrors {
        @Test("entering connecting bluetooth starts scan")
        func enteringConnectingBluetoothStartsScan() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForPairingCalls == 1
            }

            #expect(harness.bluetooth.scanForPairingCalls == 1)
            #expect(store.bluetoothConnectionState == .loading)
        }

        @Test("returning to connecting bluetooth retries scan")
        func returningToConnectingBluetoothRetriesScan() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForPairingCalls == 1
            }

            store.moveToPreviousStep()
            #expect(store.currentStep == .selectUser)

            store.moveToNextStep()
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForPairingCalls == 2
            }

            #expect(harness.bluetooth.scanForPairingCalls == 2)
            #expect(store.bluetoothConnectionState == .loading)
        }

        @Test("connection timeout moves state to failure when no device is discovered")
        func connectionTimeoutMovesStateToFailure() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(pairingTimeoutNs: 30_000_000)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            await BluetoothScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
                store.bluetoothConnectionState == .failure
            }

            #expect(store.bluetoothConnectionState == .failure)
            #expect(harness.bluetooth.confirmSmartPairCalls == 0)
            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }

        @Test("non bluetooth discovery events are ignored and pairing eventually times out")
        func nonBluetoothDiscoveryEventsAreIgnored() async {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(pairingTimeoutNs: 40_000_000)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent(setupType: .wifi)
            )

            await BluetoothScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 600_000_000) {
                store.bluetoothConnectionState == .failure
            }

            #expect(harness.bluetooth.confirmSmartPairCalls == 0)
            #expect(store.bluetoothConnectionState == .failure)
        }

        @Test("successful pairing captures selected user number and saves the scale")
        func successfulPairingCapturesSelectedUserAndSavesScale() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .success(.creationCompleted)
            bluetooth.getDeviceInfoResult = .success(
                BluetoothScaleSetupStoreTestFixtures.makeDeviceInfo(serialNumber: "serial-22")
            )
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.selectedUserNumber = 4
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.confirmSmartPairCalls == 1 &&
                harness.scaleService.createBluetoothScaleCalls == 1 &&
                store.bluetoothConnectionState == .success
            }
            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                store.currentStep == .setUser
            }

            #expect(harness.bluetooth.lastConfirmedPairUserNumber == 4)
            #expect(harness.scaleService.createBluetoothScaleCalls == 1)
            #expect(store.currentStep == .setUser)
        }

        @Test("pairing failure keeps store in failure state and does not save scale")
        func pairingFailureKeepsFailureStateAndDoesNotSave() async {
            let bluetooth = MockBluetoothService()
            bluetooth.confirmSmartPairResult = .failure(.notImplemented)
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(bluetooth: bluetooth)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.selectedUserNumber = 1
            store.currentStepIndex = BluetoothScaleSetupStep.connectingBluetooth.index
            harness.bluetooth.deviceDiscoveredSubject.send(BluetoothScaleSetupStoreTestFixtures.makeDiscoveryEvent())

            await BluetoothScaleSetupStoreTestFixtures.waitUntil {
                store.bluetoothConnectionState == .failure
            }

            #expect(store.bluetoothConnectionState == .failure)
            #expect(harness.scaleService.createBluetoothScaleCalls == 0)
        }
    }
}
