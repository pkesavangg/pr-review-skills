//  HealthKitStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/06/25.
//

import Foundation
import SwiftUI
import Combine

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
    @Published var activeState: AppleHealthIntegrationState? = nil
    
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var entryService: EntryService
    @Injector private var accountService: AccountService
    @Injector private var integrationService: IntegrationsService
    @Injector private var healthKitService: HealthKitService
    @Injector private var logger: LoggerService
    private let kvStorage = KvStorageService.shared
    
    var cancellables: Set<AnyCancellable> = []
    let wgTotalPermissionsCount = 5
    
    /// Retains the Combine subscription for app-active notifications specifically used
    /// when we need to re-check HealthKit permissions after the user is redirected to
    /// the Apple Health app.
    private var foregroundObserver: AnyCancellable? = nil
    /// Tracks whether sync has already been performed in the current integration flow.
    /// Used to prevent showing sync prompt twice in the permission-denied flow.
    private var hasSyncedInCurrentFlow: Bool = false
    
    let alertLang = AlertStrings.self
    let tag = "HealthKitStore"
    // MARK: - Init
    init() {
        loadStatus()
    }
    
    func loadStatus() {
        self.getLocalStoredData()
    }
    
    func getLocalStoredData() {
        Task {
            do {
                let result = try await integrationService.getStoredIntegrationData()
                isIntegrated = (result?.isIntegrated ?? false) && (result?.assignedTo == accountService.activeAccount?.accountId)
                isOutOfSync = await healthKitService.isHKOutOfSync()
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to load integration data", data: error.localizedDescription)
            }
        }
    }
    
    // MARK: - Flow Helpers ------------------------------------------------
    /// Called when the Apple-Health row is tapped in the integrations list.
    func handleRowTap() {
        if isIntegrated {
            showHKRemoveAlert()
            return
        }
        // Reset sync flag when starting a new integration flow
        hasSyncedInCurrentFlow = false
        Task {
            do {
                let isAlreadyIntegrated = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                if isAlreadyIntegrated {
                    activeState = .userConflict
                    return
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to check if HealthKit integration already exists", data: error.localizedDescription)
            }
            
            // Check if this is the first-time connection attempt
            let hasShownFirstTimeConnectScreen = getHasShownFirstTimeConnectScreen()
            
            if !hasShownFirstTimeConnectScreen {
                // First-time connection - show the connect screen
                activeState = .permissionsNotAllowed
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
                    case wgTotalPermissionsCount...:
                        activeState = .permissionsAllowed
                    case 1..<wgTotalPermissionsCount:
                        activeState = .integrationComplete
                    default:
                        activeState = .integrationFailed
                    }
                } else {
                    // User has seen the connect screen before but no integration record exists
                    // Check permissions to determine the appropriate state
                    let permissionCount = healthKitService.getApprovedPermissionList().count
                    if permissionCount == 0 {
                        // No permissions granted - show integration failed screen
                        activeState = .integrationFailed
                    } else {
                        // Some permissions exist but no record - show integration complete to finish setup
                        activeState = .integrationComplete
                    }
                }
            }
        }
    }
    
    /// Handles the primary button tap for the given modal `state`.
    /// Maps UI states to store actions.
    /// - Parameter state: Current `AppleHealthIntegrationState` presented.
    func handlePrimaryAction(for state: AppleHealthIntegrationState) {
        Task {
            switch state {
            case .permissionsNotAllowed:
                setHasShownFirstTimeConnectScreen(true)
                requestAuthorization()
            case .integrationComplete:
                let currentIntegration = try? await integrationService.getStoredIntegrationData()
                if currentIntegration?.isIntegrated != true {
                    await persistIntegrationAfterPermissionGrant()
                }
                finishIntegrationFlow(hasAlreadySynced: hasSyncedInCurrentFlow)
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
        activeState = nil
    }
    
    // MARK: - Private Flow Steps -----------------------------------------
    
    /// Requests HealthKit authorisation via `HealthKitService`. Updates state depending on result.
    private func requestAuthorization() {
        Task {
            do {
                let wasPreviouslyIntegrated = await wasPreviouslyIntegrated()
                let success = try await healthKitService.integrate(turnOn: true)
                
                if success {
                    activeState = wasPreviouslyIntegrated ? nil : .integrationComplete
                    if wasPreviouslyIntegrated {
                        finishIntegrationFlow()
                    }
                } else {
                    activeState = .integrationFailed
                }
                getLocalStoredData()
            } catch {
                activeState = error is IntegrationError ? .userConflict : .integrationFailed
            }
        }
    }
    
    /// Called when user taps **FINISH** on the *Integration Complete* screen.
    /// Presents *Sync Weight History* alert if needed, otherwise shows toast and ends flow.
    /// - Parameter hasAlreadySynced: If true, skips showing sync prompt since sync was already performed.
    private func finishIntegrationFlow(hasAlreadySynced: Bool = false) {
        Task {
            dismissModal()
            
            // If we've already synced in this flow, don't show sync prompt again
            if hasAlreadySynced {
                notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
                hasSyncedInCurrentFlow = false // Reset flag
                return
            }
            
            let hasEntries = (try? await entryService.getEntryCount() ?? 0) ?? 0 > 0
            if hasEntries {
                presentSyncHistoryAlert()
            } else {
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
                    self.dismissModal()
                },
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.syncButton, type: .primary) { _ in
                    self.performFullSync()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Presents the *Sync Weight History* confirmation alert with completion callback.
    /// Used for the "permission denied → later allowed" flow to show Integration Complete screen after sync.
    private func presentSyncHistoryAlertWithCompletion() {
        let alert = AlertModel(
            title: AlertStrings.SyncWeightHistoryAlert.title,
            message: AlertStrings.SyncWeightHistoryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.cancelButton, type: .secondary) { _ in
                    // User cancelled sync, still show Integration Complete screen
                    // Don't mark as synced since user cancelled
                    Task {
                        try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds
                        await MainActor.run {
                            self.activeState = .integrationComplete
                        }
                    }
                },
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.syncButton, type: .primary) { _ in
                    self.performFullSyncWithCompletion()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Performs the actual sync and shows toast after successful sync.
    private func performFullSync() {
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.syncing))
            defer { notificationService.dismissLoader() }
            
            do {
                try await healthKitService.syncAllData()
                if let latestEntry = try? await entryService.getLatestEntry() {
                    await integrationService.logHealthEntry(entry: latestEntry)
                }
                dismissModal()
                notificationService.showToast(ToastModel(message: ToastStrings.weightHistorySynced))
            } catch {
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: ToastStrings.pleaseTryAgain))
            }
        }
    }
    
    /// Performs the actual sync, shows toast after successful sync, then displays Integration Complete screen.
    /// Used for the "permission denied → later allowed" flow.
    private func performFullSyncWithCompletion() {
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.syncing))
            defer { notificationService.dismissLoader() }
            
            do {
                try await healthKitService.syncAllData()
                if let latestEntry = try? await entryService.getLatestEntry() {
                    await integrationService.logHealthEntry(entry: latestEntry)
                }
                // Mark that we've synced in this flow
                hasSyncedInCurrentFlow = true
                // Show sync success toast
                notificationService.showToast(ToastModel(message: ToastStrings.weightHistorySynced))
                // Wait a bit for toast to be visible, then show Integration Complete screen
                try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
                await MainActor.run {
                    self.activeState = .integrationComplete
                }
            } catch {
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: ToastStrings.pleaseTryAgain))
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
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration))
            defer { notificationService.dismissLoader() }
            
            do {
                try await healthKitService.clearHealthKit()
                getLocalStoredData()
                notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationRemoved))
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to clear HealthKit data", data: error.localizedDescription)
            }
        }
    }
    
    /// Presents an *Apple Health Out of Sync* alert.
    func showHKOutOfSyncAlert() {
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
    
    /// Handles the permission check and integration persistence when user returns from Apple Health.
    private func handlePermissionChangeAfterReturningFromAppleHealth() async {
        let permissionsGranted = healthKitService.getApprovedPermissionList().count
        
        guard permissionsGranted > 0 else {
            activeState = nil
            return
        }
        
        if await checkForUserConflict() {
            activeState = .userConflict
            return
        }
        
        await persistIntegrationAfterPermissionGrant()
        getLocalStoredData()
        
        // Handle flow based on permission count: full permissions show completion screen after sync
        let isFullPermissions = permissionsGranted >= wgTotalPermissionsCount
        await handlePermissionFlowAfterReturning(isFullPermissions: isFullPermissions)
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
    /// - Parameter isFullPermissions: If true, shows Integration Complete screen after sync completion.
    private func handlePermissionFlowAfterReturning(isFullPermissions: Bool) async {
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
