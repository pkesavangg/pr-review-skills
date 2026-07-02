import Combine
import Foundation
@testable import meApp
import Testing

extension BtWifiStoreTests.Customization {
    @Test("back from scale metrics restores the last saved metric snapshot")
    func backFromScaleMetricsRestoresSavedSnapshot() async {
        let harness = BtWifiStoreTestFixtures.makeSUT()
        let store = harness.store

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .scaleMetrics
        store.savedDeviceMetricsSnapshot = ["weight", "bodyFat"]
        store.selectedDeviceMetrics = ["weight"]

        store.handleBackButtonClick()

        await BtWifiStoreTestFixtures.waitUntil {
            store.currentCustomizeSetting == .none && store.currentStep == .customizeSettings
        }

        #expect(store.selectedDeviceMetrics == ["weight", "bodyFat"])
    }

    @Test("setupScaleUsernameForm skips re-fetch when user list already exists and leaves matching display name stable")
    func setupScaleUsernameFormSkipsFetchWhenUserListAlreadyPresent() async {
        let scaleService = MockScaleService()
        let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
        let attached = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Lakshmi"),
            scaleId: scale.id
        )
        scaleService.fetchAttachedPreferenceResult = attached
        let harness = BtWifiStoreTestFixtures.makeSUT(scaleService: scaleService)
        let store = harness.store

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.savedScale = scale
        store.userList = [DeviceUser(name: "Existing", token: "token-1", lastActive: 1, isBodyMetricsEnabled: true)]

        store.setupScaleUsernameForm()

        await BtWifiStoreTestFixtures.waitUntil {
            store.userNameForm.displayName.value == "Lakshmi" && store.userNameForm.userList.count == 1
        }

