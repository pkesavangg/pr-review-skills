import Foundation
import Testing
import UIKit
@testable import meApp

@Suite(.serialized)
@MainActor
struct HealthKitStoreTests {
    @Test("loadStatus maps integration state for active account")
    func loadStatusMapsIntegrationState() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-1")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: true,
            assignedTo: "hk-1"
        )
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.isHKOutOfSyncResult = true

        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit
        )

        let updated = await waitUntil { store.isIntegrated && store.isOutOfSync }
        #expect(updated == true)
    }

    @Test("loadStatus keeps integration off when assigned to another account")
    func loadStatusAssignedToAnotherAccount() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-2")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: true,
            assignedTo: "someone-else"
        )
        let (store, _, _, _, _, _, _) = makeSUT(accountService: account, integrationService: integration)

        let updated = await waitUntil { integration.getStoredIntegrationDataCalls > 0 }
        #expect(updated == true)
        #expect(store.isIntegrated == false)
    }

    @Test("activeState transition posts sheet presented and dismissed notifications")
    func activeStateTransitionsPostNotifications() async {
        let (store, _, _, _, _, _, _) = makeSUT()
        var presentedCount = 0
        var dismissedCount = 0

        let presentedObserver = NotificationCenter.default.addObserver(
            forName: .appleHealthSheetPresented,
            object: nil,
            queue: nil
        ) { _ in
            presentedCount += 1
        }
        let dismissedObserver = NotificationCenter.default.addObserver(
            forName: .appleHealthSheetDismissed,
            object: nil,
            queue: nil
        ) { _ in
            dismissedCount += 1
        }

        store.activeState = .permissionsNotAllowed
        store.activeState = nil
        let posted = await waitUntil { presentedCount == 1 && dismissedCount == 1 }

        NotificationCenter.default.removeObserver(presentedObserver)
        NotificationCenter.default.removeObserver(dismissedObserver)
        #expect(posted == true)
    }

    @Test("handleRowTap integrated shows remove alert")
    func handleRowTapIntegratedShowsRemoveAlert() {
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(notificationService: notification)
        store.isIntegrated = true

        store.handleRowTap()

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.buttons.count == 2)
        #expect(notification.alertData?.buttons.last?.type == .danger)
    }

    @Test("handleRowTap first-time user opens permissions-not-allowed state")
    func handleRowTapFirstTimeState() async {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.isIntegrated = false

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .permissionsNotAllowed }

        #expect(updated == true)
    }

    @Test("handleRowTap with previous integration and full permissions opens permissions-allowed state")
    func handleRowTapPreviousIntegrationFullPermissions() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-3")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: false,
            assignedTo: "hk-3"
        )
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["a", "b", "c", "d", "e"]
        let kv = MockKvStorageService()
        kv.setValue(
            true,
            forKey: KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: "hk-3")
        )
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            kvStorage: kv
        )

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .permissionsAllowed }

        #expect(updated == true)
    }

    @Test("handleRowTap with previous integration and partial permissions opens integration-complete state")
    func handleRowTapPreviousIntegrationPartialPermissions() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-3b")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: false,
            assignedTo: "hk-3b"
        )
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["a", "b"]
        let kv = MockKvStorageService()
        kv.setValue(
            true,
            forKey: KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: "hk-3b")
        )
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            kvStorage: kv
        )

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .integrationComplete }

        #expect(updated == true)
    }

    @Test("handleRowTap with previous integration and no permissions opens integration-failed state")
    func handleRowTapPreviousIntegrationNoPermissions() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-3c")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: false,
            assignedTo: "hk-3c"
        )
        let kv = MockKvStorageService()
        kv.setValue(
            true,
            forKey: KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: "hk-3c")
        )
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            kvStorage: kv
        )

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .integrationFailed }

        #expect(updated == true)
    }

    @Test("handleRowTap after first-time screen with no integration and partial permissions opens integration-complete state")
    func handleRowTapSeenConnectScreenPartialPermissions() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-3d")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = nil
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let kv = MockKvStorageService()
        kv.setValue(
            true,
            forKey: KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: "hk-3d")
        )
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            kvStorage: kv
        )

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .integrationComplete }

        #expect(updated == true)
    }

    @Test("handleRowTap when conflict check fails still continues first-time flow")
    func handleRowTapConflictCheckFailureFallsBack() async {
        let integration = MockHealthKitStoreIntegrationService()
        integration.isIntegrationAlreadyUsedError = HealthKitStoreTestError.loadFailed
        let (store, _, _, _, _, _, _) = makeSUT(integrationService: integration)

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .permissionsNotAllowed }

        #expect(updated == true)
    }

    @Test("handleRowTap conflict branch opens user-conflict state")
    func handleRowTapConflictState() async {
        let integration = MockHealthKitStoreIntegrationService()
        integration.isIntegrationAlreadyUsedResult = true
        let (store, _, _, _, _, _, _) = makeSUT(integrationService: integration)

        store.handleRowTap()
        let updated = await waitUntil { store.activeState == .userConflict }

        #expect(updated == true)
    }

    @Test("primary action permissions-not-allowed sets first-time flag and moves to integration-complete on success")
    func primaryActionPermissionsNotAllowedSuccess() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-4")
        let kv = MockKvStorageService()
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.integrateResult = .success(true)
        let (store, _, _, _, _, _, _) = makeSUT(accountService: account, healthKitService: healthKit, kvStorage: kv)

        store.handlePrimaryAction(for: .permissionsNotAllowed)
        let updated = await waitUntil { store.activeState == .integrationComplete }

        let key = KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: "hk-4")
        #expect(updated == true)
        #expect(kv.getValue(forKey: key) as? Bool == true)
        #expect(healthKit.integrateCalls == [true])
    }

    @Test("primary action permissions-not-allowed maps authorization failure to integration-failed")
    func primaryActionPermissionsNotAllowedAuthorizationFailure() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.integrateResult = .success(false)
        let (store, _, _, _, _, _, _) = makeSUT(healthKitService: healthKit)

        store.handlePrimaryAction(for: .permissionsNotAllowed)
        let updated = await waitUntil { store.activeState == .integrationFailed }

        #expect(updated == true)
    }

    @Test("primary action permissions-not-allowed maps integration conflict error to user-conflict")
    func primaryActionPermissionsNotAllowedConflictError() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.integrateResult = .failure(IntegrationError.userConflict)
        let (store, _, _, _, _, _, _) = makeSUT(healthKitService: healthKit)

        store.handlePrimaryAction(for: .permissionsNotAllowed)
        let updated = await waitUntil { store.activeState == .userConflict }

        #expect(updated == true)
    }

    @Test("primary action integration-complete with entries shows sync alert")
    func primaryActionIntegrationCompleteWithEntriesShowsSyncAlert() async {
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(2)
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(entryService: entry, notificationService: notification)

        store.handlePrimaryAction(for: .integrationComplete)
        let shown = await waitUntil { notification.showAlertCalls == 1 }

        #expect(shown == true)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("primary action integration-complete with no entries persists integration and shows toast")
    func primaryActionIntegrationCompleteNoEntriesPersistsIntegration() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-5")
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(0)
        let integration = MockHealthKitStoreIntegrationService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            entryService: entry,
            notificationService: notification
        )

        store.handlePrimaryAction(for: .integrationComplete)
        let persisted = await waitUntil { integration.setStoredIntegrationDataCalls == 1 }

        #expect(persisted == true)
        #expect(notification.showToastCalls == 1)
        #expect(integration.lastSetStoredInfo?.isIntegrated == true)
    }

    @Test("primary action integration-complete with no entries still shows toast when persist fails")
    func primaryActionIntegrationCompleteNoEntriesPersistFailureStillToasts() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-5b")
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(0)
        let integration = MockHealthKitStoreIntegrationService()
        integration.setStoredIntegrationDataError = HealthKitStoreTestError.loadFailed
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            entryService: entry,
            notificationService: notification
        )

        store.handlePrimaryAction(for: .integrationComplete)
        let updated = await waitUntil { notification.showToastCalls == 1 && integration.setStoredIntegrationDataCalls == 1 }

        #expect(updated == true)
    }

    @Test("sync history cancel path persists integration and shows synced toast")
    func syncHistoryCancelPathPersistsAndToasts() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-5c")
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(2)
        let integration = MockHealthKitStoreIntegrationService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            entryService: entry,
            notificationService: notification
        )

        store.handlePrimaryAction(for: .integrationComplete)
        _ = await waitUntil { notification.showAlertCalls == 1 }
        notification.alertData?.buttons.first?.action(nil)
        let updated = await waitUntil { notification.showToastCalls == 1 && integration.setStoredIntegrationDataCalls == 1 }

        #expect(updated == true)
    }

    @Test("sync history sync path runs full sync, logs latest entry, and persists integration")
    func syncHistorySyncPathPerformsFullSync() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-5d")
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(1)
        entry.latestEntry = HealthKitStoreTestFixtures.makeEntry(accountId: "hk-5d")
        let healthKit = MockHealthKitStoreHealthKitService()
        let integration = MockHealthKitStoreIntegrationService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            entryService: entry,
            notificationService: notification
        )

        store.handlePrimaryAction(for: .integrationComplete)
        _ = await waitUntil { notification.showAlertCalls == 1 }
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntil {
            healthKit.syncAllDataCalls == 1
                && integration.logHealthEntryCalls == 1
                && integration.setStoredIntegrationDataCalls == 1
                && notification.dismissLoaderCalls == 1
                && notification.toastData?.message == ToastStrings.weightHistorySynced
        }

        #expect(updated == true)
    }

    @Test("sync history sync path maps sync error to generic failure toast")
    func syncHistorySyncPathErrorShowsFailureToast() async {
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(1)
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.syncAllDataError = HealthKitStoreTestError.syncFailed
        let integration = MockHealthKitStoreIntegrationService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            integrationService: integration,
            healthKitService: healthKit,
            entryService: entry,
            notificationService: notification
        )

        store.handlePrimaryAction(for: .integrationComplete)
        _ = await waitUntil { notification.showAlertCalls == 1 }
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntil {
            healthKit.syncAllDataCalls == 1
                && notification.dismissLoaderCalls == 1
                && notification.toastData?.title == ToastStrings.somethingWentWrongTitle
        }

        #expect(updated == true)
        #expect(integration.setStoredIntegrationDataCalls == 0)
    }

    @Test("primary action permissions-allowed requests authorization when previously integrated")
    func primaryActionPermissionsAllowedPreviouslyIntegrated() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-6")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: false,
            assignedTo: "hk-6"
        )
        let healthKit = MockHealthKitStoreHealthKitService()
        let entry = MockHealthKitStoreEntryService()
        entry.entryCountResult = .success(0)
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            entryService: entry
        )

        store.handlePrimaryAction(for: .permissionsAllowed)
        let updated = await waitUntil { healthKit.integrateCalls == [true] && integration.setStoredIntegrationDataCalls == 1 }

        #expect(updated == true)
    }

    @Test("primary action integration-failed opens Apple Health and returns to integration-complete when permissions exist")
    func primaryActionIntegrationFailedPermissionReturnSuccess() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let integration = MockHealthKitStoreIntegrationService()
        let (store, _, _, _, _, _, _) = makeSUT(
            integrationService: integration,
            healthKitService: healthKit
        )

        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntil { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntil { store.activeState == .integrationComplete }

        #expect(updated == true)
        #expect(healthKit.openAppleHealthCalls == 1)
    }

    @Test("primary action integration-failed keeps modal dismissed when no permissions are granted")
    func primaryActionIntegrationFailedNoPermissions() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = []
        let (store, _, _, _, _, _, _) = makeSUT(healthKitService: healthKit)

        store.activeState = .integrationFailed
        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntil { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntil { store.activeState == nil }

        #expect(updated == true)
    }

    @Test("primary action integration-failed maps conflict after permission grant to user-conflict")
    func primaryActionIntegrationFailedConflictAfterPermissionGrant() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let integration = MockHealthKitStoreIntegrationService()
        integration.isIntegrationAlreadyUsedResult = true
        let (store, _, _, _, _, _, _) = makeSUT(
            integrationService: integration,
            healthKitService: healthKit
        )

        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntil { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntil { store.activeState == .userConflict }

        #expect(updated == true)
    }

    @Test("primary action integration-failed continues to integration-complete when conflict check throws")
    func primaryActionIntegrationFailedConflictCheckFailureFallsBack() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let integration = MockHealthKitStoreIntegrationService()
        integration.isIntegrationAlreadyUsedError = HealthKitStoreTestError.loadFailed
        let (store, _, _, _, _, _, _) = makeSUT(
            integrationService: integration,
            healthKitService: healthKit
        )

        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntil { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntil { store.activeState == .integrationComplete }

        #expect(updated == true)
    }

    @Test("integrated row remove action clears HealthKit and refreshes local state")
    func removeActionClearsHealthKitAndShowsToast() async {
        let account = MockAccountService()
        account.activeAccount = HealthKitStoreTestFixtures.makeActiveAccount(id: "hk-7")
        let integration = MockHealthKitStoreIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitStoreTestFixtures.makeHealthKitInfo(
            isIntegrated: false,
            assignedTo: "hk-7"
        )
        let healthKit = MockHealthKitStoreHealthKitService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            notificationService: notification
        )
        store.isIntegrated = true

        store.handleRowTap()
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntil {
            healthKit.clearHealthKitCalls == 1
                && notification.showLoaderCalls == 1
                && notification.dismissLoaderCalls == 1
                && notification.toastData?.message == ToastStrings.hkIntegrationRemoved
        }

        #expect(updated == true)
        #expect(integration.getStoredIntegrationDataCalls > 1)
    }

    @Test("clear integration failure dismisses loader without success toast")
    func removeActionClearFailure() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.clearHealthKitError = HealthKitStoreTestError.syncFailed
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            healthKitService: healthKit,
            notificationService: notification
        )
        store.isIntegrated = true

        store.handleRowTap()
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntil { healthKit.clearHealthKitCalls == 1 && notification.dismissLoaderCalls == 1 }

        #expect(updated == true)
        #expect(notification.toastData?.message == nil)
    }

    @Test("out-of-sync alert return with full permissions and synced state shows toast")
    func outOfSyncAlertReturnSyncedShowsToast() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["a", "b", "c", "d", "e"]
        healthKit.isHKOutOfSyncResult = false
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            healthKitService: healthKit,
            notificationService: notification
        )

        store.showHKOutOfSyncAlert()
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntil { notification.toastData?.message == ToastStrings.hkIntegrationSynced && store.isOutOfSync == false }

        #expect(updated == true)
    }

    @Test("out-of-sync alert return still out-of-sync does not show toast")
    func outOfSyncAlertReturnStillOutOfSyncNoToast() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["a", "b"]
        healthKit.isHKOutOfSyncResult = true
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUT(
            healthKitService: healthKit,
            notificationService: notification
        )

        store.showHKOutOfSyncAlert()
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntil { store.isOutOfSync == true }

        #expect(updated == true)
        #expect(notification.showToastCalls == 0)
    }

    @Test("primary action user-conflict dismisses modal")
    func primaryActionUserConflictDismissesModal() async {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.activeState = .userConflict

        store.handlePrimaryAction(for: .userConflict)
        let dismissed = await waitUntil { store.activeState == nil }

        #expect(dismissed == true)
    }
}

