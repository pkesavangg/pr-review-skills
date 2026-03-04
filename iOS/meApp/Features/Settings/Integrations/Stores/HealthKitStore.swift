//  HealthKitStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/06/25.
//

import Combine
import Foundation
import SwiftUI

// MARK: - HealthKitStore

/// Centralised observable store that wraps all HealthKit related workflows.
/// It talks to `HealthKitService` for the heavy-lifting and exposes a small, UI-friendly
/// API (published properties + async helpers) so that screens only have to observe
/// this object.
@MainActor
final class HealthKitStore: ObservableObject {
    // MARK: - Published State

    /// Current on/off status of the Apple Health integration.
    @Published var isIntegrated: Bool = false

    @Published var isOutOfSync: Bool = false
    /// Current Health-access modal state. `nil` means no modal is visible.
    @Published var activeState: AppleHealthIntegrationState?

    // MARK: - Dependencies

    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var entryService: EntryServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var integrationService: IntegrationServiceProtocol
    @Injector private var healthKitService: HealthKitServiceProtocol
    @Injector private var logger: LoggerServiceProtocol
    private let kvStorage: KvStorageServiceProtocol

    var cancellables: Set<AnyCancellable> = []
    static let wgTotalPermissionsCount = 5

    /// Duration to wait for sheet dismissal animation to complete before showing subsequent UI.
    private static let sheetDismissalAnimationDurationNanoseconds: UInt64 = 300_000_000 // 0.3 seconds

    /// Retains the Combine subscription for app-active notifications specifically used
    /// when we need to re-check HealthKit permissions after the user is redirected to
    /// the Apple Health app.
    private var foregroundObserver: AnyCancellable?
    /// Tracks whether sync has already been performed in the current integration flow.
    /// Used to prevent showing sync prompt twice in the permission-denied flow.
    private var hasSyncedInCurrentFlow: Bool = false

    /// Tracks the previous activeState value to detect transitions between nil and non-nil
    private var previousActiveState: AppleHealthIntegrationState?

    let alertLang = AlertStrings.self
    let tag = "HealthKitStore"

    // MARK: - Init

    init(
        kvStorage: KvStorageServiceProtocol? = nil,
        notificationService: NotificationHelperServiceProtocol? = nil,
        entryService: EntryServiceProtocol? = nil,
        accountService: AccountServiceProtocol? = nil,
        integrationService: IntegrationServiceProtocol? = nil,
        healthKitService: HealthKitServiceProtocol? = nil,
        logger: LoggerServiceProtocol? = nil
    ) {
        self.kvStorage = kvStorage ?? KvStorageService.shared

        if let notificationService {
            self.notificationService = notificationService
        }
        if let entryService {
            self.entryService = entryService
        }
        if let accountService {
            self.accountService = accountService
        }
        if let integrationService {
            self.integrationService = integrationService
        }
        if let healthKitService {
            self.healthKitService = healthKitService
        }
        if let logger {
            self.logger = logger
        }
        loadStatus()
        // Observe activeState changes to post notifications when sheet is presented/dismissed
        // Only post notifications when transitioning between nil and non-nil states
        $activeState
            .dropFirst()
            .sink { [weak self] newState in
                guard let self else { return }
                let wasPresented = self.previousActiveState != nil
                let isPresented = newState != nil

                if !wasPresented, isPresented {
                    // Transitioning from nil to non-nil: sheet is being presented
                    NotificationCenter.default.post(name: .appleHealthSheetPresented, object: nil)
                } else if wasPresented, !isPresented {
                    // Transitioning from non-nil to nil: sheet is being dismissed
                    NotificationCenter.default.post(name: .appleHealthSheetDismissed, object: nil)
                }

                self.previousActiveState = newState
            }
            .store(in: &cancellables)
    }

    func loadStatus() {
        getLocalStoredData()
    }

