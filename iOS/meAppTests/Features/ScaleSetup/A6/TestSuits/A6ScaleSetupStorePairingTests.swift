import Foundation
@testable import meApp
import Testing

extension A6ScaleSetupStoreTests {
    @Suite("Pairing And Discovery")
    @MainActor
    struct PairingAndDiscovery {
        @Test("entering wake up step starts scan and subscribes to discovery")
        func enteringWakeUpStartsScan() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            await A6ScaleSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForPairingCalls == 1
            }

            #expect(harness.bluetooth.scanForPairingCalls == 1)
        }

        @Test("new scale discovery moves to connectingBluetooth step")
        func newScaleDiscoveryMovesToConnecting() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(connectionTransitionDelayNs: 300_000_000)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent()
            )

            let reached = await A6ScaleSetupStoreTestFixtures.waitUntil {
                store.currentStep == .connectingBluetooth
            }
            #expect(reached == true)
        }

        @Test("non-LCBT discovery events are ignored")
        func nonLCBTDiscoveryEventsAreIgnored() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(pairingTimeoutNs: 40_000_000)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(setupType: .bluetooth)
            )

            await A6ScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
                store.connectionState == .failure
            }

            #expect(store.connectionState == .failure)
        }

        @Test("wake-up timeout transitions to failure when no device discovered")
        func wakeUpTimeoutMovesToFailure() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(pairingTimeoutNs: 30_000_000)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index

            await A6ScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
                store.connectionState == .failure
            }

            #expect(store.connectionState == .failure)
        }

        @Test("known scale discovery shows alert instead of advancing")
        func knownScaleDiscoveryShowsAlert() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(isNew: false)
            )

            await A6ScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls == 1
            }

            #expect(harness.notification.showAlertCalls == 1)
            #expect(store.currentStep == .wakeUp)
        }

        @Test("known scale alert exit button cleans up and dismisses")
        func knownScaleAlertExitDismisses() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            var dismissCalls = 0
            store.dismissAction = { dismissCalls += 1 }

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(isNew: false)
            )

            await A6ScaleSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls == 1
            }
            harness.notification.alertData?.buttons.first?.action(nil)

            #expect(dismissCalls == 1)
            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        @Test("discovery event during non-wakeUp step is ignored")
        func discoveryDuringNonWakeUpIsIgnored() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.intro.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent()
            )

            try? await Task.sleep(nanoseconds: 50_000_000)
            #expect(store.currentStep == .intro)
        }

        @Test("retrying pairing jumps back to wake up step")
        func retryPairingGoesToWakeUp() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(pairingTimeoutNs: 30_000_000)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            await A6ScaleSetupStoreTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) {
                store.connectionState == .failure
            }

            #expect(store.currentStep == .connectingBluetooth)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            await A6ScaleSetupStoreTestFixtures.waitUntil {
                harness.bluetooth.scanForPairingCalls >= 2
            }
            #expect(harness.bluetooth.scanForPairingCalls >= 2)
        }
    }
}