@MainActor
private func makeSUT(
    accountService: MockAccountService? = nil,
    integrationService: MockHealthKitStoreIntegrationService? = nil,
    healthKitService: MockHealthKitStoreHealthKitService? = nil,
    entryService: MockHealthKitStoreEntryService? = nil,
    notificationService: MockNotificationHelperService? = nil,
    loggerService: MockLoggerService? = nil,
    kvStorage: MockKvStorageService? = nil
) -> (
    store: HealthKitStore,
    accountService: MockAccountService,
    integrationService: MockHealthKitStoreIntegrationService,
    healthKitService: MockHealthKitStoreHealthKitService,
    entryService: MockHealthKitStoreEntryService,
    notificationService: MockNotificationHelperService,
    kvStorage: MockKvStorageService
) {
    let account = accountService ?? MockAccountService()
    let integration = integrationService ?? MockHealthKitStoreIntegrationService()
    let healthKit = healthKitService ?? MockHealthKitStoreHealthKitService()
    let entry = entryService ?? MockHealthKitStoreEntryService()
    let notification = notificationService ?? MockNotificationHelperService()
    let logger = loggerService ?? MockLoggerService()
    let kv = kvStorage ?? MockKvStorageService()

    let store = HealthKitStore(
        kvStorage: kv,
        notificationService: notification,
        entryService: entry,
        accountService: account,
        integrationService: integration,
        healthKitService: healthKit,
        logger: logger
    )
    return (store, account, integration, healthKit, entry, notification, kv)
}

@MainActor
private func waitUntil(
    timeoutNanoseconds: UInt64 = 2_000_000_000,
    pollIntervalNanoseconds: UInt64 = 10_000_000,
    condition: @MainActor () -> Bool
) async -> Bool {
    let start = DispatchTime.now().uptimeNanoseconds
    while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
        if condition() { return true }
        try? await Task.sleep(nanoseconds: pollIntervalNanoseconds)
    }
    return false
}
