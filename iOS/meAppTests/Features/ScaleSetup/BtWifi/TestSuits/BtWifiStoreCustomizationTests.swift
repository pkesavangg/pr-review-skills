import Combine
import Foundation
@testable import meApp
import Testing

extension BtWifiStoreTests {
    @Suite("Customization")
    @MainActor
    struct Customization {
        @Test("setCustomizationPage for username preloads saved preference, user list, and navigates to view settings")
        func setCustomizationPageUsernamePreloadsAndNavigates() async {
            let bluetooth = MockBluetoothService()
            bluetooth.getScaleUserListResult = .success([
                DeviceUser(name: "Current", token: "token-1", lastActive: 1, isBodyMetricsEnabled: true),
                DeviceUser(name: "Existing", token: "token-2", lastActive: 2, isBodyMetricsEnabled: false)
            ])
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScale(token: "token-1")
            scaleService.fetchAttachedPreferenceResult = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved Name"),
                scaleId: scale.id
            )
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale.toSnapshot()
            store.discoveredScale = scale
            store.navigateToStep(.customizeSettings)

            store.setCustomizationPage(.scaleUsername)

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .viewSettings &&
                    store.userNameForm.displayName.value == "Saved Name" &&
                    store.userList.map(\.name) == ["Existing"]
            }