        #expect(harness.bluetooth.getScaleUserListCalls == 0)
        #expect(store.initialDisplayNameSnapshot == "Lakshmi")
        #expect(store.userNameForm.currentUserName == "Lakshmi")
    }

    @Test("updateCustomizeSettings with attached preference preserves untouched fields")
    func updateCustomizeSettingsPreservesUntouchedAttachedFields() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .success(.creationCompleted)
        let scaleService = MockScaleService()
        let scale = BtWifiStoreTestFixtures.makeScaleSnapshot()
        let attached = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: scale.id, displayName: "Saved Name"),
            scaleId: scale.id
        )
        attached.displayMetrics = ["bodyFat", "water"]
        attached.shouldMeasureImpedance = true
        attached.shouldMeasurePulse = false
        scaleService.fetchAttachedPreferenceResult = attached
        let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
        let store = harness.store

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.savedScale = scale
        store.selectedCustomizeItems = [CustomizeSettingsItem.deviceModes.rawValue]
        store.userNameForm.setDisplayName("Ignored")
        store.selectedDeviceMode = .weightOnly
        store.isHeartRateEnabled = true
        store.selectedDeviceMetrics = ["weight"]

        await store.updateCustomizeSettings()

        await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 3_500_000_000) {
            store.currentStep == .stepOn && bluetooth.updateAccountCalls == 1
        }

        #expect(bluetooth.lastUpdateAccountBroadcastId == scale.broadcastIdString)
    }

    @Test("updateCustomizeSettings handles delayed local apply failure after bluetooth success")
    func updateCustomizeSettingsDelayedLocalApplyFailureSetsError() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .success(.creationCompleted)
        let scaleService = MockScaleService()
        scaleService.updateScalePreferenceErrorsByCall = [2: ScaleTestError.localFailure]
        let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
        let store = harness.store

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot()
        store.selectedCustomizeItems = [CustomizeSettingsItem.scaleMetrics.rawValue]
        store.selectedDeviceMetrics = ["weight"]

        await store.updateCustomizeSettings()

        await BtWifiStoreTestFixtures.waitUntil {
            store.scaleSetupError == .updateSettingsFailed
        }

        #expect(scaleService.updateScalePreferenceCalls == 2)
        #expect(bluetooth.updateAccountCalls == 1)
        #expect(store.currentStep != .stepOn)
        #expect(store.hasCustomizeChanges == false)
    }

    @Test("checkGoalModalAfterSetup requests entry count and forwards it to goal alert service")
    func checkGoalModalAfterSetupUsesEntryCount() async {
        let entryService = MockEntryService()
        entryService.getEntryCountResult = .success(3)
        let goalAlert = MockGoalAlertService()
        let harness = BtWifiStoreTestFixtures.makeSUT(entryService: entryService, goalAlertService: goalAlert)

        harness.store.checkGoalModalAfterSetup()

        await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 2_200_000_000) {
            goalAlert.checkSetGoalCardCalls == 1
        }

        #expect(entryService.getEntryCountCalls == 1)
        #expect(goalAlert.lastEntryCount == 3)
    }

    @Test("resumeScanningAndSyncDevices resumes scan and syncs devices on success")
    func resumeScanningAndSyncDevicesSuccess() async {
        let harness = BtWifiStoreTestFixtures.makeSUT()

        await harness.store.resumeScanningAndSyncDevices()

        #expect(harness.bluetooth.resumeSmartScanCalls == 1)
        #expect(harness.bluetooth.lastResumeClearOnlyPairing == false)
        #expect(harness.scaleService.updateAllScalesStatusCalls == 1)
        #expect(harness.bluetooth.syncDevicesCalls == 1)
    }

    @Test("resumeScanningAndSyncDevices suppresses sync when scale status refresh fails")
    func resumeScanningAndSyncDevicesFailure() async {
        let harness = BtWifiStoreTestFixtures.makeSUT()
        harness.scaleService.updateAllScalesStatusError = ScaleTestError.localFailure

        await harness.store.resumeScanningAndSyncDevices()

        #expect(harness.bluetooth.resumeSmartScanCalls == 1)
        #expect(harness.scaleService.updateAllScalesStatusCalls == 1)
        #expect(harness.bluetooth.syncDevicesCalls == 0)
    }

    @Test("disconnectDevice only disconnects unsaved discovered scales with a broadcast id")
    func disconnectDeviceGuardsAndValidPath() async {
        let harness = BtWifiStoreTestFixtures.makeSUT()
        let store = harness.store

        store.disconnectDevice()
        #expect(harness.bluetoothSetupManager.disconnectIfNeededCalls == 0)

        let savedScale = BtWifiStoreTestFixtures.makeScale(id: "saved-scale")
        let discoveredScale = BtWifiStoreTestFixtures.makeScale(id: "temp-scale")
        discoveredScale.broadcastIdString = "broadcast-1"
        store.discoveredScale = discoveredScale
        store.savedScale = savedScale.toSnapshot()

        store.disconnectDevice()
        #expect(harness.bluetoothSetupManager.disconnectIfNeededCalls == 0)

        store.savedScale = nil
        store.disconnectDevice()

        await BtWifiStoreTestFixtures.waitUntil {
            harness.bluetoothSetupManager.disconnectIfNeededCalls == 1
        }

        #expect(harness.bluetoothSetupManager.lastDisconnectedBroadcastId == "broadcast-1")
        #expect(harness.bluetoothSetupManager.lastDisconnectConsiderForSession == true)
    }

    @Test("cleanup cancels tasks, clears references, and re-enables bluetooth scanning state")
    func cleanupClearsStateAndSubscriptions() async {
        let harness = BtWifiStoreTestFixtures.makeSUT()
        let store = harness.store

        store.dismissAction = {}
        store.fetchWifiNetworksTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
        store.deviceDiscoveryCancellable = PassthroughSubject<Int, Never>().sink { _ in }
        store.networkFormCancellable = PassthroughSubject<Int, Never>().sink { _ in }
        store.newEntrySubscription = PassthroughSubject<Int, Never>().sink { _ in }
        store.liveMeasurementSubscription = PassthroughSubject<Int, Never>().sink { _ in }
        store.measurementTimeoutTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
        store.stepOnTimeoutTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
        store.stepTimerTask = Task { try? await Task.sleep(nanoseconds: 5_000_000_000) }
        store.dashboardStoreCancellable = PassthroughSubject<Int, Never>().sink { _ in }
        store.dashboardMetricsUpdatedCancellable = PassthroughSubject<Int, Never>().sink { _ in }
        store.cancellables = [PassthroughSubject<Int, Never>().sink { _ in }]
        store.discoveredScale = BtWifiStoreTestFixtures.makeScale(id: "discovered")
        store.discoveryEvent = BtWifiStoreTestFixtures.makeDiscoveryEvent()
        store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot(id: "saved")
        harness.bluetooth.isSetupInProgress = true
        store.isExiting = true
        store.isExitingFromStepOn = true

        store.cleanup()

        await BtWifiStoreTestFixtures.waitUntil {
            store.isExiting == false && store.isExitingFromStepOn == false
        }

        #expect(store.dismissAction == nil)
        #expect(store.fetchWifiNetworksTask == nil)
        #expect(store.deviceDiscoveryCancellable == nil)
        #expect(store.networkFormCancellable == nil)
        #expect(store.newEntrySubscription == nil)
        #expect(store.liveMeasurementSubscription == nil)
        #expect(store.measurementTimeoutTask == nil)
        #expect(store.stepOnTimeoutTask == nil)
        #expect(store.stepTimerTask == nil)
        #expect(store.dashboardStoreCancellable == nil)
        #expect(store.dashboardMetricsUpdatedCancellable == nil)
        #expect(store.cancellables.isEmpty)
        #expect(store.discoveredScale == nil)
        #expect(store.discoveryEvent == nil)
        #expect(store.savedScale == nil)
        #expect(harness.bluetooth.isSetupInProgress == false)
        #expect(harness.bluetooth.reapplySkipDevicesExcludingPairedCalls == 1)
    }

    @Test("dashboard snapshot, change detection, and discard keep customization state stable on re-entry")
    func dashboardSnapshotChangeDetectionAndDiscardAreStable() {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

        let dashboardStore = store.dashboardStore
        dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)

        store.snapshotDashboardState()
        #expect(store.hasDashboardCustomizationChanged() == false)

        let firstMetric = dashboardStore.metricsManager.state.metrics.removeFirst()
        dashboardStore.metricsManager.state.metrics.append(firstMetric)
        dashboardStore.state.ui.removedMetrics.insert(firstMetric.label)

        #expect(store.hasDashboardCustomizationChanged() == true)

        store.selectedCustomizeItems = [
            CustomizeSettingsItem.dashboardMetrics.rawValue,
            CustomizeSettingsItem.scaleMetrics.rawValue
        ]
        store.hasCustomizeChanges = true
        store.discardDashboardCustomization()

        #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue) == false)
        #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.scaleMetrics.rawValue) == true)
        #expect(store.hasCustomizeChanges == true)

        store.snapshotDashboardState()
        #expect(store.hasDashboardCustomizationChanged() == false)
    }

    @Test("dashboard save and back paths remain stable across repeated entry")
    func dashboardSaveAndBackPathsRemainStable() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        _ = store.dashboardStore

        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .dashboardMetrics
        store.dashboardStore.beginEdit()
        store.dashboardStore.state.ui.isEditMode = true
        let initialGridLayoutId = store.dashboardStore.state.ui.gridLayoutId

        store.performViewSettingsSave()

        await BtWifiStoreTestFixtures.waitUntil {
            store.currentCustomizeSetting == .none
        }

        #expect(store.hasSavedSettings == true)
        #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue))
        #expect(store.dashboardStore.state.ui.gridLayoutId != initialGridLayoutId)

        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .dashboardMetrics
        store.dashboardStore.beginEdit()
        store.dashboardStore.state.ui.isEditMode = true
        store.dashboardStore.state.ui.removedMetrics.insert(DashboardStrings.bmi)

        store.handleBackButtonClick()

        await BtWifiStoreTestFixtures.waitUntil {
            store.currentCustomizeSetting == .none
        }

        #expect(store.dashboardStore.state.ui.removedMetrics.contains(DashboardStrings.bmi) == false)
    }

    @Test("setupDashboardMetricsCustomization enters edit mode and snapshots current dashboard state")
    func setupDashboardMetricsCustomizationPreparesEditState() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .dashboardMetrics

        await store.setupDashboardMetricsCustomization()

        #expect(store.dashboardStore.state.ui.isEditMode == true)
        #expect(store.initialDashboardMetricLabelsSnapshot != nil)
        #expect(store.dashboardStoreCancellable != nil)
        #expect(store.dashboardMetricsUpdatedCancellable != nil)
    }

    @Test("dashboard type helpers remain stable for already-upgraded and dashboard4 states")
    func dashboardTypeHelpersRemainStable() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)

        store.dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)
        store.dashboardStore.metricsManager.updateDashboardType(.dashboard12)
        await store.upgradeDashboardTypeFrom4To12WithDefaults()
        #expect(store.dashboardStore.metricsManager.state.dashboardType == .dashboard12)

        harness.account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: harness.account.activeAccount?.accountId ?? "acct-1",
            email: "btwifi@example.com",
            isActiveAccount: true,
            dashboardType: "dashboard4"
        )
        store.dashboardStore.metricsManager.updateDashboardType(.dashboard4)
        #expect(store.isDashboardTypeFour == true)
    }

    @Test("persistDashboardMetricsIfNeeded skips when dashboard metrics were not selected")
    func persistDashboardMetricsIfNeededSkipsWithoutSelection() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.selectedCustomizeItems = [CustomizeSettingsItem.scaleMetrics.rawValue]

        store.persistDashboardMetricsIfNeeded()

        await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 200_000_000) { true }
        #expect(harness.account.refreshAccountCalls == 0)
    }

    @Test("persistDashboardMetrics failure path keeps protocol account refresh untouched")
    func persistDashboardMetricsFailureLeavesRefreshUntouched() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        let concrete = TestDependencyContainer.registerDashboardConcreteDependencies()
        concrete.account.activeAccount = BtWifiStoreTestFixtures.makeAccount()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)

        await store.persistDashboardMetrics()

        #expect(harness.account.refreshAccountCalls == 0)
    }

    @Test("setCustomizationPage default branch still navigates without preloading customization data")
    func setCustomizationPageDefaultBranchNavigatesSafely() async {
        let bluetooth = MockBluetoothService()
        let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth)
        let store = harness.store

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.navigateToStep(.customizeSettings)
        store.userNameForm.setDisplayName("KeepMe")
        store.selectedDeviceMetrics = ["weight"]

        store.setCustomizationPage(.none)

        await BtWifiStoreTestFixtures.waitUntil {
            store.currentStep == .viewSettings
        }

        #expect(store.currentCustomizeSetting == .none)
        #expect(store.userNameForm.displayName.value == "KeepMe")
        #expect(store.selectedDeviceMetrics == ["weight"])
        #expect(bluetooth.getScaleUserListCalls == 0)
    }

    @Test("updateCustomizeSettings without an attached preference builds a default payload and falls back to the account first name")
    func updateCustomizeSettingsWithoutAttachedPreferenceBuildsDefaultPayload() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .failure(.notImplemented)
        let scaleService = MockScaleService()
        scaleService.fetchAttachedPreferenceResult = nil
        let harness = BtWifiStoreTestFixtures.makeSUT(bluetooth: bluetooth, scaleService: scaleService)
        let store = harness.store

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        store.savedScale = BtWifiStoreTestFixtures.makeScaleSnapshot(id: "scale-default-pref")
        store.selectedCustomizeItems = [
            CustomizeSettingsItem.userName.rawValue,
            CustomizeSettingsItem.deviceModes.rawValue,
            CustomizeSettingsItem.scaleMetrics.rawValue
        ]
        store.userNameForm.setDisplayName("")
        store.selectedDeviceMode = .weightOnly
        store.isHeartRateEnabled = true
        store.selectedDeviceMetrics = ["weight", "heartRate"]

        await store.updateCustomizeSettings()

        await BtWifiStoreTestFixtures.waitUntil {
            store.scaleSetupError == .updateSettingsFailed
        }

        #expect(scaleService.updateScalePreferenceCalls == 1)
        #expect(scaleService.lastUpdatedScalePreference?.displayName == "Lakshmi")
        #expect(scaleService.lastUpdatedScalePreference?.displayMetrics == ["weight", "heartRate"])
        #expect(scaleService.lastUpdatedScalePreference?.shouldMeasureImpedance == false)
        #expect(scaleService.lastUpdatedScalePreference?.shouldMeasurePulse == true)
    }

    @Test("setupDashboardMetricsCustomization upgrades dashboard4 flows and enters edit mode")
    func setupDashboardMetricsCustomizationUpgradesDashboardFourFlow() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        _ = TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        harness.account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: harness.account.activeAccount?.accountId ?? "acct-1",
            email: "btwifi@example.com",
            isActiveAccount: true,
            dashboardType: "dashboard4"
        )
        _ = store.dashboardStore
        store.dashboardStore.metricsManager.updateDashboardType(.dashboard4)
        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .dashboardMetrics

        await store.setupDashboardMetricsCustomization()
        await BtWifiStoreTestFixtures.waitUntil {
            store.dashboardStore.metricsManager.state.dashboardType == .dashboard12 &&
                store.dashboardStore.state.ui.isEditMode
        }

        #expect(store.dashboardStore.metricsManager.state.dashboardType == .dashboard12)
        #expect(store.dashboardStore.state.metrics.dashboardType == .dashboard12)
        #expect(store.dashboardStore.metricsManager.state.metrics.isEmpty == false)
        #expect(store.initialDashboardMetricLabelsSnapshot?.isEmpty == false)
        #expect(harness.account.refreshAccountCalls >= 1)
    }

    @Test("setupDashboardMetricsCustomization loads dashboard metrics from API when local metrics are empty")
    func setupDashboardMetricsCustomizationLoadsMetricsFromAPIWhenEmpty() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        let concrete = TestDependencyContainer.registerDashboardConcreteDependencies()
        concrete.account.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardMetrics: "bmi,bodyFat,muscleMass,water",
            dashboardType: "dashboard12"
        )

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        _ = store.dashboardStore
        store.dashboardStore.metricsManager.state.metrics = []
        store.dashboardStore.metricsManager.state.activeMetricsCount = 0
        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .dashboardMetrics

        await store.setupDashboardMetricsCustomization()

        await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 2_000_000_000) {
            store.dashboardStore.metricsManager.state.metrics.isEmpty == false
        }

        let labels = store.dashboardStore.metricsManager.state.metrics.prefix(4).map(\.label)
        #expect(labels == [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.muscle,
            DashboardStrings.water
        ])
        #expect(store.dashboardStore.state.ui.isEditMode == true)
    }

    @Test("dashboard subscriptions react to edit-state changes and dashboard update notifications")
    func dashboardSubscriptionsReactToChangesAndNotifications() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        _ = TestDependencyContainer.registerDashboardConcreteDependencies()
        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        _ = store.dashboardStore
        store.dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)
        store.navigateToStep(.viewSettings)
        store.currentCustomizeSetting = .dashboardMetrics
        store.snapshotDashboardState()
        store.setupDashboardMetricsSubscriptions()
        store.updateNextEnabled()

        #expect(store.isNextEnabled == false)

        store.dashboardStore.state.ui.removedMetrics.insert(DashboardStrings.bmi)
        store.dashboardStore.objectWillChange.send()

        await BtWifiStoreTestFixtures.waitUntil {
            store.isNextEnabled == true
        }

        NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
        await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 200_000_000) { true }

        #expect(store.dashboardStoreCancellable != nil)
        #expect(store.dashboardMetricsUpdatedCancellable != nil)
    }

    @Test("persistDashboardMetricsIfNeeded schedules persistence when dashboard customization was selected")
    func persistDashboardMetricsIfNeededSelectedPathSchedulesPersistence() async {
        let harness = BtWifiStoreTestFixtures.makeSUT { DashboardStore(lightweight: true) }
        let store = harness.store

        let concrete = TestDependencyContainer.registerDashboardConcreteDependencies()
        concrete.account.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardMetrics: "bmi,bodyFat,muscleMass,water",
            dashboardType: "dashboard12"
        )

        store.configure(with: SettingsConstants.defaultR4Sku, isWifiSetupOnly: false)
        _ = store.dashboardStore
        store.dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)
        store.selectedCustomizeItems = [CustomizeSettingsItem.dashboardMetrics.rawValue]

        store.persistDashboardMetricsIfNeeded()

        await BtWifiStoreTestFixtures.waitUntil(timeoutNanoseconds: 300_000_000) { true }

        #expect(store.selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue))
    }
}
