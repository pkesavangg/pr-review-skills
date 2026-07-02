import Foundation
@testable import meApp
import Testing
import UIKit

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

        await store.waitForLoadStatus()
        #expect(store.isIntegrated == true)
        #expect(store.isOutOfSync == true)
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

        await store.waitForLoadStatus()
        #expect(integration.getStoredIntegrationDataCalls == 1)
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
        let key = KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: "hk-4")
        let updated = await waitUntil(timeoutNanoseconds: 5_000_000_000) {
            store.activeState == .integrationComplete &&
                (kv.getValue(forKey: key) as? Bool) == true &&
                healthKit.integrateCalls == [true]
        }

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
        await Task.yield()
        let updated = await waitUntil { store.activeState == .integrationFailed }

        #expect(updated == true)
    }

    @Test("primary action permissions-not-allowed maps integration conflict error to user-conflict")
    func primaryActionPermissionsNotAllowedConflictError() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.integrateResult = .failure(IntegrationError.userConflict)
        let (store, _, _, _, _, _, _) = makeSUT(healthKitService: healthKit)

        store.handlePrimaryAction(for: .permissionsNotAllowed)
        await Task.yield()
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
    // Test factory return; labeled tuple is clearer than a one-off SUT struct.
    // swiftlint:disable:next large_tuple
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

    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(entry as EntryServiceProtocol)
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(integration as IntegrationServiceProtocol)
    DependencyContainer.shared.register(healthKit as HealthKitServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

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