    func getLocalStoredData() {
        Task {
            do {
                let result = try await integrationService.getStoredIntegrationData()
                isIntegrated = (result?.isIntegrated ?? false) && (result?.assignedTo == accountService.activeAccount?.accountId)
                isOutOfSync = await healthKitService.isHKOutOfSync()
                logger.log(level: .info, tag: tag, message: "Loaded HealthKit local state. isIntegrated=\(isIntegrated), isOutOfSync=\(isOutOfSync)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to load integration data", data: error.localizedDescription)
            }
        }
    }

    // MARK: - Flow Helpers ------------------------------------------------

    /// Called when the Apple-Health row is tapped in the integrations list.
    func handleRowTap() { // swiftlint:disable:this function_body_length
        logger.log(level: .info, tag: tag, message: "HealthKit row tapped. currentlyIntegrated=\(isIntegrated)")
        if isIntegrated {
            showHKRemoveAlert()
            return
        }
        // Reset sync flag and ensure integration status is false when starting a new integration flow
        hasSyncedInCurrentFlow = false
        isIntegrated = false // Ensure checkmark doesn't show until after sync completes
        Task {
            do {
                let isAlreadyIntegrated = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                if isAlreadyIntegrated {
                    logger.log(level: .error, tag: tag, message: "HealthKit connection blocked due to user conflict")
                    activeState = .userConflict
                    return
                }
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to check if HealthKit integration already exists",
                    data: error.localizedDescription
                )
            }

            // Check if this is the first-time connection attempt
            let hasShownFirstTimeConnectScreen = getHasShownFirstTimeConnectScreen()

            if !hasShownFirstTimeConnectScreen {
                // First-time connection - show the connect screen
                activeState = .permissionsNotAllowed
                logger.log(level: .info, tag: tag, message: "HealthKit flow state set. state=permissionsNotAllowed")
            } else {
                // User has attempted connection before - use existing logic
                let wasPreviouslyIntegrated = await self.wasPreviouslyIntegrated()

                if wasPreviouslyIntegrated {
                    // Determine the correct modal to present based on existing HealthKit permissions.
                    let permissionCount = healthKitService.getApprovedPermissionList().count
                    // According to WG: 5 permissions granted ⇒ show *Permissions Allowed* flow,
                    // partial permissions ( >0 & <5 ) ⇒ show *Integration Complete* flow so the user can finish,
                    // no permissions ⇒ proceed with normal *Permissions Not Allowed* flow.
                    switch permissionCount {
                    case Self.wgTotalPermissionsCount...:
                        activeState = .permissionsAllowed
                        logger.log(
                            level: .info,
                            tag: tag,
                            message: "HealthKit flow state set. state=permissionsAllowed, permissionCount=\(permissionCount)"
                        )
                    case 1 ..< Self.wgTotalPermissionsCount:
                        activeState = .integrationComplete
                        logger.log(
                            level: .info,
                            tag: tag,
                            message: "HealthKit flow state set. state=integrationComplete, permissionCount=\(permissionCount)"
                        )
                    default:
                        activeState = .integrationFailed
                        logger.log(
                            level: .info,
                            tag: tag,
                            message: "HealthKit flow state set. state=integrationFailed, permissionCount=\(permissionCount)"
                        )
                    }
                } else {
                    // User has seen the connect screen before but no integration record exists
                    // Check permissions to determine the appropriate state
                    let permissionCount = healthKitService.getApprovedPermissionList().count
                    if permissionCount == 0 {
                        // No permissions granted - show integration failed screen
                        activeState = .integrationFailed
                        logger.log(level: .info, tag: tag, message: "HealthKit flow state set. state=integrationFailed, no permissions")
                    } else {
                        // Some permissions exist but no record - show integration complete to finish setup
                        activeState = .integrationComplete
                        logger.log(
                            level: .info,
                            tag: tag,
                            message: "HealthKit flow state set. state=integrationComplete, permissionCount=\(permissionCount)"
                        )
                    }
                }
            }
        }
    }

    /// Handles the primary button tap for the given modal `state`.
    /// Maps UI states to store actions.
    /// - Parameter state: Current `AppleHealthIntegrationState` presented.
    func handlePrimaryAction(for state: AppleHealthIntegrationState) {
        logger.log(level: .info, tag: tag, message: "HealthKit primary action tapped. state=\(String(describing: state))")
        Task {
            switch state {
            case .permissionsNotAllowed:
                setHasShownFirstTimeConnectScreen(true)
                requestAuthorization()
            case .integrationComplete:
                finishIntegrationFlow()
            case .integrationFailed:
                observeForegroundForPermissionChanges()
                healthKitService.openAppleHealth()
            case .permissionsAllowed:
                if await wasPreviouslyIntegrated() {
                    requestAuthorization()
                } else {
                    finishIntegrationFlow()
                }
            case .userConflict:
                dismissModal()
            }
        }
    }

    /// Dismisses the modal entirely.
    func dismissModal() {
        logger.log(level: .info, tag: tag, message: "HealthKit modal dismissed")
        activeState = nil
    }

    // MARK: - Private Flow Steps -----------------------------------------

    /// Requests HealthKit authorisation via `HealthKitService`. Updates state depending on result.
    private func requestAuthorization() {
        logger.log(level: .info, tag: tag, message: "HealthKit authorization flow started")
        Task {
            do {
                let wasPreviouslyIntegrated = await wasPreviouslyIntegrated()
                let success = try await healthKitService.integrate(turnOn: true)

                if success {
                    activeState = wasPreviouslyIntegrated ? nil : .integrationComplete
                    logger.log(
                        level: .success,
                        tag: tag,
                        message: "HealthKit authorization flow succeeded. wasPreviouslyIntegrated=\(wasPreviouslyIntegrated)"
                    )
                    if wasPreviouslyIntegrated {
                        finishIntegrationFlow()
                    }
                } else {
                    activeState = .integrationFailed
                    logger.log(level: .error, tag: tag, message: "HealthKit authorization flow failed (service returned false)")
                }
                // Don't refresh integration status here - wait until after sync completes
            } catch {
                activeState = error is IntegrationError ? .userConflict : .integrationFailed
                logger.log(
                    level: .error,
                    tag: tag,
                    message: """
                    HealthKit authorization flow error. mappedState=\(error is IntegrationError ? "userConflict" : "integrationFailed"), \
                    error=\(error.localizedDescription)
                    """
                )
            }
        }
    }

    /// Handles the integration flow completion when user has granted full permissions.
    /// This skips the Integration Complete screen and goes directly to the sync alert.
    private func finishIntegrationFlowForFullPermissions() async {
        // Dismiss the current sheet
        dismissModal()
        // Small delay to ensure sheet dismissal animation completes
        try? await Task.sleep(nanoseconds: Self.sheetDismissalAnimationDurationNanoseconds)
        // Show sync alert if needed
        do {
            let count = try await entryService.getEntryCount()
            if count > 0 {
                presentSyncHistoryAlert()
            } else {
                // Nothing to sync – just show toast.
                notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
            }
        } catch {
            // If we can't get entry count, just show toast
            notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
        }
    }

    /// Called when user taps **FINISH** on the *Integration Complete* screen.
    /// Presents *Sync Weight History* alert if needed, otherwise persists integration and shows toast.
    /// - Parameter hasAlreadySynced: If true, skips showing sync prompt since sync was already performed.
    private func finishIntegrationFlow(hasAlreadySynced: Bool = false) {
        logger.log(level: .info, tag: tag, message: "Finish HealthKit integration flow requested. hasAlreadySynced=\(hasAlreadySynced)")
        Task {
            dismissModal()

            let hasEntries = ((try? await entryService.getEntryCount()) ?? 0) > 0
            if hasEntries {
                logger.log(level: .info, tag: tag, message: "HealthKit integration requires history sync prompt")
                presentSyncHistoryAlert()
            } else {
                // No entries to sync - persist integration and show toast immediately
                await persistIntegrationAfterPermissionGrant()
                logger.log(level: .success, tag: tag, message: "HealthKit integration completed without history sync")
                notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
            }
        }
    }

    /// Presents the *Sync Weight History* confirmation alert.
    private func presentSyncHistoryAlert() {
        let alert = AlertModel(
            title: AlertStrings.SyncWeightHistoryAlert.title,
            message: AlertStrings.SyncWeightHistoryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.cancelButton, type: .secondary) { _ in
                    Task {
                        self.dismissModal()
                        self.notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
                        // Delay persistence to ensure toast appears first
                        await self.persistIntegrationAfterPermissionGrant()
                    }
                },
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.syncButton, type: .primary) { _ in
                    self.performFullSync()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Performs the actual sync and shows toast after successful sync.
    private func performFullSync() {
        logger.log(level: .info, tag: tag, message: "HealthKit full sync from store started")
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.syncing))

            do {
                try await healthKitService.syncAllData()
                if let latestEntry = try? await entryService.getLatestEntry() {
                    let notification = EntryNotification(from: latestEntry)
                    await integrationService.logHealthEntry(notification: notification)
                }
                // Dismiss loader before showing toast to avoid showing both at the same time
                notificationService.dismissLoader()
                dismissModal()
                // Persist integration and show toast simultaneously so checkmark and toast appear together
                await persistIntegrationAfterPermissionGrant()
                notificationService.showToast(ToastModel(message: ToastStrings.weightHistorySynced))
                logger.log(level: .success, tag: tag, message: "HealthKit full sync from store completed")
            } catch {
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: ToastStrings.pleaseTryAgain))
                logger.log(level: .error, tag: tag, message: "HealthKit full sync from store failed", data: error.localizedDescription)
            }
        }
    }

    private func showHKRemoveAlert() {
        let lang = alertLang.HKRemoveAlert
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.cancelButton, type: .secondary) { _ in },
                AlertButtonModel(title: lang.removeButton, type: .danger) { _ in
                    self.clearIntegration()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Clears *all* HealthKit data that was previously written by the app and
    /// updates local status accordingly.
    private func clearIntegration() {
        logger.log(level: .info, tag: tag, message: "HealthKit clear integration requested from store")
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration))
            defer { notificationService.dismissLoader() }

            do {
                try await healthKitService.clearHealthKit()
                getLocalStoredData()
                notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationRemoved))
                logger.log(level: .success, tag: tag, message: "HealthKit clear integration completed from store")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to clear HealthKit data", data: error.localizedDescription)
            }
        }
    }

    /// Presents an *Apple Health Out of Sync* alert.
    func showHKOutOfSyncAlert() {
        logger.log(level: .info, tag: tag, message: "Showing HealthKit out-of-sync alert")
        let lang = alertLang.HKOutOfSyncAlert
        // Set up observer to check permissions when user returns from Apple Health
        observeForegroundForOutOfSyncPermissionChanges()
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.closeButton, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func wasPreviouslyIntegrated() async -> Bool {
        guard let info = try? await integrationService.getStoredIntegrationData() else {
            return false
        }
        return info.type == .healthKit && !info.isIntegrated
    }

    // MARK: - Permission change observer when opening Apple Health

    /// Sets up a temporary observer that fires when the app becomes active again
    /// (i.e. user returns from the Apple Health app). At that point we re-evaluate
    /// granted permissions and, if at least one permission is now granted, persist
    /// the integration and advance the flow appropriately.
    private func observeForegroundForPermissionChanges() {
        foregroundObserver?.cancel()

        foregroundObserver = NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in
                guard let self else { return }
                Task { @MainActor in
                    await self.handlePermissionChangeAfterReturningFromAppleHealth()
                    self.foregroundObserver?.cancel()
                    self.foregroundObserver = nil
                }
            }
    }

    /// Sets up a temporary observer that fires when the app becomes active again
    /// (i.e. user returns from the Apple Health app) after seeing the out-of-sync alert.
    /// At that point we re-check if permissions are now in sync.
    private func observeForegroundForOutOfSyncPermissionChanges() {
        foregroundObserver?.cancel()

        foregroundObserver = NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in
                guard let self else { return }
                Task { @MainActor in
                    await self.handleOutOfSyncPermissionCheck()
                    self.foregroundObserver?.cancel()
                    self.foregroundObserver = nil
                }
            }
    }

    /// Handles the permission check when user returns from Apple Health after seeing out-of-sync alert.
    private func handleOutOfSyncPermissionCheck() async {
        let permissionsGranted = healthKitService.getApprovedPermissionList().count

        // Update the out-of-sync status
        isOutOfSync = await healthKitService.isHKOutOfSync()

        // If permissions are now in sync, show a toast notification
        if permissionsGranted >= Self.wgTotalPermissionsCount, !isOutOfSync {
            notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
            logger.log(
                level: .success,
                tag: tag,
                message: "HealthKit out-of-sync resolved after returning from Apple Health. permissionCount=\(permissionsGranted)"
            )
        } else {
            logger.log(
                level: .info,
                tag: tag,
                message: "HealthKit still out-of-sync after return check. permissionCount=\(permissionsGranted), isOutOfSync=\(isOutOfSync)"
            )
        }
    }

    /// Handles the permission check when user returns from Apple Health.
    /// Integration persistence is deferred until after sync completes (or user cancels sync).
    private func handlePermissionChangeAfterReturningFromAppleHealth() async {
        let permissionsGranted = healthKitService.getApprovedPermissionList().count

        guard permissionsGranted > 0 else {
            activeState = nil
            logger.log(level: .error, tag: tag, message: "No HealthKit permissions granted after returning from Apple Health")
            return
        }

        if await checkForUserConflict() {
            activeState = .userConflict
            logger.log(level: .error, tag: tag, message: "HealthKit conflict detected after permission grant")
            return
        }

        // Don't persist integration here - wait until sync completes or user cancels
        await handlePermissionFlowAfterReturning()
    }

    /// Checks if integration is already used by another user
    private func checkForUserConflict() async -> Bool {
        do {
            return try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to check for user conflict after permission grant", data: error.localizedDescription)
            return false
        }
    }

    /// Handles the flow after permissions are granted when returning from Apple Health.
    private func handlePermissionFlowAfterReturning() async {
        dismissModal()
        try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds

        // Always show Integration Complete screen first, then sync prompt will be shown when user taps Finish
        activeState = .integrationComplete
    }

    /// Persists the HealthKit integration after permissions have been granted via Apple Health app.
    /// This is called when the user returns from Apple Health after granting permissions.
    private func persistIntegrationAfterPermissionGrant() async {
        guard let accountID = accountService.activeAccount?.accountId else {
            logger.log(level: .error, tag: tag, message: "No active account when persisting integration")
            return
        }

        do {
            let integrationInfo = IntegrationInfo(
                type: .healthKit,
                isIntegrated: true,
                assignedTo: accountID
            )
            try await integrationService.setStoredIntegrationData(integrationInfo)
            // Refresh the local state to reflect the updated integration status
            getLocalStoredData()
            logger.log(level: .success, tag: tag, message: "Persisted HealthKit integration after permission grant. accountId=\(accountID)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to persist integration after permission grant", data: error.localizedDescription)
        }
    }

    // MARK: - First-Time Connect Screen Flag Management

    /// Checks if the first-time connect screen has been shown for the current account
    private func getHasShownFirstTimeConnectScreen() -> Bool {
        let accountId = accountService.activeAccount?.accountId
        let key = KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: accountId)
        return kvStorage.getValue(forKey: key) as? Bool ?? false
    }

    /// Sets the flag indicating that the first-time connect screen has been shown
    private func setHasShownFirstTimeConnectScreen(_ value: Bool) {
        let accountId = accountService.activeAccount?.accountId
        let key = KvStorageKeys.scopedHealthKitModalKey(.hasShownFirstTimeConnectScreenBase, accountId: accountId)
        kvStorage.setValue(value, forKey: key)
    }
}
