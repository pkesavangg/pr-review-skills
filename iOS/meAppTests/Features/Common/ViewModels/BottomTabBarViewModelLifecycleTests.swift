//
//  BottomTabBarViewModelLifecycleTests.swift
//  meAppTests
//
//  Covers BottomTabBarViewModel's init-driven prompt Task (Apple Health check, set-goal /
//  graph-scroll prompts, permission-disabled alert, push setup), the publisher-driven sinks
//  (logout, feed badge, appsync detection, Wi-Fi reading cards) and the goal-alert callbacks.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BottomTabBarViewModelLifecycleTests {

    // The prompt Task waits `promptDelay` (3s); presentSetGoalCard adds another 3s.
    private static let promptWaitNs: UInt64 = 4_500_000_000
    private static let goalWaitNs: UInt64 = 7_000_000_000

    struct SUT {
        let vm: BottomTabBarViewModel
        let account: MockAccountService
        let bluetooth: MockBluetoothService
        let entry: MockEntryService
        let permissions: MockPermissionsService
        let push: MockPushNotificationService
        let notification: MockNotificationHelperService
        let device: MockScaleService
        let feed: MockContentViewModelFeedService
        let goalAlert: MockGoalAlertService
        let healthKit: MockHealthKitServiceForIntegrations
    }

    private func makeSUT(
        accountId: String = "acct-life",
        goalType: GoalType? = .maintain,
        activeAccount: Bool = true,
        entryCount: Int = 0
    ) -> SUT {
        TestDependencyContainer.reset()
        let account = MockAccountService()
        if activeAccount {
            account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
                id: accountId, email: "\(accountId)@example.com", isActiveAccount: true, goalType: goalType
            )
        }
        let bluetooth = MockBluetoothService()
        let entry = MockEntryService()
        entry.getEntryCountResult = .success(entryCount)
        let permissions = MockPermissionsService()
        let push = MockPushNotificationService()
        let notification = MockNotificationHelperService()
        let device = MockScaleService()
        let feed = MockContentViewModelFeedService()
        let goalAlert = MockGoalAlertService()
        let healthKit = MockHealthKitServiceForIntegrations()

        DependencyContainer.shared.register(healthKit as HealthKitServiceProtocol)
        DependencyContainer.shared.register(account as AccountServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(entry as EntryServiceProtocol)
        DependencyContainer.shared.register(permissions)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(push as PushNotificationServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(device as PairedDeviceServiceProtocol)
        DependencyContainer.shared.register(feed as FeedServiceProtocol)
        DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)

        return SUT(
            vm: BottomTabBarViewModel(),
            account: account,
            bluetooth: bluetooth,
            entry: entry,
            permissions: permissions,
            push: push,
            notification: notification,
            device: device,
            feed: feed,
            goalAlert: goalAlert,
            healthKit: healthKit
        )
    }

    private func wifiCapableScale(id: String = "wifi-1") -> DeviceSnapshot {
        let device = ScaleTestFixtures.makeDevice(id: id)
        device.bathScale = BathScale(scaleType: DeviceModelType.wifi.rawValue, bodyComp: false)
        return device.toSnapshot(isConnected: true, isWifiConfigured: true)
    }

    private func appsyncScale(id: String = "appsync-1") -> DeviceSnapshot {
        let device = ScaleTestFixtures.makeDevice(id: id)
        device.bathScale = BathScale(scaleType: DeviceSourceType.appsync.rawValue, bodyComp: true)
        return device.toSnapshot(isConnected: true)
    }

    private func scaleEntryNotification(source: String? = nil) -> EntryNotification {
        EntryNotification(from: makeDTO(
            entryTimestamp: "2026-04-22T10:00:00Z",
            entryType: EntryType.scale.rawValue,
            source: source,
            weight: 90_700
        ))
    }

    private func makeDTO(
        entryTimestamp: String,
        entryType: String,
        source: String?,
        pulse: Double? = nil,
        systolic: Double? = nil,
        diastolic: Double? = nil,
        weight: Double? = nil
    ) -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: "acct-life",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: entryTimestamp,
            entryType: entryType,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "create",
            proteinPercent: nil,
            pulse: pulse,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: source,
            subcutaneousFatPercent: nil,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: weight
        )
    }

    private func makeBaby(name: String) -> Baby {
        Baby(
            accountId: "acct-life",
            name: name,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 4_000_000_000,
        pollNanoseconds: UInt64 = 30_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    // MARK: - init prompt Task

    @Test("init prompt task updates push device info when notifications are not required")
    func initTaskUpdatesPushDeviceInfo() async {
        let sut = makeSUT()

        let done = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.push.updateDeviceInfoCalls == 1 }

        #expect(done == true)
    }

    @Test("init prompt task sets up push notifications when notifications are required")
    func initTaskSetsUpPushWhenNotificationsRequired() async {
        let sut = makeSUT()
        sut.permissions.emitRequiredCategories([.notifications])
        sut.permissions.emitPermissions([.NOTIFICATION: .ENABLED])

        let done = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.push.setupPushNotificationsCalls == 1 }

        #expect(done == true)
    }

    @Test("init prompt task shows the permission-disabled alert when a required permission is missing")
    func initTaskShowsPermissionAlert() async {
        let sut = makeSUT()
        sut.permissions.emitRequiredCategories([.bluetooth])
        sut.permissions.emitPermissions([.BLUETOOTH: .DISABLED, .BLUETOOTH_SWITCH: .DISABLED])

        let shown = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.notification.showAlertCalls >= 1 }
        #expect(shown == true)

        // Tapping APP PERMISSION routes to the App Permissions settings screen.
        sut.notification.alertData?.buttons.last?.action(nil)
        #expect(sut.vm.selectedTab == .settings)
        #expect(sut.vm.pendingSettingsNavigation == .appPermissions)
    }

    @Test("init prompt task shows the notification-only permission alert")
    func initTaskShowsNotificationOnlyPermissionAlert() async {
        let sut = makeSUT(accountId: "acct-notif-\(UUID().uuidString)")
        sut.permissions.emitRequiredCategories([.notifications])
        sut.permissions.emitPermissions([.NOTIFICATION: .DISABLED])

        let shown = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.notification.showAlertCalls >= 1 }
        #expect(shown == true)
    }

    @Test("init prompt task presents a dashboard prompt modal when eligible")
    func initTaskPresentsDashboardPromptModal() async {
        // Fresh account id + no goal + >=3 entries => set-goal / graph-scroll prompts are eligible.
        let sut = makeSUT(accountId: "acct-goal-\(UUID().uuidString)", goalType: nil, entryCount: 3)

        let shown = await waitUntil(timeoutNanoseconds: Self.goalWaitNs) { sut.notification.showModalCalls >= 1 }

        #expect(shown == true)
    }

    // MARK: - Publisher-driven sinks

    @Test("logging out dismisses all modals, alerts and loaders")
    func logoutDismissesOverlays() async {
        let sut = makeSUT()

        sut.account.activeAccount = nil

        let dismissed = await waitUntil { sut.notification.dismissAllModalsCalls >= 1 }
        #expect(dismissed == true)
    }

    @Test("feed notification badge updates from the feed service publisher")
    func feedBadgeUpdatesFromPublisher() async {
        let sut = makeSUT()

        sut.feed.notificationBadgeUpdated.send(true)

        let updated = await waitUntil { sut.vm.canShowFeedNotificationBadge == true }
        #expect(updated == true)
    }

    @Test("showAppSync becomes true when an appsync scale is published")
    func appSyncDetectedFromScalesPublisher() async {
        let sut = makeSUT()

        sut.device.scales = [appsyncScale()]

        let detected = await waitUntil { sut.vm.showAppSync == true }
        #expect(detected == true)
    }

    // MARK: - Wi-Fi reading cards

    @Test("Wi-Fi weight reading card is shown for an already-saved Wi-Fi weight entry")
    func wifiWeightReadingCardShown() async {
        let sut = makeSUT()
        sut.device.scales = [wifiCapableScale()]

        sut.bluetooth.newEntryReceivedSubject.send(scaleEntryNotification(source: "wifi"))

        // 1s debounce on the publisher.
        let shown = await waitUntil(timeoutNanoseconds: 3_000_000_000) { sut.notification.showToastCalls >= 1 }
        #expect(shown == true)
    }

    // NOTE: The Wi-Fi BPM card path is unreachable from a DTO-built notification —
    // `EntryNotification.init(from: BathScaleOperationDTO)` hardcodes `source = nil`, and the
    // Wi-Fi BPM filter requires a non-nil source. Covered only when a source-bearing entry path exists.

    // MARK: - Apple Health integration modal (init prompt task)

    @Test("init prompt task presents the Apple Health add-integration modal")
    func initTaskPresentsHKAddIntegrationModal() async {
        let sut = makeSUT()
        sut.healthKit.shouldShowModalResult = .addIntegration

        let shown = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.notification.showModalCalls >= 1 }
        #expect(shown == true)
    }

    @Test("init prompt task presents the Apple Health out-of-sync modal")
    func initTaskPresentsHKOutOfSyncModal() async {
        let sut = makeSUT()
        sut.healthKit.shouldShowModalResult = .outOfSync

        let shown = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.notification.showModalCalls >= 1 }
        #expect(shown == true)
    }

    @Test("init prompt task shows the synced toast when HK permissions were restored")
    func initTaskShowsHKSyncedToastOnRestore() async {
        let sut = makeSUT()
        sut.healthKit.checkIfPermissionsRestoredResult = true

        let shown = await waitUntil(timeoutNanoseconds: Self.promptWaitNs) { sut.notification.showToastCalls >= 1 }
        #expect(shown == true)
    }

    // MARK: - Reading arrival cards (multiple readings + auto-save timeout)

    @Test("a second pending weight reading shows the multiple-readings card and auto-saves on timeout")
    func weightMultipleReadingsAndAutoSave() async {
        let sut = makeSUT()
        sut.bluetooth.confirmPendingScaleEntryError = nil

        sut.bluetooth.pendingScaleEntrySubject.send(scaleEntryNotification())
        _ = await waitUntil(timeoutNanoseconds: 2_000_000_000) { sut.notification.showToastCalls == 1 }
        sut.bluetooth.pendingScaleEntrySubject.send(scaleEntryNotification())
        let secondShown = await waitUntil(timeoutNanoseconds: 2_000_000_000) { sut.notification.showToastCalls == 2 }
        #expect(secondShown == true)

        // The card auto-saves the pending entry after its 8s display timeout.
        let autoSaved = await waitUntil(timeoutNanoseconds: 10_000_000_000) { sut.bluetooth.confirmPendingScaleEntryCalls >= 1 }
        #expect(autoSaved == true)
    }

    @Test("a second pending BPM reading shows the multiple-readings card")
    func bpmMultipleReadings() async {
        let sut = makeSUT()

        sut.bluetooth.pendingBpmEntrySubject.send(scaleEntryNotification())
        _ = await waitUntil(timeoutNanoseconds: 2_000_000_000) { sut.notification.showToastCalls == 1 }
        sut.bluetooth.pendingBpmEntrySubject.send(scaleEntryNotification())

        let secondShown = await waitUntil(timeoutNanoseconds: 2_000_000_000) { sut.notification.showToastCalls == 2 }
        #expect(secondShown == true)

        // Let the BPM card's auto-save timeout Task run for coverage.
        _ = await waitUntil(timeoutNanoseconds: 9_000_000_000) { false }
        #expect(sut.notification.showToastCalls == 2)
    }

    @Test("a baby reading with multiple profiles shows the assign card")
    func babyReadingWithMultipleProfilesShowsAssignCard() async {
        let sut = makeSUT()
        let babyMock = MockBabyService()
        babyMock.babies = [makeBaby(name: "Baby A"), makeBaby(name: "Baby B")]
        sut.vm.babyService = babyMock
        sut.vm.entryService = sut.entry
        sut.vm.notificationService = sut.notification

        sut.bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())

        let shown = await waitUntil(timeoutNanoseconds: 3_000_000_000) { sut.notification.showToastCalls >= 1 }
        #expect(shown == true)
    }

    // MARK: - Goal-alert callbacks

    @Test("goal-alert navigate callback routes to goal settings")
    func goalAlertNavigateCallbackRoutesToGoal() {
        let sut = makeSUT()

        sut.goalAlert.onNavigateToGoalSetting?()

        #expect(sut.vm.selectedTab == .settings)
        #expect(sut.vm.pendingSettingsNavigation == .goal)
    }

    @Test("goal-alert dashboard-check callback reflects the selected tab")
    func goalAlertDashboardCheckCallback() {
        let sut = makeSUT()

        sut.vm.selectTab(.dash)
        #expect(sut.goalAlert.isOnDashboardTab?() == true)

        sut.vm.selectTab(.history)
        #expect(sut.goalAlert.isOnDashboardTab?() == false)
    }

    @Test("clearGoalAlertCallbacks clears the goal-alert closures")
    func clearGoalAlertCallbacksClears() {
        let sut = makeSUT()

        sut.vm.clearGoalAlertCallbacks()

        #expect(sut.goalAlert.onNavigateToGoalSetting == nil)
        #expect(sut.goalAlert.isOnDashboardTab == nil)
    }
}
