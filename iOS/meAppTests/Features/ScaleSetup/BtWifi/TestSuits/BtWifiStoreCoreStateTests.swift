import Foundation
import SwiftUI
import Testing
import UIKit
@testable import meApp

extension BtWifiStoreTests {
    @Suite("Store Core")
    @MainActor
    struct StoreCore {
        @Test("stepViews is empty until store is configured with a scale item")
        func stepViewsIsEmptyUntilConfigured() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            #expect(store.stepViews.isEmpty)

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("computed mode flags and settings context reflect current state")
        func computedModeFlagsAndSettingsContextReflectState() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            #expect(store.isWifiSetupOnlyMode == false)
            #expect(store.isReconnectMode == false)
            #expect(store.isDuplicatedMode == false)
            #expect(store.isSettingsContext == false)

            store.isWifiSetupOnly = true
            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            #expect(store.isSettingsWifiSetup == true)
            #expect(store.isSettingsContext == true)

            store.isReconnect = true
            #expect(store.isReconnectMode == true)
            #expect(store.isSettingsContext == false)

            store.isDuplicated = true
            #expect(store.isDuplicatedMode == true)
            #expect(store.isSettingsContext == false)
        }

        @Test("wifi password validity tracks password requirements")
        func wifiPasswordValidityTracksPasswordRequirements() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.networkForm.networkHasNoPassword = false
            store.networkForm.setPassword("")
            #expect(store.isFormValid == false)

            store.networkForm.setPassword("secret")
            #expect(store.isFormValid == true)

            store.networkForm.networkHasNoPassword = true
            store.networkForm.setPassword("")
            #expect(store.isFormValid == true)
        }

        @Test("stepViews covers gathering network branches")
        func stepViewsCoversGatheringNetworkBranches() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()

            store.scaleSetupError = .maxUserReached
            #expect(store.stepViews.count == store.steps.count)

            store.scaleSetupError = .duplicatesFound
            #expect(store.stepViews.count == store.steps.count)

            store.scaleSetupError = .none
            store.connectionState = .noNetworks
            #expect(store.stepViews.count == store.steps.count)

            store.connectionState = .loading
            #expect(store.stepViews.count == store.steps.count)

            store.savedScale = nil
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("stepViews covers available wifi list branches")
        func stepViewsCoversAvailableWifiListBranches() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            store.isWifiSetupOnly = true
            #expect(store.stepViews.count == store.steps.count)

            store.isWifiSetupOnly = false
            store.isExiting = true
            #expect(store.stepViews.count == store.steps.count)

            store.isExiting = false
            store.scaleSetupError = .noNetworkFound
            #expect(store.stepViews.count == store.steps.count)

            store.scaleSetupError = .wifiConnectionFailed
            #expect(store.stepViews.count == store.steps.count)

            store.scaleSetupError = .none
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("stepViews covers wifi password and connecting wifi branches")
        func stepViewsCoversWifiPasswordAndConnectingWifiBranches() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            store.selectedWifiNetwork = nil
            #expect(store.stepViews.count == store.steps.count)

            store.selectedWifiNetwork = WifiDetails(macAddress: "AA", ssid: "Home", rssi: -40)
            #expect(store.stepViews.count == store.steps.count)

            store.connectionState = .failure
            store.errorCode = "104"
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("stepViews covers view settings variants without dashboard section")
        func stepViewsCoversViewSettingsVariants() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            store.currentCustomizeSetting = .none
            #expect(store.stepViews.count == store.steps.count)

            store.currentCustomizeSetting = .scaleUsername
            #expect(store.stepViews.count == store.steps.count)

            store.currentCustomizeSetting = .scaleMode
            #expect(store.stepViews.count == store.steps.count)

            store.currentCustomizeSetting = .scaleMetrics
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("stepViews covers update settings and measurement error states")
        func stepViewsCoversUpdateSettingsAndMeasurementErrorStates() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            store.scaleSetupError = .updateSettingsFailed
            #expect(store.stepViews.count == store.steps.count)

            store.scaleSetupError = .none
            #expect(store.stepViews.count == store.steps.count)

            store.isExitingFromStepOn = true
            #expect(store.stepViews.count == store.steps.count)

            store.isExitingFromStepOn = false
            store.scaleSetupError = .collectMeasurementFailed
            #expect(store.stepViews.count == store.steps.count)

            store.scaleSetupError = .none
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("connecting bluetooth state prevents no network and failure overrides")
        func connectingBluetoothStatePreventsInvalidConnectionOverrides() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.connectingBluetooth)

            store.connectionState = .noNetworks
            #expect(store.connectionState == .loading)

            store.connectionState = .success
            store.connectionState = .failure
            #expect(store.connectionState == .success)
        }

        @Test("step index change is reverted while exiting")
        func stepIndexChangeIsRevertedWhileExiting() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            let originalIndex = store.currentStepIndex
            let nextIndex = min(originalIndex + 1, store.steps.count - 1)

            store.isExiting = true
            store.currentStepIndex = nextIndex

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStepIndex == originalIndex
            }

            #expect(store.currentStepIndex == originalIndex)
        }

        @Test("exit cancellation path clears revert flag without changing step index")
        func exitCancellationPathClearsRevertFlagWithoutChangingStepIndex() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

            let originalIndex = store.currentStepIndex
            let nextIndex = min(originalIndex + 1, store.steps.count - 1)

            store.isExiting = true
            store.currentStepIndex = nextIndex
            store.isExiting = false

            await BtWifiStoreTestFixtures.waitUntil {
                store.isRevertingStepIndex == false
            }

            #expect(store.currentStepIndex == nextIndex)
            #expect(store.isRevertingStepIndex == false)
        }

        @Test("view settings dashboard metrics branch initializes dashboard store")
        func viewSettingsDashboardMetricsBranchInitializesDashboardStore() {
            let harness = BtWifiStoreTestFixtures.makeSUT(
                dashboardStoreFactory: { DashboardStore(lightweight: true) }
            )
            let store = harness.store

            TestDependencyContainer.registerDashboardConcreteDependencies()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.currentCustomizeSetting = .dashboardMetrics

            let viewSettingsIndex = store.steps.firstIndex(of: .viewSettings)
            #expect(viewSettingsIndex != nil)

            _ = store.dashboardStore
            let view = store.stepViews[viewSettingsIndex!] // swiftlint:disable:this force_unwrapping
            let host = UIHostingController(rootView: view)
            _ = host.view
        }

        @Test("store can initialize using default dashboard store factory parameter")
        func storeCanInitializeUsingDefaultDashboardStoreFactoryParameter() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            _ = BtWifiScaleSetupStore(
                bluetoothSetupManager: harness.bluetoothSetupManager,
                networkMonitor: harness.networkMonitor
            )
        }
    }
}
