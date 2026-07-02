import Foundation
@testable import meApp
import Testing
import UIKit

extension HealthKitStoreTests {
    @Test("primary action integration-failed opens Apple Health and returns to integration-complete when permissions exist")
    func primaryActionIntegrationFailedPermissionReturnSuccess() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let integration = MockHealthKitStoreIntegrationService()
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            integrationService: integration,
            healthKitService: healthKit
        )

        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntilExtra { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntilExtra { store.activeState == .integrationComplete }

        #expect(updated == true)
        #expect(healthKit.openAppleHealthCalls == 1)
    }

    @Test("primary action integration-failed keeps modal dismissed when no permissions are granted")
    func primaryActionIntegrationFailedNoPermissions() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = []
        let (store, _, _, _, _, _, _) = makeSUTExtra(healthKitService: healthKit)

        store.activeState = .integrationFailed
        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntilExtra { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntilExtra { store.activeState == nil }

        #expect(updated == true)
    }

    @Test("primary action integration-failed maps conflict after permission grant to user-conflict")
    func primaryActionIntegrationFailedConflictAfterPermissionGrant() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let integration = MockHealthKitStoreIntegrationService()
        integration.isIntegrationAlreadyUsedResult = true
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            integrationService: integration,
            healthKitService: healthKit
        )

        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntilExtra { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntilExtra { store.activeState == .userConflict }

        #expect(updated == true)
    }

    @Test("primary action integration-failed continues to integration-complete when conflict check throws")
    func primaryActionIntegrationFailedConflictCheckFailureFallsBack() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["weight"]
        let integration = MockHealthKitStoreIntegrationService()
        integration.isIntegrationAlreadyUsedError = HealthKitStoreTestError.loadFailed
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            integrationService: integration,
            healthKitService: healthKit
        )

        store.handlePrimaryAction(for: .integrationFailed)
        _ = await waitUntilExtra { healthKit.openAppleHealthCalls == 1 }
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntilExtra { store.activeState == .integrationComplete }

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
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            accountService: account,
            integrationService: integration,
            healthKitService: healthKit,
            notificationService: notification
        )
        store.isIntegrated = true

        store.handleRowTap()
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntilExtra {
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
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            healthKitService: healthKit,
            notificationService: notification
        )
        store.isIntegrated = true

        store.handleRowTap()
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntilExtra { healthKit.clearHealthKitCalls == 1 && notification.dismissLoaderCalls == 1 }

        #expect(updated == true)
        #expect(notification.toastData?.message == nil)
    }

    @Test("out-of-sync alert return with full permissions and synced state shows toast")
    func outOfSyncAlertReturnSyncedShowsToast() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["a", "b", "c", "d", "e"]
        healthKit.isHKOutOfSyncResult = false
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            healthKitService: healthKit,
            notificationService: notification
        )

        store.showHKOutOfSyncAlert()
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntilExtra { notification.toastData?.message == ToastStrings.hkIntegrationSynced && store.isOutOfSync == false }

        #expect(updated == true)
    }

    @Test("out-of-sync alert return still out-of-sync does not show toast")
    func outOfSyncAlertReturnStillOutOfSyncNoToast() async {
        let healthKit = MockHealthKitStoreHealthKitService()
        healthKit.approvedPermissionList = ["a", "b"]
        healthKit.isHKOutOfSyncResult = true
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _, _, _) = makeSUTExtra(
            healthKitService: healthKit,
            notificationService: notification
        )

        store.showHKOutOfSyncAlert()
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        let updated = await waitUntilExtra { store.isOutOfSync == true }

        #expect(updated == true)
        #expect(notification.showToastCalls == 0)
    }

    @Test("primary action user-conflict dismisses modal")
    func primaryActionUserConflictDismissesModal() async {
        let (store, _, _, _, _, _, _) = makeSUTExtra()
        store.activeState = .userConflict

        store.handlePrimaryAction(for: .userConflict)
        let dismissed = await waitUntilExtra { store.activeState == nil }

        #expect(dismissed == true)
    }
}

@MainActor
private func makeSUTExtra(
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
private func waitUntilExtra(
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