            #expect(store.currentCustomizeSetting == .scaleUsername)
            #expect(store.initialDisplayNameSnapshot == "Saved Name")
            #expect(store.userNameForm.currentUserName == "Saved Name")
            #expect(bluetooth.getScaleUserListCalls == 1)
        }

        @Test("setCustomizationPage for scale metrics falls back to defaults and preserves saved snapshot on re-entry")
        func setCustomizationPageScaleMetricsFallbackIsStableOnReentry() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.savedDeviceMetricsSnapshot = ["saved-metric"]
            store.navigateToStep(.customizeSettings)

            store.setCustomizationPage(.scaleMetrics)

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .viewSettings &&
                    store.selectedDeviceMetrics == DeviceMetrics.defaultMetricsKeys
            }

            #expect(store.initialDeviceMetricsSnapshot == DeviceMetrics.defaultMetricsKeys)
            #expect(store.savedDeviceMetricsSnapshot == ["saved-metric"])

            store.navigateToStep(.customizeSettings)
            store.setCustomizationPage(.scaleMetrics)

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .viewSettings &&
                    store.selectedDeviceMetrics == DeviceMetrics.defaultMetricsKeys
            }

            #expect(store.savedDeviceMetricsSnapshot == ["saved-metric"])
        }

        @Test("handleScaleModeChange updates state and next button reflects changed versus reverted values")
        func handleScaleModeChangeUpdatesValidationState() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMode
            store.initialDeviceModeSnapshot = .allBodyMetrics
            store.initialHeartRateEnabledSnapshot = false
            store.selectedDeviceMode = .allBodyMetrics
            store.isHeartRateEnabled = false
            store.updateNextEnabled()

            #expect(store.isNextEnabled == false)

            store.handleScaleModeChange(.weightOnly, heartRateEnabled: true)
            #expect(store.selectedDeviceMode == .weightOnly)
            #expect(store.isHeartRateEnabled == true)
            #expect(store.isNextEnabled == true)

            store.handleScaleModeChange(.allBodyMetrics, heartRateEnabled: false)
            #expect(store.isNextEnabled == false)
        }

        @Test("invalid username save does not persist changes or mark customization as saved")
        func invalidUsernameSaveDoesNotPersist() async {
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            let attached = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Original"),
                scaleId: scale.id
            )
            scaleService.fetchAttachedPreferenceResult = attached
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleUsername
            store.userNameForm.setDisplayName("")

            store.performViewSettingsSave()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none
            }

            #expect(attached.displayName == "Original")
            #expect(store.hasCustomizeChanges == false)
            #expect(store.hasSavedSettings == false)
            #expect(store.currentStep == .customizeSettings)
        }

        @Test("back from username customization restores the snapshot and clears current setting")
        func backFromUsernameCustomizationRestoresOriginalValue() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleUsername
            store.initialDisplayNameSnapshot = "Lakshmi"
            store.userNameForm.setDisplayName("Changed")

            store.handleBackButtonClick()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none && store.currentStep == .customizeSettings
            }

            #expect(store.userNameForm.displayName.value == "Lakshmi")
        }

        @Test("repeated scale metrics saves preserve the latest snapshot without duplicating selection state")
        func repeatedScaleMetricsSavesRemainStable() async {
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            scaleService.fetchAttachedPreferenceResult = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved"),
                scaleId: scale.id
            )
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store
            let metricsKey = CustomizeSettingsItem.scaleMetrics.rawValue

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMetrics
            store.selectedDeviceMetrics = ["weight"]

            store.performViewSettingsSave()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none &&
                    store.savedDeviceMetricsSnapshot == ["weight"]
            }

            #expect(store.selectedCustomizeItems == [metricsKey])
            #expect(store.hasSavedSettings == true)

            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMetrics
            store.selectedDeviceMetrics = ["weight", "heartRate"]

            store.performViewSettingsSave()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none &&
                    store.savedDeviceMetricsSnapshot == ["weight", "heartRate"]
            }

            #expect(store.selectedCustomizeItems == [metricsKey])
            #expect(store.savedDeviceMetricsSnapshot == ["weight", "heartRate"])
        }

        @Test("customization helper actions track visited items and present both info modals")
        func customizationHelpersTrackSelectionAndPresentModals() {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            store.addSelectedCustomizeItem(CustomizeSettingsItem.userName.rawValue)
            store.addSelectedCustomizeItem(CustomizeSettingsItem.userName.rawValue)
            store.showAccuCheckInfoModal()
            store.openBIAModel()

            #expect(store.isCustomizeItemSelected(CustomizeSettingsItem.userName.rawValue) == true)
            #expect(store.visitedCustomizeItems.count == 1)
            #expect(harness.notification.showModalCalls == 2)
            #expect(harness.notification.modalViewData.count == 2)
        }

        @Test("updateCustomizeSettings without a saved scale fails fast")
        func updateCustomizeSettingsWithoutSavedScaleFailsFast() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store

            await store.updateCustomizeSettings()

            #expect(store.scaleSetupError == .updateSettingsFailed)
            #expect(harness.bluetooth.updateAccountCalls == 0)
            #expect(harness.scaleService.pushLocalChangesToServerCalls == 0)
        }

        @Test("updateCustomizeSettings success builds preference from customization state, updates dependencies, and navigates")
        func updateCustomizeSettingsSuccessNavigatesAndResetsState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.updateAccountResult = .success(.creationCompleted)
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.selectedCustomizeItems = [
                CustomizeSettingsItem.userName.rawValue,
                CustomizeSettingsItem.deviceModes.rawValue,
                CustomizeSettingsItem.scaleMetrics.rawValue
            ]
            store.hasCustomizeChanges = true
            store.hasSavedSettings = true
            store.userNameForm.setDisplayName("")
            store.selectedDeviceMode = .weightOnly
            store.isHeartRateEnabled = true
            store.selectedDeviceMetrics = ["weight", "heartRate"]

            await store.updateCustomizeSettings()

            await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 3_500_000_000) {
                store.currentStep == .stepOn &&
                    bluetooth.syncDevicesCalls == 1 &&
                    store.selectedCustomizeItems.isEmpty
            }

            #expect(scaleService.updateScalePreferenceCalls == 2)
            #expect(scaleService.pushLocalChangesToServerCalls == 1)
            #expect(bluetooth.updateAccountCalls == 1)
            #expect(bluetooth.lastUpdateAccountBroadcastId == scale.broadcastIdString)
            #expect(store.scaleSetupError == .none)
            #expect(store.hasCustomizeChanges == false)
            #expect(store.hasSavedSettings == false)
        }

        @Test("updateCustomizeSettings local preference update failure sets error and skips bluetooth update")
        func updateCustomizeSettingsLocalFailureSetsError() async {
            let scaleService = MockScaleService()
            scaleService.updateScalePreferenceError = ScaleTestError.localFailure
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            store.selectedCustomizeItems = [CustomizeSettingsItem.scaleMetrics.rawValue]
            store.selectedDeviceMetrics = ["weight"]

            await store.updateCustomizeSettings()

            await BtWifiStoreTestFixtures.waitUntil {
                store.scaleSetupError == .updateSettingsFailed
            }

            #expect(scaleService.updateScalePreferenceCalls == 1)
            #expect(harness.bluetooth.updateAccountCalls == 0)
            #expect(store.currentStep != .stepOn)
        }

        @Test("updateCustomizeSettings bluetooth account failure leaves flow recoverable without navigating forward")
        func updateCustomizeSettingsBluetoothFailureLeavesRecoverableState() async {
            let bluetooth = MockBluetoothService()
            bluetooth.updateAccountResult = .failure(.notImplemented)
            let scaleService = MockScaleService()
            let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            store.selectedCustomizeItems = [
                CustomizeSettingsItem.deviceModes.rawValue,
                CustomizeSettingsItem.scaleMetrics.rawValue
            ]
            store.selectedDeviceMode = .weightOnly
            store.isHeartRateEnabled = true
            store.selectedDeviceMetrics = ["weight", "heartRate"]
            store.hasCustomizeChanges = true
            store.hasSavedSettings = true

            await store.updateCustomizeSettings()

            await BtWifiStoreTestFixtures.waitUntil {
                store.scaleSetupError == .updateSettingsFailed
            }

            #expect(scaleService.updateScalePreferenceCalls == 1)
            #expect(scaleService.pushLocalChangesToServerCalls == 1)
            #expect(bluetooth.updateAccountCalls == 1)
            #expect(store.currentStep != .stepOn)
            #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.deviceModes.rawValue))
            #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.scaleMetrics.rawValue))
            #expect(store.hasCustomizeChanges == true)
            #expect(store.hasSavedSettings == true)
        }

        @Test("setCustomizationPage for scale mode preloads attached preference and navigates")
        func setCustomizationPageScaleModePreloadsAttachedPreference() async {
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            let attached = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved"),
                scaleId: scale.id
            )
            attached.shouldMeasureImpedance = false
            attached.shouldMeasurePulse = true
            scaleService.fetchAttachedPreferenceResult = attached
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.navigateToStep(.customizeSettings)

            store.setCustomizationPage(.scaleMode)

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .viewSettings &&
                    store.selectedDeviceMode == .weightOnly &&
                    store.isHeartRateEnabled == true
            }

            #expect(store.currentCustomizeSetting == .scaleMode)
        }

        @Test("setCustomizationPage for scale metrics loads attached preference and records initial saved snapshot")
        func setCustomizationPageScaleMetricsLoadsAttachedPreference() async {
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            let attached = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved"),
                scaleId: scale.id
            )
            attached.displayMetrics = ["weight", "bodyFat"]
            scaleService.fetchAttachedPreferenceResult = attached
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.navigateToStep(.customizeSettings)

            store.setCustomizationPage(.scaleMetrics)

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentStep == .viewSettings && store.selectedDeviceMetrics == ["weight", "bodyFat"]
            }

            #expect(store.initialDeviceMetricsSnapshot == ["weight", "bodyFat"])
            #expect(store.savedDeviceMetricsSnapshot == ["weight", "bodyFat"])
        }

        @Test("valid username save updates attached preference and returns to customize settings")
        func validUsernameSavePersistsAttachedPreference() async {
            let scaleService = MockScaleService()
            let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
            let attached = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Original"),
                scaleId: scale.id
            )
            scaleService.fetchAttachedPreferenceResult = attached
            let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
            let store = harness.store

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleUsername
            store.userNameForm.setDisplayName("UpdatedName")

            store.performViewSettingsSave()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none
            }

            #expect(attached.displayName == "UpdatedName")
            #expect(store.hasCustomizeChanges == true)
            #expect(store.hasSavedSettings == true)
            #expect(store.currentStep == .customizeSettings)
        }

        @Test("scale mode save updates attached preference and back restores saved preference fallback")
        func scaleModeSaveAndBackRestoreSavedPreferenceFallback() async {
            let harness = BtWifiStoreTestFixtures.makeSUT()
            let store = harness.store
            let scale = BtWifiStoreTestFixtures.makeScale()
            let attached = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved"),
                scaleId: scale.id
            )
            attached.shouldMeasureImpedance = false
            attached.shouldMeasurePulse = true
            scale.r4ScalePreference = attached
            harness.scaleService.fetchAttachedPreferenceResult = attached

            store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
            store.savedScale = scale.toSnapshot()
            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMode
            store.selectedDeviceMode = .allBodyMetrics
            store.isHeartRateEnabled = false

            store.performViewSettingsSave()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none
            }

            #expect(attached.shouldMeasureImpedance == true)
            #expect(attached.shouldMeasurePulse == false)

            store.navigateToStep(.viewSettings)
            store.currentCustomizeSetting = .scaleMode
            store.initialDeviceModeSnapshot = nil
            store.initialHeartRateEnabledSnapshot = nil
            store.selectedDeviceMode = .allBodyMetrics
            store.isHeartRateEnabled = false
            attached.shouldMeasureImpedance = false
            attached.shouldMeasurePulse = true

            store.handleBackButtonClick()

            await BtWifiStoreTestFixtures.waitUntil {
                store.currentCustomizeSetting == .none
            }

            #expect(store.selectedDeviceMode == .weightOnly)
            #expect(store.isHeartRateEnabled == true)
        }
    }
}
