import Foundation
import Testing
@testable import meApp

extension A6ScaleSetupStoreTests {
    @Suite("Connection And Scale Saving")
    @MainActor
    struct ConnectionAndScaleSaving {
        @Test("connecting bluetooth step with discovered scale saves and transitions to success")
        func connectingWithDiscoveredScaleSavesAndSucceeds() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent()
            )

            let reached = await A6ScaleSetupStoreTestFixtures.waitUntil {
                harness.scaleService.createA6ScaleCalls == 1 &&
                store.connectionState == .success
            }
            #expect(reached == true)
            #expect(harness.scaleService.createA6ScaleCalls == 1)
            #expect(store.connectionState == .success)
        }

        @Test("connecting bluetooth transitions to setupFinished after delay")
        func connectingTransitionsToFinished() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.currentStepIndex = A6ScaleSetupStep.wakeUp.index
            harness.bluetooth.deviceDiscoveredSubject.send(
                A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent()
            )

            let reached = await A6ScaleSetupStoreTestFixtures.waitUntil {
                store.currentStep == .setupFinished
            }
            #expect(reached == true)
        }

        @Test("saveDiscoveredScale with no discovery context logs error and returns")
        func saveWithNoContextReturns() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.testSetInternalState(discoveredScale: nil, discoveryEvent: nil)

            await store.testSaveDiscoveredScale()

            #expect(harness.scaleService.createA6ScaleCalls == 0)
        }

        @Test("saveDiscoveredScale with no active account returns without saving")
        func saveWithNoAccountReturns() async {
            let account = MockAccountService()
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(account: account)
            let store = harness.store
            harness.account.activeAccount = nil

            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            let event = A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: device)
            store.testSetInternalState(discoveredScale: device, discoveryEvent: event)

            await store.testSaveDiscoveredScale()

            #expect(harness.scaleService.createA6ScaleCalls == 0)
        }

        @Test("saveDiscoveredScale success clears setup flag and syncs")
        func saveSuccessClearsSetupFlag() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            harness.bluetooth.isSetupInProgress = true

            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            let event = A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: device)
            store.testSetInternalState(discoveredScale: device, discoveryEvent: event)

            await store.testSaveDiscoveredScale()

            #expect(harness.scaleService.createA6ScaleCalls == 1)
            #expect(harness.scaleService.syncAllScalesWithRemoteCalls == 1)
            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        @Test("saveDiscoveredScale failure shows toast and clears setup flag")
        func saveFailureShowsToastAndClearsFlag() async {
            let scaleService = MockScaleService()
            scaleService.createA6ScaleError = NSError(domain: "test", code: 1)
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            harness.bluetooth.isSetupInProgress = true

            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            let event = A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: device)
            store.testSetInternalState(discoveredScale: device, discoveryEvent: event)

            await store.testSaveDiscoveredScale()

            #expect(harness.notification.showToastCalls == 1)
            #expect(harness.bluetooth.isSetupInProgress == false)
        }

        @Test("saveDiscoveredScale preserves original SKU from configure")
        func savePreservesOriginalSku() async {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0022")

            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            let event = A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: device)
            store.testSetInternalState(discoveredScale: device, discoveryEvent: event)

            await store.testSaveDiscoveredScale()

            #expect(harness.scaleService.lastCreatedA6Scale?.sku == "0022")
        }

        @Test("configure with discoveredScale starts at connectingBluetooth")
        func configureWithDiscoveredScaleStartsAtConnecting() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            let device = A6ScaleSetupStoreTestFixtures.makeA6Device()
            let event = A6ScaleSetupStoreTestFixtures.makeDiscoveryEvent(scale: device)

            store.configure(with: "0022", discoveredScale: device, discoveryEvent: event)

            #expect(store.currentStep == .connectingBluetooth)
        }

        @Test("configure without discoveredScale starts at intro")
        func configureWithoutDiscoveredScaleStartsAtIntro() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: "0022")

            #expect(store.currentStep == .intro)
        }
    }
}
