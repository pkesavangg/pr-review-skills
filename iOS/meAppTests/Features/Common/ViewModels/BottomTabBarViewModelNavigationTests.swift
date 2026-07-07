//
//  BottomTabBarViewModelNavigationTests.swift
//  meAppTests
//
//  Covers the tab-navigation, settings-routing, device-setup, discovery-gating and
//  permission surface of BottomTabBarViewModel (complements the reading-card suite).
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BottomTabBarViewModelNavigationTests {

    // MARK: - Helpers

    // swiftlint:disable:next large_tuple
    private func makeSUT() -> (
        sut: BottomTabBarViewModel,
        bluetooth: MockBluetoothService,
        entry: MockEntryService,
        permissions: MockPermissionsService,
        logger: MockLoggerService
    ) {
        TestDependencyContainer.reset()
        let bluetooth = MockBluetoothService()
        let entry = MockEntryService()
        let permissions = MockPermissionsService()
        let logger = MockLoggerService()
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(entry as EntryServiceProtocol)
        DependencyContainer.shared.register(permissions)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        return (BottomTabBarViewModel(), bluetooth, entry, permissions, logger)
    }

    private func makeSnapshot(sku: String = "R4-001", broadcastId: String = "A1B2C3") -> DeviceSnapshot {
        ScaleTestFixtures.makeDevice(id: "scale-1", broadcastIdString: broadcastId, sku: sku)
            .toSnapshot(isConnected: true)
    }

    private func makeEvent(
        sku: String = "R4-001",
        setupType: DeviceSetupType = .btWifiR4,
        isNew: Bool = true,
        broadcastId: String = "A1B2C3"
    ) -> DeviceDiscoveryEvent {
        DeviceDiscoveryEvent(
            device: makeSnapshot(sku: sku, broadcastId: broadcastId),
            deviceInfo: DeviceItemInfo(
                productName: "R4 Scale", sku: sku, imgPath: "", setupType: setupType, bodyComp: true
            ),
            protocolType: .R4,
            isNew: isNew
        )
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    // MARK: - visibleTabs

    @Test("visibleTabs excludes appsync when showAppSync is false")
    func visibleTabsWithoutAppSync() {
        let (sut, _, _, _, _) = makeSUT()
        sut.showAppSync = false
        #expect(sut.visibleTabs == [.dash, .entry, .history, .settings])
    }

    @Test("visibleTabs appends appsync when showAppSync is true")
    func visibleTabsWithAppSync() {
        let (sut, _, _, _, _) = makeSUT()
        sut.showAppSync = true
        #expect(sut.visibleTabs == [.dash, .entry, .history, .settings, .appsync])
    }

    // MARK: - selectTab

    @Test("selectTab updates selectedTab and remembers the last non-appsync tab")
    func selectTabTracksPreviousNonAppSync() {
        let (sut, _, _, _, _) = makeSUT()

        sut.selectTab(.history)
        #expect(sut.selectedTab == .history)
        #expect(sut.previousNonAppSyncTab == .history)

        sut.selectTab(.appsync)
        #expect(sut.selectedTab == .appsync)
        // appsync must not overwrite the remembered non-appsync tab
        #expect(sut.previousNonAppSyncTab == .history)
    }

    @Test("selectTab away from settings clears the settings navigation source")
    func selectTabClearsSettingsSourceWhenLeaving() {
        let (sut, _, _, _, _) = makeSUT()
        sut.navigateToSettings(route: .goal, sourceTab: .history)
        #expect(sut.settingsNavigationSourceTab == .history)

        sut.selectTab(.entry)

        #expect(sut.settingsNavigationSourceTab == nil)
    }

    // MARK: - restorePreviousTab

    @Test("restorePreviousTab restores the last non-appsync selection")
    func restorePreviousTabRestores() {
        let (sut, _, _, _, _) = makeSUT()
        sut.selectTab(.history)
        sut.selectTab(.appsync)

        sut.restorePreviousTab()

        #expect(sut.selectedTab == .history)
    }

    // MARK: - navigateToSettings / goal

    @Test("navigateToSettings selects settings, records the source tab and pends the route")
    func navigateToSettingsSetsRouteAndSource() {
        let (sut, _, _, _, _) = makeSUT()
        sut.selectTab(.history)

        sut.navigateToSettings(route: .goal)

        #expect(sut.selectedTab == .settings)
        #expect(sut.settingsNavigationSourceTab == .history)
        #expect(sut.pendingSettingsNavigation == .goal)
    }

    @Test("navigateToSettings honors an explicit source tab")
    func navigateToSettingsExplicitSource() {
        let (sut, _, _, _, _) = makeSUT()

        sut.navigateToSettings(route: .goal, sourceTab: .entry)

        #expect(sut.settingsNavigationSourceTab == .entry)
    }

    @Test("navigateToGoalSetting routes to the goal settings screen")
    func navigateToGoalSettingRoutesToGoal() {
        let (sut, _, _, _, _) = makeSUT()

        sut.navigateToGoalSetting()

        #expect(sut.selectedTab == .settings)
        #expect(sut.pendingSettingsNavigation == .goal)
    }

    @Test("returnToSettingsSourceTab returns to the origin tab and clears the source")
    func returnToSettingsSourceTabReturnsAndClears() {
        let (sut, _, _, _, _) = makeSUT()
        sut.navigateToSettings(route: .goal, sourceTab: .history)

        sut.returnToSettingsSourceTab()

        #expect(sut.selectedTab == .history)
        #expect(sut.settingsNavigationSourceTab == nil)
    }

    @Test("returnToSettingsSourceTab is a no-op when no source is recorded")
    func returnToSettingsSourceTabNoOpWithoutSource() {
        let (sut, _, _, _, _) = makeSUT()
        sut.selectTab(.entry)

        sut.returnToSettingsSourceTab()

        #expect(sut.selectedTab == .entry)
    }

    @Test("clearSettingsNavigationSource resets the tracked source tab")
    func clearSettingsNavigationSourceResets() {
        let (sut, _, _, _, _) = makeSUT()
        sut.navigateToSettings(route: .goal, sourceTab: .history)

        sut.clearSettingsNavigationSource()

        #expect(sut.settingsNavigationSourceTab == nil)
    }

    // MARK: - assignPendingEntry

    @Test("assignPendingEntry is a no-op when there is no pending entry")
    func assignPendingEntryNoPending() async {
        let (sut, _, entry, _, _) = makeSUT()

        await sut.assignPendingEntry(to: "baby-1")

        #expect(entry.assignBabyEntryCalls == 0)
    }

    @Test("assignPendingEntry assigns the pending entry and clears the pending id")
    func assignPendingEntrySuccess() async {
        let (sut, _, entry, _, _) = makeSUT()
        sut.pendingBabyAssignmentEntryId = UUID()

        await sut.assignPendingEntry(to: "baby-1")

        #expect(entry.assignBabyEntryCalls == 1)
        #expect(entry.lastAssignedBabyId == "baby-1")
        #expect(sut.pendingBabyAssignmentEntryId == nil)
    }

    @Test("assignPendingEntry failure logs an error")
    func assignPendingEntryFailureLogs() async {
        let (sut, _, entry, _, logger) = makeSUT()
        entry.assignBabyEntryError = ScaleTestError.localFailure
        sut.pendingBabyAssignmentEntryId = UUID()

        await sut.assignPendingEntry(to: "baby-1")

        #expect(logger.entries.contains { $0.level == .error })
    }

    // MARK: - dismissDiscoveredScaleSheet

    @Test("dismissDiscoveredScaleSheet clears the discovered scale and event")
    func dismissDiscoveredScaleSheetClears() {
        let (sut, _, _, _, _) = makeSUT()
        sut.discoveredScale = makeSnapshot()
        sut.discoveryEvent = makeEvent()

        sut.dismissDiscoveredScaleSheet()

        #expect(sut.discoveredScale == nil)
        #expect(sut.discoveryEvent == nil)
    }

    // MARK: - Deactivation / reselect handlers

    @Test("registered deactivation handler is retrievable and removable")
    func deactivationHandlerLifecycle() async {
        let (sut, _, _, _, _) = makeSUT()
        sut.registerDeactivationHandler(for: .entry) { true }

        #expect(sut.deactivationHandler(for: .entry) != nil)
        let canLeave = await sut.deactivationHandler(for: .entry)?()
        #expect(canLeave == true)

        sut.removeDeactivationHandler(for: .entry)
        #expect(sut.deactivationHandler(for: .entry) == nil)
    }

    @Test("handleTabReselect invokes the registered reselect handler")
    func handleTabReselectInvokesHandler() {
        let (sut, _, _, _, _) = makeSUT()
        var reselected = 0
        sut.registerReselectHandler(for: .dash) { reselected += 1 }

        sut.handleTabReselect(.dash)

        #expect(reselected == 1)
    }

    @Test("handleTabReselect is a no-op when no handler is registered")
    func handleTabReselectNoHandler() {
        let (sut, _, _, _, _) = makeSUT()

        sut.handleTabReselect(.history)

        #expect(sut.selectedTab == .dash)
    }

    // MARK: - openDeviceSetup

    @Test("openDeviceSetup builds a setup payload and marks setup in progress for a supported type")
    func openDeviceSetupSupportedType() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        let event = makeEvent(setupType: .btWifiR4)

        sut.openDeviceSetup(scale: makeSnapshot(), event: event)

        #expect(sut.setupPayload != nil)
        #expect(bluetooth.isSetupInProgress == true)
        // the discovered sheet is dismissed once setup opens
        #expect(sut.discoveredScale == nil)
    }

    @Test("openDeviceSetup returns early when the sku is empty")
    func openDeviceSetupEmptySku() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        let event = makeEvent(sku: "")

        sut.openDeviceSetup(scale: makeSnapshot(sku: ""), event: event)

        #expect(sut.setupPayload == nil)
        #expect(bluetooth.isSetupInProgress == false)
    }

    // MARK: - shouldShowDiscoveredScale

    @Test("shouldShowDiscoveredScale returns true for a fresh valid discovery event")
    func shouldShowDiscoveredScaleTrue() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        bluetooth.canShowScaleDiscoveredModal = true
        bluetooth.isSetupInProgress = false

        #expect(sut.shouldShowDiscoveredScale(for: makeEvent()) == true)
    }

    @Test("shouldShowDiscoveredScale returns false while setup is in progress")
    func shouldShowDiscoveredScaleFalseDuringSetup() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        bluetooth.canShowScaleDiscoveredModal = true
        bluetooth.isSetupInProgress = true

        #expect(sut.shouldShowDiscoveredScale(for: makeEvent()) == false)
    }

    @Test("shouldShowDiscoveredScale returns false when the modal is not allowed")
    func shouldShowDiscoveredScaleFalseWhenNotAllowed() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        bluetooth.canShowScaleDiscoveredModal = false

        #expect(sut.shouldShowDiscoveredScale(for: makeEvent()) == false)
    }

    @Test("shouldShowDiscoveredScale returns false for a non-new event")
    func shouldShowDiscoveredScaleFalseForOldEvent() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        bluetooth.canShowScaleDiscoveredModal = true

        #expect(sut.shouldShowDiscoveredScale(for: makeEvent(isNew: false)) == false)
    }

    @Test("shouldShowDiscoveredScale returns false when the appsync tab is active")
    func shouldShowDiscoveredScaleFalseOnAppSyncTab() {
        let (sut, bluetooth, _, _, _) = makeSUT()
        bluetooth.canShowScaleDiscoveredModal = true
        sut.selectTab(.appsync)

        #expect(sut.shouldShowDiscoveredScale(for: makeEvent()) == false)
    }

    // MARK: - handleCameraPermission

    @Test("handleCameraPermission short-circuits when camera is already enabled")
    func handleCameraPermissionAlreadyEnabled() async {
        let (sut, _, _, permissions, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])

        let state = await sut.handleCameraPermission()

        #expect(state == .ENABLED)
        #expect(permissions.handlePermissionCalls.isEmpty)
    }

    @Test("handleCameraPermission requests permission when camera is not enabled")
    func handleCameraPermissionRequestsWhenDisabled() async {
        let (sut, _, _, permissions, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])
        permissions.handlePermissionResults[.camera] = .ENABLED

        let state = await sut.handleCameraPermission()

        #expect(permissions.handlePermissionCalls.contains(.camera))
        #expect(state == .ENABLED)
    }
}
